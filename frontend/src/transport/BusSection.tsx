import { useEffect, useRef, useState } from "react";
import { apiFetch } from "../session/client";
import { startVisiblePolling } from "../session/polling";
import BusMap from "./BusMap";
import { busLines, initialSection, keepPolling, nextSection } from "./busView";
import type { BusObservations, SectionView } from "./busView";
import "./transport.css";

const messages: Partial<Record<SectionView["status"], string>> = {
  preparing: "A preparar os autocarros…",
  noRoutes: "A organização ainda não indicou linhas de autocarro para este evento.",
  noData: "Ainda não há dados dos autocarros.",
  gone: "Este evento não está disponível.",
};

/**
 * US-41 D11, D12: the buses on the event's routes near the venue, polled while the page is visible (rules in
 * nextSection). The last list stays on screen while a poll fails; while the gateway asks to wait (429, 503) the
 * section says so and offers no retry; a 404 after an answer stops the polling, and before any answer it shows
 * "A preparar…" for up to six polls, since Transports' copy of a member who has just joined may still be arriving.
 */
export default function BusSection({ eventId }: { eventId: string }) {
  const [state, setState] = useState<SectionView>(initialSection);
  const [attempt, setAttempt] = useState(0);
  // The page is mounted once per event, so a retry keeps the last list and whether an answer already came
  const section = useRef(initialSection());

  useEffect(() => {
    const advance = (status: number | "error", view?: BusObservations) => {
      section.current = nextSection(section.current, status, view);
      setState(section.current);
    };
    let stop = () => {};
    stop = startVisiblePolling(async signal => {
      try {
        const response = await apiFetch(`/api/v1/events/${eventId}/bus-observations`, { signal });
        const view: BusObservations | undefined = response.status === 200 ? await response.json() : undefined;
        if (signal.aborted) return response;
        advance(response.status, view);
        if (!keepPolling(section.current)) queueMicrotask(stop);
        return response;
      } catch (error) {
        if (!signal.aborted) advance("error");
        throw error;
      }
    });
    return () => stop();
  }, [eventId, attempt]);

  const message = messages[state.status];
  return <section className="bus-section" aria-labelledby="buses-title">
    <h2 id="buses-title">Autocarros perto do recinto</h2>
    {state.status === "loading" && <p role="status">A carregar os autocarros…</p>}
    {message && <p role={state.status === "gone" ? "alert" : "status"}>{message}</p>}
    {state.status === "error" && (state.retryNow
      ? <div role="alert" className="bus-error">
        <p>Não foi possível carregar os autocarros.</p>
        <button type="button" className="member-primary" onClick={() => setAttempt(value => value + 1)}>
          Tentar novamente
        </button>
      </div>
      : <p role="status">Muitos pedidos neste momento; voltamos a tentar dentro de momentos.</p>)}
    {state.view && (state.status === "list" || state.status === "error") &&
      <BusMap venue={state.view.venue} buses={state.view.buses}/>}
    {state.view && (state.status === "list" || state.status === "error") &&
      <ul className="bus-list">{busLines(state.view).map((line, index) => <li key={index}>{line}</li>)}</ul>}
  </section>;
}
