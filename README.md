# 몽글

홈 화면에 사는 작은 펫이 일정을 알려 주고, 할 일을 마치면 함께 자라는 앱입니다. 화면 문구는 한국어입니다. 펫은 배고파도 죽지 않고, 포만감과 행복, 경험치로 알 → 아기 → 어린이 → 어른이 됩니다. 가끔 외출했다가 선물을 가져오고, 선물을 팔아 코인을 모은 뒤 옷과 가구로 방을 바꿉니다.

패키지 이름은 `app.mongle`, 버전은 0.1.0 (versionCode 1)입니다. 스토어 제출 전에 남은 사람 일은 `RELEASE.md`에 있습니다. 개인정보 처리방침과 스토어 문구는 `store/`에 있습니다.

## 검증 범위

이 작업 환경은 Linux라서 Android만 빌드했습니다. iOS 타깃, 위젯 익스텐션, Xcode 프로젝트는 소스까지 넣어 두었고 Mac에서 한 번도 컴파일하지 않았습니다.

| 항목 | 상태 |
| --- | --- |
| `:shared:jvmTest` (성장, 보상, 외출, 상점, 도감 카탈로그, SQLite 왕복) | Linux에서 통과 |
| `:composeApp:assembleDebug` | Linux에서 통과 |
| `:composeApp:testDebugUnitTest` 7개 (Compose UI, Glance, 알람, 배경화면) | Linux에서 통과. Robolectric 4.14 + Roborazzi |
| 디버그 APK | `composeApp/build/outputs/apk/debug/composeApp-debug.apk` |
| Android 위젯을 실제 런처에 올려 본 것 | 미검증. 화면은 Glance `RemoteViews` 테스트 호스트로 그렸음 |
| Android 12+ 정확한 알람 권한 UX | 미검증. 테스트는 권한이 있는 상태로 알람 시각만 확인 |
| 에뮬레이터 | 이 커널에서 부팅되지 않음. `/dev/kvm`은 열리지만 vCPU 생성이 `kvm_arch_vcpu_create`에서 커널 버그로 멈춤 |
| iOS 앱, WidgetKit, App Intents, 알림 | 미검증. `.github/workflows/ios.yml`이 macOS에서 앱과 위젯을 서명 없이 빌드합니다. 이 Linux에서는 실행하지 않았습니다. |

## 지금 되는 것

- 로컬 저장. 펫, 일정, 먹이, 선물, 코인, 장부, 방 프리셋은 SQLDelight(`mongle.db`)에 있습니다. 계정과 서버는 없습니다.
- 일정 추가, 완료, 10분 미루기, 삭제. 완료하면 경험치와 코인, 행복이 오릅니다. 만든 지 2분 안 된 일정, 10분보다 일찍 완료한 일정, 하루 8번을 넘긴 완료는 보상이 줄어듭니다. 같은 반복 일정을 15초 안에 다시 완료하면 무시합니다.
- 알은 일정 3번을 마치면 부화합니다. 아기에서 어린이는 경험치 50, 어린이에서 어른은 경험치 160입니다. 날짜 게이트 기본값은 0이라 데모에서 바로 자랍니다.
- 포만감은 시간당 4, 행복은 시간당 2씩 줄고, 포만감이 30 미만이면 행복이 시간당 5 줄습니다. 집에 있으면 기력은 시간당 10 회복합니다. 72시간을 넘긴 공백은 72시간만 계산하고, 알은 수치가 줄지 않습니다. 앱을 열 때와 15분마다 게으르게 반영합니다.
- 어린이 이상, 포만감 35 이상, 기력 30 이상, 마지막 외출 후 90분이 지나면 외출할 수 있습니다. 20분 뒤 돌아오고 기력 35를 씁니다. 선물은 떠날 때 정해지고, 돌아올 때 공개됩니다.
- 친구 50종과 물건 50종은 `shared`의 `mongle-manifest.json`에서 만든 `SpeciesCatalog`, `Catalog`입니다. 그림은 코드로 그리지 않고 Compose 리소스의 WebP 일러스트입니다. 기본 몽글(`base`)은 숨쉬기, 눈 깜빡임, 기쁨, 먹기, 잠, 걷기가 있고, 다른 종은 같은 움직임에 정지 그림을 올립니다. 방 배경은 나무, 민트, 밤하늘과 낮·밤입니다. 예전 저장의 id는 불러올 때 매니페스트 id로 바꿉니다. 가격이 0인 복숭아와 달걀은 처음부터 데려올 수 있고, 나머지는 도감에서 사거나 산책에서 만납니다. 오늘의 상점은 먹이만 돌리고, 가구와 옷은 방 그리드에서 삽니다.
- 화면은 홈, 일정, 방 꾸미기, 마이(도감)입니다. 글꼴은 Pretendard Medium이고 SIL Open Font License입니다.
- 상점의 오늘 상품은 날짜마다 바뀌고, 이미 가진 단일 아이템은 건너뜁니다. 위젯에서 사면 45초 안에 한 번 더 눌러 확정합니다. 앱 안에서는 한 번에 삽니다.
- Android 작은 위젯(오늘의 기분, 다음 일정, 완료)과 큰 위젯(아늑한 방, 밥주기, 선물 팔기, 오늘의 상품, 꾸미기). 일정 추가는 `mongle://schedule/add`로 앱의 작성 화면을 엽니다.
- 일정 시각 알림. Android 12 이상에서 정확한 알람을 허용하면 `setExactAndAllowWhileIdle`, 아니면 15분 창입니다. 알림 액션은 완료와 10분 뒤입니다. 재부팅과 앱 업데이트 뒤 다시 등록합니다.
- 앱 안에서 펫이 방 그림 위에 올라가고, 숨쉬듯 늘어났다 줄었다가 하며 가끔 눈을 깜빡입니다. 밥을 주면 먹는 그림, 밤이나 기력이 낮으면 잠, 밖에 나가면 기울며 걷습니다. Android 라이브 배경화면도 같은 그림과 움직임입니다. 위젯은 상태와 시간에 맞는 정지 장면입니다. Compose 화면은 WebP를 그대로 쓰고, Glance 위젯과 배경화면은 `androidMain/assets/mongle`의 PNG를 줄여 그립니다. iOS 위젯은 `PetWidget/Art`의 WebP를 읽습니다.
- 온라인 세계, 채팅, 유저 간 거래는 인터페이스만 있습니다. `EconomyAuthority`와 `CommunityRepository`를 나중에 서버 구현으로 바꾸면 됩니다. 지금은 `LocalEconomyAuthority`와 `UnavailableCommunityRepository`입니다.

## 아직 없는 것

- 계정, 결제, 클라우드 저장, 실제 커뮤니티 월드와 채팅, 유저 간 거래.
- iOS 빌드 확인, 위젯 갤러리에서 추가한 뒤의 동작, 알림 액션이 앱이 꺼진 상태에서 깨워지는지.
- 스토어 계정과 업로드 키. 릴리스 빌드 설정과 체크리스트는 `RELEASE.md`에 있습니다. 런처 아이콘은 일러스트 `app_icon`이고, Android는 적응형 아이콘과 밀도별 mipmap입니다. iOS 앱 아이콘은 1024입니다. Play용 512는 `store/icon-512.png`입니다. 글꼴 Pretendard Medium은 SIL Open Font License입니다.
- 위젯의 연속 애니메이션. 홈 화면 위젯은 정지 화면이고, 움직임은 앱과 Android 배경화면에서만 있습니다.

커뮤니티 단계에서는 `CommunityRepository`에 원격 펫, 거래 초안, 채팅을 연결하고, 보상과 구매는 `EconomyAuthority`가 서버에서 확정하게 두면 됩니다. 위젯과 일정 알림은 지금처럼 기기 안에 남겨도 됩니다.

## 구조

```
shared/          도메인, SQLDelight, GameService, 스냅샷 JSON
  commonMain     규칙과 저장소. Android와 iOS가 같이 씀
  androidMain    Android SQLite 드라이버, 알림 훅
  iosMain        App Group DB, UNUserNotificationCenter, MongleBridge
  jvmMain        테스트용 인메모리 SQLite
composeApp/      Compose Multiplatform UI
  commonMain     홈, 일정, 방 꾸미기, 도감. 일러스트 WebP, Pretendard(OFL)
  androidMain    Glance 위젯, AlarmManager, WorkManager, 배경화면
  iosMain        MainViewController. Mac에서만 컴파일
iosApp/          SwiftUI 셸, WidgetKit, App Intents, Xcode 프로젝트
```

iOS 타깃(`iosX64`, `iosArm64`, `iosSimulatorArm64`)은 `os.name`에 `mac`이 있을 때만 Gradle에 붙습니다. Linux에서는 공유 모듈의 JVM과 Android만 컴파일합니다.

상태는 한 프로세스 안에서 `GameService`가 잠그고 저장합니다. 위젯은 앱과 같은 데이터베이스를 봅니다. Android는 앱 프로세스의 Glance가 상태를 읽고, iOS는 앱과 위젯 익스텐션이 App Group `group.app.mongle` 아래 `mongle.db`를 같이 엽니다. 위젯용 요약은 `WidgetSnapshot` JSON입니다.

## Android에서 빌드하고 설치

JDK 17 이상, Android SDK(compileSdk 36, 플랫폼 android-36)가 필요합니다. SDK 경로는 `local.properties`의 `sdk.dir`에 둡니다. 이 파일은 저장소에 넣지 않습니다. 2026-08-31부터 Play는 대상 API 36을 요구합니다.

릴리스 AAB는 R8이 켜져 있습니다. 서명은 환경 변수 `MONGLE_STORE_FILE`, `MONGLE_STORE_PASSWORD`, `MONGLE_KEY_ALIAS`, `MONGLE_KEY_PASSWORD`가 있을 때만 붙고, 키 파일은 커밋하지 않습니다.

```bash
./gradlew :composeApp:bundleRelease
```

```bash
./gradlew :shared:jvmTest :composeApp:assembleDebug :composeApp:testDebugUnitTest
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

UI 테스트는 에뮬레이터 없이 Robolectric(API 34, `graphicsMode=NATIVE`)으로 Compose 화면을 누르고, Roborazzi(`roborazzi.test.record=true`)로 PNG를 남깁니다. 위젯은 `GlanceRemoteViews`로 그린 뒤 버튼 콜백을 직접 호출합니다. 알람은 Robolectric `ShadowAlarmManager`와 `ReminderReceiver`로 1분 뒤 트리거, 완료, 10분 뒤, 알림 닫힘을 확인합니다. 배경화면은 `WallpaperPainter`가 두 시각에 그린 프레임이 다른지로 확인합니다.

일정 작성 화면의 분은 10분 단위입니다. 정확히 60초 뒤 알람은 서비스 API로 겁니다.

처음 켜면 이름을 정합니다. 알림을 허용하고, Android 12 이상이면 방 화면의 링크나 시스템 설정에서 알람 및 리마인더(정확한 알람)를 켜야 제시간에 울립니다. Android 13 이상은 알림 권한도 필요합니다.

위젯은 홈 화면을 길게 눌러 위젯 목록에서 **몽이**(2×2) 또는 **몽이의 방**(4×4)을 추가합니다. 배경화면은 방 화면의 버튼으로 고릅니다.

## iOS에서 빌드 (미검증)

Mac, Xcode 16 전후, iOS 17 이상 시뮬레이터 또는 기기가 필요합니다. 이 저장소를 Mac에서 연 뒤:

1. `iosApp/Configuration/Config.xcconfig`의 `TEAM_ID`에 Apple Developer Team ID를 넣습니다. 번들은 `app.mongle`, 위젯은 `app.mongle.widget`, App Group은 `group.app.mongle`입니다. 개발자 사이트에서 앱과 위젯 둘 다에 App Groups 기능을 켜고 같은 그룹을 등록해야 합니다. 번들 ID가 이미 쓰였으면 xcconfig만 바꾸면 됩니다. entitlements 파일의 그룹 문자열도 같이 바꾸세요.
2. Xcode에서 `iosApp/iosApp.xcodeproj`를 엽니다. 스킴은 `iosApp`입니다.
3. 앱 타깃의 빌드 스크립트가 `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`를 실행하고 `ComposeApp.framework`(shared를 export한 정적 프레임워크)를 링크합니다. 위젯 타깃은 `./gradlew :shared:embedAndSignAppleFrameworkForXcode`로 `Shared.framework`만 링크합니다. 위젯 소스는 `WIDGET` 컴파일 조건으로 `Shared`를 import합니다.
4. 서명 팀이 비어 있으면 빌드가 실패합니다. 사용자 스크립트 샌드박스는 프로젝트에서 꺼 두었습니다. Gradle이 저장소 루트에 프레임워크를 써야 하기 때문입니다.
5. 홈 화면에 위젯 **몽이**를 추가합니다. 작은 위젯은 오늘의 기분과 완료, 큰 위젯은 밥주기, 선물 팔기, 오늘의 상품(한 번 누르면 확인, 다시 누르면 구매), 꾸미기, `mongle://schedule/add` 일정 추가입니다. 버튼은 iOS 17 App Intents입니다.
6. 일정 알림은 `UNUserNotificationCenter`이고 카테고리 `MONGLE_SCHEDULE`에 완료, 10분 뒤 액션이 있습니다. Swift `NotificationRouter`가 카테고리를 다시 등록하므로, Kotlin 쪽 UserNotifications 시그니처가 한 단계 어긋나도 액션 문구는 남길 수 있습니다. 그 경우 `shared/src/iosMain`의 컴파일 오류를 그 버전의 cinterop에 맞게 고치면 됩니다.

iOS에서 처음 깨질 수 있는 곳:

- `UNNotificationAction` / `UNNotificationCategory`의 옵션 인자 타입 (`0u`가 `UNNotificationActionOptions`와 안 맞을 수 있음)
- `WidgetCenter.sharedCenter()` 이름
- `content.userInfo`에 Kotlin `Map`을 넣는 부분
- 정적 프레임워크를 앱과 익스텐션에 따로 링크한 뒤의 서명

앱과 위젯은 메모리를 공유하지 않습니다. 버튼은 App Group의 SQLite를 통해 같은 펫을 바꿉니다.

## 규칙 요약

- 알이 아닌 펫의 일정 완료 기본 보상은 경험치 14, 코인 8, 행복 10입니다. 알은 4, 2, 6입니다.
- 생성 후 2분 미만은 ×0.25, 예정 시각보다 10분 넘게 이르면 ×0.5, 그날 8회를 넘기면 ×0.4입니다.
- 외출 선물은 조개 350, 클로버 350, 돌멩이 140, 편지 80, 도토리 60, 별 조각 20 (가중치 /1000)입니다. 판매가는 아이템 시트를 따릅니다. 산책에서 돌아오면 아직 없는 친구를 낮은 확률로 만납니다.
- 시작 코인은 40, 풀잎 쿠키 3개, 방 프리셋 세 개(포근한 방, 민트 방, 밤하늘 방)입니다.
