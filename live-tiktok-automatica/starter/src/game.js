// "Corrida de Elementos": 4 times correm até a linha de chegada.
// - Comentar 1, 2, 3 ou 4 (ou o nome do time) = entrar no time (GRÁTIS).
// - Curtidas, comentários, seguir e compartilhar = empurram o time (GRÁTIS).
// - Presentes = empurram mais forte + efeitos especiais.
// Nenhum prêmio real é oferecido; o placar é só diversão/reconhecimento.

export class Game {
  constructor(config) {
    this.cfg = config;
    this.teams = config.teams.map((t) => ({ ...t, pos: 0, frozenUntil: 0, shieldUntil: 0, members: 0 }));
    this.viewerTeam = new Map(); // userId -> teamId
    this.lastComment = new Map(); // userId -> timestamp (cooldown)
    this.likeBuffer = new Map(); // teamId -> likes acumulados
    this.contrib = new Map(); // userId -> { nick, pts } da rodada
    this.allTime = { rounds: 0, wins: Object.fromEntries(config.teams.map((t) => [t.id, 0])) };
    this.phase = 'racing';
    this.elapsed = 0;
    this.intermission = 0;
    this.winner = null;
    this.round = 1;
    this.feed = [];
  }

  now() { return Date.now(); }

  teamFor(user, text) {
    if (text) {
      const t = text.trim().toLowerCase();
      const byNum = this.teams.find((x) => t === String(x.id));
      const byName = this.teams.find((x) => t === x.name.toLowerCase());
      const chosen = byNum || byName;
      if (chosen) {
        const prev = this.viewerTeam.get(user.id);
        if (prev !== chosen.id) {
          if (prev) this.teams.find((x) => x.id === prev).members--;
          chosen.members++;
          this.viewerTeam.set(user.id, chosen.id);
          this.pushFeed(`${user.nick} entrou no time ${chosen.emoji} ${chosen.name}`);
        }
        return chosen;
      }
    }
    let id = this.viewerTeam.get(user.id);
    if (!id) { // auto-atribui ao time com menos gente (equilíbrio)
      const t = [...this.teams].sort((a, b) => a.members - b.members)[0];
      t.members++;
      id = t.id;
      this.viewerTeam.set(user.id, id);
    }
    return this.teams.find((x) => x.id === id);
  }

  pushFeed(msg) {
    this.feed.unshift({ msg, t: this.now() });
    this.feed = this.feed.slice(0, 6);
  }

  add(team, pts, user) {
    if (this.phase !== 'racing' || pts <= 0) return;
    if (team.frozenUntil > this.now()) pts *= 0.25; // congelado anda bem devagar
    team.pos = Math.min(this.cfg.round.trackLength, team.pos + pts);
    if (user) {
      const c = this.contrib.get(user.id) || { nick: user.nick, pts: 0, team: team.id };
      c.pts += pts;
      this.contrib.set(user.id, c);
    }
  }

  handle(ev) {
    const s = this.cfg.scoring;
    if (ev.type === 'join') return null;
    const team = this.teamFor(ev.user, ev.type === 'chat' ? ev.text : null);

    switch (ev.type) {
      case 'chat': {
        const last = this.lastComment.get(ev.user.id) || 0;
        if (this.now() - last >= s.commentCooldownSeconds * 1000) {
          this.lastComment.set(ev.user.id, this.now());
          this.add(team, s.pointsPerComment, ev.user);
        }
        return null;
      }
      case 'like': {
        const buf = (this.likeBuffer.get(team.id) || 0) + ev.count;
        const batches = Math.floor(buf / s.likesPerBatch);
        this.likeBuffer.set(team.id, buf % s.likesPerBatch);
        this.add(team, batches * s.pointsPerLikeBatch, ev.user);
        return null;
      }
      case 'follow':
        this.add(team, s.pointsPerFollow, ev.user);
        return { kind: 'follow', team: team.id, nick: ev.user.nick };
      case 'share':
        this.add(team, s.pointsPerShare, ev.user);
        return { kind: 'share', team: team.id, nick: ev.user.nick };
      case 'gift': {
        this.add(team, ev.totalDiamonds * s.pointsPerDiamond, ev.user);
        const special = this.cfg.specialGifts[ev.giftName];
        if (special) this.applyEffect(special.effect, team, ev);
        this.pushFeed(`${ev.user.nick} → ${team.emoji} ${ev.giftName}${ev.count > 1 ? ' x' + ev.count : ''}`);
        return { kind: 'gift', team: team.id, nick: ev.user.nick, gift: ev.giftName, count: ev.count, effect: special?.effect || null, diamonds: ev.totalDiamonds };
      }
      default:
        return null;
    }
  }

  applyEffect(effect, team, ev) {
    const now = this.now();
    if (effect === 'boost') this.add(team, 10 * ev.count, ev.user);
    if (effect === 'mega') this.add(team, 300, ev.user);
    if (effect === 'shield') team.shieldUntil = now + 10000;
    if (effect === 'freeze_leader') {
      const leader = [...this.teams].filter((t) => t.id !== team.id).sort((a, b) => b.pos - a.pos)[0];
      if (leader && leader.shieldUntil < now) {
        leader.frozenUntil = now + 5000;
        this.pushFeed(`${ev.user.nick} congelou ${leader.emoji} ${leader.name}!`);
      }
    }
  }

  tick(dt) {
    if (this.phase === 'racing') {
      this.elapsed += dt;
      // "modo atração": anda sozinho bem devagar para a tela nunca ficar parada
      for (const t of this.teams) if (t.frozenUntil < this.now()) t.pos = Math.min(this.cfg.round.trackLength, t.pos + 0.15 * dt);
      const done = this.teams.find((t) => t.pos >= this.cfg.round.trackLength);
      if (done || this.elapsed >= this.cfg.round.maxSeconds) this.finish(done || [...this.teams].sort((a, b) => b.pos - a.pos)[0]);
    } else {
      this.intermission -= dt;
      if (this.intermission <= 0) this.reset();
    }
  }

  finish(winner) {
    this.phase = 'intermission';
    this.winner = { id: winner.id, name: winner.name, emoji: winner.emoji, color: winner.color, mvp: this.topContrib(3, winner.id) };
    this.allTime.rounds++;
    this.allTime.wins[winner.id]++;
    this.intermission = this.cfg.round.intermissionSeconds;
  }

  reset() {
    for (const t of this.teams) { t.pos = 0; t.frozenUntil = 0; t.shieldUntil = 0; }
    this.contrib.clear();
    this.phase = 'racing';
    this.elapsed = 0;
    this.winner = null;
    this.round++;
  }

  topContrib(n, teamId) {
    return [...this.contrib.values()].filter((c) => !teamId || c.team === teamId).sort((a, b) => b.pts - a.pts).slice(0, n).map((c) => ({ nick: c.nick, pts: Math.round(c.pts) }));
  }

  snapshot() {
    const now = this.now();
    return {
      phase: this.phase,
      round: this.round,
      timeLeft: Math.max(0, Math.ceil(this.cfg.round.maxSeconds - this.elapsed)),
      intermission: Math.max(0, Math.ceil(this.intermission)),
      teams: this.teams.map((t) => ({ id: t.id, pos: t.pos, members: t.members, frozen: t.frozenUntil > now, shield: t.shieldUntil > now })),
      top: this.topContrib(5),
      winner: this.winner,
      wins: this.allTime.wins,
      feed: this.feed.map((f) => f.msg),
    };
  }
}
