// Trilha sonora ORIGINAL gerada em tempo real (Web Audio) — sem arquivos, sem direitos autorais.
// Cada jogo tem um tema; presentes, golpes e explosões têm efeitos sonoros.
// O OBS captura o áudio da fonte de navegador (reroute_audio) e manda junto com a live.
window.LiveMusic = (function () {
  const THEMES = {
    // bpm, escala (semitons a partir da tônica), progressão (graus), tônica MIDI, timbre
    chefao:   { bpm: 104, root: 45, scale: [0, 2, 3, 5, 7, 8, 10], prog: [0, 5, 2, 6], lead: 'sawtooth', mood: 'epico' },   // lá menor épico
    corrida:  { bpm: 132, root: 48, scale: [0, 2, 4, 5, 7, 9, 11], prog: [0, 4, 5, 3], lead: 'square',   mood: 'agitado' }, // dó maior animado
    melancia: { bpm: 116, root: 50, scale: [0, 2, 4, 5, 7, 9, 11], prog: [0, 3, 4, 3], lead: 'triangle', mood: 'divertido' }, // ré maior brincalhão
    default:  { bpm: 120, root: 48, scale: [0, 2, 4, 5, 7, 9, 11], prog: [0, 5, 3, 4], lead: 'triangle', mood: 'neutro' },
  };
  let ctx, master, musicBus, sfxBus, theme, step = 0, nextTime = 0, timer = null, opts = { volume: 0.5, sfx: true, enabled: true };
  let melodySeed = 1;

  const mtof = (m) => 440 * Math.pow(2, (m - 69) / 12);
  function rnd() { melodySeed = (melodySeed * 16807) % 2147483647; return melodySeed / 2147483647; }

  function noteOf(degree, octave = 0) {
    const s = theme.scale, n = s.length;
    const d = ((degree % n) + n) % n, o = Math.floor(degree / n);
    return theme.root + s[d] + 12 * (o + octave);
  }

  function env(gainNode, t, a, d, peak, sustainEnd) {
    const g = gainNode.gain;
    g.cancelScheduledValues(t);
    g.setValueAtTime(0.0001, t);
    g.exponentialRampToValueAtTime(peak, t + a);
    g.exponentialRampToValueAtTime(0.0001, t + a + d + (sustainEnd || 0));
  }

  function tone(freq, t, dur, type, peak, bus, detune = 0) {
    const o = ctx.createOscillator(), g = ctx.createGain(), f = ctx.createBiquadFilter();
    o.type = type; o.frequency.setValueAtTime(freq, t); o.detune.value = detune;
    f.type = 'lowpass'; f.frequency.value = type === 'sawtooth' ? 2200 : 3500;
    o.connect(f); f.connect(g); g.connect(bus);
    env(g, t, 0.01, dur, peak);
    o.start(t); o.stop(t + dur + 0.1);
  }

  function noise(t, dur, peak, hp, bus) {
    const len = Math.max(1, Math.floor(ctx.sampleRate * dur));
    const buf = ctx.createBuffer(1, len, ctx.sampleRate), data = buf.getChannelData(0);
    for (let i = 0; i < len; i++) data[i] = Math.random() * 2 - 1;
    const src = ctx.createBufferSource(), f = ctx.createBiquadFilter(), g = ctx.createGain();
    src.buffer = buf; f.type = 'highpass'; f.frequency.value = hp;
    src.connect(f); f.connect(g); g.connect(bus);
    env(g, t, 0.002, dur, peak);
    src.start(t); src.stop(t + dur + 0.05);
  }

  function kick(t, peak = 0.9, bus = musicBus) {
    const o = ctx.createOscillator(), g = ctx.createGain();
    o.frequency.setValueAtTime(140, t); o.frequency.exponentialRampToValueAtTime(42, t + 0.18);
    o.connect(g); g.connect(bus); env(g, t, 0.003, 0.22, peak);
    o.start(t); o.stop(t + 0.3);
  }

  // Sequenciador: 16 passos por compasso, 4 compassos por ciclo (um acorde por compasso)
  function scheduleStep(t) {
    const s16 = step % 16, bar = Math.floor(step / 16) % theme.prog.length;
    const chord = theme.prog[bar];
    // bateria
    if (s16 % 4 === 0) kick(t, theme.mood === 'divertido' ? 0.5 : 0.7);
    if (s16 % 4 === 2) noise(t, 0.04, 0.12, 7000, musicBus);
    if (s16 === 4 || s16 === 12) noise(t, 0.12, theme.mood === 'epico' ? 0.28 : 0.2, 1500, musicBus);
    // baixo
    if (s16 % 4 === 0 || (theme.mood === 'agitado' && s16 % 2 === 0)) tone(mtof(noteOf(chord, -1)), t, 0.18, 'triangle', 0.32, musicBus);
    // arpejo do acorde (1-3-5)
    if (s16 % 2 === 0) {
      const arp = [0, 2, 4, 2][(s16 / 2) % 4];
      tone(mtof(noteOf(chord + arp, 1)), t, 0.12, 'square', 0.045, musicBus);
    }
    // pad (acorde longo no início do compasso)
    if (s16 === 0) for (const k of [0, 2, 4]) tone(mtof(noteOf(chord + k, 0)), t, (60 / theme.bpm) * 3.6, 'sine', 0.06, musicBus, k * 3);
    // melodia gerada (muda a cada ciclo, mas sempre dentro da escala)
    if (s16 % 4 === 0 && rnd() < 0.75) {
      const deg = chord + [0, 2, 4, 5, 7][Math.floor(rnd() * 5)];
      tone(mtof(noteOf(deg, 2)), t, (60 / theme.bpm) * (rnd() < 0.3 ? 0.9 : 0.45), theme.lead, theme.lead === 'sawtooth' ? 0.035 : 0.05, musicBus);
    }
    step++;
  }

  function scheduler() {
    if (!ctx) return;
    if (ctx.state !== 'running') ctx.resume().catch(() => {});
    const stepDur = 60 / theme.bpm / 4;
    while (nextTime < ctx.currentTime + 0.25) { scheduleStep(nextTime); nextTime += stepDur; }
  }

  function start(gameId, options) {
    opts = { ...opts, ...(options || {}) };
    if (!opts.enabled) return;
    theme = THEMES[gameId] || THEMES.default;
    melodySeed = (gameId || 'x').split('').reduce((a, c) => a + c.charCodeAt(0), 7);
    if (!ctx) {
      ctx = new (window.AudioContext || window.webkitAudioContext)();
      master = ctx.createGain(); master.gain.value = opts.volume;
      const comp = ctx.createDynamicsCompressor();
      master.connect(comp); comp.connect(ctx.destination);
      musicBus = ctx.createGain(); musicBus.gain.value = 0.8; musicBus.connect(master);
      sfxBus = ctx.createGain(); sfxBus.gain.value = 1.0; sfxBus.connect(master);
    }
    master.gain.value = opts.volume;
    nextTime = ctx.currentTime + 0.1;
    if (!timer) timer = setInterval(scheduler, 50);
  }

  let lastHit = 0;
  function sfx(kind, size = 1) {
    if (!ctx || !opts.sfx) return;
    const t = ctx.currentTime + 0.01;
    if (kind === 'gift') { // brilho ascendente; quanto maior o presente, mais notas
      const n = Math.min(8, 2 + Math.floor(Math.log10(size + 1) * 2));
      for (let i = 0; i < n; i++) tone(mtof(noteOf(i * 2, 2)), t + i * 0.06, 0.25, 'triangle', 0.12, sfxBus);
    } else if (kind === 'big') { // presente grande: fanfarra
      [0, 4, 7, 12].forEach((s, i) => tone(mtof(theme.root + 24 + s), t + i * 0.09, 0.5, 'square', 0.09, sfxBus));
      noise(t, 0.6, 0.15, 3000, sfxBus);
    } else if (kind === 'hit') { // golpe curto (limitado para não virar barulho)
      if (ctx.currentTime - lastHit < 0.12) return;
      lastHit = ctx.currentTime;
      noise(t, 0.05, 0.12, 900, sfxBus);
    } else if (kind === 'boom') { // explosão / vitória
      kick(t, 1.0, sfxBus); noise(t, 0.9, 0.35, 120, sfxBus);
      [0, 4, 7].forEach((s, i) => tone(mtof(theme.root + 12 + s), t + 0.15 + i * 0.12, 0.6, 'square', 0.08, sfxBus));
    } else if (kind === 'follow') {
      tone(mtof(noteOf(4, 2)), t, 0.15, 'sine', 0.1, sfxBus); tone(mtof(noteOf(7, 2)), t + 0.08, 0.2, 'sine', 0.1, sfxBus);
    }
  }

  return { start, sfx };
})();
