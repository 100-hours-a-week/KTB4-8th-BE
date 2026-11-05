import { sleep } from 'k6';
import {
    memberForVu,
    recordAiStats,
    resetAiStats,
    runSync,
    scrapeDatabase,
    writeSummary,
} from './common.js';

const MEMBER_OFFSET_VALUE = __ENV.MEMBER_OFFSET || '0';

export const options = {
    scenarios: {
        first_sync: {
            executor: 'per-vu-iterations',
            exec: 'firstSync',
            vus: Number(__ENV.VUS || '1'),
            iterations: 1,
            maxDuration: __ENV.MAX_DURATION || '2m',
        },
        database_scrape: {
            executor: 'constant-vus',
            exec: 'scrape',
            vus: 1,
            duration: __ENV.SCRAPE_DURATION || '15s',
            gracefulStop: '0s',
        },
    },
};

export function setup() {
    resetAiStats();
}

export function firstSync() {
    runSync(memberForVu(MEMBER_OFFSET_VALUE), 'first-sync');
}

export function scrape() {
    scrapeDatabase();
}

export function teardown() {
    return { ai: recordAiStats() };
}

export function handleSummary(data) {
    return writeSummary('first-sync', data);
}
