# 딩그로(Dingrow) - Firebase 서버 저장 설정 가이드

설정 전에도 앱은 그대로 동작합니다(기기 저장만, 설정 화면 "서버 저장: 꺼짐"). 아래를 마치면 서버 저장·복원·구매 검증이 켜집니다.

## 구조 요약
- **인증**: Firebase Auth. 게스트 = 익명 로그인, Google/Apple 로그인 시 익명 계정에 연결(link). 이미 연결된 계정이면 그 계정으로 로그인 → 재설치 후 복원.
- **저장**: Firestore `users/{uid}` 문서 1개 `{ state(JSON), schema, summary, updatedAt(serverTimestamp) }`. 코인·보유 아이템·방·코디·레벨/EXP·컨디션·일정·선물 천장·설정 전부 포함.
- **동기화**: 오프라인 우선. 실행 시 + 변경 3초 후 동기화. 한쪽만 바뀌었으면 그쪽, 둘 다면 서버 시각 기준 최신이 이김. 진 쪽의 코인 원장(ledger)은 id/중복키로 재적용, 보유 아이템은 합집합 → 코인·아이템 유실/중복 지급 없음. (구현: `shared/src/commonMain/kotlin/app/mongle/cloud/`)
- **구매**: 앱 → `verifyPurchase` 함수 → Play(Android Publisher API) / App Store Server API 검증 → `purchases/{platform:token}`, `users/{uid}/credits/{token}` 에 트랜잭션으로 1회만 기록. 검증 거절이면 지급·소비(finish) 안 함, 서버 미배포·오프라인이면 기기에서 지급하고 같은 키(`iap:<token>`)로 나중에 중복 없이 병합.
- **보안 규칙**: `firestore.rules` — 본인 문서만 읽기/쓰기, `updatedAt`은 서버 시각만, `credits`·`purchases`는 클라이언트 쓰기 불가(함수 전용).
  - 참고: 게임 플레이로 오프라인에서 코인을 버는 구조라 `state` 안의 코인 값 자체는 클라이언트가 씁니다. **구매 코인 지급(credits)만** 서버 전용입니다.

## 1. Firebase 프로젝트
1. https://console.firebase.google.com → 프로젝트 추가(예: `dingrow-prod`), Google Analytics 끄기 가능.
2. `.firebaserc`의 `YOUR_FIREBASE_PROJECT_ID`를 프로젝트 ID로 교체.
3. **Blaze(종량제) 요금제로 업그레이드** — Cloud Functions 배포와 외부 API(Google Play/Apple) 호출에 필요. 소규모 사용량은 무료 한도 내. 예산 알림 설정 권장.

## 2. 앱 등록 + 설정 파일 (git에 올리지 않음, .gitignore 처리됨)
- **Android**: 앱 추가 → 패키지 `app.dingrow`, **SHA-1/SHA-256** 등록(디버그: `./gradlew :composeApp:signingReport`, 출시: Play Console → 앱 무결성 → 앱 서명 키). `google-services.json` 다운로드 → `composeApp/google-services.json` (형식 예: `composeApp/google-services.json.example`). 파일이 있으면 Gradle이 google-services 플러그인을 자동 적용.
- **iOS**: 앱 추가 → 번들 ID `app.dingrow`. `GoogleService-Info.plist` → `iosApp/iosApp/GoogleService-Info.plist` 후 Xcode에서 iosApp 타깃에 추가. Xcode → File → Add Package Dependencies → `https://github.com/firebase/firebase-ios-sdk` → **FirebaseAuth, FirebaseFirestore, FirebaseFunctions** 추가. (패키지가 없으면 `iOSApp.swift`의 Firebase 코드는 `#if canImport`로 빠지고 앱은 기기 저장만 사용)

## 3. Authentication
콘솔 → Authentication → 시작하기 → 로그인 방법:
- **익명** 사용 설정(게스트).
- **Google** 사용 설정. 앱의 Google 로그인에 쓰는 웹 클라이언트 ID(현재 앱 설정값)가 같은 GCP 프로젝트의 OAuth 클라이언트여야 ID 토큰 연결이 됩니다. 다른 프로젝트라면 Google 제공업체의 "외부 프로젝트의 클라이언트 ID 허용 목록"에 추가.
- **Apple** 사용 설정. iOS 네이티브 로그인만 쓰면 서비스 ID/키 없이 번들 ID만으로 동작. Xcode 타깃에 "Sign in with Apple" capability 확인.

## 4. Firestore
콘솔 → Firestore Database → 만들기 → **프로덕션 모드**, 위치 **asia-northeast3(서울)**. 규칙·인덱스는 아래 배포 명령으로 올립니다.

## 5. Google Play 구매 검증 (서비스 계정)
1. GCP 콘솔(같은 프로젝트) → API 및 서비스 → **Google Play Android Developer API** 사용 설정.
2. Functions 실행 계정 확인: IAM → `PROJECT_NUMBER-compute@developer.gserviceaccount.com` (2세대 함수 기본 계정).
3. Play Console → 사용자 및 권한 → 새 사용자 초대 → 위 이메일 → 앱 권한(딩그로): **재무 데이터 보기, 주문 및 구독 관리**. 반영까지 최대 24시간.
4. 패키지명이 다르면 함수 환경 변수 `ANDROID_PACKAGE` 지정(기본 `app.dingrow`).

## 6. App Store 구매 검증 (App Store Server API 키)
1. App Store Connect → 사용자 및 액세스 → 통합 → **App Store Connect API → 인앱 구매** 키 생성 → `.p8` 다운로드, **Key ID**, **Issuer ID** 기록.
2. Apple 루트 인증서(https://www.apple.com/certificateauthority/ 의 `AppleRootCA-G3.cer`) 다운로드.
3. `functions/.env` (커밋 금지) 작성:
   ```
   APPSTORE_KEY_ID=XXXXXXXXXX
   APPSTORE_ISSUER_ID=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
   IOS_BUNDLE_ID=app.dingrow
   IOS_APP_APPLE_ID=1234567890
   APPLE_ROOT_CERTS_B64=<base64 -w0 AppleRootCA-G3.cer 결과>
   ```
4. 개인 키는 Secret Manager에: `firebase functions:secrets:set APPSTORE_PRIVATE_KEY < AuthKey_XXXXXXXXXX.p8`
   (키가 없으면 iOS 검증은 "unavailable" → 앱이 기기에서 지급, 거절하지 않음)

## 7. 배포
```bash
npm i -g firebase-tools
firebase login
cd functions && npm ci && npm test && cd ..
firebase deploy --only firestore:rules,firestore:indexes,functions
```
함수(리전 asia-northeast3): `verifyPurchase`, `deleteAccount`.

## 8. 테스트
- 함수 단위 테스트(멱등 지급, 토큰 재사용 차단, 거절/장애 구분): `cd functions && npm test`
- 보안 규칙(에뮬레이터, Java 필요): `cd functions && npm run test:rules`
- 앱 동기화/병합 테스트: `./gradlew :shared:jvmTest --tests '*CloudSyncTest*'`
- 실기기 확인: 게스트로 코인 사용 → 앱 삭제 → 재설치 후 같은 Google/Apple 계정 로그인 → 복원 확인. Play 라이선스 테스터로 코인 구매 → Firestore `users/{uid}/credits` 생성 확인.

## 9. 계정 삭제
설정 → 데이터 → **계정과 서버 데이터 삭제**: `deleteAccount` 함수가 `users/{uid}`(credits 포함)와 Auth 계정을 삭제하고, `purchases` 기록에서는 uid 연결만 지웁니다. 함수 미배포 시 앱이 직접 문서+Auth 계정 삭제를 시도합니다. 개인정보처리방침: `docs/privacy/index.html#delete`.
