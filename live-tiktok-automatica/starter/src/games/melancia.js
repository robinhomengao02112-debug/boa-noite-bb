// "Estoura ou Não Estoura": cada interação coloca elásticos numa melancia (ou ar num balão).
// O limite que faz estourar é escondido e muda a cada rodada -> suspense.
// Quem causa o estouro ganha o destaque "EXPLODIU por @nome". Nenhum prêmio real.

export class Melancia {
  static id = 'melancia';

  constructor(cfg) {
    this.cfg = cfg;
    this.round = 0;
    this.lastComment = new Map();
    this.likeBuf = 0;
    this.feed = [];
    this.todayExploders = new Map(); // userId -> { nick, n }
    this.history = [];
    this.next();
  }

  now() { return Date.now(); }
  publicConfig() { return {}; }

  next() {
    this.round++;
    this.obj = this.cfg.objects[(this.round - 1) % this.cfg.objects.length];
    this.bands = 0;
    this.limit = this.cfg.minBands + Math.floor(Math.random() * (this.cfg.maxBands - this.cfg.minBands + 1));
    this.phase = 'filling';
    this.phaseLeft = 0;
    this.exploder = null;
    this.contrib = new Map();
  }

  pushFeed(m) { this.feed.unshift(m); this.feed = this.feed.slice(0, 6); }

  add(n, user) {
    if (this.phase !== 'filling' || n <= 0) return null;
    n = Math.min(n, this.cfg.maxBandsPerEvent);
    this.bands += n;
    if (user) {
      const c = this.contrib.get(user.id) || { nick: user.nick, n: 0 };
      c.n += n;
      this.contrib.set(user.id, c);
    }
    if (this.bands >= this.limit) {
      this.phase = 'exploded';
      this.phaseLeft = this.cfg.explodedSeconds;
      this.exploder = user ? user.nick : 'o vento';
      if (user) {
        const e = this.todayExploders.get(user.id) || { nick: user.nick, n: 0 };
        e.n++;
        this.todayExploders.set(user.id, e);
      }
      this.history.unshift({ obj: this.obj.emoji, bands: this.bands, by: this.exploder });
      this.history = this.history.slice(0, 5);
      this.pushFeed(`💥 ${this.exploder} estourou a ${this.obj.name}!`);
      return { kind: 'explode', nick: this.exploder };
    }
    return { kind: 'band', nick: user?.nick, n };
  }

  handle(ev) {
    const c = this.cfg;
    switch (ev.type) {
      case 'chat': {
        const last = this.lastComment.get(ev.user.id) || 0;
        if (this.now() - last < c.commentCooldownSeconds * 1000) return null;
        this.lastComment.set(ev.user.id, this.now());
        return this.add(c.bandsPerComment, ev.user);
      }
      case 'like': {
        this.likeBuf += ev.count;
        const n = Math.floor(this.likeBuf / c.likesPerBand);
        this.likeBuf %= c.likesPerBand;
        return n ? this.add(n, ev.user) : null;
      }
      case 'follow': return this.add(c.bandsPerFollow, ev.user);
      case 'share': return this.add(c.bandsPerShare, ev.user);
      case 'gift': {
        const fx = this.add(Math.max(1, Math.round(ev.totalDiamonds * c.bandsPerCoin)), ev.user);
        if (fx && fx.kind === 'band') this.pushFeed(`${ev.user.nick}: ${ev.giftName}${ev.count > 1 ? ' x' + ev.count : ''} +${fx.n} elásticos`);
        return fx;
      }
      default: return null;
    }
  }

  tick(dt) {
    if (this.phase === 'exploded') {
      this.phaseLeft -= dt;
      if (this.phaseLeft <= 0) this.next();
    }
  }

  snapshot() {
    // "tensão" visível sem revelar o número exato: usa a faixa mínima/máxima pública.
    const tension = Math.min(1, Math.max(0, (this.bands - this.cfg.minBands * 0.5) / (this.cfg.maxBands - this.cfg.minBands * 0.5)));
    return {
      phase: this.phase,
      round: this.round,
      obj: this.obj,
      bands: this.bands,
      tension,
      exploder: this.exploder,
      phaseLeft: Math.max(0, Math.ceil(this.phaseLeft)),
      top: [...this.contrib.values()].sort((a, b) => b.n - a.n).slice(0, 5),
      exploders: [...this.todayExploders.values()].sort((a, b) => b.n - a.n).slice(0, 3),
      history: this.history,
      feed: this.feed,
      range: [this.cfg.minBands, this.cfg.maxBands],
    };
  }
}
