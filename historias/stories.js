// Histórias originais do Enredo.
// Formato do roteiro (o mesmo usado no editor do app):
//   # cena            começa uma cena
//   @bg nome          fundo (cidade, quarto, escola, ceu, floresta, trem, tunel, palacio, praia, preto, branco, laboratorio, telhado)
//   @fx nome          efeito (chuva, petalas, faiscas, neve, estrelas, nenhum)
//   @show id lado humor   mostra personagem (lado: esq, dir, centro / humor: normal, feliz, triste, bravo, surpreso)
//   @hide id | @hide todos
//   @set var +1       muda afinidade/variável
//   @if var>=2 -> cena    pula se a condição for verdadeira
//   @goto cena
//   @cine Texto       momento cinematográfico (tela cheia)
//   @flash | @shake   efeitos rápidos
//   @end Título | descrição   final
//   ? Texto -> cena [var+1] {var>=2}   escolha (efeito e condição opcionais)
//   id: fala   |  id(humor): fala   |   qualquer outra linha é narração
//   {nome} = nome do jogador
const STORIES = [
{
  id: "chuva-neon",
  title: "Chuva de Neon",
  genre: "Ficção científica · Romance",
  synopsis: "Na última entrega da noite, a caixa que você carrega começa a respirar. Dentro dela está alguém que a cidade inteira quer apagar.",
  cover: { from: "#1b1040", to: "#e0307a", art: "cidade" },
  chars: {
    lis:  { name: "Lis",  hair: "#8fd8ff", eyes: "#3fa9ff", skin: "#f3dccd", style: "longo",  accent: "#8fd8ff" },
    kael: { name: "Kael", hair: "#2a2438", eyes: "#ff5c8a", skin: "#d9a27c", style: "curto",  accent: "#ff5c8a" },
    vex:  { name: "Agente Vex", hair: "#e9e9ef", eyes: "#9aa0b5", skin: "#e8c6ad", style: "coque", accent: "#cfd3e3" }
  },
  script: `
# inicio
@bg cidade
@fx chuva
@cine NOVA AURORA · 23:58
Nova Aurora nunca dorme. Ela só troca de cor.
Você pedala pela avenida alagada com a última entrega da noite presa na garupa: uma caixa prateada, sem remetente, pesada demais para o tamanho.
O endereço de destino é um galpão no Setor 9. O pagamento é o triplo do normal.
Então a caixa se mexe.
@shake
? Parar a bicicleta e abrir a caixa -> abrir [lis+1]
? Ignorar e acelerar até o Setor 9 -> ignorar

# abrir
Você para debaixo de um letreiro que pisca "ABERTO 24H" em rosa.
A trava da caixa cede com um suspiro de ar comprimido.
@flash
@show lis centro surpreso
Lá dentro, encolhida, está uma garota de cabelo azul-gelo. Os olhos dela acendem como telas ligando.
lis(surpreso): ...Você não é o comprador.
lis: Por favor. Se me levar até o Setor 9, eles vão me desmontar.
? "Quem é você?" -> quem
? "Isso não é problema meu." -> frio [lis-1]

# ignorar
Você acelera. A caixa bate contra a garupa, cada vez mais forte.
No cruzamento da Rua 40, um drone de segurança desce na sua frente, e a luz vermelha dele te cega.
@flash
A trava da caixa se abre sozinha.
@show lis centro bravo
lis(bravo): Se você quer viver, vire à esquerda. Agora.
? Confiar nela e virar -> fuga [lis+1]
? Frear e entregar a caixa ao drone -> drone

# quem
lis: Meu nome é Lis. Modelo L-15. Fui feita para lembrar.
lis(triste): Lembrar o quê, eu ainda não sei. Apagaram quase tudo.
Uma sirene corta a chuva, ainda longe e se aproximando.
lis: Eles rastreiam a caixa. Precisamos sair daqui.
@goto fuga

# frio
lis(triste): Então é isso que eu valho. O triplo de uma entrega comum.
Ela sai da caixa e começa a andar sozinha pela chuva, descalça.
Você observa. Algo no jeito dela andar parece humano demais.
? Ir atrás dela -> fuga [lis+1]
? Deixá-la ir -> sozinho

# sozinho
Você volta para casa. Toma um banho quente. Tenta dormir.
No dia seguinte, os telões da cidade mostram uma notícia curta: "Androide defeituoso recolhido no Setor 4."
A foto é dela.
@end Entrega Cancelada | Você escolheu não se envolver. A cidade agradece, mas você nunca esquece aqueles olhos.

# drone
Você levanta as mãos. O drone escaneia a caixa e emite um bipe satisfeito.
@show lis centro triste
lis(triste): Eu entendo. Todo mundo tem medo.
Dois agentes de terno cinza descem de um carro preto e levam a caixa embora.
Na sua conta, o pagamento cai na hora. O triplo, como prometido.
@end O Preço Certo | Você recebeu cada centavo. E perdeu algo que nenhum dinheiro devolve.

# fuga
@bg cidade
@show lis esq normal
Vocês correm por becos que só os entregadores conhecem. Lis segue cada passo seu sem errar.
lis: Você conhece bem esta cidade.
? "Entrego pizza aqui há quatro anos." -> fuga2 [lis+1]
? "Menos conversa, mais corrida." -> fuga2

# fuga2
No fim do beco, uma porta de metal se abre antes de vocês baterem.
@show kael dir normal
kael: Entra logo. Os drones chegam em noventa segundos.
Um rapaz de jaqueta escura, com fones neon no pescoço, fecha a porta atrás de vocês.
kael: Me chamo Kael. Eu que mandei aquela caixa pra você, {nome}.
@shake
? "Você me usou?!" -> kaelbravo [kael-1]
? "Por que eu?" -> kaelporque [kael+1]

# kaelbravo
kael(bravo): Usei. E faria de novo. Você era o único entregador que nunca abre os pacotes.
kael(normal): ...Pelo visto, eu estava errado.
@goto esconderijo

# kaelporque
kael(feliz): Porque você é o único entregador desta cidade que já devolveu uma carteira cheia de créditos.
kael: Eu precisava de alguém honesto. Honestidade é rara no Setor 9.
@goto esconderijo

# esconderijo
@bg laboratorio
@fx nenhum
O esconderijo de Kael é um ninho de cabos, telas e restos de máquinas.
Lis toca uma tela, e imagens passam rápido demais para acompanhar.
@show lis centro surpreso
lis(surpreso): Eu conheço este lugar.
kael: Claro que conhece. Você foi construída aqui. Por mim e pela minha irmã.
@hide kael
lis(triste): Sua irmã...?
kael: Ela gravou as próprias memórias em você antes da Corporação Halo levar ela embora.
Silêncio. Só o zumbido das máquinas.
? Segurar a mão de Lis -> mao [lis+2]
? Perguntar o que a Halo quer -> halo [kael+1]

# mao
@show lis centro feliz
A mão dela é fria no começo e depois esquenta, como se aprendesse com a sua.
lis(feliz): Obrigada, {nome}. Ninguém fez isso antes. Ou, se fez, eu esqueci.
@goto halo

# halo
@show kael dir bravo
kael(bravo): A Halo vende memórias. Lembranças felizes por assinatura.
kael: Minha irmã descobriu que eles roubam essas memórias de pessoas comuns enquanto elas dormem.
kael: A prova está dentro da Lis. Se a gente transmitir no telão central, a cidade inteira vai ver.
@flash
Um estrondo. A porta de metal entorta para dentro.
@show vex esq bravo
vex(bravo): Que reunião adorável. A propriedade da Halo vem comigo.
@cine A ESCOLHA
? Proteger Lis com o próprio corpo -> proteger [lis+2]
? Ajudar Kael a segurar a porta -> segurar [kael+2]
? Negociar com a Agente Vex -> negociar

# proteger
@hide kael
Você se coloca entre Lis e a agente.
vex: Coragem é só um defeito de fábrica, entregador.
lis(surpreso): {nome}, não!
O disparo de choque te atinge no ombro. Tudo fica branco por um segundo.
@flash
@goto climax

# segurar
@hide lis
Você e Kael empurram um armário contra a porta. A agente fica presa do outro lado por alguns segundos.
kael(feliz): Nada mal pra um entregador.
kael: Lis, a antena do telhado! Vai!
@goto climax

# negociar
@hide kael
vex: Fale rápido.
Você oferece a caixa vazia, o rastreador e o seu silêncio em troca de deixarem Lis em paz.
vex(feliz): Que fofo. Não.
@shake
Pelo menos a conversa ganhou tempo. Kael já subiu para o telhado com Lis.
@goto climax

# climax
@bg telhado
@fx chuva
@hide todos
@cine TELHADO · 00:41
No alto do prédio, a antena pisca sob a tempestade. Lá embaixo, Nova Aurora brilha como um mar de néon.
@show lis centro normal
lis: Se eu transmitir tudo, minha memória vai junto. Não sobra nada de mim.
lis(triste): Eu vou esquecer você.
@if lis>=4 -> finalLis
@if kael>=3 -> finalKael
@goto finalVerdade

# finalLis
? "Então eu te ensino a lembrar de novo." -> amor
? "Transmita. A cidade precisa saber." -> verdade

# finalKael
@show kael dir triste
kael(triste): Tem outro jeito. Eu posso transmitir usando a minha cabeça como ponte. O implante aguenta.
kael: Talvez.
? Deixar Kael se arriscar -> sacrificio
? "Ninguém se sacrifica hoje." -> verdade [kael+1]

# finalVerdade
? Ligar a transmissão -> verdade

# amor
@fx estrelas
@show lis centro feliz
Lis sorri e encosta a testa na sua.
lis(feliz): Promete?
A transmissão começa. Os telões da cidade inteira se acendem com as memórias roubadas.
Quando acaba, Lis abre os olhos devagar. Ela não sabe seu nome. Mas segura sua mão sem saber por quê.
@cine UM ANO DEPOIS
Vocês pedalam juntos pela mesma avenida. Ela já lembra seu nome, sua comida favorita e o caminho de casa.
lis(feliz): {nome}... eu acho que já te amava antes de lembrar.
@end Memória Nova | O amor de vocês sobreviveu ao esquecimento. FINAL VERDADEIRO.

# verdade
@fx faiscas
@flash
A transmissão explode pelos telões. Milhões de pessoas veem a verdade ao mesmo tempo.
A Corporação Halo cai em uma única noite.
@show lis centro normal
Lis agora é só uma androide gentil e vazia, aprendendo o mundo do zero.
lis: Olá. Você parece alguém importante.
@end Cidade Desperta | Você salvou Nova Aurora. Agora é tempo de recomeçar.

# sacrificio
@show kael dir feliz
kael(feliz): Cuida dela por mim, {nome}.
@flash
@shake
A antena brilha até ficar branca. As memórias se espalham pela cidade, e Lis continua inteira.
Kael não acorda. Os médicos dizem que talvez acorde um dia.
@show lis esq triste
lis: Eu vou lembrar dele por nós dois.
@end Ponte de Luz | Um sacrifício salvou a verdade e a memória de Lis.
`
},
{
  id: "academia-estrelas",
  title: "Academia das Estrelas Caídas",
  genre: "Fantasia · Escola de Magia",
  synopsis: "Você chegou à academia sem magia nenhuma, mas com uma estrela caída no bolso. Agora todos querem saber o que você é.",
  cover: { from: "#0d1b4a", to: "#7a4bd6", art: "estrelas" },
  chars: {
    aiko: { name: "Aiko", hair: "#ff9ec7", eyes: "#c05bff", skin: "#f6dfd2", style: "chiquinhas", accent: "#ff9ec7" },
    ren:  { name: "Ren",  hair: "#d6362f", eyes: "#ffb03b", skin: "#eccab0", style: "espetado", accent: "#ff6a3d" },
    mestra: { name: "Mestra Oriel", hair: "#5a5f8f", eyes: "#7fe0ff", skin: "#c99b7d", style: "longo", accent: "#7fe0ff" }
  },
  script: `
# inicio
@bg ceu
@fx estrelas
@cine PRIMEIRO DIA
Dizem que só uma estrela cai por século sobre a Academia de Lumen.
Ontem à noite, ela caiu no seu quintal.
Agora ela brilha morna no seu bolso enquanto você atravessa os portões dourados da escola.
@bg escola
@fx petalas
@show aiko dir feliz
aiko(feliz): Ei! Você é aluno novo? Seu bolso está brilhando!
aiko: Eu sou a Aiko. Feitiços de cura, terceiro ano, péssima em pontualidade.
? "Prazer! Eu sou {nome}." -> amiga [aiko+1]
? Esconder o brilho do bolso -> esconder

# amiga
aiko(feliz): {nome}! Que nome bonito. Vem, eu te mostro tudo!
@goto ren

# esconder
aiko(surpreso): Ué, sumiu? Tudo bem, todo mundo tem segredos aqui.
aiko(feliz): Vem, a cerimônia já vai começar!
@goto ren

# ren
@bg palacio
@fx nenhum
No Salão dos Cristais, cada novo aluno toca o Orbe de Avaliação para descobrir seu elemento.
@show ren esq normal
Um garoto de cabelo vermelho toca o orbe primeiro. Chamas sobem até o teto.
ren(feliz): Fogo. Nível sete. Como esperado.
Os alunos aplaudem. Ele passa por você e para.
ren(bravo): E você? Tem cara de quem não acende nem uma vela.
? "Veremos." -> orbe [ren+1]
? Ignorar Ren -> orbe
? "E você tem cara de quem precisa de um extintor." -> orbe [ren+2, aiko+1]

# orbe
@hide todos
Sua vez. Você coloca a mão no orbe.
Nada acontece.
Risadinhas atravessam o salão.
Então a estrela no seu bolso queima. O orbe racha de cima a baixo.
@flash
@shake
@cine O ORBE SE PARTIU
@show mestra centro surpreso
mestra(surpreso): Silêncio! Ninguém se mexe.
mestra: {nome}. Você vem comigo para a torre. Agora.
@goto torre

# torre
@bg ceu
@fx estrelas
@show mestra centro normal
Do alto da torre, a Mestra Oriel observa o céu por um longo tempo antes de falar.
mestra: O que você carrega não é uma pedra. É um coração estelar. A última vez que alguém teve um, a academia quase foi destruída.
mestra: Eu posso guardá-lo em segurança. Você voltaria a ser uma pessoa comum.
? Entregar a estrela à Mestra -> entregar
? "Ela me escolheu. Eu fico com ela." -> ficar [mestra+1]

# entregar
mestra(triste): Uma escolha sábia. Talvez sábia até demais.
Sem a estrela, você volta para casa no dia seguinte. A academia some no horizonte como um sonho.
@end Vida Comum | Você escolheu a paz. Às vezes ainda sonha com o céu.

# ficar
mestra(feliz): Era o que eu esperava ouvir.
mestra: Então você vai precisar aprender a controlá-la. Em sete dias acontece o Torneio da Lua Partida. Se você perder o controle lá...
mestra: Melhor nem pensar nisso.
@goto treino

# treino
@bg floresta
@fx petalas
@cine SEMANA DE TREINO
Você precisa de um parceiro de treino. Dois nomes vêm à cabeça.
? Treinar com Aiko -> treinoAiko [aiko+2]
? Pedir ajuda ao Ren -> treinoRen [ren+2]

# treinoAiko
@show aiko centro feliz
aiko: Magia não é força. É respirar junto com ela.
Vocês passam as tardes no bosque. Aiko cura cada arranhão seu com luz rosada.
aiko(triste): Sabe... eu não tenho muitos amigos. As pessoas acham que curandeiros são fracos.
? "Você é a pessoa mais forte que eu conheço." -> torneio [aiko+2]
? "Eles que perdem." -> torneio [aiko+1]

# treinoRen
@show ren centro bravo
ren(bravo): Por que eu ajudaria você?
? "Porque você quer um rival de verdade." -> renSim [ren+2]
? "Porque eu pedi por favor." -> renSim [ren+1]

# renSim
ren(feliz): ...Tch. Tá bom. Mas, se chorar, eu vou embora.
Ren treina você sem piedade. Você cai, levanta, cai de novo.
No quinto dia, a estrela responde. Uma faísca dourada dança na sua mão.
ren(surpreso): Nada mal. Nada mal mesmo.
ren(triste): Meu pai só me olha quando eu venço. Por isso eu não posso perder.
@goto torneio

# torneio
@bg palacio
@fx faiscas
@hide todos
@cine TORNEIO DA LUA PARTIDA
A arena está lotada. No telão de cristal aparece seu nome ao lado do adversário da final.
@show ren dir normal
ren: Sabia que seria você.
O duelo começa. Chamas e luz estelar se chocam no ar.
@flash
@shake
No meio da luta, a estrela pulsa forte demais. Você sente que vai perder o controle.
@show aiko esq surpreso
aiko(surpreso): {nome}! Respira!
@if aiko>=4 -> respirar
@if ren>=4 -> confiar
@goto descontrole

# respirar
Você lembra das tardes no bosque e respira junto com a estrela.
A luz se acalma e vira um campo de constelações sobre a arena.
@fx estrelas
aiko(feliz): Conseguiu!
? Abraçar Aiko na frente de todos -> finalAiko
? Estender a mão ao Ren -> finalAmizade

# confiar
ren(bravo): Olha pra mim! Você treinou comigo. Você aguenta!
Vocês dois apontam as mãos para o céu ao mesmo tempo. O fogo dele envolve sua luz e a leva para cima, longe da plateia.
@fx estrelas
@flash
ren(feliz): ...Empate. Eu odeio empates. Mas esse eu aceito.
? "Na próxima eu ganho." -> finalRen
? Estender a mão aos dois -> finalAmizade

# descontrole
@flash
@shake
@bg branco
A luz engole tudo. Quando você acorda, a arena virou cristal.
@bg palacio
@show mestra centro triste
mestra(triste): Ninguém se feriu. Mas a estrela se apagou.
@end Estrela Apagada | Você não estava sozinho, mas ainda não deixava ninguém chegar perto. Tente criar laços.

# finalAiko
@show aiko centro feliz
@hide ren
O público explode em aplausos. Aiko te abraça forte, com o rosto vermelho.
aiko(feliz): Eu... posso continuar treinando com você? Pra sempre?
@end Luz Rosada | Você e Aiko viraram a dupla mais lendária de Lumen.

# finalRen
@show ren centro feliz
@hide aiko
ren(feliz): Vai sonhando. Amanhã, seis da manhã. Não se atrase.
Pela primeira vez, o pai dele aplaude da arquibancada. Ren nem olha. Ele está olhando pra você.
@end Rival de Fogo | Um rival virou a pessoa em quem você mais confia.

# finalAmizade
@show aiko esq feliz
@show ren dir feliz
Vocês três ficam de mãos dadas no centro da arena enquanto a estrela desenha uma constelação nova no céu.
mestra: Três estrelas. Um novo signo para Lumen.
@end Constelação | Vocês provaram que a magia mais forte é a que se divide. FINAL VERDADEIRO.
`
},
{
  id: "ultimo-trem",
  title: "O Último Trem",
  genre: "Suspense · Mistério",
  synopsis: "O metrô das 0h13 não para em nenhuma estação. Os passageiros parecem conhecer você. E o relógio volta toda vez que o trem entra no túnel.",
  cover: { from: "#0b0f14", to: "#2f6b5e", art: "trem" },
  chars: {
    noa:  { name: "Noa", hair: "#141414", eyes: "#4fe3b0", skin: "#e7c3a6", style: "curto", accent: "#4fe3b0" },
    vovo: { name: "Senhora Iara", hair: "#d9d6cf", eyes: "#7a6a58", skin: "#c69476", style: "coque", accent: "#e8c07a" },
    cond: { name: "Condutor", hair: "#0a0a0a", eyes: "#ff3b3b", skin: "#9c8f86", style: "chapeu", accent: "#ff3b3b" }
  },
  script: `
# inicio
@bg trem
@fx nenhum
@cine 00:13 · LINHA 7
Você quase perde o último metrô. As portas se fecham atrás de você com um chiado.
O vagão está quase vazio: uma garota de fones, uma senhora tricotando e um condutor que você não vê, só ouve.
cond: Próxima estação... nenhuma.
@show noa dir normal
noa: Ele sempre diz isso. Senta. Vai demorar.
? "Como assim, sempre?" -> sempre [noa+1]
? Sentar longe dela -> longe

# sempre
noa: É minha quadragésima segunda volta. Ou quadragésima terceira. Perdi a conta.
noa(triste): Toda vez que o trem entra no túnel, o relógio volta para 00:13.
@goto tunel

# longe
@hide noa
Você senta no fundo do vagão. A senhora sorri sem tirar os olhos do tricô.
@show vovo esq feliz
vovo(feliz): Você chegou, querido. Eu guardei o seu lugar.
vovo: Faz tanto tempo.
@goto tunel

# tunel
@bg tunel
@flash
@shake
@cine 00:13
As luzes piscam. O relógio do vagão volta para 00:13.
Lá fora, as paredes do túnel estão cobertas de fotos. Em todas aparece você.
@bg trem
? Examinar o vagão -> examinar
? Falar com Noa -> noa2 [noa+1]
? Falar com a Senhora Iara -> iara [vovo+1]

# examinar
Embaixo do seu banco há um bilhete amassado, escrito com a sua própria letra:
"NÃO CONFIE NO CONDUTOR. CONFIE EM QUEM LEMBRA DE VOCÊ."
@shake
? Falar com Noa -> noa2 [noa+1]
? Falar com a Senhora Iara -> iara [vovo+1]

# noa2
@show noa dir normal
noa: Eu tentei tudo. Puxar o freio de emergência, quebrar as janelas, gritar.
noa: A única coisa que eu nunca tentei foi ir até a cabine do condutor.
noa(triste): Eu fico com medo do que tem lá.
? "A gente vai junto." -> juntas [noa+2]
? "Por que você está aqui?" -> porque [noa+1]

# porque
noa(triste): Eu briguei com meu irmão naquela noite. Saí correndo e peguei este trem.
noa: Acho que não estou presa no trem. Estou presa naquela briga.
@goto juntas

# juntas
noa(feliz): Junto, então. Só não me larga.
@goto decisao

# iara
@show vovo esq normal
vovo: Você não lembra de mim, não é? Tudo bem. Eu lembro por nós dois.
Ela te entrega o tricô. É um cachecol verde com o seu nome bordado.
vovo(triste): Eu nunca consegui terminar e te entregar. Você foi embora tão rápido.
? Vestir o cachecol -> cachecol [vovo+2]
? Devolver com cuidado -> decisao

# cachecol
@flash
Uma lembrança te atinge: cozinha cheirando a café, uma voz cantando, mãos enrugadas segurando as suas.
Avó.
vovo(feliz): Aí está você.
@goto decisao

# decisao
@hide todos
@bg trem
@cine A CABINE
A porta da cabine está entreaberta. Uma luz vermelha pulsa lá dentro.
cond: Volte para o seu lugar. A viagem é mais segura se você não pensar.
@show cond centro bravo
? Abrir a porta da cabine -> cabine
? Obedecer e voltar ao seu lugar -> obedecer

# obedecer
@hide cond
Você volta para o banco. O túnel chega. O relógio volta.
00:13. 00:13. 00:13.
Depois de um tempo, você nem lembra por que queria sair.
@end Passageiro Eterno | O trem continua. Talvez para sempre. Tente lembrar de alguém.

# cabine
@bg tunel
@fx faiscas
cond(bravo): Você não deveria estar aqui.
Na cabine não há volante, só um relógio enorme parado em 00:13 e uma janela que mostra uma rua chuvosa e um acidente.
Você reconhece aquela rua. Reconhece aquela noite.
cond: Este trem é para quem ainda não decidiu se fica ou se volta. Eu só dirijo.
@if noa>=3 -> comNoa
@if vovo>=3 -> comIara
@goto sozinha

# comNoa
@show noa esq feliz
noa: {nome}, olha! O relógio está andando!
noa: Lembrar é a saída. Eu lembro do meu irmão. Eu quero pedir desculpa.
noa: E agora eu lembro de você também.
? Segurar a mão dela e puxar o freio -> finalNoa
? Deixar Noa ir primeiro -> finalSacrificio

# comIara
@show vovo esq feliz
vovo(feliz): Agora você precisa voltar, meu bem. Eu já fiquei tempo demais esperando o trem.
vovo: Leva o cachecol. Aí lá fora você não sente frio.
? "Vem comigo, vó." -> finalIara
? Abraçar ela uma última vez -> finalIara

# sozinha
Você puxa a alavanca com toda a força. O relógio range, mas não anda.
cond: Ninguém sai sem alguém que lembre dele.
@flash
@goto obedecer

# finalNoa
@bg branco
@flash
@hide todos
@cine 00:14
Você acorda numa ambulância. Chove. Um paramédico sorri, aliviado.
@bg cidade
@fx chuva
Semanas depois, na estação, uma garota de fones corre até você.
@show noa centro feliz
noa(feliz): Eu sabia que era real! Meu irmão me esperou no hospital. E eu... esperei você.
@end 00:14 | Vocês saíram juntos do loop. O relógio voltou a andar. FINAL VERDADEIRO.

# finalSacrificio
@show noa esq triste
noa(triste): Não! A gente ia junto!
Você empurra Noa para fora pela porta de luz. Ela some no branco.
@hide noa
cond: Escolha nobre. Mas alguém precisa ocupar o lugar.
Você senta. O relógio volta. Mas agora você sorri, porque alguém lá fora lembra de você.
@end Lugar Reservado | Você salvou Noa e ficou no trem esperando a sua vez de lembrar.

# finalIara
@bg branco
@flash
@show vovo centro feliz
vovo(feliz): Eu vou estar no cheiro do café, sempre que você precisar.
@hide todos
@cine 00:14
Você acorda num hospital, com um cachecol verde enrolado no pescoço.
Ninguém sabe dizer de onde ele veio.
@end Cachecol Verde | O amor de quem já partiu te trouxe de volta.
`
}
];
