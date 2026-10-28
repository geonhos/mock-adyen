/* ── 지갑 등록 (Sessions flow): /api/sessions → 지갑 마운트 → SDK가 /sessions/{id}/payments 자동 호출 ──
 *
 * 1. POST /api/sessions  (서버가 Adyen POST /sessions: amount, storePaymentMethod=true, rpm, Ecommerce)
 * 2. AdyenCheckout({ session }) — SDK가 /sessions/{id}/setup 으로 결제수단·지갑 설정을 가져온다
 * 3. googlepay / applepay 컴포넌트 create → isAvailable → mount
 * 4. 지갑 승인 → SDK가 /sessions/{id}/payments 호출 → onPaymentCompleted({ resultCode, sessionResult })
 *    (콜백에는 storedPaymentMethodId 가 없다)
 * 5. GET /api/sessions/{id}?sessionResult=  +  GET /api/stored-payment-methods 재조회로 새 토큰 확인
 */
(function walletSessions() {
  let wsG = null, wsA = null;

  async function wsListStored(ref) {
    const r = await api('저장 결제수단 조회', { url: '/api/stored-payment-methods?shopperReference=' + encodeURIComponent(ref) });
    return { ok: r.ok, list: (r.data && r.data.response && r.data.response.storedPaymentMethods) || [] };
  }

  function wsRenderStored(list, beforeIds) {
    const el = $('#reg-ws-stored');
    if (!list.length) { el.innerHTML = '<p class="muted">저장된 결제수단 없음</p>'; return; }
    el.innerHTML = '<p class="muted">GET /storedPaymentMethods (shopperReference: ' + escapeHtml(getShopperRef()) + ')</p>' + list.map(t => {
      const isNew = !beforeIds.has(t.id);
      return `<div class="verdict" style="${isNew ? 'font-weight:600;border-left:3px solid #2e9e5b;padding-left:8px' : ''}">`
        + `${isNew ? '🆕 ' : ''}<code>${escapeHtml(t.id)}</code> — ${escapeHtml(describeStored(t))}</div>`;
    }).join('');
  }

  async function wsInit() {
    const ref = getShopperRef(), v = $('#reg-ws-verdict'), st = $('#reg-ws-status');
    if (!ref) { v.textContent = 'shopperReference가 필요합니다'; return; }
    const amount = Number($('#reg-ws-amount').value), currency = $('#reg-ws-cur').value.toUpperCase();
    v.textContent = ''; $('#reg-ws-stored').innerHTML = ''; st.textContent = '';

    // 등록 직전 목록 스냅샷 — 새로 생긴 결제수단을 강조하기 위함
    const before = await wsListStored(ref);
    const beforeIds = new Set(before.list.map(t => t.id));

    // 1. 세션 생성
    const r = await api('세션 생성(지갑)', {
      method: 'POST', url: '/api/sessions',
      body: { reference: 'WS-' + Date.now(), amount, currency, shopperReference: ref, countryCode: 'US',
              recurringProcessingModel: $('#reg-ws-rpm').value },
    });
    if (!r.ok || !r.data.id) { toast('세션 생성 실패', false); return; }
    const sessionId = r.data.id;

    // 2. AdyenCheckout — onSubmit을 넘기지 않는다(넘기면 Sessions 자동 처리가 깨진다)
    let setupErr = '';
    const { environment, clientKey } = await newCheckoutOptions();
    const checkout = await AdyenCheckout({
      environment, clientKey, countryCode: 'US', amount: { value: amount, currency },
      session: { id: sessionId, sessionData: r.data.sessionData },
      onPaymentCompleted: async (result) => {
        const ok = result.resultCode === 'Authorised';
        v.innerHTML = `${ok ? '✅' : '↩︎'} onPaymentCompleted resultCode: <code>${escapeHtml(result.resultCode)}</code>`
          + ` <span class="muted">(콜백에는 토큰/storedPaymentMethodId 없음)</span>`;
        renderInspector('세션 결제 완료(SDK)', { method: 'SDK', url: '/sessions/' + sessionId + '/payments' }, { status: 200, body: result });
        // 5a. 세션 결과 조회
        if (result.sessionResult) {
          const sr = await api('세션 결과 조회', { url: `/api/sessions/${encodeURIComponent(sessionId)}?sessionResult=${encodeURIComponent(result.sessionResult)}` });
          v.innerHTML += `<br>세션 상태: <code>${escapeHtml((sr.data && sr.data.status) || ('HTTP ' + sr.status))}</code>`;
        }
        // 5b. 저장 결제수단 재조회 → 새 토큰 강조. 토큰 생성이 비동기라 직후 조회에 안 보이면 잠시 뒤 다시 누른다.
        const after = await wsListStored(ref);
        wsRenderStored(after.list, beforeIds);
      },
      onError: (e) => { setupErr = (e && e.message) || String(e); v.textContent = 'onError: ' + setupErr; },
    });

    // /setup 실패(CORS·403 등) 시 SDK는 reject 없이 onError만 부르고 paymentMethodsResponse가 비어 있다 → create 생략
    if (!checkout || !checkout.paymentMethodsResponse) {
      v.innerHTML = '⚠️ 세션 /setup 호출 실패 — 지갑 컴포넌트를 만들지 않습니다.<br><code>' + escapeHtml(setupErr || '(원인 불명)') + '</code>'
        + '<br><span class="muted">Origin ' + escapeHtml(location.origin) + ' 이(가) clientKey의 Allowed origins에 등록돼 있는지 확인 (Adyen Customer Area → Developers → API credentials → Client settings)</span>';
      return;
    }

    // 3. 지갑 컴포넌트 마운트 — amount·countryCode·merchantId는 SDK가 /setup 응답에서 가져온다
    const gst = document.createElement('div'), ast = document.createElement('div'); st.append(gst, ast);
    wsG = await mountWallet({ checkout, type: 'googlepay', amount: { value: amount, currency }, container: '#reg-ws-gpay',
      statusEl: gst, previous: wsG });
    // Apple Pay recurringPaymentRequest: 0원 인증에서 기기 종속 DPAN 대신 MPAN 발급을 유도(정기결제용). 선택값.
    const extra = ($('#reg-ws-rpr').checked && amount === 0) ? { recurringPaymentRequest: {
      paymentDescription: '0원 인증 — 결제수단 저장(테스트)',
      regularBilling: { label: 'Stored payment method', amount: '0.00', paymentTiming: 'recurring' },
      managementURL: location.origin,
    } } : {};
    wsA = await mountWallet({ checkout, type: 'applepay', amount: { value: amount, currency }, container: '#reg-ws-apay',
      statusEl: ast, previous: wsA, extra });
  }

  $('#reg-ws-init').addEventListener('click', () => wsInit().catch(e => toast('초기화 실패: ' + e, false)));
})();
