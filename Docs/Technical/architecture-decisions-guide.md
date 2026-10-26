# Decisões Arquiteturais

> LABDSOF 2026/27 · Sprint A · Enunciado §6 (requisitos técnicos), §7.1.2 (entregáveis 7, 8 e 10), §8.4 e §8.5 (contentores e CI/CD)
> **Estado (25 de outubro de 2026):** registo de **todas** as decisões arquiteturais da equipa. **Todas as decisões aqui registadas foram tomadas pela equipa**, não por um membro em particular. Quando uma decisão fica final, passa para um ADR e para o [`architecture.md`](architecture.md).
> **Histórico:** reuniões de 11 e 15 de outubro de 2026 ([`2026-10-08-sprint-1-gap-review.md`](../Meetings/2026-10-08-sprint-1-gap-review.md)) e resposta do professor de 21 de outubro de 2026 ao email de 15/10 (§Respostas do professor) e reunião com o professor de 22 de outubro de 2026 ([`2026-10-22-professor-meeting.md`](../Meetings/2026-10-22-professor-meeting.md)) e reunião da equipa de 22 de outubro de 2026 sobre identidade e API gateway ([`2026-10-22-identity-and-gateway-decisions.md`](../Meetings/2026-10-22-identity-and-gateway-decisions.md)); procedimento de entrega local fechado a 23 de outubro de 2026 (#129).

**Legenda do estado:**
✅ Decidido · ⏳ À espera da resposta do professor · 🔍 Em investigação (responsável indicado) · 🔮 Melhoria futura (fora do MVP) · ❗ Por decidir, **sem responsável**

## Responsáveis pelas investigações em curso

| Membro | Área |
|---|---|
| **Tomás** | Aplicar o procedimento de entrega local no walking skeleton ([`local-delivery.md`](local-delivery.md)) · integração de IA (se é viável fazer deploy de Ollama) · observabilidade · avaliação de Responsible AI e dataset de avaliação da triagem · walking skeleton |
| **Guerra** | Security & Privacy assessment (E5, escrito a 25/10, por rever) · simulador da API do Porto Digital (J4) · Keycloak e API gateway no walking skeleton (identidade e gateway decididos a 22/10: ADR-006, ADR-013) |
| **Tomás + Guerra** | Modelo de domínio, incluindo o FarolAdmin (P4), as *memberships* por evento e as equipas com permissões |
| **Tiago** | Resiliência e degradação · início do backlog · biblioteca do mapa do recinto · lista de NFRs |
| **Bruno** | Diagramas (draw.io, com o MCP do draw.io) · depois, avaliação e investigação da pipeline de CI/CD · procedimento de entrega local (G4–G7, H5) · eliminação de conta · API outline e catálogo de eventos · estimativa de custos (salários + infraestrutura) |

## Investigação de deploy, observabilidade e IA (22 de outubro de 2026)

**[Issue #53](https://github.com/tomasoliveira21/LABDSOF/issues/53): investigação documentada, propostas para revisão da equipa.** O [relatório e mapa de cobertura](../Research/architecture-investigation/README.md) cobre G1–G7, I1–I3, K1–K2, F2, alojamento do RabbitMQ (C1) e número de servidores PostgreSQL (D2). Inclui comparação de plataformas/custos, observabilidade, avaliação Responsible AI, 100 casos sintéticos e baseline reproduzível.

Propostas de IA e observabilidade: [ADR-005](adr/0005-ai-triage-provider-and-evaluation.md) (IA), [ADR-011](adr/0011-deployment-and-infrastructure.md) (infraestrutura), [ADR-012](adr/0012-observability.md) (observabilidade). **As propostas técnicas mantêm os estados 🔍:** a investigação não é aprovação da equipa; a autorização posterior para entrega apenas local está registada abaixo. Faltam validação dos rótulos, medições de modelos/SC3/SC5, dimensionamento local e revisão de privacidade. Orçamento/créditos e quotas de cloud só voltam a ser necessários se essa alternativa for retomada. Não houve deploy nem contratação de serviços. O G7 fechou a 23/10 sem mexer no D5: só *dumps* de dados sintéticos antes da demo, sem garantia contra perda do disco.

## Entrega apenas local autorizada pelo professor (registo de 22 de outubro de 2026)

Segundo a informação transmitida pelo **Tomás**, o professor autorizou avançar com **execução/deployment apenas local, sem deployment na cloud, por causa dos custos**. É necessário explicar no relatório e na apresentação que a ausência de deployment na cloud se deve aos custos. O [registo da clarificação](../Meetings/2026-10-22-local-deployment-clarification.md) e o [ADR-011](adr/0011-deployment-and-infrastructure.md) documentam o âmbito e a justificação.

A avaliação de Azure/ECS/Vercel, alojamento e preços **mantém-se como evidência e alternativa futura**, não como plano de contratação. Os cinco serviços continuam independentes; execução local não exige cinco máquinas nem elimina testes, imagens, integração, observabilidade ou demonstração dos modos degradados. A topologia e o procedimento local ficaram definidos a 23/10 em [`local-delivery.md`](local-delivery.md) (#129); falta implementá-los no walking skeleton. Esta clarificação substitui a expectativa anterior de deploy automático numa produção cloud em H4/H5.

## 0 · Base

| Decisão | Estado |
|---|---|
| **Backend em Spring Boot** (Java) | ✅ |
| **Uma só app web React** para todos; cada página é feita para o telemóvel ou para o portátil (F1). A app mobile React Native saiu a 22/10 (reunião com o professor, #125) | ✅ |
| **Arquitetura distribuída**; o monólito sem fronteiras impostas está excluído | ✅ |
| **C4 model nos 4 níveis** | ✅ |
| **Pipeline de CI e CD completa** | ✅ |
| **Entrega/deployment local**, sem cloud devido aos custos, autorizado pelo professor segundo o Tomás (registo de 22/10). Avaliação cloud preservada como justificação e alternativa futura | ✅ âmbito local; detalhes no ADR-011 |

## A · Estilo arquitetural (ADR-001)

| # | Decisão | Estado |
|---|---|---|
| A1 | **Microserviços** como estado final. Cumprem o §6.2 sem ter de provar que os serviços seriam complexidade desnecessária, isolam falhas e dão contratos pequenos por serviço (base do Spec-Driven Development) | ✅ |
| A2 | **5 serviços.** **Auth & Users** (authN, authZ, contas, papel global `FarolAdmin`, *memberships* por evento com o papel `EventAdmin` ou `RegularUser` e as equipas de cada membro, amigos: F1, F2, papéis e membros das equipas do F4, F18) · **Events** (eventos, mapa e pontos de interesse, equipas com as suas permissões e categorias de ajuda, anúncios, check-in, entradas: F3, equipas do F4, F5, F10, F16, F17) · **Reports** (relatos comunitários e confirmação, pedidos de ajuda, backoffice, triagem IA com fallback: F6, F7, F8, F12, F13) · **Transports** (F9; fonte: Plataforma Urbana do Porto Digital (FIWARE, NGSI v2), aceite pelo professor a 21/10, com simulador sob o nosso controlo (J4); ADR-008) · **Badges** (F14, F15) | ✅ |
| A3 | Cada serviço é um **projeto Maven próprio** em `backend/<serviço>/`, no mesmo repositório; nenhum serviço depende do código de outro, só dos contratos (OpenAPI e eventos) | ✅ |
| A4 | **Microserviços desde o início**: os 5 serviços são deployáveis de forma independente desde o Sprint A, sem fase de monólito. Não há percurso imposto: qualquer evolução tem de ser **motivada por evidência** (medições, testes, falhas), não pelo calendário, e fica registada num ADR (resposta do professor). Cumpre já o requisito do Sprint B de dois componentes backend com deploy independente (§6.2). Rejeitado: monólito modular nos Sprints A e B, dividido no C | ✅ |

## B · C4 model e documentação

| # | Decisão | Estado |
|---|---|---|
| B1 | Diagramas em **draw.io**, feitos com o MCP do draw.io (substitui Mermaid C4 / C4-PlantUML) | ✅ |
| B2 | **Nível 1:** o Farol (caixa única), as pessoas P1–P5 e os sistemas externos que usamos: **Plataforma Urbana do Porto Digital** (FIWARE NGSI v2), fornecedor de IA (🔍 Tomás) e o IdP se for externo (🔍 Guerra) | ✅ |
| B3 | **Nível 2:** app web React (a única aplicação, F1), cada serviço, o RabbitMQ e a base de dados de cada serviço; os sistemas externos mantêm-se. Gateway: ver C6 | ✅ |
| B4 | **Nível 3 para todos os serviços.** A documentação de cada serviço (nível 3, nível 4, contratos) fica em `backend/<serviço>/docs/` | ✅ |
| B5 | **Nível 4** mostra a **arquitetura hexagonal** de cada serviço: `domain`, `application` (casos de uso e portas), `adapters/in` (REST gerado do OpenAPI, consumidores RabbitMQ), `adapters/out` (JPA, publicador do outbox, clientes HTTP externos) | ✅ |
| B6 | **Vistas do 4+1, sem cenários** (os workflows W1–W8 já os cobrem): lógica (C4 1–3 e modelo de domínio), processos (dinâmicos: pedido de ajuda e triagem assíncrona), desenvolvimento (repositório, módulos Maven, pacotes hexagonais), física (contentores no ambiente local autorizado; diagramas a alinhar pelo responsável) | ✅ |
| B7 | ADRs em `Docs/Technical/adr/NNNN-titulo.md`, formato **Michael Nygard** (Contexto, Decisão, Estado, Consequências) + secção **Alternativas consideradas** (§7.1.2) | ✅ |

## C · Comunicação (ADR-004)

| # | Decisão | Estado |
|---|---|---|
| C1 | **RabbitMQ** com Spring AMQP, exchanges *topic* e DLQ; corre localmente em contentor com volume persistente. O alojamento cloud estudado fica como alternativa futura (ADR-011) | ✅ tecnologia e âmbito local; 🔍 configuração |
| C2 | **Spring AMQP** | ✅ |
| C3 | **Tabela outbox** em cada serviço, publicada por um job de polling nosso. Consumidores idempotentes com tabela de mensagens processadas (`message_id`); após N tentativas, DLQ. **N e backoff:** **4 tentativas** no consumidor (`spring.rabbitmq.listener.simple.retry.max-retries=3`), com backoff exponencial **1 s → 2 s → 4 s** (máximo 10 s): cerca de 7 s até à DLQ. Porquê: as falhas que um retry resolve (ligação à base de dados perdida, broker a reiniciar) passam em segundos; o que dura mais é um bug ou uma mensagem inválida, que nenhum retry corrige, e mais tentativas só atrasariam a DLQ. Mensagens que não se convertem ou não passam a validação vão logo para a DLQ. Uma DLQ por fila (`<fila>.dlq`), com o motivo nos cabeçalhos (`RepublishMessageRecoverer`). **Mensagens na DLQ:** nunca se perdem nem se reprocessam sozinhas; há um alerta quando a DLQ não está vazia (painel e alerta propostos no [ADR-012](adr/0012-observability.md), I3); corrigida a causa, são devolvidas à mão à fila original (seguro, porque os consumidores são idempotentes; à mão porque reprocessar sozinho uma mensagem que falhou por um bug entra em ciclo); as que nunca serão válidas ficam registadas e são apagadas. Uma falha da IA **não** é uma falha da mensagem: o worker regista o fallback (D1) e confirma a mensagem. Uma mensagem na DLQ nunca esconde um pedido de ajuda ao operador. Detalhe em [`architecture.md`](architecture.md) §6 | ✅ |
| C4 | **Só eventos entre serviços.** Cada serviço guarda uma cópia local do que precisa de outro (ex.: o Reports guarda as equipas, as suas permissões e categorias de ajuda do Events; todos guardam as *memberships* e as equipas de cada membro do Auth & Users). Custo aceite: consistência eventual | ✅ |
| C5 | **Polling** chega para o MVP (lista do backoffice, estado do pedido, anúncios). Uma instância por serviço por agora | ✅ |
| C6 | **Spring Cloud Gateway (Server WebMvc)**, único ponto de entrada público da API. Encaminha por **caminho de recurso** (`/api/v1/events/{id}/help-requests`), cada caminho de um só serviço, segundo o API outline (C8). **CORS só no gateway**, para as origens da app web. Valida o JWT (os serviços validam outra vez) e não verifica papéis nem permissões. **Rate limiting por conta** (`sub`), nunca por IP: 120 pedidos/min; **5 pedidos de ajuda por 10 min**; upload do mapa ≤ 20 MB. O limite de reports por conta (US-48) é regra de negócio do Reports. Corre no `docker compose` local (G1); Azure Container Apps se a alternativa cloud for retomada (reunião da equipa, 22/10, [registo](../Meetings/2026-10-22-identity-and-gateway-decisions.md); [ADR-013](adr/0013-api-gateway.md)) | ✅ |
| C7 | **Contract-first:** OpenAPI escrito à mão em `backend/<serviço>/contracts/openapi.yaml`; o `openapi-generator` gera interfaces e DTOs Spring e os clientes das apps. Caminhos com `/api/v1`. Base do [Spec-Driven Development](../Process/spec-driven-development.md) | ✅ |
| C8 | **API outline e catálogo de eventos** ([`api-outline.md`](api-outline.md), entregável 7 do Sprint A). Caminhos por recurso em `/api/v1`, sem prefixo de serviço, com `{eventId}` em tudo o que é de um evento; mudanças de estado como `POST /{id}/<ação>`; cursor `updatedSince` nas listas vivas; erros em Problem Details (RFC 9457). Uma exchange *topic* por produtor (`farol.<serviço>`), routing key `<agregado>.<verbo-no-passado>`, payload com o estado completo e `version`. A triagem IA corre dentro do Reports, através de uma fila. As decisões do outline (D1–D12) estão tomadas e registadas no §10.1. Alinhado a 26/10 com o ADR-006 e o ADR-013: registo no Keycloak e conta criada no primeiro `GET /me` (sem `POST /accounts`), token só com a identidade, rotas do gateway, limites (429/413), `UserStatusChanged` (conta desativada), `UserDeleted` definido e `actorUserId` (o `sub` de quem age) no envelope. A pesquisa por e-mail segue o AC-9 do assessment e o `HelpRequestSubmitted` leva só o `requestId` (M-5). O1–O4 fechadas; O5–O7 (§10.2) esperam por outros responsáveis (preferências de badges: Tomás + Guerra; US-41: Tiago; escala de prioridade: Tomás) | ✅ (#123) |

## D · Dados (ADR-003)

| # | Decisão | Estado |
|---|---|---|
| D1 | **PostgreSQL.** Uma só tecnologia (§6.4); o mapa é uma imagem, sem PostGIS | ✅ |
| D2 | **Uma base de dados por serviço**, a que só esse serviço acede | ✅ (n.º de servidores: 🔍 Tomás) |
| D3 | **`event_id` + filtro na aplicação** em todas as tabelas com dados de um evento. Cada serviço tem um teste de integração que prova que um membro do evento X não lê nem altera dados do evento Y | ✅ |
| D4 | Cache para o pico de leituras | 🔮 |
| D5 | Retenção e backup | 🔮 |
| D6 | **Flyway**, no arranque da aplicação. Migrações compatíveis com a versão anterior (*expand and contract*) | ✅ |
| D7 | Eliminação de conta (F2, Must): se entra no MVP e como funciona, sem política de retenção | 🔍 Bruno |

## E · Identidade e segurança (ADR-006)

E1–E4 decididos na reunião da equipa de 22/10 ([registo](../Meetings/2026-10-22-identity-and-gateway-decisions.md)): [ADR-006](adr/0006-identity-and-access.md).

| # | Pergunta | Estado |
|---|---|---|
| E1 | **Keycloak**, self-hosted num contentor com a sua base de dados, desde o walking skeleton. No Sprint B, o realm importado com contas de teste é a autenticação *mocked*. Login, logout e registo no Keycloak; a conta no Auth & Users é criada no primeiro `GET /me` | ✅ |
| E2 | Onde se valida o token e onde se verificam o papel e as permissões por evento. **Modelo de papéis definido a 22/10** pelo Tiago, a confirmar com a equipa ([`features.md` §4](../Product/features.md#4-roles-teams-and-permissions-22-october-2026)): `FarolAdmin` é o único papel global e não dá acesso aos dados de nenhum evento; `EventAdmin` e `RegularUser` são papéis da *membership* de cada evento (o mesmo utilizador pode ser EventAdmin num evento e RegularUser noutro); os EventAdmins criam equipas e dão a cada uma permissões de uma lista fixa (`HelpRequests.Triage`, `HelpRequests.Respond`, `Reports.Review` por tipo de report, `Reports.Respond`, `Announcements.Publish`, `Map.Edit`, `Transport.Edit`, `Entrances.Update`). As *memberships* e as equipas de cada membro ficam no Auth & Users; as equipas e as suas permissões ficam no Events. **Decidido a 22/10:** o token leva **só a identidade** (`sub`, email, nome, `aud`). Papéis, equipas e permissões chegam aos serviços por eventos e cada serviço junta-os na sua cópia local (C4): o EventAdmin tem todas as permissões do evento; o RegularUser tem as das suas equipas. **Cada serviço valida o JWT** (e o gateway também) e verifica a permissão **no caso de uso**, com filtro por `event_id`; quem não é membro recebe 404, quem não tem a permissão 403. O `FarolAdmin` é papel global no Auth & Users e nunca é aceite para dados operacionais. Revogação com consistência eventual, alvo ≤ 5 s. Access token de 5 min, refresh tokens rodados; sessão de 12 h, ou 30 dias com *Remember me* | ✅ |
| E3 | Sem HTTP entre serviços (C4): rede privada; **um utilizador RabbitMQ por serviço**, limitado às suas exchanges e filas; *client credentials* para a Admin API do Keycloak (eliminação de conta) | ✅ |
| E4 | Variáveis de ambiente: `.env` local (fora do git), **secrets do GitHub Actions** no CI, **gitleaks** em cada PR (job do GitHub Actions); Azure Key Vault só se a alternativa cloud for retomada | ✅ |
| E5 | **Security & Privacy assessment** (entregável 9 do Sprint A), escrito a 25/10: [`security-and-privacy-assessment.md`](security-and-privacy-assessment.md). Threat model STRIDE (T1–T21), dados sensíveis, requisitos de controlo de acesso (AC-1–AC-11), minimização (M-1–M-14), retenção e eliminação, consentimento e termos do registo (US-03), ambiente da demo local. Três problemas a resolver antes das funcionalidades que dependem deles: o bootstrap do `FarolAdmin` confia num e-mail não verificado (T4), os telemóveis não conseguem fazer login na demo local por HTTP (§9) e a mensagem da triagem leva o texto do pedido de ajuda pelo RabbitMQ (T11). Tudo o que é novo está marcado **[PROPOSED]**; ações e responsáveis na §10 | ✅ escrito (Guerra); 🔍 revisão da equipa |

## F · Frontend

| # | Decisão | Estado |
|---|---|---|
| F1 | **Uma só aplicação web React.** Cada página é feita para uma utilização: no **telemóvel** (participantes, equipas de terreno) ou no **portátil** (gestão de eventos, sala de controlo). Substitui, desde 22/10, as duas aplicações (web React para a gestão de eventos e mobile React Native para o resto) (reunião com o professor, [ata](../Meetings/2026-10-22-professor-meeting.md); #125; [ADR-002](adr/0002-backend-and-frontend-technology.md)) | ✅ |
| F2 | App web servida **localmente**; alojamento cloud não é exigido para a entrega autorizada (ADR-011) | ✅ âmbito; 🔍 implementação |
| F3 | **Mapa do recinto ([ADR-007](adr/0007-venue-map-representation.md)), reescrito para a web a 23/10:** a imagem com uma camada **SVG** para os marcadores, dentro do zoom/pan do **`react-zoom-pan-pinch`**; o mesmo componente nas páginas de telemóvel e de portátil; posições em coordenadas normalizadas (`MapPosition`); filtro por tipo (US-22) com estado React. Antes de implementar o mapa, um *spike* confirma o pinch, o pan e o toque num iPhone (Safari) e num Android (Chrome) reais; se falhar, o zoom/pan escreve-se com Pointer Events. **PDF convertido em imagem no upload**, no serviço Events, com Apache PDFBox (só PDFs de uma página; imagem com no máximo 4096 px no lado maior; guarda-se o original e a imagem). Onde ficam os ficheiros depende do G1. A versão de 22/10 era para React Native | 🔍 Tiago (revê a versão web) |
| F4 | ~~Configuração do React Native (ex.: Expo ou não) e como a app mobile chega aos professores e à demo (§12)~~ Já não se aplica: sem app mobile desde 22/10 (F1, #125) | ✅ fechado |

## G · Deploy e infraestrutura

| # | Pergunta | Estado |
|---|---|---|
| G1 | Execução/deployment **apenas local**, sem cloud devido aos custos; autorização do professor reportada pelo Tomás. Comparação das plataformas preservada no relatório de investigação | ✅ âmbito local |
| G2 | Avaliação de custos preservada; explicar no relatório/apresentação que o custo motivou não fazer deploy na cloud. **Estimativa de custos de operação feita a 23/10** ([`operating-cost-estimation.md`](../Deliverables/operating-cost-estimation.md), #134, Bruno): 6 roles a tempo inteiro ≈ 58 510 € no desenvolvimento (infraestrutura local 341 €, só o Copilot Business, nocional: a equipa usa-o grátis pelo GitHub Education) e ≈ 3 410 €/mês de manutenção (1 FTE + cloud 730 h). A cloud custaria ≈ 301 € na demo dos Sprints B e C e 358 €/mês sempre ligada, contra 89 € de crédito único | ✅ |
| G3 | Região cloud não se aplica à entrega local; RGPD e fluxos de dados locais/fornecedores revistos no E5 ([assessment](security-and-privacy-assessment.md) §3, §8; checklist do fornecedor de IA na §8.5) | ✅ sem região cloud; 🔍 privacidade: revisão do E5 pela equipa |
| G4 | Desenvolvimento, testes e demo localmente, sem ambiente cloud. **Demo:** no portátil de um membro, com um segundo portátil de reserva (mesma tag, *dumps* restaurados, smoke test na véspera). Os telemóveis abrem a app web em `http://<IP do portátil na LAN>:<porta>`, num hotspot ou router da equipa. Só o gateway, o Keycloak e o `web` ficam na rede; as consolas de administração (RabbitMQ, WireMock, Grafana) só em `127.0.0.1`. **Em aberto: HTTPS para os telemóveis** — em HTTP simples o login PKCE falha fora de `localhost` e os tokens ficam legíveis na rede; o assessment propõe mkcert ou o modo de dispositivo no portátil (§9, achado 2). **Guerra**, com o Tomás. Configuração em `infra/.env` (a partir do `infra/.env.example` versionado, sem segredos no repositório); dados de demonstração sintéticos por migrações Flyway só com o perfil `demo`. [`local-delivery.md`](local-delivery.md) §2, §3, §8 | ✅ Bruno define, Tomás aplica; 🔍 HTTPS nos telemóveis: Guerra |
| G5 | **Um só `infra/compose.yaml`** com *profiles* opcionais (`ai`, `observability`); **uma instância PostgreSQL com seis bases de dados e seis utilizadores** (os cinco serviços e o Keycloak; não seis contentores); o gateway (ADR-013), o Keycloak (ADR-006) e o WireMock (J2, J4) no mesmo ficheiro; imagens de terceiros com a versão fixada; health checks; volumes nomeados para PostgreSQL e RabbitMQ. Bicep/cloud IaC fica como alternativa estudada. [`local-delivery.md`](local-delivery.md) §1 | ✅ Bruno define, Tomás aplica |
| G6 | **Só comandos `docker compose`** (sem script nem Makefile): arrancar, atualizar para uma tag, substituir um só serviço (`up -d --no-deps`), repor os dados. **Smoke test** é uma checklist de comandos à mão: todos os contentores *healthy*, a app web abre (também num telemóvel), o gateway responde 401 sem token, o login funciona, um *round trip* pelo RabbitMQ. A demo passa sempre pelo WireMock (J4); D1, D2, broker e serviço em baixo mostram-se com `docker compose stop`. [`local-delivery.md`](local-delivery.md) §4, §5, §7, §8 | ✅ Bruno define, Tomás aplica |
| G7 | **Rollback = construir a tag anterior** (H7); as migrações Flyway não se desfazem (*expand and contract*). **`pg_dump` das seis bases (com a do Keycloak) antes de cada demo e de cada atualização**, para voltar a um estado conhecido e passá-lo ao portátil de reserva. Volumes não são backup; mensagens em fila e perda do disco não estão cobertas (RPO não garantido); D5 mantém-se 🔮. RTO medido no walking skeleton. [`local-delivery.md`](local-delivery.md) §5, §6 | ✅ Bruno define; 🔍 RTO medido pelo Tomás |

## H · Pipeline de CI/CD

O Bruno vai avaliar e investigar a pipeline; as decisões abaixo são o ponto de partida dessa investigação.

| # | Decisão | Estado |
|---|---|---|
| H1 | **GitHub Actions** | ✅ |
| H2 | **Monorepo atual:** `backend/<serviço>/`, `frontend/`, `infra/`, `Docs/` | ✅ |
| H3 | **Maven**, **Java 21 (LTS)**, **Spring Boot 4.x**; imagens com **Dockerfile multi-stage** | ✅ |
| H4 | **Corre tudo, sempre:** verificações em cada PR, merge e tag para todos os serviços (sem path filters); imagens versionadas e validação/smoke test local. Cloud não é destino obrigatório | ✅ âmbito atualizado; 🔍 automação Bruno |
| H5 | A expectativa anterior de **deploy automático na produção cloud** é substituída pela entrega local autorizada por custos. **Entrega = construir localmente a partir da tag** e correr o smoke test à mão ([`local-delivery.md`](local-delivery.md)); o CI não acede à máquina de ninguém. Correr o smoke test no CI fica para o ADR-010 | ✅ |
| H6 | **Não se aplica na entrega local:** sem registry (H7) não há publicação de imagens nem credencial para isso, e o CI não acede à máquina de ninguém (H5). A autenticação de deploy cloud fica com essa alternativa (ADR-011) | ✅ |
| H7 | **SemVer, uma versão para todo o monorepo.** Tag git anotada `vMAJOR.MINOR.PATCH` em `main`; as **sete imagens nossas** (os cinco serviços, o API gateway do ADR-013 e a app web) levam a mesma versão sem o `v` (`farol-<serviço>:0.2.0`); builds fora de uma tag ficam `dev`. As imagens de terceiros (Keycloak, PostgreSQL, RabbitMQ, WireMock e a stack de observabilidade) não seguem esta versão: ficam com a versão exata fixada no Compose (ADR-011, ponto 4) e só mudam num PR. Fica em **0.x** durante os sprints, uma release por *release* do [`features.md` §8.2](../Product/features.md#82-initial-release-plan): fim do Sprint A `v0.1.0` (R0, walking skeleton), Sprint B `v0.2.0` (R1, vertical slice), Sprint C `v1.0.0` (R2, MVP, entrega final); **PATCH** só para corrigir uma release já demonstrada, com a tag criada em `main` (sem branches de release; o PATCH leva o que já estiver em `main`). A tag é a única fonte da versão (sem commits de *bump*). **Só tags git:** sem registry nem GitHub Releases; o CI constrói as imagens em cada PR, merge e tag sem as publicar (H4), e localmente constrói-se a partir da tag (rollback = construir a tag anterior, G7). As notas de cada release ficam no [`CHANGELOG.md`](../../CHANGELOG.md). Entra no ADR-010 | ✅ |
| H8 | Repositório **privado** | ✅ |
| H9 | ~~Build e distribuição da app mobile na pipeline~~ Já não se aplica: sem app mobile desde 22/10 (F1, #125); a app web entra no build como os outros contentores | ✅ fechado |

## I · Observabilidade (§6.7)

| # | Pergunta | Estado |
|---|---|---|
| I1 | **Local:** Micrometer/Actuator + Prometheus/Grafana; OpenTelemetry Collector → **Tempo**; Alertmanager na UI local, logs estruturados Docker. [Decisão concreta](plans/local-observability.md) | ✅ documentado; revisão/execução pendentes |
| I2 | Métricas, populações e janelas definidas: triagem ≥95% em ≤30 s por coorte de 1 h, sem excluir falhas; SC5 separado a 100%. Scrape/avaliação 15 s; no-data explícito | ✅ documentado; medições pendentes |
| I3 | Um dashboard Farol local com seis grupos de painéis; regras e resolução especificadas, outbox >60 s por 2 min; Alertmanager sem destinatários externos | ✅ documentado; configuração/ensaios pendentes |

**Alinhamento documental (26/10, #145):** [ADR-005](adr/0005-ai-triage-provider-and-evaluation.md#coverage-definition-aligned-26-october-2026-145), ADR-012, a observabilidade e o avaliador passam a usar a proposta **≥95% de sugestões válidas guardadas em ≤30 s**, com janela operacional de 1 h. As populações offline/operacional e o tratamento de falhas/abstenções estão explícitos. A discrepância de 90%/95% fica corrigida nas fontes para o NFR-03 da PR #139; As escolhas I1–I3 ficam documentadas no #148; K1–K2 são fechadas documentalmente no #149. Revisão, implementação e resultados medidos são estados separados. [Estado do que existe e do que falta](../Research/architecture-investigation/README.md#ai-and-observability-status-26-october-2026-145).

## J · Resiliência e degradação (§6.8)

| # | Decisão | Estado |
|---|---|---|
| J1 | **Resilience4j 2.4** (`resilience4j-spring-boot4`, com suporte para Spring Boot 4 desde março de 2026), usado só nos adaptadores de saída (`adapters/out`). Porquê: é a única opção madura com circuit breaker para Spring Boot 4, configura-se no `application.yml` e publica métricas Micrometer e o estado no health check. Alternativas postas de parte: o retry do Spring Framework 7 (não tem circuit breaker; é o que o Spring AMQP usa no C3), Spring Cloud Circuit Breaker (mais uma camada por cima do Resilience4j) e Failsafe (sem configuração automática no Spring Boot). Os timeouts ficam no cliente HTTP (`RestClient`); o retry envolve o circuit breaker, por isso cada tentativa conta para o circuito. Valores e porquê nas tabelas abaixo | ✅ (valores da IA: a confirmar com K1; IdP: com E1) |
| J2 | **D1 e D2 demonstram-se com o WireMock à frente do sistema externo** (proposto como simulador do J4), na máquina local da entrega apenas local (G4, ADR-011), como mais um contentor do Compose. Em funcionamento normal, o WireMock reencaminha os pedidos para o sistema real (fornecedor de IA ou Porto Digital); na demo, um admin ativa pela API de administração do WireMock uma resposta com falha de ligação, atraso maior que o timeout ou dados inválidos, e depois remove-a. Numa execução local sem falhas, o URL aponta direto para o sistema real; o WireMock nunca entra na imagem de um serviço. Porquê: os serviços não levam código de falhas (o URL de cada sistema externo já é configuração, ADR-004); funciona com HTTPS (o WireMock é o cliente HTTPS do sistema real, enquanto um proxy de rede como o Toxiproxy falha a verificação do certificado); e cumpre o pedido do professor. **Testes automáticos** em todas as PRs, com o WireMock no lugar do sistema externo: US-36 (D1, SC5) e US-42 e US-43 (D2). **O que mostra que funcionou:** etiqueta *"Not triaged"* e banner (D1); *"Data from HH:MM"* e aviso, com os shuttles visíveis (D2); estado do circuit breaker, contador de fallbacks e idade dos dados no dashboard local proposto no [ADR-012](adr/0012-observability.md) (I3; enquanto não existir, os endpoints `health` e `metrics` do Actuator); logs com o motivo e o *correlation id*. **Frescura (D2), o *y* da reunião com o professor de 22/10: 5 min.** As posições chegam com cerca de 1 min (medido a 16 e 22/10) e um autocarro na cidade anda cerca de 2 km em 5 min, por isso uma posição mais antiga engana. Substitui as 24 h, que eram para horários GTFS, abandonados com a resposta do professor (ADR-008). Guião passo a passo em [`architecture.md`](architecture.md) §9 | ✅ (demo local, G4; Compose: 🔍 Tomás, G5–G6; WireMock no J4: confirma o 🔍 Guerra) |
| J4 | **Simulador da API do Porto Digital, ou respostas reais gravadas**, sob controlo da equipa (pedido do professor): testes automáticos repetíveis, provocar falhas, atrasos e dados inválidos na demo do D2, e não depender da API real no dia da apresentação. A integração real continua a ser o Porto Digital. **Forma e ferramenta, proposta do Tiago com o J2 (#43): WireMock**, em contentor; o Guerra, responsável do J4 (reunião de 22/10), confirma ou muda: em modo *proxy* para a API real; respostas reais gravadas (*record and playback*) para os testes e para o dia da apresentação; respostas de falha para a demo. O mesmo WireMock serve de simulador do fornecedor de IA. Porquê: uma só ferramenta para testes, demo e apresentação, já suportada pelo Testcontainers, sem termos de escrever e manter um simulador nosso. Alternativas: simulador próprio em Spring Boot (mais código para manter perto do formato NGSI v2) e Toxiproxy (só falhas de rede, não grava respostas nem gera dados inválidos, e falha com HTTPS) | ✅ (forma e ferramenta: proposta WireMock; 🔍 Guerra) |
| J3 | **Pico esperado: ~1 300 pedidos de leitura/s durante 10 min** no fim do último concerto (12 000 telemóveis com a app web aberta a fazer polling de 10 em 10 s), mais **1 pedido de ajuda/s**; cálculo, fontes e pressupostos em [`architecture.md`](architecture.md) §10.1. **Com uma instância por serviço (C5):** (1) menos trabalho por pedido: pedidos condicionais (ETag a partir da hora da última atualização → 304), imagem do mapa com URL versionado e cache longa, polling com *jitter* e só com a página visível (Page Visibility API); (2) isolamento: os pedidos de ajuda ficam no Reports, longe do pico de leituras (Events e Transports); (3) escala vertical: mais CPU e memória para o contentor mais carregado (limites no Compose), dentro do que a máquina local dá. Porquê esta ordem: os passos 1 e 2 não custam infraestrutura e protegem o que importa (o pedido de ajuda); escalar é o último recurso, e o C5 ainda o limita. **O primeiro serviço a ter mais instâncias**, quando o C5 mudar, é o **Events** (recebe a maior parte do polling: anúncios, mapa, pontos de interesse), depois o Transports; localmente, mais um contentor da mesma imagem atrás do gateway (`docker compose up --scale`); a escala gerida na cloud fica só como alternativa avaliada (ADR-011), sem ser demonstrada. Antes disso, o job do outbox e as importações têm de correr numa só instância (`FOR UPDATE SKIP LOCKED` ou ShedLock). **Teste de carga com k6** (em vez de Gatling ou JMeter): cenários com taxa de chegada (como os pedidos chegam na realidade), critérios de aprovação escritos como *thresholds* que fazem falhar a execução, um só binário (ou a imagem Docker oficial) que corre ao lado do Compose. 5 cenários (smoke, pico do fim do concerto, pedidos de ajuda durante o pico, stress até ao limite, D1 com carga); corre à mão no Sprint C, contra o sistema completo levantado com `docker compose` na máquina local (G1, G4, ADR-011), com a IA substituída pelo WireMock; um runner público do GitHub Actions não chega à máquina de um membro da equipa. O k6 corre noutro computador da mesma rede, se houver; senão na mesma máquina, com limites de CPU e memória nos contentores do Farol. Os resultados valem para essa máquina (registada com eles), não para a cloud; se uma máquina não chegar ao pico, o L4 regista o limite atingido e é esse limite a evidência do plano de escala. Scripts, Compose usado e resumo do k6 ficam no repositório | ✅ (máquina local; Compose: 🔍 Tomás, G5–G6) |

**Valores do J1 por chamada externa:**

| Chamada externa | Timeout | Retry | Circuit breaker | Quando falha |
|---|---|---|---|---|
| **Fornecedor de IA** (Reports, worker de triagem) | ligação 2 s, resposta 10 s | 2 tentativas, espera de 2 s; só em timeout, erro de ligação, 5xx e 429; nunca em 4xx nem em resposta inválida | últimas 10 chamadas, mínimo 4; abre com ≥ 50 % de falhas ou de chamadas lentas (> 8 s); 30 s aberto; 1 chamada de teste | Fallback D1. Pior caso ≈ 22 s, dentro dos 30 s do SC3. **A confirmar com K1:** um modelo local (Ollama em CPU) pode precisar de resposta 20 s e de 1 só tentativa |
| **Porto Digital** (Transports, importação agendada a cada 30 s: o *x* da reunião com o professor de 22/10) | ligação 3 s, resposta 5 s | 3 tentativas, esperas de 1 s e 2 s; não repete com o circuito aberto | últimas 6 chamadas, mínimo 3; abre com ≥ 50 % de falhas; 60 s aberto; 1 chamada de teste | D2. Só os autocarros a menos de 2 km do recinto (consulta geográfica NGSI: 10 KB em vez de 190–300 KB, medido a 22/10) |
| **IdP**, se for externo (chaves JWKS, em todos os serviços) | ligação 2 s, resposta 3 s | não | não | Chaves em cache (5 min, renovadas quando chega um `kid` desconhecido); se a renovação falha, continua com as chaves em cache. **A confirmar com E1** (com autenticação simulada não há chamada externa) |

**Porquê destes valores** (detalhe em [`architecture.md`](architecture.md) §9.1):

- **IA, 10 s e 2 tentativas:** é o máximo que cabe nos 30 s do SC3 (10 + 2 + 10 = 22 s, mais cerca de 8 s para o outbox, a fila e a gravação); uma 3.ª tentativa já não cabe (34 s). Ligação 2 s: um fornecedor acessível aceita a ligação em menos de 1 s.
- **IA, só repete falhas passageiras:** um 4xx (chave errada, pedido mal formado) falha sempre da mesma maneira; uma resposta inválida também não compensa repetir, porque o fallback é seguro e mantém o pior caso curto.
- **IA, circuito pequeno (10 chamadas, mínimo 4, 30 s):** à noite há poucos pedidos de ajuda por minuto; com uma janela grande, muitos pedidos esperariam 22 s até o circuito abrir. Assim abre ao fim de 2 pedidos falhados, e 30 s aberto deixa a IA voltar depressa quando o fornecedor recupera. 50 % tolera uma resposta inválida isolada; > 8 s apanha o fornecedor lento antes de começar a falhar.
- **Porto Digital, 30 s:** as posições chegam com cerca de 1 min de atraso (medido); importar mais vezes não traz dados mais novos, e importar menos vezes envelhece-os. Resposta 5 s: a API respondeu em 0,4–0,8 s (margem de 6×).
- **Porto Digital, 3 tentativas e circuito de 60 s:** o pior caso (3 × 5 + 3 = 18 s) é menor que o intervalo, por isso duas importações nunca se sobrepõem. Durante uma falha, o circuito reduz as chamadas de 6 para 1 por minuto: não sobrecarregamos um serviço público que não controlamos, e a recuperação vê-se em cerca de 90 s.
- **IdP:** os tokens validam-se localmente com as chaves em cache; a chamada só acontece quando as chaves mudam, por isso não precisa de retry nem de circuito, mas tem de ser curta porque acontece durante o pedido de um utilizador.

## K · IA (ADR-005)

| # | Pergunta | Estado |
|---|---|---|
| K1 | Fornecedor e modelo; viabilidade do deploy de Ollama | 🔍 Tomás |
| K2 | Avaliação de Responsible AI e dataset de avaliação da triagem | 🔍 Tomás |

## Respostas do professor

Email enviado a 15/10/2026 com duas perguntas; o professor respondeu a ambas:

1. **Integração externa:** é válido usar a API pública da Plataforma Urbana do Porto Digital (FIWARE, NGSI v2), com dados abertos dos autocarros em circulação no Porto? Define a fonte do serviço Transports (A2), o sistema externo no nível 1 (B2) e o ADR-008. **Resposta: sim**, mas não basta mostrar os dados: a aplicação tem de lidar com indisponibilidade, atrasos e dados inválidos, e a equipa deve ter um simulador da API ou respostas reais gravadas (J4). Fecha A2 e B2; [ADR-008](adr/0008-transport-data-source.md).
2. **Evolução da arquitetura:** a arquitetura deve mudar de sprint para sprint (monólito modular no B → microserviços no C) ou ficar definida desde o início, com a evolução feita de refinamentos? E que evidência apresentar? Define o A4 e, por arrasto, a fase de monólito do A3 e do D2. **Resposta:** não há percurso imposto; a arquitetura fica definida desde o início e qualquer evolução é motivada por evidência, não pelo calendário. No Sprint B já são exigidos dois componentes backend com deploy independente, ou um monólito modular justificado (§6.2). Fecha A4; a fase de monólito sai do A3 e do D2; [ADR-001](adr/0001-architectural-style.md).

## Próximos passos

**Feito:**
- Resposta do professor (21/10): A4 e a fonte de transportes (A2, B2) fechados; ADR-001 e ADR-008.
- Diagramas C4 e 4+1 em draw.io e ADR-009 (Bruno). A 22/10 saíram o nível 4 da vista física e os seus mapeamentos lógico–físico.
- ADR-002, ADR-003 e ADR-004 (Bruno) e ADR-007 (Tiago).
- API outline e catálogo de eventos (C8, Bruno, #123): [`api-outline.md`](api-outline.md).
- A app mobile React Native saiu: **uma só app web React**, com páginas para o telemóvel e para o portátil (F1; reunião com o professor, 22/10; #125). F4 e H9 fecham; ADR-002 atualizado e ADR-007 reescrito para a web (falta a revisão do Tiago).
- Deploy **apenas local**, sem cloud por causa dos custos, autorizado pelo professor (G1, G2; Tomás, 22/10, [registo](../Meetings/2026-10-22-local-deployment-clarification.md)). Investigação de deploy, observabilidade e IA no #53 (ADR-005, ADR-011 e ADR-012, propostos).
- Resiliência e degradação (Tiago, #43): C3, J1, J2 e J3 decididos, alinhados com a entrega apenas local e com a app web. **Transportes: x = 30 s** (intervalo do poll) e **y = 5 min** (limite de frescura, aviso ao utilizador) (professor, 22/10; J1 e D2). WireMock proposto para o J4.
- Identidade e API gateway decididos pela equipa (C6, E1–E4; reunião da equipa, 22/10, [registo](../Meetings/2026-10-22-identity-and-gateway-decisions.md)): Keycloak, token só com a identidade, permissões verificadas em cada serviço, Spring Cloud Gateway com CORS e rate limiting por conta. [ADR-006](adr/0006-identity-and-access.md) e [ADR-013](adr/0013-api-gateway.md) (o 011 e o 012 já estavam ocupados).
- Security & Privacy assessment escrito (E5; Guerra, 25/10): [`security-and-privacy-assessment.md`](security-and-privacy-assessment.md). Falta a revisão da equipa e as ações da sua §10.
- Procedimento de entrega local e demo (G4–G7, H5; Bruno, 23/10, #129): um `infra/compose.yaml` com o gateway e o Keycloak, uma instância PostgreSQL com seis bases, só comandos `docker compose`, smoke test em checklist, telemóveis pelo IP na LAN (HTTPS em aberto, Guerra), `pg_dump` antes da demo. H6 fecha: sem registry não há publicação. Em [`local-delivery.md`](local-delivery.md) e no ADR-011.
- Versionamento e etiquetas (H7, Bruno, 23/10): SemVer único para o monorepo e para as sete imagens nossas (com o gateway), uma release por sprint (R0, R1, R2), imagens de terceiros fixadas, só tags git. Entra no ADR-010.
- Estimativa de custos de operação, salários + infraestrutura (G2; Bruno, 23/10, #134): ≈ 58 510 € no desenvolvimento e ≈ 3 410 €/mês de manutenção; explica porque a entrega é local. Em [`operating-cost-estimation.md`](../Deliverables/operating-cost-estimation.md).

**Em aberto, com responsável:**
1. **Cada responsável fecha a sua área** e regista aqui a decisão da equipa: Tomás (G3, I, K, F2, alojamento do RabbitMQ), Guerra (J4: confirmar ou mudar o WireMock; E5: levar o assessment à revisão da equipa), Tomás + Guerra (modelo de domínio, incluindo o FarolAdmin), Tiago (backlog, revisão do ADR-007 para a web; valores do J1 para a IA com o K1 e para o Keycloak com o E1), Bruno (D7).
2. ✅ feito no #123 (26/10). **Alinhar o API outline, o catálogo de eventos e o D7 com o ADR-006:** registo no Keycloak e sem `POST /accounts` (O2); token só com a identidade (O3); O1 fechado pela tabela de rotas do gateway; evento de conta desativada, `UserDeleted` definido e `sub` de quem age no envelope ([ADR-006](adr/0006-identity-and-access.md#changes-for-the-api-outline-and-d7)). O que cada serviço apaga ou anonimiza no `UserDeleted` fica para o D7. **Bruno.**
3. **Backlog:** ✅ feito no #141 (24/10): critério de aceitação para a resposta 429 na página do pedido de ajuda (US-23; limite de 5 por 10 min, ADR-013) e limite de 20 MB no upload do mapa confirmado (US-19). **Tiago.**
4. **Walking skeleton** (entregável 10): implementação mínima de ponta a ponta, com build automático, um teste inicial e deploy em contentor. Pedido de novo pelo professor a 22/10. Segue o [`local-delivery.md`](local-delivery.md) e acrescenta-lhe o que só se mede com código: RAM mínima, RTO e o comando do *round trip* no smoke test. **Tomás.**
5. **Nível 3 da vista física:** mostrar as cinco bases de dados, uma por serviço (professor, 22/10). **Bruno.** Na mesma passagem, tirar a app mobile dos diagramas (níveis 1–3 e `*-level-4-mobile`; #125).
6. **Lista de NFRs** para a apresentação. **Tiago.**
7. **Manter o [`architecture.md`](architecture.md) alinhado** com este documento sempre que uma área fechar. **Quem fecha a área.**

**Sem responsável, ficam para o fim** ❗ (reunião com o professor, 22/10, [ata](../Meetings/2026-10-22-professor-meeting.md)):
- Apresentação (deck) com as secções pedidas pelo professor.
- PDF único com os documentos dos entregáveis do Sprint A.
- Lista de pressupostos e dependências, juntada a partir dos outros documentos.
