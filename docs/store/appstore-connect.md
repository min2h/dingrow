# App Store Connect 메타데이터 초안

| 필드 | 한국어 | English (U.S.) |
|---|---|---|
| 이름 (30자) | 딩그로(Dingrow) | Dingrow: Pet Planner |
| 부제 (30자) | 일정을 지키면 자라는 몽이 | Plans that grow a tiny pet |
| 프로모션 텍스트 (170자) | 할 일을 끝낼 때마다 몽이가 자라요. 날씨 따라 바뀌는 방, 위젯, 선물까지! | Finish a task, Mongi grows. Weather-aware room, widgets and surprise gifts. |
| 키워드 (100바이트) | 플래너,할일,일정,알림,투두,캐릭터,다마고치,위젯,습관,루틴 | planner,to-do,reminder,schedule,pet,tamagotchi,widget,habit,routine,cute |
| 설명 | `listing.md`의 한국어 자세한 설명 | `listing.md` English full description |
| 지원 URL | https://min2h.github.io/dingrow/ | same |
| 마케팅 URL | (선택) 동일 | same |
| 개인정보처리방침 URL | https://min2h.github.io/dingrow/privacy/ | same |
| 카테고리 | 기본: 생산성 / 보조: 라이프스타일 | Productivity / Lifestyle |
| 저작권 | 2026 MinHyoung Lee | same |
| 연령 등급 | content-rating.md 참고 | |
| 가격 | 무료 + 앱 내 구입 | Free + IAP |
| 앱 내 구입 | coins_100 (소비성, $1.99), coins_1000 (소비성, $16.99) | |
| 심사 메모 | 로그인 없이 "게스트로 시작"으로 모든 기능 이용 가능. 코인은 일정 완료로도 획득 가능. 위치 권한은 선택(날씨). | Use "Start as guest" — no account needed. Coins can also be earned by completing tasks. Location is optional (weather only). |
| 스크린샷 6.9" | `screens/appstore-6.9/{ko,en}` (1290×2796) | |

# 앱 개인정보 보호(Privacy Nutrition Label) 초안

**데이터 수집 여부: "예"** (위치만)  
Apple 정의: 개발자 또는 제3자 파트너가 데이터를 실시간 요청 처리 이상으로 오래 보관하는 경우 '수집'. 날씨 조회는 실시간 처리 후 보관하지 않으므로 '수집 안 함'도 가능하나, Open‑Meteo의 로그 보관 정책을 확인할 수 없으면 보수적으로 아래처럼 신고 권장.

| 데이터 유형 | 용도 | 사용자에게 연결됨 | 추적에 사용 |
|---|---|---|---|
| 위치 › 대략적인 위치 | 앱 기능 | 아니요 | 아니요 |

수집하지 않음: 연락처 정보(이름·이메일 — Apple 로그인 정보는 기기 Keychain에만), 사용자 ID, 구매 항목(StoreKit 거래 ID는 기기에만), 사용 데이터, 진단, 식별자.  
추적(ATT): **안 함** → App Tracking Transparency 프롬프트 불필요.  
Privacy Manifest(PrivacyInfo.xcprivacy): Required Reason API 사용 시(UserDefaults → `CA92.1`) 매니페스트 추가 필요 — 체크리스트 참고.
