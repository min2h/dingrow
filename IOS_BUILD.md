# 딩그로 iOS 빌드·실행 가이드 (집 Mac용)

> 이 저장소의 iOS 부분은 Linux에서 **Kotlin 코드까지만** 컴파일 검증했습니다
> (`:shared:compileKotlinIosArm64`, `:shared:compileKotlinIosSimulatorArm64`, `:composeApp:compileKotlinIosArm64` 성공).
> 프레임워크 링크, Swift/SwiftUI/WidgetKit 컴파일, 서명, 실기기 실행은 **Mac + Xcode에서만** 됩니다. 아래 순서대로 하시면 돼요.

## 0. 준비물
- macOS 14 이상 Mac (Apple Silicon 권장)
- Apple ID, 그리고 TestFlight/실배포까지 하려면 **Apple Developer Program**(연 $99) 가입된 팀
- 아이폰(iOS 17 이상), USB-C/라이트닝 케이블

## 1. 도구 설치
1. App Store에서 **Xcode 16** 이상 설치 → 한 번 실행해서 추가 구성요소 설치를 끝냅니다.
2. 터미널에서:
   ```bash
   xcode-select --install          # 커맨드라인 도구
   sudo xcodebuild -license accept
   brew install openjdk@17 git      # Homebrew가 없다면 https://brew.sh 먼저
   echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 17)' >> ~/.zshrc && source ~/.zshrc
   ```
3. 저장소 받기:
   ```bash
   git clone https://github.com/<계정>/dingrow.git && cd dingrow
   ```

## 2. Kotlin 공유 코드 먼저 확인 (선택이지만 권장)
```bash
./gradlew :shared:compileKotlinIosSimulatorArm64 :composeApp:compileKotlinIosSimulatorArm64
```
Mac에서는 iOS 타깃이 자동으로 켜집니다(Linux에서는 `-Pdingrow.ios=true -Pkotlin.native.enableKlibsCrossCompilation=true` 를 줘야 함).
처음엔 Kotlin/Native 툴체인 다운로드 때문에 5~10분 걸릴 수 있어요.

## 3. Xcode 프로젝트 열기
```bash
open iosApp/iosApp.xcodeproj
```
- 타깃은 두 개: **iosApp**(앱) / **PetWidget**(WidgetKit 위젯).
- 빌드할 때 Xcode의 "Run Script" 단계가 Gradle로 `ComposeApp` 프레임워크를 만들어 붙입니다(`embedAndSignAppleFrameworkForXcode`). 첫 빌드는 오래 걸려요.

## 4. 서명 설정 (내 개발자 팀)
1. `iosApp/Configuration/Config.xcconfig` 를 엽니다:
   ```
   TEAM_ID=ABCDE12345            ← 내 팀 ID (developer.apple.com → Membership에서 확인)
   BUNDLE_ID=app.dingrow         ← 이미 다른 사람이 쓰고 있으면 com.내이름.dingrow 처럼 바꾸기
   WIDGET_BUNDLE_ID=app.dingrow.widget   ← 반드시 BUNDLE_ID + ".widget"
   ```
2. Xcode → 프로젝트 → **iosApp 타깃 → Signing & Capabilities**
   - "Automatically manage signing" 체크, Team 선택
   - Capabilities 확인/추가: **App Groups** (`group.app.dingrow`), **Sign in with Apple**, **Push Notifications는 불필요**(로컬 알림만 사용)
   - In‑App Purchase capability 추가(StoreKit 2 코인 팩)
3. **PetWidget 타깃**도 같은 Team, **App Groups `group.app.dingrow`** 체크.
   - 번들 ID를 바꿨다면 App Group 이름도 `group.<내 번들ID>` 로 바꾸고, `iosApp/Shared/MongleKit.swift` 의 `AppGroup.identifier`, 두 `.entitlements` 파일을 같은 값으로 맞춰 주세요. (앱과 위젯이 같은 DB를 공유하는 통로예요.)

## 5. 시뮬레이터에서 실행
1. 상단 기기 선택 → **iPhone 16 (iOS 18)** 등 → ▶︎ Run (⌘R)
2. 확인할 것:
   - 스플래시 → 로그인(게스트 / Apple로 로그인) → 홈
   - 설정 → 위치 켜기 → 권한 팝업 허용 → 날씨칩·"지금 11도, 맑아요 / 오후에 비 와요" 대사
     (시뮬레이터 메뉴 Features → Location → Apple 등으로 위치 지정)
   - 일정 추가(장소 포함) → 알림 권한 허용 → 알림 도착/액션(확인·5분 뒤·완료)
   - 홈 화면 길게 누르기 → 위젯 추가 → "딩그로의 방"(작게/중간/크게): 말풍선(일정 굵은 강조), 배부름·컨디션, 오늘 일정 목록
   - 위젯의 일정 줄 탭 → 앱이 열리며 그 일정이 강조됨 (`dingrow://schedule/<id>`)
   - 터미널로 딥링크 테스트: `xcrun simctl openurl booted "dingrow://schedule/add"`

## 6. 내 아이폰에서 실행
1. 아이폰을 Mac에 연결 → "이 컴퓨터를 신뢰" → 아이폰 **설정 → 개인정보 보호 및 보안 → 개발자 모드 켜기**(재부팅)
2. Xcode 기기 목록에서 내 아이폰 선택 → ▶︎ Run
3. 무료 Apple ID로도 7일간 설치는 되지만, **App Groups/Sign in with Apple/IAP는 유료 개발자 팀이 필요**합니다.

## 7. 코인 팩(StoreKit 2) 테스트
- App Store Connect → 앱 → **앱 내 구입**에 소모성 상품을 만들고, 제품 ID를 공유 설정(`shared/.../logic/Care.kt`의 `StoreProducts`)과 똑같이 맞춥니다.
- 로컬 테스트: Xcode → File → New → **StoreKit Configuration File** → 같은 ID로 상품 추가 → Scheme → Run → Options → StoreKit Configuration 선택.
- 가격은 앱이 `Product.displayPrice`(현지 통화)로 표시합니다. 구매 검증 후 `MongleBridge.grantPurchase(productId, transactionId)`가 코인을 지급하고 거래 ID로 중복 지급을 막아요.

## 8. TestFlight 업로드
1. `Config.xcconfig`/타깃의 **Version(MARKETING_VERSION)**, **Build(CURRENT_PROJECT_VERSION)** 올리기
2. 기기 선택을 **Any iOS Device (arm64)** 로 → 메뉴 **Product → Archive**
3. Organizer 창 → **Distribute App → App Store Connect → Upload**
4. App Store Connect → 내 앱 → **TestFlight** 탭 → 빌드 처리(10~30분) 후 수출 규정 질문 답변(암호화: "표준 암호화만 사용/면제")
5. 내부 테스터에 내 Apple ID 추가 → 아이폰의 **TestFlight 앱**에서 설치

## 9. 출시 전에 남은 일 (정직한 목록)
- 로그인 세션을 `NSUserDefaults` → **Keychain**으로 옮기기 (`composeApp/src/iosMain/.../IosAuth.kt`)
- Sign in with Apple은 사용자 ID만 받는 **스텁**입니다. 서버 검증(identityToken)은 없어요.
- iOS에는 라이브 배경화면 개념이 없어 해당 기능은 Android 전용입니다.
- 위젯 말풍선은 SwiftUI 레이아웃으로 몽이 머리 위에 쌓아 올리는 방식(꼬리 아래로)이고, Android처럼 걸어다니는 위치를 따라가진 않습니다(위젯은 정지 화면).
- Swift 코드(위치·StoreKit·Apple 로그인·v4 위젯)는 Linux에서 컴파일해 볼 수 없어서 **Mac 첫 빌드 때 사소한 컴파일 오류가 날 수 있어요.** 오류 메시지를 알려 주시면 바로 고칠게요.

## 문제 해결
- `No such module 'ComposeApp'` → 한 번 ⌘B로 빌드(Run Script가 프레임워크 생성) / `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` 로그 확인
- 위젯이 빈 화면 → 앱을 한 번 실행해서 DB 생성, 두 타깃의 App Group 값이 같은지 확인
- 서명 오류 "No profiles" → Team 선택, 번들 ID 중복 여부 확인

---

## Mac 없이 빌드하기 (GitHub Actions)

### 1) 시뮬레이터 빌드 — `.github/workflows/ios.yml`
- 실행 조건: `main`에 `iosApp/**`, `shared/**`, `composeApp/src/{commonMain,iosMain}/**`, gradle 파일이 바뀐 push가 들어올 때, 또는 Actions 탭에서 **Run workflow**(workflow_dispatch)를 눌렀을 때.
- 하는 일: KMP 프레임워크 빌드(arm64 시뮬레이터 전용) → SPM resolve → `xcodebuild` (Debug, iphonesimulator, 서명 없음) → `Dingrow-sim.zip`.
- 결과물 위치:
  - Actions 아티팩트 `Dingrow-simulator`
  - 롤링 pre-release **`ios-sim`** (매 빌드마다 새로 덮어씀). Actions 권한이 없는 토큰으로도 받을 수 있게 하려고 둔 경로입니다.
- Firebase는 선택 사항입니다. `GoogleService-Info.plist`가 없으면 Firebase 초기화를 건너뛰니(`iOSApp.swift`), plist 없이도 빌드됩니다.
- 캐시: Gradle(setup-gradle), `~/.konan`(빌드가 실패해도 저장), SPM.
- Appetize 자동 업로드(선택): 저장소 secret `APPETIZE_API_TOKEN`과 variable `APPETIZE_IOS_KEY`(기존 iOS 앱 publicKey)가 **둘 다** 있을 때만 실행되고, 그 앱을 갱신합니다.

### 2) TestFlight 업로드 — `.github/workflows/ios-testflight.yml` (수동 실행 전용)
Apple Developer Program 가입 후에 쓸 수 있습니다. fastlane/match 없이 **App Store Connect API 키 + Xcode 자동 서명**을 사용합니다.

준비:
1. App Store Connect → 사용자 및 액세스 → 통합 → **App Store Connect API** → 키 생성 (역할: App Manager 이상). `.p8` 파일은 한 번만 내려받을 수 있습니다.
2. App Store Connect에 번들 ID `app.dingrow`(위젯 `app.dingrow.widget`)로 앱을 등록하고, App Group `group.app.dingrow`를 만듭니다. 자동 서명이 프로필은 만들어 주지만, 앱 레코드는 직접 만들어야 업로드가 됩니다.
3. 저장소 Settings → Secrets and variables → Actions에 아래 secret을 추가합니다.

| Secret | 내용 |
|---|---|
| `ASC_KEY_ID` | API 키 ID (예: `ABC123DEFG`) |
| `ASC_ISSUER_ID` | Issuer ID (UUID) |
| `ASC_KEY_P8` | `.p8` 파일 내용 전체 (`-----BEGIN PRIVATE KEY-----` 포함) |
| `TEAM_ID` | Apple 팀 ID (10자리) |
| `GOOGLE_SERVICE_INFO_PLIST_B64` | (선택) `base64 -i GoogleService-Info.plist` 결과 |

실행: Actions → **iOS TestFlight** → Run workflow → `build_number`를 입력합니다. 이전 업로드보다 큰 숫자여야 합니다.
워크플로가 하는 일:
1. p8을 `~/.appstoreconnect/private_keys`에 씁니다.
2. `Config.xcconfig`에 TEAM_ID와 빌드 번호를 넣습니다.
3. `xcodebuild archive -allowProvisioningUpdates`(API 키 인증, 자동 서명)를 실행합니다.
4. `-exportArchive`(method `app-store-connect`, destination `upload`)로 TestFlight에 바로 업로드합니다.
5. 끝나면 키를 삭제합니다.

### 참고/제약
- 현재 저장소 푸시용 PAT에는 Actions 권한이 없어서 API로 dispatch하거나 아티팩트를 내려받을 수 없습니다. 그래서 push로 빌드를 트리거하고, 결과는 `ios-sim` 릴리스로 받습니다.
- macOS 러너는 private 저장소에서 분당 10배로 과금됩니다. 경로 필터와 동시 실행 취소(concurrency)로 불필요한 실행을 줄였습니다.
