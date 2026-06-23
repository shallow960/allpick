package com.allpick.new_allpick.auth.domain.service;

import com.allpick.new_allpick.auth.domain.exception.AuthErrorCode;
import com.allpick.new_allpick.auth.domain.exception.AuthException;
import com.allpick.new_allpick.auth.domain.model.PhoneVerifyResult;
import com.allpick.new_allpick.auth.infra.portone.PhoneVerifyKeyStore;
import com.allpick.new_allpick.auth.infra.portone.PortOneApiClient;
import com.allpick.new_allpick.auth.infra.portone.PortOneProperties;
import com.allpick.new_allpick.common.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.Map;

/**
 * [포트폴리오 발췌] PASS 본인인증(다날, PortOne V1 경유) 검증 + verifyKey 발급/소비.
 *
 * 흐름
 *  1) 프론트 WebView가 본인인증 → impUid 캡처 → 백엔드 호출
 *  2) verifyAndIssueKey: PortOne 결과 조회 → certified / merchant_uid / 연령 / CI 검증
 *     → 결과를 Redis 5분 단명 토큰(verifyKey)으로 저장
 *  3) consume(verifyKey): 회원가입·비번찾기 등 다운스트림이 1회 소비 (재사용 방지)
 *
 * 설계 포인트
 *  - 본인인증 단계와 가입 단계를 verifyKey(1회용 토큰)로 분리 → 인증 결과 재사용/위조 차단
 *  - merchant_uid 응답 일치 검증으로 타 거래 결과 도용 방지
 *  - 신원(이름·전화·생년·CI/DI)은 PASS 결과를 진실값으로 사용
 *
 * (로컬 QA bypass 분기, PII 디버그 로그는 발췌에서 제거)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortOneIdentityService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PortOneApiClient portOneApiClient;
    private final PhoneVerifyKeyStore phoneVerifyKeyStore;
    private final PortOneProperties properties;

    /** imp_uid + merchant_uid 검증 → 검증 결과 반환 (verifyKey 저장은 storeAndGetKey에서) */
    public PhoneVerifyResult verifyAndIssueKey(String impUid, String merchantUid) {
        if (impUid == null || impUid.isBlank()) {
            throw new AuthException(AuthErrorCode.PASS_VERIFY_FAILED);
        }
        String accessToken = portOneApiClient.issueAccessToken();
        Map<String, Object> data = portOneApiClient.fetchCertification(impUid, accessToken);
        return mapAndValidate(data, impUid, merchantUid);
    }

    /** 검증 결과를 Redis에 저장하고 verifyKey 발급 */
    public String storeAndGetKey(PhoneVerifyResult result) {
        return phoneVerifyKeyStore.issue(result);
    }

    /** verifyKey 1회 소비 (회원가입 등 다운스트림에서 호출) */
    public PhoneVerifyResult consume(String verifyKey) {
        return phoneVerifyKeyStore.consume(verifyKey);
    }

    // ─── PortOne 응답 → PhoneVerifyResult 매핑 + 검증 ───
    private PhoneVerifyResult mapAndValidate(Map<String, Object> data, String impUid, String merchantUid) {
        // 1) 본인인증 성공 여부
        if (!(data.get("certified") instanceof Boolean b && b)) {
            throw new AuthException(AuthErrorCode.PASS_NOT_CERTIFIED);
        }
        // 2) merchant_uid 일치 (위변조 방지)
        String responseMerchantUid = asString(data.get("merchant_uid"));
        if (merchantUid != null && !merchantUid.isBlank() && !merchantUid.equals(responseMerchantUid)) {
            throw new AuthException(AuthErrorCode.PASS_MERCHANT_UID_MISMATCH);
        }

        String phone = normalizePhone(asString(data.get("phone")));
        String name = asString(data.get("name"));
        LocalDate birthDate = parseBirthDate(asString(data.get("birthday")), asString(data.get("birth")));
        String gender = mapGender(asString(data.get("gender")));
        String carrier = asString(data.get("carrier"));
        boolean foreigner = data.get("foreigner") instanceof Boolean f && f;
        String ci = asString(data.get("unique_key"));
        String di = asString(data.get("unique_in_site"));
        LocalDateTime certifiedAt = parseCertifiedAt(data.get("certified_at"));

        // 3) CI 필수
        if (ci == null || ci.isBlank()) {
            throw new AuthException(AuthErrorCode.PASS_VERIFY_FAILED);
        }
        // 4) 연령 제한
        int ageLimit = properties.getPass().getAgeLimit();
        if (birthDate != null && Period.between(birthDate, LocalDate.now(KST)).getYears() < ageLimit) {
            throw new BusinessRuleException("만 " + ageLimit + "세 이상만 가입할 수 있습니다.");
        }

        return new PhoneVerifyResult(phone, name, birthDate, gender, carrier, foreigner,
                ci, di, certifiedAt, impUid, responseMerchantUid);
    }

    /** 휴대폰 정규화: '-' 제거, +82 → 0 */
    private String normalizePhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+82")) digits = "0" + digits.substring(3);
        return digits.replaceAll("[^0-9]", "");
    }

    private String mapGender(String gender) {
        if (gender == null) return null;
        return switch (gender.toLowerCase()) {
            case "male" -> "M";
            case "female" -> "F";
            default -> null;
        };
    }

    private LocalDate parseBirthDate(String birthday, String birth) {
        try {
            if (birthday != null && !birthday.isBlank()) return LocalDate.parse(birthday);
            if (birth != null && birth.length() == 8) {
                return LocalDate.of(Integer.parseInt(birth.substring(0, 4)),
                        Integer.parseInt(birth.substring(4, 6)), Integer.parseInt(birth.substring(6, 8)));
            }
        } catch (Exception e) {
            log.warn("[PASS] 생년월일 파싱 실패");
        }
        return null;
    }

    private LocalDateTime parseCertifiedAt(Object certifiedAt) {
        if (certifiedAt instanceof Number n) {
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(n.longValue()), KST);
        }
        return LocalDateTime.now(KST);
    }

    private String asString(Object o) {
        return o == null ? null : o.toString();
    }
}
