import assert from "node:assert/strict";
import test from "node:test";
import { canOpen, eventIdFromPath, isEventPath } from "../src/session/eventPath.ts";

const id = "3f0c6b1e-5a3b-4f0e-9d0e-6a1b2c3d4e5f";

test("US-41 D9 an event's page is /events/<id>, with or without a final slash", () => {
  assert.equal(eventIdFromPath(`/events/${id}`), id);
  assert.equal(eventIdFromPath(`/events/${id}/`), id);
});

test("US-41 D9 any other path is not an event's page", () => {
  for (const path of ["/events/", "/events", "/events/not-a-uuid", `/events/${id.toUpperCase()}`,
    `/events/${id}/x`, `/x/events/${id}`, "/staff", "/", `/events/${id}extra`]) {
    assert.equal(eventIdFromPath(path), undefined, path);
  }
});

test("US-41 D5 a RegularUser opens a published or closed event", () => {
  assert.equal(canOpen({ role: "RegularUser", eventStatus: "Published" }), true);
  assert.equal(canOpen({ role: "RegularUser", eventStatus: "Closed" }), true);
  for (const eventStatus of ["Draft", "Suspended", undefined]) {
    assert.equal(canOpen({ role: "RegularUser", eventStatus }), false, String(eventStatus));
  }
});

test("US-41 D5 an EventAdmin opens a draft, published or closed event", () => {
  for (const eventStatus of ["Draft", "Published", "Closed"]) {
    assert.equal(canOpen({ role: "EventAdmin", eventStatus }), true, eventStatus);
  }
  for (const eventStatus of ["Suspended", undefined]) {
    assert.equal(canOpen({ role: "EventAdmin", eventStatus }), false, String(eventStatus));
  }
});

test("US-41 D9 a path under /events/ that is not an event's page is still shown as an event not found", () => {
  for (const path of ["/events", "/events/", "/events/not-a-uuid", `/events/${id.toUpperCase()}`, `/events/${id}/x`]) {
    assert.equal(isEventPath(path), true, path);
  }
  for (const path of ["/", "/staff", "/demo", "/eventsx", `/x/events/${id}`]) {
    assert.equal(isEventPath(path), false, path);
  }
});
