/* ── 공통: 환경 부트스트랩 · api() · 인스펙터 · 토스트 · 지갑 마운트 ─────────── */
const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

/* ── 환경 부트스트랩 (test / live) ──────────────────────────────────
 * 활성 환경은 서버가 들고 있다. 페이지 로드 시 /api/config 로 받아와
 * 그 환경의 CDN에서 Adyen Web SDK를 주입한다.
 *
 * SDK를 index.html에 하드코딩하지 않는 이유: test와 live는 CDN 호스트가 다르고,
 * 한번 로드된 AdyenCheckout 전역은 자기가 온 빌드에 묶여 있어 갈아끼울 수 없다.
 * 그래서 환경 전환 = 서버 상태 변경 + 페이지 리로드. */
const SDK_VERSION = '5.72.0';

let appConfig = null;
const configReady = fetch('/api/config')
  .then(res => res.ok ? res.json() : Promise.reject(new Error('HTTP ' + res.status)))
  .then(cfg => (appConfig = cfg));

const sdkReady = configReady.then(cfg => {
  if (!cfg.sdkBaseUrl) throw new Error(`sdk-base-url 미설정 (environment=${cfg.environment})`);
  const base = `${cfg.sdkBaseUrl}/checkoutshopper/sdk/${SDK_VERSION}`;
  injectStylesheet(`${base}/adyen.css`);
  return injectScript(`${base}/adyen.js`);
});

// 아래에서 await로 다시 다루므로, 여기서는 unhandled rejection만 막는다.
configReady.catch(e => toast('설정 로드 실패: ' + e.message, false));
sdkReady.catch(e => toast('Adyen SDK 로드 실패: ' + e.message, false));

function injectStylesheet(href) {
  const link = document.createElement('link');
  link.rel = 'stylesheet';
  link.href = href;
  link.crossOrigin = 'anonymous';
  document.head.appendChild(link);
}
function injectScript(src) {
  return new Promise((resolve, reject) => {
    const s = document.createElement('script');
    s.src = src;
    s.crossOrigin = 'anonymous';
    s.onload = resolve;
    s.onerror = () => reject(new Error('스크립트 로드 실패: ' + src));
    document.head.appendChild(s);
  });
}

/* shopperReference (전역, localStorage) */
const SHOPPER_KEY = 'adyen_shopperRef';
function getShopperRef() { return ($('#shopper-ref').value || '').trim(); }
function setShopperRef(v) { $('#shopper-ref').value = v; localStorage.setItem(SHOPPER_KEY, v); }

/* 테마 */
function applyTheme(t) { document.documentElement.setAttribute('data-theme', t); localStorage.setItem('adyen_theme', t); }

/* 토스트 */
function toast(msg, ok = true) {
  const el = document.createElement('div');
  el.className = 'toast ' + (ok ? 'ok' : 'bad');
  el.textContent = msg;
  $('#toasts').appendChild(el);
  setTimeout(() => el.remove(), 3500);
}

/* ★ api() — 모든 호출을 요청/응답으로 인스펙터에 기록 */
async function api(label, { method = 'GET', url, body } = {}) {
  const request = { method, url, body: body ?? null };
  let status = 0, data = null, ok = false;
  try {
    const res = await fetch(url, {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : {},
      body: body ? JSON.stringify(body) : undefined,
    });
    status = res.status; ok = res.ok;
    const text = await res.text();
    try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  } catch (e) { data = String(e); }
  renderInspector(label, request, { status, body: data });
  // 이 호출 중에 서버가 Adyen과 주고받은 실제 HTTP도 인스펙터에 이어 붙인다.
  await renderAdyenExchanges();
  return { ok, status, data };
}

/* ★ 서버 ↔ Adyen 구간 — 인터셉터가 기록한 실제 HTTP 교환을 인스펙터에 추가.
 * 시계 차이를 피하려고 시간이 아니라 서버 시퀀스를 커서로 쓴다.
 * api()가 아니라 raw fetch를 쓴다 — api()로 부르면 자기 자신을 다시 기록하게 된다. */
let _lastAdyenSeq = 0;
async function fetchAdyenExchanges() {
  const res = await fetch('/api/debug/adyen-exchanges?afterSeq=' + _lastAdyenSeq);
  if (!res.ok) throw new Error('HTTP ' + res.status);
  return res.json();
}
async function renderAdyenExchanges() {
  let list;
  try { list = await fetchAdyenExchanges(); } catch { return; }
  for (const ex of list) {
    if (ex.seq > _lastAdyenSeq) _lastAdyenSeq = ex.seq;
    renderInspector(
      `Adyen ↔ 서버${ex.durationMs != null ? ' · ' + ex.durationMs + 'ms' : ''}`,
      { method: ex.method, url: ex.uri, body: { headers: ex.requestHeaders, body: ex.requestBody } },
      { status: ex.error ? 0 : ex.status,
        body: ex.error ? { error: ex.error } : { headers: ex.responseHeaders, body: ex.responseBody } }
    );
  }
}
/* 페이지 로드 시점의 커서를 맞춰 둔다 — 이전에 쌓인 교환이 한꺼번에 쏟아지지 않게. */
(async function primeAdyenCursor() {
  try {
    const list = await fetchAdyenExchanges();
    _lastAdyenSeq = list.reduce((m, e) => Math.max(m, e.seq), 0);
  } catch { /* 디버그 경로가 없어도 콘솔은 그대로 동작해야 한다 */ }
})();

/* 인스펙터 — 요청/응답을 세로로 쌓아 표시 + 확대(모달)/복사 */
function renderInspector(label, request, response) {
  $('#insp-empty').style.display = 'none';
  const statusCls = response.status >= 200 && response.status < 300 ? 'ok' : response.status === 0 ? 'neutral' : 'bad';
  const reqText = fmt(request.body);
  const resText = fmt(response.body);
  const meta = { label, method: request.method, url: shortUrl(request.url), status: response.status, reqText, resText };
  const item = document.createElement('div');
  item.className = 'insp-item';
  item.innerHTML = `
    <div class="insp-head">
      <span class="lbl">${escapeHtml(label)}</span>
      <span class="meth" title="${escapeHtml(request.method + ' ' + shortUrl(request.url))}">${escapeHtml(request.method)} ${escapeHtml(shortUrl(request.url))}</span>
      <span class="spacer" style="flex:1"></span>
      <button class="iconbtn" data-act="expand" title="확대해서 보기">⛶</button>
      <button class="iconbtn" data-act="copy" title="요청+응답 JSON 복사">⧉</button>
      <span class="badge ${statusCls}">${response.status || 'ERR'}</span>
    </div>
    <div class="insp-body">
      <div class="insp-col"><h4>요청</h4><pre>${escapeHtml(reqText)}</pre></div>
      <div class="insp-col"><h4>응답</h4><pre>${escapeHtml(resText)}</pre></div>
    </div>`;
  const head = item.querySelector('.insp-head');
  const bodyEl = item.querySelector('.insp-body');
  head.addEventListener('click', (e) => {
    const btn = e.target.closest('button[data-act]');
    if (btn) {
      e.stopPropagation();
      if (btn.dataset.act === 'expand') openInspectorModal(meta);
      else copyText(inspectorCopyText(meta));
      return;
    }
    bodyEl.style.display = bodyEl.style.display === 'none' ? 'flex' : 'none';
  });
  $('#insp-list').prepend(item);
  // 최근 12개만 유지
  $$('#insp-list .insp-item').slice(12).forEach(n => n.remove());
}

/* 인스펙터 항목(요청+응답)을 복사용 텍스트로 — 인라인/모달 공통 */
function inspectorCopyText(meta) {
  return `// ${meta.label} — ${meta.method} ${meta.url} (HTTP ${meta.status || 'ERR'})\n\n// 요청\n${meta.reqText}\n\n// 응답\n${meta.resText}`;
}

/* 확대 모달: 큰 화면에 요청/응답 JSON을 크게 표시 */
function openInspectorModal(meta) {
  const m = $('#insp-modal');
  m.querySelector('.lbl').textContent = meta.label;
  m.querySelector('.meth').textContent = `${meta.method} ${meta.url}`;
  const badge = m.querySelector('.badge');
  badge.textContent = meta.status || 'ERR';
  badge.className = 'badge ' + (meta.status >= 200 && meta.status < 300 ? 'ok' : meta.status === 0 ? 'neutral' : 'bad');
  m.querySelector('[data-pre="req"]').textContent = meta.reqText;
  m.querySelector('[data-pre="res"]').textContent = meta.resText;
  m.dataset.copy = inspectorCopyText(meta);
  m.classList.add('open');
}
function closeInspectorModal() { $('#insp-modal').classList.remove('open'); }
async function copyText(text) {
  try { await navigator.clipboard.writeText(text); toast('JSON 복사됨'); }
  catch { toast('복사 실패(브라우저 권한)', false); }
}
function fmt(v) { return v == null ? '(없음)' : typeof v === 'string' ? v : JSON.stringify(v, null, 2); }
function shortUrl(u) { try { return u.replace(location.origin, ''); } catch { return u; } }
function escapeHtml(s) { return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); }

/* clientKey — 부트스트랩 config에서 온다(활성 환경의 값). */
async function getClientKey() {
  const cfg = await configReady;
  if (!cfg.clientKey) {
    toast(`clientKey 미설정 (environment=${cfg.environment})`, false);
    throw new Error('clientKey');
  }
  return cfg.clientKey;
}

/* 지갑 컴포넌트(googlepay|applepay) 생성·마운트. 금액/통화가 바뀌면 호출부가 다시 부른다(이전 것은 unmount).
 * isAvailable() reject 사유는 그대로 보여주고 힌트를 덧붙인다. 반환: 마운트된 컴포넌트 또는 null. */
async function mountWallet({ checkout, type, amount, container, statusEl, previous, extra = {} }) {
  if (previous) { try { previous.unmount(); } catch (e) {} }
  $(container).innerHTML = '';
  const name = type === 'applepay' ? 'Apple Pay' : 'Google Pay';
  const hint = type === 'applepay'
    ? 'Safari + HTTPS + Adyen CA 도메인 등록/검증 + Apple Pay 지갑에 카드가 필요하다.'
    : 'Chrome + Google 계정에 카드가 필요하다. TEST 환경은 localhost에서도 동작한다.';
  const say = (m) => { statusEl.textContent = m; };
  let comp;
  try {
    const base = type === 'googlepay'
      ? { environment: (await configReady).live ? 'PRODUCTION' : 'TEST', buttonType: 'plain' }
      : {};
    // Sessions flow: onSubmit을 넘기지 않는다. 넘기면 SDK의 /sessions/{id}/payments 자동 처리가 깨진다.
    comp = checkout.create(type, { ...base, amount, countryCode: 'US', ...extra });
  } catch (e) { say(`${name} 생성 실패: ${e && e.message || e}`); return null; }
  try {
    await comp.isAvailable();
    comp.mount(container);
    say('');
    return comp;
  } catch (e) {
    say(`${name} 사용 불가: ${(e && e.message) || e || '(사유 없음)'} — ${hint}`);
    return null;
  }
}

/* 저장 결제수단 응답 → 한 줄 표시 */
function describeStored(t) {
  const exp = (t.expiryMonth && t.expiryYear) ? `${t.expiryMonth}/${t.expiryYear}` : '';
  const si = Array.isArray(t.supportedShopperInteractions) ? t.supportedShopperInteractions.join('/') : '';
  return [t.type, t.brand, t.lastFour ? '•••• ' + t.lastFour : '', exp, si && 'interactions: ' + si]
    .filter(Boolean).join(' · ');
}

/* AdyenCheckout 공통 옵션 — SDK 로드를 기다린 뒤 활성 환경 값을 돌려준다.
 * AdyenCheckout을 부르는 모든 지점은 이걸 먼저 await 해야 한다(전역이 아직 없을 수 있다). */
async function newCheckoutOptions() {
  const [cfg] = await Promise.all([configReady, sdkReady]);
  const clientKey = await getClientKey();
  return { environment: cfg.environment, clientKey };
}

/* 환경 배지/셀렉터 — config가 도착한 뒤 상단바를 실제 환경으로 채운다. */
async function applyEnvironment() {
  let cfg;
  try { cfg = await configReady; }
  catch { $('#env-badge').textContent = '설정 오류'; return; }

  const badge = $('#env-badge');
  badge.textContent = cfg.environment.toUpperCase();
  badge.classList.toggle('live', cfg.live);
  // live일 때 화면 전체에 경고색을 입힌다(CSS가 [data-live] 를 본다).
  document.documentElement.toggleAttribute('data-live', cfg.live);

  const select = $('#env-select');
  select.innerHTML = cfg.environments.map(e =>
    `<option value="${e.name}" ${e.name === cfg.environment ? 'selected' : ''} ${e.configured ? '' : 'disabled'}>
       ${e.name}${e.configured ? '' : ' (미설정)'}</option>`).join('');
  select.disabled = false;
  select.addEventListener('change', () => switchEnvironment(select, cfg.environment));
}

/* 환경 전환 — 서버 활성 환경을 바꾸고 페이지를 리로드한다.
 * 리로드가 필요한 이유: 이미 로드된 Adyen Web SDK는 다른 환경 빌드로 갈아끼울 수 없다. */
async function switchEnvironment(select, current) {
  const target = select.value;
  if (target === current) return;

  const goingLive = target === 'live';
  const confirmed = confirm(goingLive
    ? '운영(live) 환경으로 전환합니다.\n\n실제 카드로 실제 청구가 발생합니다. 계속할까요?'
    : `${target} 환경으로 전환합니다. 페이지가 리로드됩니다.`);
  if (!confirmed) { select.value = current; return; }

  const r = await api('환경 전환', {
    method: 'POST', url: '/api/config/environment', body: { environment: target },
  });
  if (!r.ok) {
    select.value = current;
    toast((r.data && r.data.reason) || '환경 전환 실패', false);
    return;
  }
  location.reload();
}

/* 초기화: 테마 · shopperRef · 인스펙터 */
(function initShell() {
  applyTheme(localStorage.getItem('adyen_theme') || 'light');
  $('#theme-toggle').addEventListener('click', () =>
    applyTheme(document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark'));

  $('#shopper-ref').value = localStorage.getItem(SHOPPER_KEY) || 'test-shopper-001';
  $('#shopper-ref').addEventListener('change', () => setShopperRef(getShopperRef()));

  $('#insp-clear').addEventListener('click', () => { $('#insp-list').innerHTML = ''; $('#insp-empty').style.display = ''; });

  // 인스펙터 확대 모달: 닫기(X/배경/ESC) + 복사
  const modal = $('#insp-modal');
  modal.addEventListener('click', (e) => { if (e.target === modal || e.target.closest('[data-act="close"]')) closeInspectorModal(); });
  $('#insp-modal [data-act="mcopy"]').addEventListener('click', () => copyText(modal.dataset.copy || ''));
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeInspectorModal(); });

  applyEnvironment();
})();
