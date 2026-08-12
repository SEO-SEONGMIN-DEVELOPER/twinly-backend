import http from 'k6/http';
import { check } from 'k6';
import { BASE, login, requireLifetime } from './auth.js';

const SMOKE = __ENV.MODE === 'smoke';

// 2회차에서 120 RPS 를 CPU 62% 로 통과했다. 선형이면 190 근처가 한계지만
// 고정 비용이 있어 더 갈 수 있으므로 400 까지 열어둔다.
const RAMP = [
  { target: 120, duration: '1m' },
  { target: 160, duration: '30s' }, { target: 160, duration: '1m' },
  { target: 200, duration: '30s' }, { target: 200, duration: '1m' },
  { target: 250, duration: '30s' }, { target: 250, duration: '1m' },
  { target: 300, duration: '30s' }, { target: 300, duration: '1m' },
  { target: 400, duration: '30s' }, { target: 400, duration: '1m' },
];
const SMOKE_STAGES = [{ target: 2, duration: '30s' }];

const PLANNED_MS = (SMOKE ? 30 : 10 * 60) * 1000;
const MARGIN_MS = 3 * 60 * 1000;

export const options = {
  discardResponseBodies: true,
  scenarios: {
    limit: {
      executor: 'ramping-arrival-rate',
      startRate: SMOKE ? 2 : 120,
      timeUnit: '1s',
      preAllocatedVUs: SMOKE ? 20 : 200,
      maxVUs: SMOKE ? 50 : 2000,
      stages: SMOKE ? SMOKE_STAGES : RAMP,
    },
  },
  thresholds: {
    'http_req_failed{name:people}': [
      { threshold: 'rate<0.01', abortOnFail: !SMOKE, delayAbortEval: '30s' },
    ],
    'http_req_duration{name:people}': [
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
    ` / 대상 ${BASE}/api/v1/people?limit=100`
  );

  return { token: token.accessToken };
}

export default function (data) {
  const res = http.get(`${BASE}/api/v1/people?limit=100`, {
    headers: { Authorization: `Bearer ${data.token}` },
    tags: { name: 'people' },
  });

  check(res, { 'status is 200': (r) => r.status === 200 });
}
