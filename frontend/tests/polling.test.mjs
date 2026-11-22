import assert from "node:assert/strict";
import test from "node:test";
import { nextPollDelay } from "../src/session/polling.ts";

test("normal polls have a ten-second interval plus jitter", () => {
  assert.equal(nextPollDelay(undefined, 0, 0.5), 10_500);
});

test("rate limiting and unavailable responses use Retry-After and conservative backoff", () => {
  const limited = new Response(null, { status: 429, headers: { "Retry-After": "40" } });
  const unavailable = new Response(null, { status: 503, headers: { "Retry-After": "n/a" } });
  const date = new Response(null, { status: 503, headers: { "Retry-After": new Date(45_000).toUTCString() } });
  assert.equal(nextPollDelay(limited, 0, 0), 40_000);
  assert.equal(nextPollDelay(unavailable, 0, 0), 20_000);
  assert.equal(nextPollDelay(date, 0, 0), 45_000);
});
