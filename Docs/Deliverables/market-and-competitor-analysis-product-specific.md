# Anexo 02a — Análise de mercado específica do produto

> LABDSOF 2026/27 · Sprint A 
> **O que é este documento:** a análise de mercado **de cada coisa que queremos incluir no projeto**. O documento geral (`02-Analise-Mercado-e-Concorrencia.md`) cobre o mercado todo, fase a fase; este anexo desce ao nível da funcionalidade.
> **Base:** o que está no repositório em 2026-10-06 (`main`, commit `442ecc3`): `Docs/Deliverables/problem-opportunity-report-queima.md` (problema, visão, MVP, assunções A1–A9) e `Docs/Research/inquerito-estudantes-google-forms.gs` (inquérito a estudantes, perguntas 1–11).
> **Enunciado, secção Mercado:** concorrentes e alternativas não digitais: utilizadores, features, forças/fraquezas, modelo de negócio, UX, privacidade → proposta de valor.
>
> **Legenda de evidência**
> - Sem marca = facto com fonte. IDs (todos na secção 9): `P#` = fontes de 2026-10-06; `M#` = fontes de segurança, operação e apps; `N#` = fontes de bilhética, pagamentos e mercado; `F#` = caso de referência (Queima do Porto); `R:` = evidência citada no Problem and Opportunity Report do repositório.
> - As funcionalidades dos concorrentes são **alegações dos fornecedores** (sites, preços, lojas de apps). Não testámos nenhum produto.
> - Na matriz, ❌ quer dizer "não encontrado nas fontes", não "não existe".

---

## 0. Resumo

| Pergunta | Resposta curta |
|---|---|
| O que queremos construir? | Uma plataforma **multi-evento** com duas faces: uma **aplicação web para o participante** (planta, informação, transportes, pedido de ajuda) e um **backoffice para a equipa de cada evento** (configurar o evento, atualizar informação, tratar ocorrências) |
| Há algum produto que faça isto tudo para eventos académicos em Portugal? | **Não encontrámos.** Cada componente existe isolado: apps de festival dão planta e avisos; centrais profissionais tratam ocorrências; o Google Maps dá transportes; o WhatsApp recebe pedidos de ajuda |
| Quem chega mais perto? | **Appmiral** (planta, avisos e SOS numa app de festival) do lado do participante e **Raven Controls** (ocorrências com reporte do público por QR) do lado da equipa. Nenhum junta as duas faces para equipas académicas, em português e sem instalação |
| O que já é comum e não nos distingue? | Planta interativa, horários, avisos push, botão SOS, painel de ocorrências, tempo real de transportes |
| O que nos distingue? | (1) as duas faces ligadas pela **planta**; (2) **multi-evento com permissões por evento**; (3) **sem instalação** e preparada para rede fraca; (4) **transportes do próprio evento** mantidos pela equipa; (5) contexto português |
| Maior ameaça | O status quo gratuito: **Instagram + linha WhatsApp + rádio** |

---

## 1. O que queremos incluir (lido do repositório)

| # | Componente | Estado no repositório | Origem |
|---|---|---|---|
| **C1** | Plataforma multi-evento: cada evento com owner, equipa e permissões próprias | **MVP** | Relatório, "Visão do produto" e "Definição do MVP" |
| **C2** | Aplicação web do participante (PWA), aberta por link ou QR, sem instalação obrigatória | **MVP** | Relatório; inquérito, perguntas 7 e 9 |
| **C3** | Planta do recinto carregada e configurada pela equipa (entradas, palcos, WC, primeiros socorros, paragens) | **MVP** | Relatório, "Oportunidade"; inquérito, pergunta 8 |
| **C4** | Informação essencial do evento, atualizada durante a noite pela equipa (avisos) | **MVP** | Relatório, causa raiz 2; inquérito, pergunta 8 |
| **C5** | Informação de transportes e regresso a casa | **MVP** | Relatório; inquérito, perguntas 3 e 8 |
| **C6** | Pedido de ajuda pelo participante, com local indicado na planta | **MVP** | Relatório; inquérito, perguntas 5, 8 e 10 |
| **C7** | Backoffice: receber, priorizar e encaminhar ocorrências | **MVP** | Relatório, "Principais jornadas" |
| **C8** | Tempo de espera previsto nas entradas | **Candidato** (opção do inquérito) | Inquérito, pergunta 8. A capacidade física das portas está fora de âmbito |
| **C9** | IA: assistente para dúvidas sobre o evento e apoio à priorização de pedidos | **Candidato** (o enunciado obriga a pelo menos um workflow com IA) | Inquérito, pergunta 8; relatório, assunção A7 |
| **C10** | Funcionar com rede fraca (cache, modo degradado) e mostrar a hora da última atualização | **Requisito** | Relatório, assunção A8 e riscos técnicos |
| **C11** | Bilhete eletrónico na aplicação | **Candidato** (opção do inquérito); a bilhética é parceiro, não parte do MVP | Inquérito, pergunta 8; relatório, mapa de stakeholders |

**Fora de âmbito (repositório):** capacidade física das entradas; integrações pesadas; previsão avançada de multidões; automação completa.

---

## 2. Análise por componente

Cada ficha responde a: quem já faz isto, com que forças e fraquezas, se usa IA, e o que isso implica para nós.

### C1 — Plataforma multi-evento com equipas e permissões por evento

| Alternativa | O que faz | Forças | Fraquezas para nós | IA |
|---|---|---|---|---|
| **App por evento** (Appmiral; apps do NOS Alive e do Rock in Rio Lisboa) | Uma app com a marca de cada festival (M2, M8, M9) | Marca própria; madura | Cada evento paga e mantém a sua; o estudante instala uma por festa | Não identificada |
| **Woov** | Uma app para centenas de festivais; o utilizador escolhe o evento (M5, P2) | Prova que o modelo "uma app, muitos eventos" funciona; mais de 1 M de utilizadores por temporada | Sem operação; o organizador não trata ocorrências ali | Moderação do chat |
| **FEST App** | "Contentor" multi-festival com painel web para o organizador; plano de entrada gratuito (P10) | Self-service; barato | Distribuída pelas lojas de apps; só informação | Não identificada |
| **LineUpr** | Subscrição anual "Flex": uma quota de eventos e participantes para distribuir pelo ano, até 60 % mais barata do que comprar evento a evento (P3) | Modelo de preço pensado para quem tem vários eventos por ano | Conferências; sem ocorrências | Não identificada |
| **BindePaFesta** (PT, junho de 2026) | Mapa de arraiais e festas populares de todo o país; os organizadores gerem o seu conteúdo (P1) | Mostra que há procura em Portugal por uma app comum a muitas festas; chegou ao topo da App Store portuguesa | Descoberta de festas, não operação do evento; público das festas populares | Não identificada |
| **APP FAP** | App oficial da federação do Porto (M10) | Já tem a base de estudantes do Porto | Uma só federação; cada federação teria de fazer a sua | Não identificada |
| **MSL + native / FIXR** (Reino Unido) | Plataformas para students' unions com permissões por comissão (N15, N16, N17) | Provam que o segmento académico compra plataformas próprias | Não existem em Portugal; centradas na venda | Não identificada |
| **Não digital:** cada organização trata do seu evento com as suas ferramentas | — | Independência total | Nada se reaproveita entre eventos nem entre direções | — |

**O que aprendemos:** o multi-evento **não é novidade** do lado do participante (Woov, FEST, BindePaFesta). A novidade está em o **backoffice** também ser multi-evento, com **isolamento entre equipas**. É por isso que o relatório do repositório trata as permissões entre eventos como risco técnico: é o ponto que tem de ser demonstrado.

### C2 — Aplicação web do participante, sem instalação

| Alternativa | Canal de entrada | Forças | Fraquezas para nós |
|---|---|---|---|
| **Apps nativas** (Appmiral, Woov, FEST, SafeZone, APP FAP) | Instalação pela loja de apps | Push fiável; funcionam melhor offline | Fricção de instalar mais uma app |
| **LineUpr** | PWA, sem instalação (P3) | Abre por link; 20 línguas | Push só no plano mais caro |
| **Raven Eye** | O público reporta por **WhatsApp com QR** (M13) | Zero instalação; canal que todos têm | Expõe o número de telefone |
| **42Chat FestivalBot** | Assistente por **SMS ou web** (P4) | Sem instalação | Só perguntas e respostas |
| **Linha WhatsApp/QR da FAP** | QR no recinto (F4, F5) | Já é usada | Sem estrutura nem métricas |
| **Não digital:** placas, sinalética, staff | Presencial | Não depende do telemóvel | Em 2022 e 2023 houve queixas de "falta de informação e sinalética" (R: Renascença, Público) |

**O que aprendemos:** o mercado profissional já foi atrás do participante **sem lhe pedir para instalar nada** (WhatsApp, SMS, PWA). O link ou QR é a escolha certa; o custo é perder o push nativo, por isso os avisos (C4) têm de funcionar com a aplicação aberta e, se possível, com notificações web. A pergunta 9 do inquérito mede exatamente esta barreira ("ter de instalar mais uma app").

### C3 — Planta do recinto configurável

| Alternativa | O que faz | Modelo | Forças | Fraquezas para nós |
|---|---|---|---|---|
| **Imagem da planta no Instagram ou no site** (status quo) | Imagem estática | Grátis | Custo zero | Não se atualiza; não serve para indicar um local; não se liga a pedidos |
| **Appmiral** | Mapa interativo (só no plano Pro, €935/mês) (M2) | SaaS anual | Maduro; localizar amigos | Preço; uma app por festival |
| **Woov** | Mapa do festival; mapas 3D (M5, P2) | B2B, preço não público | Experiência rica | Recolhe localização exata do utilizador (P2) |
| **MapsPeople (MapsIndoors) / Mappedin** | Mapas interiores com percursos passo a passo, pesquisa de pontos e atualização em tempo real; clientes como a Fira Barcelona (P6) | Enterprise, preço não público | Qualidade profissional | Pensados para feiras e edifícios; não diz se serve recintos temporários ao ar livre |
| **Iventis** | Planeamento georreferenciado do recinto para a organização (M42) | SaaS, sob consulta | Planos de capacidade e evacuação | É para planear antes, não para o participante durante |
| **Google Maps / OpenStreetMap** | Mapa da cidade | Grátis | Universal | Não conhecem o interior de um recinto temporário |
| **what3words** (usado na central da Controlled Events) | Dá um nome a cada quadrado de 3 m (M18) | — | Localização precisa por voz | Mais uma app; não mostra o recinto |

**O que aprendemos:** mapa interativo é **comum** nas apps de festival. O que não encontrámos foi a planta usada como **referência partilhada entre o participante e a equipa**: o participante indica na planta onde está, e a equipa vê a ocorrência nesse ponto. A solução simples do repositório (a equipa carrega uma imagem ou PDF e marca pontos) evita o custo de um fornecedor de mapas e responde ao facto de a localização do telemóvel ser imprecisa num recinto cheio.

### C4 — Informação essencial e avisos durante a noite

| Alternativa | O que faz | Forças | Fraquezas para nós |
|---|---|---|---|
| **Instagram (stories) e site** | Avisos da organização | Alcance | Não chega de forma fiável a quem está no recinto; mistura-se com o resto do conteúdo |
| **Push das apps de festival** (Appmiral, WMT Digital, Woov, FEST) | Avisos gerais e por zona (geofencing no WMT) (M2, M7, P10) | Imediato | Exige app instalada |
| **Aloompa Presence** (Crowd Connected) | Mapas de calor ao vivo e push segmentado por zona do recinto, com localização por consentimento (P5) | Avisar só quem está numa zona | App nativa; a organização tem de fornecer a planta em CAD |
| **Painéis digitais, megafone e staff** | Avisos no local (P8) | Não dependem do telemóvel | Alcance limitado; sem registo |

**O que aprendemos:** difundir avisos é **commodity**. O que acrescentamos é a **fiabilidade**: cada informação com a hora da última atualização (o relatório identifica o risco de "informação desatualizada piorar a situação") e uma só fonte para o que muda durante a noite.

### C5 — Transportes e regresso a casa

| Alternativa | O que faz | Forças | Fraquezas para nós |
|---|---|---|---|
| **Comunicados da STCP, Metro e Andante** | Anunciam vaivéns e reforços antes do evento (R: Andante, STCP) | Oficiais | Estáticos; em canais separados |
| **Google Maps com tempo real da STCP** (desde 9 de junho de 2026) | Localização dos autocarros e tempo até à chegada em 2 517 paragens (P9) | Grátis; já instalado | A notícia não diz se os vaivéns especiais da Queima estão incluídos **[H: verificar no GTFS]** |
| **Moovit, app Anda** | Planeamento; bilhética Andante (M36) | Conhecidas | Não conhecem o evento |
| **Apps de festival** (Rock in Rio Lisboa) | Informação estática de shuttles (M9) | No mesmo sítio que o resto | Não muda durante a noite |
| **SafeZone** | Acompanhamento de shuttles do campus (M22) | Tempo real | Campus, não eventos |
| **Puntos Violeta** (Espanha) | Grupos para "voltar juntos" (M28) | Regresso em segurança | Outro país; outro foco |
| **TVDE** (Uber, Bolt) | Partilha de viagem (M35) | Já instaladas | Preço nos picos |

**O que aprendemos:** desde junho de 2026, o tempo real das linhas regulares da STCP **já está resolvido** pelo Google Maps; não vale a pena refazê-lo. A nossa parte é o que as apps de mobilidade não sabem: **paragens temporárias na planta, horários do vaivém do evento e o que mudou esta noite**, introduzidos pela equipa (assunção A6 do relatório). Para o resto, basta um link para o Google Maps.

### C6 — Pedido de ajuda com local na planta

| Alternativa | Utilizadores | UX | Privacidade | Forças | Fraquezas para nós | IA |
|---|---|---|---|---|---|---|
| **Linha WhatsApp/QR da FAP** | Participante → operador | Familiar, sem instalar | Expõe o número; dados na Meta | Já existe | Sem categoria, local ou estado | — |
| **Procurar um segurança, voluntário ou o posto médico** | Participante | Presencial | Boa | Humano | Só serve quem os vê e consegue lá chegar | — |
| **112** | Cidadão → emergência | Chamada | — | Oficial | Não conhece o recinto; não é para "a minha amiga não está bem" | — |
| **Appmiral SOS** | Participante → segurança do festival | Botão na app | Localização em tempo real | "Centenas de alertas" tratados (M1) | App nativa; sem versão para equipas voluntárias | Não |
| **SafeZone** | Estudante → segurança do campus | Três botões: emergência, primeiros socorros, ajuda | Localização **só no momento do alerta** (M22) | Boa prática de referência | Só no campus | Não |
| **Raven Eye** | Público → central | WhatsApp + QR | Número exposto | Integra com a central (M13) | Enterprise; sem PT | Sim, na central |
| **AlertCops** (Espanha) | Cidadão → polícia | App | Identificação obrigatória (M27) | Oficial | Não aceita anonimato | Não |

**O que aprendemos:** o botão de ajuda **existe** em apps de festival e de campus. Três padrões a seguir: (1) entrada sem instalar, como o Raven Eye; (2) localização só no momento do pedido, como o SafeZone; (3) deixar claro que não substitui o 112, como já está no relatório. O que é novo é o participante **marcar o local na planta** em vez de depender do GPS. A pergunta 10 do inquérito mede a aceitação disto.

### C7 — Backoffice de ocorrências

| Alternativa | Utilizadores | Features | Modelo | Forças | Fraquezas para nós | IA |
|---|---|---|---|---|---|---|
| **Rádio + central com registo manual** | Coordenação, segurança | Voz por canal | €20–25 por rádio por semana (M45) | Funciona sem rede | Sem histórico estruturado; local descrito de viva voz | — |
| **Grupos WhatsApp e folhas de cálculo** | Equipa | Mensagens | Grátis | Todos sabem usar | Sem dono nem estado por caso | — |
| **Raven Controls** | Central profissional | Registo, mapas, checklists, notificação de grupos | SaaS, sob consulta (M13) | Usado na COP26 e em parceria para o Mundial FIFA | Para centrais profissionais; sem PT | **Sim**: sugere categoria, prioridade e procedimento |
| **Momentus WeTrack** | Recintos | Incidentes com gravidade e planos de resposta (M16) | Enterprise | Mais de 4 000 recintos | Enterprise | Não identificada |
| **Controlled Events** | Eventos cívicos | Central com mapa e meteorologia; licença **por evento** (M18) | Por evento ou anual | Modelo de preço útil para eventos sazonais | Não se liga ao participante | Não identificada |
| **Appmiral Emergency Reporting** | Organizador | Junta os canais de reporte numa vista (M3) | Incluído na app | Do mesmo fornecedor que a app | Pouca informação pública | Não identificada |
| **Software de central de controlo** (mercado) | Estádios, cidades | — | US$ 30 k a mais de US$ 1 M (M19, fonte de um fornecedor) | — | Fora do alcance de uma federação | — |

**O que aprendemos:** o ciclo ocorrência → prioridade → encaminhamento está **provado** no mercado profissional. O que não encontrámos é uma versão para **equipas académicas** (organização, comissão de prevenção, voluntários), em português, ligada à aplicação do participante e a um preço de federação.

### C8 — Tempo de espera nas entradas *(candidato)*

| Alternativa | O que faz | Forças | Fraquezas para nós |
|---|---|---|---|
| **Sem informação** (status quo) | O participante só descobre a fila quando chega | — | Filas de mais de 500 m e mais de 3 h em 2022 e 2023 (R: Renascença, Público) |
| **Appmiral Venue Occupancy** | Estado vermelho, laranja ou verde por zona, **atualizado à mão** pelo staff (M4) | Simples; não precisa de sensores | Depende de alguém atualizar |
| **Painéis de operações de festival** | Juntam leituras das portas, comprimento das filas e vaivéns; alertas quando a espera passa de um limite, com aviso por push e sinalética (P8, blog de um fornecedor) | Prática descrita no setor | Precisa dos dados da bilhética |
| **Waitwhile** | Fila virtual por link ou SMS com tempo estimado (P7) | Mais de 30 000 pessoas geridas no Tribeca | Não é para portas de um recinto |
| **Queue-it** | Fila virtual para a venda online, desde US$ 1 499 por evento (N34) | Resolve picos na compra | É para a venda, não para a porta |
| **Crowdscan** | Mede densidade com sensores, sem câmaras (M43) | Dados objetivos | Hardware e custo por evento |
| **Estimativa a partir da app** | Um estudo mostra correlação acima de 0,8 entre a densidade estimada por uma app de festival e a medida em vídeo (R: arXiv 1306.3133) | Viável tecnicamente | Exige muitos utilizadores com localização ligada |

**O que aprendemos:** a versão simples (a equipa indica à mão o estado de cada porta, como no Appmiral) cabe no MVP e é coerente com a assunção A6. A versão automática depende de dados da bilhética ou de localização, que são integração pesada e levantam questões de privacidade. Faz sentido avançar só se o inquérito (pergunta 8) mostrar procura.

### C9 — IA *(candidato; o enunciado obriga a um workflow com IA e a um fallback)*

| Uso | Quem já faz | Forças | Fraquezas e riscos | Alternativa sem IA |
|---|---|---|---|---|
| **Assistente de dúvidas do participante** | **42Chat FestivalBot**: SMS e web, mais de 60 temas, escalões até 50 000 utilizadores (P4) | Responde de imediato; alivia o staff | O fornecedor não publica precisão nem o que faz quando não sabe; uma resposta errada sobre transportes pode pôr alguém em risco | Página de perguntas frequentes e pesquisa na informação do evento |
| **Triagem de ocorrências** | **Raven Controls** sugere categoria, prioridade e procedimento a um operador que decide (M13) | Valida o padrão "a IA sugere, a pessoa decide" | Sem PT; operador profissional | Categoria escolhida pelo participante + ordem de chegada |
| **Entrevista guiada para reportes sensíveis** | **Spot** (M32) | Ajuda a descrever | Fora do momento do evento | Formulário curto |
| **Moderação de conteúdo** | **Woov**, com 95 % menos trabalho humano (M6) | Escala | Não se aplica ao nosso MVP | — |
| **Marketing e previsão de procura** | Eventbrite, Fever (N1; documento geral) | — | Fora do nosso âmbito | — |

**O que aprendemos:** a IA **não distingue o produto por si**, porque os dois usos candidatos já existem. O que conta é cumprir o enunciado melhor do que os fornecedores mostram publicamente: respostas do assistente **só a partir da informação que a equipa carregou**, com indicação da fonte e da hora; a IA nunca baixa a prioridade de um pedido sem uma pessoa; avaliação com conjunto de casos e comparação com a alternativa sem IA (assunção A7).

### C10 — Rede fraca e modo degradado

| Alternativa | Como lida com a rede | Fonte |
|---|---|---|
| Rádio | Não depende da rede móvel | M45 |
| Woov | Cashless RFID a funcionar offline | M5 |
| Zpos | POS offline até 5 dias | N24 |
| Weezevent | Terminais de entrada offline | N5 |
| Zello, WhatsApp | Dependem da rede | M40 |
| Apps de festival | Não encontrámos informação pública sobre o modo offline | — |

**O que aprendemos:** só os sistemas profissionais de pagamento e entrada publicitam o offline. O congestionamento em multidões está documentado (o Sónar é caso de estudo, M46). Guardar a planta e a última informação em cache e dizer "dados das HH:MM" é um requisito do enunciado que quase nenhuma alternativa do lado do participante mostra.

### C11 — Bilhete eletrónico na aplicação *(candidato; bilhética = parceiro)*

| Alternativa | Situação |
|---|---|
| **BOL, Fnac, APP FAP** | Já vendem os bilhetes das Queimas do Porto, Coimbra e do Enterro da Gata (N8–N11, M10) |
| **Pulseira cashless** | No Porto, a entrada faz-se só com pulseira (N22) |

**O que aprendemos:** vender ou guardar bilhetes seria competir com fornecedores que já estão dentro do cliente, numa fase bem servida. Se o inquérito mostrar procura, a resposta de menor risco é um **link para o bilhete** na plataforma de bilhética, não bilhética própria.

---

## 3. Matriz comparativa (componentes do nosso produto)

Legenda: ✅ confirmado na fonte · ◐ parcial · ❌ não encontrado nas fontes · ? sem informação · — não se aplica. A última linha é o **alvo** do MVP, não um facto.

| Alternativa | C1 Multi-evento | C2 Sem instalar | C3 Planta | C4 Avisos | C5 Transportes | C6 Pedir ajuda | C7 Ocorrências | C8 Espera | C9 IA | C10 Rede fraca | PT-PT |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Instagram + site + planta em imagem | — | ✅ | ◐ estática | ◐ | ◐ estático | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| Linha WhatsApp/QR da FAP | ❌ | ✅ | ❌ | ❌ | ❌ | ✅ | ◐ manual | ❌ | ❌ | ◐ | ✅ |
| Rádio + central | — | — | ❌ | ❌ | ❌ | ❌ | ◐ verbal | ❌ | ❌ | ✅ | ✅ |
| APP FAP | ◐ eventos da FAP | ❌ | ❌ | ◐ | ❌ | ❌ | ❌ | ❌ | ❌ | ? | ✅ |
| Google Maps (STCP em tempo real) | — | ✅ | ❌ | ❌ | ✅ linhas regulares | ❌ | ❌ | ❌ | ? | ◐ | ✅ |
| BindePaFesta | ✅ | ◐ app e site | ◐ mapa de festas | ✅ | ◐ direções | ❌ | ❌ | ❌ | ❌ | ? | ✅ |
| Appmiral | ❌ uma por festival | ❌ | ✅ (Pro) | ✅ | ? | ✅ SOS | ◐ | ◐ manual | ❌ | ? | ? |
| Woov | ✅ | ❌ | ✅ | ✅ | ? | ❌ | ❌ | ❌ | ◐ moderação | ◐ cashless | ✅ interface |
| LineUpr | ✅ | ✅ PWA | ◐ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ? | ? |
| FEST App | ✅ | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ? | ? |
| 42Chat FestivalBot | ? | ✅ SMS/web | ❌ | ❌ | ◐ responde a dúvidas | ❌ | ❌ | ❌ | ✅ assistente | ? | ❌ |
| Raven Controls (+ Eye) | ? | ✅ WhatsApp | ✅ para a central | ❌ | ❌ | ✅ | ✅ | ❌ | ✅ triagem | ? | ❌ |
| SafeZone | ❌ campus | ❌ | ◐ | ✅ | ◐ shuttles | ✅ | ✅ | ❌ | ❌ | ? | ? |
| Waitwhile | ? | ✅ | ❌ | ◐ SMS | ❌ | ❌ | ❌ | ✅ fila virtual | ❌ | ? | ? |
| **Nós (alvo do MVP)** | ✅ | ✅ | ✅ | ✅ | ✅ do evento | ✅ | ✅ | ◐ candidato | ◐ com fallback | ✅ | ✅ |

**Leitura:**
1. **Nenhuma alternativa cobre ao mesmo tempo C3 + C5 + C6 + C7**, que é o núcleo do MVP no repositório (planta, transportes, pedido de ajuda, ocorrências).
2. Quem tem **C6 e C7** (Raven, SafeZone) não tem a face de informação do participante nem versão portuguesa. Quem tem **C3 e C4** (Appmiral, Woov, FEST) não tem backoffice de ocorrências.
3. **C2 (sem instalar)** aparece sobretudo em canais de mensagens (WhatsApp, SMS); as apps de festival com mais funcionalidades são nativas.
4. O status quo português cobre quase tudo **em parte e em canais diferentes**. É esse o concorrente real.

---

## 4. Concorrentes mais próximos nas dimensões do enunciado

| Dimensão | Appmiral (CM.com) | Raven Controls | Woov | Status quo da Queima do Porto |
|---|---|---|---|---|
| **Utilizadores** | Participante; organizador de festival | Central de controlo; staff; público pelo Raven Eye | Participante; organizador | Participante; organização; voluntários; segurança |
| **Features relevantes** | Mapa, horários, push, SOS com localização, lotação manual | Registo de ocorrências, mapas, checklists, IA de triagem, reporte por WhatsApp/QR | Mapa, horários, amigos, chat | Instagram e site, APP FAP, linha WhatsApp/QR, rádio, voluntários por cores |
| **Forças** | Madura; SOS com uso real | Ciclo completo; credibilidade em grandes eventos | Uma app para muitos festivais; interface em PT | Grátis; conhecido; humano |
| **Fraquezas** | Uma app por festival; ADN de marketing | Enterprise; sem PT; para profissionais | Sem ajuda nem operação | Estático; sem registo; canais separados |
| **Modelo de negócio** | €375–935/mês, faturação anual | SaaS por níveis, preço sob consulta | B2B, preço não público | Sem custo de software |
| **UX** | App nativa com a marca do festival | Consola para operadores; público por WhatsApp | App nativa social | Vários canais; nenhum mostra o estado do pedido |
| **Privacidade** | Localização em tempo real no SOS | Número de telefone exposto no reporte | Localização exata e contactos ligados ao utilizador (P2) | Número exposto no WhatsApp; rádio em canal aberto |

**Nós, nas mesmas dimensões (alvo):** participante e equipa do evento com o mesmo peso · planta, informação, transportes, pedido de ajuda e ocorrências · aplicação web por link ou QR · local indicado pelo participante na planta, partilhado só no pedido, com retenção curta.

---

## 5. Diferenciação por componente

| Componente | É comum no mercado? | Onde nos distinguimos | Como se prova |
|---|---|---|---|
| C1 Multi-evento | Sim, do lado do participante | Backoffice multi-evento com isolamento entre equipas | Testes automáticos de permissões; um segundo evento configurado sem desenvolvimento |
| C2 Sem instalação | Parcial (canais de mensagens) | Aplicação web completa, não só um canal de reporte | Tempo entre ler o QR e ver a planta |
| C3 Planta | Sim | Planta carregada pela equipa e usada pelas duas faces | Percentagem de pedidos com local marcado |
| C4 Avisos | Sim | Hora da última atualização em cada informação | Percentagem de conteúdos com hora visível |
| C5 Transportes | Sim, para linhas regulares | Vaivéns e paragens do evento; link para o Google Maps no resto | Comparação com os comunicados oficiais |
| C6 Pedido de ajuda | Sim, em apps nativas | Sem instalar; local na planta; "Ligar 112" primeiro | Tempo até o pedido ser submetido |
| C7 Ocorrências | Sim, em centrais profissionais | Para equipas académicas, em PT, ligado ao participante | Tempo entre pedido e encaminhamento |
| C8 Espera | Raro | Versão manual por porta | Só se o inquérito mostrar procura |
| C9 IA | Sim | Avaliação publicada e fallback | Precisão contra a alternativa sem IA |
| C10 Rede fraca | Raro do lado do participante | Cache e "dados das HH:MM" | Demonstração do modo degradado |

**Em resumo:** quase todos os componentes existem em algum produto. A diferenciação está na **combinação** (as duas faces ligadas pela planta), no **segmento** (equipas de eventos académicos portugueses) e na **forma de entrega** (multi-evento, sem instalação, resistente a rede fraca).

---

## 6. Modelo de negócio: âncoras de preço relevantes

| Referência | Preço | Fonte |
|---|---|---|
| Appmiral (app de festival) | €375 / 625 / 935 por mês, faturação anual | M2 |
| LineUpr (app de evento em PWA) | Três escalões; subscrição anual para vários eventos até 60 % mais barata; preço por calculadora | P3 |
| FEST App | Plano de entrada gratuito; a própria empresa situa ferramentas de festival entre US$ 1 000 e US$ 25 000 e desenvolvimento à medida entre US$ 40 000 e US$ 400 000 | P10 (blog de um fornecedor) |
| 42Chat FestivalBot | Três escalões, até 5 000, 15 000 e 50 000 utilizadores; preço não público | P4 |
| Controlled Events | Licença por evento ou anual; preço não público | M18 |
| Software de central de controlo | US$ 30 k a mais de US$ 1 M | M19 (fonte de um fornecedor) |
| Aluguer de rádios (PT) | €20–25 por rádio por semana | M45 |
| Zello | US$ 6,80–12,75 por utilizador por mês | M40 |
| Queue-it | Desde US$ 1 499 por evento | N34 |

---

## 7. Ameaças

| Ameaça | Probabilidade | Impacto | Resposta |
|---|---|---|---|
| A organização continua com Instagram, WhatsApp e rádio | Alta | Alto | Medir e mostrar tempo até à ajuda e informação atualizada num piloto |
| O fornecedor da app da federação acrescenta planta e ajuda | Média | Alto | Entrar como complemento por link; valor do multi-evento |
| Appmiral ou Woov ganham clientes académicos em Portugal | Baixa a média | Alto | Operação e ocorrências; contexto local; sem instalação |
| O Google Maps passa a cobrir os vaivéns do evento | Média | Baixo | Usar o link; manter só as paragens na planta |
| Os participantes não abrem a aplicação durante a noite (assunção A2) | Média | Muito alto | Inquérito, perguntas 7 e 9; QR no bilhete e no recinto |

---

## 8. O que falta validar e onde

| Hipótese desta análise | Onde se valida | Liga a |
|---|---|---|
| Os estudantes procuram hoje a informação no Instagram e em grupos de WhatsApp | Inquérito, pergunta 4 | C4 |
| Planta, transportes e botão de ajuda são as funcionalidades mais úteis | Inquérito, pergunta 8 | C3, C5, C6 |
| Instalar uma app é uma barreira | Inquérito, pergunta 9 | C2 |
| Os estudantes aceitam indicar o local na planta em vez de partilhar o GPS | Inquérito, pergunta 10 | C6 |
| Há procura por tempo de espera, assistente e bilhete na aplicação | Inquérito, pergunta 8 | C8, C9, C11 |
| As ocorrências são coordenadas por rádio e telefone, sem histórico | Entrevista ao voluntário da segurança | C7 |
| Os vaivéns especiais não aparecem no Google Maps | Verificação técnica do GTFS da STCP | C5 |
| As organizações pagariam uma licença anual | Conversa com organizações | Secção 6 |

Até lá, estas linhas são hipóteses e não entram no relatório como evidência.

---

## 9. Fontes

Só estão listadas as fontes citadas neste anexo. Os IDs vêm das rondas de pesquisa originais, por isso a numeração tem saltos.

### 9.1 Fontes novas (consultadas em 2026-10-06)

| ID | Fonte | Tipo |
|---|---|---|
| P1 | [RFM — App portuguesa mostra onde são as festas (BindePaFesta), 2026-08-27](https://rfm.pt/atualidade/23680/esta-app-portuguesa-mostra-te-onde-sao-as-festas-da-terrinha-e-ja-esta-entre-as-mais-descarregadas) | Imprensa |
| P2 | [App Store — Woov: Your Festival Companion](https://apps.apple.com/nl/app/woov-your-festival-companion/id1580044680) | Loja de apps |
| P3 | [LineUpr — Pricing](https://lineupr.com/en/pricing/) | Fornecedor |
| P4 | [42Chat — FestivalBot](https://www.42chat.com/festivalbot) | Fornecedor |
| P5 | [Aloompa — Presence (powered by Crowd Connected)](https://support.aloompa.com/article/400-presence-crowdconnected) | Fornecedor |
| P6 | [MapsPeople — Interactive indoor maps for events](https://mapspeople.com/industries/events) | Fornecedor |
| P7 | [Waitwhile — Tribeca Film Festival case study](https://waitwhile.com/case-studies/tribeca-film-festival/) | Fornecedor |
| P8 | [Ticket Fairy — Real-time festival ops dashboards](https://www.ticketfairy.com/blog/real-time-festival-ops-dashboards-feeder-city-arrivals-and-queue-health.md) | Blog de fornecedor |
| P9 | [Renascença — STCP disponibiliza horários em tempo real no Google Maps, 2026-06-09](https://rr.pt/noticia/pais/2026/06/09/stcp-disponibiliza-horarios-dos-autocarros-em-tempo-real-no-google-maps/473765/) | Imprensa |
| P10 | [FEST — How much does a festival app cost in 2026](https://festapp.io/blog/how-much-festival-app-cost-2026) | Blog de fornecedor |

### 9.2 Evidência do repositório (Problem and Opportunity Report, secção 4)

| ID | Fonte |
|---|---|
| R: Renascença | [Filas para a Queima do Porto (2022)](https://rr.pt/noticia/pais/2022/05/05/filas-para-a-queima-das-fitas-do-porto-achei-que-ia-colapsar-a-ser-esmagada-e-quase-a-sufocar/283010/) |
| R: Público | [Primeira noite caótica na Queima do Porto (2023)](https://www.publico.pt/2023/05/08/p3/noticia/primeira-noite-caotica-queima-fitas-porto-voltou-normal-2048919) |
| R: Andante, STCP | [Operações especiais da Queima 2025](https://andante.pt/noticias/operacoes-especiais-queima-fitas-2025/) · [STCP com serviço dedicado](https://www.stcp.pt/pt/noticias/stcp-com-servico-dedicado-para-a-queima-das-fitas-do-porto/) |
| R: arXiv | [Estimativa de densidade de multidões a partir de uma app de festival](https://arxiv.org/pdf/1306.3133) |

### 9.3 Segurança, operação e apps de evento (consultadas em 2026-09-24)

| ID | Fonte |
|---|---|
| M1 | [CM.com — Festival App Introduces SOS Alert (2024-08-05)](https://www.cm.com/press/festival-app-introduces-sos-notification/) |
| M2 | [Appmiral — Pricing](https://appmiral.com/pricing) |
| M3 | [Appmiral Knowledge Center — Emergency Reporting](https://help.appmiral.com/emergency) |
| M4 | [Appmiral — Venue Occupancy Module](https://help.appmiral.com/app-features-explained/crowd-control-with-the-venue-occupancy-module) |
| M5 | [Woov — site oficial](https://woov.com/) |
| M6 | [Lasso Moderation — Woov case study](https://www.lassomoderation.com/case-studies/woov-keeping-festival-communities-safe-at-scale) |
| M7 | [WMT Digital (ex-Aloompa) — Live Events](https://wmt.digital/platforms/live-events) |
| M8 | [PCGuia — App do Dia: NOS Alive](https://www.pcguia.pt/2026/07/app-do-dia-nos-alive-2024/) |
| M9 | [PCGuia — App do Dia: Rock in Rio Lisboa](https://www.pcguia.pt/2026/06/app-do-dia-rock-in-rio-lisboa-2/) |
| M10 | [FAP — APP FAP, a FAP no teu bolso](https://www.fap.pt/noticias/app-fap-fap-no-teu-bolso) |
| M13 | [Raven Controls — Total Event Control](https://ravencontrols.com/) |
| M16 | [Momentus — WeTrack Incident Management](https://gomomentus.com/incident-management-software) |
| M18 | [Controlled Events — Incident Management Software](https://controlledevents.com/incident-management-software/) |
| M19 | [SIRV — Best control room software (2024; fonte de um fornecedor)](https://getsirv.com/2024/04/best-control-room-software/) |
| M22 | [CriticalArc — SafeZone Features](https://criticalarc.com/safezone-features/) |
| M27 | [Segurma — ¿Qué es AlertCops?](https://segurma.com/que-es-alertcops/) |
| M28 | [Puntos Violeta — app oficial](https://www.puntosvioleta.com/index.html) |
| M32 | [Talk to Spot — FAQ](https://www.talktospot.com/faq) |
| M35 | [Uber — Rider Safety Features](https://www.uber.com/gb/en/ride/safety/rider-safety-features/) |
| M36 | [Andante — App Anda](https://andante.pt/en/purchase/anda-app/) |
| M40 | [Zello — Pricing](https://zello.com/pricing/) |
| M42 | [Iventis — Pricing & Plans](https://www.iventis.com/pricing/) |
| M43 | [Crowdscan — Events](https://www.crowdscan.be/markets/events) |
| M45 | [Rádio Silva — Aluguer de rádios](https://radiosilva.com/radios/) |
| M46 | [MDPI Sensors — Opportunistic network algorithms in music festival scenarios (2021)](https://www.mdpi.com/1424-8220/21/10/3315) |

### 9.4 Bilhética, pagamentos e mercado (consultadas em 2026-10-01)

| ID | Fonte |
|---|---|
| N1 | [TickPick — Eventbrite's 2026 changes: what organizers pay now](https://www.tickpick.com/blog/organizer/eventbrite-2026-fee-changes) |
| N5 | [Weezevent — Festivals](https://weezevent.com/en-gb/festivals/) |
| N8 | [BOL — Enterro da Gata 2026 (loja com a marca do evento)](https://enterrodagata.bol.pt/Comprar/Bilhetes/176491-enterro_da_gata_2026_bilhete_geral-forum_braga_gatodromo/) |
| N9 | [Bilheteira Fnac — Enterro da Gata 2026](https://bilheteira.fnac.pt/Evento-556974/ENTERRO-DA-GATA-2026-) · [Bilheteira Fnac — Queima das Fitas do Porto 2026](https://bilheteira.fnac.pt/Evento-555445/NOITES-DA-QUEIMA-DAS-FITAS-DO-PORTO-2026-) |
| N10 | [BOL — Passe Geral Queima das Fitas do Porto 2026](https://www.bol.pt/Comprar/Bilhetes/169689-passe_geral_noites_da_queima_das_fitas_do_porto_2026-queimodromo/) |
| N11 | [BOL — Queima das Fitas Coimbra 2026](https://www.bol.pt/Comprar/Bilhetes/168549-queima_das_fitas_coimbra_2026_bilhete_geral-praca_da_cancao/) |
| N15 | [FIXR — 10 reasons to use FIXR for your student society](https://blog.fixr.co/reasons-to-use-fixr-for-your-society-events/) |
| N16 | [MSL — About MSL](https://www.ukmsl.com/aboutmsl/) |
| N17 | [native — Events & Ticketing (knowledge base)](https://knowledge.native.fm/kb/en/events-ticketing-227940) |
| N22 | [JPN — Queima das Fitas: no Queimódromo, pagamentos com pulseira (2023)](https://www.jpn.up.pt/2023/04/14/queima-das-fitas-no-queimodromo-pagamentos-vao-ser-feitos-com-uma-pulseira/) |
| N24 | [Zpos — Festivais](https://en.zpos.pt/festivals/) |
| N34 | [Queue-it — Pricing](https://queue-it.com/pricing/) |

### 9.5 Caso de referência: Queima das Fitas (consultadas em 2026-09-24)

| ID | Fonte |
|---|---|
| F4 | [JPN — "Pantones da Queima": estudantes vestem diferentes cores para dar assistência (2023)](https://www.jpn.up.pt/2023/05/15/pantones-da-queima-estudantes-vestem-diferentes-cores-para-dar-assistencia-no-recinto/) |
| F5 | [24 Notícias — Queima do Porto regressa mais segura e responsável (2022)](https://24noticias.sapo.pt/atualidade/artigos/as-excecoes-nao-marcam-o-que-e-a-queima-do-porto-dois-anos-depois-a-festa-dos-estudantes-regressa-com-vontade-de-ser-mais-segura-e-responsavel) |

---

## 10. Como usei IA nesta pesquisa

### 10.1 Ferramenta e papel

| Item | Descrição |
|---|---|
| Ferramenta | **Claude** (Anthropic), com pesquisa na web, leitura de páginas e acesso de leitura à pasta local do projeto |
| Papel da IA | Ler o repositório, extrair a lista de componentes do produto, procurar alternativas para cada um e organizar a comparação |
| Papel meu | Pedir a iteração com base no repositório e a separação entre documento geral e anexo específico; rever o resultado |
| O que a IA **não** fez | Não alterou o repositório, não testou produtos, não gerou respostas de utilizadores |

### 10.2 O que foi feito nesta iteração

| Passo | Como | Resultado |
|---|---|---|
| 1. Ler o repositório | A IA leu o Problem and Opportunity Report, o guião do inquérito e o histórico de commits do clone local | Lista de componentes C1–C11 (secção 1), cada um com a sua origem no repositório |
| 2. Reaproveitar a pesquisa anterior | A IA reutilizou as fichas relevantes das duas rondas anteriores (segurança, 2026-09-24; mercado geral, 2026-10-01) | Fontes `M#`, `N#` e `F#` (secções 9.3 a 9.5) |
| 3. Pesquisa nova | 13 pesquisas na web sobre: apps multi-festival em Portugal, apps oficiais das Queimas, Woov, LineUpr, mapas de recinto, filas e tempos de espera, assistentes de IA para festivais, transportes da STCP | 10 fontes novas (`P1`–`P10`) |
| 4. Leitura das páginas | A IA abriu 11 páginas e extraiu factos concretos (preços, funcionalidades, datas) | Tabelas da secção 2 |
| 5. Síntese | Matriz por componente, diferenciação, ameaças e ligação às perguntas do inquérito | Secções 3 a 8 |

### 10.3 Pesquisas sem resultado útil

| Pesquisa | O que aconteceu | Como ficou no documento |
|---|---|---|
| App oficial da Queima de Coimbra em 2026 | Sem resultados relevantes | Não afirmamos nada sobre essa app |
| App oficial do Enterro da Gata ou de semanas académicas | Sem resultados relevantes | Não incluído |
| Novidades da APP FAP em 2026 (planta, avisos) | Só queixas de consumidores e notícias já conhecidas | Mantém-se a descrição da página da FAP |
| Preços da Woov, MapsPeople e 42Chat | Não públicos | "Preço não público" |
| Greencopper e Crowd Connected | Só páginas antigas ou indiretas | Greencopper não incluído; Crowd Connected citado através da Aloompa (P5) |

### 10.4 Regras e limitações

| Regra ou limitação | Como foi tratada |
|---|---|
| As páginas foram lidas através de um resumo automático | Os números devem ser confirmados na fonte antes de irem para o relatório final |
| Duas das fontes novas são blogs de fornecedores (P8, P10) e cinco são páginas comerciais (P3–P7) | Indicado na coluna "Tipo" e junto de cada uso |
| A IA pode interpretar mal uma página | "❌" quer dizer "não encontrado", não "não existe" |
| A avaliação "alvo do MVP" na matriz é intenção, não facto | Indicado na legenda da matriz |
| Nenhum dado de utilizadores vem da IA | As hipóteses ficam para o inquérito e a entrevista (secção 8) |

