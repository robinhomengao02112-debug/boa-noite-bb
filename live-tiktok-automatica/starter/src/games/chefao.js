// "Raid do Chefão": todo o chat ataca junto um chefe gigante (cooperativo).
// - Comentar = entra na raid e dá um golpe (GRÁTIS, com intervalo por pessoa).
// - Curtidas, seguir, compartilhar = dano (GRÁTIS).
// - Presentes = dano forte + efeitos visuais por faixa de valor.
// - Chefe derrotado -> ranking de dano + "último golpe" -> próximo chefe mais forte.
// Sem prêmio real: o reconhecimento é o nome na tela.

export class Chefao {
  static id = 'chefao';

  constructor(cfg) {
    this.cfg = cfg;
    this.level = 1;
    this.phase = 'fighting';
    this.phaseLeft = 0;
    this.dmgBy = new Map(); // userId -> { nick, dmg } do chefe atual
    this.lastComment = new Map();
    this.likeBuf = 0;
    this.lastHit = null;
    this.result = null;
    this.feed = [];
    this.today = { defeated: 0, bestLevel: 1, mvp: new Map() };
    this.spawn();
  }

  now() { return Date.now(); }
  publicConfig() { return { tiers: this.cfg.tiers }; }

  spawn() {
    const b = this.cfg.bosses[(this.level - 1) % this.cfg.bosses.length];
    this.boss = { ...b, maxHp: Math.round(this.cfg.baseHp * Math.pow(this.cfg.hpGrowth, this.level - 1)) };
    this.boss.hp = this.boss.maxHp;
    this.dmgBy.clear();
    this.lastHit = null;
  }

  pushFeed(m) { this.feed.unshift(m); this.feed = this.feed.slice(0, 6); }

  hit(dmg, user) {
    if (this.phase !== 'fighting' || dmg <= 0) return;
    this.boss.hp = Math.max(0, this.boss.hp - dmg);
    if (user) {
      const d = this.dmgBy.get(user.id) || { nick: user.nick, dmg: 0 };
      d.dmg += dmg;
      this.dmgBy.set(user.id, d);
      const t = this.today.mvp.get(user.id) || { nick: user.nick, dmg: 0 };
      t.dmg += dmg;
      this.today.mvp.set(user.id, t);
      this.lastHit = user.nick;
    }
    if (this.boss.hp <= 0) this.defeat();
  }

  tierFor(coins) {
    return [...this.cfg.tiers].reverse().find((t) => coins >= t.minCoins) || this.cfg.tiers[0];
  }

  handle(ev) {
    const d = this.cfg.damage;
    switch (ev.type) {
      case 'chat': {
        const last = this.lastComment.get(ev.user.id) || 0;
        if (this.now() - last < d.commentCooldownSeconds * 1000) return null;
        this.lastComment.set(ev.user.id, this.now());
        this.hit(d.perComment, ev.user);
        return { kind: 'hit', nick: ev.user.nick, dmg: d.perComment, fx: 'hit' };
      }
      case 'like': {
        this.likeBuf += ev.count;
        const batches = Math.floor(this.likeBuf / d.likesPerBatch);
        this.likeBuf %= d.likesPerBatch;
        if (batches) this.hit(batches * d.perLikeBatch, ev.user);
        return null;
      }
      case 'follow':
        this.hit(d.perFollow, ev.user);
        this.pushFeed(`${ev.user.nick} seguiu: golpe crítico!`);
        return { kind: 'hit', nick: ev.user.nick, dmg: d.perFollow, fx: 'crit' };
      case 'share':
        this.hit(d.perShare, ev.user);
        this.pushFeed(`${ev.user.nick} compartilhou: golpe crítico!`);
        return { kind: 'hit', nick: ev.user.nick, dmg: d.perShare, fx: 'crit' };
      case 'gift': {
        const dmg = ev.totalDiamonds * d.perCoin;
        const tier = this.tierFor(ev.totalDiamonds);
        this.hit(dmg, ev.user);
        this.pushFeed(`${ev.user.nick}: ${tier.name} (${ev.giftName}${ev.count > 1 ? ' x' + ev.count : ''}) -${dmg}`);
        return { kind: 'hit', nick: ev.user.nick, dmg, fx: tier.fx, label: tier.name };
      }
      default:
        return null;
    }
  }

  defeat() {
    this.phase = 'defeated';
    this.phaseLeft = this.cfg.defeatedSeconds;
    this.today.defeated++;
    this.result = {
      boss: this.boss.name, emoji: this.boss.emoji, level: this.level,
      lastHit: this.lastHit,
      top: this.top(this.dmgBy, 3),
    };
    this.pushFeed(`${this.boss.name} derrotado! Último golpe: ${this.lastHit || 'aliados'}`);
  }

  top(map, n) {
    return [...map.values()].sort((a, b) => b.dmg - a.dmg).slice(0, n).map((x) => ({ nick: x.nick, dmg: Math.round(x.dmg) }));
  }

  tick(dt) {
    if (this.phase === 'fighting') {
      // Aliados automáticos: o chefe sempre perde vida devagar (tela nunca parada).
      this.boss.hp = Math.max(0, this.boss.hp - this.cfg.allyDps * dt);
      if (this.boss.hp <= 0) this.defeat();
    } else {
      this.phaseLeft -= dt;
      if (this.phaseLeft <= 0) {
        this.level++;
        this.today.bestLevel = Math.max(this.today.bestLevel, this.level);
        this.phase = 'fighting';
        this.result = null;
        this.spawn();
      }
    }
  }

  snapshot() {
    return {
      phase: this.phase,
      level: this.level,
      boss: { name: this.boss.name, emoji: this.boss.emoji, hp: Math.ceil(this.boss.hp), maxHp: this.boss.maxHp },
      top: this.top(this.dmgBy, 5),
      result: this.result,
      phaseLeft: Math.max(0, Math.ceil(this.phaseLeft)),
      today: { defeated: this.today.defeated, bestLevel: this.today.bestLevel, mvp: this.top(this.today.mvp, 3) },
      feed: this.feed,
    };
  }
}
