package com.allpick.new_allpick.auth.domain.service.OAuth;

import com.allpick.new_allpick.auth.domain.exception.AuthErrorCode;
import com.allpick.new_allpick.auth.domain.exception.AuthException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * [포트폴리오 발췌] 구글 로그인 토큰 검증.
 * 라이브러리가 제공하는 GoogleIdTokenVerifier로 서명·aud를 검증한다.
 * (애플엔 대응되는 표준 검증기가 없어 직접 구현했음 — AppleAuthServiceImpl 참고)
 */
@Service
public class GoogleAuthServiceImpl implements GoogleAuthService {

    @Value("${google.client-id}")
    private String googleClientId;

    @Override
    public GoogleUserInfo getGoogleUserInfo(String idTokenString) throws Exception {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        GoogleIdToken idToken = verifier.verify(idTokenString);
        if (idToken == null) {
            throw new AuthException(AuthErrorCode.GOOGLE_TOKEN_INVALID);
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String providerUserId = "gg_" + payload.getSubject();
        String name = (String) payload.get("name");

        return new GoogleUserInfo(providerUserId, name);
    }
}
