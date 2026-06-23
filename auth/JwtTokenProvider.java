package com.allpick.new_allpick.auth.infra.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.allpick.new_allpick.user.domain.entity.UserRole;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * [포트폴리오 발췌] JWT 발급/검증 — 다중 역할 권한 구조.
 *
 * 기존엔 role(단일) 하나만 담던 구조였으나, 한 사용자가 여러 역할을 동시에 갖게 되면서
 *  - authorities : 보유 권한 전체 (인가 기준)
 *  - currentRole : 현재 선택한 역할 (UI/모드 분기 기준)
 * 으로 분리했다. authorities에는 어떤 역할이든 ROLE_USER를 항상 포함해
 * 기본 기능 접근이 끊기지 않도록 보장한다. (OTP/슬라이딩 세션 등은 발췌에서 생략)
 */
@Component
public class JwtTokenProvider {

	private final JwtProperties props;
	private final SecretKey key;

	public JwtTokenProvider(JwtProperties props) {
		this.props = props;
		this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
	}

	public String generateAccessToken(UUID uid,
									   String userId,
									   List<UserRole> ownedRoles,   // 보유 권한 (ap_user_roles 기반)
									   UserRole currentRole) {      // 선택 권한
		Instant now = Instant.now();
		Instant exp = now.plusSeconds(props.accessTokenExpSeconds());

		// authorities = 보유 권한 전체 + ROLE_USER 기본 포함. 예: ["ROLE_USER", "ROLE_OWNER"]
		List<String> authorities = buildAuthorities(ownedRoles);

		return Jwts.builder()
				.setIssuer(props.issuer())
				.setSubject(uid.toString())
				.setIssuedAt(Date.from(now))
				.setExpiration(Date.from(exp))
				.claim("uid", uid.toString())
				.claim("userId", userId)
				.claim("authorities", authorities)   // 인가 기준
				.claim("currentRole", currentRole.name())  // UI/모드 분기 기준
				.signWith(key)
				.compact();
	}

	private List<String> buildAuthorities(List<UserRole> ownedRoles) {
		// 중복 제거 + ROLE_USER 강제 — OWNER/SELLER여도 일반 회원 기능 접근 유지를 여기서 보장
		Set<String> set = new HashSet<>();
		set.add("ROLE_USER");
		if (ownedRoles != null) {
			for (UserRole r : ownedRoles) {
				if (r != null) set.add("ROLE_" + r.name());
			}
		}
		return new ArrayList<>(set);
	}

	public boolean validate(String token) {
		try {
			Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/** 보유 권한 전체 반환 (인가용). 누락 시 ROLE_USER 보정 */
	public List<String> getAuthorities(String token) {
		Object raw = getClaims(token).get("authorities");
		if (!(raw instanceof List<?> list)) {
			return List.of("ROLE_USER");
		}
		List<String> result = new ArrayList<>();
		for (Object o : list) {
			if (o != null) result.add(String.valueOf(o));
		}
		if (!result.contains("ROLE_USER")) result.add("ROLE_USER");
		return result;
	}

	/** 선택 권한 반환 (UI 분기용) */
	public UserRole getRole(String token) {
		return UserRole.valueOf(getClaims(token).get("currentRole", String.class));
	}

	public UUID getUid(String token) {
		return UUID.fromString(getClaims(token).get("uid", String.class));
	}

	public String getUserId(String token) {
		return getClaims(token).getSubject();
	}

	private Claims getClaims(String token) {
		return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
	}
}
