package com.allpick.new_allpick.auth.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * [포트폴리오 발췌] PASS 본인인증 결과.
 * Redis 단명 토큰(verifyKey)에 담겨 회원가입/비밀번호 찾기 등 다운스트림에서 1회 소비된다.
 * ci/di는 본인확인 식별자로, 재가입 차단(CI 해시) 등에만 사용.
 */
public record PhoneVerifyResult(
        String phone,
        String name,
        LocalDate birthDate,
        String gender,        // "M" | "F" | null
        String carrier,       // SKT | KTF | LGT | MVNO
        boolean foreigner,
        String ci,            // unique_key (본인확인 CI)
        String di,            // unique_in_site (DI)
        LocalDateTime certifiedAt,
        String impUid,
        String merchantUid
) {}
