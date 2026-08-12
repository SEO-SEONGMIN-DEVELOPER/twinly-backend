import http from 'k6/http';

export const BASE = __ENV.BASE_URL || 'http://10.0.23.25:8080';

const PHONE = __ENV.LOAD_PHONE || '01000009999';
const CODE = __ENV.TEST_CODE || '000000';

function post(path, body) {
  const res = http.post(`${BASE}${path}`, JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json' },
    responseType: 'text',
    tags: { name: 'setup' },
  });

  if (res.status !== 200) {
    throw new Error(`setup 실패: POST ${path} -> ${res.status} ${res.body}`);
  }

  return res.json();
}

export function login() {
  const sent = post('/api/v1/auth/sms/send', { phone: PHONE });
  const verified = post('/api/v1/auth/sms/verify', {
    smsVerificationToken: sent.smsVerificationToken,
    code: CODE,
  });
  return post('/api/v1/auth/login', { smsVerifiedToken: verified.smsVerifiedToken });
}

export function requireLifetime(token, neededMs) {
  const remainMs = new Date(token.accessExpiresAt).getTime() - Date.now();

  if (remainMs < neededMs) {
    throw new Error(
      `accessToken 잔여 ${Math.round(remainMs / 1000)}초 < 필요 ${Math.round(neededMs / 1000)}초. ` +
      '테스트 도중 만료되면 401 급증을 서버 붕괴로 오독하게 되므로 중단한다.'
    );
  }

  return remainMs;
}
