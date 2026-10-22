# 딩그로(Dingrow) 출시 체크리스트

버전 0.1.0 (versionCode / 빌드 번호 1). 스토어 계정, 결제 프로필, 배포용 인증서는 아직 없습니다. 아래는 사람이 해야 하는 일입니다.

## 이미 저장소에 있는 것

- Android `compileSdk` / `targetSdk` 36. 2026-08-31부터 Play에 올리는 새 앱과 업데이트는 Android 16 (API 36)을 타깃으로 해야 합니다. AGP 8.7.3은 35까지 검증되었으므로 `gradle.properties`에 `android.suppressUnsupportedCompileSdk=36`을 두었습니다. 가능하면 AGP를 API 36을 공식 지원하는 버전으로 올린 뒤 이 억제를 빼세요.
- 릴리스 빌드는 R8(`isMinifyEnabled`)과 리소스 축소가 켜져 있습니다. 서명은 `DINGROW_STORE_FILE`, `DINGROW_STORE_PASSWORD`, `DINGROW_KEY_ALIAS`, `DINGROW_KEY_PASSWORD`가 환경 변수나 `-P`로 있을 때만 붙습니다. 예전 이름 `MONGLE_STORE_FILE`, `MONGLE_STORE_PASSWORD`, `MONGLE_KEY_ALIAS`, `MONGLE_KEY_PASSWORD`도 값이 비어 있지 않으면 그대로 읽습니다. 키스토어 파일은 커밋하지 않습니다 (`.gitignore`의 `*.jks`, `*.keystore`).
- `./gradlew :composeApp:bundleRelease`로 AAB를 만듭니다. 키가 없으면 서명되지 않은 AAB가 나오고, Play App Signing에 올리려면 서명이 필요합니다.
- 버전은 `-PDINGROW_VERSION_CODE`와 `-PDINGROW_VERSION_NAME`으로 덮어쓸 수 있습니다. `MONGLE_VERSION_CODE`, `MONGLE_VERSION_NAME`도 폴백입니다. 기본은 1 / 0.1.0입니다.
- iOS 번들 `app.dingrow`, 위젯 `app.dingrow.widget`, App Group `group.app.dingrow`. 마케팅 버전과 빌드 번호는 `iosApp/Configuration/Config.xcconfig`의 `MARKETING_VERSION`, `CURRENT_PROJECT_VERSION`입니다. 릴리스는 Swift `-O`, whole module, dSYM입니다. 기기 표시 이름은 영어 Dingrow, 한국어 딩그로입니다.
- `.github/workflows/ios.yml`은 macOS에서 스킴 `iosApp`(앱 + 위젯)을 시뮬레이터용으로 서명 없이 빌드합니다. 이 Linux 환경에서는 실행하지 않았습니다.
- 개인정보 처리방침: `store/privacy.md`, `store/privacy.html`
- 스토어 문구: `store/listing-ko.md`, `store/listing-en.md`
- Play 데이터 보안과 정확한 알람 선언 초안: `store/play-data-safety.md`
- 아이콘: Android 적응형·밀도별 mipmap, iOS `AppIcon` 1024, Play용 `store/icon-512.png`, 마스터 `store/icon-1024.png`
- 스토어 그래픽: `store/graphics/`. Play 그래픽 이미지 1024×500, Play 휴대전화 6장 1080×1920, App Store 6.7인치 6장 1290×2796.

## Google Play

1. Play Console 개발자 계정을 만들고 등록비를 냅니다. 계정은 이 저장소에서 만들지 않았습니다.
2. 앱을 만들고 패키지 이름을 `app.dingrow`로 고정합니다. 한 번 올리면 바꿀 수 없습니다. 한국어 제목은 `딩그로(Dingrow)`, 짧은 설명은 `할 일 하면 자라는 위젯 친구`로 시작합니다. 영어 제목은 `Dingrow - Tasks help you grow`입니다.
3. 업로드 키를 만듭니다. 예시는 비밀번호를 저장소에 남기지 마세요.

   ```bash
   keytool -genkeypair -v -keystore dingrow-upload.jks -alias dingrow \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

4. Play App Signing에 가입하고 업로드 키로 서명한 AAB를 올립니다.

   ```bash
   export DINGROW_STORE_FILE="$PWD/dingrow-upload.jks"
   export DINGROW_STORE_PASSWORD="..."
   export DINGROW_KEY_ALIAS="dingrow"
   export DINGROW_KEY_PASSWORD="..."
   ./gradlew :composeApp:bundleRelease
   ```

   결과: `composeApp/build/outputs/bundle/release/composeApp-release.aab`
5. 스토어 등록정보 한국어는 `store/listing-ko.md`, 영어는 `store/listing-en.md`에서 붙여 넣습니다. 아이콘은 `store/icon-512.png`입니다. 아이콘에는 글자가 없습니다. 휴대전화 스크린샷은 출시 기기에서 홈, 일정, 방, 도감을 다시 찍습니다. 이 저장소의 Robolectric 캡처는 검토용 대용입니다.
6. 개인정보 처리방침 URL에 `store/privacy.html`을 호스팅한 주소를 넣습니다. 정적 호스팅이 필요합니다.
7. 데이터 보안 양식을 `store/play-data-safety.md`대로 “수집 안 함”으로 제출합니다.
8. 앱 콘텐츠: 정확한 알람 선언, 광고 없음, 콘텐츠 등급 설문(전체이용가 예상), 타깃 연령, 뉴스 앱 아님, 정부 앱 아님.
9. 국가, 가격(무료), 출시 트랙(내부 테스트 → 비공개 → 프로덕션)을 고릅니다.
10. 결제 상품은 없습니다. 나중에 상점을 넣으면 Play Billing 계정과 상품 ID가 필요합니다. 지금은 코인이 기기 안에만 있습니다.

## App Store

1. Apple Developer Program에 가입합니다. Team ID를 `iosApp/Configuration/Config.xcconfig`의 `TEAM_ID`에 넣습니다. 비어 있으면 기기 빌드와 업로드가 서명에서 실패합니다. CI의 시뮬레이터 빌드는 `CODE_SIGNING_ALLOWED=NO`라서 팀이 없어도 컴파일 확인이 됩니다.
2. 개발자 사이트에서 번들 ID `app.dingrow`와 `app.dingrow.widget`을 만들고, 둘 다 App Groups `group.app.dingrow`를 켭니다. 위젯은 iOS 17 이상입니다. 한국어 표시 이름은 딩그로, 영어는 Dingrow입니다. App Store 부제는 `할 일 하면 자라는 위젯 친구`입니다.
3. Mac에서 `iosApp/iosApp.xcodeproj` 스킴 `iosApp`으로 아카이브합니다. 위젯 익스텐션이 같이 들어갑니다. 인증서와 프로비저닝 프로파일은 Xcode 자동 서명이 만듭니다.
4. App Store Connect에 앱을 만들고 개인정보 영양 라벨을 “데이터 수집 안 함”으로 채웁니다. 개인정보 처리방침 URL은 Play와 같은 페이지를 씁니다.
5. 스크린샷(6.7인치, 6.1인치)과 1024 아이콘을 올립니다. 아이콘에 투명 픽셀이 있으면 심사가 거절하므로 `store/icon-1024.png`처럼 배경이 채워진 파일을 씁니다.
6. 수출 규정: 암호화는 HTTPS를 쓰지 않고 표준 암호화만 해당하면 면제 질문에서 예외에 해당합니다. 출시 전에 최신 질문을 다시 읽으세요.
7. 심사 노트에 계정 없이 바로 펫 이름을 정하고 쓴다는 점, 위젯과 알림은 선택이라는 점을 적습니다.

## 출시 전에 한 번 더

- [ ] 업로드 키와 Apple 인증서를 비밀번호 관리자에 보관하고, 저장소와 CI 로그에 넣지 않았는지 확인
- [ ] `versionCode` / `CURRENT_PROJECT_VERSION`을 이전 업로드보다 크게
- [ ] 개인정보 처리방침 URL이 공개로 열리는지
- [ ] 정확한 알람을 끈 기기와 알림을 거부한 기기에서 일정이 저장되는지만 확인
- [ ] 내부 테스트 트랙에 AAB를 올려 위젯, 알람, 배경화면을 실제 런처에서 확인
- [ ] iOS는 `.github/workflows/ios.yml`이 녹색인지, 그다음 TestFlight에 올렸는지
