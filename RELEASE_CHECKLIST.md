# 딩그로(Dingrow) 출시 체크리스트

> 개발자 계정 인증이 끝나면 아래 순서대로 진행하세요. ✅ = 준비 완료(박스/저장소), ☐ = 직접 할 일.
> 관련 문서: `docs/STORE_SETUP.md`(결제·로그인 상세), `docs/store/*.md`(등록 문구·설문 초안), 이미지: `/workspace/release/store/`.

## 0. 이미 준비된 것
- ✅ 업로드 키: `/workspace/secrets/dingrow-upload.jks` (별칭 `upload`, 비밀번호 `/workspace/secrets/dingrow-upload.password`, 권한 600, 저장소 밖)
  - SHA-1 `DA:66:49:13:83:AF:5B:CC:F8:1A:2B:C2:19:1B:A4:DA:23:19:03:75`
- ✅ 서명된 릴리스 AAB: `/workspace/release/dingrow-release.aab` (versionCode 1, versionName 0.1.0, R8 축소 적용)
- ✅ 릴리스 빌드는 가짜 결제·가짜 로그인 비활성(`ReleaseConfigTest`)
- ✅ 인앱 상품 코드: `coins_100` / `coins_1000` (Play Billing: 지급 후 소비, 대기 결제, 미지급 복구 / StoreKit 2)
- ✅ 개인정보처리방침(KO/EN): `docs/privacy/index.html` → GitHub Pages
- ✅ 스토어 문구·스크린샷·아이콘·그래픽 이미지·데이터 보안/등급 설문 초안

## 1. 꼭 먼저 할 일 (공통)
1. ☐ **업로드 키 백업**: `/workspace/secrets/` 의 `.jks`와 비밀번호를 비밀번호 관리자/개인 클라우드에 따로 보관. (박스 밖에 사본이 없으면 분실 위험)
2. ☐ **문의 이메일 정하기** → `docs/index.html`, `docs/privacy/index.html`, `docs/store/*.md` 의 `CONTACT_EMAIL` 을 바꾸고 커밋.
3. ☐ **GitHub Pages 켜기**: GitHub 저장소 → Settings → Pages → Source "Deploy from a branch", Branch `main`, 폴더 `/docs` → 저장.
   - 저장소가 비공개면 Pages는 유료 플랜에서만 공개됩니다. 그 경우 `docs/privacy`만 별도 공개 저장소에 올리세요.
   - 확인: https://min2h.github.io/dingrow/privacy/ 가 열리는지.

## 2. Google Play Console
### 2-1. 앱 만들기
1. ☐ 모든 앱 → 앱 만들기: 이름 `딩그로(Dingrow) - 몽이와 함께하는 일정 플래너`, 기본 언어 한국어, 앱, 무료.
2. ☐ 앱 무결성 → **Play 앱 서명 사용**(기본값 유지).

### 2-2. 앱 콘텐츠 (정책)
1. ☐ 개인정보처리방침 URL 입력.
2. ☐ 광고: 없음.
3. ☐ 앱 액세스 권한: "모든 기능을 제한 없이 이용 가능"(게스트 로그인).
4. ☐ 콘텐츠 등급: `docs/store/content-rating.md` 답변대로.
5. ☐ 타겟층: 13세 이상(아동 대상 아님).
6. ☐ 데이터 보안: `docs/store/data-safety.md` 답변대로, 삭제 URL `…/privacy/#delete`.
7. ☐ 권한 신고:
   - **전체 화면 인텐트(USE_FULL_SCREEN_INTENT)**: "알람/일정 알림" 용도로 신고. 거절되면 저에게 말해 주세요 → 매니페스트에서 제거 후 재빌드.
   - 정확한 알람: 사용자가 설정한 일정 알림.

### 2-3. 스토어 등록정보
1. ☐ 한국어/영어: `docs/store/listing.md` 문구 붙여넣기 (영어는 "번역 추가 → 영어(미국)").
2. ☐ 이미지: 아이콘 `graphics/icon-512.png`, 그래픽 이미지 `graphics/feature-graphic-1024x500.png`, 휴대전화 스크린샷 `screens/ko/*`(한국어), `screens/en/*`(영어).
3. ☐ 카테고리: 생산성, 연락처 이메일·웹사이트.

### 2-4. 비공개 테스트 (개인 개발자 계정 필수: 테스터 12명 × 14일)
1. ☐ 테스트 및 출시 → 테스트 → **비공개 테스트** → 트랙 만들기(예: `closed-alpha`).
2. ☐ 테스터: 이메일 목록(Google 그룹 권장) 만들고 **최소 12명** 추가. (가족·지인 Gmail, 안드로이드 폰 필요)
3. ☐ 새 버전 만들기 → `/workspace/release/dingrow-release.aab` 업로드 → 출시 노트(한/영) → 검토 후 출시.
4. ☐ 테스터에게 참여 링크(웹/Play) 전달 → 각자 **옵트인 후 설치**, **14일 연속** 참여 유지(앱 삭제·옵트아웃하면 다시 셈).
5. ☐ 업로드 후 앱 무결성 → **앱 서명 키 SHA-1** 복사 → Google Cloud OAuth Android 클라이언트에 추가(3번 참고).
6. ☐ 14일 지나면 대시보드 → "프로덕션 액세스 신청" → 설문(테스트 소감·변경 사항) 제출 → 심사(보통 7일 이내).

### 2-5. 인앱 상품 (AAB 업로드 이후 가능)
1. ☐ 수익 창출 → 제품 → 인앱 상품:
   - `coins_100` · 코인 100개 / 100 Coins · US$1.99 → 활성화
   - `coins_1000` · 코인 1,000개 / 1,000 Coins · US$16.99 → 활성화
2. ☐ 결제 프로필(판매자) + 세금 정보 + 정산 계좌.
3. ☐ 설정 → 라이선스 테스트: 내 Gmail·테스터 추가 → 실제 청구 없이 구매/대기 결제 테스트.

### 2-6. 다음 버전 올릴 때
```bash
source ~/sdk/env.sh && source /workspace/secrets/dingrow-signing.env
DINGROW_VERSION_CODE=2 DINGROW_VERSION_NAME=0.1.1 ./gradlew :composeApp:bundleRelease
```
versionCode는 매번 1씩 올려야 합니다.

## 3. Google 로그인 (Google Cloud Console)
1. ☐ 프로젝트 만들기 → OAuth 동의 화면(외부, 앱 이름 딩그로, 지원 이메일, 개인정보처리방침 URL) → **게시(프로덕션)**.
2. ☐ 사용자 인증 정보 → OAuth 클라이언트 ID:
   - Android, 패키지 `app.dingrow`, SHA-1 `DA:66:49:13:83:AF:5B:CC:F8:1A:2B:C2:19:1B:A4:DA:23:19:03:75` (업로드 키)
   - Android, 패키지 `app.dingrow`, SHA-1 = Play 앱 서명 키 SHA-1 (2-4의 5번)
   - **웹 애플리케이션** → 생성된 클라이언트 ID를 저에게 알려주세요(또는 `composeApp/src/androidMain/res/values/auth.xml` 의 `REPLACE_WITH_WEB_CLIENT_ID` 교체). 그 전까지 Google 버튼은 "준비 중" 안내, 게스트 로그인 정상.

## 4. App Store Connect (Apple)
1. ☐ Apple Developer → Certificates, IDs & Profiles → Identifiers:
   - `app.dingrow` 생성: **Sign In with Apple**, **App Groups**(`group.app.dingrow`), **In-App Purchase**, Push 불필요.
   - 위젯 확장 ID(Xcode의 PetWidget 타깃 번들 ID)도 생성 + App Groups.
2. ☐ 계약/세금/금융: "유료 앱" 계약 동의 + 은행·세금 정보 (없으면 인앱 상품이 로드되지 않음).
3. ☐ 나의 앱 → 새로운 앱: 플랫폼 iOS, 이름 `딩그로(Dingrow)`, 기본 언어 한국어, 번들 ID `app.dingrow`, SKU `dingrow-ios`.
4. ☐ 앱 정보/가격/개인정보: `docs/store/appstore-connect.md` 그대로(개인정보처리방침 URL, 카테고리, 연령 등급, **앱 개인정보 보호 라벨**).
5. ☐ 앱 내 구입 → 소비성 2개(`coins_100` $1.99, `coins_1000` $16.99), 현지화(ko/en), 심사용 스크린샷(코인 상점 화면).
6. ☐ Mac에서 Xcode로 빌드 (박스에는 Mac이 없어 iOS는 Kotlin 컴파일까지만 확인됨):
   - `iosApp/iosApp.xcodeproj` 열기 → Signing에서 팀 선택(두 타깃 모두).
   - **`iosApp/iosApp/PrivacyInfo.xcprivacy` 를 iosApp 타깃에 추가**(Add Files → Target 체크).
   - 로컬 결제 테스트: Scheme → Run → Options → StoreKit Configuration = `iosApp/Dingrow.storekit`.
   - Product → Archive → Distribute → App Store Connect 업로드.
7. ☐ TestFlight: 내부 테스터(최대 100명, 심사 없음)로 실제 기기 확인 → Sandbox 계정으로 구매·Apple 로그인 테스트.
8. ☐ 버전 페이지: 스크린샷 `screens/appstore-6.9/{ko,en}` (6.9" 1290×2796), 설명·키워드·부제·지원 URL, 빌드 선택, 인앱 상품 첨부, 심사 메모("게스트로 시작" 안내) → **심사 제출**.

## 5. 출시 전 최종 확인 (실기기)
- ☐ 첫 설치 → 로그인 화면 → 게스트/Google/Apple 로그인 → 재실행 시 자동 로그인 → 로그아웃 시 로그인 화면
- ☐ 일정 추가/수정/완료, 알림(정시·미리 알림), 재부팅 후 알림 유지
- ☐ 코인 구매(라이선스 테스터/Sandbox): 즉시 승인·대기 후 승인·거절, 앱 강제 종료 후 재실행 시 지급
- ☐ 위치 끈 상태 → 날씨 없음 / 켠 상태 → 날씨 표시
- ☐ 위젯 3종, 라이브 배경화면, 다크 모드, 한/영 전환
- ☐ 설정 → 데이터 → 몽이 처음부터 다시 → 데이터 삭제 확인

## 6. 알려진 제한 / 결정 필요
- 서버 저장·구매 검증은 Firebase(FIREBASE_SETUP.md). 출시 전 Firebase 프로젝트 생성, google-services.json / GoogleService-Info.plist 배치, Functions 배포, Play 서비스 계정·App Store 키 설정을 마쳐야 합니다. 미설정 시 앱은 기기 저장만으로 동작합니다.
- 코인은 기기에 저장 → 앱 삭제 시 사라짐(백업 내보내기/복원으로만 이동). 스토어 설명·개인정보처리방침에 명시됨.
- 릴리스 AAB는 박스에 KVM 권한이 없어 에뮬레이터 실행 확인을 못 했습니다 → **비공개 테스트 트랙 첫 설치로 실행 확인** 필요.
- 등급: 음주 관련 문구를 모두 '저녁약속'으로 바꿔 전체이용가/4+ 목표(docs/store/content-rating.md).
