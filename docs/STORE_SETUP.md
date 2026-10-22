# Dingrow – 결제·로그인 콘솔 설정 (Billing & Sign-in setup)

## 0. 식별자 한눈에
| 항목 | 값 |
|---|---|
| Android applicationId | `app.dingrow` |
| iOS Bundle ID | `app.dingrow` (위젯 확장: `app.dingrow.widget` 등 Xcode 타깃 이름 확인) |
| App Group | `group.app.dingrow` |
| 업로드 키 별칭 | `upload` (`/workspace/secrets/dingrow-upload.jks`) |
| 업로드 키 SHA-1 | `DA:66:49:13:83:AF:5B:CC:F8:1A:2B:C2:19:1B:A4:DA:23:19:03:75` |
| 업로드 키 SHA-256 | `02:1D:78:98:95:FA:ED:4E:CF:92:4F:25:5C:0A:86:FA:37:D9:56:03:53:C8:51:E9:87:13:5A:82:E9:D5:7D:8F` |
| 인앱 상품 | `coins_100` (소비성, US$1.99, 코인 100) · `coins_1000` (소비성, US$16.99, 코인 1000) |
| 개인정보처리방침 URL | `https://min2h.github.io/dingrow/privacy/` (GitHub Pages: Settings → Pages → Branch `main`, folder `/docs`) |

## 1. 릴리스 서명 (Android)
키와 비밀번호는 저장소 밖 `/workspace/secrets/`(권한 600)에 있습니다. 빌드:
```bash
source ~/sdk/env.sh
source /workspace/secrets/dingrow-signing.env   # DINGROW_STORE_FILE / _STORE_PASSWORD / _KEY_ALIAS / _KEY_PASSWORD
DINGROW_VERSION_CODE=2 DINGROW_VERSION_NAME=0.1.1 ./gradlew :composeApp:bundleRelease
# → composeApp/build/outputs/bundle/release/composeApp-release.aab
```
Play App Signing을 사용하세요(기본값). 이 키는 **업로드 키**이고, 앱 서명 키는 Google이 보관합니다. 업로드 키와 비밀번호는 반드시 별도 안전한 곳(비밀번호 관리자 등)에 백업하세요. 분실 시 Play Console에서 업로드 키 재설정을 요청해야 합니다.

## 2. Google Play 결제 (Play Billing 7)
코드: `composeApp/src/androidMain/kotlin/app/mongle/billing/PlayBilling.kt`
- 구매 성공(PURCHASED) → 앱이 코인 지급(구매 토큰으로 중복 방지) → `consumeAsync`로 소비(= 승인). 소비성 상품은 소비가 곧 acknowledge이므로 3일 자동 환불을 막습니다.
- 대기(PENDING, 편의점/현금 결제 등) → 코인 지급 안 함, "결제가 대기 중이에요" 안내. 완료되면 다음 실행 시 `claimPendingPurchases()`가 지급 + 소비.
- 앱이 지급 전에 종료되거나 소비가 실패한 경우에도 다음 실행 시 같은 방식으로 복구(토큰 중복 방지로 두 번 지급되지 않음).
- `restore` 버튼(코인 상점)은 Play에 남아 있는 미소비 구매를 지급·소비합니다. 이미 소비된 소비성 상품은 Play 정책상 '복원' 대상이 아닙니다(코인은 기기에 저장).
- 가격 표시는 Play의 현지화 가격(`formattedPrice`)을 사용합니다.
- 디버그 빌드·Robolectric만 `FakeBilling`, 릴리스 빌드는 항상 Play Billing(`ReleaseConfigTest`).

Play Console 설정:
1. 수익 창출 → 제품 → 인앱 상품 → 상품 만들기
   - `coins_100`: 이름 "코인 100개 / 100 Coins", 설명, 기본 가격 US$1.99 (다른 국가 자동 환산, 한국은 원하는 가격으로 조정 가능) → 활성화
   - `coins_1000`: "코인 1,000개 / 1,000 Coins", US$16.99 → 활성화
2. 인앱 상품은 **AAB를 한 번 이상 트랙(내부/비공개 테스트)에 업로드**한 뒤에 만들 수 있습니다.
3. 설정 → 라이선스 테스트에 테스터 Gmail 추가 → 실제 청구 없이 결제 테스트(“테스트 카드, 항상 승인/지연 후 승인/거부” 선택 가능 → 대기 결제 테스트).
4. 결제 프로필(판매자 계정) 및 세금 정보 등록 필요.

## 3. App Store (StoreKit 2)
코드: `iosApp/iosApp/iOSApp.swift` (`CoinStore`)
- `Product.purchase()` → 검증된 거래만 지급(거래 ID로 중복 방지) → `transaction.finish()`.
- `.pending`(Ask to Buy, 추가 인증) → 안내 문구, 승인 시 `Transaction.updates`에서 지급.
- 앱 시작 시 `Transaction.unfinished`를 훑어 미완료 거래 지급 + finish.
- 로컬 테스트: `iosApp/Dingrow.storekit` (Xcode → Scheme → Run → Options → StoreKit Configuration).

App Store Connect 설정:
1. 앱 → 수익화 → 앱 내 구입 → 생성: 유형 **소비성(Consumable)**
   - 참조 이름 "Coins 100", 제품 ID `coins_100`, 가격 US$1.99 등급, 현지화(ko/en) 표시 이름·설명, 심사용 스크린샷(코인 상점 화면 `store/screens` 사용)
   - `coins_1000`, US$16.99
2. 계약/세금/은행(유료 앱 계약) 완료 필요 — 미완료 시 상품이 로드되지 않습니다.
3. 첫 앱 심사 제출 때 인앱 상품을 버전에 함께 첨부해야 합니다.
4. Sandbox 테스터: 사용자 및 액세스 → Sandbox → 테스터 추가.

## 4. Google 로그인 (Credential Manager)
코드: `composeApp/src/androidMain/kotlin/app/mongle/auth/AndroidAuth.kt`, 클라이언트 ID 자리: `composeApp/src/androidMain/res/values/auth.xml` (`REPLACE_WITH_WEB_CLIENT_ID`). 자리값이면 Google 버튼은 "준비 중"으로 안내되고 게스트 로그인은 동작합니다.

Google Cloud Console(동일 프로젝트)에서 OAuth 클라이언트 **3개**:
1. OAuth 동의 화면: 외부, 앱 이름 "딩그로", 지원 이메일, 개인정보처리방침 URL, 범위는 기본(openid, email, profile)만.
2. **Android 클라이언트 (업로드 키)**: 패키지 `app.dingrow`, SHA-1 `DA:66:49:13:83:AF:5B:CC:F8:1A:2B:C2:19:1B:A4:DA:23:19:03:75`
3. **Android 클라이언트 (Play 앱 서명 키)**: 패키지 `app.dingrow`, SHA-1 = Play Console → 테스트 및 출시 → 앱 무결성 → 앱 서명 키 인증서의 SHA-1 (첫 AAB 업로드 후 표시). *스토어에서 받은 앱은 이 키로 서명되므로 반드시 필요.*
4. (선택) **Android 클라이언트 (디버그 키)**: `keytool -list -v -keystore ~/.android/debug.keystore -storepass android`의 SHA-1.
5. **웹 애플리케이션 클라이언트**: 이 클라이언트 ID(`…apps.googleusercontent.com`)를 `auth.xml`의 `google_web_client_id`에 넣습니다(Credential Manager의 serverClientId).
현재 서버가 없으므로 ID 토큰은 기기에서만 사용합니다(서버 검증 TODO).

## 5. Apple로 로그인
코드: `iosApp/iosApp/iOSApp.swift`(ASAuthorizationController), 엔타이틀먼트 `com.apple.developer.applesignin` 이미 포함.
1. Apple Developer → Identifiers → `app.dingrow` → Capabilities: **Sign In with Apple**, **App Groups**(`group.app.dingrow`), **In-App Purchase** 체크.
2. 위젯 확장 식별자에도 App Groups 체크.
3. 서버가 없으므로 Services ID/키(.p8)는 지금은 필요 없습니다(웹/안드로이드에서 Apple 로그인을 쓰거나 서버 검증을 추가할 때 필요).
4. 앱이 Google 로그인을 제공하므로 App Store 가이드라인 4.8에 따라 Apple 로그인이 iOS에 함께 제공되어야 합니다(구현됨).
