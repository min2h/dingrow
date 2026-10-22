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
