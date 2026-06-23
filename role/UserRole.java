package com.allpick.new_allpick.user.domain.entity;

/**
 * 사용자 권한 enum (ap_user_roles에 다중 부여 — UserRoleGrant 참고)
 *
 * 관리자 권한은 2계층으로 동작한다:
 *  1) 역할(UserRole): SUPER_ADMIN / COMPANY_ADMIN / ADMIN 보유 여부
 *  2) 티어(AdminTier): 위 역할 조합에서 도출
 *      - SUPER_ADMIN  → 1차 (개발사 총괄)
 *      - COMPANY_ADMIN→ 2차 (클라이언트 회사 슈퍼계정)
 *      - ADMIN만 보유  → 3차 (직원)
 *     1·2차는 모든 어드민 권한 암묵 보유, 3차(직원)는 ap_admin_permissions에
 *     부여된 기능 권한만 접근.
 *  (티어 판정·권한 평가는 AdminScopeService / AdminPermissionEvaluator 참고)
 */
public enum UserRole {
	USER,          // 일반회원
	ADMIN,         // 관리자 (3차 = 직원 기본 자격)
	OWNER,         // 오프라인 매장 사장님
	SELLER,        // 온라인 매장 사장님
	MARKET,        // 마켓장
	INFLUENCER,    // 인플루언서
	SUPER_ADMIN,   // 1차 — 개발사 총괄
	COMPANY_ADMIN  // 2차 — 클라이언트 회사 슈퍼계정
}
