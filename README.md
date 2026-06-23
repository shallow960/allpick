# AllPick — 인증 / 권한 / 결제 모듈 (포트폴리오 발췌)

리워드 기반 커머스 앱 **AllPick**(3인 팀 프로젝트, Spring Boot + React Native)에서
제가 단독으로 담당한 **인증·인가 도메인**과 **기프티콘(결제성) 모듈**의 핵심 코드 발췌본입니다.

> 모든 키·제휴사 인증정보는 제거 했으며, 단독 빌드는 되지 않습니다.
> 탐색 편의를 위해 폴더는 기능별로 평평하게 정리했고, 각 파일의 `package` 선언은 원본(실제 프로젝트) 구조를 그대로 유지했습니다.
> **코드의 의도와 기술적 의사결정을 보여주기 위한 자료**입니다.

---

## 다룬 문제 (트러블슈팅)

### 1. 소셜 로그인 토큰 검증 — 애플 계정 탈취 취약점 차단
소셜 로그인 4종(카카오·네이버·구글·애플)의 토큰 검증 방식을 점검하던 중,
애플만 JWT **서명 검증 없이** payload를 신뢰하고 있어 위조 토큰으로 계정 탈취가
가능한 상태였습니다. 애플 공개키(JWKS) 기반 서명·iss·aud·exp 검증을 직접 구현해
구글 검증기와 동일한 수준으로 끌어올렸습니다.

- `auth/AppleAuthServiceImpl.java` — JWKS 서명 검증 (핵심)
- `auth/GoogleAuthServiceImpl.java` — 라이브러리 검증기 (비교군)
- `auth/KakaoOauthServiceImpl.java`, `auth/NaverAuthServiceImpl.java` — 서버 직접 호출 방식

### 2. 다중 역할 + 3계층 관리자 권한 구조
한 사용자가 여러 역할(일반 회원·매장 사장·셀러 등)을 동시에 갖게 되면서, 단일 role
클레임으로는 권한을 표현할 수 없는 문제가 생겼습니다. JWT를 **authorities(보유 권한
전체, 인가용)** 와 **currentRole(선택 역할, UI 분기용)** 로 분리하고, authorities에는
`ROLE_USER`를 항상 포함해 어떤 역할이든 기본 기능 접근이 끊기지 않게 했습니다.

관리자 권한은 한 단계 더 들어가, **역할 → 티어 도출 → 세분화 기능 권한**의 3계층으로
설계했습니다. `UserRole` 조합으로 `AdminTier`(1·2·3차)를 도출하고, 1·2차는 모든 권한을
암묵 보유, 3차(직원)는 `ap_admin_permissions`에 부여된 기능 권한만 접근합니다. 매 요청
호출되는 핫패스라 1·2차는 권한 테이블 조회를 건너뛰도록 최적화했습니다.

- `auth/JwtTokenProvider.java` — authorities/currentRole 분리 (핵심)
- `auth/JwtAuthenticationFilter.java` — 토큰 → 권한 주입
- `role/UserRole.java`, `role/UserRoleGrant.java` — 역할 체계 / 다중 부여
- `role/AdminTier.java`, `role/AdminPermission.java` — 티어 / 기능 권한 정의
- `role/AdminScopeService.java` — 역할 조합 → 티어 도출
- `role/AdminPermissionEvaluator.java` — `@PreAuthorize` SpEL 권한 평가 (핵심)

### 3. SNS 회원가입 — PASS 본인인증으로 신원 통일
SNS 회원가입 시 신원 정보(이름·전화번호 등)를 프론트 입력에 의존하면, 타인의 전화번호로
SNS 계정을 붙여 계정을 가로챌 여지가 있었습니다. 카카오·네이버·구글·애플 **모든 provider를
PASS 본인인증으로 통일**해, 이름·전화·생년·CI/DI를 PASS 결과에서만 받도록 했습니다.
재가입 차단도 전화번호가 아닌 **본인확인 CI 해시 기준 30일**로 처리하고, 같은 전화번호의
기존 회원이 있으면 이름 일치 검증 후 **계정 통합**으로 분기합니다.

본인인증 자체는 PortOne(아임포트) V1을 경유한 PASS로 처리했습니다. 본인인증 단계와 가입
단계를 **`verifyKey`(Redis 5분 단명 1회용 토큰)** 로 분리해 인증 결과 재사용·위조를 막고,
응답의 `merchant_uid` 일치 검증으로 타 거래 결과 도용을 차단했습니다.

- `auth/AuthServiceImpl.java` — oauthSignup 핵심 흐름 (발췌)
- `auth/PortOneIdentityService.java` — PASS 검증 + verifyKey 발급/소비 (핵심)
- `auth/PortOneApiClient.java` — PortOne V1 API 호출
- `auth/PhoneVerifyResult.java` — 본인인증 결과 모델

### 4. 기프티콘 구매 실패 시 거래 로그 보존
구매 실패 시 다이아 차감은 트랜잭션 롤백으로 자동 복구되지만, 같은 트랜잭션에 있던
**실패 거래 로그까지 함께 롤백**돼 운영·CS 추적이 불가능했습니다. 실패 로그 저장만
`REQUIRES_NEW`로 분리(별도 빈)해 메인 트랜잭션과 독립 커밋되도록 했습니다.

- `gifticon/GifticonTxLogWriter.java` — 독립 트랜잭션 로그 기록 (핵심)
- `gifticon/GifticonCouponServiceImpl.java` — 구매 트랜잭션 흐름

---

## 기술 스택
Java 17, Spring Boot, Spring Security, JWT(jjwt), JPA, PostgreSQL, Redis

## 구조
```
auth/       인증·인가 (OAuth 4종, JWT, Security, PASS 본인인증)
role/       역할 + 관리자 3계층 권한 (다중 역할, 티어 도출, 기능 권한 평가)
gifticon/   기프티콘 구매·쿠폰 (트랜잭션)
application-example.yml   설정 예시 (키는 placeholder)
```
