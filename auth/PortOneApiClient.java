package com.allpick.new_allpick.auth.infra.portone;

import com.allpick.new_allpick.auth.domain.exception.AuthErrorCode;
import com.allpick.new_allpick.auth.domain.exception.AuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * [포트폴리오 발췌] PortOne(아임포트) V1 REST API 클라이언트.
 *
 *  1) POST /users/getToken          → access_token 발급 (REST API Key/Secret)
 *  2) GET  /certifications/{impUid} → 본인인증 결과 조회
 *
 * 응답 포맷: { code: 0|-1, message, response: {...} } — code=0만 정상.
 * 본인인증은 단발성이라 토큰 캐싱은 하지 않음.
 * (REST API Key/Secret은 설정에서 주입 — application-example.yml 참고)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortOneApiClient {

    private final PortOneProperties properties;
    private final RestTemplate restTemplate;

    @SuppressWarnings("unchecked")
    public String issueAccessToken() {
        String url = properties.getBaseUrl() + "/users/getToken";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of(
                "imp_key", properties.getRestApiKey(),
                "imp_secret", properties.getRestApiSecret()
        );

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
            Map<String, Object> respBody = response.getBody();
            assertCodeOk(respBody, "/users/getToken");

            Map<String, Object> data = (Map<String, Object>) respBody.get("response");
            if (data == null || data.get("access_token") == null) {
                throw new AuthException(AuthErrorCode.PASS_VERIFY_FAILED);
            }
            return data.get("access_token").toString();
        } catch (RestClientException e) {
            log.error("[PortOne] getToken 호출 실패", e);
            throw new AuthException(AuthErrorCode.PASS_VERIFY_FAILED);
        }
    }

    /**
     * 본인인증 결과 조회. 응답 response 구조(V1):
     *   imp_uid, merchant_uid, name, gender, birthday(YYYY-MM-DD), foreigner, phone,
     *   carrier, certified(boolean), certified_at(unix sec), unique_key(CI), unique_in_site(DI)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchCertification(String impUid, String accessToken) {
        String url = properties.getBaseUrl() + "/certifications/" + impUid;

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, accessToken);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> respBody = response.getBody();
            assertCodeOk(respBody, "/certifications/" + impUid);

            Map<String, Object> data = (Map<String, Object>) respBody.get("response");
            if (data == null) {
                throw new AuthException(AuthErrorCode.PASS_VERIFY_FAILED);
            }
            return data;
        } catch (RestClientException e) {
            log.error("[PortOne] /certifications 호출 실패: impUid={}", impUid, e);
            throw new AuthException(AuthErrorCode.PASS_VERIFY_FAILED);
        }
    }

    /** PortOne 표준 응답에서 code가 0이 아니면 실패로 간주 */
    private void assertCodeOk(Map<String, Object> body, String path) {
        if (body == null) {
            throw new AuthException(AuthErrorCode.PASS_VERIFY_FAILED);
        }
        Object codeObj = body.get("code");
        int code = (codeObj instanceof Number n) ? n.intValue() : -1;
        if (code != 0) {
            log.error("[PortOne] {} 실패: code={}, message={}", path, code, body.get("message"));
            throw new AuthException(AuthErrorCode.PASS_VERIFY_FAILED);
        }
    }
}
