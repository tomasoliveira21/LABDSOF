import { useEffect, useState } from "react";
import { apiFetch } from "../session/client";
import { startVisiblePolling } from "../session/polling";
import { busLines, sectionState } from "./busView";
import type { BusObservations, SectionState } from "./busView";
import "./transport.css";

type State = { status: SectionState | "loading"; view?: BusObservations };

const messages: Partial<Record<SectionState, string>> = {
  noRoutes: "A organização ainda não indicou linhas de autocarro para este evento.",
  noData: "Ainda não há dados dos autocarros.",
  gone: "Este evento não está disponível.",
};

/**
 * US-41 D11, D12: the buses on the event's routes near the venue, polled while the page is visible. The last good
 * answer stays on screen while a poll fails; a 404 (the member removed, the event suspended) stops the polling.
 */
export default function BusSection({ eventId }: { eventId: string }) {
  const [state, setState] = useState<State>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let stop = () => {};
    stop = startVisiblePolling(async signal => {
      try {
        const response = await apiFetch(`/api/v1/events/${eventId}/bus-observations`, { signal });
        const view: BusObservations | undefined = response.status === 200 ? await response.json() : undefined;
        const status = sectionState(response.status, view);
        if (signal.aborted) return response;
        setState(previous => status === "error" ? { status, view: previous.view } : { status, view });
        if (status === "gone") queueMicrotask(stop);
        return response;
      } catch (error) {
        if (!signal.aborted) setState(previous => ({ status: "error", view: previous.view }));
        throw error;
      }
    });
    return () => stop();
  }, [eventId, attempt]);

  const message = messages[state.status as SectionState];
  return <section className="bus-section" aria-labelledby="buses-title">
    <h2 id="buses-title">Autocarros perto do recinto</h2>
    {state.status === "loading" && <p role="status">A carregar os autocarros…</p>}
    {message && <p role={state.status === "gone" ? "alert" : "status"}>{message}</p>}
    {state.status === "error" && <div role="alert" className="bus-error">
      <p>Não foi possível carregar os autocarros.</p>
      <button type="button" className="member-primary" onClick={() => setAttempt(value => value + 1)}>
        Tentar novamente
      </button>
    </div>}
    {state.view && (state.status === "list" || state.status === "error") &&
      <ul className="bus-list">{busLines(state.view).map((line, index) => <li key={index}>{line}</li>)}</ul>}
  </section>;
}
