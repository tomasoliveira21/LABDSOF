const interval = 10_000;
const backoff = 20_000;

export function nextPollDelay(response?: Response, now = Date.now(), random = Math.random()) {
  const jitter = random * 1000;
  if (response?.status !== 429 && response?.status !== 503) return interval + jitter;

  const retryAfter = response.headers.get("Retry-After");
  const retryDelay = retryAfter
    ? /^\d+$/.test(retryAfter) ? Number(retryAfter) * 1000 : Date.parse(retryAfter) - now
    : 0;
  return Math.max(backoff, Number.isFinite(retryDelay) ? retryDelay : 0) + jitter;
}

/** Poll one request at a time while visible. A pending backoff survives a visibility change. */
export function startVisiblePolling(poll: (signal: AbortSignal) => Promise<Response | void>) {
  let due = Date.now();
  let timer: number | undefined;
  let running: AbortController | undefined;
  let stopped = false;

  function schedule() {
    window.clearTimeout(timer);
    if (stopped || document.hidden || running) return;
    timer = window.setTimeout(run, Math.min(2_147_483_647, Math.max(0, due - Date.now())));
  }

  async function run() {
    if (stopped || document.hidden || running) return;
    if (due > Date.now()) {
      schedule();
      return;
    }
    running = new AbortController();
    try {
      const response = await poll(running.signal);
      due = Date.now() + nextPollDelay(response || undefined);
    } catch {
      due = Date.now() + nextPollDelay();
    } finally {
      running = undefined;
      schedule();
    }
  }

  document.addEventListener("visibilitychange", schedule);
  schedule();
  return () => {
    stopped = true;
    window.clearTimeout(timer);
    running?.abort();
    document.removeEventListener("visibilitychange", schedule);
  };
}
