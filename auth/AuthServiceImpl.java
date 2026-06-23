package com.allpick.new_allpick.auth.domain.service;

/**
 * [포트폴리오 발췌] SNS 회원가입 — oauthSignup() 핵심 흐름만 발췌.
 *
 * 설계 포인트
 *  1. 신원(이름·전화·생년·CI/DI)은 프론트 입력이 아니라 PASS 본인인증 결과를 진실값으로 사용
 *     → 카카오/네이버/구글/애플 모든 provider를 PASS로 통일해, 전화번호 위변조로 타인 계정에
 *       SNS를 붙이는 경로를 차단.
 *  2. 탈퇴 회원 재가입은 CI 해시 기준 30일 차단 (전화번호가 아니라 본인확인 CI로 식별).
 *  3. 같은 전화번호의 기존 회원이 있으면 → 이름 일치 검증 후 "계정 통합"(기존 회원에 SNS 연결),
 *     없으면 → 신규 가입.
 *
 * (필수값/닉네임·이메일 중복 검증, 성별 정규화, User 빌더 등 보일러플레이트는 생략)
 */
public class AuthServiceImpl /* implements AuthService */ {

    public OAuthLoginResponse oauthSignup(OAuthSignupRequest request, String deviceId, String osType) {

        // 0) Apple은 providerUserId(identityToken)를 서버에서 재검증해 진짜 고유 ID로 치환
        String finalProviderUserId = request.getProviderUserId();
        if (request.getProvider() == UserSnsAccount.Provider.APPLE) {
            finalProviderUserId = appleAuthService.getAppleUserInfo(finalProviderUserId).providerUserId();
        }

        // 1) 이미 연동된 SNS 계정이면 중복 가입 차단
        if (userSnsAccountRepository.existsByProviderAndProviderUserId(
                request.getProvider(), request.getProviderUserId())) {
            throw new DuplicateResourceException("이미 연동된 OAuth 계정입니다.");
        }

        // 2) PASS 본인인증 1회 소비 → 신원 확정 (phone/name/birth/CI/DI). verifyKey 없으면 가입 불가
        if (request.getVerifyKey() == null || request.getVerifyKey().isBlank()) {
            throw new AuthException(AuthErrorCode.PHONE_REQUIRED);
        }
        PhoneVerifyResult passResult = portOneIdentityService.consume(request.getVerifyKey());
        String finalPhoneNumber = passResult.phone();

        // 3) 탈퇴 후 30일 이내 재가입 차단 — 전화번호가 아닌 본인확인 CI 해시로 식별
        String ciHash = HashUtil.sha256(passResult.ci());
        if (withdrawnUserIdentityRepository.existsByCiHashAfter(ciHash, LocalDateTime.now(KST).minusDays(30))) {
            throw new BusinessRuleException("탈퇴 후 30일 이내에는 재가입할 수 없습니다.");
        }

        // 4) 같은 전화번호의 기존 회원이 있으면 → 계정 통합 경로
        Optional<User> existingUserOpt = userRepository.findByUserPhone(finalPhoneNumber);
        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();

            // 이미 같은 provider가 붙어 있으면 그냥 로그인 처리
            if (userSnsAccountRepository.existsByUserUidAndProvider(existingUser.getUid(), request.getProvider())) {
                return issueLoginSession(existingUser.getUid(), request.getRememberMe(), deviceId, osType,
                        request.getProvider(), finalProviderUserId);
            }

            // 본인 검증: 입력 이름과 기존 계정 이름이 다르면 차단 (타인 번호로 연동 시도 방지)
            if (!request.getName().trim().equals(existingUser.getUserName().trim())) {
                throw new AuthException(AuthErrorCode.OAUTH_NAME_MISMATCH);
            }

            // 계정 통합: 기존 회원에 현재 SNS 계정을 연결 (예: 기존 카카오 회원에게 구글 추가)
            userSnsAccountRepository.save(
                    UserSnsAccount.create(existingUser, request.getProvider(), finalProviderUserId));

            // 보유 권한 그대로, 선택 권한은 항상 USER로 시작해 세션 발급
            List<UserRole> ownedRoles = userRoleGrantRepository.findAllByUid(existingUser.getUid()).stream()
                    .map(UserRoleGrant::getRoleType).distinct().toList();
            String accessToken = jwtTokenProvider.generateAccessToken(
                    existingUser.getUid(), existingUser.getUserId(), ownedRoles, UserRole.USER);
            // ... 디바이스 갱신 / refreshToken 발급 / lastLogin 기록 ...

            return OAuthLoginResponse.builder()
                    .signupRequired(false)
                    .isAccountLinked(true)          // 프론트에서 "계정 통합됨" 안내용 신호
                    .accessToken(accessToken)
                    .provider(request.getProvider().name())
                    .providerUserId(finalProviderUserId)
                    .build();
        }

        // 5) 신규 가입 경로
        //    - 닉네임/이메일 중복 검증
        //    - 추천인 코드 검증 (자기추천 방지, 탈퇴·잠김 계정 코드는 "코드 없음"과 동일 에러로 통합)
        //    - 이름·생년월일·CI/DI는 PASS 결과를 진실값으로 사용, 성별만 사용자 입력값 사용
        //    - User 생성 후 USER 권한 부여 + 가입 보상 지급
        // ... (생략) ...
        return /* 신규 가입 세션 응답 */ null;
    }
}
