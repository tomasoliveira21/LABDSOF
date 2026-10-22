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
| C3 | **Tabela outbox** em cada serviço, publicada por um job de polling nosso. Consumidores idempotentes com tabela de mensagens processadas (`message_id`); após N tentativas, DLQ. **N e backoff:** **4 tentativas** no consumidor (`spring.rabbitmq.listener.simple.retry.max-retries=3`), com backoff exponencial **1 s → 2 s → 4 s** (máximo 10 s): cerca de 7 s até à DLQ. Porquê: as falhas que um retry resolve (ligação à base de dados perdida, broker a reiniciar) passam em segundos; o que dura mais é um bug ou uma mensagem inválida, que nenhum retry corrige, e mais tentativas só atrasariam a DLQ. Mensagens que não se convertem ou não passam a validação vão logo para a DLQ. Uma DLQ por fila (`<fila>.dlq`), com o motivo nos cabeçalhos (`RepublishMessageRecoverer`). **Mensagens na DLQ:** nunca se perdem nem se reprocessam sozinhas; há um alerta quando a DLQ não está vazia; corrigida a causa, são devolvidas à mão à fila original (seguro, porque os consumidores são idempotentes; à mão porque reprocessar sozinho uma mensagem que falhou por um bug entra em ciclo); as que nunca serão válidas ficam registadas e são apagadas. Uma falha da IA **não** é uma falha da mensagem: o worker regista o fallback (D1) e confirma a mensagem. Uma mensagem na DLQ nunca esconde um pedido de ajuda ao operador. Detalhe em [`architecture.md`](architecture.md) §6 | ✅ |
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

| # | Decisão | Estado |
|---|---|---|
| J1 | **Resilience4j 2.4** (`resilience4j-spring-boot4`, com suporte para Spring Boot 4 desde março de 2026), usado só nos adaptadores de saída (`adapters/out`). Porquê: é a única opção madura com circuit breaker para Spring Boot 4, configura-se no `application.yml` e publica métricas Micrometer e o estado no health check. Alternativas postas de parte: o retry do Spring Framework 7 (não tem circuit breaker; é o que o Spring AMQP usa no C3), Spring Cloud Circuit Breaker (mais uma camada por cima do Resilience4j) e Failsafe (sem configuração automática no Spring Boot). Os timeouts ficam no cliente HTTP (`RestClient`); o retry envolve o circuit breaker, por isso cada tentativa conta para o circuito. Valores e porquê nas tabelas abaixo | ✅ (valores da IA: a confirmar com K1; IdP: com E1) |
| J2 | **D1 e D2 demonstram-se com o WireMock à frente do sistema externo** (o simulador do J4). Em funcionamento normal, o WireMock reencaminha os pedidos para o sistema real (fornecedor de IA ou Porto Digital); na demo, um admin ativa pela API de administração do WireMock uma resposta com falha de ligação, atraso maior que o timeout ou dados inválidos, e depois remove-a. Em produção, o URL aponta direto para o sistema real. Porquê: os serviços não levam código de falhas (o URL de cada sistema externo já é configuração, ADR-004); funciona com HTTPS (o WireMock é o cliente HTTPS do sistema real, enquanto um proxy de rede como o Toxiproxy falha a verificação do certificado); e cumpre o pedido do professor. **Testes automáticos** em todas as PRs, com o WireMock no lugar do sistema externo: SC5 (D1) e US-15 (D2). **O que mostra que funcionou:** etiqueta *"Not triaged"* e banner (D1); *"Data from HH:MM"* e aviso, com os shuttles visíveis (D2); estado do circuit breaker, contador de fallbacks e idade dos dados no dashboard; logs com o motivo e o *correlation id*. **Frescura (D2): 5 min.** As posições chegam com cerca de 1 min (medido a 16 e 22/10) e um autocarro na cidade anda cerca de 2 km em 5 min, por isso uma posição mais antiga engana. Substitui as 24 h, que eram para horários GTFS, abandonados com a resposta do professor (ADR-008). Guião passo a passo em [`architecture.md`](architecture.md) §9 | ✅ (ambiente da demo: 🔍 Tomás, G4) |
| J4 | **Simulador da API do Porto Digital, ou respostas reais gravadas**, sob controlo da equipa (pedido do professor): testes automáticos repetíveis, provocar falhas, atrasos e dados inválidos na demo do D2, e não depender da API real no dia da apresentação. A integração real continua a ser o Porto Digital. **Forma e ferramenta: WireMock** (decidido com o J2), em contentor: em modo *proxy* para a API real; respostas reais gravadas (*record and playback*) para os testes e para o dia da apresentação; respostas de falha para a demo. O mesmo WireMock serve de simulador do fornecedor de IA. Porquê: uma só ferramenta para testes, demo e apresentação, já suportada pelo Testcontainers, sem termos de escrever e manter um simulador nosso. Alternativas: simulador próprio em Spring Boot (mais código para manter perto do formato NGSI v2) e Toxiproxy (só falhas de rede, não grava respostas nem gera dados inválidos, e falha com HTTPS) | ✅ |
| J3 | **Pico esperado: ~1 300 pedidos de leitura/s durante 10 min** no fim do último concerto (12 000 apps abertas a fazer polling de 10 em 10 s), mais **1 pedido de ajuda/s**; cálculo, fontes e pressupostos em [`architecture.md`](architecture.md) §10.1. **Com uma instância por serviço (C5):** (1) menos trabalho por pedido: pedidos condicionais (ETag a partir da hora da última atualização → 304), imagem do mapa com URL versionado e cache longa, polling com *jitter* e só com a app em primeiro plano; (2) isolamento: os pedidos de ajuda ficam no Reports, longe do pico de leituras (Events e Transports); (3) escala vertical. Porquê esta ordem: os passos 1 e 2 não custam infraestrutura e protegem o que importa (o pedido de ajuda); escalar é o último recurso, e o C5 ainda o limita. **O primeiro serviço a ter mais instâncias**, quando o C5 mudar, é o **Events** (recebe a maior parte do polling: anúncios, mapa, pontos de interesse), depois o Transports; antes disso, o job do outbox e as importações têm de correr numa só instância (`FOR UPDATE SKIP LOCKED` ou ShedLock). **Teste de carga com k6** (em vez de Gatling ou JMeter): cenários com taxa de chegada (como os pedidos chegam na realidade), critérios de aprovação escritos como *thresholds* que fazem falhar a execução, um só binário fácil de correr no GitHub Actions. 5 cenários (smoke, pico do fim do concerto, pedidos de ajuda durante o pico, stress até ao limite, D1 com carga); corre à mão (`workflow_dispatch`) contra o ambiente de deploy no Sprint C, com a IA substituída pelo WireMock | ✅ (ambiente do teste: 🔍 Tomás, G1) |

**Valores do J1 por chamada externa:**

| Chamada externa | Timeout | Retry | Circuit breaker | Quando falha |
|---|---|---|---|---|
| **Fornecedor de IA** (Reports, worker de triagem) | ligação 2 s, resposta 10 s | 2 tentativas, espera de 2 s; só em timeout, erro de ligação, 5xx e 429; nunca em 4xx nem em resposta inválida | últimas 10 chamadas, mínimo 4; abre com ≥ 50 % de falhas ou de chamadas lentas (> 8 s); 30 s aberto; 1 chamada de teste | Fallback D1. Pior caso ≈ 22 s, dentro dos 30 s do SC3. **A confirmar com K1:** um modelo local (Ollama em CPU) pode precisar de resposta 20 s e de 1 só tentativa |
| **Porto Digital** (Transports, importação agendada a cada 30 s) | ligação 3 s, resposta 5 s | 3 tentativas, esperas de 1 s e 2 s; não repete com o circuito aberto | últimas 6 chamadas, mínimo 3; abre com ≥ 50 % de falhas; 60 s aberto; 1 chamada de teste | D2. Só os autocarros a menos de 2 km do recinto (consulta geográfica NGSI: 10 KB em vez de 190–300 KB, medido a 22/10) |
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

1. **Resposta do professor:** recebida. A4 e a fonte de transportes (A2, B2) fechados; este documento, o [`architecture.md`](architecture.md), os diagramas e os ADR-001 e ADR-008 atualizados.
2. **Walking skeleton** (entregável 10): implementação mínima de ponta a ponta, com build automático, um teste inicial e deploy em contentor. Fica para uma fase futura, ainda sem responsável.
3. **Cada responsável fecha a sua área** e regista aqui a decisão da equipa: Tomás (G, I, K, F2, alojamento do RabbitMQ), Guerra (C6, E), Tomás + Guerra (modelo de domínio, incluindo o P4), Tiago (F3 e backlog; J fechado a 22/10), Bruno (C8, D7, F4, H6, H7, H9).
4. **Bruno:** diagramas C4 e 4+1 em draw.io a partir das decisões ✅.
5. **Bruno: escrever os ADRs** já prontos (lista no [`architecture.md`](architecture.md) §13): ADR-002 (tecnologias), ADR-003 (dados), ADR-004 (comunicação) e ADR-009 (documentação). Os restantes escrevem-se quando a área fechar.
6. **Manter o [`architecture.md`](architecture.md) alinhado** com este documento sempre que uma área fechar (última atualização: 22/10).
