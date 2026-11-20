import { createServer as createHttpServer } from 'node:http';
import { createHash, randomUUID } from 'node:crypto';
import { spawn } from 'node:child_process';
import { createRequire } from 'node:module';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { mkdtempSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';

const assetsDir = dirname(fileURLToPath(import.meta.url));
const rootDir = resolve(assetsDir, '..');
const frontendDir = join(rootDir, 'verdikt-frontend');
const roomId = '5d8b8ce2-89a1-4cfb-9419-2d2ea138d8e4';
const playerToken = '62dc78f1-ff1e-46e0-b88a-2c8603788ca0';
const players = [
  { id: 'f337c383-1ba3-46d1-932e-8cf519a98790', name: 'Amara', isHost: true, isActive: true },
  { id: '965ec96e-f110-40ce-95e8-6e994244c602', name: 'Kemi', isHost: false, isActive: true },
  { id: '78272792-e2fd-4625-a630-c1fa2f3f36d4', name: 'Tobi', isHost: false, isActive: true },
];
const questions = [
  'Who is most likely to turn a quick errand into an all-day adventure?',
  'Who would accidentally become famous on vacation?',
  'Who gives the best advice, then ignores their own?',
  'Who is most likely to move abroad on a whim?',
  'Who could win an argument with a customer-service bot?',
].map((text) => ({ id: randomUUID(), text, spiceLevel: 'MILD' }));

let roomName = 'The Group Chat Court';
let questionCount = 5;
let questionDurationSeconds = 15;
let apiOrigin = '';

function sendJson(response, status, body) {
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'Access-Control-Allow-Origin': apiOrigin,
    'Access-Control-Allow-Methods': 'GET,POST,PUT,DELETE,OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type,X-Player-Token',
  });
  response.end(body === undefined ? '' : JSON.stringify(body));
}

async function readJson(request) {
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  return JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}');
}

function reportCard() {
  return {
    overview: {
      roomName,
      totalPlayers: players.length,
      totalQuestions: questionCount,
      leaderboard: [
        { playerName: 'Tobi', totalVotesReceived: 11, topTitle: 'Most likely to say yes to anything' },
        { playerName: 'Kemi', totalVotesReceived: 8, topTitle: 'Best advice giver' },
        { playerName: 'Amara', totalVotesReceived: 5, topTitle: 'Most likely to make the plan' },
      ],
      verdict: 'The GC has spoken',
    },
    pollCards: questions.map((question, index) => ({
      questionId: question.id,
      questionText: question.text,
      orderIndex: index + 1,
      results: [
        { playerId: players[2].id, playerName: 'Tobi', voteCount: index === 0 ? 3 : 2, isWinner: true, votedByNames: ['Amara', 'Kemi', 'Zee'] },
        { playerId: players[1].id, playerName: 'Kemi', voteCount: 2, isWinner: false, votedByNames: ['Tobi', 'Zee'] },
        { playerId: players[0].id, playerName: 'Amara', voteCount: 1, isWinner: false, votedByNames: ['Kemi'] },
      ],
    })),
    playerCards: [
      { playerId: players[2].id, playerName: 'Tobi', titlesWon: ['Most likely to say yes to anything'], totalVotesReceived: 11 },
      { playerId: players[1].id, playerName: 'Kemi', titlesWon: ['Best advice giver'], totalVotesReceived: 8 },
      { playerId: players[0].id, playerName: 'Amara', titlesWon: ['Most likely to make the plan'], totalVotesReceived: 5 },
    ],
  };
}

const apiServer = createHttpServer(async (request, response) => {
  if (request.method === 'OPTIONS') return sendJson(response, 204);
  const url = new URL(request.url ?? '/', 'http://127.0.0.1');
  const path = url.pathname;

  if (path === '/ws/info') {
    response.writeHead(200, {
      'Content-Type': 'application/json; charset=utf-8',
      'Access-Control-Allow-Origin': apiOrigin,
      'Access-Control-Allow-Credentials': 'true',
    });
    return response.end(JSON.stringify({ websocket: true, origins: ['*:*'], cookie_needed: false, entropy: 42 }));
  }
  if (request.method === 'POST' && path === '/api/rooms') {
    const body = await readJson(request);
    roomName = body.name || roomName;
    questionCount = Number(body.questionCount || questionCount);
    questionDurationSeconds = body.questionDurationSeconds === null ? null : Number(body.questionDurationSeconds ?? 15);
    return sendJson(response, 201, {
      id: roomId, code: 'GC42FUN', name: roomName, status: 'WAITING', voteMode: body.voteMode || 'PUBLIC',
      maxPlayers: 10, maxQuestions: questionCount, questionDurationSeconds,
      players: [players[0]], playerToken,
    });
  }
  if (path === '/api/rooms/rejoin') {
    return sendJson(response, 200, {
      id: roomId, code: 'GC42FUN', name: roomName, status: 'WAITING', voteMode: 'PUBLIC',
      maxQuestions: questionCount, questionDurationSeconds, players, playerToken,
    });
  }
  if (path.endsWith('/game/preview-questions')) {
    return sendJson(response, 200, questions.slice(0, Number(url.searchParams.get('count') || questionCount)));
  }
  if (path.endsWith('/game/current-question')) {
    return sendJson(response, 200, {
      questionId: questions[0].id,
      text: questions[0].text,
      questionIndex: 1,
      totalQuestions: questionCount,
      startedAt: new Date().toISOString(),
    });
  }
  if (path.endsWith('/votes/current')) {
    return sendJson(response, 200, [
      { voterId: players[1].id, voterName: 'Kemi', votedForId: players[2].id },
      { voterId: players[2].id, voterName: 'Tobi', votedForId: players[2].id },
      { voterId: players[0].id, voterName: 'Amara', votedForId: players[1].id },
      { voterId: players[0].id, voterName: 'Amara', votedForId: players[2].id },
    ]);
  }
  if (path.endsWith('/game/start') || path.endsWith('/game/end') || path.endsWith('/votes')) {
    return sendJson(response, 200, path.endsWith('/votes') ? [] : undefined);
  }
  if (path.endsWith('/game/next-question')) return sendJson(response, 204);
  if (path.endsWith('/report-card')) return sendJson(response, 200, reportCard());
  return sendJson(response, 404, { message: `Fixture API route not found: ${path}` });
});

function websocketFrame(text) {
  const data = Buffer.from(text);
  if (data.length < 126) return Buffer.concat([Buffer.from([0x81, data.length]), data]);
  if (data.length < 65536) {
    const header = Buffer.alloc(4);
    header[0] = 0x81;
    header[1] = 126;
    header.writeUInt16BE(data.length, 2);
    return Buffer.concat([header, data]);
  }
  throw new Error('Fixture WebSocket frame is unexpectedly large.');
}

apiServer.on('upgrade', (request, socket) => {
  if (!request.url?.startsWith('/ws/')) return socket.destroy();
  const accept = createHash('sha1')
    .update(`${request.headers['sec-websocket-key']}258EAFA5-E914-47DA-95CA-C5AB0DC85B11`)
    .digest('base64');
  socket.write(`HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ${accept}\r\n\r\n`);
  socket.write(websocketFrame('o'));
  let buffer = Buffer.alloc(0);
  socket.on('data', (chunk) => {
    buffer = Buffer.concat([buffer, chunk]);
    while (buffer.length >= 2) {
      const opcode = buffer[0] & 0x0f;
      const masked = (buffer[1] & 0x80) !== 0;
      let length = buffer[1] & 0x7f;
      let offset = 2;
      if (length === 126) {
        if (buffer.length < 4) return;
        length = buffer.readUInt16BE(2);
        offset = 4;
      } else if (length === 127) {
        if (buffer.length < 10) return;
        length = Number(buffer.readBigUInt64BE(2));
        offset = 10;
      }
      const maskLength = masked ? 4 : 0;
      if (buffer.length < offset + maskLength + length) return;
      const mask = masked ? buffer.subarray(offset, offset + 4) : undefined;
      const payload = Buffer.from(buffer.subarray(offset + maskLength, offset + maskLength + length));
      if (mask) for (let i = 0; i < payload.length; i += 1) payload[i] ^= mask[i % 4];
      buffer = buffer.subarray(offset + maskLength + length);
      if (opcode === 0x8) return socket.end();
      if (opcode !== 0x1) continue;
      try {
        const messages = JSON.parse(payload.toString('utf8'));
        if (messages.some((message) => /^(CONNECT|STOMP)\n/.test(message))) {
          const connected = 'CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\u0000';
          socket.write(websocketFrame(`a${JSON.stringify([connected])}`));
        }
      } catch {
        // Ignore SockJS heartbeats and malformed fixture packets.
      }
    }
  });
  socket.on('error', () => {});
});

class DevTools {
  constructor(socket) {
    this.socket = socket;
    this.nextId = 0;
    this.pending = new Map();
    socket.addEventListener('message', ({ data }) => {
      const message = JSON.parse(data);
      if (!message.id) return;
      const pending = this.pending.get(message.id);
      if (!pending) return;
      this.pending.delete(message.id);
      if (message.error) pending.reject(new Error(message.error.message));
      else pending.resolve(message.result ?? {});
    });
  }

  call(method, params = {}) {
    const id = ++this.nextId;
    this.socket.send(JSON.stringify({ id, method, params }));
    return new Promise((resolvePromise, rejectPromise) => this.pending.set(id, { resolve: resolvePromise, reject: rejectPromise }));
  }

  async evaluate(expression) {
    const response = await this.call('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
    if (response.exceptionDetails) throw new Error(response.exceptionDetails.text || 'Browser evaluation failed.');
    return response.result?.value ?? Boolean(response.result?.objectId);
  }
}

function delay(milliseconds) {
  return new Promise((resolvePromise) => setTimeout(resolvePromise, milliseconds));
}

async function findAvailablePort() {
  const temporaryServer = createHttpServer();
  await new Promise((resolvePromise, rejectPromise) => {
    temporaryServer.once('error', rejectPromise);
    temporaryServer.listen(0, '127.0.0.1', resolvePromise);
  });
  const port = temporaryServer.address().port;
  await new Promise((resolvePromise) => temporaryServer.close(resolvePromise));
  return port;
}

async function waitFor(devtools, expression, description, timeout = 15000) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeout) {
    try {
      if (await devtools.evaluate(expression)) return;
    } catch {
      // Vite can briefly replace the document while opening a route.
    }
    await delay(150);
  }
  let browserState = 'unavailable';
  try {
    browserState = await devtools.evaluate("JSON.stringify({ href: location.href, title: document.title, body: document.body?.innerText.slice(0, 240) })");
  } catch {
    // Keep the timeout useful even if the document is closing.
  }
  throw new Error(`Timed out waiting for ${description}. Browser state: ${browserState}`);
}

async function clickText(devtools, label, selector = 'button, a') {
  const expression = `(() => { const target = [...document.querySelectorAll(${JSON.stringify(selector)})].find((element) => { const text = element.innerText.trim(); return text === ${JSON.stringify(label)} || text.split('\\n')[0] === ${JSON.stringify(label)}; }); if (!target) return false; target.click(); return true; })()`;
  if (!await devtools.evaluate(expression)) throw new Error(`Could not find “${label}”.`);
}

async function fillInput(devtools, selector, value) {
  const expression = `(() => { const element = document.querySelector(${JSON.stringify(selector)}); if (!element) return false; const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set; setter.call(element, ${JSON.stringify(value)}); element.dispatchEvent(new Event('input', { bubbles: true })); element.dispatchEvent(new Event('change', { bubbles: true })); return true; })()`;
  if (!await devtools.evaluate(expression)) throw new Error(`Could not find input ${selector}.`);
}

async function capture(devtools, name) {
  await devtools.evaluate('document.fonts.ready');
  await delay(900);
  const { data } = await devtools.call('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  writeFileSync(join(assetsDir, name), Buffer.from(data, 'base64'));
  console.log(`Captured readme-assets/${name}`);
}

let viteServer;
let chrome;
let devtools;
let chromeProfile;

try {
  await new Promise((resolvePromise, rejectPromise) => {
    apiServer.once('error', rejectPromise);
    apiServer.listen(0, '127.0.0.1', resolvePromise);
  });
  const apiPort = apiServer.address().port;
  process.env.VITE_API_URL = `http://127.0.0.1:${apiPort}`;

  const require = createRequire(import.meta.url);
  const viteModule = await import(pathToFileURL(require.resolve('vite', { paths: [frontendDir] })).href);
  const vitePort = await findAvailablePort();
  viteServer = await viteModule.createServer({
    root: frontendDir,
    configFile: join(frontendDir, 'vite.config.ts'),
    logLevel: 'error',
    server: { host: '127.0.0.1', port: vitePort, strictPort: true },
  });
  await viteServer.listen();
  apiOrigin = `http://127.0.0.1:${vitePort}`;
  const appUrl = `${apiOrigin}/`;

  const chromePath = process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
  chromeProfile = mkdtempSync(join(tmpdir(), 'verdikt-capture-'));
  chrome = spawn(chromePath, [
    '--headless=new', '--disable-gpu', '--no-sandbox', '--no-first-run', '--no-default-browser-check',
    '--remote-debugging-port=0', `--user-data-dir=${chromeProfile}`, '--window-size=1365,1100', 'about:blank',
  ], { stdio: 'ignore', windowsHide: true });
  chrome.once('error', (error) => { throw error; });

  let debugPort;
  for (let attempt = 0; attempt < 100; attempt += 1) {
    try {
      debugPort = Number(readFileSync(join(chromeProfile, 'DevToolsActivePort'), 'utf8').split(/\r?\n/)[0]);
      if (debugPort) break;
    } catch {
      await delay(100);
    }
  }
  if (!debugPort) throw new Error('Headless Chrome did not publish its DevTools port.');

  let targets = [];
  for (let attempt = 0; attempt < 50; attempt += 1) {
    try {
      targets = await (await fetch(`http://127.0.0.1:${debugPort}/json/list`)).json();
      if (targets.some((target) => target.type === 'page')) break;
    } catch {
      await delay(100);
    }
  }
  const pageTarget = targets.find((target) => target.type === 'page');
  if (!pageTarget) throw new Error('Headless Chrome did not open a page target.');
  devtools = new DevTools(new WebSocket(pageTarget.webSocketDebuggerUrl));
  await new Promise((resolvePromise, rejectPromise) => {
    devtools.socket.addEventListener('open', resolvePromise, { once: true });
    devtools.socket.addEventListener('error', rejectPromise, { once: true });
  });
  await devtools.call('Page.enable');
  await devtools.call('Runtime.enable');
  await devtools.call('Emulation.setDeviceMetricsOverride', { width: 1365, height: 1100, deviceScaleFactor: 1, mobile: false });

  console.log('Opening the local Verdikt UI and walking through a sample room...');
  await devtools.call('Page.navigate', { url: appUrl });
  await waitFor(devtools, "Boolean(document.querySelector('.home__headline'))", 'the Verdikt home screen');
  await devtools.evaluate("(() => { const style = document.createElement('style'); style.textContent = '* { scrollbar-width: none !important; } *::-webkit-scrollbar { display: none !important; }'; document.head.append(style); })()");
  await devtools.call('Emulation.setDeviceMetricsOverride', { width: 1365, height: 760, deviceScaleFactor: 1, mobile: false });
  await capture(devtools, 'verdikt-1.png');

  await devtools.call('Emulation.setDeviceMetricsOverride', { width: 1365, height: 1100, deviceScaleFactor: 1, mobile: false });
  await clickText(devtools, 'Start a round');
  await waitFor(devtools, "Boolean(document.querySelector('#roomName'))", 'the create-room screen');
  await fillInput(devtools, '#roomName', roomName);
  await fillInput(devtools, '#hostName', 'Amara');
  await clickText(devtools, '5');
  await clickText(devtools, 'No timer');
  await capture(devtools, 'verdikt-2.png');

  await clickText(devtools, 'Create room');
  await waitFor(devtools, "location.pathname.startsWith('/lobby/') && Boolean(document.querySelector('.lobby__copy'))", 'the room lobby');
  await waitFor(devtools, "document.body.innerText.includes('3 players')", 'the joined players');
  await devtools.evaluate("(() => { const link = document.querySelector('.lobby__invite-url'); if (link) { link.href = 'https://verdikt.online/join?code=GC42FUN'; link.textContent = link.href; } })()");
  await capture(devtools, 'verdikt-3.png');
  await clickText(devtools, 'Preview questions');
  await waitFor(devtools, "document.body.innerText.includes('playable')", 'the question preview');

  await clickText(devtools, 'Start the game');
  await waitFor(devtools, "location.pathname.startsWith('/play/') && Boolean(document.querySelector('.play__question')) && !document.querySelector('.play__status-card')", 'the live voting screen');
  await waitFor(devtools, "document.body.innerText.includes('Live sync stable')", 'the live room connection');
  await capture(devtools, 'verdikt-4.png');

  await devtools.evaluate("document.querySelector('.play__end-btn')?.click()");
  await waitFor(devtools, "document.body.innerText.includes('End the game now?')", 'the end-game confirmation');
  await clickText(devtools, 'End game');
  await waitFor(devtools, "location.pathname.startsWith('/report-card/') && Boolean(document.querySelector('.reportcard__intro-title'))", 'the results report card');
  await waitFor(devtools, `document.querySelector('.reportcard__intro-title')?.textContent === ${JSON.stringify(roomName)}`, 'the loaded results');
  await capture(devtools, 'verdikt-5.png');
} catch (error) {
  console.error(error);
  process.exitCode = 1;
} finally {
  if (chromeProfile) {
    try {
      const debugPort = Number(readFileSync(join(chromeProfile, 'DevToolsActivePort'), 'utf8').split(/\r?\n/)[0]);
      const browserInfo = await (await fetch(`http://127.0.0.1:${debugPort}/json/version`)).json();
      const browserSocket = new WebSocket(browserInfo.webSocketDebuggerUrl);
      const browserDevTools = new DevTools(browserSocket);
      await new Promise((resolvePromise, rejectPromise) => {
        browserSocket.addEventListener('open', resolvePromise, { once: true });
        browserSocket.addEventListener('error', rejectPromise, { once: true });
      });
      await Promise.race([browserDevTools.call('Browser.close').catch(() => {}), delay(500)]);
    } catch {
      // Fall back to stopping the exact Chrome process launched by this script.
    }
  }
  if (devtools?.socket.readyState === WebSocket.OPEN) devtools.socket.close();
  if (chrome && chrome.exitCode === null) {
    await Promise.race([
      new Promise((resolvePromise) => chrome.once('exit', resolvePromise)),
      delay(1500),
    ]);
  }
  if (chrome && chrome.exitCode === null) {
    await new Promise((resolvePromise) => {
      const killer = spawn('taskkill.exe', ['/PID', String(chrome.pid), '/T', '/F'], { stdio: 'ignore', windowsHide: true });
      const timeout = setTimeout(resolvePromise, 3000);
      timeout.unref();
      killer.once('close', resolvePromise);
      killer.once('error', resolvePromise);
    });
    if (chrome.exitCode === null) chrome.kill();
  }
  if (viteServer) await viteServer.close();
  if (apiServer.listening) await new Promise((resolvePromise) => apiServer.close(resolvePromise));
  if (chromeProfile && dirname(chromeProfile) === tmpdir()) {
    try {
      rmSync(chromeProfile, { recursive: true, force: true });
    } catch {
      console.warn(`Chrome left its temporary profile at ${chromeProfile}; remove it after Chrome releases the files.`);
    }
  }
}
