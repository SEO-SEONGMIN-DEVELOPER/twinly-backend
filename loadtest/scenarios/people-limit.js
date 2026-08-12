import http from 'k6/http';
import { check } from 'k6';
import { BASE, login, requireLifetime } from './auth.js';

const SMOKE = __ENV.MODE === 'smoke';

// 스모크런에서 5 RPS 에 p95 82ms 였다. main 보다 4배 무거우므로 무릎이 100 근처일
// 가능성이 높다. 그 구간을 20 단위로 훑어 무릎을 놓치지 않게 한다.
const RAMP = [
  { target: 20, duration: '1m' },
  { target: 40, duration: '30s' }, { target: 40, duration: '1m' },
  { target: 60, duration: '30s' }, { target: 60, duration: '1m' },
  { target: 80, duration: '30s' }, { target: 80, duration: '1m' },
  { target: 100, duration: '30s' }, { target: 100, duration: '1m' },
  { target: 140, duration: '30s' }, { target: 140, duration: '1m' },
];
const SMOKE_STAGES = [{ target: 5, duration: '30s' }];

const PLANNED_MS = (SMOKE ? 30 : 9 * 60) * 1000;
const MARGIN_MS = 3 * 60 * 1000;

export const options = {
  discardResponseBodies: true,
  scenarios: {
    limit: {
      executor: 'ramping-arrival-rate',
      startRate: SMOKE ? 5 : 20,
      timeUnit: '1s',
      preAllocatedVUs: SMOKE ? 20 : 100,
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
    ` / 대상 ${BASE}/api/v1/people`
  );

  return { token: token.accessToken };
}

export default function (data) {
  const res = http.get(`${BASE}/api/v1/people`, {
    headers: { Authorization: `Bearer ${data.token}` },
    tags: { name: 'people' },
  });

  check(res, { 'status is 200': (r) => r.status === 200 });
}
