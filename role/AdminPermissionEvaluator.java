package com.allpick.new_allpick.admin.infra.security;

import com.allpick.new_allpick.admin.application.service.AdminScopeService;
import com.allpick.new_allpick.admin.domain.AdminPermission;
import com.allpick.new_allpick.admin.domain.AdminTier;
import com.allpick.new_allpick.admin.domain.repository.AdminPermissionRepository;
import com.allpick.new_allpick.auth.util.SecurityUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * [포트폴리오 발췌] @PreAuthorize SpEL용 권한 평가기.
 * @Component("adminPerm")로 등록되어 SpEL에서 다음처럼 호출된다:
 *   @PreAuthorize("hasRole('ADMIN') and @adminPerm.canAccess('USER_MANAGEMENT')")
 *   @PreAuthorize("hasRole('ADMIN') and @adminPerm.isTier1Or2()")
 *
 * 매 요청 호출되는 핫패스라, 1차/2차는 권한 테이블 조회 없이 티어 체크만으로 통과시키고
 * 3차일 때만 ap_admin_permissions(인덱스된 작은 테이블)를 조회한다.
 */
@Component("adminPerm")
@RequiredArgsConstructor
@Slf4j
public class AdminPermissionEvaluator {

    private final AdminScopeService adminScopeService;
    private final AdminPermissionRepository adminPermissionRepository;

    /**
     * 페이지 권한 체크
     * - 1차/2차: 무조건 통과 (테이블 조회 없음)
     * - 3차: ap_admin_permissions에 row 있으면 통과
     * - 비-admin: false
     */
    public boolean canAccess(String permissionName) {
        UUID uid = currentUidOrNull();
        if (uid == null) return false;

        AdminTier tier = adminScopeService.resolveTier(uid);
        if (tier == null) return false;
        if (tier == AdminTier.TIER_1 || tier == AdminTier.TIER_2) return true; // 암묵 보유

        AdminPermission permission;
        try {
            permission = AdminPermission.valueOf(permissionName);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown AdminPermission name in @PreAuthorize: {}", permissionName);
            return false;
        }
        return adminPermissionRepository.existsByUser_UidAndPermission(uid, permission);
    }

    /** 1차 또는 2차 여부 — 직원 관리 컨트롤러 가드용 */
    public boolean isTier1Or2() {
        UUID uid = currentUidOrNull();
        if (uid == null) return false;
        AdminTier tier = adminScopeService.resolveTier(uid);
        return tier == AdminTier.TIER_1 || tier == AdminTier.TIER_2;
    }

    /** SecurityContext에서 현재 uid 추출 — 비인증/이상 컨텍스트는 null로 안전 처리 */
    private UUID currentUidOrNull() {
        try {
            return SecurityUtil.getCurrentUid();
        } catch (Exception e) {
            return null;
        }
    }
}
