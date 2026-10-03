# Problem and Opportunity Report

**Projeto:** Plataforma de coordenação para eventos académicos de grande escala
**Caso-piloto:** Queima das Fitas do Porto (Queimódromo, 8 noites, maio)
**Unidade curricular:** LABDSOF 2026/27, Sprint 1
**Direção do enunciado:** 4.3 *Local Event Coordination Platform*, aplicada a um evento com multidões e cortes de trânsito
**Versão:** revista com o questionário de alinhamento do grupo e as decisões do Tiago (outubro de 2026)

---

## Visão do produto (contexto)

Propomos uma **plataforma construída de raiz** para coordenar eventos académicos de grande escala. É uma única aplicação que reúne **vários eventos** (a Queima do Porto, a de Coimbra, uma semana académica). Cada evento tem um **owner e uma equipa** que o configuram e operam de forma independente.

- **Para o participante:** uma aplicação web (PWA) onde escolhe o evento e encontra a informação e a ajuda de que precisa durante a noite.
- **Para cada equipa de evento:** um backoffice onde configura o seu evento (por exemplo, a planta do recinto e os transportes) e recebe e trata as ocorrências.
- **Permissões por evento:** cada equipa só vê e altera o seu próprio evento.

**Fora de âmbito:** a capacidade física das entradas (quantas portas existem e como está desenhado o funil). Isso resolve-se com infraestrutura, não com software.

---

## 1. Target Community

### Comunidade

Comunidade académica que organiza e participa em grandes festas estudantis em Portugal: Queimas das Fitas, semanas académicas e eventos semelhantes. No **caso-piloto**, a Queima das Fitas do Porto, são cerca de **250 mil entradas em 8 noites**, numa academia com mais de 80 mil estudantes.

A plataforma não é feita para um único evento. Cada evento entra na plataforma com a sua própria equipa, por isso a comunidade cresce evento a evento, sem ser preciso redesenhar o produto.

### Utilizadores

| Grupo | Perfil | Contexto de uso |
|---|---|---|
| **Equipa do evento** (utilizador principal) | Owner do evento (por exemplo a FAP) e a sua equipa: organização, comissão de Prevenção de Comportamentos de Risco (PCR), coordenação de segurança | Antes do evento, configura-o. Durante o evento, está na sala de controlo e no terreno, precisa de visão do recinto e de responder depressa. |
| **Participantes** (utilizador principal) | Estudantes de 18 a 25 anos, sobretudo em grupo | De noite, com o telemóvel em mão, bateria limitada, rede congestionada e muitas vezes sob efeito de álcool. Precisam de informação curta e imediata: onde está cada coisa, que transporte apanhar, onde pedir ajuda. |
| **Parceiros operacionais** (utilizadores secundários) | STCP, Metro do Porto, PSP, Proteção Civil | São fonte ou destino de informação: horários reforçados, cortes de trânsito, incidentes. |

Os dois utilizadores principais têm **o mesmo peso**, por escolha unânime do grupo. Cada um tem a sua interface: o backoffice para a equipa do evento e a aplicação web para o participante.

### Porquê este grupo

- O problema concentra-se em eventos previsíveis, recorrentes e de grande escala, onde uma falha tem consequências de segurança.
- A organização de cada evento tem simultaneamente a necessidade, o orçamento (35% do orçamento da Queima do Porto em 2026 foi para segurança) e o canal até aos estudantes (bilhética, pulseiras, redes sociais).
- O formato repete-se em várias cidades e todos os anos, o que justifica uma plataforma multi-evento em vez de uma solução feita à medida de um só evento.

---

## 2. Problem Statement

### Formulação

> **A organização de festas académicas de grande escala e os seus participantes** enfrentam **informação operacional fragmentada e desatualizada** **quando** dezenas de milhares de pessoas circulam por noite entre o recinto, as entradas e os transportes, **o que causa** respostas lentas a incidentes, filas longas e mal distribuídas, e participantes sem saber para onde ir, como voltar ou a quem pedir ajuda.

Do ponto de vista do participante:

> **Estudantes que vão à Queima** não têm acesso a informação fiável e em tempo real **quando** estão no recinto ou à volta dele de madrugada, **o que leva** a esperas longas e a decisões arriscadas (separar-se do grupo, voltar a pé, conduzir).

### Causa raiz

1. **Não existe uma fonte única de informação operacional.** Organização, PCR, segurança, PSP, transportes e Câmara comunicam cada um pelo seu canal: rádio, site, Instagram, comunicados, a plataforma de trânsito do município. Ninguém tem a visão completa.
2. **A comunicação é estática.** Horários, cortes de trânsito e regras são publicados antes do evento. O que muda durante a noite (uma porta congestionada, um vaivém atrasado, uma zona a evitar) não chega ao participante.
3. **Não há uma referência espacial do recinto partilhada.** Quando existe, a planta é uma imagem publicada nas redes sociais, que não se atualiza e não serve para indicar onde está cada coisa nem onde aconteceu uma ocorrência.
4. **A coordenação de ocorrências depende de rádio e telefone.** Isto não deixa histórico estruturado nem permite priorizar e encaminhar com rapidez. É uma hipótese a confirmar na entrevista ao voluntário da segurança.
5. **Processos lentos feitos na própria porta.** Em 2023, o caos na entrada foi causado pela troca do bilhete pela pulseira no momento de entrar.

### Oportunidade

Cada **equipa de configuração do evento pode carregar a planta do recinto** na plataforma. A planta passa a ser a referência comum: o participante orienta-se por ela, e a equipa usa-a para situar pedidos de ajuda e informação. O mesmo modelo aplica-se a qualquer evento, porque é cada equipa que carrega e mantém a sua planta.

### O que é e o que não é o problema

| É o problema | Não é o problema |
|---|---|
| Processos de acesso lentos e mal distribuídos entre portas | Falta de capacidade física das entradas (é infraestrutura, fica fora de âmbito) |
| Informação dispersa e desatualizada durante o evento | Falta de segurança: o investimento já é recorde (mais de 500 efetivos por noite, mais de 100 câmaras) |
| Coordenação de ocorrências sem visão comum | Comportamentos de risco em si (álcool, violência). São culturais. O produto pode encurtar o tempo de resposta, mas não elimina a causa. |
| | "Não haver app": a FAP já tem uma, mas ela não cobre a operação do evento |

### Consequências de não resolver

- Episódios de esmagamento e ataques de pânico nas filas, como os de 2022 e 2023.
- Mais tempo até a assistência chegar a quem precisa.
- Perda de reputação para a organização, com queixas públicas e cobertura negativa na imprensa.
- Regressos de madrugada sem informação, com mais risco de condução sob o efeito do álcool e de deslocações inseguras.

---

## 3. Stakeholder Map

| Stakeholder | Papel | Interesse | Influência | Posição |
|---|---|---|---|---|
| Federação académica (FAP) | Organizador, cliente e decisor | Alto | Alta | A favor |
| Comissão PCR e equipas de apoio | Utilizador operacional | Alto | Média | A favor |
| Estudantes participantes | Utilizadores finais | Médio | Baixa individualmente, alta coletivamente (redes sociais, queixas) | Neutros. Adotam se for útil e imediato. |
| Segurança privada | Execução no terreno | Médio | Média | Neutra |
| PSP e Proteção Civil | Ordem pública e emergência | Alto | Alta | Neutras ou céticas (têm sistemas e protocolos próprios) |
| INEM e bombeiros | Resposta médica | Alto | Média | Neutros |
| STCP e Metro do Porto | Transporte | Médio | Média | A favor, se não exigir integração pesada |
| Câmara Municipal (Porto e Matosinhos) | Licenciamento e trânsito | Médio | Alta | Neutra (já tem plataforma de trânsito própria) |
| Fornecedor da app atual da federação | Concorrente ou parceiro | Alto | Média | Contra, se se sentir substituído |
| Bilhética (BOL, FNAC) | Parceiro necessário para validar acessos | Médio | Alta | Neutra |
| Instituições de ensino (ISEP, universidades) | Influenciadores institucionais, canal de comunicação com os estudantes | Médio (bem-estar e segurança dos alunos) | Média | A favor |
| Associações de estudantes de cada faculdade | Co-organizadoras (cortejo, barracas) e canal direto para os estudantes | Alto | Média | A favor, se tiverem voz na configuração |
| Patrocinadores e concessionários | Financiam o evento | Baixo | Média | A favor, pela visibilidade |
| CNPD (proteção de dados) | Regulador | Baixo, sobe se houver dados de localização | Alta | Neutra, mas é uma restrição |
| Pais e famílias | Influenciadores | Alto na segurança | Baixa | A favor |

### Matriz influência × interesse

- **Gerir de perto** (influência alta, interesse alto): FAP, PSP e Proteção Civil.
- **Manter satisfeitos** (influência alta, interesse médio ou baixo): Câmara, bilhética, CNPD.
- **Manter informados** (influência baixa ou média, interesse alto): PCR, INEM, famílias, fornecedor da app atual, associações de estudantes de cada faculdade.
- **Envolver como canal:** instituições de ensino (divulgação e inquéritos).
- **Monitorizar:** patrocinadores, concessionários.

### Conflitos de interesse

1. **Plataforma nova vs. fornecedor atual (o mais crítico, segundo o grupo).** O fornecedor da app da federação pode ver o produto como ameaça e não colaborar na integração. Mitigação: entrar como módulo complementar, por link ou QR, sem exigir alterações à app existente.
2. **Transparência vs. reputação.** Mostrar tempos de fila reais ajuda o participante, mas expõe falhas da organização.
3. **Monitorização vs. privacidade.** Densidade e localização ajudam a operação, mas exigem minimização de dados e consentimento (RGPD).
4. **Receita vs. segurança.** Mostrar a lotação em tempo real pode pressionar a organização a limitar entradas, o que reduz receita.
5. **Mandatos anuais.** As direções académicas mudam todos os anos, o que põe em risco a continuidade da adoção.

---

## 4. Problem Evidence

### Evidência forte (factos públicos verificáveis)

| # | Evidência | Fonte |
|---|---|---|
| E1 | Cerca de 250 mil participantes previstos em 8 dias (2025 e 2026) | [Jornal da Maia](https://www.jornaldamaia.pt/queima-das-fitas-do-porto-2025-reforca-seguranca-e-prevencao/), [Observador](https://observador.pt/2026/04/30/queima-das-fitas-do-porto-arranca-com-maior-investimento-em-seguranca-de-sempre-diz-fap/) |
| E2 | A segurança absorve 35% do orçamento: mais de 500 efetivos por dia, 12 torres de vigia, mais de 100 câmaras, PCR com 90 pessoas | [JN](https://www.jn.pt/pais/artigo/organizacao-investe-35-das-verbas-para-garantir-seguranca-na-queima-do-porto/18077018), [Rádio Festival](https://radiofestival.com.pt/noticia/2541955/queima-do-porto-2026-seguranca-recebe-investimento-recorde-e-absorve-35-do-orcamento) |
| E3 | 2022 e 2023: filas de mais de 500 m, esperas de mais de 3h, relatos de esmagamento e ataques de pânico, queixas por "falta de informação e sinalética" | [Renascença](https://rr.pt/noticia/pais/2022/05/05/filas-para-a-queima-das-fitas-do-porto-achei-que-ia-colapsar-a-ser-esmagada-e-quase-a-sufocar/283010/), [Público](https://www.publico.pt/2023/05/08/p3/noticia/primeira-noite-caotica-queima-fitas-porto-voltou-normal-2048919) |
| E4 | A causa identificada em 2023 foi a troca do bilhete pela pulseira à entrada. A organização alterou o recinto depois das queixas. | [JN](https://www.jn.pt/pais/artigo/horas-na-fila-e-nao-entraram-troca-de-bilhete-por-pulseira-gera-confusao-no-queimodromo/16310596), [Notícias ao Minuto](https://www.noticiasaominuto.com/pais/2315392/alteracoes-no-queimodromo-do-porto-apos-queixas-na-queima-das-fitas) |
| E5 | Operação especial de transportes (vaivém a cada 15 min ou menos, reforço de metro e autocarro) e cortes de trânsito, comunicados por canais separados | [Andante](https://andante.pt/noticias/operacoes-especiais-queima-fitas-2025/), [STCP](https://www.stcp.pt/pt/noticias/stcp-com-servico-dedicado-para-a-queima-das-fitas-do-porto/) |
| E6 | A app FAP existe e cobre bilhetes, descontos e desporto, mas não a operação do evento | [FAP](https://www.fap.pt/noticias/app-fap-fap-no-teu-bolso) |
| E7 | A estimativa de densidade de multidões a partir de uma app de festival teve correlação acima de 0,8 com a medição em vídeo (viabilidade técnica) | [arXiv 1306.3133](https://arxiv.org/pdf/1306.3133) |

### Evidência média ou fraca

- Incidentes graves em 2026: uma queixa de violação no Queimódromo do Porto e duas em Coimbra. Mostram a necessidade de canais de pedido de ajuda rápidos, mas não provam que uma app teria evitado nada.
- Taxas de adoção de apps de eventos entre 55% e 65% com promoção. Os números vêm de blogs de fornecedores (Cvent, Lineupr), que têm interesse comercial.
- A ideia de que "os estudantes querem informação em tempo real" é senso comum e ainda não foi validada.

### Lacunas e plano de validação primária

O grupo decidiu avançar com dois métodos de validação:

| Lacuna | Método |
|---|---|
| Que informação os estudantes procuram durante a noite, se a falta de informação continua a ser um problema e se usariam uma aplicação para isso | **Inquérito online a estudantes** (meta: 100 ou mais respostas) |
| Como a segurança coordena hoje as ocorrências, que canais usa e onde estão os atrasos | **Entrevista a um voluntário da segurança** da Queima |

**Limitação assumida:** o grupo não tem acesso direto à organização (FAP) nem aos seus dados internos de filas e incidentes. A visão da operação vem da entrevista ao voluntário e dos dados públicos. Seguindo o enunciado, nenhum dado de pesquisa com utilizadores neste relatório foi inventado. As limitações ficam documentadas.

---

## 5. Assumptions and Risks

### Assunções críticas

| # | Assunção | Risco | Facilidade de validação |
|---|---|---|---|
| A1 | As organizações aceitam configurar e manter o seu evento na plataforma (planta, transportes, equipa) | Alto | Média (entrevista e conversa com organizações) |
| A2 | Os participantes consultam a aplicação durante a noite, mesmo em condições adversas (álcool, bateria, ruído) | Alto | Média (inquérito e protótipo) |
| A3 | Os participantes adotam uma aplicação comum a vários eventos, acedida por link ou QR, em vez de uma app por evento | Alto | Fácil (inquérito) |
| A4 | Pedidos de ajuda feitos pela aplicação chegam à equipa certa mais depressa do que pelos canais atuais | Alto | Média (entrevista ao voluntário e simulação) |
| A5 | Cada organização tem uma planta do recinto utilizável (imagem ou PDF) | Médio | Fácil |
| A6 | A informação em tempo real (transportes, filas) pode ser introduzida ou estimada pela equipa do evento, sem integração direta com os operadores | Médio | Média |
| A7 | As funcionalidades com IA são mais úteis do que uma alternativa sem IA e não induzem os utilizadores em erro | Médio | Fácil (dataset simulado e comparação com uma alternativa sem IA) |
| A8 | A rede móvel aguenta o pico, ou a aplicação funciona em modo degradado ou offline | Médio | Fácil (testes e relatos) |
| A9 | O mesmo produto serve eventos diferentes apenas com configuração, sem desenvolvimento à medida | Médio | Média |

As assunções mais críticas são **A2, A3 e A4**. Se os participantes não usarem a aplicação durante a noite, ou se os pedidos de ajuda não ficarem mais rápidos, o produto perde a razão de existir. O inquérito valida A2 e A3; a entrevista ao voluntário da segurança valida A4.

### Riscos

**Mercado e adoção**
- O uso concentra-se em poucos dias por ano por evento, com pouca retenção. A plataforma multi-evento atenua isto, porque o mesmo utilizador volta noutros eventos.
- Convencer os estudantes a usar mais uma aplicação. Mitigação: acesso por link ou QR, sem instalação obrigatória.
- O fornecedor da app atual de cada federação pode copiar as funcionalidades.
- As direções académicas mudam todos os anos, o que torna incerta a continuidade de cada evento na plataforma.

**Técnicos**
- **Permissões entre eventos:** um erro no sistema de autorizações pode expor dados ou ocorrências de um evento à equipa de outro. Exige testes automáticos dedicados a este isolamento.
- Picos de carga de dezenas de milhares de pedidos em minutos, por exemplo no fim de um concerto.
- Rede móvel congestionada no recinto: exige cache, modo offline e mensagens curtas.
- Informação desatualizada ou errada pode piorar a situação (por exemplo, um transporte que já não passa). Exige indicação clara de quando cada dado foi atualizado.
- A localização do telemóvel dentro de um recinto cheio é imprecisa. O participante deve poder indicar a localização na planta.

**Execução**
- **Âmbito grande para 3 sprints.** Mitigação: priorizar no MVP os pedidos de ajuda, a planta e a informação de transportes. O resto entra por ordem de valor.
- Acesso limitado à organização real durante o semestre. Mitiga-se com a entrevista ao voluntário, dados públicos e simulação, documentando a limitação.

**Regulatórios e éticos**
- RGPD: a localização e os pedidos de ajuda (sobretudo de saúde) são dados sensíveis. Aplicam-se consentimento explícito, minimização e retenção curta.
- Um botão de pedido de ajuda cria expectativa de resposta. A aplicação tem de deixar claro que não substitui o 112.
- Conteúdo gerado por IA não pode ser apresentado como facto verificado, como exige o enunciado (5.6.2).

---

## 3 perguntas-chave antes da fase de solução

1. **Os estudantes usariam uma aplicação durante a noite** e que informação procurariam primeiro? (inquérito)
2. **Como chega hoje um pedido de ajuda à equipa certa** e quanto tempo demora? (entrevista ao voluntário da segurança)
3. **As organizações dos eventos estariam dispostas a configurar e manter o seu evento** numa plataforma partilhada?
