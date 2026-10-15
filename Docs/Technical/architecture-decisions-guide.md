# Guia de Decisões da Arquitetura de Alto Nível

> LABDSOF 2026/27 · Sprint A · Enunciado §6 (requisitos técnicos), §7.1.2 (entregáveis 7, 8 e 10), §8.4 e §8.5 (contentores e CI/CD), §10 (cada aluno tem de saber explicar a arquitetura)
> **Estado:** documento de trabalho, em português, para estudo e preparação das decisões. **Não é um entregável.** Quando uma pergunta tiver resposta, a decisão passa para um ADR e para o [`architecture.md`](architecture.md).
> **Como usar:** a Parte 1 dá a teoria necessária. A Parte 2 tem as perguntas, agrupadas por área. Cada pergunta indica porque é importante, as opções com prós e contras, e as secções da Parte 1 a ler antes. Escreve a resposta na linha **Decisão** e o ADR correspondente.
> **Decisões propostas (15 de outubro de 2026):** o Bruno respondeu às áreas A, B, C, D e H; o Tiago valida-as e decide as que estão marcadas **Tiago** (E, F, G, I, J, K e algumas de C, D e H). Só depois disso passam para ADRs.
> **Atenção:** preços, limites de planos gratuitos e disponibilidade de serviços no Azure mudam com frequência. Os valores indicados são ordens de grandeza; confirma-os na [calculadora de preços do Azure](https://azure.microsoft.com/pricing/calculator/) antes de decidir.

## 0. O que já está decidido (11 de outubro de 2026)

| Decisão | Consequência para este guia |
|---|---|
| **Backend em Spring Boot** (Java) | Pergunta-se apenas a versão, as bibliotecas e o build |
| **Frontend em React** | Pergunta-se apenas o build, o alojamento e o PWA |
| **Deploy no Azure**, com a subscrição **Azure for Students** | Os serviços têm de caber no crédito; detalhes do Azure: Tiago |
| **Arquitetura distribuída: microserviços ou monólito modular.** O monólito clássico (sem fronteiras impostas) está excluído | A primeira decisão (§A) é escolher entre os dois, com avaliação documentada (ADR-001) |
| **Documentação com o C4 model, nos 4 níveis** | §B decide ferramenta, âmbito e detalhe de cada nível |
| **Pipeline de CI e CD completa** | §H decide ferramenta, etapas e estratégia de deploy |

Já está proposto no [`architecture.md`](architecture.md), e serve de ponto de partida: drivers D1–D9, dois contextos (Event Configuration e Incident Management), worker de triagem assíncrono, padrão outbox, modos degradados D1 (IA indisponível) e D2 (transportes indisponíveis).

---

# Parte 1 · Conhecimentos base

## 1.1 C4 model

O [C4 model](https://c4model.com) (Simon Brown) descreve uma arquitetura em **quatro níveis de zoom**, como um mapa: país, cidade, bairro, rua.

| Nível | Diagrama | Responde a | Elementos | Público |
|---|---|---|---|---|
| 1 | **System Context** | O que é o sistema, quem o usa e com que sistemas fala? | Pessoas, o *software system* (caixa única), sistemas externos | Toda a gente, incluindo não técnicos |
| 2 | **Container** | Que peças executáveis compõem o sistema e como comunicam? | *Containers*: aplicações e bases de dados (ver nota) | Equipa técnica, operações |
| 3 | **Component** | Como está organizado **um** container por dentro? | Componentes: agrupamentos de código atrás de uma interface (ex.: `HelpRequestController`, `TriageService`, `HelpRequestRepository`) | Programadores |
| 4 | **Code** | Como está implementado **um** componente? | Classes, interfaces (diagrama de classes UML ou ER) | Programadores; o C4 diz que é **opcional** e que deve ser gerado por ferramentas quando possível |

**"Container" no C4 não é um contentor Docker.** É qualquer coisa que tem de estar a correr para o sistema funcionar: uma aplicação Spring Boot, uma SPA React no browser, uma base de dados, uma fila. Muitas vezes coincide com um contentor Docker, mas não é obrigatório.

**Diagramas suplementares** que o C4 também define, e que o enunciado pede indiretamente:
- **Deployment diagram**: onde corre cada container (ex.: Azure Container Apps, Azure Database for PostgreSQL). Cobre o *deployment view* do §7.1.2.
- **Dynamic diagram**: a sequência de interações num cenário (ex.: pedido de ajuda → fila → worker de triagem → backoffice). Cobre o *asynchronous workflow* do §7.1.2.
- **System Landscape**: vários sistemas da organização; não se aplica aqui.

**Notação:** o C4 não impõe símbolos, mas cada diagrama deve ter título, legenda, e cada caixa deve indicar nome, tipo, tecnologia e responsabilidade. Cada seta tem uma etiqueta com o que faz e o protocolo (ex.: "Publica pedidos novos [AMQP]").

**Ferramentas "diagram as code"** (o enunciado §9 prefere documentação mantida junto ao código):

| Ferramenta | Como funciona | Prós | Contras |
|---|---|---|---|
| **Structurizr DSL** | Um único **modelo** em texto; cada vista (níveis 1–3, deployment, dynamic) é gerada a partir dele | Feito pelo autor do C4; modelo único evita diagramas inconsistentes | Precisa de exportar imagens (Structurizr Lite em Docker ou CLI); o GitHub não o renderiza |
| **C4-PlantUML** | Biblioteca de macros PlantUML (`Person()`, `Container()`, `Rel()`) | Muito usada, bom aspeto, suporta todos os níveis | Cada diagrama é independente; precisa de renderizador ou de imagem exportada |
| **Mermaid C4** | Sintaxe `C4Context`, `C4Container`, … | O GitHub renderiza Mermaid diretamente no Markdown; o repositório já usa Mermaid | O suporte C4 do Mermaid é **experimental**: layout pouco controlável |
| **draw.io / IcePanel** | Desenho visual | Rápido para quem não quer código | Não é código; desatualiza-se facilmente |

## 1.2 Microserviços e monólito modular

**Monólito modular:** uma única aplicação (um só deployável), dividida internamente em **módulos** com fronteiras impostas. Cada módulo tem a sua API pública e o seu modelo de dados; os outros módulos não podem usar as classes internas nem as tabelas de outro módulo. Comunicam por chamadas de método à API pública ou por eventos internos.

**Microserviços:** cada contexto é uma aplicação separada, **deployável de forma independente**, com a sua própria base de dados (ou schema), e comunica pela rede (HTTP, mensagens).

| Critério | Monólito modular | Microserviços |
|---|---|---|
| Deploy | Um artefacto; tudo é publicado junto | Cada serviço é publicado sozinho |
| Falhas | Um erro grave (ex.: memória) derruba tudo | Uma falha fica isolada no serviço, se a comunicação for bem desenhada |
| Escala | Escala-se a aplicação inteira | Escala-se só o serviço com carga (ex.: leituras do participante, driver D2) |
| Comunicação | Chamada de método: rápida, transacional, sem falhas de rede | Rede: latência, falhas parciais, timeouts, retries |
| Consistência de dados | Transação única possível entre módulos | Consistência eventual; sagas ou outbox |
| Custo operacional | Um pipeline, um contentor, um conjunto de logs | N pipelines/imagens, observabilidade distribuída, mais custo no Azure |
| Refatorar fronteiras | Barato (mover código) | Caro (mudar contratos de rede e dados) |
| Imposição de fronteiras | Tem de ser **verificada por ferramenta** (Spring Modulith, ArchUnit, módulos Maven) | É imposta pela rede |

**O que o enunciado diz (§6.2):** pede **pelo menos dois componentes de backend deployáveis de forma independente**. Aceita um monólito modular apenas com "evidência convincente" de que os serviços trariam complexidade desnecessária, e com fronteiras **explícitas e verificáveis**. Em ambos os casos é preciso justificar: fronteiras, responsabilidades e dados, comunicação síncrona e assíncrona, acoplamento e coesão, implicações de deploy, tratamento de falhas.

**Conceitos a dominar:**
- **Bounded context** (DDD): fronteira dentro da qual um modelo e a sua linguagem têm um significado único. Bons candidatos a módulos ou serviços. O [`domain-model.md`](domain-model.md) define dois.
- **Acoplamento** (quanto um módulo depende de outro) e **coesão** (quanto o que está dentro de um módulo pertence junto). Objetivo: baixo acoplamento, alta coesão.
- **Monólito distribuído:** microserviços que têm sempre de ser publicados juntos ou que se chamam em cadeia de forma síncrona. Tem os custos das duas abordagens e as vantagens de nenhuma; é o principal risco dos microserviços.
- **Lei de Conway:** a arquitetura tende a copiar a estrutura de comunicação da equipa. Com 4–5 pessoas, quantos serviços conseguem manter?
- **Híbrido:** um monólito modular **mais** um componente separado (ex.: o worker de triagem) já tem dois deployáveis independentes.
- **Spring Modulith:** projeto Spring para monólitos modulares. Cada pacote de topo é um módulo; `ApplicationModules.of(App.class).verify()` falha um teste se um módulo usar classes internas de outro. Tem eventos entre módulos com *Event Publication Registry* (guarda o evento na base de dados na mesma transação, um outbox) e gera documentação, incluindo **diagramas C4 de componentes**.
- **ArchUnit:** biblioteca de testes que verifica regras de dependência entre pacotes; serve para os dois estilos.

## 1.3 Comunicação síncrona e assíncrona

- **Síncrona** (HTTP/REST, gRPC): quem chama espera pela resposta. Simples, mas acopla no tempo: se o destino está em baixo, quem chama também falha.
- **Assíncrona** (mensagens): quem envia publica numa fila ou tópico e continua. O consumidor processa quando puder.
  - **Fila (queue):** cada mensagem é processada por **um** consumidor. Ex.: trabalhos de triagem.
  - **Tópico (topic / pub-sub):** cada mensagem chega a **todos** os subscritores. Ex.: "pedido criado" interessa à triagem, às notificações e às métricas.
- **Garantias de entrega:** *at-most-once* (pode perder), *at-least-once* (pode duplicar; o mais comum), *exactly-once* (na prática, conseguido com at-least-once + consumidor idempotente).
- **Consumidor idempotente:** processar a mesma mensagem duas vezes tem o mesmo efeito que uma (ex.: guardar o `messageId` já processado).
- **Dead-letter queue (DLQ):** fila para onde vão as mensagens que falharam N vezes, para análise.
- **Outbox pattern:** o serviço grava os dados **e** o evento a enviar na mesma transação da base de dados; um processo à parte publica os eventos pendentes. Resolve o problema "gravei mas não publiquei" (ou o inverso).
- **Saga:** sequência de transações locais em vários serviços, com ações de compensação se um passo falhar. Só é necessária se houver operações de negócio que atravessem serviços.
- **Push para o cliente:** **WebSocket** (bidirecional; no Spring é comum STOMP sobre WebSocket), **Server-Sent Events** (só servidor → cliente, sobre HTTP simples, reconecta sozinho), **polling** (o cliente pergunta de X em X segundos). Com várias instâncias do serviço, as ligações abertas ficam espalhadas: é preciso um broker partilhado ou um serviço gerido (Azure Web PubSub / SignalR Service).
- **API Gateway:** ponto de entrada único: roteamento, TLS, autenticação, rate limiting, CORS. No mundo Spring: **Spring Cloud Gateway**.
- **Contratos de API:** **OpenAPI** descreve a API REST; **springdoc-openapi** gera-a a partir do código. Abordagem *code-first* (gerar do código) ou *contract-first* (escrever o contrato e gerar código). **Testes de contrato** (Spring Cloud Contract, Pact) verificam que consumidor e produtor concordam.

## 1.4 Dados

- **Database per service:** cada serviço é o único dono dos seus dados; os outros só lhes acedem pela API ou por eventos. Variantes, da mais isolada à mais barata: servidor por serviço → base de dados por serviço no mesmo servidor → **schema por serviço** na mesma base de dados (com utilizadores diferentes para impedir acessos cruzados).
- **Multi-tenancy:** aqui cada **evento** é um tenant (driver D1). Opções: coluna `event_id` em todas as tabelas e filtro em todas as queries; **Row-Level Security** do PostgreSQL (a base de dados filtra pelas linhas do tenant atual); schema por evento (pesado).
- **Migrações:** **Flyway** (scripts SQL versionados `V1__init.sql`) ou **Liquibase** (changelogs em XML/YAML/SQL). Correm no arranque da aplicação ou num passo do pipeline.
- **Cache:** cabeçalhos HTTP (`Cache-Control`, `ETag`) + CDN; cache em memória (Caffeine); cache distribuída (Redis). Para o pico de leituras (D2), o que é público e igual para todos (mapa, horários) pode ser servido por cache HTTP/CDN.
- **Ficheiros** (mapas do recinto): armazenamento de objetos (**Azure Blob Storage**), não a base de dados.

## 1.5 Contentores e orquestração (Kubernetes do zero)

- **Imagem Docker:** pacote imutável com a aplicação e tudo o que precisa para correr. Definida por um `Dockerfile`. **Multi-stage build:** uma etapa compila (com JDK e Maven), outra só leva o JAR e um JRE, e a imagem final fica pequena.
- **Alternativas ao Dockerfile para Spring Boot:** **Buildpacks** (`mvn spring-boot:build-image`, sem Dockerfile) e **Jib** (plugin Maven/Gradle, não precisa de Docker instalado).
- **Registry:** onde as imagens ficam guardadas (Azure Container Registry, GitHub Container Registry, Docker Hub). O deploy puxa a imagem do registry.
- **Docker Compose:** arranca vários contentores numa máquina com um ficheiro YAML. Ideal para correr tudo localmente.
- **Orquestrador:** gere contentores em várias máquinas: reinicia os que falham, escala, faz rolling updates, faz a descoberta de serviços.
- **Kubernetes, os conceitos mínimos:**
  - **Pod:** a unidade mínima; um ou mais contentores que partilham rede. Os pods são descartáveis.
  - **Deployment:** declara "quero 3 réplicas desta imagem"; o Kubernetes mantém esse estado e faz rolling updates.
  - **Service:** nome DNS estável e balanceamento para um conjunto de pods.
  - **Ingress:** expõe serviços HTTP para fora do cluster, com regras de roteamento.
  - **ConfigMap / Secret:** configuração e segredos injetados nos pods.
  - **Probes:** *liveness* (está vivo? senão reinicia) e *readiness* (pode receber tráfego?). O Spring Boot Actuator expõe ambos.
  - **HPA:** Horizontal Pod Autoscaler; escala réplicas pela CPU ou por métricas.
  - **Helm:** gestor de pacotes/templates de manifestos Kubernetes.
  - Tudo é declarado em YAML e aplicado com `kubectl apply`. É **declarativo**: descreves o estado desejado, não os passos.
- **Azure Container Apps (ACA):** serviço gerido que corre **por cima de Kubernetes** mas esconde-o: não há nós nem YAML de Kubernetes para gerir. Traz ingress, HTTPS, **revisões** (cada deploy é uma revisão; dá para dividir tráfego e voltar atrás), escala automática com **KEDA** (inclusive **até zero** réplicas, e por tamanho de fila), probes, segredos, rede interna entre apps e **Dapr** opcional. Para uma equipa com pouco Kubernetes, dá a maior parte dos benefícios sem a curva de aprendizagem.
- **Arranque a frio (cold start):** com escala até zero, o primeiro pedido acorda a aplicação; o Spring Boot demora vários segundos a arrancar. Mitigações: mínimo de 1 réplica nos serviços críticos, CDS/AOT do Spring, ou imagem nativa GraalVM.

## 1.6 Azure e o Azure for Students

**Azure for Students:** crédito de **US$100**, válido **12 meses**, sem cartão de crédito, renovável enquanto fores estudante. Inclui também alguns serviços gratuitos. Quando o crédito acaba, **os recursos são desativados** até a subscrição ser convertida. Normalmente fica **numa conta pessoal**: os colegas entram por RBAC (funções como *Contributor* ou *Reader*). Algumas subscrições de estudante restringem as **regiões** permitidas por política; confirmar quais.

**Conceitos:** **Subscription** (conta de faturação) → **Resource Group** (pasta lógica de recursos; apagar o grupo apaga tudo) → **Resource**. **Managed Identity**: identidade dada a um recurso Azure para aceder a outros (ex.: a app lê o Key Vault) sem passwords. **RBAC**: quem pode fazer o quê em que âmbito.

**Serviços relevantes para o Farol:**

| Necessidade | Serviço Azure | Notas de custo e limites (confirmar) |
|---|---|---|
| Correr contentores do backend | **Container Apps** · App Service (Web App for Containers) · AKS (Kubernetes gerido) · Container Instances · VM com Docker Compose | O ACA tem uma cota gratuita mensal de vCPU-segundos e pedidos, e escala até zero. O AKS cobra os nós (VMs) sempre ligados. O App Service cobra o plano mesmo parado |
| Frontend React (ficheiros estáticos) | **Static Web Apps** · Blob Storage (static website) · contentor nginx | O Static Web Apps tem plano gratuito, HTTPS, domínio e ambientes de preview por PR |
| Registry de imagens | **Azure Container Registry** · GitHub Container Registry | O ACR Basic tem custo diário pequeno; o GHCR é gratuito para pacotes públicos |
| Base de dados relacional | **Azure Database for PostgreSQL – Flexible Server** · Azure SQL Database | Escalão *Burstable* (B1ms) é o mais barato; pode ser parado quando não está em uso |
| Mensagens | **Service Bus** (filas e tópicos, DLQ nativa) · Storage Queues · Event Grid · Event Hubs · RabbitMQ em contentor | Service Bus **Basic só tem filas**, **Standard** tem tópicos. Existe um **emulador do Service Bus** em Docker para desenvolvimento local |
| Ficheiros (mapas) | **Blob Storage** | Barato; **Azurite** emula-o localmente |
| Segredos | **Key Vault** | Custo por operação, baixo |
| Identidade | **Microsoft Entra ID** (equipa) · **Entra External ID** (clientes/participantes) | O External ID tem um escalão gratuito generoso de utilizadores ativos por mês |
| Observabilidade | **Azure Monitor**: **Application Insights** + **Log Analytics** · Azure Managed Grafana | Paga-se por GB ingerido, com uma cota gratuita mensal; logs verbosos gastam crédito |
| Gateway / CDN | **API Management** (escalão Consumption) · **Front Door** · Spring Cloud Gateway num contentor · ingress do ACA | O Front Door tem custo base mensal; o APIM Consumption paga por chamada |
| IA | **Azure OpenAI / Azure AI Foundry** · API OpenAI ou outra | Confirmar se a subscrição de estudante tem acesso e quotas aos modelos |
| Tempo real | **Web PubSub** / **SignalR Service** | Escalão gratuito com limite de ligações |

**Infraestrutura como código (IaC):** descrever os recursos Azure em ficheiros versionados em vez de cliques no portal. **Bicep** (linguagem nativa do Azure, simples), **Terraform** (multi-cloud, muito usado no mercado), **Azure Developer CLI (`azd`)** (templates que juntam código, Bicep e pipeline). Responde ao §8.4 (*reproducible builds*, *deployment instructions*).

## 1.7 Ecossistema Spring

| Necessidade | Biblioteca |
|---|---|
| API REST e validação | Spring Web (MVC) ou WebFlux (reativo); Bean Validation |
| Persistência | Spring Data JPA (Hibernate) ou Spring Data JDBC; Flyway/Liquibase |
| Segurança | Spring Security, **OAuth2 Resource Server** (valida JWT), method security (`@PreAuthorize`) |
| Mensagens | **Spring Cloud Stream** (abstração sobre brokers, com *binders* para Service Bus, RabbitMQ, Kafka) · **Spring Cloud Azure** (integração direta com Service Bus, Key Vault, Blob) · Spring AMQP (RabbitMQ) |
| Resiliência | **Resilience4j**: timeout, retry, circuit breaker, rate limiter, bulkhead |
| Saúde e métricas | **Spring Boot Actuator** (`/actuator/health/liveness`, `/readiness`), **Micrometer** (métricas), **Micrometer Tracing** / **OpenTelemetry** (traces) |
| Gateway | Spring Cloud Gateway |
| Documentação da API | springdoc-openapi (Swagger UI) |
| Módulos | **Spring Modulith** |
| IA | **Spring AI** (cliente para OpenAI, Azure OpenAI, Ollama, …) |
| Testes | JUnit 5, Mockito, **Testcontainers** (PostgreSQL e broker reais em Docker durante os testes), ArchUnit, Spring Cloud Contract |

**Versões:** Java tem versões LTS (17, 21, 25); o Spring Boot 4 saiu no fim de 2025 (baseado no Spring Framework 7) e o 3.x continua suportado durante um período. Escolher a combinação pela qual há mais documentação e suporte nas bibliotecas acima.

## 1.8 Frontend React

- **Vite**: ferramenta de build atual para React (o Create React App foi descontinuado). Alternativa: um framework como Next.js, que traz renderização no servidor (normalmente desnecessária para uma SPA atrás de login).
- **PWA**: *manifest* + **service worker** para instalação e cache offline (ex.: `vite-plugin-pwa`, que usa Workbox). O F11 (offline) é Could, mas o PWA influencia o alojamento.
- **Configuração:** variáveis do Vite (`import.meta.env`) ficam fixas **no momento do build**. Para usar a mesma build em vários ambientes, carregar um `config.json` em runtime.
- **Autenticação no browser:** fluxo OAuth2 **Authorization Code com PKCE**; bibliotecas `oidc-client-ts` ou **MSAL** (se Entra).
- **Mapa do recinto** (imagem, não mapa geográfico): Leaflet com `CRS.Simple` e *image overlay*, ou SVG próprio.

## 1.9 CI/CD

- **Continuous Integration (CI):** cada push/PR compila, testa e analisa o código automaticamente; o merge só é feito com tudo verde.
- **Continuous Delivery:** cada alteração no `main` gera um artefacto **pronto** a publicar; o deploy em produção é um botão.
- **Continuous Deployment:** cada alteração no `main` vai **automaticamente** para produção.
- **GitHub Actions:** *workflow* (ficheiro YAML em `.github/workflows/`) → *jobs* (correm em *runners*, em paralelo ou com `needs:`) → *steps*. *Triggers*: `pull_request`, `push`, `workflow_dispatch` (manual), `release`. **Environments** com regras de aprovação e segredos próprios. **Path filters** para correr só o pipeline do serviço alterado. **Matrix** para correr variações.
- **Autenticação GitHub → Azure:** **OIDC / federated credentials** (sem segredo guardado; recomendado) ou *service principal* com segredo.
- **Branch protection / rulesets:** exigir *status checks* verdes e review antes do merge (o Tomás já ativou branch protection).
- **Análise estática:** **SonarQube Cloud** (gratuito para repositórios públicos), **CodeQL** (GitHub, gratuito em repos públicos), Checkstyle/SpotBugs/PMD, ESLint.
- **Dependências e segurança:** **Dependabot** (alertas e PRs de atualização), OWASP Dependency-Check, **Trivy** (vulnerabilidades em imagens Docker), *secret scanning*.
- **Tag de imagem:** pelo SHA do commit (rastreável e imutável), por versão semântica (`v1.2.0`, nas releases) e evitar `latest` em produção.
- **Estratégias de deploy e rollback:** *rolling update*; *blue-green* (duas versões, troca-se o tráfego); *canary* (uma percentagem do tráfego vai para a nova versão). No ACA, cada revisão pode receber uma percentagem do tráfego; o rollback é reativar a revisão anterior. **Atenção:** o rollback do código não desfaz migrações da base de dados; as migrações devem ser compatíveis com a versão anterior (*expand and contract*).
- **Monorepo vs polyrepo:** um repositório para tudo (mais simples para equipas pequenas; pipelines com path filters) ou um por serviço (independência total, mais coordenação).

## 1.10 Observabilidade

- **Três pilares:** **logs** (eventos, em JSON estruturado), **métricas** (números ao longo do tempo: pedidos/s, latência, erros), **traces** (o percurso de um pedido por vários serviços).
- **Correlation / trace ID:** um identificador que acompanha o pedido por todos os serviços **e mensagens**, para juntar os logs. O padrão atual é o cabeçalho **W3C `traceparent`**, propagado automaticamente pelo OpenTelemetry e pelo Micrometer Tracing.
- **OpenTelemetry (OTel):** padrão aberto para recolher logs, métricas e traces. O **agente Java do Application Insights** e a distro Azure Monitor OpenTelemetry instrumentam o Spring Boot quase sem código.
- **SLI / SLO:** *Service Level Indicator* é o que se mede (ex.: % de pedidos de ajuda aceites em < 2 s); *Service Level Objective* é a meta (ex.: 99%). O enunciado (§6.7) pede SLIs relevantes para o produto. O critério de sucesso "triagem em ≤ 30 s" ([`product-vision.md`](../Product/product-vision.md) §5) é um bom candidato.
- **Dashboard:** Azure Workbooks / Azure Dashboards, ou Grafana.

## 1.11 Identidade e segurança

- **OAuth2:** delegação de autorização (quem pode aceder a quê). **OpenID Connect (OIDC):** autenticação por cima do OAuth2 (quem é o utilizador). O IdP emite um **JWT** (token assinado com *claims*: `sub`, `roles`, `exp`); os serviços validam a assinatura com a chave pública do IdP (**JWKS**) sem lhe fazer chamadas.
- **Resource server:** cada serviço valida o JWT de forma independente. Validar só no gateway deixa os serviços expostos se alguém chegar até eles por outra via.
- **Autorização por evento** (invariante I1): o papel (`Owner`, `Admin`, `Operator`) depende do evento, por isso não cabe bem num `role` global do token. É preciso verificá-lo em cada pedido, contra a `Membership`.
- **Serviço a serviço:** rede interna (ingress interno no ACA), *client credentials* do OAuth2, managed identities, mTLS.
- **Segredos:** nunca no repositório; em Key Vault ou nos segredos do ACA/GitHub, injetados como variáveis de ambiente.

---

# Parte 2 · Perguntas a responder

Formato: **pergunta** → porque importa → opções → ler antes → **Decisão**.
Ordem sugerida: **A → B → G → H** primeiro (bloqueiam o walking skeleton), depois as restantes.

## A · Estilo arquitetural (ADR-001)

**A1. Microserviços ou monólito modular?**
- **Porque importa:** é a decisão mais difícil de mudar e a que o enunciado manda justificar explicitamente (§6.2). Condiciona pipelines, custo no Azure, observabilidade e testes.
- **Opções:**

| Opção | Prós | Contras |
|---|---|---|
| **Microserviços** (ex.: Event, Incident, Triage worker) | Cumpre o §6.2 sem precisar de "evidência convincente"; isola falhas (pedido de ajuda continua a funcionar num pico de leituras); escala por serviço (D2); conta uma boa história na avaliação | Mais pipelines, imagens, configurações e custo; consistência eventual entre serviços; depuração distribuída; 3 sprints com equipa pequena (D9) |
| **Monólito modular** (Spring Modulith) | Um deploy, menos custo e menos peças; refatorar fronteiras é barato; transações locais | Tem de **provar** que serviços seriam complexidade desnecessária; fronteiras verificadas por testes; uma falha pode afetar tudo |
| **Híbrido:** monólito modular + worker de triagem separado | Dois deployáveis independentes com pouco custo extra; a parte assíncrona e frágil (IA) fica isolada | Ainda tem de justificar porque Event e Incident ficam juntos |

- **Ler antes:** §1.2, §1.3, §1.6 (custos).
- **Para pensar:** com que dados reais justificas cada opção (carga esperada, tamanho da equipa, crédito disponível)? Que critérios usas e que peso tem cada um? Uma matriz de decisão com pesos é uma boa evidência para o ADR. O que farias se o professor perguntasse "porque não a outra?"
- **Decisão:** **Microserviços** (estado final; ver A4). Cumprem o §6.2 sem ter de provar que os serviços seriam complexidade desnecessária, isolam falhas (um pico de leituras não derruba os pedidos de ajuda) e dão contratos pequenos e fechados por serviço, que é o que o Spec-Driven Development precisa. *(Bruno, 15/10, a validar pelo Tiago)*

**A2. Quais são as fronteiras (módulos ou serviços) e quantas?**
- **Porque importa:** o [`domain-model.md`](domain-model.md) tem dois contextos, mas desde então surgiram funcionalidades sem casa: contas de utilizador (F1, F2), relatos comunitários e badges (F12–F15), check-in (F16), amigos (F18), administrador da plataforma (P4, proposto).
- **Opções:**

| Opção | Módulos / serviços |
|---|---|
| 2 + worker | Event · Incident · Triage worker (o atual `architecture.md`) |
| 3 + worker | + **Identity/Account** (contas, perfil, consentimentos, eliminação de dados) |
| 4 + worker | + **Engagement** (relatos, confirmações, badges, check-in, amigos) |
| Outra divisão | Ex.: separar Transport (integração externa) num serviço próprio |

- **Ler antes:** §1.2 (bounded context, coesão, Lei de Conway).
- **Para pensar:** para cada funcionalidade, pergunta "quem a altera, porquê e com que frequência?" e "que dados lê e escreve?". Os relatos comunitários são do evento ou do incidente? As badges dependem de confirmações feitas por operadores (Incident?) e de check-ins (Event?). Que serviço é dono do `UserAccount`?
- **Decisão:** **5 serviços.** **Auth & Users**: authN, authZ, contas, papéis por evento, amigos (F1, F2, papéis do F4, F18) · **Events**: eventos, mapa e pontos de interesse, equipas de resposta, anúncios, check-in, entradas (F3, equipas do F4, F5, F10, F16, F17) · **Reports**: relatos comunitários e a sua confirmação, pedidos de ajuda, backoffice de incidentes e triagem IA com fallback (F6, F7, F8, F12, F13) · **Transports**: integração STCP GTFS e shuttles (F9) · **Badges**: badges (F14, F15). *(Bruno, 15/10, a validar pelo Tiago)*

**A3. Como se impõem as fronteiras?**
- **Opções:** pacotes + **Spring Modulith** `verify()` · **ArchUnit** · **módulos Maven** separados (o compilador impede dependências) · repositórios separados (só microserviços).
- **Para pensar:** o §6.2 pede fronteiras "explícitas e verificáveis". Que teste falha se alguém violar uma fronteira? Este teste corre no CI?
- **Decisão:** Cada serviço é um **projeto Maven próprio** em `backend/<serviço>/`, no mesmo repositório; nenhum serviço depende do código de outro, só dos contratos (OpenAPI e eventos). Na fase de monólito (A4) são módulos Maven do mesmo build, e um teste **ArchUnit** no módulo de arranque falha se um serviço importar classes de outro. *(Bruno, 15/10, a validar pelo Tiago)*

**A4. Como evolui a arquitetura entre os sprints A, B e C?**
- **Porque importa:** o Sprint C pede uma *architecture evolution narrative* (§7.3.2, item 11).
- **Opções:** começar com o estilo final · começar com um monólito modular e extrair serviços quando houver razão medida (ex.: teste de carga) · começar só com o walking skeleton (1 serviço + frontend) e adicionar.
- **Decisão:** **Sprints A e B: monólito modular** (um deployável que junta os 5 módulos). **Sprint C: cada módulo passa a microserviço.** A1–A3 descrevem o estado final. Como os módulos só comunicam por eventos (C4) e têm dados separados (D2), a extração é mecânica (novo `main`, Dockerfile e base de dados). Dá a narrativa de evolução do §7.3.2 (item 11). *(Bruno, 15/10, a validar pelo Tiago)*

## B · C4 model e documentação

**B1. Que ferramenta usamos para os diagramas?**
- **Opções:** Structurizr DSL · C4-PlantUML · Mermaid C4 · draw.io (ver §1.1).
- **Para pensar:** os diagramas têm de ser vistos no GitHub sem passos extra? Queremos um modelo único que gere todas as vistas? Quem atualiza os diagramas quando o código muda?
- **Decisão:** **Mermaid C4** agora (o GitHub mostra-o no Markdown sem passos extra); na entrega final passamos para **C4-PlantUML no Visual Paradigm**. *(Bruno, 15/10, a validar pelo Tiago)*

**B2. O que entra no nível 1 (System Context)?**
- **Para pensar:** que pessoas aparecem (P1–P4)? Que sistemas externos (STCP GTFS ou API meteorológica, fornecedor de IA, IdP, 112)? O IdP é externo (Entra) ou nosso (Keycloak, que seria então um container)? O 112 aparece, mesmo que o sistema não comunique com ele?
- **Decisão:** O Farol (caixa única), as pessoas P1–P4 e os **sistemas externos que usamos**: STCP GTFS, fornecedor de IA e o IdP se for externo (depende de E1). O 112 não entra: o sistema não comunica com ele. *(Bruno, 15/10, a validar pelo Tiago)*

**B3. O que entra no nível 2 (Container)?**
- **Para pensar:** a SPA do participante e o backoffice são um ou dois containers? O broker, a base de dados e o Blob Storage aparecem como containers (são nossos, ainda que geridos pelo Azure) ou como sistemas externos? O gateway aparece? Os serviços de observabilidade aparecem ou ficam numa nota?
- **Decisão:** **Frontend e backend separados**; cada serviço (A2), o RabbitMQ e a base de dados de cada serviço; os sistemas externos mantêm-se. O número de frontends (F1) e o gateway (C6) dependem das decisões do Tiago. *(Bruno, 15/10, a validar pelo Tiago)*

**B4. Para que containers fazemos o nível 3 (Component)?**
- **Opções:** todos · só os de negócio · só o mais rico (provavelmente Incident, que tem o fluxo assíncrono).
- **Para pensar:** um diagrama de componentes por container custa tempo e desatualiza-se. O Spring Modulith gera diagramas de componentes a partir do código; isso muda a resposta?
- **Decisão:** **Todos os serviços.** A documentação de cada serviço (nível 3, nível 4, contratos) fica junto ao código, em `backend/<serviço>/docs/`. *(Bruno, 15/10, a validar pelo Tiago)*

**B5. Como fazemos o nível 4 (Code)?**
- **Opções:** diagrama de classes do domínio de um componente (pode reaproveitar o [`domain-model.md`](domain-model.md)) · diagrama gerado pelo IDE (IntelliJ) · diagrama ER da base de dados de um serviço.
- **Para pensar:** o C4 recomenda fazê-lo só para componentes importantes ou complexos e gerá-lo automaticamente. Qual é o componente que mais vale a pena explicar ao detalhe (a máquina de estados do `HelpRequest`? o motor de badges?)
- **Decisão:** Mostra a **arquitetura hexagonal** dentro de cada serviço: `domain` (entidades e regras), `application` (casos de uso e portas de entrada e saída), `adapters/in` (REST gerado do OpenAPI, consumidores RabbitMQ), `adapters/out` (JPA, publicador do outbox, clientes HTTP externos). *(Bruno, 15/10, a validar pelo Tiago)*

**B6. Que diagramas suplementares fazemos?**
- **Opções:** deployment (Azure) · dynamic (triagem assíncrona; pedido de ajuda) · ambos.
- **Para pensar:** o §7.1.2 pede *deployment view* e *asynchronous workflow*; os diagramas dinâmicos e de deployment do C4 cobrem os dois.
- **Decisão:** **Vistas do 4+1, sem os cenários:** lógica (C4 níveis 1–3 e modelo de domínio), processos (diagramas dinâmicos: pedido de ajuda e triagem assíncrona), desenvolvimento (repositório, módulos Maven, pacotes hexagonais), física (deployment no Azure, depende de G1). Os cenários ficam de fora porque os workflows W1–W8 já os cobrem. *(Bruno, 15/10, a validar pelo Tiago)*

**B7. Onde ficam os ADRs e em que formato?**
- **Opções:** `Docs/Technical/adr/NNNN-titulo.md` com o formato de Michael Nygard (Contexto, Decisão, Estado, Consequências) · formato MADR (inclui alternativas consideradas, que o §7.1.2 exige).
- **Decisão:** `Docs/Technical/adr/NNNN-titulo.md`, formato **Michael Nygard** (Contexto, Decisão, Estado, Consequências), com uma secção **Alternativas consideradas**, que o §7.1.2 exige. *(Bruno, 15/10, a validar pelo Tiago)*

## C · Comunicação

**C1. Que broker de mensagens?** (ADR-004)

| Opção | Prós | Contras |
|---|---|---|
| **Azure Service Bus** | Gerido; filas, tópicos (Standard), DLQ e retries nativos, deteção de duplicados; emulador local | Standard tem custo mensal fixo; dependência do Azure |
| **RabbitMQ** num contentor | Gratuito, popular, igual local e na cloud | Tens de o operar (persistência, atualizações); num ACA com escala até zero perdes mensagens se não tiver volume |
| **Azure Storage Queues** | Muito barato e simples | Sem tópicos, sem DLQ nativa (só contagem de tentativas), mensagens até 64 KB |
| **Event Hubs / Kafka** | Streaming de alto volume, replay | Excessivo para este volume |

- **Para pensar:** precisamos de tópicos (vários consumidores do mesmo evento) ou chegam filas? Como corremos o broker localmente e nos testes (Testcontainers)?
- **Decisão:** **RabbitMQ.** Gratuito; igual localmente, nos testes (Testcontainers) e na cloud; exchanges *topic* dão pub-sub e a DLQ faz-se com *dead-letter exchange*. Não tem o custo fixo mensal do Service Bus Standard. Onde corre no Azure: Tiago (G1). *(Bruno, 15/10, a validar pelo Tiago)*

**C2. Como fala o código com o broker?**
- **Opções:** Spring Cloud Stream (troca de broker só com configuração) · Spring Cloud Azure Service Bus · Spring AMQP · Spring Modulith *externalized events* (no monólito modular).
- **Decisão:** **Spring AMQP**: a integração nativa do Spring com o RabbitMQ. O Spring Cloud Stream só compensaria se quiséssemos trocar de broker. *(Bruno, 15/10, a validar pelo Tiago)*

**C3. Como garantimos o outbox, a idempotência e a DLQ?**
- **Opções para o outbox:** tabela outbox + publicador por polling (feito por nós) · Event Publication Registry do Spring Modulith · CDC com Debezium (excessivo).
- **Para pensar:** onde fica guardado o "já processei esta mensagem"? Quantas tentativas antes da DLQ? Quem olha para a DLQ e como?
- **Decisão:** **Tabela outbox** em cada serviço, publicada por um job de polling feito por nós. Consumidores idempotentes com uma tabela de mensagens processadas (`message_id`); depois de N tentativas a mensagem vai para a DLQ. N e backoff: J1 (Tiago). *(Bruno, 15/10, a validar pelo Tiago)*

**C4. Os serviços chamam-se diretamente (síncrono) ou só por eventos?**
- **Exemplo concreto:** o Incident precisa das equipas de resposta e dos pontos de interesse do Event.
- **Opções:** chamada REST quando precisa (acoplamento temporal) · cópia local alimentada por eventos (*event-carried state transfer*; consistência eventual) · o cliente envia os IDs e ninguém valida (inseguro).
- **Para pensar:** o que acontece a um pedido de ajuda se o Event estiver em baixo, em cada opção?
- **Decisão:** **Só eventos.** Cada serviço guarda uma cópia local do que precisa de outro (ex.: o Reports guarda as equipas de resposta do Events; todos guardam os papéis por evento do Auth & Users). Se um serviço cair, os outros continuam; o custo é a consistência eventual. *(Bruno, 15/10, a validar pelo Tiago)*

**C5. Como é feito o push em tempo real** (lista do backoffice, estado do pedido, anúncios)?
- **Opções:** WebSocket/STOMP no Spring · Server-Sent Events · Azure Web PubSub/SignalR · polling a cada N segundos.
- **Para pensar:** funciona com 2+ réplicas do serviço? Passa pelo gateway e pelo ingress do ACA? O polling é suficiente para o MVP?
- **Decisão:** **Sem réplicas** por agora (uma instância por serviço). A tecnologia de push: **Tiago** decide. *(Bruno, 15/10, a validar pelo Tiago)*

**C6. Há um API gateway? Qual?**
- **Opções:** Spring Cloud Gateway num contentor · Azure API Management (Consumption) · só o ingress do ACA com rotas por app · nenhum (o frontend chama cada serviço diretamente).
- **Para pensar:** onde fica o rate limiting dos pedidos de ajuda (abuso)? Onde fica o CORS? É mais um container a manter?
- **Decisão:** **Tiago** decide. *(Bruno sem posição, 15/10)*

**C7. Como documentamos e versionamos a API?** (entregável *API outline*, Sprint A; *API contracts*, Sprint B)
- **Opções:** code-first com springdoc-openapi · contract-first (OpenAPI escrito à mão + geração de código) · versionamento no caminho (`/api/v1`) ou nenhum no MVP · testes de contrato sim/não.
- **Decisão:** **Contract-first:** OpenAPI escrito à mão em `backend/<serviço>/contracts/openapi.yaml`; o `openapi-generator` gera as interfaces e DTOs Spring e o cliente do frontend. Caminhos com `/api/v1`. É a base do Spec-Driven Development ([`spec-driven-development.md`](../Process/spec-driven-development.md)). *(Bruno, 15/10, a validar pelo Tiago)*

## D · Dados (ADR-003)

**D1. Que base de dados?**
- **Opções:** **PostgreSQL** (Flexible Server) · Azure SQL · Cosmos DB.
- **Para pensar:** o §6.4 pede **uma só tecnologia** salvo justificação. Precisamos de consultas geográficas (PostGIS) ou o mapa é uma imagem com coordenadas em píxeis (ADR-007)? Precisamos de Row-Level Security?
- **Decisão:** **PostgreSQL.** Uma só tecnologia (§6.4); o mapa é uma imagem, por isso não é preciso PostGIS; tem Row-Level Security se um dia for preciso. *(Bruno, 15/10, a validar pelo Tiago)*

**D2. Como isolamos os dados por serviço/módulo?**
- **Opções:** servidor por serviço (caro) · base de dados por serviço no mesmo servidor · schema por serviço com utilizador próprio · tabelas com prefixo (só monólito).
- **Para pensar:** quanto custa um servidor PostgreSQL por mês no escalão mais baixo e quantos cabem no crédito?
- **Decisão:** **Uma base de dados por serviço**, a que só esse serviço acede. Na fase de monólito (A4), um schema por módulo, que passa a base de dados no Sprint C. Quantos servidores: Tiago (G2). *(Bruno, 15/10, a validar pelo Tiago)*

**D3. Como isolamos os dados por evento (tenant, driver D1)?**
- **Opções:** `event_id` + filtro na aplicação · `event_id` + Row-Level Security · schema por evento.
- **Para pensar:** que teste automático prova que um membro do evento X não vê dados do evento Y (exigido pelo relatório §5)?
- **Decisão:** **`event_id` + filtro na aplicação** em todas as tabelas com dados de um evento. Cada serviço tem um teste de integração que prova que um membro do evento X não lê nem altera dados do evento Y. *(Bruno, 15/10, a validar pelo Tiago)*

**D4. Como aguentamos o pico de leituras (D2)?**
- **Opções:** cache HTTP + CDN (Front Door ou o CDN do Static Web Apps para conteúdo estático) · Caffeine em memória · Redis · réplicas de leitura · só escala horizontal.
- **Para pensar:** que dados são iguais para todos os participantes (cacheáveis) e quais são pessoais? Como mostras a "última atualização" (D4) com cache?
- **Decisão:** **Tiago** decide.

**D5. Retenção, backup e eliminação** (§6.4; privacidade)
- **Para pensar:** durante quanto tempo guardamos a localização de um pedido de ajuda? Quem apaga e como (job agendado)? O backup automático do Flexible Server chega? Como funciona o "eliminar conta" (F2) com backups?
- **Decisão:** **Tiago** decide.

**D6. Migrações:** Flyway ou Liquibase? Correm no arranque da app ou num passo do pipeline?
- **Decisão:** **Flyway**, a correr no arranque da aplicação. Regra: migrações compatíveis com a versão anterior (*expand and contract*), porque o rollback do código não as desfaz. *(Bruno, 15/10, a validar pelo Tiago)*

## E · Identidade e segurança (ADR-006)

**E1. Que IdP?**

| Opção | Prós | Contras |
|---|---|---|
| **Mock** (emissor JWT próprio, permitido no MVP) | Rápido; zero custo | Não demonstra segurança real; tem de ser substituível |
| **Keycloak** num contentor | Open source, completo, igual local e na cloud | Mais um container a operar (com base de dados) |
| **Microsoft Entra External ID** | Gerido, nativo no Azure, escalão gratuito | Configuração mais burocrática; dependência do Azure; testar localmente é mais difícil |

- **Decisão:** **Tiago** decide. Nota: o A2 põe a authN e a authZ no serviço Auth & Users; o IdP tem de encaixar nisso.

**E2. Onde se valida o token e onde se verifica o papel por evento?**
- **Para pensar:** gateway, cada serviço, ou ambos? O papel por evento vai no token (fica desatualizado quando o papel muda) ou é consultado em cada pedido (mais lento; cache)? O `PlatformAdmin` é um papel global no token?
- **Decisão:** **Tiago** decide.

**E3. Como se autenticam os serviços entre si?**
- **Opções:** só rede interna (ingress interno do ACA) · client credentials OAuth2 · managed identity · mTLS.
- **Decisão:** **Tiago** decide.

**E4. Onde ficam os segredos** (password da base de dados, chave da API de IA, connection string do broker)?
- **Opções:** Key Vault + managed identity · segredos do ACA · GitHub Secrets injetados no deploy · managed identity em todo o lado (sem passwords, onde o serviço o suporta).
- **Decisão:** **Tiago** decide.

## F · Frontend

**F1. Uma ou duas aplicações React** (participante e backoffice)?
- **Opções:** uma app com rotas e permissões · duas apps no mesmo repositório (monorepo com workspaces) · duas apps separadas.
- **Para pensar:** o participante é mobile-first e PWA; o backoffice é desktop. Partilham componentes (mapa)? No C4, são um ou dois containers?
- **Decisão:** **Tiago** decide.

**F2. Onde fica alojado o frontend e como chega a configuração?**
- **Opções:** Static Web Apps · Blob static website + CDN · contentor nginx no ACA. Configuração no build ou em runtime (§1.8).
- **Decisão:** **Tiago** decide.

**F3. Biblioteca do mapa do recinto** (ADR-007): Leaflet `CRS.Simple` · SVG próprio · outra.
- **Decisão:** **Tiago** decide.

## G · Azure e deploy (responsável: Tiago)

**G1. Onde correm os contentores do backend?**

| Opção | Kubernetes necessário | Custo em repouso | Escala | Notas |
|---|---|---|---|---|
| **Azure Container Apps** | Não (está por baixo, escondido) | Pode ser ~0 com escala até zero | Automática, por HTTP ou tamanho de fila (KEDA) | Revisões e divisão de tráfego para rollback |
| **AKS** | **Sim** | Nós sempre ligados (o mais caro) | Total controlo | Faz sentido só se aprender Kubernetes for um objetivo |
| **App Service** (contentores) | Não | Plano sempre a pagar | Por plano | Simples; menos natural para vários serviços e workers |
| **VM + Docker Compose** | Não | VM sempre a pagar (pode ser desligada) | Manual | Muito simples; não é "cloud-native"; rollback manual |
| **Container Instances** | Não | Por segundo de execução | Manual | Sem ingress/escala integrados |

- **Para pensar:** o §8.4 encoraja orquestração "quando justificada". Com Kubernetes fraco, o ACA dá orquestração sem esse custo de aprendizagem; o AKS pode ser uma justificação de evolução futura no roadmap.
- **Decisão:** **Tiago** decide.

**G2. Como cabe tudo em US$100 por ano?**
- **Para pensar:** faz uma estimativa por serviço (base de dados, broker, registry, compute, logs). O que está ligado 24/7 e o que pode ser desligado fora das demos? Configuramos **alertas de orçamento** (Cost Management → Budgets)? O que acontece se o crédito acabar antes da avaliação final (janeiro 2027)? Quem é dono da subscrição e como entram os outros (RBAC)?
- **Decisão:** **Tiago** decide.

**G3. Que região?**
- **Para pensar:** dados pessoais e potencialmente de saúde → região na UE (RGPD). Que regiões permite a política da subscrição de estudante? Todos os serviços escolhidos existem nessa região?
- **Decisão:** **Tiago** decide.

**G4. Quantos ambientes?**
- **Opções:** só produção · `dev` + `prod` · ambientes de preview por PR (o Static Web Apps faz isto no frontend).
- **Para pensar:** cada ambiente multiplica o custo. Onde testamos antes de mostrar ao professor?
- **Decisão:** **Tiago** decide.

**G5. Infraestrutura por código ou à mão?**
- **Opções:** Bicep · Terraform · `azd` · portal com instruções escritas.
- **Para pensar:** o §8.4 pede builds reproduzíveis e instruções de deploy; o §12 pede que os professores consigam fazer deploy. Conseguimos recriar tudo do zero com um comando?
- **Decisão:** **Tiago** decide.

**G6. Como corre tudo localmente?**
- **Para pensar:** um `docker compose up` arranca toda a solução (PostgreSQL, broker ou emulador do Service Bus, Azurite, serviços, frontend)? É o mesmo que os professores usam se o Azure não estiver disponível?
- **Decisão:** **Tiago** decide.

**G7. Como fazemos rollback e recuperação?** (§8.4)
- **Opções:** revisões do ACA (reativar a anterior) · redeploy da imagem com o SHA anterior · restauro *point-in-time* da base de dados.
- **Para pensar:** e se a nova versão incluir uma migração da base de dados?
- **Decisão:** **Tiago** decide.

## H · Pipeline de CI/CD (responsável a definir: DevOps lead)

**H1. Que ferramenta?** GitHub Actions (o código já está no GitHub) · Azure DevOps Pipelines.
- **Decisão:** **GitHub Actions** (o código já está no GitHub). *(Bruno, 15/10, a validar pelo Tiago)*

**H2. Como organizamos o repositório?**
- **Opções:** monorepo atual (`backend/<serviço>`, `frontend/`, `infra/`, `Docs/`) com path filters · um repositório por serviço.
- **Para pensar:** com microserviços, cada serviço tem o seu workflow e é publicado independentemente (isso é parte da prova do §6.2).
- **Decisão:** **Monorepo atual:** `backend/<serviço>/`, `frontend/`, `infra/`, `Docs/`. *(Bruno, 15/10, a validar pelo Tiago)*

**H3. Build:** Maven ou Gradle? Que versões de Java e Spring Boot (§1.7)? Imagem por Dockerfile multi-stage, Buildpacks ou Jib?
- **Decisão:** **Maven**, **Java 21 (LTS)**, **Spring Boot 4.x** (última versão estável quando criarmos o projeto); imagens com **Dockerfile multi-stage** (JDK para compilar, só JRE na imagem final). *(Bruno, 15/10, a validar pelo Tiago)*

**H4. O que corre em cada momento?** Preencher:

| Etapa | Em cada PR | No merge para `main` | Numa release/tag |
|---|---|---|---|
| Compilar e testes unitários | | | |
| Testes de integração (Testcontainers) | | | |
| Teste de fronteiras (Modulith/ArchUnit) | | | |
| Testes de isolamento entre eventos | | | |
| Testes de contrato | | | |
| Testes E2E (ex.: Playwright) | | | |
| Lint e análise estática (Sonar, CodeQL, ESLint) | | | |
| Dependências e imagem (Dependabot, Trivy) | | | |
| Construir e publicar imagem | | | |
| Deploy em `dev` | | | |
| Deploy em `prod` | | | |
| Smoke test pós-deploy (`/actuator/health`) | | | |

- **Para pensar:** o §8.5 pede feedback "rápido e visível". Quanto tempo pode demorar o pipeline de um PR? O que é *required check* para o merge?
- **Decisão:** **Corre tudo, sempre:** todas as verificações da tabela correm em cada PR, no merge e nas tags, para todos os serviços (sem path filters). A imagem é publicada e o deploy em produção é feito no merge para `main` (H5), seguido do smoke test. *(Bruno, 15/10, a validar pelo Tiago)*

**H5. Deploy contínuo ou com aprovação?**
- **Opções:** cada merge vai para produção (continuous deployment) · cada merge vai para `dev`, produção com aprovação manual num *environment* (continuous delivery) · produção só em releases com tag.
- **Decisão:** **Continuous deployment por agora:** cada merge em `main` vai para produção. *(Bruno, 15/10, a validar pelo Tiago)*

**H6. Como se autentica o pipeline no Azure?** OIDC (federated credentials) ou service principal com segredo.
- **Decisão:** **Tiago** decide.

**H7. Como versionamos e etiquetamos?** SHA do commit nas imagens; versão semântica nas releases (o §12 pede uma *release version*); changelog gerado ou manual.
- **Decisão:** **Tiago** decide.

**H8. O repositório é público?** Muda o que é gratuito (CodeQL, SonarQube Cloud, GHCR, minutos de Actions).
- **Decisão:** **Não**, o repositório fica privado. *(Bruno, 15/10, a validar pelo Tiago)*

## I · Observabilidade (§6.7)

**I1. Que stack?**
- **Opções:** Application Insights + Log Analytics (agente Java OTel) · Prometheus + Grafana + Loki em contentores · Azure Managed Grafana sobre Azure Monitor.
- **Para pensar:** custo de ingestão de logs; o trace atravessa as mensagens do broker (correlation ID no §6.3 do `architecture.md`)?
- **Decisão:** **Tiago** decide.

**I2. Que SLIs e SLOs?**
- **Candidatos:** tempo até à sugestão de triagem (≤ 30 s), taxa de pedidos de ajuda aceites, latência p95 das leituras do participante, idade dos dados de transporte, mensagens na DLQ.
- **Decisão:** **Tiago** decide.

**I3. Que dashboard e que alertas?** Azure Workbook / Dashboard ou Grafana; alertas por email quando um SLO falha?
- **Decisão:** **Tiago** decide.

## J · Resiliência e degradação (§6.8)

**J1. Que padrões em cada chamada externa** (IA, transportes)? Timeout, retry com backoff, circuit breaker (Resilience4j); valores concretos.
- **Decisão:** **Tiago** decide.

**J2. Como demonstramos os modos degradados D1 e D2 na apresentação?**
- **Opções:** *feature flag* / variável que desliga a integração · parar o contentor ou réplica · simulador que devolve erros ou atrasos (*fault injection*).
- **Decisão:** **Tiago** decide.

**J3. Como escalamos para o pico?** Mínimo e máximo de réplicas por serviço; escala até zero aceitável em que serviços (cold start)? Como provamos com um teste de carga (k6, JMeter, Azure Load Testing) para o *performance evidence* do Sprint C?
- **Decisão:** **Tiago** decide.

## K · IA (ADR-005)

**K1. Que fornecedor e modelo?** Azure OpenAI/Foundry (confirmar acesso na subscrição de estudante) · API OpenAI ou outra · modelo local (Ollama) num contentor.
- **Para pensar:** que dados do pedido de ajuda saem para o fornecedor (privacidade, região)? Quanto custa por pedido? Spring AI permite mudar de fornecedor por configuração?
- **Decisão:** **Tiago** decide.

---

# Parte 3 · Perguntas que te podem fazer na avaliação

O §10 diz que **cada aluno** tem de saber explicar a arquitetura, as decisões técnicas e o modelo de deploy. Treina responder a estas sem consultar o documento:

1. Porque escolheram microserviços (ou monólito modular) e não a outra opção? Que evidência têm?
2. Desenha o diagrama de containers de memória e explica a responsabilidade de cada caixa.
3. Que dados pertencem a cada serviço? O que acontece se um serviço precisar de dados de outro?
4. Percorre o fluxo de um pedido de ajuda do clique do participante até ao ecrã do operador. O que é síncrono e o que é assíncrono? Porquê?
5. O que acontece se a mensagem de triagem for entregue duas vezes? E se o broker estiver em baixo quando o pedido é gravado?
6. O que acontece se o fornecedor de IA ficar indisponível? E a fonte de transportes?
7. Como garantem que a equipa de um festival não vê os dados de outro? Como o testam?
8. Como chega o código de um PR até produção? Que verificações o podem travar?
9. Como fazem rollback de um deploy com erro? E se a versão incluir uma migração de base de dados?
10. Onde estão os segredos e quem lhes acede?
11. Como sabem que o sistema está saudável em produção? Que indicadores mediram?
12. Quanto custa a solução por mês e o que fariam para a escalar para 100 000 participantes?
13. Porque não usaram Kubernetes diretamente (ou porque usaram)?
14. Qual foi a decisão de arquitetura de que mais se arrependem e como a mudariam?

---

## Ligação às decisões e ADRs

| Secção deste guia | ADR (ver [`architecture.md`](architecture.md) §13) |
|---|---|
| A | ADR-001 Estilo arquitetural |
| §0 + H3 | ADR-002 Tecnologia de backend e frontend (Spring Boot + React, decidido) |
| D | ADR-003 Base de dados e posse dos dados |
| C1–C3 | ADR-004 Broker e garantias de entrega |
| K | ADR-005 Workflow de IA |
| E | ADR-006 Identidade |
| F3 | ADR-007 Representação do mapa |
| — | ADR-008 Fonte de transportes |
| B, G, H, I | **Novos ADRs a criar:** ferramenta C4, plataforma de deploy no Azure, estratégia de CI/CD, stack de observabilidade |
