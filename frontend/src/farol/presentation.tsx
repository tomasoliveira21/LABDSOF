import { lazy, Suspense, useEffect, useState } from "react";
import { Icon } from "./icon";

const Diorama = lazy(() => import("./scene"));
export type Landmark = "stage" | "entry";

export function VenuePresentation({ welcome = false }: { welcome?: boolean }) {
  const [enabled, setEnabled] = useState(false);
  const [available, setAvailable] = useState(true);
  const [landmark, setLandmark] = useState<Landmark>();
  useEffect(() => {
    const preference = window.matchMedia("(prefers-reduced-motion: reduce)");
    let timer: number | undefined;
    const update = () => {
      window.clearTimeout(timer);
      if (preference.matches) setEnabled(false);
      else timer = window.setTimeout(() => setEnabled(true), 220);
    };
    update();
    preference.addEventListener("change", update);
    return () => { window.clearTimeout(timer); preference.removeEventListener("change", update); };
  }, []);

  return <figure className={"diorama" + (welcome ? " diorama-welcome" : "")}>
    <div className="diorama-surface">
      <img className="diorama-poster" src="/assets/recinto.svg" alt="Miniatura isométrica da praça, do palco, da entrada e do farol" decoding="async"/>
      <div className="scene-corner" aria-hidden="true"><Icon name="cube" size={17}/> VISTA DO RECINTO</div>
      {enabled && available && <Suspense fallback={null}><Diorama landmark={landmark} unavailable={() => setAvailable(false)}/></Suspense>}
      {landmark && <span className="landmark-label"><Icon name="pin" size={16}/>{landmark === "stage" ? "Palco Principal" : "Entrada Norte"}</span>}
    </div>
    <figcaption>
      <div className="diorama-note">{enabled && available ? "Arrasta para rodar a miniatura." : "Vista estática da miniatura."}</div>
      <div className="landmark-options" aria-label="Pontos da miniatura">
        <button type="button" aria-pressed={landmark === "stage"} onClick={() => setLandmark("stage")}><Icon name="music" size={18}/> Palco</button>
        <button type="button" aria-pressed={landmark === "entry"} onClick={() => setLandmark("entry")}><Icon name="enter" size={18}/> Entrada</button>
      </div>
    </figcaption>
  </figure>;
}
