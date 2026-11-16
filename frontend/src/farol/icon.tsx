import type { ReactNode } from "react";

const paths: Record<string, ReactNode> = {
  mark: <><path d="M12 2v3m-4 6 4-4 4 4-1 11H9L8 11Zm1 0h6M3 8l4 2m14-2-4 2M3 13h3m12 0h3M8 22h8"/><path d="M11 15h2v4h-2z"/></>,
  map: <><path d="m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3V6Z"/><path d="M9 3v15m6-12v15"/></>,
  pin: <><path d="M19 10c0 5-7 10-7 10S5 15 5 10a7 7 0 1 1 14 0Z"/><circle cx="12" cy="10" r="2"/></>,
  bell: <><path d="M18 8a6 6 0 1 0-12 0c0 6-3 6-3 8h18c0-2-3-2-3-8m-8 11a2 2 0 0 0 4 0"/></>,
  bus: <><rect x="5" y="3" width="14" height="16" rx="3"/><path d="M5 10h14M8 19v2m8-2v2M8 15h.01M16 15h.01M9 6h6"/></>,
  list: <><path d="M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01"/></>,
  cube: <><path d="m12 3 9 5v8l-9 5-9-5V8l9-5Zm0 9 9-4M3 8l9 4v9"/></>,
  user: <><circle cx="12" cy="7" r="3"/><path d="M5 20v-2a5 5 0 0 1 5-5h4a5 5 0 0 1 5 5v2"/></>,
  monitor: <><rect x="3" y="4" width="18" height="13" rx="2"/><path d="M12 17v4m-4 0h8"/></>,
  shield: <path d="m12 22 4-3c3-2 4-5 4-8V5l-8-3-8 3v6c0 3 1 6 4 8l4 3Z"/>,
  cross: <path d="M12 5v14M5 12h14"/>,
  enter: <path d="M11 4h8v16h-8m-8-8h11m-4-4 4 4-4 4"/>,
  music: <><path d="M9 17V5l11-2v12M9 9l11-2"/><ellipse cx="6" cy="17" rx="3" ry="2"/><ellipse cx="17" cy="15" rx="3" ry="2"/></>,
  wc: <><circle cx="6" cy="5" r="1.5"/><circle cx="17" cy="5" r="1.5"/><path d="M6 9v12m-3-7v-3a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v3m8-5-4 8h8l-4-8Zm-2 8v4m4-4v4"/></>,
  info: <><circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10h.01"/></>,
  "arrow-right": <path d="M4 12h16m-6-6 6 6-6 6"/>,
  chevron: <path d="m7 10 5 5 5-5"/>,
  chevronRight: <path d="m9 6 6 6-6 6"/>,
  check: <path d="m5 12 4 4L19 6"/>,
  clock: <><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></>,
  route: <><circle cx="5" cy="18" r="2"/><circle cx="19" cy="6" r="2"/><path d="M7 18h8a3 3 0 1 0 0-6H9a3 3 0 0 1 0-6h8"/></>,
  plus: <path d="M12 5v14M5 12h14"/>,
  minus: <path d="M5 12h14"/>,
  reset: <><path d="M4 7V3m0 4h4m-4 0a9 9 0 1 1-1 9"/><path d="M12 8v8m-4-4h8"/></>,
  north: <path d="m12 3 7 17-7-5-7 5 7-17Z"/>,
  alert: <><path d="M12 3 2 20h20L12 3Zm0 6v5m0 3h.01"/></>,
  x: <path d="m6 6 12 12m0-12L6 18"/>,
  spark: <><path d="m12 2 2.5 7.5L22 12l-7.5 2.5L12 22l-2.5-7.5L2 12l7.5-2.5L12 2Z"/></>,
  logout: <path d="M10 4H4v16h6m0-8h11m-4-4 4 4-4 4"/>,
};
export function Icon({ name, size = 20 }: { name: string; size?: number }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
    strokeWidth="1.65" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>;
}
