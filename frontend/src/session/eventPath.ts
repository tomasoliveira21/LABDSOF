/** US-41 D9: the event's page is /events/<event id>, the id in the canonical lower-case form the API writes. */
const EVENT_PAGE = /^\/events\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\/?$/;

export type OpenableMembership = { role: "EventAdmin" | "RegularUser"; eventStatus?: string };

export function eventIdFromPath(pathname: string): string | undefined {
  return EVENT_PAGE.exec(pathname)?.[1];
}

/** US-41 D5, as Transports decides it: an EventAdmin also opens a draft; nobody opens a suspended event. */
export function canOpen(membership: OpenableMembership): boolean {
  const open = membership.role === "EventAdmin" ? ["Draft", "Published", "Closed"] : ["Published", "Closed"];
  return membership.eventStatus !== undefined && open.includes(membership.eventStatus);
}
