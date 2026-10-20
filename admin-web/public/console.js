(() => {
  'use strict';

  const PAGE_SIZE = 20;
  const MESSAGE_PAGE_SIZE = 50;

  const LABEL = {
    gender: { male: '남', female: '여' },
    memberStatus: { ACTIVE: '활성', WITHDRAWING: '탈퇴 유예', WITHDRAWN: '탈퇴' },
    kind: { PAID: '유료 구독', TRIAL: '무료 체험', EARLY_GRANT: '선착순 무료' },
    store: { app_store: 'App Store', play_store: 'Play Store', promotional: '프로모션', stripe: 'Stripe' },
    reason: {
      spam: '스팸', inappropriatePhoto: '부적절한 사진', fraudSuspected: '사기 의심',
      harassment: '괴롭힘', threat: '위협', other: '기타',
      SPAM: '스팸', INAPPROPRIATE_PHOTO: '부적절한 사진', FRAUD_SUSPECTED: '사기 의심',
      HARASSMENT: '괴롭힘', THREAT: '위협', OTHER: '기타'
    },
    reportStatus: { PENDING: ['접수', 'idle'], IN_REVIEW: ['검토 중', 'warn'], RESOLVED: ['처리 완료 · 조치', 'ok'], REJECTED: ['처리 완료 · 반려', 'ok'] },
    feedbackStatus: { RECEIVED: ['접수', 'idle'], IN_REVIEW: ['검토 중', 'warn'], DONE: ['완료', 'ok'] },
    violation: { AI_UTTERANCE: 'AI 발화 문제', USER_VIOLATION: '회원 위반', NONE: '문제 없음' },
    entry: { BOTH: ['둘 다 입장', 'ok'], ONE: ['한 명만 입장', 'warn'], NONE: ['미입장', 'idle'] },
    sceneType: { ACTION: '행동', DIALOGUE: '대화', MOVE: '이동' },
    caseCategory: { USER_REPORT: '유저 신고', AI_UTTERANCE_REPORT: 'AI 발화 신고', SUGGESTION: '건의' },
    caseRole: { REPORTER: '신고함', REPORTED: '신고당함', AUTHOR: '작성' }
  };

  const $ = id => document.getElementById(id);
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const num = value => Number(value ?? 0).toLocaleString('ko-KR');
  const pct = (part, whole) => whole ? (part / whole * 100).toFixed(1) : '0.0';

  const kstParts = iso => {
    const parts = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
    }).formatToParts(new Date(iso));
    return Object.fromEntries(parts.map(p => [p.type, p.value]));
  };
  const fmtDateTime = iso => { if (!iso) return '—'; const p = kstParts(iso); return `${p.month}-${p.day} ${p.hour}:${p.minute}`; };
  const fmtDate = iso => { if (!iso) return '—'; const p = kstParts(iso); return `${p.year}-${p.month}-${p.day}`; };
  const fmtTime = iso => { if (!iso) return ''; const p = kstParts(iso); return `${p.hour}:${p.minute}`; };
  const fmtDay = iso => { const p = kstParts(iso); return `${p.month}-${p.day}`; };

  const pill = (label, cls, plain) => `<span class="pill ${cls}${plain ? ' plain' : ''}">${esc(label)}</span>`;
  const statusPill = status => pill(LABEL.memberStatus[status] || '알 수 없음', status === 'ACTIVE' ? 'ok' : status === 'WITHDRAWING' ? 'warn' : 'bad');
  const reportPill = status => { const [l, c] = LABEL.reportStatus[status] || [status, 'idle']; return pill(l, c); };
  const feedbackPill = status => { const [l, c] = LABEL.feedbackStatus[status] || [status, 'idle']; return pill(l, c); };
  const kindPill = sub => {
    if (!sub) return '<span class="dash">없음</span>';
    if (!sub.active) return pill('만료', 'idle', true);
    return pill(LABEL.kind[sub.kind] || sub.kind, sub.kind === 'PAID' ? 'pink' : sub.kind === 'TRIAL' ? 'warn' : 'ok', true);
  };
  const who = member => member
    ? `<div class="who"><span>${esc(member.nickname || '(알 수 없음)')}</span><small class="num">ID ${esc(member.id ?? member.userId)} · ${esc(LABEL.gender[member.gender] || '-')}</small></div>`
    : '<span class="dash">—</span>';
  const storeLabel = store => store ? (LABEL.store[String(store).toLowerCase()] || store) : '—';

  const ratioBlock = (male, female) => {
    const total = male + female;
    const malePct = pct(male, total);
    const femalePct = total ? (100 - Number(malePct)).toFixed(1) : '0.0';
    return `<div><div class="ratio">${total ? `<span class="m" style="width:${malePct}%"></span><span class="f" style="flex:1"></span>` : ''}</div>
      <div class="ratio-legend num"><span>${malePct}%</span><span>${femalePct}%</span></div></div>`;
  };
  const mfLine = (count, prefix) => `<div class="mf num">${prefix ? `<span>${prefix}</span>` : ''}<span><i class="sw m"></i>남 ${num(count.male)}</span><span><i class="sw f"></i>여 ${num(count.female)}</span></div>`;
  const stateRow = (cols, message, error) => `<tr class="state-row${error ? ' error' : ''}"><td colspan="${cols}">${esc(message)}</td></tr>`;

  class ApiError extends Error {
    constructor(status, code, message) { super(message); this.status = status; this.code = code; }
  }

  async function api(path, { method = 'GET', body } = {}) {
    const response = await fetch(path, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body)
    });

    const text = await response.text();
    let data = null;
    try {
      data = text ? JSON.parse(text) : null;
    } catch (e) {
      data = null;
    }

    if (response.status === 401 || response.status === 403) {
      throw new ApiError(response.status, data?.code, '어드민 권한을 확인하지 못했어요. 페이지를 새로고침해 다시 로그인해 주세요.');
    }

    if (!response.ok) {
      throw new ApiError(response.status, data?.code, data?.message || '요청을 처리하지 못했어요.');
    }

    return data;
  }

  const query = params => {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (Array.isArray(value)) value.forEach(v => search.append(key, v));
      else if (value !== undefined && value !== null && value !== '') search.append(key, value);
    });
    const text = search.toString();
    return text ? `?${text}` : '';
  };

  const views = [...document.querySelectorAll('.view')];
  const navButtons = [...document.querySelectorAll('.nav button')];
  const loaders = {};
  let currentView = 'dashboard';

  function show(name) {
    if (!$('v-' + name)) name = 'dashboard';
    currentView = name;
    views.forEach(view => { view.hidden = view.id !== 'v-' + name; });
    navButtons.forEach(button => button.dataset.view === name ? button.setAttribute('aria-current', 'page') : button.removeAttribute('aria-current'));
    const view = $('v-' + name);
    $('page-title').textContent = view.dataset.title;
    $('page-desc').textContent = view.dataset.desc;
    if (location.hash.slice(1) !== name) history.replaceState(null, '', '#' + name);
    window.scrollTo(0, 0);
    loaders[name]?.();
  }

  navButtons.forEach(button => button.addEventListener('click', () => show(button.dataset.view)));
  document.querySelector('.site-header .brand').addEventListener('click', event => { event.preventDefault(); show('dashboard'); });

  function markRefreshed() {
    $('refreshed-at').textContent = `${fmtTime(new Date().toISOString())} 기준`;
  }

  async function refreshPendingCount() {
    try {
      const summary = await api('/admin/cases/summary');
      const pending = summary.pendingUserReports + summary.pendingAiUtteranceReports;
      $('pending-count').hidden = pending === 0;
      $('pending-count').textContent = pending;
      return summary;
    } catch (e) {
      return null;
    }
  }

  const drawer = $('drawer');
  const scrim = $('scrim');
  let lastFocus = null;

  function openDrawer(html) {
    lastFocus = document.activeElement;
    drawer.innerHTML = html;
    drawer.hidden = false;
    scrim.hidden = false;
    drawer.scrollTop = 0;
    drawer.querySelector('.close')?.addEventListener('click', closeDrawer);
    drawer.querySelector('.close')?.focus();
  }

  function closeDrawer() {
    if (drawer.hidden) return;
    drawer.hidden = true;
    scrim.hidden = true;
    lastFocus?.focus?.();
  }

  scrim.addEventListener('click', closeDrawer);
  document.addEventListener('keydown', event => { if (event.key === 'Escape') closeDrawer(); });
  const drawerHead = (title, sub) => `<div class="drawer-head"><div><h2 id="drawer-title">${title}</h2><div class="hint">${sub}</div></div><button class="close" type="button" aria-label="닫기">×</button></div>`;
  const drawerLoading = title => openDrawer(`${drawerHead(esc(title), '불러오는 중이에요')}<div class="state-box">불러오는 중이에요…</div>`);
  const drawerError = (title, error) => openDrawer(`${drawerHead(esc(title), '')}<div class="state-box error">${esc(error.message)}</div>`);

  function pager(element, result, go) {
    const totalPages = Math.max(1, Math.ceil(result.totalCount / result.size));
    element.innerHTML = `<button type="button" data-p="${result.page - 1}" ${result.page <= 0 ? 'disabled' : ''}>이전</button>
      <span class="num">${result.page + 1} / ${totalPages} · 총 ${num(result.totalCount)}건</span>
      <button type="button" data-p="${result.page + 1}" ${result.page + 1 >= totalPages ? 'disabled' : ''}>다음</button>`;
    element.querySelectorAll('button[data-p]').forEach(button => button.addEventListener('click', () => go(Number(button.dataset.p))));
  }

  function bindRowOpen(tbody, attribute, open) {
    tbody.addEventListener('click', event => {
      const row = event.target.closest(`tr[${attribute}]`);
      if (row) open(row.getAttribute(attribute));
    });
    tbody.addEventListener('keydown', event => {
      const row = event.target.closest(`tr[${attribute}]`);
      if (row && event.key === 'Enter') open(row.getAttribute(attribute));
    });
  }

  loaders.dashboard = async () => {
    const body = $('dashboard-body');
    body.innerHTML = '<div class="state-box">불러오는 중이에요…</div>';
    try {
      const d = await api('/admin/dashboard');
      markRefreshed();
      const members = d.members;
      const subs = d.subscribers.count;
      const withdrawn = d.withdrawn.count;
      const schools = d.organizations;
      const top = schools.slice(0, 3);
      const topSum = top.reduce((sum, s) => sum + s.memberCount, 0);
      const maxCount = schools[0]?.memberCount || 1;

      body.innerHTML = `<div class="dashboard-grid">
        <div class="kpis">
          <article class="card kpi wide">
            <div class="kpi-label">가입자 수</div>
            <div class="kpi-value num">${num(members.total)}<small>명</small></div>
            ${mfLine(members)}${ratioBlock(members.male, members.female)}
            <svg class="flow-arrow" viewBox="0 0 12 12" aria-hidden="true"><path d="M3 1l6 5-6 5" fill="none" stroke="currentColor" stroke-width="2"/></svg>
          </article>
          <article class="card kpi wide">
            <div class="kpi-label">구독자 수 <span class="card-sub">현재 시즌 참가자</span></div>
            <div class="kpi-value num">${num(subs.total)}<small>명</small></div>
            ${mfLine(subs)}${ratioBlock(subs.male, subs.female)}
            <div class="rates num"><span class="rate">가입자 대비 구독률<b>${pct(subs.total, members.total)}%</b></span><span class="rate">유료 구독<b>${num(d.subscribers.paid)}</b></span><span class="rate">무료 체험<b>${num(d.subscribers.trial)}</b></span><span class="rate">선착순 무료<b>${num(d.subscribers.earlyGrant)}</b></span></div>
          </article>
        </div>
        ${ageCard(d.birthYears, d.unknownBirthYearMembers)}
        <article class="card school-card">
          <div class="school-head">
            <div class="summary-main">
              <div class="kpi-label">가입한 학교 수 <span class="card-sub">가입자가 1명 이상인 학교</span></div>
              <div class="kpi-value num">${num(schools.length)}<small>개교</small></div>
            </div>
            <div class="rates num">${top.map(s => `<span class="rate">${esc(s.name)}<b>${num(s.memberCount)}명</b></span>`).join('')}${top.length ? `<span class="rate">상위 ${top.length}개교 비중<b>${pct(topSum, members.total)}%</b></span>` : ''}</div>
          </div>
          <details class="school-list">
            <summary><span>학교 목록 보기</span><span class="hint">가입자 많은 순</span><svg class="plus" width="14" height="14" viewBox="0 0 14 14" aria-hidden="true"><path d="M7 1v12M1 7h12" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg></summary>
            <ol class="school-rows num">${schools.map((s, i) => `<li><span class="rank">${i + 1}</span><span class="name">${esc(s.name)}</span><span class="track"><span class="fill" style="display:block;width:${s.memberCount / maxCount * 100}%"></span></span><span class="cnt">${num(s.memberCount)}명</span></li>`).join('') || '<li><span></span><span class="hint">아직 가입한 학교가 없어요</span></li>'}</ol>
          </details>
        </article>
        <article class="card summary">
          <div class="summary-main">
            <div class="kpi-label">탈퇴자 수 <span class="card-sub">탈퇴 완료 + 탈퇴 유예</span></div>
            <div class="kpi-value num">${num(withdrawn.total)}<small>명</small></div>
          </div>
          <div class="summary-ratio">${mfLine(withdrawn)}${ratioBlock(withdrawn.male, withdrawn.female)}</div>
          <div class="summary-chips" style="display:grid;gap:10px;justify-items:start">
            <div class="rates num"><span class="rate">탈퇴 완료<b>${num(d.withdrawn.completed)}</b></span><span class="rate">탈퇴 유예<b>${num(d.withdrawn.pending)}</b></span><span class="rate">탈퇴율<b>${pct(withdrawn.total, members.total + withdrawn.total)}%</b></span></div>
            <button class="kpi-link" type="button" id="go-withdraw">탈퇴 사유 보기 →</button>
          </div>
        </article>
      </div>`;
      $('go-withdraw').addEventListener('click', () => { reportState.tab = 'withdraw'; reportState.filter = ''; reportState.page = 0; show('reports'); });
    } catch (error) {
      body.innerHTML = `<div class="state-box error">${esc(error.message)}</div>`;
    }
  };

  const AGE_ROW_LIMIT = 9;

  function ageCard(birthYears, unknown) {
    const thisYear = Number(kstParts(new Date().toISOString()).year);
    const shown = birthYears.slice(0, AGE_ROW_LIMIT - 1);
    const rest = birthYears.slice(AGE_ROW_LIMIT - 1);
    const rows = shown.map(b => ({ ...b, label: `<b>${String(b.year).slice(2)}년생</b><small>${thisYear - b.year - 1}~${thisYear - b.year}세</small>` }));
    if (rest.length) {
      const first = rest[0].year;
      rows.push({
        year: first,
        members: rest.reduce((sum, b) => sum + b.members, 0),
        subscribers: rest.reduce((sum, b) => sum + b.subscribers, 0),
        label: `<b>${String(first).slice(2)}년생 이전</b><small>${thisYear - first - 1}세 이상</small>`
      });
    }
    const total = birthYears.reduce((sum, b) => sum + b.members, 0);
    if (!total) {
      return `<article class="card age-card"><div class="card-head"><h2>나이 통계</h2></div><div class="state-box">아직 집계할 가입자가 없어요.</div></article>`;
    }
    const average = birthYears.reduce((sum, b) => sum + (thisYear - b.year - 0.5) * b.members, 0) / total;
    const top = birthYears.reduce((a, b) => (b.members > a.members ? b : a));
    const max = Math.max(...rows.map(r => r.members));
    return `<article class="card age-card">
      <div class="card-head"><h2>나이 통계</h2><span class="card-sub">가입자 기준 · 나이는 만 나이 (올해 생일 전~후)</span></div>
      <div class="rates num"><span class="rate">평균 나이<b>${average.toFixed(1)}세</b></span><span class="rate">가장 많은 출생연도<b>${String(top.year).slice(2)}년생 ${num(top.members)}명</b></span>${unknown ? `<span class="rate">생년월일 확인 불가<b>${num(unknown)}명</b></span>` : ''}</div>
      <div class="table-wrap"><table class="age-table num">
        <thead><tr><th>출생연도</th><th>가입자</th><th>구독자</th><th>구독률</th></tr></thead>
        <tbody>${rows.map(r => `<tr class="${r.year === top.year && r.members === top.members ? 'top' : ''}">
          <td>${r.label}</td>
          <td><div class="age-bar"><span class="track-wrap"><span class="stackbar" style="width:${r.members / max * 100}%"><span class="all" style="flex:1"></span></span></span><b>${num(r.members)}</b></div></td>
          <td>${num(r.subscribers)}</td><td>${pct(r.subscribers, r.members)}%</td></tr>`).join('')}</tbody>
      </table></div>
    </article>`;
  }

  const memberState = { key: 'NICKNAME', query: '', status: '', page: 0 };
  const memberHints = {
    ID: ['회원 ID는 숫자 전체를 입력해야 해요.', '예: 1042'],
    NICKNAME: ['닉네임은 일부만 입력해도 찾을 수 있어요.', '닉네임 일부를 입력하세요'],
    EMAIL: ['이메일은 암호화되어 있어 주소 전체가 정확히 일치해야 찾을 수 있어요.', '예: name@skku.edu (전체 주소)'],
    SCHOOL: ['학교 이름 일부를 입력하면 목록이 나와요. 목록에서 고른 학교의 회원을 찾아요.', '예: 성균관']
  };

  $('m-key').addEventListener('change', event => {
    const key = event.target.value;
    const input = $('m-q');
    $('m-hint').textContent = memberHints[key][0];
    input.placeholder = memberHints[key][1];
    if (key === 'SCHOOL') input.setAttribute('list', 'school-list'); else input.removeAttribute('list');
    input.value = '';
    input.focus();
  });

  let schoolTimer = null;
  $('m-q').addEventListener('input', () => {
    if ($('m-key').value !== 'SCHOOL') return;
    clearTimeout(schoolTimer);
    schoolTimer = setTimeout(async () => {
      try {
        const names = await api('/admin/organizations' + query({ query: $('m-q').value.trim() }));
        $('school-list').innerHTML = names.map(name => `<option value="${esc(name)}">`).join('');
      } catch (e) {
        $('school-list').innerHTML = '';
      }
    }, 200);
  });

  $('member-search').addEventListener('submit', event => {
    event.preventDefault();
    memberState.key = $('m-key').value;
    memberState.query = $('m-q').value.trim();
    memberState.status = $('m-status').value;
    memberState.page = 0;
    loaders.members();
  });
  $('m-status').addEventListener('change', () => { memberState.status = $('m-status').value; memberState.page = 0; loaders.members(); });

  loaders.members = async () => {
    const rows = $('member-rows');
    rows.innerHTML = stateRow(7, '불러오는 중이에요…');
    try {
      const result = await api('/admin/members' + query({
        key: memberState.query ? memberState.key : '',
        query: memberState.query,
        status: memberState.status,
        page: memberState.page,
        size: PAGE_SIZE
      }));
      markRefreshed();
      rows.innerHTML = result.items.length ? result.items.map(m => `<tr tabindex="0" data-member="${esc(m.id)}">
          <td class="num">${esc(m.id)}</td><td><b>${esc(m.nickname || '-')}</b></td><td class="num">${esc(m.maskedEmail || '—')}</td>
          <td>${esc(m.organization || '—')}</td><td class="num">${fmtDate(m.createdAt)}</td><td>${kindPill(m.subscription)}</td><td>${statusPill(m.status)}</td></tr>`).join('')
        : stateRow(7, memberState.key === 'EMAIL' && memberState.query ? '이메일 전체가 정확히 일치하는 회원이 없어요. 오타나 대소문자를 확인해 주세요.'
          : memberState.key === 'SCHOOL' && memberState.query ? '이 학교의 회원이 없어요. 이름 일부를 입력한 뒤 목록에서 골라 주세요.' : '조건에 맞는 회원이 없어요.');
      pager($('member-pager'), result, page => { memberState.page = page; loaders.members(); });
    } catch (error) {
      rows.innerHTML = stateRow(7, error.message, true);
      $('member-pager').innerHTML = '';
    }
  };
  bindRowOpen($('member-rows'), 'data-member', id => openMember(id));

  async function openMember(id) {
    drawerLoading(`회원 ${id}`);
    let m;
    try {
      m = await api(`/admin/members/${encodeURIComponent(id)}`);
    } catch (error) {
      drawerError(`회원 ${id}`, error);
      return;
    }
    const sub = m.subscription;
    const verified = (at, label) => at ? `<span class="check">완료</span><span class="d num">${fmtDate(at)}</span>` : `<span class="dash">미완료</span><span class="d">${label}</span>`;
    openDrawer(`${drawerHead(esc(m.nickname || '(닉네임 없음)'), `ID ${esc(m.id)} · ${esc(LABEL.gender[m.gender] || '-')} · ${esc(m.organization || '학교 없음')} · 가입 ${fmtDate(m.createdAt)}`)}
      <div>${statusPill(m.status)}${m.withdrawalRequestedAt ? ` <span class="hint">탈퇴 신청 ${fmtDate(m.withdrawalRequestedAt)}</span>` : ''}</div>
      <div class="status-grid">
        <div class="status-cell"><span class="l">본인 인증</span>${verified(m.identityVerifiedAt, '')}</div>
        <div class="status-cell"><span class="l">학교 인증</span>${verified(m.emailVerifiedAt, '학교 메일 미인증')}${m.maskedEmail ? `<span class="d num">${esc(m.maskedEmail)}</span>` : ''}</div>
        <div class="status-cell" style="grid-column:1/-1"><span class="l">구독</span><span>${kindPill(sub)}</span><span class="d num">${sub ? `${storeLabel(sub.store)} · 만료 ${sub.expiresAt ? fmtDate(sub.expiresAt) : '없음'} · 동기화 ${fmtDateTime(sub.syncedAt)}` : '이용권 없음'}</span></div>
      </div>
      <div class="card-flat">
        <div class="card-head"><h2 style="font-size:15px">AI 활동 기록</h2><span class="hint">날짜를 고르면 그날의 장면을 보여줘요</span></div>
        <div class="date-chips" id="date-chips">${m.sceneDates.map((date, i) => `<button class="chip num" type="button" data-d="${esc(date)}" aria-pressed="${i === 0}">${esc(date.slice(5))}</button>`).join('') || '<span class="hint">AI 활동 기록이 없어요.</span>'}</div>
        <div id="scene-list"></div>
      </div>
      <div class="card-flat">
        <div class="card-head"><h2 style="font-size:15px">신고·문의</h2><span class="hint num">${m.relatedCases.length}건</span></div>
        <div class="mini-list">${m.relatedCases.map(c => {
          const status = c.category === 'SUGGESTION' ? feedbackPill(c.status) : reportPill(c.status);
          const summary = c.category === 'USER_REPORT' ? (LABEL.reason[c.summary] || c.summary) : c.summary;
          return `<div class="mini"><span>${esc(LABEL.caseCategory[c.category])} #${esc(c.id)} · ${esc(LABEL.caseRole[c.role])} · ${esc(summary || '')}</span>${status}</div>`;
        }).join('') || '<span class="hint">관련된 신고·문의가 없어요.</span>'}</div>
      </div>`);

    const chips = drawer.querySelector('#date-chips');
    const loadScenes = async date => {
      chips.querySelectorAll('.chip').forEach(chip => chip.setAttribute('aria-pressed', chip.dataset.d === date));
      const list = drawer.querySelector('#scene-list');
      list.innerHTML = '<div class="state-box">불러오는 중이에요…</div>';
      try {
        const scenes = await api(`/admin/members/${encodeURIComponent(m.id)}/scenes` + query({ date }));
        list.innerHTML = scenes.map(scene => sceneHtml(scene, m.nickname)).join('') || '<div class="state-box">이날 장면이 없어요.</div>';
      } catch (error) {
        list.innerHTML = `<div class="state-box error">${esc(error.message)}</div>`;
      }
    };
    chips.addEventListener('click', event => { const chip = event.target.closest('.chip'); if (chip) loadScenes(chip.dataset.d); });
    if (m.sceneDates.length) loadScenes(m.sceneDates[0]);
  }

  function sceneHtml(scene, ownerNickname) {
    const place = scene.type === 'MOVE' ? `${esc(scene.fromPlace || '?')} → ${esc(scene.place || '?')}` : esc(scene.place || '');
    const partners = scene.partnerNicknames.length ? `<span class="hint">with ${esc(scene.partnerNicknames.join(', '))}</span>` : '';
    const lines = scene.lines.length ? `<div class="lines">${scene.lines.map(line => line.speaker
        ? `<div class="line ${line.speaker === ownerNickname ? 'me' : ''}"><span class="who-s">${esc(line.speaker)}${line.action ? ` · ${esc(line.action)}` : ''}</span>${esc(line.text)}</div>`
        : `<p>${esc(line.text)}</p>`).join('')}</div>` : '';
    return `<div class="scene"><time>${fmtTime(scene.startsAt)}</time><div>
      <div class="place">${place} ${pill(LABEL.sceneType[scene.type] || scene.type, 'idle', true)}${partners}</div>
      ${scene.narration ? `<p>${esc(scene.narration)}</p>` : ''}${scene.mind ? `<p>속마음: ${esc(scene.mind)}</p>` : ''}${lines}</div></div>`;
  }

  const chatState = { query: '', entry: '', page: 0 };
  $('chat-search').addEventListener('submit', event => { event.preventDefault(); chatState.query = $('c-q').value.trim(); chatState.page = 0; loaders.chats(); });
  $('chat-chips').addEventListener('click', event => {
    const chip = event.target.closest('.chip');
    if (!chip) return;
    chatState.entry = chip.dataset.f;
    chatState.page = 0;
    $('chat-chips').querySelectorAll('.chip').forEach(c => c.setAttribute('aria-pressed', c === chip));
    loaders.chats();
  });

  loaders.chats = async () => {
    const rows = $('chat-rows');
    rows.innerHTML = stateRow(7, '불러오는 중이에요…');
    try {
      const result = await api('/admin/chat-rooms' + query({ query: chatState.query, entryState: chatState.entry, page: chatState.page, size: PAGE_SIZE }));
      markRefreshed();
      rows.innerHTML = result.items.length ? result.items.map(room => {
        const [label, cls] = LABEL.entry[room.entryState];
        const [a, b] = room.participants;
        return `<tr tabindex="0" data-room="${esc(room.id)}"><td class="num">#${esc(room.id)}</td><td>${who(a)}</td><td>${who(b)}</td>
          <td class="num">${fmtDateTime(room.openedAt)}</td><td>${pill(label, cls)}</td><td class="num">${num(room.messageCount)}개</td>
          <td class="num">${room.lastMessageAt ? fmtDateTime(room.lastMessageAt) : '<span class="dash">—</span>'}</td></tr>`;
      }).join('') : stateRow(7, '조건에 맞는 채팅방이 없어요.');
      pager($('chat-pager'), result, page => { chatState.page = page; loaders.chats(); });
    } catch (error) {
      rows.innerHTML = stateRow(7, error.message, true);
      $('chat-pager').innerHTML = '';
    }
  };
  bindRowOpen($('chat-rows'), 'data-room', id => openRoom(id));

  async function openRoom(id) {
    drawerLoading(`채팅방 #${id}`);
    let room;
    let page;
    try {
      [room, page] = await Promise.all([
        api(`/admin/chat-rooms/${encodeURIComponent(id)}`),
        api(`/admin/chat-rooms/${encodeURIComponent(id)}/messages` + query({ size: MESSAGE_PAGE_SIZE }))
      ]);
    } catch (error) {
      drawerError(`채팅방 #${id}`, error);
      return;
    }

    let messages = page.messages.slice().reverse();
    let hasMore = page.hasMore;
    const [label] = LABEL.entry[room.entryState];
    const nicknameById = Object.fromEntries(room.participants.map(p => [p.userId, p.nickname || '(알 수 없음)']));
    const sideById = Object.fromEntries(room.participants.map((p, i) => [p.userId, i === 0 ? 'a' : 'b']));

    const personCell = p => {
      const state = p.leftAt ? pill('나감', 'idle') : p.enteredAt ? pill('입장', 'ok') : pill('미입장', 'warn');
      return `<div class="person"><div class="top-line"><b>${esc(p.nickname || '(알 수 없음)')}</b>${state}</div>
        <span class="d num">ID ${esc(p.userId)} · ${esc(LABEL.gender[p.gender] || '-')} · ${esc(p.organization || '학교 없음')}</span>
        <span class="d num">${p.enteredAt ? '입장 ' + fmtDateTime(p.enteredAt) : '아직 입장하지 않음'}${p.leftAt ? ' · 나감 ' + fmtDateTime(p.leftAt) : ''}</span>
        <button class="chip" type="button" data-member="${esc(p.userId)}" style="justify-self:start;padding:3px 10px;font-size:12px;margin-top:4px">회원 상세</button></div>`;
    };

    const renderLog = () => {
      const oldest = hasMore && messages.length ? new Date(messages[0].sentAt) : null;
      const events = [[room.openedAt, '채팅방이 열렸어요']];
      room.participants.forEach(p => {
        if (p.enteredAt) events.push([p.enteredAt, `${p.nickname || '회원'}님이 입장했어요`]);
        if (p.leftAt) events.push([p.leftAt, `${p.nickname || '회원'}님이 나갔어요`]);
      });
      const items = [
        ...events.filter(([at]) => at && (!oldest || new Date(at) >= oldest)).map(([at, text]) => ({ at, sys: text })),
        ...messages.map(message => ({ at: message.sentAt, message }))
      ].sort((x, y) => new Date(x.at) - new Date(y.at));

      let day = '';
      let html = hasMore ? '<button class="load-more" type="button" id="load-older">이전 메시지 더 보기</button>' : '';
      items.forEach(item => {
        const d = fmtDay(item.at);
        if (d !== day) { day = d; html += `<span class="chat-day num">${d}</span>`; }
        if (item.sys) { html += `<span class="chat-sys">${esc(item.sys)}</span>`; return; }
        const m = item.message;
        const side = sideById[m.senderUserId] || 'a';
        const body = m.type === 'TEXT' ? `<div class="bubble">${esc(m.text)}</div>` : `<div class="bubble media">${m.type === 'PHOTO' ? '사진' : '동영상'}</div>`;
        html += `<div class="msg ${side}"><span class="who-s">${esc(nicknameById[m.senderUserId] || '(알 수 없음)')}</span>${body}<time>${fmtTime(m.sentAt)}</time></div>`;
      });
      if (!messages.length) html += '<span class="chat-sys">아직 주고받은 메시지가 없어요</span>';
      const log = drawer.querySelector('#chat-log');
      log.innerHTML = html;
      log.querySelector('#load-older')?.addEventListener('click', loadOlder);
    };

    const loadOlder = async () => {
      const button = drawer.querySelector('#load-older');
      button.disabled = true;
      try {
        const older = await api(`/admin/chat-rooms/${encodeURIComponent(room.id)}/messages` + query({ beforeId: messages[0].id, size: MESSAGE_PAGE_SIZE }));
        messages = older.messages.slice().reverse().concat(messages);
        hasMore = older.hasMore;
        renderLog();
      } catch (error) {
        button.disabled = false;
        button.textContent = error.message;
      }
    };

    openDrawer(`${drawerHead(`채팅방 #${esc(room.id)}`, `${esc(label)} · 메시지 ${num(room.messageCount)}개`)}
      <div class="people-pair">${room.participants.map(personCell).join('')}</div>
      ${room.commonPoint ? `<div class="field"><span class="lbl">AI가 찾은 공통점</span><div class="common-point">${esc(room.commonPoint)}</div></div>` : ''}
      <div class="field"><span class="lbl">대화 내용</span><div class="chat-log" id="chat-log"></div></div>`);
    renderLog();
    drawer.querySelectorAll('[data-member]').forEach(button => button.addEventListener('click', () => openMember(button.dataset.member)));
  }

  const subState = { kind: '', activeOnly: true, page: 0 };
  $('sub-active').addEventListener('change', () => { subState.activeOnly = $('sub-active').checked; subState.page = 0; loaders.subs(); });
  $('sub-chips').addEventListener('click', event => {
    const chip = event.target.closest('.chip');
    if (!chip) return;
    subState.kind = chip.dataset.f;
    subState.page = 0;
    loaders.subs();
  });

  loaders.subs = async () => {
    const rows = $('sub-rows');
    rows.innerHTML = stateRow(6, '불러오는 중이에요…');
    try {
      const result = await api('/admin/subscriptions' + query({ kind: subState.kind, activeOnly: subState.activeOnly, page: subState.page, size: PAGE_SIZE }));
      markRefreshed();
      const counts = result.activeCounts;
      const chips = [['', '전체', counts.paid + counts.trial + counts.earlyGrant], ['PAID', '유료 구독', counts.paid], ['TRIAL', '무료 체험', counts.trial], ['EARLY_GRANT', '선착순 무료', counts.earlyGrant]];
      $('sub-chips').innerHTML = chips.map(([key, label, count]) => `<button class="chip" type="button" data-f="${key}" aria-pressed="${subState.kind === key}">${label}<span class="c">${num(count)}</span></button>`).join('');
      const items = result.page.items;
      rows.innerHTML = items.length ? items.map(item => {
        const s = item.subscription;
        return `<tr tabindex="0" data-member="${esc(s.userId)}"><td>${who(item.member)}</td><td>${pill(LABEL.kind[s.kind], s.kind === 'PAID' ? 'pink' : s.kind === 'TRIAL' ? 'warn' : 'ok', true)}</td>
          <td>${s.active ? pill('활성', 'ok') : pill('만료', 'idle')}</td><td class="num">${s.expiresAt ? fmtDate(s.expiresAt) : '없음'}</td>
          <td>${esc(storeLabel(s.store))}</td><td class="num">${fmtDateTime(s.syncedAt)}</td></tr>`;
      }).join('') : stateRow(6, '조건에 맞는 이용권이 없어요.');
      pager($('sub-pager'), result.page, page => { subState.page = page; loaders.subs(); });
    } catch (error) {
      rows.innerHTML = stateRow(6, error.message, true);
      $('sub-pager').innerHTML = '';
    }
  };
  bindRowOpen($('sub-rows'), 'data-member', id => openMember(id));

  const reportState = { tab: 'user', filter: '', page: 0 };
  const reportFilters = [['', '전체'], ['PENDING', '접수'], ['IN_REVIEW', '검토 중'], ['DONE', '처리 완료']];

  async function renderCaseSummary() {
    const summary = await refreshPendingCount();
    const box = $('case-summary');
    if (!summary) {
      box.innerHTML = '<div class="state-box error">미처리 신고 수를 불러오지 못했어요.</div>';
      return;
    }
    const pending = summary.pendingUserReports + summary.pendingAiUtteranceReports;
    box.innerHTML = `<div class="summary-main">
        <div class="kpi-label">미처리 신고 <span class="card-sub">접수 + 검토 중</span></div>
        <div class="kpi-value num">${num(pending)}<small>건</small></div>
      </div>
      <div class="summary-ratio">${mfLine(summary.pendingReporters, '신고자')}${ratioBlock(summary.pendingReporters.male, summary.pendingReporters.female)}</div>
      <div class="rates num summary-chips"><span class="rate">유저 신고<b>${num(summary.pendingUserReports)}</b></span><span class="rate">AI 발화 신고<b>${num(summary.pendingAiUtteranceReports)}</b></span><span class="rate">미처리 건의<b>${num(summary.pendingSuggestions)}</b></span></div>`;
  }

  $('report-tabs').addEventListener('click', event => {
    const tab = event.target.closest('.tab');
    if (!tab) return;
    reportState.tab = tab.dataset.tab;
    reportState.filter = '';
    reportState.page = 0;
    renderReportBody();
  });

  $('report-body').addEventListener('click', event => {
    const chip = event.target.closest('#r-filter .chip');
    if (chip) {
      reportState.filter = chip.dataset.f;
      reportState.page = 0;
      renderReportBody();
      return;
    }
    const row = event.target.closest('tr[data-case]');
    if (row) openCase(row.dataset.case);
  });
  $('report-body').addEventListener('keydown', event => {
    const row = event.target.closest('tr[data-case]');
    if (row && event.key === 'Enter') openCase(row.dataset.case);
  });

  loaders.reports = () => {
    renderCaseSummary();
    renderReportBody();
  };

  const caseCache = {};

  async function renderReportBody() {
    document.querySelectorAll('#report-tabs .tab').forEach(tab => tab.setAttribute('aria-selected', tab.dataset.tab === reportState.tab));
    const body = $('report-body');
    const filterChips = reportState.tab === 'withdraw' ? '' : `<div class="chips" id="r-filter">${reportFilters.map(([key, label]) => `<button class="chip" type="button" data-f="${key}" aria-pressed="${reportState.filter === key}">${label}</button>`).join('')}</div><div style="height:12px"></div>`;
    body.innerHTML = filterChips + '<div class="state-box">불러오는 중이에요…</div>';
    const params = query({ status: reportState.filter, page: reportState.page, size: PAGE_SIZE });

    try {
      let html = '';
      let pageResult;
      if (reportState.tab === 'user') {
        pageResult = await api('/admin/cases/user-reports' + params);
        pageResult.items.forEach(r => { caseCache['user:' + r.id] = r; });
        html = `<div class="table-wrap"><table><thead><tr><th>번호</th><th>신고자</th><th>대상 회원</th><th>사유</th><th>상세 내용</th><th>접수 시각</th><th>상태</th></tr></thead><tbody>
          ${pageResult.items.map(r => `<tr tabindex="0" data-case="user:${esc(r.id)}"><td class="num">#${esc(r.id)}</td><td>${who(r.reporter)}</td><td>${who(r.reported)}</td>
            <td>${esc(LABEL.reason[r.reason] || r.reason)}</td><td class="wrap">${esc(r.detail || '—')}</td><td class="num">${fmtDateTime(r.createdAt)}</td><td>${reportPill(r.status)}</td></tr>`).join('') || stateRow(7, '신고가 없어요.')}</tbody></table></div>`;
      } else if (reportState.tab === 'ai') {
        pageResult = await api('/admin/cases/ai-utterance-reports' + params);
        pageResult.items.forEach(r => { caseCache['ai:' + r.id] = r; });
        html = `<div class="table-wrap"><table><thead><tr><th>번호</th><th>신고자</th><th>발화한 트윈</th><th>관련 장면</th><th>신고된 발화</th><th>신고 사유</th><th>접수 시각</th><th>상태</th></tr></thead><tbody>
          ${pageResult.items.map(r => `<tr tabindex="0" data-case="ai:${esc(r.id)}"><td class="num">#${esc(r.id)}</td><td>${who(r.reporter)}</td><td>${who(r.reported)}</td>
            <td><div class="who"><span>${esc(r.scenePlace || '—')}</span><small class="num">장면 #${esc(r.sceneId)}${r.sceneStartsAt ? ' · ' + fmtDateTime(r.sceneStartsAt) : ''}</small></div></td>
            <td class="wrap">“${esc(r.utteranceText)}”</td><td class="wrap">${esc(r.reason)}</td><td class="num">${fmtDateTime(r.createdAt)}</td><td>${reportPill(r.status)}</td></tr>`).join('') || stateRow(8, '신고가 없어요.')}</tbody></table></div>`;
      } else if (reportState.tab === 'suggest') {
        pageResult = await api('/admin/cases/suggestions' + params);
        pageResult.items.forEach(r => { caseCache['suggest:' + r.id] = r; });
        html = `<div class="table-wrap"><table><thead><tr><th>번호</th><th>작성자</th><th>분류</th><th>내용</th><th>앱</th><th>접수 시각</th><th>검토 상태</th><th>운영 메모</th></tr></thead><tbody>
          ${pageResult.items.map(r => `<tr tabindex="0" data-case="suggest:${esc(r.id)}"><td class="num">#${esc(r.id)}</td><td>${who(r.author)}</td><td>${esc(r.optionLabels.join(', ') || '—')}</td>
            <td class="wrap">${esc(r.detail || '—')}</td><td class="num">${esc([r.appPlatform, r.appVersion].filter(Boolean).join(' ') || '—')}</td><td class="num">${fmtDateTime(r.createdAt)}</td>
            <td>${feedbackPill(r.status)}</td><td class="wrap hint">${esc(r.memo || '—')}</td></tr>`).join('') || stateRow(8, '건의사항이 없어요.')}</tbody></table></div>`;
      } else {
        const result = await api('/admin/cases/withdrawal-reasons' + query({ page: reportState.page, size: PAGE_SIZE }));
        pageResult = result.page;
        const max = Math.max(1, ...result.reasonCounts.map(r => r.count));
        html = `<div class="card-head"><h2 style="font-size:15px">사유별 선택 수</h2><span class="hint num">탈퇴 신청 ${num(result.withdrawalCount)}건 · 여러 개 선택 가능</span></div>
          <div class="reason-bars bars num">${result.reasonCounts.map(r => `<div class="bar-row" style="grid-template-columns:minmax(0,180px) minmax(0,1fr) 72px"><span>${esc(r.label)}</span><div class="track"><div class="fill" style="width:${r.count / max * 100}%;background:var(--accent)"></div></div><span class="val"><b>${num(r.count)}</b>건</span></div>`).join('')}</div>
          <div class="section-label">최근 탈퇴 사유</div>
          <div class="table-wrap"><table><thead><tr><th>선택한 사유</th><th>추가 의견</th><th>접수 시각</th></tr></thead><tbody>
          ${pageResult.items.map(w => `<tr style="cursor:default"><td><div class="chips">${w.optionLabels.map(label => pill(label, 'idle', true)).join('') || '<span class="dash">없음</span>'}</div></td>
            <td class="wrap">${esc(w.detail) || '<span class="dash">없음</span>'}</td><td class="num">${fmtDateTime(w.createdAt)}</td></tr>`).join('') || stateRow(3, '탈퇴 사유가 없어요.')}</tbody></table></div>`;
      }
      markRefreshed();
      body.innerHTML = filterChips + html + '<div class="pager" id="report-pager"></div>';
      pager($('report-pager'), pageResult, page => { reportState.page = page; renderReportBody(); });
    } catch (error) {
      body.innerHTML = filterChips + `<div class="state-box error">${esc(error.message)}</div>`;
    }
  }

  function openCase(key) {
    const [type] = key.split(':');
    const item = caseCache[key];
    if (!item) return;
    if (type === 'suggest') openSuggestion(item);
    else openReport(type, item);
  }

  function openSuggestion(r) {
    const statuses = [['RECEIVED', '접수'], ['IN_REVIEW', '검토 중'], ['DONE', '완료']];
    openDrawer(`${drawerHead(`건의 #${esc(r.id)}`, `${esc(r.optionLabels.join(', ') || '분류 없음')} · ${esc([r.appPlatform, r.appVersion].filter(Boolean).join(' ') || '앱 정보 없음')} · ${fmtDateTime(r.createdAt)} 접수`)}
      <dl class="dl"><dt>작성자</dt><dd>${esc(r.author?.nickname || '(알 수 없음)')} (ID ${esc(r.author?.id)})</dd></dl>
      <div class="quote">${esc(r.detail || '내용 없음')}</div>
      <div class="field"><span class="lbl">검토 상태</span><div class="steps" id="steps">${statuses.map(([k, l]) => `<button type="button" class="step" data-s="${k}" aria-pressed="${r.status === k}">${l}</button>`).join('')}</div></div>
      <div class="field"><label for="sg-memo">운영 메모</label><textarea class="ctl" id="sg-memo" maxlength="2000" placeholder="확인한 내용이나 전달한 곳을 남겨 주세요">${esc(r.memo || '')}</textarea></div>
      <div class="toolbar"><button class="btn" type="button" id="d-save">저장</button><span class="saved" id="d-saved" hidden>저장했어요</span><span class="save-error" id="d-error" hidden></span></div>`);
    bindSteps();
    bindSave(`/admin/cases/suggestions/${encodeURIComponent(r.id)}`, () => ({
      status: drawer.querySelector('#steps .step[aria-pressed="true"]').dataset.s,
      memo: drawer.querySelector('#sg-memo').value.trim() || null
    }));
  }

  function openReport(type, r) {
    const isAi = type === 'ai';
    const done = r.status === 'RESOLVED' || r.status === 'REJECTED';
    const stage = done ? 'DONE' : r.status;
    const info = isAi
      ? `<dl class="dl"><dt>신고자</dt><dd>${esc(r.reporter?.nickname || '(알 수 없음)')} (ID ${esc(r.reporter?.id)})</dd>
          <dt>발화한 트윈</dt><dd>${esc(r.reported?.nickname || '(알 수 없음)')}의 트윈 (ID ${esc(r.reported?.id)}) <button class="chip" type="button" data-member="${esc(r.reported?.id)}" style="padding:2px 10px;font-size:12px">회원 상세</button></dd>
          <dt>관련 장면</dt><dd class="num">#${esc(r.sceneId)}${r.sceneStartsAt ? ' · ' + fmtDateTime(r.sceneStartsAt) : ''} · ${esc(r.scenePlace || '장소 없음')}</dd>
          <dt>접수 시각</dt><dd class="num">${fmtDateTime(r.createdAt)}</dd></dl>
         <div class="quote flag">“${esc(r.utteranceText)}”</div><dl class="dl"><dt>신고 사유</dt><dd>${esc(r.reason)}</dd></dl>`
      : `<dl class="dl"><dt>신고자</dt><dd>${esc(r.reporter?.nickname || '(알 수 없음)')} (ID ${esc(r.reporter?.id)})</dd>
          <dt>대상 회원</dt><dd>${esc(r.reported?.nickname || '(알 수 없음)')} (ID ${esc(r.reported?.id)}) <button class="chip" type="button" data-member="${esc(r.reported?.id)}" style="padding:2px 10px;font-size:12px">회원 상세</button></dd>
          <dt>사유</dt><dd>${esc(LABEL.reason[r.reason] || r.reason)}</dd><dt>접수 시각</dt><dd class="num">${fmtDateTime(r.createdAt)}</dd></dl>
         <div class="quote">${esc(r.detail || '상세 내용 없음')}</div>`;
    const kindField = isAi ? `<div class="field"><span class="lbl">문제 구분</span><div class="radio-row">${Object.entries(LABEL.violation).map(([k, l]) => `<label><input type="radio" name="kind" value="${k}" ${r.violationKind === k ? 'checked' : ''}> ${l}</label>`).join('')}</div><span class="hint">AI 발화 문제는 AI 쪽 개선으로, 회원 위반은 해당 회원 조치로 이어져요.</span></div>` : '';

    openDrawer(`${drawerHead(`${isAi ? 'AI 발화 신고' : '유저 신고'} #${esc(r.id)}`, done ? `처리 완료 · ${fmtDateTime(r.processedAt)}` : '처리 중')}
      ${info}
      <div class="field"><span class="lbl">처리 상태</span><div class="steps" id="steps">${[['PENDING', '접수'], ['IN_REVIEW', '검토 중'], ['DONE', '처리 완료']].map(([k, l]) => `<button type="button" class="step" data-s="${k}" aria-pressed="${stage === k}">${l}</button>`).join('')}</div></div>
      ${kindField}
      <div class="field"><label for="rv">검토 내용</label><textarea class="ctl" id="rv" maxlength="2000" placeholder="확인한 사실을 적어 주세요">${esc(r.reviewNote || '')}</textarea></div>
      <div class="field" id="result-field" ${done ? '' : 'hidden'}><span class="lbl">처리 결과</span><div class="radio-row"><label><input type="radio" name="result" value="RESOLVED" ${r.status !== 'REJECTED' ? 'checked' : ''}> 조치</label><label><input type="radio" name="result" value="REJECTED" ${r.status === 'REJECTED' ? 'checked' : ''}> 반려</label></div></div>
      <div class="field"><label for="ac">조치 사유</label><textarea class="ctl" id="ac" maxlength="2000" style="min-height:70px" placeholder="어떤 조치를 왜 했는지 적어 주세요">${esc(r.actionNote || '')}</textarea></div>
      <dl class="dl"><dt>처리 시각</dt><dd class="num" id="done-at">${done ? fmtDateTime(r.processedAt) : '처리 완료로 바꾸면 저장할 때 기록돼요'}</dd></dl>
      <div class="toolbar"><button class="btn" type="button" id="d-save">저장</button><span class="saved" id="d-saved" hidden>저장했어요</span><span class="save-error" id="d-error" hidden></span></div>`);

    bindSteps(stageValue => {
      drawer.querySelector('#result-field').hidden = stageValue !== 'DONE';
    });
    drawer.querySelectorAll('[data-member]').forEach(button => button.addEventListener('click', () => openMember(button.dataset.member)));
    bindSave(`/admin/cases/${isAi ? 'ai-utterance-reports' : 'user-reports'}/${encodeURIComponent(r.id)}`, () => {
      const stageValue = drawer.querySelector('#steps .step[aria-pressed="true"]').dataset.s;
      const status = stageValue === 'DONE' ? drawer.querySelector('input[name="result"]:checked').value : stageValue;
      const payload = {
        status,
        reviewNote: drawer.querySelector('#rv').value.trim() || null,
        actionNote: drawer.querySelector('#ac').value.trim() || null
      };
      if (isAi) payload.violationKind = drawer.querySelector('input[name="kind"]:checked')?.value || null;
      return payload;
    });
  }

  function bindSteps(onChange) {
    const steps = drawer.querySelector('#steps');
    steps.addEventListener('click', event => {
      const step = event.target.closest('.step');
      if (!step) return;
      steps.querySelectorAll('.step').forEach(s => s.setAttribute('aria-pressed', s === step));
      onChange?.(step.dataset.s);
    });
  }

  function bindSave(path, payload) {
    const button = drawer.querySelector('#d-save');
    button.addEventListener('click', async () => {
      button.disabled = true;
      drawer.querySelector('#d-saved').hidden = true;
      drawer.querySelector('#d-error').hidden = true;
      try {
        await api(path, { method: 'PATCH', body: payload() });
        drawer.querySelector('#d-saved').hidden = false;
        renderCaseSummary();
        renderReportBody();
      } catch (error) {
        drawer.querySelector('#d-error').hidden = false;
        drawer.querySelector('#d-error').textContent = error.message;
      } finally {
        button.disabled = false;
      }
    });
  }

  loaders.usage = async () => {
    const body = $('usage-body');
    body.innerHTML = '<div class="state-box">불러오는 중이에요…</div>';
    try {
      const u = await api('/admin/usage');
      markRefreshed();
      const subs = u.subscribers;
      const users = u.balanceGame.users;
      const chat = u.chatRoom;
      const bar = (label, cls, part, whole, unit) => `<div class="bar-row"><span>${label}</span><div class="track"><div class="fill ${cls}" style="width:${pct(part, whole)}%"></div></div><span class="val"><b>${pct(part, whole)}%</b> ${num(part)}${unit}</span></div>`;
      const stack = (label, partners) => {
        const total = partners.sameGender + partners.oppositeGender;
        const same = Math.round(Number(pct(partners.sameGender, total)));
        return `<div class="bar-row"><span>${label}</span><div class="stack">${total ? `<span class="same" style="width:${same}%">동성 ${same}%</span><span class="opp" style="flex:1">이성 ${100 - same}%</span>` : '<span class="same" style="flex:1">기록 없음</span>'}</div><span class="val">방 ${num(total)}개</span></div>`;
      };
      body.innerHTML = `<div class="usage-grid">
        <article class="card">
          <div class="card-head"><h2>밸런스 게임</h2></div>
          <div class="stat-row">
            <div class="stat"><span class="l">이용자 수</span><span class="v num">${num(users.total)}명</span><span class="s num">남 ${num(users.male)} · 여 ${num(users.female)}</span></div>
            <div class="stat"><span class="l">사용 비율</span><span class="v num">${pct(users.total, subs.total)}%</span><span class="s">구독자(시즌 참가자) 대비</span></div>
            <div class="stat"><span class="l">이용자 성비</span><span class="v num">${Math.round(Number(pct(users.male, users.total)))} : ${users.total ? 100 - Math.round(Number(pct(users.male, users.total))) : 0}</span><span class="s num">남 ${pct(users.male, users.total)}% · 여 ${pct(users.female, users.total)}%</span></div>
          </div>
          <div class="section-label">성별 사용 비율</div>
          <div class="bars num">${bar('전체', '', users.total, subs.total, '명')}${bar('남성', 'm', users.male, subs.male, '명')}${bar('여성', 'f', users.female, subs.female, '명')}</div>
        </article>
        <article class="card">
          <div class="card-head"><h2>채팅방</h2></div>
          <div class="stat-row two">
            <div class="stat"><span class="l">열린 채팅방</span><span class="v num">${num(chat.openedRooms)}개</span><span class="s num">회원별 ${num(chat.openedEntries.total)}건</span></div>
            <div class="stat"><span class="l">둘 다 입장한 채팅방</span><span class="v num">${num(chat.bothEnteredRooms)}개</span><span class="s num">열린 방의 ${pct(chat.bothEnteredRooms, chat.openedRooms)}%</span></div>
          </div>
          <div class="section-label">열린 방 대비 입장</div>
          <div class="bars num">${bar('전체', '', chat.enteredEntries.total, chat.openedEntries.total, '건')}${bar('남성', 'm', chat.enteredEntries.male, chat.openedEntries.male, '건')}${bar('여성', 'f', chat.enteredEntries.female, chat.openedEntries.female, '건')}</div>
          <div class="section-label">입장한 채팅방의 상대 성별</div>
          <div class="bars num">${stack('남성 회원', chat.malePartners)}${stack('여성 회원', chat.femalePartners)}</div>
        </article>
      </div>`;
    } catch (error) {
      body.innerHTML = `<div class="state-box error">${esc(error.message)}</div>`;
    }
  };

  const noticeIds = $('n-ids');
  const parseIds = () => [...new Set(noticeIds.value.split(/[\s,]+/).filter(value => /^\d+$/.test(value)))];
  const isAllTarget = () => document.querySelector('input[name="n-target"]:checked').value === 'all';
  const withPush = () => document.querySelector('input[name="n-channel"]:checked').value === 'push';
  let recipientTimer = null;
  let recipientCount = null;

  function refreshRecipients() {
    clearTimeout(recipientTimer);
    recipientTimer = setTimeout(async () => {
      const ids = parseIds();
      if (!isAllTarget() && !ids.length) {
        recipientCount = 0;
        $('n-count').textContent = '0';
        return;
      }
      try {
        const result = await api('/admin/app-notifications/notices/recipient-count' + query({ userIds: isAllTarget() ? [] : ids }));
        recipientCount = result.recipientCount;
        $('n-count').textContent = num(recipientCount);
      } catch (error) {
        recipientCount = null;
        $('n-count').textContent = '-';
      }
    }, 250);
  }

  document.querySelectorAll('input[name="n-target"]').forEach(radio => radio.addEventListener('change', () => {
    noticeIds.hidden = radio.value !== 'some';
    refreshRecipients();
  }));
  noticeIds.addEventListener('input', refreshRecipients);
  document.querySelectorAll('input[name="n-channel"]').forEach(radio => radio.addEventListener('change', () => {
    $('n-push-hint').hidden = !withPush();
  }));
  loaders.settings = refreshRecipients;

  const noticeMessage = (text, error) => {
    const done = $('n-done');
    done.hidden = false;
    done.style.color = error ? 'var(--red)' : '';
    done.textContent = text;
  };

  $('n-review').addEventListener('click', () => {
    const title = $('n-title').value.trim();
    const bodyText = $('n-body').value.trim();
    if (!title || !bodyText) { noticeMessage('제목과 본문을 모두 입력해 주세요.', true); return; }
    if (!isAllTarget() && !parseIds().length) { noticeMessage('받을 회원 ID를 한 명 이상 입력해 주세요.', true); return; }
    $('n-done').hidden = true;
    $('pv-title').textContent = title;
    $('pv-body').textContent = bodyText;
    $('pv-channel').textContent = withPush() ? '피드 + 푸시로 보내요. 피드에는 이렇게 보이고, 푸시도 같은 제목·본문으로 가요' : '피드만 보내요. 회원 알림 피드에는 이렇게 보여요';
    $('pv-count').innerHTML = `<b>${recipientCount === null ? '-' : num(recipientCount)}명</b>에게 발행해요.${withPush() ? ' 그중 이벤트 알림을 켠 회원에게 푸시도 가요.' : ''} 같은 제목·본문을 이미 받은 회원은 건너뛰어요.`;
    $('n-publish').textContent = withPush() ? '피드 + 푸시 발행하기' : '피드에 발행하기';
    $('notice-step1').hidden = true;
    $('notice-step2').hidden = false;
  });

  $('n-back').addEventListener('click', () => { $('notice-step1').hidden = false; $('notice-step2').hidden = true; });

  $('n-publish').addEventListener('click', async () => {
    const button = $('n-publish');
    button.disabled = true;
    try {
      const result = await api('/admin/app-notifications/notices', {
        method: 'POST',
        body: {
          title: $('n-title').value.trim(),
          body: $('n-body').value.trim(),
          push: withPush(),
          ...(isAllTarget() ? {} : { userIds: parseIds() })
        }
      });
      noticeMessage(`발행했어요. ${num(result.publishedCount)}명의 알림 피드에 새로 들어갔어요.${withPush() ? ' 이 회원들 중 이벤트 알림을 켠 회원에게 푸시를 보냈어요.' : ''}`);
      $('notice-step1').hidden = false;
      $('notice-step2').hidden = true;
    } catch (error) {
      noticeMessage(error.message, true);
    } finally {
      button.disabled = false;
    }
  });

  async function showOperator() {
    try {
      const me = await fetch('/me').then(response => response.json());
      $('operator').textContent = me.email || '';
    } catch (e) {
      $('operator').textContent = '';
    }
  }

  window.addEventListener('hashchange', () => {
    if (location.hash.slice(1) !== currentView) show(location.hash.slice(1));
  });

  showOperator();
  refreshPendingCount();
  show(location.hash.slice(1) || currentView);
})();
