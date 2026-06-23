package com.allpick.new_allpick.auth.infra.security;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.allpick.new_allpick.auth.infra.jwt.JwtTokenProvider;
import com.allpick.new_allpick.user.domain.entity.User;
import com.allpick.new_allpick.user.domain.entity.UserConstants;
import com.allpick.new_allpick.user.domain.entity.UserRole;
import com.allpick.new_allpick.user.domain.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * [포트폴리오 발췌] JWT 인증 필터.
 * 토큰의 authorities(보유 권한 전체)를 GrantedAuthority로 변환해 SecurityContext에 주입한다.
 * principal에는 currentRole(선택 권한)을 넣어 UI/마이페이지 분기에 사용.
 * (permitAll 경로 목록, 어드민 슬라이딩 세션 갱신 등은 발췌에서 생략)
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtTokenProvider jwtTokenProvider;
	private final UserRepository userRepository;

	public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.userRepository = userRepository;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			filterChain.doFilter(request, response);
			return;
		}

		String token = authHeader.substring(7);
		if (!jwtTokenProvider.validate(token)) {
			filterChain.doFilter(request, response);
			return;
		}

		UUID uid = jwtTokenProvider.getUid(token);
		UserRole currentRole = jwtTokenProvider.getRole(token);        // 선택 권한 (UI 분기)
		List<String> roleStrings = jwtTokenProvider.getAuthorities(token); // 보유 권한 (인가)

		// 사용자 상태 확인 (탈퇴/정지 계정 차단)
		User user = userRepository.findByUid(uid).orElse(null);
		if (user == null || !UserConstants.Status.ACTIVE.equals(user.getStatus())) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		AuthUserDetails principal = new AuthUserDetails(uid, currentRole.name());

		// 토큰 authorities → GrantedAuthority. ROLE_ 접두어 보정 + ROLE_USER 누락 대비
		Set<String> normalized = new HashSet<>();
		normalized.add("ROLE_USER");
		if (roleStrings != null) {
			for (String r : roleStrings) {
				if (r == null || r.isBlank()) continue;
				String upper = r.trim().toUpperCase();
				if (!upper.startsWith("ROLE_")) upper = "ROLE_" + upper;
				normalized.add(upper);
			}
		}
		List<SimpleGrantedAuthority> authorities = normalized.stream()
				.map(SimpleGrantedAuthority::new)
				.toList();

		UsernamePasswordAuthenticationToken authentication =
				new UsernamePasswordAuthenticationToken(principal, null, authorities);
		SecurityContextHolder.getContext().setAuthentication(authentication);

		filterChain.doFilter(request, response);
	}
}
