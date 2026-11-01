import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const receivedDuration = new Trend('sse_received_accepted_to_success_ms', true);
const syncErrors = new Rate('sync_errors');
export const options = {
    scenarios: { sync: { executor: 'per-vu-iterations', vus: Number(__ENV.VUS), iterations: 1, maxDuration: '5m' } },
    thresholds: { sync_errors: ['rate==0'], http_req_failed: ['rate==0'] },
};

export default function () {
    // 로컬 수신기가 스트림을 한 줄씩 읽으며 단조 시계로 이벤트 수신 시각을 기록한다.
    const response = http.post(`${__ENV.RECEIVER_URL}/sync?index=${__VU - 1}`, null, { timeout: '5m' });
    const result = response.status === 200 ? response.json() : {};
    const ok = check(result, { 'SSE success and counts': value => value.ok === true });
    syncErrors.add(!ok);
    if (ok) receivedDuration.add(result.duration_ms);
}

export function handleSummary(data) {
    return { [__ENV.SUMMARY_PATH]: JSON.stringify(data, null, 2) };
}
