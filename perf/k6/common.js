import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Gauge, Rate, Trend } from 'k6/metrics';
import exec from 'k6/execution';

export const API_BASE_URL = __ENV.API_BASE_URL || 'http://127.0.0.1:18081';
export const MANAGEMENT_BASE_URL = __ENV.MANAGEMENT_BASE_URL || 'http://127.0.0.1:18082';
export const AI_STUB_BASE_URL = __ENV.AI_STUB_BASE_URL || 'http://127.0.0.1:18080';
export const ADMIN_BASE_URL = __ENV.ADMIN_BASE_URL || 'http://127.0.0.1:18083';
export const TOKENS = JSON.parse(open(__ENV.TOKENS_FILE || '../../build/perf/tokens.json'));
export const MEMBERS = TOKENS.members;
export const CONDITIONS = TOKENS.conditions;

export const syncDuration = new Trend('youtube_sync_accepted_to_success_ms', true);
export const successRate = new Rate('youtube_sync_success_rate');
export const failedSyncs = new Counter('youtube_sync_failed');
export const completedSyncs = new Counter('youtube_sync_completed');
export const acceptedEvents = new Counter('youtube_sync_accepted_events');
export const progressEvents = new Counter('youtube_sync_progress_events');
export const dbConnectionsActive = new Gauge('db_connections_active');
export const dbConnectionsPending = new Gauge('db_connections_pending');
export const aiAnalysisCalls = new Counter('ai_analysis_calls_total');
export const aiEmbeddingCalls = new Counter('ai_embedding_calls_total');

export function memberForVu(offset) {
    const index = Number(offset) + exec.vu.idInTest - 1;
    if (index >= MEMBERS.length) {
        throw new Error(`not enough perf members: need index ${index}, have ${MEMBERS.length}`);
    }
    return MEMBERS[index];
}

/// SSE 응답을 끝까지 받은 뒤, DB의 youtube_sync 행에서 accepted~success 구간을 계산한다.
export function runSync(member, scenarioName, recordMetrics = true) {
    const tags = { scenario: scenarioName };
    const startedAt = Date.now();
    const response = http.post(
        `${API_BASE_URL}/api/v3/member/youtube-sync`,
        null,
        {
            headers: {
                Authorization: `Bearer ${member.token}`,
                Accept: 'text/event-stream',
            },
            tags,
            timeout: __ENV.SYNC_TIMEOUT || '5m',
        },
    );

    const statusOk = check(response, {
        'stream completed with 200': (result) => result && result.status === 200,
    }, tags);

    const body = response && response.body ? response.body : '';
    const accepted = countEvents(body, 'youtube-sync.accepted');
    const progress = countEvents(body, 'youtube-sync.progress');
    const success = countEvents(body, 'youtube-sync.success');
    const errorEvent = firstErrorEvent(body);
    acceptedEvents.add(accepted, tags);
    progressEvents.add(progress, tags);

    let sync = null;
    if (statusOk && success > 0 && errorEvent === null) {
        sync = latestSync(member.id);
    }
    const duration = sync && typeof sync.accepted_to_success_ms === 'number' ? sync.accepted_to_success_ms : null;
    const finishedOk = statusOk && success > 0 && errorEvent === null && duration !== null;

    if (recordMetrics) {
        successRate.add(finishedOk, tags);
        if (finishedOk) {
            syncDuration.add(duration, tags);
            completedSyncs.add(1, tags);
        } else {
            failedSyncs.add(1, tags);
        }
    }
    if (!finishedOk) {
        console.error(
            `sync failed member=${member.id} status=${response && response.status} accepted=${accepted} progress=${progress} success=${success} error=${errorEvent} db=${JSON.stringify(sync)} elapsed=${Date.now() - startedAt}ms`,
        );
    }

    return { status: response ? response.status : 0, accepted, progress, success, errorEvent, sync, duration };
}

function latestSync(memberId) {
    // SSE가 닫힌 직후에는 종료 커밋이 이미 반영돼 있다. 그래도 경계 경쟁을 피하려고 한 번 재시도한다.
    for (let attempt = 0; attempt < 3; attempt++) {
        const response = http.get(`${ADMIN_BASE_URL}/syncs/latest?memberId=${memberId}`);
        if (response.status === 200) {
            const sync = response.json();
            if (sync.finished_at_ms !== null) {
                return sync;
            }
        }
        sleep(0.1);
    }
    return null;
}

function countEvents(body, name) {
    return (body.match(new RegExp(`event:${name}`, 'g')) || []).length;
}

function firstErrorEvent(body) {
    const match = body.match(/event:(youtube-sync\.(?:conflict|bad-gateway|service-unavailable|internal-server-error))/);
    return match ? match[1] : null;
}

export function resetAiStats() {
    http.post(`${AI_STUB_BASE_URL}/__admin/reset`);
}

export function readAiStats() {
    const response = http.get(`${AI_STUB_BASE_URL}/__admin/stats`);
    return response.json();
}

export function recordAiStats() {
    const stats = readAiStats();
    aiAnalysisCalls.add(stats.analysis_requests);
    aiEmbeddingCalls.add(stats.embedding_requests);
    return stats;
}

/// 관리 포트의 Prometheus 지표에서 HikariCP 사용량을 읽는다.
export function scrapeDatabase() {
    const response = http.get(`${MANAGEMENT_BASE_URL}/actuator/prometheus`);
    if (response.status !== 200) {
        return;
    }
    recordGauge(response.body, 'hikaricp_connections_active', dbConnectionsActive);
    recordGauge(response.body, 'hikaricp_connections_pending', dbConnectionsPending);
    sleep(0.5);
}

function recordGauge(body, name, gauge) {
    const expression = new RegExp(`^${name}(?:\\{[^}]*\\})?\\s+([0-9.]+)$`, 'm');
    const match = body.match(expression);
    if (match) {
        gauge.add(Number(match[1]));
    }
}

export function summaryPath(name) {
    return __ENV.SUMMARY_OUT || `build/perf/${name}-summary.json`;
}

export function writeSummary(name, data, extra = null) {
    const metrics = data.metrics;
    const condensed = {
        scenario: name,
        conditions: CONDITIONS,
        generated_at: new Date().toISOString(),
        metrics: {
            accepted_to_success_ms: {
                p50: metricValue(metrics, 'youtube_sync_accepted_to_success_ms', 'med'),
                p95: metricValue(metrics, 'youtube_sync_accepted_to_success_ms', 'p(95)'),
                max: metricValue(metrics, 'youtube_sync_accepted_to_success_ms', 'max'),
                count: metricValue(metrics, 'youtube_sync_completed', 'count'),
            },
            success_rate: metricValue(metrics, 'youtube_sync_success_rate', 'rate'),
            failed_syncs: metricValue(metrics, 'youtube_sync_failed', 'count') || 0,
            ai_analysis_calls_total: metricValue(metrics, 'ai_analysis_calls_total', 'count'),
            ai_embedding_calls_total: metricValue(metrics, 'ai_embedding_calls_total', 'count'),
            db_connections_active: {
                max: metricValue(metrics, 'db_connections_active', 'max'),
                value: metricValue(metrics, 'db_connections_active', 'value'),
            },
            db_connections_pending: {
                max: metricValue(metrics, 'db_connections_pending', 'max'),
                value: metricValue(metrics, 'db_connections_pending', 'value'),
            },
            http_req_failed: metricValue(metrics, 'http_req_failed', 'rate'),
            http_reqs: metricValue(metrics, 'http_reqs', 'count'),
            http_reqs_rate: metricValue(metrics, 'http_reqs', 'rate'),
            iterations: metricValue(metrics, 'iterations', 'count'),
            iteration_duration_ms: {
                p50: metricValue(metrics, 'iteration_duration', 'med'),
                p95: metricValue(metrics, 'iteration_duration', 'p(95)'),
            },
        },
        extra,
        raw: data,
    };
    return {
        [summaryPath(name)]: JSON.stringify(condensed, null, 2),
        stdout: `\n${JSON.stringify(condensed.metrics, null, 2)}\n`,
    };
}

function metricValue(metrics, name, key) {
    const metric = metrics[name];
    if (!metric || !metric.values || metric.values[key] === undefined) {
        return null;
    }
    return metric.values[key];
}
