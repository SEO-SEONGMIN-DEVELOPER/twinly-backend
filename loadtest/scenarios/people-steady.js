import http from 'k6/http';
import { check } from 'k6';
import { BASE, login, requireLifetime } from './auth.js';

// 프로파일링용 고정 부하. 램프가 섞이면 표본이 여러 부하 구간에 걸쳐 오염된다.
// 40 RPS 는 people-limit 1회차에서 CPU 76% 로 안정적이던 지점이다.
const RATE = Number(__ENV.RATE || 40);
const DURATION = __ENV.DURATION || '4m';

const PLANNED_MS = 5 * 60 * 1000;
const MARGIN_MS = 3 * 60 * 1000;

export const options = {
  discardResponseBodies: true,
  scenarios: {
    steady: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 50,
      maxVUs: 500,
    },
  },
  thresholds: {
    'http_req_failed{name:people}': ['rate<0.01'],
    dropped_iterations: ['count<100'],
  },
};

export function setup() {
  const token = login();
  const remainMs = requireLifetime(token, PLANNED_MS + MARGIN_MS);
  console.log(`[setup] 고정 ${RATE} RPS × ${DURATION} / 토큰 잔여 ${Math.round(remainMs / 1000)}초`);
  return { token: token.accessToken };
}

export default function (data) {
  const res = http.get(`${BASE}/api/v1/people`, {
    headers: { Authorization: `Bearer ${data.token}` },
    tags: { name: 'people' },
  });

  check(res, { 'status is 200': (r) => r.status === 200 });
}
