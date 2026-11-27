import { useEffect, useState } from "react";
import { apiFetch } from "./client";
import { canOpen } from "./eventPath";

export const eventStatuses = {
  Draft: "Rascunho",
  Published: "Publicado",
  Suspended: "Suspenso",
  Closed: "Encerrado",
};

export type Membership = {
  eventId: string;
  eventName?: string;
  eventStatus?: keyof typeof eventStatuses;
  role: "EventAdmin" | "RegularUser";
  teamIds: string[];
};

type State = { status: "loading" | "error" } | { status: "ready"; events: Membership[] };

export default function MembershipList() {
  const [state, setState] = useState<State>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const request = new AbortController();
    async function loadEvents() {
      try {
        const response = await apiFetch("/api/v1/me/memberships", { signal: request.signal });
        if (!response.ok) throw new Error("Membership request failed");
        const events: Membership[] = await response.json();
        if (!request.signal.aborted) setState({ status: "ready", events });
      } catch {
        if (!request.signal.aborted) setState({ status: "error" });
      }
    }
    void loadEvents();
    return () => request.abort();
  }, [attempt]);

  return <section aria-labelledby="member-events" className="member-events">
    <h2 id="member-events">Os meus eventos</h2>
    {state.status === "loading" && <p role="status">A carregar os teus eventos…</p>}
    {state.status === "error" && <>
      <p role="alert">Não foi possível carregar os teus eventos.</p>
      <button type="button" className="member-primary" onClick={() => {
        setState({ status: "loading" });
        setAttempt(value => value + 1);
      }}>Tentar novamente</button>
    </>}
    {state.status === "ready" && (state.events.length === 0 ? <p>Ainda não pertences a nenhum evento.</p> :
      <ul className="member-event-list">{state.events.map(event => <li key={event.eventId}>
        {canOpen(event)
          ? <a href={"/events/" + event.eventId}><strong>{event.eventName ?? "Evento sem detalhes disponíveis"}</strong></a>
          : <strong>{event.eventName ?? "Evento sem detalhes disponíveis"}</strong>}
        <span>{(event.eventStatus && eventStatuses[event.eventStatus]) ?? "Estado indisponível"} · {event.role === "EventAdmin" ? "Administração" : "Participação"}</span>
      </li>)}</ul>)}
  </section>;
}
