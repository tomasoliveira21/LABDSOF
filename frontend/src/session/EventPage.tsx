import { useEffect, useState } from "react";
import { apiFetch } from "./client";
import { canOpen } from "./eventPath";
import { eventStatuses } from "./MembershipList";
import type { Membership } from "./MembershipList";

type State = { status: "loading" | "error" | "gone" } | { status: "ready"; event: Membership };

/** US-41 D9: an event's page, reached from "Os meus eventos"; its sections are added by their stories. */
export default function EventPage({ eventId }: { eventId: string }) {
  const [state, setState] = useState<State>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const request = new AbortController();
    async function loadEvent() {
      try {
        const response = await apiFetch("/api/v1/me/memberships", { signal: request.signal });
        if (!response.ok) throw new Error("Membership request failed");
        const events: Membership[] = await response.json();
        const event = events.find(candidate => candidate.eventId === eventId);
        if (!request.signal.aborted) setState(event && canOpen(event) ? { status: "ready", event } : { status: "gone" });
      } catch {
        if (!request.signal.aborted) setState({ status: "error" });
      }
    }
    void loadEvent();
    return () => request.abort();
  }, [eventId, attempt]);

  return <section className="member-card event-page" aria-labelledby="event-title">
    <a href="/" className="event-back">← Os meus eventos</a>
    {state.status === "loading" && <p role="status">A carregar o evento…</p>}
    {state.status === "error" && <>
      <p role="alert">Não foi possível carregar o evento.</p>
      <button type="button" className="member-primary" onClick={() => {
        setState({ status: "loading" });
        setAttempt(value => value + 1);
      }}>Tentar novamente</button>
    </>}
    {state.status === "gone" && <p role="alert">Este evento não está disponível.</p>}
    {state.status === "ready" && <>
      <h1 id="event-title">{state.event.eventName ?? "Evento sem detalhes disponíveis"}</h1>
      <p className="event-status">
        {(state.event.eventStatus && eventStatuses[state.event.eventStatus]) ?? "Estado indisponível"}
        {" · "}{state.event.role === "EventAdmin" ? "Administração" : "Participação"}
      </p>
    </>}
  </section>;
}
