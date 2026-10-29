import {
    memberForVu,
    recordAiStats,
    resetAiStats,
    runSync,
    scrapeDatabase,
    writeSummary,
} from './common.js';

const VUS = Number(__ENV.VUS || '10');
const MEMBER_OFFSET = __ENV.MEMBER_OFFSET || '0';

export const options = {
    scenarios: {
        concurrent_sync: {
            executor: 'per-vu-iterations',
            exec: 'concurrentSync',
            vus: VUS,
            iterations: 1,
            maxDuration: __ENV.MAX_DURATION || '3m',
        },
        database_scrape: {
            executor: 'constant-vus',
            exec: 'scrape',
            vus: 1,
            duration: __ENV.SCRAPE_DURATION || '20s',
            gracefulStop: '0s',
        },
    },
};

export function setup() {
    resetAiStats();
}

export function concurrentSync() {
    runSync(memberForVu(MEMBER_OFFSET), 'concurrent');
}

export function scrape() {
    scrapeDatabase();
}

export function teardown() {
    return { ai: recordAiStats() };
}

export function handleSummary(data) {
    const ai = {
        analysis_requests: data.metrics.ai_analysis_calls_total
            ? data.metrics.ai_analysis_calls_total.values.count
            : null,
        embedding_requests: data.metrics.ai_embedding_calls_total
            ? data.metrics.ai_embedding_calls_total.values.count
            : null,
    };
    return writeSummary('concurrent', data, {
        concurrent_users: VUS,
        ai_calls_per_sync: {
            analysis: ai.analysis_requests === null ? null : ai.analysis_requests / VUS,
            embedding: ai.embedding_requests === null ? null : ai.embedding_requests / VUS,
        },
    });
}
