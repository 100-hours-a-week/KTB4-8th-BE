"""#313 전용: 실제 SSE 수신 시각을 재는 k6 수신기와 교차 반복 실행기 (표준 라이브러리만 사용)."""
import argparse
import concurrent.futures
import http.server
import hashlib
import json
import math
import pathlib
import platform
import re
import subprocess
import threading
import time
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
VERSIONS = {"base": 18100, "candidate": 18200}


def request(url, post=False):
    with urllib.request.urlopen(urllib.request.Request(url, data=b"" if post else None), timeout=10) as response:
        return json.load(response)


def url(version, component, path):
    return f"http://127.0.0.1:{VERSIONS[version] + component}{path}"


def events(stream, clock=time.perf_counter_ns):
    """빈 줄까지 받은 시각을 남긴다. 주석·여러 data 행·CRLF를 처리한다."""
    event, data = None, []
    for raw in stream:
        line = raw.decode("utf-8").rstrip("\r\n")
        if not line:
            if event:
                yield event, "\n".join(data), clock()
            event, data = None, []
        elif not line.startswith(":"):
            field, _, value = line.partition(":")
            value = value.removeprefix(" ")
            if field == "event":
                event = value
            elif field == "data":
                data.append(value)


def sync(version, member, target):
    start, end = None, None
    received = []
    opened = time.perf_counter_ns()
    req = urllib.request.Request(url(version, 81, "/api/v3/member/youtube-sync"), data=b"",
                                 headers={"Authorization": "Bearer " + member["token"],
                                          "Accept": "text/event-stream"})
    try:
        with urllib.request.urlopen(req, timeout=240) as response:
            for name, data, timestamp in events(response):
                received.append({"event": name, "received_ns": timestamp, "data": data})
                if name == "youtube-sync.accepted":
                    if start is not None:
                        raise ValueError("duplicate accepted")
                    start = timestamp
                if name == "youtube-sync.success":
                    end = timestamp
        row = request(url(version, 83, f'/syncs/latest?memberId={member["id"]}'))
        ok = (start is not None and end is not None and end >= start
              and row["status"] == "SUCCEEDED" and row["video_target_count"] == target
              and row["video_analyzed_count"] == target and row["video_failed_count"] == 0
              and not any(e["event"] not in {"youtube-sync.accepted", "youtube-sync.progress",
                                             "youtube-sync.success"} for e in received))
        return {"member_id": member["id"], "ok": ok, "http_status": 200, "opened_ns": opened, "events": received,
                "duration_ms": (end - start) / 1e6 if start is not None and end is not None else None,
                "db": row}
    except Exception as error:
        return {"member_id": member["id"], "ok": False, "opened_ns": opened,
                "events": received, "http_status": getattr(error, "code", None), "error": str(error)}


def quiet(version, timeout=1800):
    deadline = time.monotonic() + timeout
    stable = None
    while time.monotonic() < deadline:
        state = request(url(version, 83, "/state"))
        stats = request(url(version, 80, "/__admin/stats"))
        idle = all(state[key] == 0 for key in ("active_syncs", "outbox", "held_slots"))
        if idle and stats["in_flight"] == 0 and state["since_finished_ms"] > 1000:
            stable = stable or time.monotonic()
            if time.monotonic() - stable >= 1:
                return
        else:
            stable = None
        time.sleep(0.25)
    raise TimeoutError(f"{version}: 후속 작업 미완료: {state}, {stats}")


class Receiver(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        index = int(urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)["index"][0])
        result = sync(self.server.version, self.server.members[index], self.server.target)
        with self.server.samples_lock:
            self.server.samples.append(result)
        body = json.dumps(result).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *_):
        pass


def scrape(version, stop, readings):
    while not stop.is_set():
        try:
            with urllib.request.urlopen(url(version, 82, "/actuator/prometheus"), timeout=5) as response:
                text = response.read().decode()
            row = {"received_ns": time.perf_counter_ns()}
            for key in ("active", "pending"):
                values = re.findall(rf'^hikaricp_connections_{key}(?:\{{[^\n]*\}})?\s+([\d.eE+-]+)', text, re.M)
                if not values:
                    raise ValueError(f"missing Hikari {key}")
                row[key] = sum(map(float, values))
            readings.append(row)
        except Exception as error:
            readings.append({"error": str(error)})
        stop.wait(0.25)


def percentile(values, p):
    values = sorted(values)
    index = (len(values) - 1) * p
    lower = math.floor(index)
    return values[lower] + (values[math.ceil(index)] - values[lower]) * (index - lower)


def run(args):
    out = ROOT / args.output
    if (out / "metadata.json").exists():
        raise ValueError("기존 원시 기록을 덮어쓸 수 없습니다. 새 출력 경로를 지정하세요")
    if args.warmups < 0 or args.repeats < 1:
        raise ValueError("워밍업은 0회 이상, 본 측정은 1회 이상이어야 합니다")
    out.mkdir(parents=True, exist_ok=True)
    metadata = {"started_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"), "base_sha": args.base_sha,
                "candidate_sha": args.candidate_sha, "warmups": args.warmups, "repeats": args.repeats,
                "measurement": "full-unreviewed" if args.warmups == 2 and args.repeats == 20 else "smoke",
                "conditions": {"members": 40, "youtube_page_size": 50, "youtube_ms": 200,
                               "analysis_batch": 10, "analysis_ms": 1000, "embedding_ms": 1000},
                "clock": "perf_counter_ns at complete SSE frame receipt; DB timestamps are validation only",
                "db_restore": "empty workload tables; preserve 40 members, slots, Flyway and Hibernate sequences",
                "platform": platform.platform(), "complete": False,
                "tool_sha256": {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
                                for path in [ROOT / "perf/loop313.py", ROOT / "perf/k6/loop-313.js",
                                             ROOT / "perf/compose.yaml", ROOT / "src/mysqlIntegrationTest/java/eightjbbm/perf/PerfHarnessApplication.java",
                                             ROOT / "src/mysqlIntegrationTest/java/eightjbbm/perf/PerfHarnessConfiguration.java",
                                             ROOT / "src/test/java/eightjbbm/keepgo/integration/stub/AiServerStub.java"]}}
    (out / "metadata.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    server = http.server.ThreadingHTTPServer(("0.0.0.0", args.port), Receiver)
    server.samples_lock = threading.Lock()
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    results = []
    try:
        for scenario in args.scenarios:
            for n in args.concurrency:
                for iteration in range(args.warmups + args.repeats):
                    # 시작 버전도 번갈아 바꿔 순서 편향을 줄인다.
                    order = ["base", "candidate"] if iteration % 2 == 0 else ["candidate", "base"]
                    for version in order:
                        tag = f"{scenario}-n{n}-{iteration:02d}-{version}"
                        print(f"PREPARE {tag}", flush=True)
                        quiet(version)
                        request(url(version, 83, "/reset"), True)
                        shared = scenario == "shared"
                        request(url(version, 83, f"/fixture?count=30&shared={str(shared).lower()}"), True)
                        members = json.loads((ROOT / f"build/perf/313/{version}/tokens.json").read_text())["members"]
                        if len(members) != 40:
                            raise ValueError("측정 회원 수는 40명이어야 합니다")
                        target = 40 if scenario == "resync" else 30
                        if scenario == "resync":
                            with concurrent.futures.ThreadPoolExecutor(max_workers=n) as executor:
                                pre = list(executor.map(lambda m: sync(version, m, 30), members[:n]))
                            (out / f"{tag}-pre.json").write_text(json.dumps(pre, indent=2), encoding="utf-8")
                            if not all(row["ok"] for row in pre):
                                raise RuntimeError(f"{tag}: 사전 동기화 실패")
                            quiet(version)
                            request(url(version, 83, "/fixture?count=40&shared=false"), True)
                        request(url(version, 80, "/__admin/reset"), True)
                        server.version, server.members, server.target = version, members[:n], target
                        server.samples = []
                        stop, readings = threading.Event(), []
                        monitor = threading.Thread(target=scrape, args=(version, stop, readings))
                        monitor.start()
                        summary = (out / f"{tag}-k6.json").relative_to(ROOT).as_posix()
                        command = ["docker", "compose", "-f", str(ROOT / "perf/compose.yaml"), "run", "--rm", "-T",
                                   "-e", f"VUS={n}", "-e", f"RECEIVER_URL=http://host.docker.internal:{args.port}",
                                   "-e", f"SUMMARY_PATH=/work/{summary}", "k6", "run", "perf/k6/loop-313.js"]
                        process_error = None
                        stdout, stderr = "", ""
                        try:
                            process = subprocess.run(command, capture_output=True, text=True,
                                                     encoding="utf-8", errors="replace", timeout=360)
                            stdout, stderr = process.stdout, process.stderr
                        except (subprocess.TimeoutExpired, OSError) as error:
                            process_error = error
                            stdout = getattr(error, "stdout", None) or ""
                            stderr = getattr(error, "stderr", None) or ""
                        finally:
                            stop.set()
                            monitor.join()
                            # TimeoutExpired의 부분 출력은 text=True여도 bytes일 수 있다.
                            stdout = stdout.decode("utf-8", errors="replace") if isinstance(stdout, bytes) else stdout
                            stderr = stderr.decode("utf-8", errors="replace") if isinstance(stderr, bytes) else stderr
                            log = stdout + stderr
                            samples = list(server.samples)
                            raw = {"scenario": scenario, "n": n, "iteration": iteration, "version": version,
                                   "warmup": iteration < args.warmups, "samples": samples, "db_readings": readings}
                            if process_error is not None:
                                raw["error"] = f"{type(process_error).__name__}: {process_error}"
                                log += "\n" + raw["error"]
                            (out / f"{tag}-k6.log").write_text(log, encoding="utf-8")
                            (out / f"{tag}.json").write_text(json.dumps(raw, indent=2), encoding="utf-8")
                        if process_error is not None:
                            raise process_error
                        quiet(version)
                        raw["ai"] = request(url(version, 80, "/__admin/stats"))
                        raw["k6_exit_code"] = process.returncode
                        (out / f"{tag}.json").write_text(json.dumps(raw, indent=2), encoding="utf-8")
                        if process.returncode or len(samples) != n or not all(row["ok"] for row in samples):
                            raise RuntimeError(f"{tag}: k6 또는 SSE·카운트 검증 실패")
                        if any("error" in row for row in readings) or not readings:
                            raise RuntimeError(f"{tag}: DB 계측 실패")
                        results.append(raw)
                        print(f'DONE {tag} ai={raw["ai"]["analysis_requests"]} duplicates={raw["ai"]["duplicate_videos"]}', flush=True)
        metadata["complete"] = True
    finally:
        server.shutdown()
        server.server_close()
        aggregate(results, out)
        metadata["finished_at"] = time.strftime("%Y-%m-%dT%H:%M:%S%z")
        (out / "metadata.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")


def aggregate(results, out):
    groups = {}
    for row in results:
        if not row["warmup"]:
            groups.setdefault((row["scenario"], row["n"], row["version"]), []).append(row)
    summary = []
    for (scenario, n, version), rows in groups.items():
        durations = [sample["duration_ms"] for row in rows for sample in row["samples"]]
        total_seconds = sum((max(e["received_ns"] for s in row["samples"] for e in s["events"] if e["event"] == "youtube-sync.success")
                             - min(e["received_ns"] for s in row["samples"] for e in s["events"] if e["event"] == "youtube-sync.accepted")) / 1e9 for row in rows)
        summary.append({"scenario": scenario, "n": n, "version": version, "rounds": len(rows),
                        "samples": len(durations), "p50_ms": percentile(durations, .5),
                        "p95_ms": percentile(durations, .95), "throughput_syncs_s": len(durations) / total_seconds,
                        "analysis_calls": sum(row["ai"]["analysis_requests"] for row in rows),
                        "embedding_calls": sum(row["ai"]["embedding_requests"] for row in rows),
                        "duplicate_videos": sum(row["ai"]["duplicate_videos"] for row in rows),
                        "sync_errors": sum(not sample["ok"] for row in rows for sample in row["samples"]),
                        "video_failures": sum(sample["db"]["video_failed_count"] for row in rows for sample in row["samples"]),
                        "db_active_max": max(r["active"] for row in rows for r in row["db_readings"]),
                        "db_pending_max": max(r["pending"] for row in rows for r in row["db_readings"])})
    (out / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="build/perf/313/results")
    parser.add_argument("--port", type=int, default=18300)
    parser.add_argument("--base-sha", required=True)
    parser.add_argument("--candidate-sha", required=True)
    parser.add_argument("--warmups", type=int, default=2)
    parser.add_argument("--repeats", type=int, default=20)
    parser.add_argument("--concurrency", type=int, nargs="+", default=[1, 5, 10, 20], choices=[1, 5, 10, 20])
    parser.add_argument("--scenarios", nargs="+", default=["first", "resync", "shared"], choices=["first", "resync", "shared"])
    run(parser.parse_args())
