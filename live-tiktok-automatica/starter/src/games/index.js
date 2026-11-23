// Registro de jogos. Para adicionar um jogo novo:
// 1) crie src/games/<id>.js com uma classe que tenha: static id, constructor(cfg),
//    publicConfig(), handle(ev) -> fx|null, tick(dt), snapshot()
// 2) crie public/<id>/index.html (o overlay)
// 3) adicione a configuração em config.json -> games.<id>
// 4) registre abaixo.
import { Corrida } from './corrida.js';
import { Chefao } from './chefao.js';
import { Melancia } from './melancia.js';

export const GAMES = {
  [Corrida.id]: Corrida,
  [Chefao.id]: Chefao,
  [Melancia.id]: Melancia,
};
