export type Point = { x: number; y: number };
export type Role = "participant" | "control" | "field";
export type Status = "received" | "dispatched" | "on-way" | "resolved";
export type Priority = "Normal" | "Alta" | "Urgente";
export const teams = ["Primeiros socorros", "Segurança", "Apoio ao participante"] as const;
export type Team = (typeof teams)[number];

export const roles: { id: Role; name: string; icon: string }[] = [
  { id: "participant", name: "Participante", icon: "user" },
  { id: "control", name: "Sala de controlo", icon: "monitor" },
  { id: "field", name: "Equipa no terreno", icon: "shield" },
];
export const categories = ["Saúde", "Segurança", "Pessoa perdida", "Outro"] as const;
export type Category = (typeof categories)[number];

export const places = [
  { id: "north", name: "Entrada Norte", type: "Entradas", icon: "enter", x: 439, y: 113, detail: "Acesso pedonal a partir da Avenida do Parque." },
  { id: "stage", name: "Palco Principal", type: "Palcos", icon: "music", x: 706, y: 246, detail: "O palco fica no lado nascente da praça central." },
  { id: "aid", name: "Primeiros Socorros", type: "Apoio", icon: "cross", x: 259, y: 304, detail: "Posto junto ao corredor principal, a poente da praça." },
  { id: "wc", name: "WC", type: "WC", icon: "wc", x: 639, y: 413, detail: "Instalações sanitárias junto ao corredor sul." },
  { id: "south", name: "Entrada Sul", type: "Entradas", icon: "enter", x: 360, y: 463, detail: "Acesso pela zona de transportes e ponto de encontro." },
  { id: "support", name: "Ponto de Informação", type: "Apoio", icon: "info", x: 353, y: 205, detail: "Informações do evento e apoio à acessibilidade." },
] as const;
export type Place = (typeof places)[number];
export type PlaceFilter = "Tudo" | Place["type"];
export const filters: PlaceFilter[] = ["Tudo", "Entradas", "Palcos", "WC", "Apoio"];

export type Change = { label: string; at: number; by: string };
export type HelpRequest = {
  id: string; category: Category; point: Point; description: string;
  priority: Priority; team: Team | null; instructions: string;
  status: Status; at: number; history: Change[]; own: boolean; manual: boolean;
};
export const statusLabels: Record<Status, string> = {
  received: "Recebido", dispatched: "Encaminhado", "on-way": "A equipa está a caminho", resolved: "Resolvido",
};
export const statusIcons: Record<Status, string> = {
  received: "clock", dispatched: "arrow-right", "on-way": "route", resolved: "check",
};
export const seed: HelpRequest[] = [
  { id: "FAR-014", category: "Segurança", point: { x: 471, y: 156 }, description: "É necessário apoio junto ao corredor de acesso.", priority: "Alta", team: "Segurança", instructions: "Confirmar a passagem ao lado da Entrada Norte.", status: "dispatched", at: 1794777000000, own: false, manual: false, history: [
    { label: "Pedido recebido", at: 1794777000000, by: "Participante" },
    { label: "Encaminhado para Segurança", at: 1794777060000, by: "Sala de controlo" },
  ] },
  { id: "FAR-015", category: "Outro", point: { x: 605, y: 362 }, description: "É preciso repor a sinalização do corredor sul.", priority: "Normal", team: null, instructions: "", status: "received", at: 1794777300000, own: false, manual: false, history: [
    { label: "Pedido recebido", at: 1794777300000, by: "Participante" },
  ] },
];

export const notices = [
  { title: "Abertura da Entrada Sul", text: "A Entrada Sul está disponível. Se já estás na zona de transportes, segue a sinalização até ao portão.", time: "22:14" },
  { title: "Ponto de encontro", text: "Combina um ponto de encontro com o teu grupo. O Ponto de Informação fica junto à Praça Central.", time: "21:50" },
];
export const transports = [
  { title: "Shuttle do recinto", text: "Paragem junto à Entrada Sul. Serviço de demonstração até às 03:00.", time: "22:10" },
  { title: "Autocarros", text: "Acesso à paragem pela Entrada Norte. Segue o percurso pedonal sinalizado.", time: "22:05" },
];

export function timeLabel(at: number) {
  return new Intl.DateTimeFormat("pt-PT", { hour: "2-digit", minute: "2-digit", timeZone: "Europe/Lisbon" }).format(at);
}
export function locationLabel(point: Point) {
  const nearest = [...places].sort((a, b) =>
    Math.hypot(a.x - point.x, a.y - point.y) - Math.hypot(b.x - point.x, b.y - point.y),
  )[0];
  return Math.hypot(point.x - nearest.x, point.y - nearest.y) < 110 ? "Junto a " + nearest.name : "Praça Central";
}
export function suggest(category: Category): { priority: Priority; team: Team } {
  if (category === "Saúde") return { priority: "Alta", team: "Primeiros socorros" };
  if (category === "Segurança") return { priority: "Alta", team: "Segurança" };
  return { priority: "Normal", team: "Apoio ao participante" };
}
