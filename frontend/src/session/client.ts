import Keycloak from "keycloak-js";

const config = {
  url: import.meta.env.VITE_KEYCLOAK_URL || "http://localhost:8180",
  realm: "farol",
  clientId: "farol-web",
};
const gateway = import.meta.env.VITE_GATEWAY_URL || "http://localhost:8080";
const keycloak = new Keycloak(config);
const callback = () => new URL("/callback", window.location.origin).href;

export type Account = { userId: string; email: string; displayName: string; globalRoles: string[] };

let initialized: Promise<boolean> | undefined;
export function initializeSession() {
  initialized ??= keycloak.init({
    onLoad: "check-sso",
    checkLoginIframe: false,
    silentCheckSsoRedirectUri: new URL("/silent-check-sso.html", window.location.origin).href,
    silentCheckSsoFallback: false,
    redirectUri: callback(),
    pkceMethod: "S256",
  });
  return initialized;
}

export function login() {
  return keycloak.login({ redirectUri: callback() });
}

export function logout() {
  return keycloak.logout({ redirectUri: new URL("/", window.location.origin).href });
}

export function onSessionEnd(callback: () => void) {
  keycloak.onAuthLogout = callback;
  keycloak.onAuthRefreshError = () => keycloak.clearToken();
  keycloak.onTokenExpired = () => { void keycloak.updateToken(30).catch(() => keycloak.clearToken()); };
  return () => {
    keycloak.onAuthLogout = undefined;
    keycloak.onAuthRefreshError = undefined;
    keycloak.onTokenExpired = undefined;
  };
}

export async function apiFetch(path: `/api/v1/${string}`, init: RequestInit = {}) {
  const origin = new URL(gateway).origin;
  const target = new URL(path, origin);
  if (target.origin !== origin || !target.pathname.startsWith("/api/v1/")) {
    throw new Error("Invalid Farol API path");
  }
  if (!keycloak.authenticated) throw new Error("No active session");
  try {
    await keycloak.updateToken(30);
  } catch (error) {
    keycloak.clearToken();
    throw error;
  }
  if (!keycloak.token) throw new Error("No active session");
  const headers = new Headers(init.headers);
  headers.set("Authorization", `Bearer ${keycloak.token}`);
  headers.set("Accept", "application/json");
  return fetch(target, { ...init, headers, credentials: "omit" });
}
