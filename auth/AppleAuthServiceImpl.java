package com.allpick.new_allpick.auth.domain.service.OAuth;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.allpick.new_allpick.auth.domain.exception.AuthErrorCode;
import com.allpick.new_allpick.auth.domain.exception.AuthException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;

/**
 * [포트폴리오 발췌] Apple 로그인 identityToken 서명 검증 부분만 발췌.
 * (refresh_token 교환 / revoke / clientSecret 생성 등 탈퇴 관련 코드는 생략)
 *
 * 문제: 기존엔 JWT payload만 디코딩해 sub를 신뢰 → 서명 검증 부재로 계정 탈취 가능.
 * 해결: Apple JWKS 공개키로 서명 + iss + aud + exp를 검증.
 */
@Service
@Slf4j
public class AppleAuthServiceImpl implements AppleAuthService {

    private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    @Value("${apple.client-id}")
    private String clientId;            // Service ID (Android/Web 로그인 시 token aud)
    @Value("${apple.ios-bundle-id:}")
    private String iosBundleId;         // iOS 앱 번들 ID (iOS 네이티브 로그인 시 token aud)

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public AppleUserInfo getAppleUserInfo(String identityToken) {
        try {
            Claims claims = verifyIdentityToken(identityToken);
            String providerUserId = "ae_" + claims.getSubject();
            return new AppleUserInfo(providerUserId, null);
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("Apple identity token 검증 실패", e);
            throw new AuthException(AuthErrorCode.APPLE_TOKEN_PARSE_FAILED, e.getMessage(), e);
        }
    }

    /**
     * Apple identityToken(JWT) 서명 검증
     * 1. JWT 헤더에서 kid 추출
     * 2. Apple JWKS에서 매칭되는 공개키 조회
     * 3. 서명 + iss + exp 검증, aud는 OS별로 달라 수동 검증
     */
    private Claims verifyIdentityToken(String identityToken) throws Exception {
        String[] parts = identityToken.split("\\.");
        if (parts.length < 3) {
            throw new AuthException(AuthErrorCode.APPLE_TOKEN_INVALID);
        }
        String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String kid = objectMapper.readTree(headerJson).get("kid").asText();

        PublicKey publicKey = getApplePublicKey(kid);

        Claims claims = Jwts.parser()
                .verifyWith((java.security.interfaces.RSAPublicKey) publicKey)
                .requireIssuer(APPLE_ISSUER)
                .build()
                .parseSignedClaims(identityToken)   // 서명 불일치·만료 시 예외
                .getPayload();

        // audience 수동 검증: Service ID(Android) 또는 iOS 번들 ID 둘 다 허용
        String aud = (claims.getAudience() != null && !claims.getAudience().isEmpty())
                ? claims.getAudience().iterator().next() : null;
        boolean validAud = clientId.equals(aud)
                || (iosBundleId != null && !iosBundleId.isBlank() && iosBundleId.equals(aud));
        if (!validAud) {
            throw new AuthException(AuthErrorCode.APPLE_TOKEN_INVALID, "Invalid audience: " + aud);
        }
        return claims;
    }

    /** Apple JWKS 엔드포인트에서 kid에 매칭되는 RSA 공개키 조회 */
    private PublicKey getApplePublicKey(String kid) throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(APPLE_JWKS_URL, String.class);
        JsonNode keys = objectMapper.readTree(response.getBody()).get("keys");

        for (JsonNode key : keys) {
            if (kid.equals(key.get("kid").asText())) {
                BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(key.get("n").asText()));
                BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(key.get("e").asText()));
                return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e));
            }
        }
        throw new AuthException(AuthErrorCode.APPLE_TOKEN_INVALID);
    }
}
