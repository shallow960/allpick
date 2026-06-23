package com.allpick.new_allpick.auth.infra.security;

import java.util.UUID;

/** SecurityContext에 함께 넣어둘 사용자 부가정보. uid = DB 조회 키, role = 선택 권한(UI 분기용) */
public record AuthUserDetails(UUID uid, String role) {
}
