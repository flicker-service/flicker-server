import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

// 커스텀 메트릭 - 에러 추적
const errors = new Counter('errors_total');
const errors_5xx = new Counter('errors_5xx');
const errors_timeout = new Counter('errors_timeout');

export const options = {
    scenarios: {
        exhaust_pool: {
            executor: 'ramping-vus',
            stages: [
                { duration: '20s', target: 200 },   // 200 -> 50 VU 점진 증가
                { duration: '2m',  target: 200 },   // 200 VU 유지 (풀 고갈 기대 구간)
                { duration: '20s', target: 0 },    // 정리
            ],
            gracefulStop: '10s',
        },
    },

    // 임계치 — 통과 기준 명시
    thresholds: {
        'http_req_duration': ['p(95)<3000'],     // 95퍼센트: 3초 이하
        'http_req_failed': ['rate<0.05'],        // 에러율 5% 이하
    },
};

const BASE_URL = 'https://a.simple-sns.link';

export default function() {
    // 타임아웃 제어: WAS가 응답을 안 주고 버틸 때, k6가 무한정 기다리지 않고 30초만에 연결을 끊도록
    const res = http.get(`${BASE_URL}/api/feeds/random`, {
        timeout: '30s',
    });

    const ok = check(res, {
        'status is 200': (r) => r.status === 200,
        'response time < 2s': (r) => r.timings.duration < 2000,
    });

    if (!ok) {
        errors.add(1);
        if (res.status >= 500) errors_5xx.add(1);
        if (res.error_code === 1050) errors_timeout.add(1);  // request timeout
    }

    sleep(0.3);
}

export function handleSummary(data) {
    return {
        'stdout': textSummary(data),
    };
}

function textSummary(data) {
    return `
========== 부하 테스트 결과 ==========
총 요청:      ${data.metrics.http_reqs.values.count}
실패 요청:    ${data.metrics.http_req_failed.values.passes}
평균 응답:    ${data.metrics.http_req_duration.values.avg.toFixed(0)}ms
p95 응답:     ${data.metrics.http_req_duration.values['p(95)'].toFixed(0)}ms
최대 응답:    ${data.metrics.http_req_duration.values.max.toFixed(0)}ms
초당 처리량:  ${data.metrics.http_reqs.values.rate.toFixed(2)}/s
=====================================
`;
}