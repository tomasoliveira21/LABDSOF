# Análise de Mercado e Concorrência (geral)

> LABDSOF 2026/27 · Sprint A
> **O que é este documento:** a visão **geral** do mercado de gestão de eventos académicos em Portugal, fase a fase, com tudo o que existe hoje. A análise **específica** das funcionalidades que queremos incluir no projeto está no anexo `02a-Anexo-Mercado-Especifico-do-Produto.md`.
> **Alinhado com o repositório** (`Docs/Deliverables/problem-opportunity-report-queima.md`, commit `442ecc3`, 2026-10-03): plataforma **multi-evento** de coordenação para eventos académicos de grande escala, com aplicação web para o participante e backoffice para a equipa de cada evento. Caso-piloto: Queima das Fitas do Porto.
> **Mercado-alvo:** Portugal. As referências internacionais servem só de comparação.
>
> **Legenda:** sem marca = facto com fonte · "Não identificada" = não encontrámos nas fontes (não quer dizer que não exista). As funcionalidades dos concorrentes são as que os próprios declaram, não foram testadas por nós.

---

## 1. Contexto

| Indicador | Valor | Fonte |
|---|---|---|
| Estudantes no ensino superior (2025/26) | 447 680 | [ECO](https://eco.sapo.pt/2026/04/24/numero-de-inscritos-no-ensino-superior-e-o-mais-alto-da-ultima-decada/) |
| Orçamentos das grandes festas (2023) | Porto €2,5 M · Coimbra €1,5 M · Braga €0,9 M · Aveiro €0,6 M | [Notícias Magazine](https://www.noticiasmagazine.pt/2023/os-milhoes-que-alimentam-as-associacoes-academicas/historias/288877/) |
| Queima do Porto 2026 | Cerca de 250 000 pessoas em 8 noites; 35 % do orçamento em segurança; mais de 500 operacionais por dia | [Observador](https://observador.pt/2026/04/30/queima-das-fitas-do-porto-arranca-com-maior-investimento-em-seguranca-de-sempre-diz-fap/) |
| Queima de Coimbra 2026 | €2,2 M; mais de 210 000 visitantes | [As Beiras](https://www.asbeiras.pt/orcamento-da-queima-das-fitas-supera-os-dois-milhoes-de-euros/) |
| Filas na Queima do Porto (2022 e 2023) | Filas de mais de 500 m, esperas de mais de 3 h, relatos de esmagamento; queixas de "falta de informação e sinalética" | [Renascença](https://rr.pt/noticia/pais/2022/05/05/filas-para-a-queima-das-fitas-do-porto-achei-que-ia-colapsar-a-ser-esmagada-e-quase-a-sufocar/283010/), [Público](https://www.publico.pt/2023/05/08/p3/noticia/primeira-noite-caotica-queima-fitas-porto-voltou-normal-2048919) |

Hoje, cada fase do evento é tratada por uma ferramenta diferente, e a informação que muda durante a noite não chega ao participante (Problem and Opportunity Report, secção 2).

---

## 2. Alternativas por fase

### Fase 1 — Disponibilização e compra *(fora do produto; parceiros)*

| Alternativa | Strengths | Weaknesses | IA |
|---|---|---|---|
| **BOL** (Queimas do Porto e Coimbra, Enterro da Gata) | Líder nas festas académicas; loja com a marca do evento; rede física; combate à revenda | Comissão de 6 % + IVA; acaba na venda | Não identificada |
| **Ticketline / MEO Blueticket / Fnac** | Marcas conhecidas; pontos de venda físicos | Cerca de 6 % de comissão; só fazem a venda | Não identificada |
| **Fever (+ DICE) / Shotgun** | Descoberta de eventos; público jovem; revenda gerida | Orientados para consumo e marketing; preço para o organizador não é público | **Sim** (Fever): recomendações e previsão de procura |
| **Eventbrite** | Self-service; app de check-in | Cara em bilhetes baratos (cerca de 15,6 % num bilhete de US$ 20) | **Sim**: gera descrições, emails e anúncios |
| **APP FAP** | App oficial; desconto de estudante; carrega a pulseira cashless | Uma só federação; não cobre a operação do evento | Não identificada |
| **Status quo** (Instagram + Google Forms + MB Way) | Grátis e conhecido | Pagamentos confirmados à mão; sem controlo de vagas | — |
| *Ref. internacional:* **FIXR / native** (Reino Unido) | Feitos para associações de estudantes; bilhetes só para estudantes | Não existem em Portugal; não tratam do recinto | Não identificada |

### Fase 2 — Entrada e controlo de acessos *(o produto só informa; a capacidade física fica fora de âmbito)*

| Alternativa | Strengths | Weaknesses | IA |
|---|---|---|---|
| **Pulseira cashless da Queima do Porto** (entrada + pagamento) | Uma pulseira para tudo; menos fraude | Em 2023, a troca do bilhete pela pulseira à porta causou horas de fila ([JN](https://www.jn.pt/pais/artigo/horas-na-fila-e-nao-entraram-troca-de-bilhete-por-pulseira-gera-confusao-no-queimodromo/16310596)); o participante não sabe que porta está menos cheia | Não |
| **Leitores da própria bilhética** (BOL, Eventbrite, pretix) | Validação de QR incluída | Cada plataforma tem o seu leitor; sem lotação por zona em tempo real | Não |
| **Listas em papel ou Excel** (eventos pequenos) | Grátis; funcionam sem rede | Fraude, filas, sem dados | — |
| *Ref. internacional:* **Weezevent** (França) | Bilhete, entrada (QR/NFC, zonas, offline) e cashless numa plataforma | Festivais profissionais; sem presença conhecida em Portugal | Não identificada |
| *Ref. internacional:* **Waitwhile** (fila virtual) | Entrada na fila por link ou SMS, com tempo de espera estimado; mais de 30 000 pessoas geridas no Tribeca Film Festival ([caso](https://waitwhile.com/case-studies/tribeca-film-festival/)) | Pensado para filas de experiências e serviços, não para portas de um recinto com dezenas de milhares de pessoas | Não identificada |

### Fase 3 — Durante o evento

#### 3a. Informação ao participante e orientação no recinto

| Alternativa | Strengths | Weaknesses | IA |
|---|---|---|---|
| **Instagram, site do evento e planta em imagem** (status quo) | É onde o público está; custo zero | Informação estática, publicada antes do evento; a planta é uma imagem que não se atualiza; não chega a quem já está no recinto | — |
| **APP FAP** | Base de estudantes do Porto; marca oficial | Bilhetes, descontos e desporto; não tem planta, avisos operacionais nem ajuda (segundo a página da FAP) | Não identificada |
| **Apps de festivais portugueses** (NOS Alive, Rock in Rio Lisboa) | Cartaz, mapa, agenda, informação de shuttles | Uma app por festival; só informação; sem pedido de ajuda | Não identificada |
| **BindePaFesta** (PT, lançada em junho de 2026) | Mapa único de arraiais e festas populares do país; chegou ao topo da App Store portuguesa ([RFM](https://rfm.pt/atualidade/23680/esta-app-portuguesa-mostra-te-onde-sao-as-festas-da-terrinha-e-ja-esta-entre-as-mais-descarregadas)) | Serve para descobrir festas, não para orientar dentro do recinto nem para a operação | Não identificada |
| *Ref. internacional:* **Appmiral** (CM.com) | App de festival madura: mapa, horários, push, botão SOS com localização | €375–935/mês; uma app por festival; exige instalação | Não identificada |
| *Ref. internacional:* **Woov** | Uma só app para muitos festivais; mapa, horários, amigos; tem interface em português ([App Store](https://apps.apple.com/nl/app/woov-your-festival-companion/id1580044680)) | Exige instalação e recolhe localização exata; sem pedido de ajuda nem operação | **Sim**: moderação automática do chat |
| *Ref. internacional:* **LineUpr** (Alemanha) | App de evento em PWA, sem instalação; subscrição anual para vários eventos ([preços](https://lineupr.com/en/pricing/)) | Feita para conferências; sem ajuda nem ocorrências | Não identificada |
| *Ref. internacional:* **42Chat FestivalBot** (EUA) | Assistente por SMS ou web que responde a dúvidas sobre o evento (mais de 60 temas) ([site](https://www.42chat.com/festivalbot)) | Só perguntas e respostas; preço não público; não diz como trata o que não sabe responder | **Sim**: chatbot |

#### 3b. Pagamentos no recinto *(fora do produto)*

| Alternativa | Strengths | Weaknesses | IA |
|---|---|---|---|
| **Zpos** (POS, PT) | Certificado pela AT; MB Way; funciona offline até 5 dias; desde €19,90/mês | Só pagamentos e faturação | Não identificada |
| **SumUp / ifthenpay** | Baratos e conhecidos | Só pagamento | Não |

#### 3c. Pedidos de ajuda, ocorrências e coordenação da equipa

| Alternativa | Strengths | Weaknesses | IA |
|---|---|---|---|
| **Rádio + grupos WhatsApp** | Imediatos; o rádio funciona sem rede | Sem registo, sem prioridade, sem localização precisa; não chegam ao participante | — |
| **Linha WhatsApp/QR + Ponto Lilás + voluntários por cores** (Queima do Porto) | Humano, conhecido, sem instalação | Pedidos sem categoria, local ou estado; não deixa dados | — |
| **Zello** (walkie-talkie no telemóvel) | Substitui rádios por US$ 7–13/utilizador/mês | Depende da rede; voz sem estrutura | Parcial: transcrição |
| *Ref. internacional:* **Raven Controls** (COP26, FIFA) | Ciclo completo ocorrência → resposta; o público reporta por WhatsApp/QR | Enterprise; sem PT; pensado para centrais profissionais | **Sim**: sugere categoria, prioridade e procedimento |
| *Ref. internacional:* **CriticalArc SafeZone** (campus) | Três botões de ajuda; localização só no momento do alerta; cerca de 25 % das universidades do Reino Unido | Só dentro do campus; exige app; licença institucional | Não identificada |
| *Ref. internacional:* **Rosterfy / VolunteerLocal** | Escalas e check-in de voluntários | Não tratam ocorrências em tempo real | **Sim** (Rosterfy) |

### Fase 4 — Regresso a casa e mobilidade

| Alternativa | Strengths | Weaknesses | IA |
|---|---|---|---|
| **Comunicados da STCP, Metro e Andante** | Informação oficial sobre vaivéns e reforços ([STCP](https://www.stcp.pt/pt/noticias/stcp-com-servico-dedicado-para-a-queima-das-fitas-do-porto/), [Andante](https://andante.pt/noticias/operacoes-especiais-queima-fitas-2025/)) | Publicados antes do evento, em canais separados; não dizem o que mudou durante a noite | — |
| **Google Maps com tempo real da STCP** (desde 9 de junho de 2026) | Localização dos autocarros e minutos até à chegada em 2 517 paragens ([Renascença](https://rr.pt/noticia/pais/2026/06/09/stcp-disponibiliza-horarios-dos-autocarros-em-tempo-real-no-google-maps/473765/)) | A notícia não diz se inclui os vaivéns especiais da Queima **[H: verificar]**; não conhece o recinto nem as paragens temporárias | Não identificada |
| **Moovit / app Anda** | Planeamento de viagem; bilhética Andante | Não conhecem o contexto do evento | Não identificada |
| **TVDE** (Uber, Bolt) | Partilha de viagem e botão de emergência | Preço e espera nos picos; não se ligam à organização | Não identificada |

### Fase 5 — Pós-evento

| Alternativa | Strengths | Weaknesses | IA |
|---|---|---|---|
| **Relatórios de cada fornecedor** (BOL, cashless, Zpos) | Dados de vendas e consumos fiáveis | Cada um mostra só a sua parte | **Sim** (Fever) |
| **Excel e relatórios manuais** | Flexível | Lento; as ocorrências tratadas por rádio não deixam histórico; o conhecimento perde-se quando a direção muda | — |

### Síntese

| Fase | Situação no mercado português | O nosso produto |
|---|---|---|
| Compra | **Bem servida** (BOL, Ticketline, Fever), com cerca de 6 % de comissão | Fora. Parceiro |
| Entrada | Resolvida tecnicamente nos eventos grandes; as filas de 2022 e 2023 mostram falta de **informação** ao participante | Só informação (portas, avisos). Tempo de espera é opção do inquérito |
| Durante: informação | **Estática** (Instagram, imagem da planta); as apps que existem são de um só evento ou de uma só federação | **Dentro**: planta, informação e avisos |
| Durante: ajuda e ocorrências | **Fragmentada e sem registo** (rádio, WhatsApp) | **Dentro**: pedido de ajuda e backoffice |
| Regresso | Informação oficial dispersa; o tempo real da STCP chegou ao Google Maps em 2026 | **Dentro**: informação de transportes do evento |
| Pós-evento | **Manual** | Consequência: fica o histórico das ocorrências |
| IA | Existe em marketing (Eventbrite, Fever), em centrais profissionais (Raven) e em assistentes de perguntas (42Chat). **Não encontrámos IA na operação de eventos académicos em Portugal** | Opções: triagem de pedidos e assistente de dúvidas, sempre com alternativa sem IA |

---

## 3. Oportunidade de diferenciação

**O problema do mercado:** quem informa o participante (Instagram, apps de festival) não trata ocorrências; quem trata ocorrências (rádio, centrais profissionais) não fala com o participante. Em Portugal, estas duas metades vivem em canais separados e nenhuma fica registada.

**A nossa proposta (repositório):** uma plataforma multi-evento com duas faces ligadas pelos mesmos dados.

| Face | O que faz | O que ganha por estar ligada à outra |
|---|---|---|
| **Aplicação web do participante** (link ou QR, sem instalar) | Escolhe o evento; vê a planta, a informação essencial e os transportes; pede ajuda | O pedido chega à equipa com contexto e local na planta |
| **Backoffice da equipa do evento** | Configura o evento (planta, pontos, transportes); atualiza informação durante a noite; recebe, prioriza e encaminha ocorrências | O que a equipa atualiza aparece logo ao participante; cada equipa só vê o seu evento |

**Porque é diferente das alternativas:**

1. **Liga participante e operação.** As apps de festival ficam na informação; o rádio e o WhatsApp ficam na operação. A referência mais próxima (Raven Controls) é para centrais profissionais e não tem versão portuguesa.
2. **Multi-evento com equipas independentes.** Uma só aplicação para várias Queimas e semanas académicas. As apps de festival clássicas são uma por evento (Appmiral); as multi-evento (Woov, LineUpr) não têm operação.
3. **A planta como referência comum.** O participante orienta-se por ela e a equipa situa nela os pedidos. Nas alternativas portuguesas a planta é uma imagem nas redes sociais.
4. **Sem instalação e preparada para rede fraca.** Appmiral, Woov e SafeZone exigem app nativa.
5. **Informação que muda durante a noite**, com a hora da última atualização. O status quo é estático.
6. **Local:** PT-PT, transportes especiais do evento, "Ligar 112" em primeiro lugar nas emergências.
7. **IA com alternativa sem IA.** Não é diferenciadora por si (o Raven e o 42Chat já a usam); o que conta é estar avaliada e ter fallback, como pede o enunciado.

**Riscos a gerir:**

| Risco | Resposta |
|---|---|
| "Já temos o Instagram e o WhatsApp" | Mostrar o ganho em tempo até à ajuda e em informação atualizada; entrada por QR tão rápida como o WhatsApp |
| O fornecedor da app atual da federação sente-se substituído | Entrar como complemento, por link ou QR, sem exigir mudanças na app existente |
| Um fornecedor internacional (Appmiral, Woov) desce de segmento | Operação e ocorrências, que não são o ADN deles; contexto português |
| Uso concentrado em poucos dias por ano | Multi-evento: o mesmo utilizador volta noutros eventos |
| Âmbito grande para o MVP | Prioridade: pedido de ajuda, planta e transportes (como está no repositório) |

O detalhe por funcionalidade (alternativas, matriz comparativa, preços e hipóteses a validar) está no anexo `02a`.

---

## 4. Fontes principais

[Problem and Opportunity Report (repositório)](https://github.com/tomasoliveira21/LABDSOF) · [Taxas de bilhética em Portugal (Observador)](https://observador.pt/especiais/bilhetes-para-concertos-mais-caros-ha-espetaculos-com-taxa-extra-para-garantir-servidores-mais-potentes/) · [BOL — Enterro da Gata 2026](https://enterrodagata.bol.pt/Comprar/Bilhetes/176491-enterro_da_gata_2026_bilhete_geral-forum_braga_gatodromo/) · [Fever — Data & Analytics](https://business.feverup.com/en/event-data-and-analytics/) · [Shotgun](https://nevent.ai/en/ticketing-platforms/shotgun/) · [Eventbrite — ferramentas de IA](https://www.eventbrite.com/blog/press/newsroom/eventbrite-introduces-ai-powered-tools-to-empower-creators-and-reimagine-event-marketing/) · [Taxas da Eventbrite 2026](https://www.tickpick.com/blog/organizer/eventbrite-2026-fee-changes) · [APP FAP](https://www.fap.pt/noticias/app-fap-fap-no-teu-bolso) · [Pulseira cashless da Queima (JPN)](https://www.jpn.up.pt/2023/04/14/queima-das-fitas-no-queimodromo-pagamentos-vao-ser-feitos-com-uma-pulseira/) · [FIXR](https://blog.fixr.co/reasons-to-use-fixr-for-your-society-events/) · [native](https://knowledge.native.fm/kb/en/events-ticketing-227940) · [Weezevent](https://weezevent.com/en-gb/festivals/) · [Waitwhile — Tribeca](https://waitwhile.com/case-studies/tribeca-film-festival/) · [BindePaFesta (RFM)](https://rfm.pt/atualidade/23680/esta-app-portuguesa-mostra-te-onde-sao-as-festas-da-terrinha-e-ja-esta-entre-as-mais-descarregadas) · [Appmiral — preços](https://appmiral.com/pricing) · [Woov (App Store)](https://apps.apple.com/nl/app/woov-your-festival-companion/id1580044680) · [LineUpr — preços](https://lineupr.com/en/pricing/) · [42Chat FestivalBot](https://www.42chat.com/festivalbot) · [Zpos](https://en.zpos.pt/festivals/) · [SumUp PT](https://pt.mobiletransaction.org/sumup-leitor-cartoes/) · [ifthenpay](https://shelf.pt/blog/ifthenpay-review) · [Zello](https://zello.com/pricing/) · [Raven Controls](https://ravencontrols.com/) · [CriticalArc SafeZone](https://criticalarc.com/safezone-features/) · [Rosterfy](https://www.rosterfy.com/pricing/) · [STCP — serviço da Queima](https://www.stcp.pt/pt/noticias/stcp-com-servico-dedicado-para-a-queima-das-fitas-do-porto/) · [STCP em tempo real no Google Maps (Renascença, 2026)](https://rr.pt/noticia/pais/2026/06/09/stcp-disponibiliza-horarios-dos-autocarros-em-tempo-real-no-google-maps/473765/).

Restantes fontes no anexo `02a`, secção 9.

---

## 5. Como usei IA nesta pesquisa

### 5.1 Ferramenta e papel

| Item | Descrição |
|---|---|
| Ferramenta | **Claude** (Anthropic), num projeto chamado "LABDSOF" que guarda os documentos de trabalho, com pesquisa na web e leitura de páginas ativadas |
| Papel da IA | **Assistente de pesquisa e de redação.** Procurou alternativas, leu as páginas dos fornecedores e a imprensa, resumiu e organizou em tabelas |
| Papel meu | Defini o âmbito e as perguntas em cada ronda, decidi o que entrava e saía, e alinhei o resultado com as decisões do grupo |
| O que a IA **não** fez | Não fez pesquisa com utilizadores, não testou produtos, não tomou decisões de produto |

### 5.2 Processo, por iteração

| Data | Versão | O que pedi | O que a IA fez |
|---|---|---|---|
| 2026-09-24 | v1.x | Concorrentes e alternativas não digitais para a coordenação de segurança, nas dimensões do enunciado | Três rondas de pesquisa (8 → 20 → 29 fichas), com matriz de 15 capacidades. |
| 2026-10-01 | v2.0 | Alargar à gestão de eventos académicos como um todo | Pesquisa por etapa do ciclo do evento: 10 famílias, mais de 50 produtos, preços públicos |
| 2026-10-01 | v3.0 | Versão curta, por fase, com forças, fraquezas e uso de IA, só mercado português | Condensou a pesquisa anterior em 2–3 páginas |
| 2026-10-06 | **v4.0** (este documento) | Iterar a análise com base no que já está no repositório | Leu o Problem and Opportunity Report, o guião do inquérito e o histórico de commits do repositório; reorganizou as fases; fez pesquisa nova para a informação ao participante, as filas e o regresso a casa (fontes P1–P10 do anexo `02a`) |

### 5.3 Regras que segui para manter a evidência fiável

| Regra | Como se vê no documento |
|---|---|
| Cada facto tem uma fonte com link | Links nas tabelas e na secção 4 |
| Ausência de evidência não é evidência de ausência | "Não identificada" na coluna de IA |
| Funcionalidades dos concorrentes são alegações comerciais | Aviso na legenda; nenhum produto foi testado |
| Nenhum dado de utilizadores foi gerado por IA | Este documento não tem resultados de inquéritos nem de entrevistas |
| A IA não é apresentada como facto | As conclusões da secção 3 são leitura nossa das tabelas, não um resultado medido |

### 5.4 Limitações do uso de IA

| Limitação | Efeito possível | Mitigação |
|---|---|---|
| A IA lê as páginas através de um resumo automático | Pode falhar um pormenor ou um preço | Abrir a fonte antes de citar um número no relatório final |
| Preços e funcionalidades mudam | Dados podem estar desatualizados | Cada lista de fontes tem a data de consulta |
| Algumas pesquisas não devolveram nada útil (por exemplo, apps oficiais da Queima de Coimbra e do Enterro da Gata) | Lacunas na cobertura | Registadas como "não encontrado" |
| A IA pode errar ou inventar | Risco de afirmações sem base | Só ficaram afirmações com link; verificação humana abaixo |
