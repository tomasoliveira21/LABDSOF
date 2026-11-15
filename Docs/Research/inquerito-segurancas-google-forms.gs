/**
 * Creates the security-staff questionnaire in Google Forms, with a response
 * sheet in Google Sheets.
 *
 * Respondents: people who did security (private company, volunteer, team lead,
 * PCR) at past Queimas or other academic festivals. It replaces the planned
 * security-volunteer interview: a form is easier to get answered than a meeting.
 *
 * The questions are in Portuguese on purpose: the respondents are Portuguese.
 * Comments in this file are in English, like the rest of the repository.
 *
 * Goal: bring primary data from the event team's side (the student survey only
 * covers participants).
 *   A4 · A help request through the app reaches the right team faster  → questions 5–11, 25, 27
 *   Root cause 4 · Coordination depends on radio and phone, no history  → questions 5, 6, 11, 23
 *   Venue map as the shared reference (A5, survey decision 4)           → questions 12–14
 *   DM-4 · Help categories and the team that handles each one           → questions 15–17
 *   F17 · Entrance status (Entrances.Update), A6                        → questions 18–20
 *   Responder (P5) can use a phone on shift; A8 weak network            → questions 21, 22, 26
 *   Reaction to W2 + W3 and to AI triage (F8: AI only suggests)         → questions 24–28
 *
 * The idea on the last page follows the help-request design of the Sprint A review
 * (15 November 2026, decisions 1 and 8): the participant writes a short description
 * instead of choosing a category, the AI suggests category and priority (not the
 * team), Theft is now a Safety case and Infrastructure is a new category.
 *
 * Questions about what happens today come before the product idea, so the idea
 * does not influence them. Every question is closed, with an "Outro" (other) option
 * where the choices may not fit, so it takes about 6 minutes; only the last one,
 * an optional comment, is free text.
 * Only people who worked at the entrances see the entrance page.
 *
 * How to use:
 *   1. Open https://script.google.com and create a "New project"
 *      (or add a new file to a project you already have).
 *   2. Paste this whole file.
 *   3. Choose the function criarInqueritoSegurancas and click "Run".
 *   4. The links to the form and to the responses appear in the "Execution log".
 */

function criarInqueritoSegurancas() {
  const TITULO = 'Segurança em festivais académicos: como funciona por dentro?';

  const DESCRICAO =
    'Somos estudantes do mestrado em Engenharia Informática do ISEP e, na unidade curricular LABDSOF, ' +
    'estamos a estudar como funciona a coordenação de ocorrências em festivais académicos grandes, ' +
    'como a Queima das Fitas.\n\n' +
    'Este questionário é para quem fez segurança numa edição anterior. Queremos perceber como o trabalho ' +
    'é feito hoje, a partir da tua experiência. Não há respostas certas nem erradas, e não estamos a avaliar ' +
    'o teu trabalho nem a organização.\n\n' +
    'Demora cerca de 6 minutos: quase tudo é de escolha. É anónimo: não recolhemos o teu nome, o teu email nem a empresa onde ' +
    'trabalhaste. Não escrevas nada confidencial nem dados de pessoas que tenhas ajudado. ' +
    'As respostas são usadas apenas de forma agregada, num trabalho académico.';

  const form = FormApp.create(TITULO)
    .setDescription(DESCRICAO)
    .setProgressBar(true)
    .setCollectEmail(false)
    .setLimitOneResponsePerUser(false)
    .setConfirmationMessage(
      'Obrigado! A tua resposta ajuda-nos muito. ' +
      'Se conheceres mais alguém que tenha feito segurança, enfermagem ou PCR na Queima, partilha este link com essa pessoa.'
    );

  const FREQUENCIA = ['Nunca', 'Algumas vezes na semana', 'Todas as noites', 'Várias vezes por noite'];

  // ---------- Page 1 · Profile ----------
  form.addSectionHeaderItem()
    .setTitle('Sobre ti');

  form.addCheckboxItem()
    .setTitle('1. Em que festivais fizeste segurança?')
    .setChoiceValues([
      'Queima das Fitas do Porto',
      'Queima das Fitas de Coimbra',
      'Enterro da Gata (Braga)',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('2. Em quantas edições (anos) fizeste segurança, no total?')
    .setChoiceValues(['1', '2 ou 3', '4 ou mais'])
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('3. Qual era a tua função?')
    .setChoiceValues([
      'Vigilante de uma empresa de segurança privada',
      'Voluntário',
      'Chefe de equipa ou supervisor',
      'Elemento da PCR (comissão de prevenção de riscos)',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addCheckboxItem()
    .setTitle('4. Onde estavas colocado durante a noite?')
    .setHelpText('Escolhe todas as que se aplicam.')
    .setChoiceValues([
      'Entradas / controlo de acessos',
      'Dentro do recinto',
      'Frente de palco',
      'Torre de vigia',
      'Perímetro exterior ou saídas',
      'Sala de controlo / coordenação',
    ])
    .showOtherOption(true)
    .setRequired(true);

  // ---------- Page 2 · How a help request arrives today ----------
  form.addPageBreakItem()
    .setTitle('Como chega hoje um pedido de ajuda')
    .setHelpText('Pensa nas noites em que trabalhaste.');

  form.addCheckboxItem()
    .setTitle('5. Por onde te chegavam os pedidos de ajuda e as ocorrências?')
    .setHelpText('Escolhe todas as que se aplicam.')
    .setChoiceValues([
      'A própria pessoa vinha ter comigo',
      'Um amigo da pessoa vinha ter comigo',
      'Pelo rádio',
      'Por chamada de telemóvel',
      'Por WhatsApp ou outra app de mensagens',
      'Pelas câmaras ou pela sala de controlo',
      'Pela torre de vigia',
      'Pelo staff dos bares ou barracas',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('6. Qual era o principal meio de comunicação dentro da tua equipa?')
    .setChoiceValues([
      'Rádio',
      'Chamada de telemóvel',
      'WhatsApp ou outra app de mensagens',
      'Pessoalmente',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('7. Havia uma sala de controlo ou um ponto central que recebia as ocorrências e decidia quem ia ao local?')
    .setChoiceValues(['Sim', 'Não', 'Não sei'])
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('8. Quando havia várias ocorrências ao mesmo tempo, quem decidia o que era mais urgente?')
    .setChoiceValues([
      'A sala de controlo ou a coordenação',
      'O chefe de equipa',
      'Cada segurança, no local',
      'Não havia um critério claro',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('9. Quando a ocorrência era de saúde, quanto tempo demorava, normalmente, até a equipa de saúde chegar à pessoa?')
    .setChoiceValues([
      'Menos de 5 minutos',
      '5 a 10 minutos',
      '10 a 20 minutos',
      'Mais de 20 minutos',
      'Não sei',
    ])
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('10. Com que equipa era mais difícil comunicar durante a noite?')
    .setChoiceValues([
      'Enfermagem / posto médico',
      'PCR',
      'PSP',
      'INEM ou bombeiros',
      'Organização (ex.: FAP)',
      'Nenhuma, funcionava bem com todas',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addCheckboxItem()
    .setTitle('11. Pensa nas ocorrências de que te lembras. O que mais atrasava a resposta?')
    .setHelpText('Escolhe até 2.')
    .setChoiceValues([
      'Encontrar a pessoa ou perceber onde era',
      'Atravessar a multidão',
      'Esperar pela equipa certa (ex.: saúde, PSP)',
      'Rádio ocupado ou não se ouvia',
      'Informação incompleta ou errada sobre o que se passava',
      'Não se sabia quem devia ir',
      'Nada, a resposta era rápida',
    ])
    .setValidation(
      FormApp.createCheckboxValidation()
        .setHelpText('Escolhe no máximo 2.')
        .requireSelectAtMost(2)
        .build()
    )
    .showOtherOption(true)
    .setRequired(true);

  // ---------- Page 3 · Location and types of incident ----------
  form.addPageBreakItem()
    .setTitle('Localização e tipos de ocorrência');

  form.addCheckboxItem()
    .setTitle('12. Quando comunicavas uma ocorrência, como dizias onde ela era?')
    .setHelpText('Escolhe todas as que se aplicam.')
    .setChoiceValues([
      'Pelo nome ou número da zona / setor',
      'Pelo número da torre ou do posto',
      'Por um ponto de referência (palco, barraca, WC, entrada)',
      'Levava a pessoa ou ia lá ter com a equipa',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('13. Havia um mapa do recinto com zonas ou setores que a equipa usava?')
    .setChoiceValues([
      'Sim, e todos tínhamos acesso',
      'Sim, mas só a coordenação o tinha',
      'Não',
      'Não sei',
    ])
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('14. Acontecia uma equipa demorar a encontrar a pessoa ou ir ao sítio errado?')
    .setChoiceValues(['Muitas vezes', 'Às vezes', 'Raramente', 'Nunca', 'Não sei'])
    .setRequired(true);

  form.addGridItem()
    .setTitle('15. Com que frequência acontecia cada tipo de ocorrência?')
    .setRows([
      'Saúde (alguém mal, intoxicação, ferimento)',
      'Agressões, brigas ou ameaças',
      'Assédio',
      'Pessoa perdida ou separada do grupo',
      'Furto ou roubo',
      'Problemas nas filas (empurrões, esmagamento)',
      'Objetos perdidos',
      'Infraestrutura danificada (barreira caída, estrutura danificada, cabo exposto)',
    ])
    .setColumns(FREQUENCIA)
    .setRequired(true);

  form.addCheckboxItem()
    .setTitle('16. Havia outros tipos de ocorrência frequentes, além dos da pergunta anterior?')
    .setHelpText('Escolhe todas as que se aplicam.')
    .setChoiceValues([
      'Não, a lista cobre tudo',
      'Pessoas a tentar entrar sem bilhete ou pulseira',
      'Consumo ou venda de droga',
      'Menores de idade',
      'Vandalismo',
      'Pessoas sem bateria ou sem forma de contactar os amigos',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('17. Numa noite cheia, mais ou menos quantas ocorrências havia no recinto todo?')
    .setChoiceValues(['Menos de 10', '10 a 30', '30 a 60', 'Mais de 60', 'Não sei'])
    .setRequired(true);

  const qEntradas = form.addMultipleChoiceItem()
    .setTitle('Trabalhaste alguma vez nas entradas ou no controlo de acessos?')
    .setRequired(true);

  // ---------- Page 4 · Entrances (only for who worked there) ----------
  const paginaEntradas = form.addPageBreakItem()
    .setTitle('Entradas e filas');

  form.addCheckboxItem()
    .setTitle('18. Como sabias que uma entrada estava a ficar congestionada?')
    .setHelpText('Escolhe todas as que se aplicam.')
    .setChoiceValues([
      'Pelo rádio',
      'Pela sala de controlo ou pelas câmaras',
      'Vendo a fila',
      'Os participantes diziam-nos',
      'Não havia forma de saber',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('19. A informação sobre o estado das entradas (filas, portas abertas ou fechadas) chegava às pessoas na fila?')
    .setChoiceValues([
      'Sim, por anúncios ou pelo staff',
      'Às vezes',
      'Não',
      'Não sei',
    ])
    .setRequired(true);

  form.addCheckboxItem()
    .setTitle('20. Que problemas viste nas filas?')
    .setHelpText('Escolhe todas as que se aplicam.')
    .setChoiceValues([
      'Esperas de mais de 1 hora',
      'Empurrões ou esmagamento',
      'Ataques de pânico ou desmaios',
      'Pessoas a forçar ou saltar a entrada',
      'Problemas com bilhetes ou pulseiras',
      'Nenhum',
    ])
    .showOtherOption(true)
    .setRequired(true);

  // ---------- Page 5 · Phone and records ----------
  const paginaTelemovel = form.addPageBreakItem()
    .setTitle('Telemóvel e registos');

  form.addMultipleChoiceItem()
    .setTitle('21. Durante o turno, podias usar o telemóvel?')
    .setChoiceValues([
      'Sim, livremente',
      'Sim, mas só para trabalho',
      'Não era permitido',
      'Não tinha telemóvel comigo',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addScaleItem()
    .setTitle('22. Como funcionava a rede móvel no recinto à hora de ponta?')
    .setBounds(1, 5)
    .setLabels('Não funcionava', 'Funcionava bem')
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('23. As ocorrências ficavam registadas em algum sítio?')
    .setChoiceValues([
      'Sim, num sistema ou numa app',
      'Sim, em papel (folha de ocorrências)',
      'Só num relatório no fim da noite',
      'Não',
      'Não sei',
    ])
    .showOtherOption(true)
    .setRequired(true);

  // ---------- Page 6 · Reaction to the idea (always last) ----------
  form.addPageBreakItem()
    .setTitle('Uma ideia')
    .setHelpText(
      'Estamos a pensar numa aplicação em que o participante pede ajuda pelo telemóvel: escreve em poucas ' +
      'palavras o que se passa e marca onde está no mapa do recinto. O sistema sugere o tipo de pedido ' +
      '(saúde, segurança, pessoa perdida, infraestrutura danificada, outro) e a prioridade. Uma pessoa na ' +
      'sala de controlo confirma ou muda essa sugestão e encaminha o pedido para a equipa certa. Um elemento ' +
      'dessa equipa aceita-o no telemóvel e vai ao local.'
    );

  form.addScaleItem()
    .setTitle('24. Isto ajudaria ou atrapalharia o trabalho da segurança?')
    .setBounds(1, 5)
    .setLabels('Atrapalharia muito', 'Ajudaria muito')
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('25. Comparado com hoje, um pedido feito assim chegaria à equipa certa…')
    .setChoiceValues([
      'Muito mais depressa',
      'Um pouco mais depressa',
      'Ao mesmo tempo',
      'Mais devagar',
      'Não sei',
    ])
    .setRequired(true);

  form.addCheckboxItem()
    .setTitle('26. Que problemas vês nesta ideia?')
    .setHelpText('Escolhe até 3.')
    .setChoiceValues([
      'Falsos alarmes ou brincadeiras',
      'Escrever o que se passa demora, para quem está aflito ou mal',
      'Demasiados pedidos para a equipa tratar',
      'Localização errada ou pouco precisa',
      'Rede fraca ou bateria',
      'Não consigo olhar para o telemóvel durante o turno',
      'As pessoas esperarem uma resposta imediata, ou deixarem de ligar para o 112',
      'Nenhum',
    ])
    .setValidation(
      FormApp.createCheckboxValidation()
        .setHelpText('Escolhe no máximo 3.')
        .requireSelectAtMost(3)
        .build()
    )
    .showOtherOption(true)
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('27. Quem deveria receber primeiro um pedido destes?')
    .setChoiceValues([
      'A sala de controlo, que depois o encaminha',
      'Diretamente a equipa mais próxima',
      'As duas ao mesmo tempo',
      'Não sei',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('28. Se o sistema sugerisse automaticamente o tipo e a prioridade de cada pedido, a partir do que a pessoa escreveu, confiarias nessa sugestão?')
    .setChoiceValues([
      'Sim',
      'Só como sugestão: quem decide deve ser uma pessoa',
      'Não',
      'Não sei',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addParagraphTextItem()
    .setTitle('29. Queres acrescentar alguma coisa? (opcional)');

  // Entrance question navigation: only who worked at the entrances sees page 4.
  qEntradas.setChoices([
    qEntradas.createChoice('Sim', paginaEntradas),
    qEntradas.createChoice('Não', paginaTelemovel),
  ]);

  const folha = SpreadsheetApp.create('Respostas · ' + TITULO);
  form.setDestination(FormApp.DestinationType.SPREADSHEET, folha.getId());

  Logger.log('Link para divulgar:   ' + form.getPublishedUrl());
  Logger.log('Editar o formulário:  ' + form.getEditUrl());
  Logger.log('Folha de respostas:   ' + folha.getUrl());
}
