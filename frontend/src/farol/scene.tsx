import { useEffect, useRef } from "react";
import type { KeyboardEvent, PointerEvent } from "react";
import type { Landmark } from "./presentation";

type Vector = [number, number, number];
type Face = { points: Vector[]; fill: string; edge?: string };
type Surface = { x: number; y: number; z: number };
const hues = {
  concrete: ["#2f4d66", "#23384b", "#456481"] as const,
  building: ["#385f7b", "#294559", "#83a1b2"] as const,
  roof: ["#172e46", "#16283b", "#325476"] as const,
  amber: ["#be8847", "#926334", "#f7cd78"] as const,
};

function prism(faces: Face[], footprint: [number, number][], bottom: number, top: number, palette: readonly string[]) {
  const lower = footprint.map(([x, y]): Vector => [x, y, bottom]);
  const upper = footprint.map(([x, y]): Vector => [x, y, top]);
  lower.forEach((a, index) => {
    const next = (index + 1) % footprint.length;
    faces.push({ points: [a, lower[next], upper[next], upper[index]], fill: palette[index % 2], edge: "#213950" });
  });
  faces.push({ points: upper, fill: palette[2], edge: "#365570" });
}
function box(faces: Face[], x: number, y: number, w: number, d: number, z: number, h: number, colors: readonly string[]) {
  prism(faces, [[x, y], [x + w, y], [x + w, y + d], [x, y + d]], z, z + h, colors);
}
function disk(faces: Face[], x: number, y: number, radius: number, z: number, h: number, colors: readonly string[], sides = 8) {
  prism(faces, Array.from({ length: sides }, (_, i) => {
    const angle = i * Math.PI * 2 / sides;
    return [x + Math.cos(angle) * radius, y + Math.sin(angle) * radius];
  }), z, z + h, colors);
}

function buildScene(active?: Landmark) {
  const faces: Face[] = [];
  const slab: [number, number][] = [[-166,-60],[-78,-105],[72,-108],[168,-64],[179,28],[111,91],[-51,101],[-154,52]];
  prism(faces, slab, -14, 0, ["#19324c","#14283f","#233e57"]);
  disk(faces, -5, -5, 69, 0, 2, hues.concrete);
  disk(faces, -5, -5, 60, 2, .5, ["#314c66", "#314c66", "#314c66"]);
  box(faces, -9, -100, 18, 51, 0, 1, hues.concrete);
  box(faces, -22, 38, 17, 50, 0, 1, hues.concrete);
  box(faces, -137, -6, 101, 14, 0, 1, hues.concrete);
  box(faces, 20, -6, 126, 12, 0, 1, hues.concrete);

  const stage = active === "stage" ? hues.amber : hues.building;
  box(faces, 69, -35, 69, 52, 1, 7, stage);
  for (const [x, y] of [[72,-32],[129,-32],[72,10],[129,10]]) box(faces, x, y, 4, 4, 8, 36, stage);
  box(faces, 68, -36, 72, 51, 44, 5, active === "stage" ? hues.amber : hues.roof);
  box(faces, 119, -28, 5, 34, 13, 17, hues.building);
  box(faces, 74, 13, 15, 6, 3, 8, hues.roof);
  box(faces, 121, 13, 15, 6, 3, 8, hues.roof);

  const gates = active === "entry" ? hues.amber : hues.building;
  box(faces, -19, -91, 5, 7, 0, 26, gates);
  box(faces, 14, -91, 5, 7, 0, 26, gates);
  box(faces, -19, -91, 38, 7, 26, 5, hues.amber);
  box(faces, -37, 77, 5, 6, 0, 21, hues.building);
  box(faces, 8, 77, 5, 6, 0, 21, hues.building);
  box(faces, -37, 77, 50, 6, 21, 4, hues.amber);

  for (const [x,y] of [[-115,-34],[-114,21],[62,56],[95,48]]) {
    box(faces, x, y, 28, 23, 1, 9, hues.building);
    const tip: Vector = [x+14,y+11.5,22];
    const rim: Vector[] = [[x,y,10],[x+28,y,10],[x+28,y+23,10],[x,y+23,10]];
    rim.forEach((a,i) => faces.push({ points: [a,rim[(i+1)%4],tip], fill: i % 2 ? "#64839b" : "#9ab3bf", edge: "#3f5e78" }));
  }
  disk(faces, -78, -69, 12, 0, 4, hues.concrete);
  disk(faces, -78, -69, 7.5, 4, 38, hues.building);
  disk(faces, -78, -69, 10, 42, 10, hues.amber);
  disk(faces, -78, -69, 12, 52, 3, hues.roof);
  faces.push({ points: [[-74,-69,46],[8,-124,43],[45,-82,42]], fill: "#f9cb742c" });
  for (const [x,y] of [[-144,20],[-132,48],[-146,-12],[122,38],[153,4],[138,-45],[45,76],[-75,76],[-53,-82],[-47,40],[42,29]]) {
    disk(faces, x, y, 3.4, 0, 6, hues.building, 6);
    disk(faces, x, y, 5, 6, 7, ["#284e50","#274645","#44726d"], 6);
  }
  return faces;
}

function project([x,y,z]: Vector, angle: number, width: number, height: number, zoom: number): Surface {
  const sx = x * Math.cos(angle) - y * Math.sin(angle);
  const sy = x * Math.sin(angle) + y * Math.cos(angle);
  return { x: width/2 + sx * zoom, y: height * .56 + (sy * .58 - z * .82) * zoom, z: sy * .82 + z * .58 };
}
function draw(ctx: CanvasRenderingContext2D, canvas: HTMLCanvasElement, angle: number, width: number, height: number, density: number, faces: Face[]) {
  const w = Math.round(width * density);
  const h = Math.round(height * density);
  if (canvas.width !== w || canvas.height !== h) { canvas.width = w; canvas.height = h; }
  ctx.setTransform(density,0,0,density,0,0);
  ctx.clearRect(0,0,width,height);
  const zoom = Math.min(width / 424, height / 225) * .98;
  const colored = faces.map(face => {
    const points = face.points.map(vertex => project(vertex, angle, width, height, zoom));
    return { ...face, points, depth: points.reduce((total, p) => total + p.z, 0)/points.length };
  }).sort((a,b) => a.depth - b.depth);
  ctx.fillStyle = "#091629";
  ctx.beginPath(); ctx.ellipse(width * .5,height * .77,Math.min(width * .34,height * .74),height * .14,0,0,Math.PI * 2); ctx.fill();
  ctx.lineJoin = "round";
  for (const { points, fill, edge } of colored) {
    ctx.beginPath();
    points.forEach((p, index) => { if (index) ctx.lineTo(p.x,p.y); else ctx.moveTo(p.x,p.y); });
    ctx.closePath();
    ctx.fillStyle = fill;
    ctx.fill();
    if (edge) { ctx.strokeStyle = edge; ctx.lineWidth = .65; ctx.stroke(); }
  }
}

export default function Diorama({ landmark, unavailable }: { landmark?: Landmark; unavailable: () => void }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const angle = useRef(-.57);
  const target = useRef(-.57);
  const paint = useRef<() => void>(() => {});
  const active = useRef<Landmark>(undefined);
  const drag = useRef<{ id: number; start: number; angle: number } | null>(null);
  const unavailableRef = useRef(unavailable);
  useEffect(() => { unavailableRef.current = unavailable; }, [unavailable]);

  useEffect(() => {
    const surface = canvas.current;
    if (!surface || typeof ResizeObserver === "undefined") { unavailableRef.current(); return; }
    let ctx: CanvasRenderingContext2D | null;
    try { ctx = surface.getContext("2d"); } catch { ctx = null; }
    if (!ctx) { unavailableRef.current(); return; }
    const context = ctx;
    let raf = 0;
    let geometry = buildScene();
    let oldLandmark: Landmark | undefined;
    function animate() {
      raf = 0;
      if (!surface) return;
      if (oldLandmark !== active.current) {
        oldLandmark = active.current;
        geometry = buildScene(active.current);
      }
      const delta = target.current - angle.current;
      angle.current += Math.abs(delta) > .002 ? delta * .18 : delta;
      const bounds = surface.getBoundingClientRect();
      if (bounds.width && bounds.height) {
        draw(context, surface, angle.current, bounds.width, bounds.height, Math.min(window.devicePixelRatio || 1, 1.7), geometry);
        surface.dataset.ready = "true";
        surface.dataset.angle = angle.current.toFixed(3);
      }
      if (Math.abs(delta) > .002) requestDraw();
    }
    function requestDraw() { if (!raf) raf = requestAnimationFrame(animate); }
    paint.current = requestDraw;
    const observer = new ResizeObserver(requestDraw);
    observer.observe(surface);
    surface.addEventListener("contextlost", unavailableRef.current);
    const onLost = unavailableRef.current;
    requestDraw();
    return () => {
      observer.disconnect();
      cancelAnimationFrame(raf);
      surface.removeEventListener("contextlost", onLost);
      paint.current = () => {};
    };
  }, []);

  useEffect(() => {
    active.current = landmark;
    if (landmark) {
      const desired = landmark === "stage" ? -.96 : .39;
      const delta = Math.atan2(Math.sin(desired - angle.current), Math.cos(desired - angle.current));
      target.current = angle.current + delta;
      paint.current();
    }
  }, [landmark]);

  function move(event: PointerEvent<HTMLCanvasElement>) {
    if (drag.current?.id !== event.pointerId) return;
    target.current = drag.current.angle + (event.clientX - drag.current.start) * .013;
    paint.current();
  }
  function finish(event: PointerEvent<HTMLCanvasElement>) {
    if (drag.current?.id === event.pointerId) drag.current = null;
  }
  function key(event: KeyboardEvent<HTMLCanvasElement>) {
    if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
    event.preventDefault();
    target.current += event.key === "ArrowRight" ? .29 : -.29;
    paint.current();
  }
  return <canvas ref={canvas} className="diorama-canvas" tabIndex={0} role="img" aria-label="Miniatura interativa. Arrasta ou usa as setas para rodar."
    onPointerDown={event => {
      if (!event.isPrimary || event.button !== 0) return;
      drag.current = { id: event.pointerId, start: event.clientX, angle: angle.current };
      event.currentTarget.setPointerCapture(event.pointerId);
    }} onPointerMove={move} onPointerUp={finish} onPointerCancel={finish} onLostPointerCapture={finish} onKeyDown={key}/>;
}
