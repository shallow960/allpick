package com.allpick.new_allpick.admin.domain;

/**
 * [포트폴리오 발췌] 관리자 계층(티어) — UserRole 보유 조합으로 도출되는 추상 개념
 *
 * - TIER_1: SUPER_ADMIN 보유 (개발사 총괄)
 * - TIER_2: COMPANY_ADMIN 보유 (클라이언트 회사 슈퍼계정)
 * - TIER_3: ADMIN만 보유 (클라이언트 회사 직원)
 *
 * 판정은 AdminScopeService에서 ap_user_roles 조회 결과 기준으로 수행.
 */
public enum AdminTier {
    TIER_1, // 개발사 총괄
    TIER_2, // 회사 슈퍼계정
    TIER_3  // 직원
}
