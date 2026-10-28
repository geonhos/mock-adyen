# mock-adyen — 지갑 등록 (Sessions flow)

Adyen **Sessions API**로 Google Pay / Apple Pay 컴포넌트를 띄우고, 0원 인증(zero-auth)으로
결제수단을 저장(vaulting)해 `storedPaymentMethodId`를 얻는 최소 구성.

Spring Boot 3.2 · Java 17 · Adyen Checkout API **v72** · Adyen Web **5.72.0** (UMD `AdyenCheckout`).

## 흐름

```
[브라우저]                         [이 서버]                        [Adyen]
   │ POST /api/sessions ───────────▶ │ POST /v72/sessions ──────────▶ │  amount 0 · storePaymentMethod=true
   │ ◀── { id, sessionData } ─────── │ ◀── { id, sessionData } ────── │  recurringProcessingModel · Ecommerce
   │
   │ AdyenCheckout({ session }) ──────────────────────────────────────▶ │  POST /sessions/{id}/setup
   │ ◀── paymentMethods (applepay / googlepay configuration 포함) ───── │
   │ googlepay / applepay create → isAvailable → mount
   │
   │ 지갑 승인 → SDK가 직접 ─────────────────────────────────────────▶ │  POST /sessions/{id}/payments
   │ ◀── onPaymentCompleted({ resultCode, sessionResult }) ──────────── │  (토큰은 콜백에 없음)
   │
   │ GET /api/sessions/{id}?sessionResult= ▶ │ GET /v72/sessions/{id} ▶ │  세션 상태
   │ GET /api/stored-payment-methods ──────▶ │ GET /v72/storedPaymentMethods ▶ │  저장된 결제수단 → 🆕 표시
```

서버가 Adyen에 직접 보내는 호출은 `/sessions`, `GET /sessions/{id}`, `GET /storedPaymentMethods` 셋뿐이다.
결제 자체는 Web SDK가 Adyen과 직접 처리한다. 자세한 설명은 [docs/wallet-sessions-zero-auth.md](docs/wallet-sessions-zero-auth.md).

## 구성

| 영역 | 파일 |
|---|---|
| 세션 생성/조회 | `controller/SessionController`, `service/SessionService`, `dto/Session*` |
| 저장 결제수단 조회 | `controller/StoredPaymentMethodController`, `service/StoredPaymentMethodService` |
| Adyen 호출 | `client/AdyenClient` (RestClient, `x-API-key` 헤더) |
| 환경(test/live) | `config/AdyenProperties` → `AdyenEnvironment` → `AdyenRestClients` / `RestClientConfig` |
| 콘솔 부트스트랩 | `controller/ConfigController` (`GET /api/config`: clientKey · SDK CDN 호스트, API key는 절대 내려보내지 않음) |
| 서버↔Adyen 인스펙터 | `client/AdyenExchangeInterceptor` → `AdyenExchangeLog` → `controller/DebugController` (`GET /api/debug/adyen-exchanges`) |
| 화면 | `static/index.html`, `static/js/shared.js`(부트스트랩·인스펙터·지갑 마운트), `static/js/wallet-sessions.js`(흐름 본체) |

### API

| 메서드 | 엔드포인트 | Adyen 매핑 |
|---|---|---|
| POST | `/api/sessions` | `POST /v72/sessions` |
| GET | `/api/sessions/{id}?sessionResult=` | `GET /v72/sessions/{id}` |
| GET | `/api/stored-payment-methods?shopperReference=` | `GET /v72/storedPaymentMethods` |
| GET | `/api/config` · POST `/api/config/environment` | — (콘솔 설정 / 환경 전환) |
| GET | `/api/debug/adyen-exchanges?afterSeq=` | — (서버↔Adyen 교환 로그, 운영 노출 금지) |

## 1. 자격증명 설정 (필수)

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

[Adyen Customer Area](https://ca-test.adyen.com/)에서 받은 값으로 채운다. `application-local.yml`은 `.gitignore` 대상이다 — **절대 커밋하지 말 것.**

| 키 | 위치 |
|---|---|
| `adyen.environments.test.api-key` | Developers → API credentials → [credential] → Server settings → API key |
| `adyen.environments.test.merchant-account` | Account → Merchant accounts |
| `adyen.environments.test.client-key` | Developers → API credentials → [credential] → Client settings → Client key |

**Allowed origins**: 같은 Client settings 화면에서 `http://localhost:8080`을 등록해야 한다.
없으면 SDK의 `/sessions/{id}/setup`이 403이 되고 브라우저에는 `Failed to fetch`로 보인다
(그 뒤 `create()`가 `Cannot read properties of undefined (reading 'paymentMethods')`로 실패한다).

## 2. 실행

```bash
./gradlew bootRun
# http://localhost:8080
```

1. 상단 `shopperReference` 입력 (저장 토큰의 주인. 조회·후속 결제에 같은 값을 쓴다)
2. amount `0`(0원 인증) · currency · recurringProcessingModel 선택
3. **세션 생성 후 지갑 마운트** → Google Pay / Apple Pay 버튼 → 승인
4. `onPaymentCompleted` 결과와 함께 `GET /storedPaymentMethods` 재조회 결과에 새 결제수단이 🆕로 표시된다.
   토큰 생성이 비동기라 직후 조회에 안 보이면 버튼을 다시 눌러 재조회한다.

오른쪽 인스펙터에 브라우저↔서버 요청/응답과, 서버↔Adyen 실제 HTTP 교환(API key는 마스킹)이 순서대로 쌓인다.

## 3. 전제 조건과 제약

- **Google Pay**: Chrome + 카드가 등록된 Google 계정. TEST 환경은 `localhost`에서도 동작한다.
- **Apple Pay**: Safari + **HTTPS** + Adyen Customer Area 도메인 등록. localhost HTTP에서는 버튼이 뜨지 않는다.
  ngrok 등 터널을 쓰고 그 도메인도 Allowed origins에 추가한다.
- **`recurringPaymentRequest`(Apple Pay)**: 선택값. 없으면 토큰이 기기 종속 DPAN 기반이 되어 기기를 바꾸면 MIT가 실패할 수 있다.
  정기결제라면 포함을 권장한다(MPAN 발급).
- **검증계 velocity**: 같은 카드로 연속 테스트하면 `FRAUD`로 오거절될 수 있다. **75초** 간격을 둔다.
- `onSubmit`을 **넘기지 않는다**. 넘기면 SDK의 Sessions 자동 처리가 깨진다.
- `storedPaymentMethodId`를 서버에서 확정하려면 운영에서는 `AUTHORISATION` 웹훅의
  `additionalData.tokenization.storedPaymentMethodId`(HMAC 검증 필요)가 정답 경로다. 이 저장소에는 웹훅 수신이 없다.

## 테스트

```bash
./gradlew test
```

Adyen 호출은 모두 mock이다. 실제 Adyen 연동은 브라우저에서 수동 확인한다.
