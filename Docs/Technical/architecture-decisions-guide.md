# Decisões Arquiteturais

> LABDSOF 2026/27 · Sprint A · Enunciado §6 (requisitos técnicos), §7.1.2 (entregáveis 7, 8 e 10), §8.4 e §8.5 (contentores e CI/CD)
> **Estado (23 de outubro de 2026):** registo de **todas** as decisões arquiteturais da equipa. **Todas as decisões aqui registadas foram tomadas pela equipa**, não por um membro em particular. Quando uma decisão fica final, passa para um ADR e para o [`architecture.md`](architecture.md).
> **Histórico:** reuniões de 11 e 15 de outubro de 2026 ([`2026-10-08-sprint-1-gap-review.md`](../Meetings/2026-10-08-sprint-1-gap-review.md)) e resposta do professor de 21 de outubro de 2026 ao email de 15/10 (§Respostas do professor) e reunião com o professor de 22 de outubro de 2026 ([`2026-10-22-professor-meeting.md`](../Meetings/2026-10-22-professor-meeting.md)).

**Legenda do estado:**
✅ Decidido · ⏳ À espera da resposta do professor · 🔍 Em investigação (responsável indicado) · 🔮 Melhoria futura (fora do MVP) · ❗ Por decidir, **sem responsável**

## Responsáveis pelas investigações em curso

| Membro | Área |
|---|---|
| **Tomás** | Deploy (Azure, ECS ou Vercel) · integração de IA (se é viável fazer deploy de Ollama) · observabilidade · avaliação de Responsible AI e dataset de avaliação da triagem · walking skeleton |
| **Guerra** | Identidade e segurança · API gateway · Security & Privacy assessment · simulador da API do Porto Digital (J4) |
| **Tomás + Guerra** | Modelo de domínio, incluindo o FarolAdmin (P4), as *memberships* por evento e as equipas com permissões |
| **Tiago** | Resiliência e degradação · início do backlog · biblioteca do mapa do recinto · lista de NFRs |
| **Bruno** | Diagramas (draw.io, com o MCP do draw.io) · depois, avaliação e investigação da pipeline de CI/CD · setup e distribuição da app mobile · eliminação de conta · API outline e catálogo de eventos · estimativa de custos (salários + infraestrutura) |

## Investigação de deploy, observabilidade e IA (22 de outubro de 2026)

**[Issue #53](https://github.com/tomasoliveira21/LABDSOF/issues/53): investigação documentada, propostas para revisão da equipa.** O [relatório e mapa de cobertura](../Research/architecture-investigation/README.md) cobre G1–G7, I1–I3, K1–K2, F2, alojamento do RabbitMQ (C1) e número de servidores PostgreSQL (D2). Inclui comparação de plataformas/custos, observabilidade, avaliação Responsible AI, 100 casos sintéticos e baseline reproduzível.

Propostas de IA e observabilidade: [ADR-005](adr/0005-ai-triage-provider-and-evaluation.md) (IA), [ADR-011](adr/0011-deployment-and-infrastructure.md) (infraestrutura), [ADR-012](adr/0012-observability.md) (observabilidade). **As propostas técnicas mantêm os estados 🔍:** a investigação não é aprovação da equipa; a autorização posterior para entrega apenas local está registada abaixo. Faltam validação dos rótulos, medições de modelos/SC3/SC5, dimensionamento local e revisão de privacidade. Orçamento/créditos e quotas de cloud só voltam a ser necessários se essa alternativa for retomada. Não houve deploy nem contratação de serviços. A proposta de recuperação G7 exige rever o âmbito de backup D5 antes de ser aceite.

## Entrega apenas local autorizada pelo professor (registo de 22 de outubro de 2026)

Segundo a informação transmitida pelo **Tomás**, o professor autorizou avançar com **execução/deployment apenas local, sem deployment na cloud, por causa dos custos**. É necessário explicar no relatório e na apresentação que a ausência de deployment na cloud se deve aos custos. O [registo da clarificação](../Meetings/2026-10-22-local-deployment-clarification.md) e o [ADR-011](adr/0011-deployment-and-infrastructure.md) documentam o âmbito e a justificação.

A avaliação de Azure/ECS/Vercel, alojamento e preços **mantém-se como evidência e alternativa futura**, não como plano de contratação. Os cinco serviços continuam independentes; execução local não exige cinco máquinas nem elimina testes, imagens, integração, observabilidade ou demonstração dos modos degradados. A topologia e a automação local detalhadas continuam por implementar/validar. Esta clarificação substitui a expectativa anterior de deploy automático numa produção cloud em H4/H5.

## 0 · Base

| Decisão | Estado |
|---|---|
| **Backend em Spring Boot** (Java) | ✅ |
| **Frontend web simples em React**, só para a gestão de eventos | ✅ |
| **App mobile em React Native** para todas as outras funcionalidades | ✅ |
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
| B3 | **Nível 2:** app web React (gestão de eventos), app mobile React Native, cada serviço, o RabbitMQ e a base de dados de cada serviço; os sistemas externos mantêm-se. Gateway: ver C6 | ✅ |
| B4 | **Nível 3 para todos os serviços.** A documentação de cada serviço (nível 3, nível 4, contratos) fica em `backend/<serviço>/docs/` | ✅ |
| B5 | **Nível 4** mostra a **arquitetura hexagonal** de cada serviço: `domain`, `application` (casos de uso e portas), `adapters/in` (REST gerado do OpenAPI, consumidores RabbitMQ), `adapters/out` (JPA, publicador do outbox, clientes HTTP externos) | ✅ |
| B6 | **Vistas do 4+1, sem cenários** (os workflows W1–W8 já os cobrem): lógica (C4 1–3 e modelo de domínio), processos (dinâmicos: pedido de ajuda e triagem assíncrona), desenvolvimento (repositório, módulos Maven, pacotes hexagonais), física (contentores no ambiente local autorizado; diagramas a alinhar pelo responsável) | ✅ |
| B7 | ADRs em `Docs/Technical/adr/NNNN-titulo.md`, formato **Michael Nygard** (Contexto, Decisão, Estado, Consequências) + secção **Alternativas consideradas** (§7.1.2) | ✅ |

## C · Comunicação (ADR-004)

| # | Decisão | Estado |
|---|---|---|
| C1 | **RabbitMQ** com Spring AMQP, exchanges *topic* e DLQ; corre localmente em contentor com volume persistente. O alojamento cloud estudado fica como alternativa futura (ADR-011) | ✅ tecnologia e âmbito local; 🔍 configuração |
| C2 | **Spring AMQP** | ✅ |
| C3 | **Tabela outbox** em cada serviço, publicada por um job de polling nosso. Consumidores idempotentes com tabela de mensagens processadas (`message_id`); após N tentativas, DLQ | ✅ (N e backoff: 🔍 Tiago) |
| C4 | **Só eventos entre serviços.** Cada serviço guarda uma cópia local do que precisa de outro (ex.: o Reports guarda as equipas, as suas permissões e categorias de ajuda do Events; todos guardam as *memberships* e as equipas de cada membro do Auth & Users). Custo aceite: consistência eventual | ✅ |
| C5 | **Polling** chega para o MVP (lista do backoffice, estado do pedido, anúncios). Uma instância por serviço por agora | ✅ |
| C6 | API gateway (e onde ficam rate limiting e CORS) | 🔍 Guerra |
| C7 | **Contract-first:** OpenAPI escrito à mão em `backend/<serviço>/contracts/openapi.yaml`; o `openapi-generator` gera interfaces e DTOs Spring e os clientes das apps. Caminhos com `/api/v1`. Base do [Spec-Driven Development](../Process/spec-driven-development.md) | ✅ |
| C8 | API outline (entregável 7 do Sprint A) e catálogo de eventos: que eventos cada serviço publica e consome | 🔍 Bruno |

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

| # | Pergunta | Estado |
|---|---|---|
| E1 | Que IdP (mock, Keycloak, Entra External ID…)? Tem de encaixar no serviço Auth & Users (A2) | 🔍 Guerra |
| E2 | Onde se valida o token e onde se verificam o papel e as permissões por evento. **Modelo de papéis definido a 22/10** pelo Tiago, a confirmar com a equipa ([`features.md` §4](../Product/features.md#4-roles-teams-and-permissions-22-october-2026)): `FarolAdmin` é o único papel global e não dá acesso aos dados de nenhum evento; `EventAdmin` e `RegularUser` são papéis da *membership* de cada evento (o mesmo utilizador pode ser EventAdmin num evento e RegularUser noutro); os EventAdmins criam equipas e dão a cada uma permissões de uma lista fixa (`HelpRequests.Triage`, `HelpRequests.Respond`, `Reports.Review` por tipo de report, `Reports.Respond`, `Announcements.Publish`, `Map.Edit`, `Transport.Edit`, `Entrances.Update`). As *memberships* e as equipas de cada membro ficam no Auth & Users; as equipas e as suas permissões ficam no Events. **Falta decidir:** se o token leva as permissões por evento ou só a identidade, e onde se junta papel + equipas + permissões para cada pedido (gateway, cada serviço com cópia local, ou consulta ao Auth & Users) | 🔍 Guerra |
| E3 | Autenticação entre serviços | 🔍 Guerra |
| E4 | Onde ficam os segredos | 🔍 Guerra |
| E5 | Security & Privacy assessment (entregável 9 do Sprint A) | 🔍 Guerra |

## F · Frontend

| # | Decisão | Estado |
|---|---|---|
| F1 | **Duas aplicações:** web React simples para a gestão de eventos; mobile React Native para o resto | ✅ |
| F2 | App web servida **localmente**; alojamento cloud não é exigido para a entrega autorizada (ADR-011) | ✅ âmbito; 🔍 implementação |
| F3 | **Mapa do recinto ([ADR-007](adr/0007-venue-map-representation.md)):** na app mobile, **`react-native-svg`** para os marcadores dentro do zoom/pan do **`react-native-zoom-toolkit`** (sobre Gesture Handler e Reanimated); posições em coordenadas normalizadas (`MapPosition`); filtro por tipo (US-22) com estado React. Funciona em iOS e Android, com Expo e com React Native *bare*, por isso não depende do F4. Antes de implementar o mapa, um *spike* confirma o zoom-toolkit com o Gesture Handler 3 num iPhone e num Android reais; se falhar, o zoom/pan escreve-se diretamente com Gesture Handler e Reanimated. A app web usa só `<img>` com marcadores posicionados. **PDF convertido em imagem no upload**, no serviço Events, com Apache PDFBox (só PDFs de uma página; imagem com no máximo 4096 px no lado maior; guarda-se o original e a imagem). Onde ficam os ficheiros depende do G1 | ✅ |
| F4 | Configuração do React Native (ex.: Expo ou não) e como a app mobile chega aos professores e à demo (§12) | 🔍 Bruno |

## G · Deploy e infraestrutura

| # | Pergunta | Estado |
|---|---|---|
| G1 | Execução/deployment **apenas local**, sem cloud devido aos custos; autorização do professor reportada pelo Tomás. Comparação das plataformas preservada no relatório de investigação | ✅ âmbito local |
| G2 | Avaliação de custos preservada; explicar no relatório/apresentação que o custo motivou não fazer deploy na cloud. Entra na estimativa de custos de operação (salários + infraestrutura) do Bruno | ✅ justificação registada |
| G3 | Região cloud não se aplica à entrega local; RGPD e fluxos de dados locais/fornecedores continuam sujeitos à revisão do Guerra | ✅ sem região cloud; 🔍 privacidade |
| G4 | Desenvolvimento, testes e demo localmente; não é necessário um ambiente cloud de produção | ✅ âmbito; 🔍 configuração |
| G5 | Especificação local versionada (Compose); Bicep/cloud IaC mantém-se como alternativa estudada, sem provisionamento exigido | 🔍 implementação local |
| G6 | Execução local com contentores independentes; procedimento reproduzível de arranque/atualização e smoke test (ADR-011) | ✅ alvo local; 🔍 implementação |
| G7 | Rollback por imagem e recuperação de estado local; volumes não são backups. Plano e medições de RTO/RPO continuam necessários | 🔍 implementação/validação |

## H · Pipeline de CI/CD

O Bruno vai avaliar e investigar a pipeline; as decisões abaixo são o ponto de partida dessa investigação.

| # | Decisão | Estado |
|---|---|---|
| H1 | **GitHub Actions** | ✅ |
| H2 | **Monorepo atual:** `backend/<serviço>/`, `frontend/`, `infra/`, `Docs/` | ✅ |
| H3 | **Maven**, **Java 21 (LTS)**, **Spring Boot 4.x**; imagens com **Dockerfile multi-stage** | ✅ |
| H4 | **Corre tudo, sempre:** verificações em cada PR, merge e tag para todos os serviços (sem path filters); imagens versionadas e validação/smoke test local. Cloud não é destino obrigatório | ✅ âmbito atualizado; 🔍 automação Bruno |
| H5 | A expectativa anterior de **deploy automático na produção cloud** é substituída pela entrega local autorizada por custos. Bruno define o procedimento/pipeline de entrega local; não se presume acesso automático à máquina pessoal | ✅ âmbito local; 🔍 automação Bruno |
| H6 | Autenticação para publicação/entrega de imagens, se aplicável; autenticação de deploy cloud fica adiada com essa alternativa | 🔍 Bruno |
| H7 | **SemVer, uma versão para todo o monorepo.** Tag git anotada `vMAJOR.MINOR.PATCH` em `main`; as imagens dos cinco serviços e do frontend levam a mesma versão sem o `v` (`farol-<serviço>:0.2.0`); builds fora de uma tag ficam `dev`. Fica em **0.x** durante os sprints: fim do Sprint A `v0.1.0` (walking skeleton), Sprint B `v0.2.0`, Sprint C `v1.0.0` (entrega final); **PATCH** só para corrigir uma release já demonstrada. A tag é a única fonte da versão (sem commits de *bump*). **Só tags git:** sem registry nem GitHub Releases; o CI constrói as imagens em cada PR, merge e tag sem as publicar (H4), e localmente constrói-se a partir da tag (rollback = construir a tag anterior, G7). As notas de cada release ficam no [`CHANGELOG.md`](../../CHANGELOG.md). Entra no ADR-010 | ✅ |
| H8 | Repositório **privado** | ✅ |
| H9 | Build e distribuição da app mobile na pipeline | 🔍 Bruno (depende de F4) |

## I · Observabilidade (§6.7)

| # | Pergunta | Estado |
|---|---|---|
| I1 | Stack | 🔍 Tomás |
| I2 | SLIs e SLOs | 🔍 Tomás |
| I3 | Dashboard e alertas | 🔍 Tomás |

## J · Resiliência e degradação (§6.8)

| # | Pergunta | Estado |
|---|---|---|
| J1 | Padrões e valores em cada chamada externa (timeout, retry, circuit breaker); N tentativas e backoff do C3 | 🔍 Tiago |
| J2 | Como demonstramos os modos degradados D1 (IA) e D2 (transportes) | 🔍 Tiago |
| J4 | **Simulador da API do Porto Digital, ou respostas reais gravadas**, sob controlo da equipa (pedido do professor): testes automáticos repetíveis, provocar falhas, atrasos e dados inválidos na demo do D2, e não depender da API real no dia da apresentação. A integração real continua a ser o Porto Digital. Falta escolher a forma e a ferramenta | ✅ (forma e ferramenta: 🔍 Guerra) |
| J3 | Escala para o pico e teste de carga (*performance evidence*, Sprint C). Tem em conta que o C5 fixa uma instância por serviço por agora | 🔍 Tiago |

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
- Deploy **apenas local**, sem cloud por causa dos custos, autorizado pelo professor (G1, G2; Tomás, 22/10, [registo](../Meetings/2026-10-22-local-deployment-clarification.md)). Investigação de deploy, observabilidade e IA no #53 (ADR-005, ADR-011 e ADR-012, propostos).
- Versionamento e etiquetas (H7, Bruno, 23/10): SemVer único para o monorepo, release no fim de cada sprint, só tags git. Entra no ADR-010.

**Em aberto, com responsável:**
1. **Cada responsável fecha a sua área** e regista aqui a decisão da equipa: Tomás (G3–G7 na entrega local, I, K, F2, alojamento do RabbitMQ), Guerra (C6, E, J4), Tomás + Guerra (modelo de domínio, incluindo o FarolAdmin), Tiago (J1–J3, backlog), Bruno (C8, D7, F4, H6, H9).
2. **Walking skeleton** (entregável 10): implementação mínima de ponta a ponta, com build automático, um teste inicial e deploy em contentor. Pedido de novo pelo professor a 22/10. **Tomás.**
3. **Nível 3 da vista física:** mostrar as cinco bases de dados, uma por serviço (professor, 22/10). **Bruno.**
4. **Transportes:** valores de x (intervalo do poll) e y (limite de frescura, aviso ao utilizador) (professor, 22/10; J1 e D2). **Tiago.**
5. **Estimativa de custos de operação:** salários + infraestrutura, simples; a parte de infraestrutura usa o G2 (Tomás) e serve também para explicar no relatório e na apresentação porque não há deployment na cloud (entrega apenas local, autorizada pelo professor; [registo](../Meetings/2026-10-22-local-deployment-clarification.md)). **Bruno.**
6. **Lista de NFRs** para a apresentação. **Tiago.**
7. **Manter o [`architecture.md`](architecture.md) alinhado** com este documento sempre que uma área fechar. **Quem fecha a área.**

**Sem responsável, ficam para o fim** ❗ (reunião com o professor, 22/10, [ata](../Meetings/2026-10-22-professor-meeting.md)):
- Apresentação (deck) com as secções pedidas pelo professor.
- PDF único com os documentos dos entregáveis do Sprint A.
- Lista de pressupostos e dependências, juntada a partir dos outros documentos.
