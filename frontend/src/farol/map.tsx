import { useId, useRef, useState } from "react";
import type { KeyboardEvent, PointerEvent } from "react";
import { Icon } from "./icon";
import { filters, places } from "./model";
import type { HelpRequest, Place, PlaceFilter, Point } from "./model";

const origin = { x: 460, y: 280 };
const width = 920;
const height = 560;
const bound = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));
const clampPoint = (p: Point) => ({ x: bound(p.x, 36, 884), y: bound(p.y, 28, 532) });
const venue = "M192 165 377 83 577 83 771 155 821 264 764 399 580 489 339 493 180 408 112 281Z";

type Props = {
  filter?: PlaceFilter;
  onFilter?: (next: PlaceFilter) => void;
  place?: Place;
  onPlace?: (next: Place) => void;
  requests?: HelpRequest[];
  selected?: string;
  onRequest?: (id: string) => void;
  pick?: boolean;
  point?: Point;
  onPoint?: (p: Point) => void;
  compact?: boolean;
};

export function VenueMap({ filter = "Tudo", onFilter, place, onPlace, requests = [],
  selected, onRequest, pick = false, point, onPoint, compact = false }: Props) {
  const uid = useId();
  const map = useRef<SVGSVGElement>(null);
  const drag = useRef<{ pointer: number; start: Point; offset: Point; moved: boolean } | null>(null);
  const [zoom, setZoom] = useState(1);
  const [offset, setOffset] = useState<Point>({ x: 0, y: 0 });
  const [keyboardPoint, setKeyboardPoint] = useState<Point>(origin);
  const [dragging, setDragging] = useState(false);

  function toScreen(x: number, y: number): Point {
    const svg = map.current;
    const matrix = svg?.getScreenCTM();
    if (!svg || !matrix) return origin;
    const svgPoint = svg.createSVGPoint();
    svgPoint.x = x;
    svgPoint.y = y;
    const result = svgPoint.matrixTransform(matrix.inverse());
    return { x: result.x, y: result.y };
  }

  function mark(x: number, y: number) {
    const screen = toScreen(x, y);
    onPoint?.(clampPoint({
      x: origin.x + (screen.x - origin.x - offset.x) / zoom,
      y: origin.y + (screen.y - origin.y - offset.y) / zoom,
    }));
  }

  function start(event: PointerEvent<SVGSVGElement>) {
    if ((event.target as Element).closest("[data-plot-marker]") || event.button !== 0 || !event.isPrimary) return;
    drag.current = { pointer: event.pointerId, start: toScreen(event.clientX, event.clientY), offset, moved: false };
    event.currentTarget.setPointerCapture(event.pointerId);
  }

  function move(event: PointerEvent<SVGSVGElement>) {
    const active = drag.current;
    if (!active || active.pointer !== event.pointerId) return;
    const next = toScreen(event.clientX, event.clientY);
    const dx = next.x - active.start.x;
    const dy = next.y - active.start.y;
    if (!active.moved && Math.hypot(dx, dy) < 9) return;
    active.moved = true;
    setDragging(true);
    setOffset({ x: bound(active.offset.x + dx, -840, 840), y: bound(active.offset.y + dy, -620, 620) });
  }

  function finish(event: PointerEvent<SVGSVGElement>) {
    const active = drag.current;
    if (!active || active.pointer !== event.pointerId) return;
    drag.current = null;
    setDragging(false);
    if (!active.moved && pick && event.type === "pointerup") mark(event.clientX, event.clientY);
  }

  function key(event: KeyboardEvent<SVGSVGElement>) {
    if (event.target !== event.currentTarget) return;
    const deltas: Record<string, Point> = {
      ArrowLeft: { x: -24, y: 0 }, ArrowRight: { x: 24, y: 0 },
      ArrowUp: { x: 0, y: -24 }, ArrowDown: { x: 0, y: 24 },
    };
    const delta = deltas[event.key];
    if (delta) {
      event.preventDefault();
      if (pick) setKeyboardPoint(p => clampPoint({ x: p.x + delta.x, y: p.y + delta.y }));
      else setOffset(p => ({ x: bound(p.x - delta.x, -840, 840), y: bound(p.y - delta.y, -620, 620) }));
    }
    if (pick && (event.key === "Enter" || event.key === " ")) {
      event.preventDefault();
      onPoint?.(keyboardPoint);
    }
    if (event.key === "+" || event.key === "-") {
      event.preventDefault();
      setZoom(value => bound(value + (event.key === "+" ? 0.25 : -0.25), 1, 3));
    }
  }

  const px = origin.x + offset.x - origin.x * zoom;
  const py = origin.y + offset.y - origin.y * zoom;
  return <div className={"venue " + (compact ? "venue-compact" : "")}>
    {onFilter && <div className="map-filters" aria-label="Filtrar pontos do mapa">
      {filters.map(item => <button key={item} type="button" aria-pressed={filter === item}
        onClick={() => onFilter(item)}>{item}</button>)}
    </div>}
    <div className="plot">
      <svg ref={map} viewBox={"0 0 " + width + " " + height} tabIndex={0}
        className={"venue-svg" + (pick ? " pick-mode" : "") + (dragging ? " is-dragging" : "")}
        role="group" aria-label={pick ? "Marcar localização. Usa as setas e Enter ou toca no mapa." : "Planta interativa do recinto"}
        onPointerDown={start} onPointerMove={move} onPointerUp={finish} onPointerCancel={finish} onLostPointerCapture={finish}
        onKeyDown={key} onWheel={event => {
          if (event.ctrlKey || event.metaKey) return;
          if (map.current === document.activeElement) setZoom(value => bound(value + (event.deltaY < 0 ? .15 : -.15), 1, 3));
        }}>
        <defs>
          <pattern id={uid + "-dot"} width="22" height="22" patternUnits="userSpaceOnUse">
            <circle cx="2" cy="2" r="1.1" fill="#416389" opacity=".40"/>
          </pattern>
          <linearGradient id={uid + "-ground"} x2="0" y2="1">
            <stop offset="0%" stopColor="#213d5b"/><stop offset="100%" stopColor="#182f4a"/>
          </linearGradient>
        </defs>
        <rect width={width} height={height} fill="#101f35"/>
        <rect width={width} height={height} fill={"url(#" + uid + "-dot)"}/>
        <g transform={"translate(" + px + " " + py + ") scale(" + zoom + ")"}>
          <g className="plot-geometry">
            <path d="M-20 147 370 57 585 57 868 156 941 235M-20 406 142 413 325 515 592 514 831 397 956 270"
              fill="none" stroke="#355474" strokeWidth="14" opacity=".45"/>
            <path d="M-20 147 370 57 585 57 868 156 941 235M-20 406 142 413 325 515 592 514 831 397 956 270"
              fill="none" stroke="#7590ab" strokeWidth="1" strokeDasharray="5 8" opacity=".5"/>
            <path d={venue} fill={"url(#" + uid + "-ground)"} stroke="#5a7895" strokeWidth="2"/>
            <path d={venue} fill={"url(#" + uid + "-dot)"}/>
            <path d="M193 173 381 93 576 93 763 161 811 265 758 392 576 479 342 483 187 401 123 282Z"
              fill="none" stroke="#7492aa" strokeWidth="1" strokeDasharray="3 5" opacity=".52"/>
            <path d="M439 80v106l38 37v112l-117 104v57M262 310l103-1 43-34h221l103 9M480 338l61 73 108 34m-182-20 114 27"
              fill="none" stroke="#486a8a" strokeWidth="23" strokeLinecap="round" strokeLinejoin="round"/>
            <path d="M439 85v101l38 37v112L360 439v50M262 309h103l43-34h221l103 9m-252 54 61 73 108 34"
              fill="none" stroke="#a1b5c9" strokeWidth="1.2" strokeDasharray="4 7" opacity=".7"/>
            <path d="M467 197h109l54 48v79l-53 49-94-2-55-52v-78Z" fill="#1a334f" stroke="#547494" strokeWidth="1.3"/>
            <path d="m495 206 69 0 56 48v60l-49 47h-82l-50-47v-66Z" fill="none" stroke="#395b7d"/>
            <path d="m666 186 68 11 25 37-10 35h-76l-20-29Z" fill="#3c536e" stroke="#7993ac" strokeWidth="1.5"/>
            <path d="m679 205 47 8 14 23-6 15h-52l-10-15Z" fill="#2b435f" stroke="#839ab1"/>
            <path d="m217 259 78-25 31 33-9 71-80 22-29-31Z" fill="#294864" stroke="#678aa6" strokeWidth="1.5"/>
            <path d="m608 381 60-9 35 31-18 33-65 10-24-30Z" fill="#28465f" stroke="#6885a0"/>
            <path d="m299 170 61-25 47 28-16 41-64 16-36-25Z" fill="#2c4866" stroke="#587e9f"/>
            <path d="m193 181 36 17 11 26-52 24-29-11m467-107 37 14 17 28m76 127-10 47-43 20m-439 48 41 21m276-296 34 12"
              fill="none" stroke="#607f9b" strokeWidth="7" opacity=".50" strokeLinecap="round"/>
            <g fill="#51718c" opacity=".55">
              <circle cx="151" cy="293" r="6"/><circle cx="165" cy="308" r="5"/><circle cx="165" cy="279" r="4"/>
              <circle cx="772" cy="306" r="7"/><circle cx="786" cy="285" r="5"/><circle cx="775" cy="327" r="4"/>
              <circle cx="292" cy="404" r="5"/><circle cx="283" cy="388" r="6"/><circle cx="573" cy="428" r="6"/>
            </g>
            <path d="M426 92h27m-27 9h27M347 477h27m-27 9h27" stroke="#e0af5a" strokeWidth="2.5"/>
            <text x="529" y="307" textAnchor="middle" className="zone-text">PRAÇA CENTRAL</text>
            <text x="213" y="129" transform="rotate(-13 213 129)" className="road-text">AVENIDA DO PARQUE</text>
            <text x="492" y="530" className="road-text">ZONA DE TRANSPORTES</text>
          </g>
          {!pick && places.filter(item => filter === "Tudo" || item.type === filter).map(item =>
            <Marker key={item.id} point={item} label={item.name} icon={item.icon} active={place?.id === item.id}
              onActivate={onPlace ? () => onPlace(item) : undefined}/>)}
          {pick && places.map(item => <Marker key={item.id} point={item} label={item.name} icon={item.icon} quiet/>)}
          {requests.map(item => <Marker key={item.id} point={item.point} label={"Pedido " + item.id}
            icon={item.status === "resolved" ? "check" : "alert"} active={selected === item.id}
            warning={item.priority === "Urgente"} request
            onActivate={onRequest ? () => onRequest(item.id) : undefined}/>)}
          {point && <Marker point={point} label="Localização assinalada" icon="pin" active/>}
          {pick && <circle className="keyboard-crosshair" cx={keyboardPoint.x} cy={keyboardPoint.y}
            r="12" fill="none" stroke="#fff" strokeWidth="2" strokeDasharray="3 3"/>}
        </g>
      </svg>
      <div className="map-north" aria-hidden="true"><Icon name="north" size={16}/><span>N</span></div>
      <div className="zoom-tools" aria-label="Controlos do mapa">
        <button type="button" aria-label="Ampliar mapa" disabled={zoom >= 3} onClick={() => setZoom(v => bound(v + .25, 1, 3))}><Icon name="plus" size={17}/></button>
        <button type="button" aria-label="Reduzir mapa" disabled={zoom <= 1} onClick={() => setZoom(v => bound(v - .25, 1, 3))}><Icon name="minus" size={17}/></button>
        <button type="button" aria-label="Repor mapa" onClick={() => {setZoom(1); setOffset({x: 0, y: 0});}}><Icon name="reset" size={17}/></button>
      </div>
    </div>
    {!compact && <div className="map-caption"><span><Icon name="info" size={14}/> Planta ilustrativa</span><span>Recinto da Queima</span></div>}
  </div>;
}

function Marker({ point, label, icon, active, warning, request, quiet, onActivate }: {
  point: Point; label: string; icon: string; active?: boolean; warning?: boolean;
  request?: boolean; quiet?: boolean; onActivate?: () => void;
}) {
  const cls = ["plot-marker", active && "plot-selected", warning && "plot-urgent", request && "plot-request", quiet && "plot-quiet"].filter(Boolean).join(" ");
  return <g transform={"translate(" + point.x + " " + point.y + ")"} data-plot-marker={onActivate ? true : undefined}
    className={cls} role={onActivate ? "button" : undefined} tabIndex={onActivate ? 0 : undefined}
    aria-label={onActivate ? label : undefined} aria-pressed={onActivate ? !!active : undefined} onClick={onActivate}
    onKeyDown={onActivate ? event => {
      if (event.key === "Enter" || event.key === " ") {event.preventDefault(); onActivate();}
    } : undefined}>
    <circle r="26" className="pin-halo"/><circle r="18" className="pin-core"/>
    <g transform="translate(-10 -10)"><Icon name={icon} size={20}/></g>
    <text x="0" y="35" textAnchor="middle">{label}</text>
  </g>;
}
