package com.allpick.new_allpick.auth.domain.service.OAuth;

import java.util.Map;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.allpick.new_allpick.auth.domain.exception.AuthErrorCode;
import com.allpick.new_allpick.auth.domain.exception.AuthException;
import com.allpick.new_allpick.global.config.KakaoProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * [포트폴리오 발췌] 카카오 로그인.
 * 클라이언트가 받은 access_token을 서버가 카카오 API(user/me)로 다시 호출해
 * 사용자 정보를 받아온다. 토큰 진위를 provider가 검증해주는 구조라 위조 토큰은 통과하지 못함.
 */
@Slf4j
@Service
public class KakaoOauthServiceImpl implements KakaoOauthService {

	private final KakaoProperties props;
	private final RestTemplate restTemplate = new RestTemplate();

	public KakaoOauthServiceImpl(KakaoProperties props) {
		this.props = props;
	}

	@Override
	public KakaoUserInfo fetchUserInfo(String kakaoAccessToken) {
		if (kakaoAccessToken == null || kakaoAccessToken.isBlank()) {
			throw new AuthException(AuthErrorCode.KAKAO_ACCESS_TOKEN_REQUIRED);
		}

		// Authorization: Bearer {access_token} 으로 카카오 user/me 호출
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(kakaoAccessToken);
		HttpEntity<Void> req = new HttpEntity<>(headers);

		ResponseEntity<Map> res = restTemplate.exchange(
				props.userInfoUri(), HttpMethod.GET, req, Map.class);

		Map body = res.getBody();
		if (body == null || body.get("id") == null) {
			throw new AuthException(AuthErrorCode.KAKAO_USERINFO_FAILED, "응답이 비었습니다.");
		}

		// 카카오 아이디 정책: "kk_" + kakaoId (내부 식별용)
		String providerUserId = "kk_" + String.valueOf(body.get("id"));

		Object kakaoAccountObj = body.get("kakao_account");
		if (!(kakaoAccountObj instanceof Map kakaoAccount)) {
			throw new AuthException(AuthErrorCode.KAKAO_USERINFO_FAILED, "kakao_account가 없습니다.");
		}

		String name = getStringOrNull(kakaoAccount, "name");
		String phoneNumber = getStringOrNull(kakaoAccount, "phone_number");
		if (name == null || name.isBlank()) {
			throw new AuthException(AuthErrorCode.KAKAO_NAME_MISSING);
		}

		return new KakaoUserInfo(providerUserId, name, phoneNumber);
	}

	private String getStringOrNull(Map map, String key) {
		Object v = map.get(key);
		return (v == null) ? null : String.valueOf(v);
	}
}
