import http from 'k6/http';
import { check } from 'k6';
import { BASE, login, requireLifetime } from './auth.js';

const SMOKE = __ENV.MODE === 'smoke';

// 1회차(풀 10)에서 400 RPS 는 p95 22ms 로 평온했고 472 에서 456ms 로 튀었다.
// 그 사이를 100 단위로 훑도록 계단을 좁힌다. 200 이하는 이미 평탄함을 확인해 건너뛴다.
const RAMP = [
  { target: 200, duration: '1m' },
  { target: 400, duration: '30s' }, { target: 400, duration: '1m' },
  { target: 500, duration: '30s' }, { target: 500, duration: '1m' },
  { target: 600, duration: '30s' }, { target: 600, duration: '1m' },
  { target: 700, duration: '30s' }, { target: 700, duration: '1m' },
  { target: 800, duration: '30s' }, { target: 800, duration: '1m' },
];
const SMOKE_STAGES = [{ target: 10, duration: '30s' }];

const PLANNED_MS = (SMOKE ? 30 : 9 * 60) * 1000;
const MARGIN_MS = 3 * 60 * 1000;

export const options = {
  discardResponseBodies: true,
  scenarios: {
    limit: {
      executor: 'ramping-arrival-rate',
      startRate: SMOKE ? 10 : 200,
      timeUnit: '1s',
      preAllocatedVUs: SMOKE ? 20 : 100,
      maxVUs: SMOKE ? 50 : 2000,
      stages: SMOKE ? SMOKE_STAGES : RAMP,
    },
  },
  thresholds: {
    'http_req_failed{name:main}': [
      { threshold: 'rate<0.01', abortOnFail: !SMOKE, delayAbortEval: '30s' },
    ],
    'http_req_duration{name:main}': [
      { threshold: 'p(95)<1000', abortOnFail: !SMOKE, delayAbortEval: '30s' },
    ],
    dropped_iterations: ['count<100'],
  },
};

export function setup() {
  const token = login();
  const remainMs = requireLifetime(token, PLANNED_MS + MARGIN_MS);

  console.log(
    `[setup] 토큰 잔여 ${Math.round(remainMs / 1000)}초 / 테스트 예정 ${PLANNED_MS / 1000}초` +
    ` / 대상 ${BASE}/api/v1/main`
  );

  return { token: token.accessToken };
}

export default function (data) {
  const res = http.get(`${BASE}/api/v1/main`, {
    headers: { Authorization: `Bearer ${data.token}` },
    tags: { name: 'main' },
  });

  check(res, { 'status is 200': (r) => r.status === 200 });
}
