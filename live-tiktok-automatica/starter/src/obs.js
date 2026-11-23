// Controle do OBS Studio via obs-websocket (já vem embutido no OBS 28+).
// - Abre o OBS se não estiver aberto
// - Cria/ajusta perfil vertical, coleção de cenas, cena "Jogo" e a fonte de navegador do jogo
// - Define servidor/chave RTMP, inicia e para a transmissão
import OBSWebSocket from 'obs-websocket-js';
import { spawn, execFile } from 'node:child_process';
import { existsSync, readdirSync, rmSync } from 'node:fs';
import path from 'node:path';
import os from 'node:os';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const IS_WIN = process.platform === 'win32';

export function obsConfigDir() {
  if (IS_WIN) return path.join(process.env.APPDATA || path.join(os.homedir(), 'AppData', 'Roaming'), 'obs-studio');
  if (process.platform === 'darwin') return path.join(os.homedir(), 'Library', 'Application Support', 'obs-studio');
  return path.join(process.env.XDG_CONFIG_HOME || path.join(os.homedir(), '.config'), 'obs-studio');
}

// Se o OBS caiu (ou o PC desligou), ele deixa um arquivo "sentinela" e, na próxima abertura,
// mostra a janela "Rodar em Modo de Segurança?" — que travaria o modo automático e desligaria o WebSocket.
// Como só abrimos o OBS quando ele NÃO está rodando, é seguro apagar esses arquivos antes.
export function clearCrashSentinel() {
  const dir = obsConfigDir();
  const sentinelDir = path.join(dir, '.sentinel');
  let removed = 0;
  try {
    for (const f of readdirSync(sentinelDir)) { rmSync(path.join(sentinelDir, f), { force: true }); removed++; }
  } catch { /* pasta não existe: ok */ }
  const legacy = path.join(dir, 'safe_mode'); // versões antigas do OBS
  if (existsSync(legacy)) { rmSync(legacy, { force: true }); removed++; }
  return removed;
}

export function findObsExecutable(custom) {
  if (custom && existsSync(custom)) return custom;
  if (IS_WIN) {
    const bases = [process.env.ProgramFiles, process.env['ProgramFiles(x86)'], 'C:\\Program Files'].filter(Boolean);
    for (const b of bases) {
      const p = path.join(b, 'obs-studio', 'bin', '64bit', 'obs64.exe');
      if (existsSync(p)) return p;
    }
    return null;
  }
  for (const p of ['/usr/bin/obs', '/usr/local/bin/obs', '/Applications/OBS.app/Contents/MacOS/OBS']) if (existsSync(p)) return p;
  return null;
}

function isObsProcessRunning() {
  return new Promise((resolve) => {
    if (IS_WIN) {
      execFile('tasklist', ['/FI', 'IMAGENAME eq obs64.exe', '/NH'], { windowsHide: true }, (err, out) => resolve(!err && /obs64\.exe/i.test(out)));
    } else {
      execFile('pgrep', ['-x', 'obs'], (err) => resolve(!err));
    }
  });
}

export class ObsController {
  constructor(opts) {
    this.o = opts; // { exe, password, port, overlayUrl, profile, collection, scene, inputName, video:{baseWidth,baseHeight,outputWidth,outputHeight,fps}, videoBitrate, audioBitrate, extraArgs }
    this.ws = new OBSWebSocket();
    this.connected = false;
    this.setupDone = false;
    this.lastError = null;
    this.ws.on('ConnectionClosed', () => { this.connected = false; this.setupDone = false; });
    this.ws.on('StreamStateChanged', (e) => { this.lastStreamState = e.outputState; });
  }

  log(...a) { (this.o.logger || console.log)(['[obs]', ...a].join(' ')); }

  async launch() {
    if (await isObsProcessRunning()) return true;
    const exe = this.o.exe || findObsExecutable();
    if (!exe) { this.lastError = 'OBS não encontrado. Instale o OBS Studio.'; return false; }
    const args = [
      '--profile', this.o.profile,
      '--collection', this.o.collection,
      '--scene', this.o.scene,
      '--minimize-to-tray',
      '--disable-missing-files-check',
      ...(IS_WIN ? ['--disable-updater'] : []),
      ...(this.o.extraArgs || []),
    ];
    const n = clearCrashSentinel();
    if (n) this.log(`limpou ${n} marca(s) de queda anterior do OBS (evita a janela de Modo de Segurança)`);
    this.log('abrindo OBS:', exe);
    const child = spawn(exe, args, { cwd: path.dirname(exe), detached: true, stdio: 'ignore', windowsHide: false });
    child.unref();
    return true;
  }

  async connect() {
    if (this.connected) return true;
    try {
      await this.ws.connect(`ws://127.0.0.1:${this.o.port}`, this.o.password || undefined, { rpcVersion: 1 });
      this.connected = true;
      this.lastError = null;
      this.log('conectado ao OBS');
      return true;
    } catch (e) {
      this.connected = false;
      this.lastError = `Sem conexão com o OBS (${e.code || e.message})`;
      return false;
    }
  }

  /** Abre o OBS (se preciso) e conecta, esperando até timeoutMs. */
  async ensureRunning(timeoutMs = 60000) {
    if (await this.connect()) return true;
    await this.launch();
    const end = Date.now() + timeoutMs;
    while (Date.now() < end) {
      await sleep(2000);
      if (await this.connect()) return true;
    }
    return false;
  }

  async call(req, data) { return this.ws.call(req, data); }

  async isStreaming() {
    const s = await this.call('GetStreamStatus');
    return !!s.outputActive;
  }

  /** Cria/ajusta perfil, cena e fonte. Idempotente. Só altera perfil/vídeo quando não está transmitindo. */
  async ensureSetup() {
    if (this.setupDone) return;
    const o = this.o;
    const streaming = await this.isStreaming();

    if (!streaming) {
      // Perfil
      const pl = await this.call('GetProfileList');
      if (!pl.profiles.includes(o.profile)) { this.log('criando perfil', o.profile); await this.call('CreateProfile', { profileName: o.profile }); await sleep(1500); }
      else if (pl.currentProfileName !== o.profile) { await this.call('SetCurrentProfile', { profileName: o.profile }); await sleep(1500); }

      // Vídeo vertical
      const v = o.video;
      await this.call('SetVideoSettings', {
        baseWidth: v.baseWidth, baseHeight: v.baseHeight,
        outputWidth: v.outputWidth, outputHeight: v.outputHeight,
        fpsNumerator: v.fps, fpsDenominator: 1,
      });

      // Saída simples + reconexão automática
      const params = [
        ['Output', 'Mode', 'Simple'],
        ['SimpleOutput', 'VBitrate', String(o.videoBitrate)],
        ['SimpleOutput', 'ABitrate', String(o.audioBitrate)],
        ['Output', 'Reconnect', 'true'],
        ['Output', 'RetryDelay', '3'],
        ['Output', 'MaxRetries', '100'],
      ];
      for (const [parameterCategory, parameterName, parameterValue] of params) {
        await this.call('SetProfileParameter', { parameterCategory, parameterName, parameterValue });
      }

      // Coleção de cenas
      const cl = await this.call('GetSceneCollectionList');
      if (!cl.sceneCollections.includes(o.collection)) { this.log('criando coleção', o.collection); await this.call('CreateSceneCollection', { sceneCollectionName: o.collection }); await sleep(2500); }
      else if (cl.currentSceneCollectionName !== o.collection) { await this.call('SetCurrentSceneCollection', { sceneCollectionName: o.collection }); await sleep(2500); }
    }

    // Cena
    const sl = await this.call('GetSceneList');
    if (!sl.scenes.some((s) => s.sceneName === o.scene)) { this.log('criando cena', o.scene); await this.call('CreateScene', { sceneName: o.scene }); }

    // Fonte de navegador com o jogo
    const inputSettings = {
      url: o.overlayUrl, width: o.video.baseWidth, height: o.video.baseHeight,
      fps_custom: true, fps: o.video.fps, reroute_audio: true, shutdown: false, restart_when_active: false,
    };
    const il = await this.call('GetInputList');
    if (!il.inputs.some((i) => i.inputName === o.inputName)) {
      this.log('criando fonte', o.inputName);
      await this.call('CreateInput', { sceneName: o.scene, inputName: o.inputName, inputKind: 'browser_source', inputSettings, sceneItemEnabled: true });
    } else {
      await this.call('SetInputSettings', { inputName: o.inputName, inputSettings, overlay: true });
      // garante que a fonte está na cena
      const items = await this.call('GetSceneItemList', { sceneName: o.scene });
      if (!items.sceneItems.some((it) => it.sourceName === o.inputName)) await this.call('CreateSceneItem', { sceneName: o.scene, sourceName: o.inputName, sceneItemEnabled: true });
    }
    // Ajusta a fonte para ocupar a tela toda
    const { sceneItemId } = await this.call('GetSceneItemId', { sceneName: o.scene, sourceName: o.inputName });
    await this.call('SetSceneItemTransform', {
      sceneName: o.scene, sceneItemId,
      sceneItemTransform: { positionX: 0, positionY: 0, boundsType: 'OBS_BOUNDS_SCALE_INNER', boundsWidth: o.video.baseWidth, boundsHeight: o.video.baseHeight, boundsAlignment: 0, alignment: 5 },
    });
    await this.call('SetCurrentProgramScene', { sceneName: o.scene });
    this.setupDone = true;
    this.log('configuração do OBS ok');
  }

  async setStreamTarget(server, key) {
    await this.call('SetStreamServiceSettings', { streamServiceType: 'rtmp_custom', streamServiceSettings: { server, key, use_auth: false } });
  }

  /** Inicia a transmissão e espera confirmar. Retorna true/false. */
  async startStream(timeoutMs = 30000) {
    if (await this.isStreaming()) return true;
    await this.call('StartStream');
    const end = Date.now() + timeoutMs;
    while (Date.now() < end) {
      await sleep(1500);
      const s = await this.call('GetStreamStatus');
      if (s.outputActive && !s.outputReconnecting) return true;
    }
    return false;
  }

  async stopStream() {
    if (!(await this.isStreaming())) return true;
    await this.call('StopStream');
    for (let i = 0; i < 20; i++) { await sleep(1000); if (!(await this.isStreaming())) return true; }
    return false;
  }

  async status() {
    if (!this.connected) return { connected: false, streaming: false, error: this.lastError };
    try {
      const s = await this.call('GetStreamStatus');
      return { connected: true, streaming: s.outputActive, reconnecting: s.outputReconnecting, timecode: s.outputTimecode, bytes: s.outputBytes, skippedFrames: s.outputSkippedFrames, totalFrames: s.outputTotalFrames, error: this.lastError };
    } catch (e) {
      return { connected: false, streaming: false, error: e.message };
    }
  }
}
