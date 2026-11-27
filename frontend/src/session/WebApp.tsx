import { useEffect, useState } from "react";
import { Icon } from "../farol/icon";
import EventPage from "./EventPage";
import MembershipList from "./MembershipList";
import { apiFetch, initializeSession, login, logout, onSessionEnd } from "./client";
import { eventIdFromPath } from "./eventPath";
import type { Account } from "./client";
import "./session.css";

const loginError = "Não foi possível iniciar sessão. Tenta novamente.";

export default function WebApp() {
  const [account, setAccount] = useState<Account>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [leaving, setLeaving] = useState(false);
  const staff = window.location.pathname === "/staff";
  const eventId = eventIdFromPath(window.location.pathname);

  useEffect(() => {
    const request = new AbortController();
    const unsubscribe = onSessionEnd(() => {
      request.abort();
      setAccount(undefined);
      setLoading(false);
    });
    async function restore() {
      try {
        if (await initializeSession()) {
          const response = await apiFetch("/api/v1/me", { signal: request.signal });
          if (!response.ok) throw new Error("Account request failed");
          const current: Account = await response.json();
          if (!request.signal.aborted) setAccount(current);
        }
      } catch {
        if (!request.signal.aborted) setError(loginError);
      } finally {
        if (!request.signal.aborted) {
          if (window.location.pathname === "/callback") window.history.replaceState(null, "", "/");
          setLoading(false);
        }
      }
    }
    void restore();
    return () => { request.abort(); unsubscribe(); };
  }, []);

  async function signIn() {
    setError("");
    try { await login(); } catch { setError(loginError); }
  }

  async function signOut() {
    setLeaving(true);
    try {
      await logout();
    } catch {
      setLeaving(false);
      setError("Não foi possível terminar a sessão. Tenta novamente.");
    }
  }

  return <div className="member-app">
    <a className="skip" href="#conteudo">Saltar para o conteúdo</a>
    <header className="member-header">
      <a className="wordmark" href="/" aria-label="Farol, início">
        <span className="brand-symbol"><Icon name="mark" size={24}/></span> Farol
      </a>
      {account && <button type="button" onClick={() => void signOut()} disabled={leaving}>
        {leaving ? "A terminar sessão…" : "Terminar sessão"}
      </button>}
    </header>
    <main id="conteudo" className="member-main">
      {account ? <>
        <nav aria-label="Área" className="member-nav">
          <a href="/" aria-current={!staff ? "page" : undefined}>Participante</a>
          <a href="/staff" aria-current={staff ? "page" : undefined}>Equipa</a>
        </nav>
        {eventId ? <EventPage key={eventId} eventId={eventId}/> : <section className="member-card">
          <h1>{staff ? "Área da equipa" : "Área do participante"}</h1>
          <p>Sessão iniciada como <strong>{account.displayName}</strong>.</p>
          <MembershipList key={account.userId}/>
          {error && <p role="alert">{error}</p>}
        </section>}
      </> : <section className="member-card">
        <h1>Entrar no Farol</h1>
        {loading ? <p role="status">A verificar a sessão…</p> : <>
          {error && <p role="alert">{error}</p>}
          <button className="member-primary" type="button" onClick={() => void signIn()}>Iniciar sessão</button>
        </>}
      </section>}
    </main>
  </div>;
}
