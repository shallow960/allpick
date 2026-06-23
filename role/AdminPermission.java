package com.allpick.new_allpick.admin.domain;

/**
 * [포트폴리오 발췌] 어드민 페이지(기능 영역)별 접근 권한
 *
 * - 1차/2차: 모든 권한 암묵적 보유 (테이블 조회 없이 통과)
 * - 3차: ap_admin_permissions 테이블에 row가 있는 권한만 통과
 *
 * 직원 승격/회수는 권한 enum이 아니라 티어 체크(@adminPerm.isTier1Or2)로 분리한다.
 */
public enum AdminPermission {
    USER_MANAGEMENT,
    BANNER_MANAGEMENT,
    BANNED_WORDS,
    FAQ_MANAGEMENT,
    GIFTICON_MANAGEMENT,
    STORE_MANAGEMENT,
    DIAMOND_MANAGEMENT,
    EVENT_TOGGLE,
    DASHBOARD,
    GAME_ADMIN,
    VAULT_ADMIN,
    NOTIFICATION_ADMIN,
    AD_MANAGEMENT,
    NOTICE_MANAGEMENT,
    TERMS_MANAGEMENT,
    ATTENDANCE_ADMIN,
    MISSION_ADMIN,
    VOTE_ADMIN
}
