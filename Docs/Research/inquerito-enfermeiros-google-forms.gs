/**
 * Creates the questionnaire for nurses of the support teams in Google Forms,
 * with a response sheet in Google Sheets.
 *
 * Respondents: nurses (and other health staff: nursing students, first-aiders,
 * doctors) who were in the first-aid post or the health team of past Queimas
 * or other academic festivals. A form is easier to get answered than a meeting.
 *
 * The questions are in Portuguese on purpose: the respondents are Portuguese.
 * Comments in this file are in English, like the rest of the repository.
 *
 * Goal: bring primary data from the team that answers Health requests
 * (Nursing in the pilot set-up, features.md §4.4).
 *   A4 · A help request through the app reaches the right team faster  → questions 6–11, 30, 32
 *   Root cause 4 · Coordination depends on radio and phone, no history  → questions 6, 7, 11, 27
 *   Triage: what information sets urgency today (F8, fallback D1)       → questions 12–15, 33
 *   DM-4 · Health as one category; cases that belong to other teams     → questions 16–19
 *   Venue map and first-aid points (survey decision 4)                  → questions 20–22
 *   Group separation after care (survey Q3: 47 % got separated)         → questions 23, 24
 *   Responder (P5) can use a phone on shift; A8 weak network            → questions 25, 26
 *   Health data is sensitive (GDPR): what other teams may see           → question 28
 *   Reaction to W2 + W3 and to AI triage (AI never lowers a priority)   → questions 29–33
 *
 * The idea on the last page follows the help-request design of the Sprint A review
 * (15 November 2026, decisions 1 and 8): the participant writes a short description
 * instead of choosing a category, the AI suggests category and priority (not the
 * team), Theft is now a Safety case and Infrastructure is a new category.
 * Question 13 (the 3 things that set urgency) also says what that description
 * should contain, as examples for the field.
 *
 * Questions about what happens today come before the product idea, so the idea
 * does not influence them. Every question is closed, with an "Outro" (other) option
 * where the choices may not fit, so it takes about 6 minutes; only the last one,
 * an optional comment, is free text.
 * No question asks for clinical data or for anything that identifies a patient.
 *
 * How to use:
 *   1. Open https://script.google.com and create a "New project"
 *      (or add a new file to a project you already have).
 *   2. Paste this whole file.
 *   3. Choose the function criarInqueritoEnfermeiros and click "Run".
 *   4. The links to the form and to the responses appear in the "Execution log".
 */

function criarInqueritoEnfermeiros() {
  const TITULO = 'Equipas de saúde em festivais académicos: como funciona por dentro?';

  const DESCRICAO =
    'Somos estudantes do mestrado em Engenharia Informática do ISEP e, na unidade curricular LABDSOF, ' +
    'estamos a estudar como funciona a resposta a pedidos de ajuda em festivais académicos grandes, ' +
    'como a Queima das Fitas.\n\n' +
    'Este questionário é para quem esteve na equipa de saúde ou no posto médico numa edição anterior. ' +
    'Queremos perceber como o trabalho é feito hoje, a partir da tua experiência. Não há respostas certas ' +
    'nem erradas, e não estamos a avaliar o teu trabalho nem a organização.\n\n' +
    'Demora cerca de 6 minutos: quase tudo é de escolha. É anónimo: não recolhemos o teu nome nem o teu email. ' +
    'Não precisamos de nenhum dado clínico nem de nada que identifique alguém. As respostas são usadas apenas de forma agregada, num trabalho académico.';

  const form = FormApp.create(TITULO)
    .setDescription(DESCRICAO)
    .setProgressBar(true)
    .setCollectEmail(false)
    .setLimitOneResponsePerUser(false)
    .setConfirmationMessage(
      'Obrigado! A tua resposta ajuda-nos muito. ' +
      'Se conheceres mais alguém que tenha feito enfermagem, segurança ou PCR na Queima, partilha este link com essa pessoa.'
    );

  const FREQUENCIA = ['Nunca', 'Algumas vezes na semana', 'Todas as noites', 'Várias vezes por noite'];

  const INFORMACAO_TRIAGEM = [
    'Se a pessoa está consciente',
    'Se respira normalmente',
    'Se está a sangrar',
    'O que bebeu ou tomou',
    'Idade aproximada',
    'Onde está, exatamente',
    'Se tem alguém com ela',
  ];

  const CANAIS = [
    'A pessoa chegava ao posto pelo próprio pé',
    'Trazida por amigos',
    'Trazida pela segurança ou pela PCR',
    'Chamada por rádio',
    'Chamada por telemóvel',
    'Alguém vinha ao posto avisar',
  ];

  // ---------- Page 1 · Profile ----------
  form.addSectionHeaderItem()
    .setTitle('Sobre ti');

  form.addCheckboxItem()
    .setTitle('1. Em que festivais estiveste na equipa de saúde?')
    .setChoiceValues([
      'Queima das Fitas do Porto',
      'Queima das Fitas de Coimbra',
      'Enterro da Gata (Braga)',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('2. Em quantas edições (anos) estiveste, no total?')
    .setChoiceValues(['1', '2 ou 3', '4 ou mais'])
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('3. Qual era a tua função?')
    .setChoiceValues([
      'Enfermeiro(a)',
      'Estudante de enfermagem',
      'Socorrista / técnico de emergência',
      'Médico(a)',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('4. Por que tipo de entidade estavas lá?')
    .setChoiceValues([
      'Cruz Vermelha ou outra organização de socorro',
      'Empresa contratada pela organização',
      'Voluntariado da associação ou federação académica',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addCheckboxItem()
    .setTitle('5. Onde estavas durante a noite?')
    .setHelpText('Escolhe todas as que se aplicam.')
    .setChoiceValues([
      'Posto médico fixo',
      'Equipa móvel / apeada no recinto',
      'Ambulância',
      'Junto às entradas',
    ])
    .showOtherOption(true)
    .setRequired(true);

  // ---------- Page 2 · How a case reaches the health team ----------
  form.addPageBreakItem()
    .setTitle('Como chega hoje um caso à equipa de saúde')
    .setHelpText('Pensa nas noites em que trabalhaste.');

  form.addCheckboxItem()
    .setTitle('6. Como é que os casos chegavam até vocês?')
    .setHelpText('Escolhe todas as que se aplicam.')
    .setChoiceValues(CANAIS)
    .showOtherOption(true)
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('7. E qual era a forma mais comum?')
    .setChoiceValues(CANAIS)
    .showOtherOption(true)
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('8. Quando era preciso ir ter com alguém ao recinto, quem decidia quem ia?')
    .setChoiceValues([
      'O coordenador da equipa de saúde',
      'A sala de controlo ou a organização',
      'Quem estivesse disponível no momento',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('9. Desde o alerta, quanto tempo demorava, normalmente, até a equipa chegar à pessoa no recinto?')
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
      'Segurança',
      'PCR',
      'Sala de controlo / organização (ex.: FAP)',
      'INEM ou bombeiros',
      'PSP',
      'Nenhuma, funcionava bem com todas',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addCheckboxItem()
    .setTitle('11. Pensa nos casos de que te lembras. O que mais atrasava a chegada até à pessoa?')
    .setHelpText('Escolhe até 2.')
    .setChoiceValues([
      'Encontrar a pessoa ou perceber onde era',
      'Atravessar a multidão com material ou maca',
      'Esperar que alguém (segurança, sala de controlo) passasse o aviso',
      'Rádio ocupado ou não se ouvia',
      'Informação incompleta ou errada sobre o estado da pessoa',
      'Não havia ninguém da equipa livre',
      'Nada, chegávamos depressa',
    ])
    .setValidation(
      FormApp.createCheckboxValidation()
        .setHelpText('Escolhe no máximo 2.')
        .requireSelectAtMost(2)
        .build()
    )
    .showOtherOption(true)
    .setRequired(true);

  // ---------- Page 3 · Triage and types of case ----------
  form.addPageBreakItem()
    .setTitle('Triagem e tipos de caso');

  form.addCheckboxItem()
    .setTitle('12. Antes de sair do posto para ir ter com alguém, que informação costumavas ter sobre a pessoa?')
    .setHelpText('Escolhe todas as que se aplicam.')
    .setChoiceValues(INFORMACAO_TRIAGEM.concat(['Quase nenhuma']))
    .showOtherOption(true)
    .setRequired(true);

  form.addCheckboxItem()
    .setTitle('13. Quais são as 3 informações mais importantes para saberes a urgência de um caso antes de sair?')
    .setHelpText('Escolhe até 3.')
    .setChoiceValues(INFORMACAO_TRIAGEM)
    .setValidation(
      FormApp.createCheckboxValidation()
        .setHelpText('Escolhe no máximo 3.')
        .requireSelectAtMost(3)
        .build()
    )
    .showOtherOption(true)
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('14. Quando havia vários casos ao mesmo tempo, como decidiam quem era atendido primeiro?')
    .setChoiceValues([
      'Com um protocolo de triagem formal (ex.: Manchester, START)',
      'Com critérios da equipa, não escritos',
      'Por ordem de chegada',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('15. Quando era preciso o INEM ou levar alguém ao hospital, quem fazia a chamada?')
    .setChoiceValues([
      'A equipa de saúde, diretamente',
      'A sala de controlo ou a organização',
      'Dependia do caso',
      'Não sei',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addGridItem()
    .setTitle('16. Com que frequência aparecia cada tipo de caso?')
    .setRows([
      'Intoxicação alcoólica',
      'Outras substâncias',
      'Quedas, ferimentos ou cortes',
      'Desmaios',
      'Crises de ansiedade ou ataques de pânico',
      'Desidratação ou calor',
      'Ferimentos de agressões',
      'Reações alérgicas',
    ])
    .setColumns(FREQUENCIA)
    .setRequired(true);

  form.addCheckboxItem()
    .setTitle('17. Havia outros tipos de caso frequentes, além dos da pergunta anterior?')
    .setHelpText('Escolhe todas as que se aplicam.')
    .setChoiceValues([
      'Não, a lista cobre tudo',
      'Entorses ou fraturas',
      'Queimaduras',
      'Asma ou dificuldade respiratória',
      'Hipoglicemia ou diabetes',
      'Convulsões',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('18. Numa noite cheia, mais ou menos quantas pessoas eram atendidas pela equipa de saúde?')
    .setChoiceValues(['Menos de 10', '10 a 30', '30 a 60', '60 a 100', 'Mais de 100', 'Não sei'])
    .setRequired(true);

  form.addCheckboxItem()
    .setTitle('19. Chegavam ao posto pessoas por motivos que não eram de saúde?')
    .setHelpText('Escolhe todas as que se aplicam.')
    .setChoiceValues([
      'Perdidas do grupo',
      'Sem bateria ou sem forma de contactar os amigos',
      'Vítimas de furto',
      'À procura de um amigo',
      'Não',
    ])
    .showOtherOption(true)
    .setRequired(true);

  // ---------- Page 4 · Location and after care ----------
  form.addPageBreakItem()
    .setTitle('Localização e depois do atendimento');

  form.addCheckboxItem()
    .setTitle('20. Quando vos chamavam, como vos diziam onde estava a pessoa?')
    .setHelpText('Escolhe todas as que se aplicam.')
    .setChoiceValues([
      'Pelo nome ou número da zona / setor',
      'Pelo número da torre ou do posto de segurança',
      'Por um ponto de referência (palco, barraca, WC, entrada)',
      'Alguém vinha ao posto buscar-nos e levava-nos lá',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('21. Havia um mapa do recinto com os postos médicos e os acessos para macas e ambulâncias?')
    .setChoiceValues([
      'Sim, e todos tínhamos acesso',
      'Sim, mas só a coordenação o tinha',
      'Não',
      'Não sei',
    ])
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('22. Acontecia a equipa demorar a encontrar a pessoa, ir ao sítio errado ou não conseguir atravessar a multidão?')
    .setChoiceValues(['Muitas vezes', 'Às vezes', 'Raramente', 'Nunca', 'Não sei'])
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('23. Depois de alguém ser atendido no posto, os amigos sabiam onde a pessoa estava?')
    .setChoiceValues(['Quase sempre', 'Às vezes', 'Raramente', 'Não sei'])
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('24. Acontecia uma pessoa ficar sozinha no posto por não conseguir contactar o grupo?')
    .setChoiceValues(['Muitas vezes', 'Às vezes', 'Raramente', 'Nunca', 'Não sei'])
    .setRequired(true);

  // ---------- Page 5 · Phone and records ----------
  form.addPageBreakItem()
    .setTitle('Telemóvel e registos');

  form.addMultipleChoiceItem()
    .setTitle('25. Durante o turno, podias usar o telemóvel?')
    .setChoiceValues([
      'Sim, livremente',
      'Sim, mas só para trabalho',
      'Não era permitido',
      'Não tinha telemóvel comigo',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addScaleItem()
    .setTitle('26. Como funcionava a rede móvel no recinto à hora de ponta?')
    .setBounds(1, 5)
    .setLabels('Não funcionava', 'Funcionava bem')
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('27. Como ficavam registados os atendimentos?')
    .setChoiceValues([
      'Ficha em papel',
      'Sistema digital da entidade',
      'Não ficavam registados',
      'Não sei',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addCheckboxItem()
    .setTitle('28. Num pedido de saúde, o que é que outras equipas (segurança, sala de controlo, organização) deveriam poder ver?')
    .setHelpText('Escolhe todas as que se aplicam.')
    .setChoiceValues([
      'Só que é um caso de saúde e onde é',
      'A urgência',
      'O tipo de problema (ex.: intoxicação, ferimento)',
      'O que a pessoa escreveu ao pedir ajuda',
      'Nada: só a equipa de saúde deve ver',
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
      'sala de controlo confirma ou muda essa sugestão e encaminha o pedido para a equipa certa, por exemplo ' +
      'a enfermagem nos casos de saúde. Um elemento dessa equipa aceita-o no telemóvel e vai ao local.'
    );

  form.addScaleItem()
    .setTitle('29. Isto ajudaria ou atrapalharia o trabalho da equipa de saúde?')
    .setBounds(1, 5)
    .setLabels('Atrapalharia muito', 'Ajudaria muito')
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('30. Comparado com hoje, um pedido de saúde feito assim chegaria à vossa equipa…')
    .setChoiceValues([
      'Muito mais depressa',
      'Um pouco mais depressa',
      'Ao mesmo tempo',
      'Mais devagar',
      'Não sei',
    ])
    .setRequired(true);

  form.addCheckboxItem()
    .setTitle('31. Que problemas vês nesta ideia?')
    .setHelpText('Escolhe até 3.')
    .setChoiceValues([
      'Falsos alarmes ou brincadeiras',
      'Escrever o que se passa demora, para quem está aflito ou mal',
      'Pedidos sem informação suficiente para saber a urgência',
      'Localização errada ou pouco precisa',
      'Rede fraca ou bateria',
      'Não consigo olhar para o telemóvel enquanto trato alguém',
      'As pessoas esperarem uma resposta imediata, ou deixarem de ligar para o 112',
      'Privacidade dos dados de saúde',
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
    .setTitle('32. Um pedido de saúde deveria passar primeiro pela sala de controlo, ou ir diretamente para a equipa de saúde?')
    .setChoiceValues([
      'Primeiro pela sala de controlo, que depois o encaminha',
      'Diretamente para a equipa de saúde',
      'Para as duas ao mesmo tempo',
      'Não sei',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('33. Se o sistema sugerisse automaticamente o tipo e a prioridade de cada pedido, a partir do que a pessoa escreveu, confiarias nessa sugestão?')
    .setChoiceValues([
      'Sim',
      'Só como sugestão: quem decide deve ser uma pessoa',
      'Não',
      'Não sei',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addParagraphTextItem()
    .setTitle('34. Queres acrescentar alguma coisa? (opcional)');

  const folha = SpreadsheetApp.create('Respostas · ' + TITULO);
  form.setDestination(FormApp.DestinationType.SPREADSHEET, folha.getId());

  Logger.log('Link para divulgar:   ' + form.getPublishedUrl());
  Logger.log('Editar o formulário:  ' + form.getEditUrl());
  Logger.log('Folha de respostas:   ' + folha.getUrl());
}
