# Decisões Arquiteturais

> LABDSOF 2026/27 · Sprint A · Enunciado §6 (requisitos técnicos), §7.1.2 (entregáveis 7, 8 e 10), §8.4 e §8.5 (contentores e CI/CD)
> **Estado (21 de outubro de 2026):** registo de **todas** as decisões arquiteturais da equipa. **Todas as decisões aqui registadas foram tomadas pela equipa**, não por um membro em particular. Quando uma decisão fica final, passa para um ADR e para o [`architecture.md`](architecture.md).
> **Histórico:** reuniões de 11 e 15 de outubro de 2026 ([`2026-10-08-sprint-1-gap-review.md`](../Meetings/2026-10-08-sprint-1-gap-review.md)) e resposta do professor de 21 de outubro de 2026 ao email de 15/10 (§Respostas do professor).

**Legenda do estado:**
✅ Decidido · ⏳ À espera da resposta do professor · 🔍 Em investigação (responsável indicado) · 🔮 Melhoria futura (fora do MVP) · ❗ Por decidir, **sem responsável**

## Responsáveis pelas investigações em curso

| Membro | Área |
|---|---|
| **Tomás** | Deploy (Azure, ECS ou Vercel) · integração de IA (se é viável fazer deploy de Ollama) · observabilidade · avaliação de Responsible AI e dataset de avaliação da triagem |
| **Guerra** | Identidade e segurança · API gateway · Security & Privacy assessment |
| **Tomás + Guerra** | Modelo de domínio, incluindo o platform admin (P4) |
| **Tiago** | Resiliência e degradação · início do backlog · biblioteca do mapa do recinto |
| **Bruno** | Diagramas (draw.io, com o MCP do draw.io) · depois, avaliação e investigação da pipeline de CI/CD · setup e distribuição da app mobile · eliminação de conta · API outline e catálogo de eventos |

## Investigação de deploy, observabilidade e IA (22 de outubro de 2026)

**[Issue #53](https://github.com/tomasoliveira21/LABDSOF/issues/53): investigação documentada, propostas para revisão da equipa.** O [relatório e mapa de cobertura](../Research/architecture-investigation/README.md) cobre G1–G7, I1–I3, K1–K2, F2, alojamento do RabbitMQ (C1) e número de servidores PostgreSQL (D2). Inclui comparação de plataformas/custos, observabilidade, avaliação Responsible AI, 100 casos sintéticos e baseline reproduzível.

Propostas: [ADR-005](adr/0005-ai-triage-provider-and-evaluation.md) (IA), [ADR-011](adr/0011-deployment-and-infrastructure.md) (infraestrutura), [ADR-012](adr/0012-observability.md) (observabilidade). **Mantêm-se os estados 🔍:** a investigação não é aprovação da equipa. Faltam validação dos rótulos, medições de modelos/SC3/SC5, orçamento/créditos, quotas e revisão de privacidade. Não houve deploy nem contratação de serviços. A proposta de recuperação G7 exige rever o âmbito de backup D5 antes de ser aceite.

## 0 · Base

| Decisão | Estado |
|---|---|
| **Backend em Spring Boot** (Java) | ✅ |
| **Frontend web simples em React**, só para a gestão de eventos | ✅ |
| **App mobile em React Native** para todas as outras funcionalidades | ✅ |
| **Arquitetura distribuída**; o monólito sem fronteiras impostas está excluído | ✅ |
| **C4 model nos 4 níveis** | ✅ |
| **Pipeline de CI e CD completa** | ✅ |
| **Plataforma de deploy:** o Azure (Azure for Students) foi escolhido a 11/10 e **voltou a estar em aberto**: Azure, ECS ou Vercel. A decisão fica para depois da investigação | 🔍 Tomás |

## A · Estilo arquitetural (ADR-001)

| # | Decisão | Estado |
|---|---|---|
| A1 | **Microserviços** como estado final. Cumprem o §6.2 sem ter de provar que os serviços seriam complexidade desnecessária, isolam falhas e dão contratos pequenos por serviço (base do Spec-Driven Development) | ✅ |
| A2 | **5 serviços.** **Auth & Users** (authN, authZ, contas, papéis por evento, amigos: F1, F2, papéis do F4, F18) · **Events** (eventos, mapa e pontos de interesse, equipas de resposta, anúncios, check-in, entradas: F3, equipas do F4, F5, F10, F16, F17) · **Reports** (relatos comunitários e confirmação, pedidos de ajuda, backoffice, triagem IA com fallback: F6, F7, F8, F12, F13) · **Transports** (F9; fonte: Plataforma Urbana do Porto Digital (FIWARE, NGSI v2), aceite pelo professor a 21/10, com simulador sob o nosso controlo (J4); ADR-008) · **Badges** (F14, F15) | ✅ |
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
| B6 | **Vistas do 4+1, sem cenários** (os workflows W1–W8 já os cobrem): lógica (C4 1–3 e modelo de domínio), processos (dinâmicos: pedido de ajuda e triagem assíncrona), desenvolvimento (repositório, módulos Maven, pacotes hexagonais), física (deployment; depende da plataforma, 🔍 Tomás) | ✅ |
| B7 | ADRs em `Docs/Technical/adr/NNNN-titulo.md`, formato **Michael Nygard** (Contexto, Decisão, Estado, Consequências) + secção **Alternativas consideradas** (§7.1.2) | ✅ |

## C · Comunicação (ADR-004)

| # | Decisão | Estado |
|---|---|---|
| C1 | **RabbitMQ**: gratuito; igual localmente, nos testes (Testcontainers) e na cloud; exchanges *topic* para pub-sub; DLQ com *dead-letter exchange*. Onde corre na cloud: depende da plataforma | ✅ (alojamento: 🔍 Tomás) |
| C2 | **Spring AMQP** | ✅ |
| C3 | **Tabela outbox** em cada serviço, publicada por um job de polling nosso. Consumidores idempotentes com tabela de mensagens processadas (`message_id`); após N tentativas, DLQ | ✅ (N e backoff: 🔍 Tiago) |
| C4 | **Só eventos entre serviços.** Cada serviço guarda uma cópia local do que precisa de outro (ex.: o Reports guarda as equipas de resposta do Events). Custo aceite: consistência eventual | ✅ |
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
| E2 | Onde se valida o token e onde se verifica o papel por evento; o `PlatformAdmin` é papel global? | 🔍 Guerra |
| E3 | Autenticação entre serviços | 🔍 Guerra |
| E4 | Onde ficam os segredos | 🔍 Guerra |
| E5 | Security & Privacy assessment (entregável 9 do Sprint A) | 🔍 Guerra |

## F · Frontend

| # | Decisão | Estado |
|---|---|---|
| F1 | **Duas aplicações:** web React simples para a gestão de eventos; mobile React Native para o resto | ✅ |
| F2 | Alojamento da app web | 🔍 Tomás (deploy) |
| F3 | Biblioteca do mapa do recinto (ADR-007), agora em React Native | 🔍 Tiago |
| F4 | Configuração do React Native (ex.: Expo ou não) e como a app mobile chega aos professores e à demo (§12) | 🔍 Bruno |

## G · Deploy e infraestrutura

| # | Pergunta | Estado |
|---|---|---|
| G1 | Plataforma (Azure, ECS ou Vercel) e onde correm os contentores | 🔍 Tomás |
| G2 | Custo e orçamento | 🔍 Tomás |
| G3 | Região (RGPD) | 🔍 Tomás |
| G4 | Ambientes | 🔍 Tomás |
| G5 | Infraestrutura como código | 🔍 Tomás |
| G6 | Execução local (`docker compose`) | 🔍 Tomás |
| G7 | Rollback e recuperação | 🔍 Tomás |

## H · Pipeline de CI/CD

O Bruno vai avaliar e investigar a pipeline; as decisões abaixo são o ponto de partida dessa investigação.

| # | Decisão | Estado |
|---|---|---|
| H1 | **GitHub Actions** | ✅ |
| H2 | **Monorepo atual:** `backend/<serviço>/`, `frontend/`, `infra/`, `Docs/` | ✅ |
| H3 | **Maven**, **Java 21 (LTS)**, **Spring Boot 4.x**; imagens com **Dockerfile multi-stage** | ✅ |
| H4 | **Corre tudo, sempre:** todas as verificações em cada PR, no merge e nas tags, para todos os serviços (sem path filters). Imagem publicada e deploy no merge para `main`, seguido de smoke test | ✅ |
| H5 | **Continuous deployment** por agora: cada merge em `main` vai para produção | ✅ |
| H6 | Autenticação da pipeline na cloud (depende de G1) | 🔍 Bruno |
| H7 | Versionamento e etiquetas | 🔍 Bruno |
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
| J4 | **Simulador da API do Porto Digital, ou respostas reais gravadas**, sob controlo da equipa (pedido do professor): testes automáticos repetíveis, provocar falhas, atrasos e dados inválidos na demo do D2, e não depender da API real no dia da apresentação. A integração real continua a ser o Porto Digital. Falta escolher a forma e a ferramenta | ✅ (forma e ferramenta: ❗) |
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

1. **Resposta do professor:** recebida. A4 e a fonte de transportes (A2, B2) fechados; este documento, o [`architecture.md`](architecture.md), os diagramas e os ADR-001 e ADR-008 atualizados.
2. **Walking skeleton** (entregável 10): implementação mínima de ponta a ponta, com build automático, um teste inicial e deploy em contentor. Fica para uma fase futura, ainda sem responsável.
3. **Cada responsável fecha a sua área** e regista aqui a decisão da equipa: Tomás (G, I, K, F2, alojamento do RabbitMQ), Guerra (C6, E), Tomás + Guerra (modelo de domínio, incluindo o P4), Tiago (J, F3 e backlog), Bruno (C8, D7, F4, H6, H7, H9).
4. **Bruno:** diagramas C4 e 4+1 em draw.io a partir das decisões ✅.
5. **Bruno: escrever os ADRs** já prontos (lista no [`architecture.md`](architecture.md) §13): ADR-002 (tecnologias), ADR-003 (dados), ADR-004 (comunicação) e ADR-009 (documentação). Os restantes escrevem-se quando a área fechar.
6. **Manter o [`architecture.md`](architecture.md) alinhado** com este documento sempre que uma área fechar (última atualização: 21/10).
