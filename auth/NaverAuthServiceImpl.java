package com.allpick.new_allpick.auth.domain.service.OAuth;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.allpick.new_allpick.auth.domain.exception.AuthErrorCode;
import com.allpick.new_allpick.auth.domain.exception.AuthException;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * [포트폴리오 발췌] 네이버 로그인.
 * 카카오와 동일하게, access_token으로 서버가 네이버 프로필 API를 호출해 사용자 정보를 받는다.
 * (인가코드→토큰 교환 메서드는 SDK 방식 전환 후 미사용이라 발췌에서 생략)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NaverAuthServiceImpl implements NaverAuthService {

	private final RestTemplate restTemplate;

	@Override
	public NaverUserInfo fetchUserInfo(String naverAccessToken) {
		String profileUrl = "https://openapi.naver.com/v1/nid/me";

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(naverAccessToken);
		HttpEntity<Void> request = new HttpEntity<>(headers);

		try {
			ResponseEntity<JsonNode> response =
					restTemplate.exchange(profileUrl, HttpMethod.GET, request, JsonNode.class);
			JsonNode responseBody = response.getBody();

			// 네이버는 resultcode "00"이 정상
			if (responseBody == null || !"00".equals(responseBody.get("resultcode").asText())) {
				throw new AuthException(AuthErrorCode.NAVER_PROFILE_FETCH_FAILED);
			}

			JsonNode userInfo = responseBody.get("response");
			String naverId = userInfo.get("id").asText();          // 식별자 필수
			String name = userInfo.has("name") ? userInfo.get("name").asText() : "성함 없음";
			String mobile = userInfo.has("mobile") ? userInfo.get("mobile").asText() : "";

			return new NaverUserInfo("nv_" + naverId, name, mobile);

		} catch (AuthException e) {
			throw e;
		} catch (Exception e) {
			log.error("네이버 프로필 조회 중 에러: {}", e.getMessage());
			throw new AuthException(AuthErrorCode.NAVER_PROFILE_FETCH_FAILED, e);
		}
	}
}
