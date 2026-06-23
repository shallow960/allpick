package com.allpick.new_allpick.admin.application.service;

import com.allpick.new_allpick.admin.domain.AdminTier;
import com.allpick.new_allpick.user.domain.entity.UserRole;
import com.allpick.new_allpick.user.domain.repository.UserRoleGrantRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * [포트폴리오 발췌] 관리자 티어 판정 서비스
 *
 * 유저의 ap_user_roles 보유 조합으로 AdminTier를 도출한다.
 * 권한 변경 즉시 반영을 위해 캐시는 적용하지 않음(어드민 액션 한정이라 호출 빈도 낮음).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminScopeService {

    private final UserRoleGrantRepository userRoleGrantRepository;

    /**
     * 유저의 관리자 티어 도출 — 비-admin이면 null
     * 우선순위: SUPER_ADMIN > COMPANY_ADMIN > ADMIN
     * (SUPER_ADMIN+ADMIN 둘 다 보유해도 TIER_1로 판정)
     */
    public AdminTier resolveTier(UUID uid) {
        if (uid == null) return null;
        if (userRoleGrantRepository.existsByUidAndRole(uid, UserRole.SUPER_ADMIN)) return AdminTier.TIER_1;
        if (userRoleGrantRepository.existsByUidAndRole(uid, UserRole.COMPANY_ADMIN)) return AdminTier.TIER_2;
        if (userRoleGrantRepository.existsByUidAndRole(uid, UserRole.ADMIN)) return AdminTier.TIER_3;
        return null;
    }

    /** 1차 또는 2차 여부 — 직원 승격/회수/권한 부여 가드용 */
    public boolean canManageTier3(UUID actorUid) {
        AdminTier tier = resolveTier(actorUid);
        return tier == AdminTier.TIER_1 || tier == AdminTier.TIER_2;
    }
}
