import { sleep } from 'k6';
import {
    memberForVu,
    recordAiStats,
    resetAiStats,
    runSync,
    scrapeDatabase,
    writeSummary,
} from './common.js';

const MEMBER_OFFSET = __ENV.MEMBER_OFFSET || '0';

export const options = {
    scenarios: {
        resync: {
            executor: 'per-vu-iterations',
            exec: 'resync',
            vus: Number(__ENV.VUS || '1'),
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

export function resync() {
    const member = memberForVu(MEMBER_OFFSET);

    // 같은 회원·같은 영상으로 한 번 동기화해 가이드와 분석 이력을 만든 뒤,
    // 측정 구간의 AI 호출만 세도록 스텁 통계를 초기화하고 같은 동기화를 다시 실행한다.
    runSync(member, 'resync-warmup', false);
    // perf 하네스는 최근 결과 재사용 창을 1초로 줄인다. 그 창이 지나야 새 동기화가 시작된다.
    sleep(Number(__ENV.RESYNC_GAP_SECONDS || '2'));
    resetAiStats();

    runSync(member, 'resync');
}

export function scrape() {
    scrapeDatabase();
}

export function teardown() {
    return { ai: recordAiStats() };
}

export function handleSummary(data) {
    return writeSummary('resync', data);
}
