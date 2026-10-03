/**
 * Cria o inquérito a estudantes no Google Forms, com uma folha de respostas
 * no Google Sheets.
 *
 * Objetivo: validar as assunções críticas do Problem and Opportunity Report.
 *   A2 · Os participantes consultam a aplicação durante a noite      → perguntas 6, 7 e 8
 *   A3 · Adotam uma aplicação comum a vários eventos, sem instalar   → perguntas 7 e 9
 *   A4 · Pedir ajuda é hoje lento ou pouco claro                     → perguntas 3, 5 e 10
 *   Problema · Informação dispersa e difícil de encontrar            → perguntas 3, 4 e 6
 *
 * Quem nunca foi a uma Queima e não tenciona ir termina na primeira página.
 * Quem nunca foi, mas tenciona ir, salta as perguntas sobre a experiência.
 *
 * Como usar:
 *   1. Abre https://script.google.com e cria um "Novo projeto"
 *      (ou acrescenta um ficheiro novo a um projeto que já tenhas).
 *   2. Cola este ficheiro inteiro.
 *   3. Escolhe a função criarInqueritoEstudantes e carrega em "Executar".
 *   4. Em "Registo de execução" aparecem os links do formulário e das respostas.
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

  // ---------- Página 1 · Filtro ----------
  const q1 = form.addMultipleChoiceItem()
    .setTitle('1. Já foste a alguma Queima das Fitas ou semana académica?')
    .setRequired(true);

  // ---------- Página 2 · A tua experiência ----------
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

  // ---------- Página 3 · Uma aplicação para o evento ----------
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

  // Navegação da pergunta 1: depende de já ter ido ou não.
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
