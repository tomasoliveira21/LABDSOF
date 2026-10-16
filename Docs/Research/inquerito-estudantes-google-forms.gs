/**
 * Creates the student survey in Google Forms, with a response sheet
 * in Google Sheets.
 *
 * The questions are in Portuguese on purpose: the respondents are Portuguese
 * students. Comments in this file are in English, like the rest of the repository.
 *
 * Goal: validate the critical assumptions of the Problem and Opportunity Report.
 *   A2 · Participants check the application during the night     → questions 6, 7 and 8
 *   A3 · They adopt one application shared by events, no install  → questions 7 and 9
 *   A4 · Asking for help is slow or unclear today                 → questions 3, 5 and 10
 *   Problem · Information is scattered and hard to find           → questions 3, 4 and 6
 *
 * Anyone who has never been to a Queima and does not plan to go ends on the first page.
 * Anyone who has never been but plans to go skips the questions about the experience.
 *
 * How to use:
 *   1. Open https://script.google.com and create a "New project"
 *      (or add a new file to a project you already have).
 *   2. Paste this whole file.
 *   3. Choose the function criarInqueritoEstudantes and click "Run".
 *   4. The links to the form and to the responses appear in the "Execution log".
 */

function criarInqueritoEstudantes() {
  const TITULO = 'Queima das Fitas: como vives a noite do evento?';

  const DESCRICAO =
    'Somos estudantes do ISEP e estamos a estudar como melhorar a experiência e a segurança ' +
    'em grandes eventos académicos, como a Queima das Fitas.\n\n' +
    'Demora cerca de 3 minutos. É anónimo: não recolhemos o teu nome nem o teu email, ' +
    'e as respostas são usadas apenas de forma agregada, num trabalho académico (LABDSOF).';

  const form = FormApp.create(TITULO)
    .setDescription(DESCRICAO)
    .setProgressBar(true)
    .setCollectEmail(false)
    .setLimitOneResponsePerUser(false)
    .setConfirmationMessage('Obrigado! A tua resposta ajuda-nos muito.');

  // ---------- Page 1 · Filter ----------
  const q1 = form.addMultipleChoiceItem()
    .setTitle('1. Já foste a alguma Queima das Fitas ou semana académica?')
    .setRequired(true);

  // ---------- Page 2 · Your experience ----------
  const paginaExperiencia = form.addPageBreakItem()
    .setTitle('A tua experiência')
    .setHelpText('Pensa na última vez que foste a uma Queima ou semana académica.');

  form.addCheckboxItem()
    .setTitle('2. A que eventos já foste?')
    .setChoiceValues([
      'Queima das Fitas do Porto',
      'Queima das Fitas de Coimbra',
      'Enterro da Gata (Braga)',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addCheckboxItem()
    .setTitle('3. Na última vez que foste, passaste por alguma destas situações?')
    .setHelpText('Escolhe todas as que se aplicam.')
    .setChoiceValues([
      'Esperei mais de 30 minutos para entrar',
      'Não sabia onde ficava alguma coisa no recinto (WC, palco, posto médico)',
      'Perdi-me do meu grupo',
      'Não sabia como voltar para casa ou a que horas havia transporte',
      'Precisei de ajuda (saúde, segurança, roubo) e não sabia a quem pedir',
      'Fui vítima ou testemunha de um roubo',
      'Nenhuma destas',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addCheckboxItem()
    .setTitle('4. Onde procuraste informação sobre o evento (transportes, horários, recinto)?')
    .setHelpText('Escolhe todas as que se aplicam.')
    .setChoiceValues([
      'Instagram ou outras redes sociais da organização',
      'App da federação académica (ex.: app FAP)',
      'Site oficial do evento',
      'Amigos ou grupos de WhatsApp',
      'Placas e staff no recinto',
      'Não procurei',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('5. Se precisasses de ajuda dentro do recinto, o que farias primeiro?')
    .setChoiceValues([
      'Procurava um segurança ou alguém do staff',
      'Ligava para o 112',
      'Pedia ajuda aos meus amigos',
      'Ia ao posto médico',
      'Não sei',
    ])
    .showOtherOption(true)
    .setRequired(true);

  form.addScaleItem()
    .setTitle('6. Durante a noite do evento, quão fácil foi encontrar a informação de que precisavas?')
    .setBounds(1, 5)
    .setLabels('Muito difícil', 'Muito fácil')
    .setRequired(true);

  // ---------- Page 3 · An application for the event ----------
  const paginaApp = form.addPageBreakItem()
    .setTitle('Uma aplicação para o evento')
    .setHelpText(
      'Imagina uma aplicação que reúne a informação de vários eventos académicos. ' +
      'Abre-se por link ou por QR code no bilhete, sem ser obrigatório instalar.'
    );

  form.addMultipleChoiceItem()
    .setTitle('7. Usarias uma aplicação destas durante a noite do evento?')
    .setChoiceValues([
      'Sim, de certeza',
      'Provavelmente sim',
      'Provavelmente não',
      'Não',
    ])
    .setRequired(true);

  form.addCheckboxItem()
    .setTitle('8. Quais destas funcionalidades te seriam mais úteis?')
    .setHelpText('Escolhe até 3.')
    .setChoiceValues([
      'Planta do recinto (WC, palcos, postos de apoio)',
      'Horários e localização dos transportes de regresso',
      'Tempo de espera previsto nas entradas',
      'Botão de ajuda (saúde, segurança, roubo) que avisa a organização',
      'Avisos da organização em tempo real (alterações, alertas)',
      'Bilhete eletrónico na aplicação',
      'Assistente para tirar dúvidas sobre o evento',
    ])
    .setValidation(
      FormApp.createCheckboxValidation()
        .setHelpText('Escolhe no máximo 3.')
        .requireSelectAtMost(3)
        .build()
    )
    .setRequired(true);

  form.addCheckboxItem()
    .setTitle('9. O que te levaria a NÃO usar uma aplicação destas?')
    .setHelpText('Escolhe até 2.')
    .setChoiceValues([
      'Ter de instalar mais uma app',
      'Gastar bateria',
      'Ter de partilhar a minha localização',
      'Rede fraca no recinto',
      'Não confiar que a informação está atualizada',
      'Nada, usaria',
    ])
    .setValidation(
      FormApp.createCheckboxValidation()
        .setHelpText('Escolhe no máximo 2.')
        .requireSelectAtMost(2)
        .build()
    )
    .showOtherOption(true)
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('10. Se pedisses ajuda pela aplicação, partilharias a tua localização com a organização?')
    .setChoiceValues([
      'Sim, a localização do telemóvel',
      'Só indicando eu o local na planta do recinto',
      'Não',
    ])
    .setRequired(true);

  form.addParagraphTextItem()
    .setTitle('11. Há alguma coisa que mudarias na experiência da Queima? (opcional)');

  // Question 1 navigation: depends on whether they have been before.
  q1.setChoices([
    q1.createChoice('Sim, várias vezes', paginaExperiencia),
    q1.createChoice('Sim, uma vez', paginaExperiencia),
    q1.createChoice('Não, mas tenciono ir', paginaApp),
    q1.createChoice('Não, nem tenciono ir', FormApp.PageNavigationType.SUBMIT),
  ]);

  const folha = SpreadsheetApp.create('Respostas · ' + TITULO);
  form.setDestination(FormApp.DestinationType.SPREADSHEET, folha.getId());

  Logger.log('Link para divulgar:   ' + form.getPublishedUrl());
  Logger.log('Editar o formulário:  ' + form.getEditUrl());
  Logger.log('Folha de respostas:   ' + folha.getUrl());
}
