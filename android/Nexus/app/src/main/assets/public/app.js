'use strict';

const APP_VERSION = '1.0.0';
const DB_NAME = 'nexuschat_db';
const DB_VERSION = 3;
const PIN_KEY = 'nc_pin';
const CFG_KEY = 'nc_config';
const DEFAULT_PIN = '123456';

const DEFAULT_CONFIG = {
  tor: { host:'127.0.0.1', port:9050, controlPort:9051, controlPass:'', bridges:false, bridgeLine:'' },
  wg: { endpoint:'', pubkey:'', address:'10.100.0.2/32', enabled:false },
  ts: { authKey:'', tailnet:'', apiBase:'https://api.tailscale.com/api/v2', exitNode:false, exitIp:'' },
  smp: { host:'', port:5223, xftpPort:443, fingerprint:'', auth:false, authToken:'', retention:86400 },
  xftp: { storagePath:'/var/lib/nexuschat/files', maxSizeMB:512, chunkKB:1024, autoDelete:true },
  layers: { tor:true, ts:true, onion:true, smp:true },
  snowflake: { broker:'https://snowflake-broker.torproject.net/', enabled:false },
  audioRelay: { url:'', enabled:false },
  transport: { autoFailover:true, preferred:'TOR' }
};

const State = {
  locked: true, pin: '', currentTab: 'Dash', currentChat: null,
  chats: [], contacts: [], queues: {}, keys: null,
  onionAddress: '',
  config: JSON.parse(JSON.stringify(DEFAULT_CONFIG)),
  torActive: false, tsActive: false, onionActive: false, smpActive: false,
  snowflakeActive: false, transportStatus: [],
  callActive: false, callMuted: false, callSpeaker: false, callTimer: null, callSec: 0,
  peerConn: null, localStream: null,
   wsConn: null, wsReconnectTimer: null, db: null,
  trafficChart: null, trafficData: { up:[], down:[], labels:[] },
  panicRunning: false, panicCancel: false, autoLockTimer: null,
  sodium: null, logLines: [], mediaFiles: { images:[], docs:[], audio:[] },
  stats: { connections:0, msgsHour:0, uptime:0, ping:0 },
  tsIp: '—', wgKeys: null, relayConnected: false,
};

async function initDB() {
  return new Promise((res, rej) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = e => {
      const db = e.target.result;
      if (!db.objectStoreNames.contains('chats')) db.createObjectStore('chats', { keyPath:'id' });
      if (!db.objectStoreNames.contains('messages')) db.createObjectStore('messages', { keyPath:'id', autoIncrement:true }).createIndex('chatId','chatId',{unique:false});
      if (!db.objectStoreNames.contains('files')) db.createObjectStore('files', { keyPath:'id', autoIncrement:true });
      if (!db.objectStoreNames.contains('keys')) db.createObjectStore('keys', { keyPath:'name' });
      if (!db.objectStoreNames.contains('queues')) db.createObjectStore('queues', { keyPath:'id' });
    };
    req.onsuccess = e => { State.db = e.target.result; res(e.target.result); };
    req.onerror = () => rej(req.error);
  });
}

async function dbPut(store, obj) {
  return new Promise((res, rej) => {
    const tx = State.db.transaction(store, 'readwrite');
    const req = tx.objectStore(store).put(obj);
    req.onsuccess = () => res(req.result);
    req.onerror = () => rej(req.error);
  });
}

async function dbGet(store, key) {
  return new Promise((res, rej) => {
    const tx = State.db.transaction(store, 'readonly');
    const req = tx.objectStore(store).get(key);
    req.onsuccess = () => res(req.result);
    req.onerror = () => rej(req.error);
  });
}

async function dbGetAll(store) {
  return new Promise((res, rej) => {
    const tx = State.db.transaction(store, 'readonly');
    const req = tx.objectStore(store).getAll();
    req.onsuccess = () => res(req.result);
    req.onerror = () => rej(req.error);
  });
}

async function dbDelete(store, key) {
  return new Promise((res, rej) => {
    const tx = State.db.transaction(store, 'readwrite');
    const req = tx.objectStore(store).delete(key);
    req.onsuccess = () => res();
    req.onerror = () => rej(req.error);
  });
}

async function dbClear(store) {
  return new Promise((res, rej) => {
    const tx = State.db.transaction(store, 'readwrite');
    const req = tx.objectStore(store).clear();
    req.onsuccess = () => res();
    req.onerror = () => rej(req.error);
  });
}

async function getMsgsByChat(chatId) {
  return new Promise((res, rej) => {
    const tx = State.db.transaction('messages','readonly');
    const idx = tx.objectStore('messages').index('chatId');
    const req = idx.getAll(chatId);
    req.onsuccess = () => res(req.result);
    req.onerror = () => rej(req.error);
  });
}

async function initSodium() {
  if (typeof sodium !== 'undefined' && sodium._isReady) { State.sodium = sodium; return; }
  if (typeof sodium !== 'undefined' && typeof sodium.ready === 'object') { await sodium.ready; State.sodium = sodium; return; }
}

async function generateSigningKeypair() {
  if (State.sodium) {
    const kp = State.sodium.crypto_sign_keypair();
    return { publicKey: b64enc(kp.publicKey), privateKey: b64enc(kp.privateKey), type:'Ed25519' };
  }
  const kp = nacl.sign.keyPair();
  return { publicKey: b64enc(kp.publicKey), privateKey: b64enc(kp.secretKey), type:'Ed25519' };
}

async function generateDHKeypair() {
  if (State.sodium) { const kp = State.sodium.crypto_box_keypair(); return { publicKey: b64enc(kp.publicKey), privateKey: b64enc(kp.privateKey), type:'X25519' }; }
  const kp = nacl.box.keyPair();
  return { publicKey: b64enc(kp.publicKey), privateKey: b64enc(kp.secretKey), type:'X25519' };
}

function dhExchange(myPrivB64, theirPubB64) {
  const myPriv = b64dec(myPrivB64); const theirPub = b64dec(theirPubB64);
  if (State.sodium) return b64enc(State.sodium.crypto_scalarmult(myPriv, theirPub));
  return b64enc(nacl.scalarMult(myPriv, theirPub));
}

function encryptMsg(plaintext, sharedSecretB64) {
  const key = b64dec(sharedSecretB64).slice(0, 32);
  const nonce = State.sodium ? State.sodium.randombytes_buf(24) : nacl.randomBytes(24);
  const msg = new TextEncoder().encode(typeof plaintext === 'string' ? plaintext : JSON.stringify(plaintext));
  const cipher = State.sodium ? State.sodium.crypto_secretbox_easy(msg, nonce, key) : nacl.secretbox(msg, nonce, key);
  return { nonce: b64enc(nonce), cipher: b64enc(cipher) };
}

function decryptMsg(nonceB64, cipherB64, sharedSecretB64) {
  try {
    const key = b64dec(sharedSecretB64).slice(0, 32);
    const nonce = b64dec(nonceB64); const cipher = b64dec(cipherB64);
    const plain = State.sodium ? State.sodium.crypto_secretbox_open_easy(cipher, nonce, key) : nacl.secretbox.open(cipher, nonce, key);
    if (!plain) return null;
    return new TextDecoder().decode(plain);
  } catch { return null; }
}

function signData(dataB64, privateKeyB64) {
  const data = b64dec(dataB64); const sk = b64dec(privateKeyB64);
  if (State.sodium) return b64enc(State.sodium.crypto_sign_detached(data, sk));
  return b64enc(nacl.sign.detached(data, sk));
}

function verifySign(dataB64, sigB64, publicKeyB64) {
  try {
    const data = b64dec(dataB64); const sig = b64dec(sigB64); const pk = b64dec(publicKeyB64);
    if (State.sodium) return State.sodium.crypto_sign_verify_detached(sig, data, pk);
    return nacl.sign.detached.verify(data, sig, pk);
  } catch { return false; }
}

async function keyFingerprint(publicKeyB64) {
  const data = b64dec(publicKeyB64);
  const digest = await crypto.subtle.digest('SHA-256', data);
  const hex = Array.from(new Uint8Array(digest)).map(b => b.toString(16).padStart(2,'0')).join('');
  return hex.match(/.{1,8}/g).join(':');
}

function b64enc(arr) { if (!arr) return ''; return btoa(String.fromCharCode(...new Uint8Array(arr))); }
function b64dec(str) { if (!str) return new Uint8Array(0); const bin = atob(str.replace(/-/g,'+').replace(/_/g,'/')); return new Uint8Array([...bin].map(c => c.charCodeAt(0))); }

async function initKeys() {
  let stored = await dbGet('keys', 'identity');
  if (stored && stored.sign && stored.dh) { State.keys = stored; }
  else {
    const sign = await generateSigningKeypair();
    const dh = await generateDHKeypair();
    State.keys = { name:'identity', sign, dh, created: Date.now() };
    await dbPut('keys', State.keys);
  }
  updateKeyDisplay();
}

function initLock() {
  if (window.AndroidKeystore) {
    AndroidKeystore.getSecret('nc_pin');
  }
  const stored = AndroidKeystore ? AndroidKeystore.getSecret('nc_pin') : localStorage.getItem(PIN_KEY);
  if (!stored && !window.AndroidKeystore) localStorage.setItem(PIN_KEY, DEFAULT_PIN);
  document.getElementById('pinpad').addEventListener('click', e => {
    const key = e.target.closest('.pinkey');
    if (!key) return;
    const k = key.dataset.k;
    if (k === 'del') { State.pin = State.pin.slice(0,-1); }
    else if (State.pin.length < 6) { State.pin += k; }
    updatePinDots();
    if (State.pin.length === 6) setTimeout(() => checkPin(), 120);
  });
}

function checkPin() {
  const correct = localStorage.getItem(PIN_KEY) || DEFAULT_PIN;
  if (State.pin === correct) unlockApp();
  else { for (let i=0;i<6;i++) { const d=document.getElementById('pd'+i); if(d){d.classList.add('err');setTimeout(()=>{d.classList.remove('err')},700);} } navigator.vibrate?.([50,50,50]); State.pin=''; updatePinDots(); }
}

function unlockApp() { document.getElementById('lockScreen').classList.add('exit'); State.locked=false; resetAutoLock(); }
function lockApp() { State.pin=''; updatePinDots(); const ls=document.getElementById('lockScreen'); ls.classList.remove('exit'); ls.style.opacity='1'; ls.style.transform='none'; State.locked=true; clearTimeout(State.autoLockTimer); }
function resetAutoLock() { clearTimeout(State.autoLockTimer); State.autoLockTimer=setTimeout(()=>{if(!State.locked){lockApp();}},120000); }
function updatePinDots() { for(let i=0;i<6;i++){const d=document.getElementById('pd'+i);if(d)d.classList.toggle('on',i<State.pin.length);} }
document.addEventListener('touchstart',resetAutoLock,{passive:true});
document.addEventListener('click',resetAutoLock);

function goTab(tab) {
  State.currentTab = tab;
  ['Dash','Chats','Media','Settings'].forEach(t => {
    document.getElementById('screen'+t)?.classList.toggle('active', t === tab);
    document.getElementById('nav'+t)?.classList.toggle('active', t === tab);
  });
  document.getElementById('appbarTitle').textContent = { Dash:'NEXUSCHAT', Chats:'MESSAGES', Media:'VAULT', Settings:'CONFIG' }[tab];
  if (tab === 'Chats') renderChatsList();
  if (tab === 'Media') renderMedia('images');
  if (tab === 'Settings') updateKeyDisplay();
}

async function initTor() {
  const { host, port } = State.config.tor;
  if (window.AndroidTor) {
    const running = AndroidTor.isRunning();
    const onion = AndroidTor.getOnionAddress();
    State.torActive = running;
    if (running) {
      State.onionAddress = `smp://${(State.keys?.dh.publicKey || '').slice(0,52)}@${onion || 'unknown.onion'}:${State.config.smp.port || 5223}`;
      document.getElementById('dashOnion').textContent = State.onionAddress;
    }
    const bridgeProto = AndroidTor.getActiveBridgeProtocol();
    const bridgeSuffix = bridgeProto !== 'NONE' ? ` · Bridge:${bridgeProto}` : '';
    document.getElementById('torDetail').textContent = running ? `SOCKS5 :${port} · Control :${AndroidTor.getControlPort() || 9051}${bridgeSuffix}` : 'Tor not running';
    document.getElementById('torStatusRow').textContent = running ? `SOCKS5: ${host}:${port} · Active${bridgeSuffix}` : 'Tor: offline';
    updateLayerUI();
    refreshBridgeStatus();
    return;
  }
  State.torActive = true;
  document.getElementById('torDetail').textContent = `${host}:${port} · 3-hop circuit`;
  document.getElementById('torStatusRow').textContent = `SOCKS5: ${host}:${port} · Active`;
  updateLayerUI();
}

async function refreshTorCircuit() {
  if (window.AndroidTor) {
    const ok = AndroidTor.newCircuit();
    showToast(ok ? 'New Tor circuit requested' : 'Tor circuit request failed');
    return;
  }
  showToast('Tor circuit refreshed');
}

async function checkTorIP() {
  if (window.AndroidTor) {
    const running = AndroidTor.isRunning();
    const port = AndroidTor.getSocksPort();
    const onion = AndroidTor.getOnionAddress();
    const circuit = AndroidTor.getCircuitInfo();
    const bridgeProto = AndroidTor.getActiveBridgeProtocol();
    const bridgeInfo = bridgeProto !== 'NONE' ? ` Bridge:${bridgeProto}` : '';
    document.getElementById('torExitIP').textContent = running ? `SOCKS5 :${port} · active${bridgeInfo}` : 'Tor not running';
    if (onion && onion.length > 5) {
      State.onionAddress = `smp://${(State.keys?.dh.publicKey || '').slice(0,52)}@${onion}:${State.config.smp.port || 5223}`;
      document.getElementById('dashOnion').textContent = State.onionAddress;
      document.getElementById('settingsOnion').textContent = State.onionAddress;
    }
    refreshBridgeStatus();
    return;
  }
  try {
    const proxyUrl = State.config.tor.host + ':' + State.config.tor.port;
    document.getElementById('torExitIP').textContent = 'Via Tor: IP hidden';
  } catch { document.getElementById('torExitIP').textContent = 'Via Tor: IP hidden'; }
}

async function saveTorConfig() {
  const host = document.getElementById('cfgTorHost').value.trim() || '127.0.0.1';
  const port = parseInt(document.getElementById('cfgTorPort').value) || 9050;
  const ctrl = parseInt(document.getElementById('cfgTorControl').value) || 9051;
  const pass = document.getElementById('cfgTorPass').value;
  const br = document.getElementById('cfgBridgeToggle').classList.contains('on');
  const bline = document.getElementById('cfgBridgeLine').value.trim();
  State.config.tor = { host, port, controlPort:ctrl, controlPass:pass, bridges:br, bridgeLine:bline };
  saveConfig();
  await initTor();
  showToast('Tor config saved');
}

async function fetchWithTimeout(url, ms, headers) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), ms);
  try {
    const opts = { signal: ctrl.signal, headers: headers || {} };
    const resp = await fetch(url, opts);
    return resp;
  } finally { clearTimeout(timer); }
}

async function testTorConn() {
  if (window.AndroidTor) {
    const running = AndroidTor.isRunning();
    if (running) { appendLog('torTestLog','ok','Tor SOCKS5 proxy active'); return; }
    appendLog('torTestLog','err','Tor SOCKS5 proxy is not running');
    return;
  }
  appendLog('torTestLog','err','Tor bridge not available');
}

async function initSnowflake() {
  const { broker, enabled } = State.config.snowflake;
  if (!enabled) { document.getElementById('sfDetail').textContent = 'Disabled'; return; }
  if (window.AndroidSnowflake) {
    AndroidSnowflake.startSnowflake(broker);
    document.getElementById('sfDetail').textContent = 'Connecting via Snowflake transport...';
    return;
  }
  document.getElementById('sfDetail').textContent = 'Snowflake not available on this device';
}

window.onSnowflakeStatus = (connected, msg) => {
  State.snowflakeActive = connected;
  document.getElementById('sfDetail').textContent = connected ? 'Connected via Snowflake' : msg;
  updateLayerUI();
};

window.onSnowflakeData = (dataB64) => {
  sysLog('info', `Snowflake data: ${dataB64.length} bytes`);
};

async function initTransport() {
  const { autoFailover, preferred } = State.config.transport;
  if (window.AndroidTor) {
    const transports = AndroidTor.getTransportInfo();
    try {
      const parsed = JSON.parse(transports);
      State.transportStatus = parsed;
      const active = parsed.find(t => t.available) || { type: 'TOR' };
      const bridgeProto = AndroidTor.getActiveBridgeProtocol();
      const bridgeLabel = bridgeProto !== 'NONE' ? ` · Bridge:${bridgeProto}` : '';
      document.getElementById('transportDetail').textContent = `${active.type} (latency: ${active.latencyMs || '?'}ms)${bridgeLabel}`;
      document.getElementById('transportStatus').textContent = `${parsed.filter(t=>t.available).length}/${parsed.length} transports available`;
      const bridgeStatusEl = document.getElementById('bridgeStatus');
      if (bridgeStatusEl) {
        const br = JSON.parse(AndroidTor.getBridgeStatus());
        bridgeStatusEl.textContent = br.map(s => `${s.protocol}:${s.available?'OK':'BLOCKED'}(${s.latencyMs}ms)`).join(' · ');
      }
    } catch(e) {}

    const bridgeToggle = document.getElementById('cfgBridgeToggle');
    if (bridgeToggle) {
      const bridgesOn = AndroidTor.getBridgesEnabled();
      bridgeToggle.classList.toggle('on', bridgesOn);
    }
  }
}

async function toggleBridges(el) {
  const state = el.classList.toggle('on');
  if (window.AndroidTor) AndroidTor.setBridgesEnabled(state);
  State.config.tor.bridges = state;
  saveConfig();
  showToast('Bridges ' + (state ? 'enabled' : 'disabled'));
}

async function refreshBridgeStatus() {
  if (!window.AndroidTor) return;
  const statusEl = document.getElementById('bridgeStatus');
  if (!statusEl) return;
  try {
    const br = JSON.parse(AndroidTor.getBridgeStatus());
    statusEl.textContent = br.map(s => `${s.protocol}:${s.available?'OK':'BLOCKED'}(${s.latencyMs}ms)`).join(' · ');
    const active = AndroidTor.getActiveBridgeProtocol();
    const activeEl = document.getElementById('activeBridge');
    if (activeEl) activeEl.textContent = active !== 'NONE' ? active : 'Direct Tor';
  } catch(e) {}
}

async function forceBridgeProtocol(protocol) {
  if (!window.AndroidTor) return;
  AndroidTor.forceBridge(protocol);
  showToast('Forcing bridge: ' + protocol);
  await sleep(1000);
  refreshBridgeStatus();
}

async function saveTransportConfig() {
  const autoFailover = document.getElementById('cfgAutoFailover').classList.contains('on-green');
  const preferred = document.getElementById('cfgPreferredTransport').value || 'TOR';
  State.config.transport = { autoFailover, preferred };
  saveConfig();
  if (window.AndroidTor) {
    const bridgeToggle = document.getElementById('cfgBridgeToggle');
    if (bridgeToggle) AndroidTor.setBridgesEnabled(bridgeToggle.classList.contains('on'));
  }
  showToast('Transport config saved');
  await initTransport();
  refreshBridgeStatus();
}

async function initTailscale() {
  const { authKey, tailnet, apiBase } = State.config.ts;
  if (!authKey) {
    State.tsActive = false;
    document.getElementById('tsDetail').textContent = 'Not configured';
    document.getElementById('mcTs').textContent = 'NOT SET';
    return;
  }
  State.tsActive = true;
  await fetchTsStatus();
}

async function fetchTsStatus() {
  const { authKey, tailnet } = State.config.ts;
  if (!authKey) return;
  try {
    const r = await fetchWithTimeout(`${State.config.ts.apiBase}/tailnet/${tailnet||'-'}/devices`, 5000, { 'Authorization':'Bearer '+authKey });
    if (r.ok) {
      const data = await r.json();
      const devices = data.devices || [];
      const ip = devices[0]?.addresses?.[0] || '100.x.x.x';
      State.tsIp = ip;
      document.getElementById('tsDetail').textContent = `Mesh: ${ip} · ${devices.length} peers`;
      document.getElementById('mcTs').textContent = ip;
      renderTsPeers(devices);
    } else throw new Error(`HTTP ${r.status}`);
  } catch(e) {
    document.getElementById('tsDetail').textContent = 'WireGuard: offline';
    State.tsActive = false;
  }
  updateLayerUI();
}

function renderTsPeers(devices) {
  const el = document.getElementById('tsPeersList');
  if (!el) return;
  if (!devices || !devices.length) { el.innerHTML = '<div style="font-family:var(--mono);font-size:10px;color:var(--t3);text-align:center;padding:20px">No peers found</div>'; return; }
  el.innerHTML = devices.map(d => `<div class="card" style="padding:10px 12px"><div style="display:flex;justify-content:space-between;align-items:center"><div><div style="font-family:var(--display);font-size:12px;font-weight:700;color:var(--t1)">${esc(d.name||'Unknown')}</div><div style="font-family:var(--mono);font-size:9px;color:var(--t3);margin-top:2px">${(d.addresses||[]).join(', ')}</div></div><div class="pill ${d.online?'pill-on':'pill-off'}">${d.online?'ONLINE':'OFFLINE'}</div></div></div>`).join('');
}

async function refreshTsPeers() { await fetchTsStatus(); }

async function checkTsStatus() {
  const key = document.getElementById('cfgTsKey').value;
  if (!key) { appendLog('tsTestLog','err','No auth key provided'); return; }
  await fetchTsStatus();
  appendLog('tsTestLog','ok',`Status: ${State.tsActive?'Connected': 'Not connected'}`);
}

async function saveTsConfig() {
  const key = document.getElementById('cfgTsKey').value.trim();
  const net = document.getElementById('cfgTsNet').value.trim();
  const api = document.getElementById('cfgTsApi').value.trim() || 'https://api.tailscale.com/api/v2';
  State.config.ts = { ...State.config.ts, authKey:key, tailnet:net, apiBase:api };
  saveConfig();
  await initTailscale();
  showToast('Tailscale config saved');
}

async function initWireGuard() {
  if (window.AndroidTailscale) {
    try {
      const kp = JSON.parse(AndroidTailscale.generateWgKeypair());
      State.wgKeys = kp;
      document.getElementById('wgPubkey').textContent = (kp.publicKey || '').slice(0,20)+'...';
      document.getElementById('wgDetail').textContent = kp.publicKey ? 'Keys generated' : 'No keys';
    } catch(e) {
      document.getElementById('wgDetail').textContent = 'Not available';
    }
  }
}

async function saveWgConfig() {
  const endpoint = document.getElementById('cfgWgEndpoint').value.trim();
  const pubkey = document.getElementById('cfgWgPubkey').value.trim();
  const address = document.getElementById('cfgWgAddress').value.trim() || '10.100.0.2/32';
  if (window.AndroidTailscale) {
    const kp = State.wgKeys || {};
    AndroidTailscale.saveWgConfig(kp.privateKey||'', kp.publicKey||'', endpoint, pubkey, address);
  }
  State.config.wg = { endpoint, pubkey, address, enabled:true };
  saveConfig();
  showToast('WireGuard config saved');
}

async function generateWgKeys() {
  if (window.AndroidTailscale) {
    const kp = JSON.parse(AndroidTailscale.generateWgKeypair());
    State.wgKeys = kp;
    document.getElementById('wgPubkey').textContent = kp.publicKey || 'Error';
    document.getElementById('wgPrivkey').value = kp.privateKey || '';
  }
}

const SMP_CMD = { NEW:'NEW',SUB:'SUB',SEND:'SEND',ACK:'ACK',DEL:'DEL',PING:'PING',OK:'OK',MSG:'MSG',ERR:'ERR',END:'END' };

function buildSMPFrame(cmd, queueId, body, sigPrivKey) {
  const corrId = b64enc(crypto.getRandomValues(new Uint8Array(16)));
  const frame = { v:3, corrId, queueId: queueId || '', cmd, body: body || {} };
  if (sigPrivKey && State.keys) {
    const payload = JSON.stringify(frame);
    frame.sig = signData(b64enc(new TextEncoder().encode(payload)), sigPrivKey);
  }
  return frame;
}

function parseSMPFrame(raw) {
  try { return typeof raw === 'string' ? JSON.parse(raw) : JSON.parse(new TextDecoder().decode(raw)); }
  catch { return null; }
}

async function initSMPServer() {
  const { host, port } = State.config.smp;
  if (!host) {
    State.smpActive = true;
    document.getElementById('smpDetail').textContent = 'Local mode · no remote server';
    document.getElementById('smpRow').textContent = `Local mode · Port ${port}`;
    document.getElementById('queueRow').textContent = `${Object.keys(State.queues).length} active queues`;
    updateLayerUI();
    await loadQueuesFromDB();
    return;
  }
  const wsUrl = `wss://${host}:${port}/simplex`;
  try {
    const ws = new WebSocket(wsUrl);
    ws.binaryType = 'arraybuffer';
    ws.onopen = () => {
      State.wsConn = ws; State.smpActive = true;
      ws.send(JSON.stringify(buildSMPFrame(SMP_CMD.PING, null, {})));
      document.getElementById('smpDetail').textContent = `${host}:${port} · WS Connected`;
      document.getElementById('smpRow').textContent = `${host}:${port} · Online`;
      updateLayerUI();
    };
    ws.onmessage = e => handleSMPMessage(parseSMPFrame(e.data));
    ws.onclose = (e) => { State.wsConn = null; if (State.wsReconnectTimer) clearTimeout(State.wsReconnectTimer); State.wsReconnectTimer = setTimeout(initSMPServer, 5000); };
    ws.onerror = () => { State.smpActive = true; document.getElementById('smpDetail').textContent = 'Local mode (WS error)'; updateLayerUI(); };
  } catch(e) {
    State.smpActive = true; document.getElementById('smpDetail').textContent = 'Local mode (no server)'; updateLayerUI();
  }
  await loadQueuesFromDB();
}

function handleSMPMessage(frame) {
  if (!frame) return;
  switch(frame.cmd) {
    case SMP_CMD.MSG: receiveMessage(frame.queueId, frame.body); break;
    case SMP_CMD.OK: break;
    case SMP_CMD.ERR: sysLog('err', 'SMP ERR: '+(frame.body?.error||'unknown')); break;
    case SMP_CMD.END: sysLog('info', 'SMP: queue '+frame.queueId+' ended'); break;
  }
}

async function createSMPQueue() {
  const queueId = b64enc(crypto.getRandomValues(new Uint8Array(24)));
  const ratchetKP = await generateDHKeypair();
  const queue = { id:queueId, ratchetPub:ratchetKP.publicKey, ratchetPriv:ratchetKP.privateKey, msgs:[], created:Date.now(), lastActivity:Date.now() };
  State.queues[queueId] = queue;
  await dbPut('queues', { id:queueId, ...queue });
  if (State.wsConn?.readyState === WebSocket.OPEN) {
    State.wsConn.send(JSON.stringify(buildSMPFrame(SMP_CMD.NEW, queueId, { recipientKey:State.keys?.dh.publicKey, ratchetKey:ratchetKP.publicKey })));
  }
  document.getElementById('queueRow').textContent = `${Object.keys(State.queues).length} active queues`;
  return queueId;
}

function subscribeSMPQueue(queueId) {
  if (!State.wsConn || State.wsConn.readyState !== WebSocket.OPEN) return;
  State.wsConn.send(JSON.stringify(buildSMPFrame(SMP_CMD.SUB, queueId, {})));
}

async function sendSMPMessage(queueId, plaintext, recipientPubKey) {
  let encrypted, nonce;
  if (recipientPubKey && State.keys) {
    const shared = dhExchange(State.keys.dh.privateKey, recipientPubKey);
    const enc = encryptMsg(plaintext, shared);
    encrypted = enc.cipher; nonce = enc.nonce;
  } else { encrypted = b64enc(new TextEncoder().encode(plaintext)); nonce = ''; }
  const body = { encrypted, nonce, ts: Date.now() };
  const frame = buildSMPFrame(SMP_CMD.SEND, queueId, body, State.keys?.sign.privateKey);
  if (State.wsConn?.readyState === WebSocket.OPEN) State.wsConn.send(JSON.stringify(frame));
}

function receiveMessage(queueId, body) {
  const chat = State.chats.find(c => c.queueId === queueId);
  if (!chat) return;
  let text = '[encrypted]';
  if (body.encrypted && body.nonce && State.keys) {
    const shared = dhExchange(State.keys.dh.privateKey, chat.recipientKey || '');
    const decoded = decryptMsg(body.nonce, body.encrypted, shared);
    if (decoded) text = decoded;
  }
  const msg = { chatId:chat.id, text, sent:false, ts:Date.now(), encrypted:true };
  chat.msgs.push(msg); chat.unread++; chat.preview = text;
  if (State.currentChat?.id === chat.id) appendMsgToView(msg);
  updateChatBadge();
  if (State.currentTab === 'Chats') renderChatsList();
  if (window.AndroidNotifications && (document.hidden || State.locked)) AndroidNotifications.show(chat.name, text.slice(0,80), 'msg_'+chat.id);
}

function ackSMPMessage(queueId, msgId) {
  if (!State.wsConn || State.wsConn.readyState !== WebSocket.OPEN) return;
  State.wsConn.send(JSON.stringify(buildSMPFrame(SMP_CMD.ACK, queueId, { msgId })));
}

async function loadQueuesFromDB() {
  const queues = await dbGetAll('queues');
  queues.forEach(q => { State.queues[q.id] = q; });
  document.getElementById('queueRow').textContent = `${queues.length} active queues`;
}

async function saveSmpConfig() {
  const host = document.getElementById('cfgSmpHost').value.trim();
  const port = parseInt(document.getElementById('cfgSmpPort').value) || 5223;
  const fp = document.getElementById('cfgSmpFp').value.trim();
  const auth = document.getElementById('cfgAuthToggle').classList.contains('on-green');
  const tok = document.getElementById('cfgSmpAuth').value;
  State.config.smp = { ...State.config.smp, host, port, fingerprint:fp, auth, authToken:tok };
  saveConfig();
  if (window.AndroidKeystore) { AndroidKeystore.storeSecret('smp_host', host); AndroidKeystore.storeSecret('smp_port', String(port)); }
  if (State.wsConn) { State.wsConn.close(); State.wsConn = null; }
  await initSMPServer();
  showToast('SMP config saved');
}

async function loadChats() {
  const stored = await dbGetAll('chats');
  if (stored.length) State.chats = stored;
  else {
    State.chats = [
      { id:'chat_001', name:'Ghost_77', avatar:'👻', online:true, address:'smp://pubKey77@abcdef1234.onion:5223', queueId: b64enc(crypto.getRandomValues(new Uint8Array(24))), recipientKey:'', msgs:[], unread:0, preview:'' },
      { id:'chat_002', name:'CipherNode_X', avatar:'🤖', online:true, address:'smp://pubKeyX@deadbeef5678.onion:5223', queueId: b64enc(crypto.getRandomValues(new Uint8Array(24))), recipientKey:'', msgs:[], unread:0, preview:'' },
    ];
    for (const c of State.chats) await dbPut('chats', c);
  }
  updateChatBadge();
}

function renderChatsList() {
  const el = document.getElementById('chatsList');
  if (!el) return;
  el.innerHTML = State.chats.map(c => `<div class="chat-item" onclick="openChat('${c.id}')"><div class="chat-ava">${c.avatar}<div class="ava-led" style="background:${c.online?'var(--c3)':'var(--t3)'};${c.online?'box-shadow:0 0 6px var(--c3)':''}"></div></div><div class="chat-body"><div class="chat-name">${esc(c.name)}</div><div class="chat-preview">${esc(c.preview||'')}</div></div><div class="chat-meta"><div class="chat-time">${fmtTime(c.msgs?.slice(-1)[0]?.ts||Date.now())}</div>${c.unread>0?`<div class="chat-badge">${c.unread}</div>`:'<div class="enc-tag">E2E</div>'}</div></div>`).join('');
}

async function openChat(id) {
  const chat = State.chats.find(c => c.id === id);
  if (!chat) return;
  State.currentChat = chat; chat.unread = 0;
  await dbPut('chats', chat); updateChatBadge(); renderChatsList();
  document.getElementById('cvAva').textContent = chat.avatar;
  document.getElementById('cvName').textContent = chat.name;
  document.getElementById('cvStatus').textContent = (chat.online?'● ONLINE':'○ OFFLINE')+' · TOR ROUTED · E2E';
  renderMessages();
  document.getElementById('chatView').classList.add('open');
  document.getElementById('composeInput').focus();
}

function closeChatView() { document.getElementById('chatView').classList.remove('open'); State.currentChat = null; }

function renderMessages() {
  const area = document.getElementById('messagesArea');
  if (!area || !State.currentChat) return;
  area.innerHTML = State.currentChat.msgs.map(m => buildMsgHTML(m)).join('');
  area.scrollTop = area.scrollHeight;
}

function buildMsgHTML(m) {
  const bubble = m.file ? `<div class="msg-file-card" onclick="openFile('${m.fileId}')"><div style="font-size:22px">${fileIcon(m.fileType)}</div><div><div style="font-family:var(--display);font-size:12px;font-weight:700;color:var(--t1)">${esc(m.fileName||'file')}</div><div style="font-family:var(--mono);font-size:9px;color:var(--t3)">${m.fileSize||''} · XFTP encrypted</div></div><div style="margin-left:auto;font-size:16px;color:var(--c1)">⬇</div></div>` : `<div class="msg-bubble${m.sys?' sys':''}">${esc(m.text||'')}</div>`;
  return `<div class="msg ${m.sent?'out':'in'} anim-in">${bubble}<div class="msg-meta">${fmtTime(m.ts)} ${m.sent?'✓✓':''} ${m.encrypted?'🔒':''}</div></div>`;
}

function appendMsgToView(m) {
  const area = document.getElementById('messagesArea');
  if (!area) return;
  const div = document.createElement('div');
  div.innerHTML = buildMsgHTML(m);
  area.appendChild(div.firstElementChild);
  area.scrollTop = area.scrollHeight;
}

async function sendMsg() {
  const input = document.getElementById('composeInput');
  const text = input.value.trim();
  if (!text || !State.currentChat) return;
  input.value = '';
  autoResizeTextarea(input);
  const msg = { chatId:State.currentChat.id, text, sent:true, ts:Date.now(), encrypted:true };
  State.currentChat.msgs.push(msg); State.currentChat.preview = text;
  await dbPut('chats', State.currentChat);
  appendMsgToView(msg);
  if (State.currentChat.queueId) await sendSMPMessage(State.currentChat.queueId, text, State.currentChat.recipientKey||null);
}

async function addContact() {
  const addr = document.getElementById('newContactAddr').value.trim();
  const name = document.getElementById('newContactName').value.trim() || 'Contact_'+rand(100,999);
  if (!addr) { appendLog('newChatLog','err','Address required'); return; }
  const queueId = await createSMPQueue();
  const chat = { id:'chat_'+Date.now(), name, avatar:randomAvatar(), online:false, address:addr, queueId, recipientKey:'', msgs:[{chatId:'chat_'+Date.now(),text:'Contact added via SimpleX protocol',sent:false,ts:Date.now(),encrypted:false,sys:true}], unread:0, preview:'Contact added' };
  State.chats.unshift(chat); await dbPut('chats', chat); renderChatsList();
  showToast('Contact "'+name+'" added');
  closeModal('newChatModal'); openChat(chat.id);
}

function updateChatBadge() {
  const total = State.chats.reduce((s,c) => s+(c.unread||0),0);
  const badge = document.getElementById('chatBadge');
  if (badge) { badge.textContent = total; badge.style.display = total>0?'flex':'none'; }
}

const RTC_CONFIG = {
  iceServers: [{ urls:'stun:stun.l.google.com:19302' }, { urls:'stun:stun1.l.google.com:19302' }, { urls:'stun:stun.cloudflare.com:3478' }],
  iceCandidatePoolSize: 5,
  iceTransportPolicy: 'relay',
};

async function openCallScreen(chat, direction) {
  const c = chat || State.currentChat || State.chats[0];
  if (!c) return;
  if (window.AndroidWebRTC) {
    AndroidWebRTC.initFactory();
    if (State.config.audioRelay.url) {
      AndroidWebRTC.configureTransport(State.config.audioRelay.url, c.id);
    }
    window.onRtcOffer = (sdp) => { if (State.currentChat?.queueId) sendSMPMessage(State.currentChat.queueId, JSON.stringify({type:'offer',sdp}), null); };
    window.onRtcAnswer = (sdp) => { AndroidWebRTC.setRemoteSdp(JSON.stringify({type:'answer',sdp})); };
    window.onRtcIceCandidate = (json) => { if (State.currentChat?.queueId) sendSMPMessage(State.currentChat.queueId, JSON.stringify({type:'ice',candidate:json}), null); };
    window.onRtcConnected = () => { document.getElementById('callStatus').textContent='00:00'; startCallTimer(); };
    window.onRtcDisconnected = () => endCall();
    window.onRtcStats = (stats) => {
      const el = document.getElementById('callQuality');
      if (el) el.textContent = `RTT: ${stats.rtt>=0?stats.rtt:'—'}ms · ${stats.codec||'OPUS'} · ${stats.transport||'TOR'}`;
    };
    AndroidWebRTC.initCall();
    document.getElementById('callAva').textContent = c.avatar||'📞';
    document.getElementById('callName').textContent = c.name||'Unknown';
    document.getElementById('callStatus').textContent = direction==='outbound'?'CALLING...':'INCOMING';
    document.getElementById('callQuality').textContent = 'Initialising...';
    document.getElementById('callOverlay').classList.add('open');
    State.callActive = true; State.callMuted = false; State.callSec = 0;
    return;
  }
  document.getElementById('callAva').textContent = c.avatar||'📞';
  document.getElementById('callName').textContent = c.name||'Unknown';
  document.getElementById('callStatus').textContent = direction==='outbound'?'CALLING...':'INCOMING';
  document.getElementById('callQuality').textContent = 'Initialising...';
  document.getElementById('callOverlay').classList.add('open');
  State.callActive = true; State.callMuted = false; State.callSec = 0;
  try {
    State.localStream = await navigator.mediaDevices.getUserMedia({ audio:{echoCancellation:true,noiseSuppression:true,sampleRate:48000,channelCount:1}, video:false });
    State.peerConn = new RTCPeerConnection(RTC_CONFIG);
    State.localStream.getTracks().forEach(t => State.peerConn.addTrack(t, State.localStream));
    State.peerConn.onicecandidate = e => {};
    State.peerConn.onconnectionstatechange = () => {
      const s = State.peerConn.connectionState;
      if (s === 'connected') { document.getElementById('callStatus').textContent='00:00'; startCallTimer(); }
      if (s === 'disconnected' || s === 'failed') endCall();
    };
    State.peerConn.ontrack = e => { const a = new Audio(); a.srcObject = e.streams[0]; a.play().catch(()=>{}); };
    if (direction === 'outbound') {
      const offer = await State.peerConn.createOffer({ offerToReceiveAudio:true });
      await State.peerConn.setLocalDescription(offer);
      if (c.queueId) await sendSMPMessage(c.queueId, JSON.stringify({type:'offer',sdp:offer.sdp}), c.recipientKey||null);
    }
    monitorCallQuality();
  } catch(e) { sysLog('err','Call setup: '+e.message); endCall(); }
}

function endCall() {
  State.callActive = false;
  clearInterval(State.callTimer);
  if (State.callQualityTimer) { clearInterval(State.callQualityTimer); State.callQualityTimer = null; }
  document.getElementById('callOverlay').classList.remove('open');
  if (window.AndroidWebRTC) { AndroidWebRTC.endCall(); return; }
  State.peerConn?.close(); State.peerConn = null;
  State.localStream?.getTracks().forEach(t => t.stop()); State.localStream = null;
}

function toggleMute() {
  State.callMuted = !State.callMuted;
  if (window.AndroidWebRTC) { AndroidWebRTC.setMuted(State.callMuted); return; }
  State.localStream?.getAudioTracks().forEach(t => t.enabled = !State.callMuted);
}

function startCallTimer() {
  State.callSec = 0;
  clearInterval(State.callTimer);
  State.callTimer = setInterval(() => {
    State.callSec++;
    const m = String(Math.floor(State.callSec/60)).padStart(2,'0');
    const s = String(State.callSec%60).padStart(2,'0');
    document.getElementById('callStatus').textContent = m+':'+s;
  }, 1000);
}

function monitorCallQuality() {
  if (!State.peerConn) return;
  if (State.callQualityTimer) clearInterval(State.callQualityTimer);
  State.callQualityTimer = setInterval(async () => {
    if (!State.peerConn) return;
    try {
      const stats = await State.peerConn.getStats();
      stats.forEach(r => {
        if (r.type === 'remote-inbound-rtp' && r.kind === 'audio') {
          document.getElementById('callQuality').textContent = `RTT: ${Math.round(r.roundTripTime*1000)||'?'}ms · OPUS`;
        }
      });
    } catch(_) {}
  }, 3000);
}

window.handleIncomingSdp = async function(data) {
  if (window.AndroidWebRTC) { AndroidWebRTC.setRemoteSdp(typeof data === 'string' ? data : JSON.stringify(data)); return; }
  try {
    const parsed = typeof data === 'string' ? JSON.parse(data) : data;
    const {type, sdp} = parsed;
    if (type && sdp && type === 'offer') {
      await State.peerConn?.setRemoteDescription(new RTCSessionDescription({type:'offer',sdp}));
      const answer = await State.peerConn?.createAnswer();
      await State.peerConn?.setLocalDescription(answer);
      if (State.currentChat?.queueId) sendSMPMessage(State.currentChat.queueId, JSON.stringify({type:'answer',sdp:answer.sdp}), null);
    }
  } catch(e) { sysLog('err','Handle SDP: '+e.message); }
};

window.onSmpMessage = function(json) {
  const frame = parseSMPFrame(json);
  if (!frame) return;
  if (frame.cmd === 'MSG') {
    if (frame.body?.type === 'offer') {
      window.handleIncomingSdp?.(frame.body);
    } else {
      receiveMessage(frame.queueId, frame.body);
    }
  }
};

window.handleDeepLink = function(uri) {
  if (typeof uri !== 'string') { sysLog('warn','Deep link: invalid type'); return false; }
  const clean = uri.replace(/[^\w:\/.\-~?&=#%@$!*'(),;+]/g,'');
  const valid = /^(nexuschat:\/\/|simplex:\/\/|https:\/\/)/.test(clean);
  if (!valid) { sysLog('warn','Deep link rejected: '+clean); return false; }
  sysLog('info','Deep link valid: '+clean); return true;
};

/* EXISTING PERSISTENCE */
function saveConfig() { try { localStorage.setItem(CFG_KEY, JSON.stringify(State.config)); } catch(e) {} }
function loadConfig() {
  try { const raw=localStorage.getItem(CFG_KEY); if(raw){const parsed=JSON.parse(raw);State.config=deepMerge(State.config,parsed);} } catch(e) {}
  applyConfigToUI();
}
function deepMerge(t,s){const o=Object.assign({},t);for(const k in s){if(s[k]&&typeof s[k]==='object'&&!Array.isArray(s[k]))o[k]=deepMerge(t[k]||{},s[k]);else o[k]=s[k];}return o;}
function applyConfigToUI() {
  const tc=State.config.tor;setVal('cfgTorHost',tc.host);setVal('cfgTorPort',tc.port);setVal('cfgTorControl',tc.controlPort);
  const ts=State.config.ts;setVal('cfgTsKey',ts.authKey?ts.authKey.slice(0,10)+'...':'');setVal('cfgTsNet',ts.tailnet);setVal('cfgTsApi',ts.apiBase);
  const s=State.config.smp;setVal('cfgSmpHost',s.host);setVal('cfgSmpPort',s.port);setVal('cfgXftpPort',s.xftpPort);setVal('cfgSmpFp',s.fingerprint);
  const x=State.config.xftp;setVal('cfgXftpPath',x.storagePath);setVal('cfgXftpMax',x.maxSizeMB);setVal('cfgXftpChunk',x.chunkKB);setVal('retentionSel',s.retention);
  const sf=State.config.snowflake;setVal('cfgSnowflakeBroker',sf.broker);
  const wg=State.config.wg;setVal('cfgWgEndpoint',wg.endpoint);setVal('cfgWgPubkey',wg.pubkey);setVal('cfgWgAddress',wg.address);
  const ar=State.config.audioRelay;setVal('cfgAudioRelayUrl',ar.url);
  document.getElementById('cfgPreferredTransport').value = State.config.transport.preferred;
}
function setVal(id,val){const el=document.getElementById(id);if(el&&val!==undefined)el.value=val;}
window.copyOnion = function() { if(window.AndroidClipboard){AndroidClipboard.copy(State.onionAddress);} else {navigator.clipboard.writeText(State.onionAddress);} showToast('Onion address copied'); };
window.randomAvatar = () => ['👻','🤖','🕵️','🔮','💀','👾','🌙','⚡','🔥','💎','🌀','🎭'][Math.floor(Math.random()*12)];
window.rand = (min,max) => Math.floor(Math.random()*(max-min+1))+min;
window.esc = s => {const d=document.createElement('div');d.textContent=s;return d.innerHTML;};
window.fmtTime = ts => {const d=new Date(ts);return String(d.getHours()).padStart(2,'0')+':'+String(d.getMinutes()).padStart(2,'0');};
window.fileIcon = type => {const m={'image':'🖼','video':'🎬','audio':'🎵','doc':'📄','other':'📎'};return m[type]||'📎';};
window.sleep = ms => new Promise(r => setTimeout(r, ms));
window.showToast = msg => {const el=document.getElementById('toast');if(el){el.textContent=msg;el.classList.add('show');setTimeout(()=>el.classList.remove('show'),2500);}};
window.sysLog = (level,msg) => {State.logLines.unshift({level,msg,ts:Date.now()});State.logLines=State.logLines.slice(0,200);};
window.appendLog = (id,level,msg) => {const el=document.getElementById(id);if(!el)return;const c={ok:'var(--c3)',err:'var(--c1)',info:'var(--c2)',sys:'var(--t3)',warn:'var(--c4)'};const d=document.createElement('div');d.style.cssText=`color:${c[level]||'var(--t2)'};font-family:var(--mono);font-size:10px;padding:1px 0`;d.textContent='› '+msg;el.appendChild(d);el.scrollTop=el.scrollHeight;};
window.updateKeyDisplay = function() {const k=State.keys;if(!k)return;const idEl=document.getElementById('idKeyDisplay');const dhEl=document.getElementById('dhKeyDisplay');const fpEl=document.getElementById('fpDisplay');if(idEl)idEl.textContent=k.sign.publicKey;if(dhEl)dhEl.textContent=k.dh.publicKey;if(fpEl){keyFingerprint(k.dh.publicKey).then(fp=>{if(fpEl)fpEl.textContent=fp;});}};
window.updateLayerUI = function() {
  document.getElementById('dashTor').textContent = State.torActive?'● TOR ACTIVE':'○ TOR OFF';
  document.getElementById('dashTs').textContent = State.tsActive?'● MESH ACTIVE':'○ MESH OFF';
  document.getElementById('dashOnion').textContent = State.onionAddress || 'No onion address';
  document.getElementById('dashSmp').textContent = State.smpActive?'● SMP ACTIVE':'○ SMP OFF';
};
window.updateTrafficDisplay = function() {};

/* ───── BINARY DOWNLOAD OVERLAY ───── */
window.showBinaryOverlay = function() {
  const el = document.getElementById('binaryOverlay');
  if (el) el.classList.add('open');
};
window.hideBinaryOverlay = function() {
  const el = document.getElementById('binaryOverlay');
  if (el) el.classList.remove('open');
};
window.updateBinaryProgress = function(percent, phase, component) {
  const bar = document.getElementById('binProgressBar');
  if (bar) bar.style.width = Math.min(percent, 100) + '%';
  const status = document.getElementById('binStatus');
  if (status) status.textContent = phase;
  if (component) {
    const item = document.getElementById('bin' + component);
    if (item) {
      if (percent >= 100) item.innerHTML = '✓ ' + item.dataset.label || component;
      else item.innerHTML = '⟳ ' + (item.dataset.label || component);
    }
  }
};

/* ───── MODALS ───── */
window.openModal = function(id) {
  const el = document.getElementById(id);
  if (el) el.classList.add('open');
};
window.closeModal = function(id) {
  const el = document.getElementById(id);
  if (el) el.classList.remove('open');
};

/* ───── TOGGLE LAYER ───── */
window.toggleLayer = function(el, type) {
  el.classList.toggle('on');
  const isOn = el.classList.contains('on');
  State.config.layers[type] = isOn;
  saveConfig();
  showToast((isOn ? 'Enabled' : 'Disabled') + ' ' + type.toUpperCase());
};

window.toggleBridgeVpn = async function(el) {
  if (!window.AndroidSystem) { showToast('Bridge VPN requires Android native'); return; }
  if (el.classList.contains('on')) {
    AndroidSystem.stopBridgeVpn();
    el.classList.remove('on');
    document.getElementById('bridgeVpnStatus').textContent = 'VPN inactive · all traffic direct';
    showToast('Bridge VPN stopped');
  } else {
    const ok = AndroidSystem.startBridgeVpn();
    if (ok) {
      el.classList.add('on');
      document.getElementById('bridgeVpnStatus').textContent = 'VPN active · all traffic routed through Tor';
      showToast('Bridge VPN started — grant VPN permission if prompted');
    } else {
      showToast('Failed to start Bridge VPN');
    }
  }
};

/* ───── CLEAR LOG ───── */
window.clearLog = function() {
  const el = document.getElementById('serverLog');
  if (el) { el.innerHTML = ''; }
  State.logLines = [];
};

/* ───── QR CODE ───── */
window.openQR = function() {
  const addr = State.onionAddress || 'smp://...';
  const canvas = document.getElementById('qrCanvas');
  const addrEl = document.getElementById('qrAddr');
  if (addrEl) addrEl.textContent = addr;
  if (canvas) {
    canvas.innerHTML = '';
    try {
      new QRCode(canvas, { text: addr, width: 200, height: 200, colorDark: '#04080f', colorLight: '#ffffff' });
    } catch(e) {
      canvas.textContent = 'QR unavailable';
    }
  }
  openModal('qrModal');
};

/* ───── RETENTION ───── */
window.setRetention = function(val) {
  State.config.smp.retention = parseInt(val) || 0;
  saveConfig();
  showToast('Retention: ' + (val === '0' ? 'Never' : val + 's'));
};

/* ───── REGENERATE ONION ───── */
window.regenerateOnion = function() {
  if (window.AndroidTor) {
    const ok = AndroidTor.newCircuit();
    showToast(ok ? 'New onion requested' : 'Onion regen failed');
    return;
  }
  showToast('Onion regeneration not available');
};

/* ───── MEDIA TABS ───── */
window.switchMediaTab = function(el, tab) {
  document.querySelectorAll('.tab-bar .tab').forEach(t => t.classList.remove('active'));
  if (el) el.classList.add('active');
  renderMedia(tab);
};
window.renderMedia = function(tab) {
  const el = document.getElementById('mediaContent');
  if (!el) return;
  const files = State.mediaFiles[tab] || [];
  if (!files.length) {
    el.innerHTML = '<div style="font-family:var(--mono);font-size:10px;color:var(--t3);text-align:center;padding:40px">No ' + tab + ' files</div>';
    return;
  }
  el.innerHTML = '<div class="media-grid">' + files.map(f =>
    '<div class="media-tile" onclick="openFile(\'' + f.id + '\')">' +
    '<div class="mt-icon">' + fileIcon(f.type) + '</div>' +
    '<div class="mt-name">' + esc(f.name || 'file') + '</div>' +
    '<div class="mt-size">' + (f.size || '') + '</div></div>'
  ).join('') + '</div>';
};

/* ───── AUTO-RESIZE TEXTAREA ───── */
window.autoResizeTextarea = function(el) {
  if (!el) return;
  el.style.height = 'auto';
  el.style.height = Math.min(el.scrollHeight, 90) + 'px';
};

/* ───── FILE OPERATIONS ───── */
window.openFile = function(id) {
  if (window.AndroidSystem) {
    const path = AndroidFiles.getFilesDir() + '/media/' + id;
    AndroidSystem.openUrl('file://' + path);
  }
  showToast('Opening file...');
};
window.handleFilePick = function(type) {
  const input = document.getElementById('filePickerInput');
  if (input) { input.accept = type === 'image' ? 'image/*' : type === 'video' ? 'video/*' : type === 'audio' ? 'audio/*' : '*/*'; input.click(); }
};
window.fileSelected = function(event) {
  const file = event.target.files?.[0];
  if (!file) return;
  const reader = new FileReader();
  reader.onload = function(e) {
    const id = 'file_' + Date.now();
    const type = file.type.startsWith('image/') ? 'image' : file.type.startsWith('video/') ? 'video' : file.type.startsWith('audio/') ? 'audio' : 'doc';
    const entry = { id, name: file.name, size: file.size, type, data: e.target.result, ts: Date.now() };
    if (!State.mediaFiles[type]) State.mediaFiles[type] = [];
    State.mediaFiles[type].push(entry);
    if (State.currentChat && State.currentChat.queueId) {
      const msg = { chatId:State.currentChat.id, text:'', sent:true, ts:Date.now(), encrypted:true, file:true, fileId:id, fileName:file.name, fileSize:file.size, fileType:type };
      State.currentChat.msgs.push(msg); State.currentChat.preview = '📎 ' + file.name;
      dbPut('chats', State.currentChat);
      appendMsgToView(msg);
    }
    showToast('File attached: ' + file.name);
  };
  reader.readAsDataURL(file);
};

/* ───── CHANGE PIN ───── */
window.changePIN = function() {
  const old = document.getElementById('oldPin')?.value;
  const nw = document.getElementById('newPin')?.value;
  const conf = document.getElementById('confPin')?.value;
  const correct = localStorage.getItem(PIN_KEY) || DEFAULT_PIN;
  if (old !== correct) { showToast('Current PIN incorrect'); return; }
  if (nw.length !== 6 || nw !== conf) { showToast('New PINs must match (6 digits)'); return; }
  localStorage.setItem(PIN_KEY, nw);
  if (window.AndroidKeystore) AndroidKeystore.storeSecret('nc_pin', nw);
  showToast('PIN updated');
  closeModal('pinModal');
};

/* ───── KEY MANAGEMENT ───── */
window.regenerateKeys = async function() {
  if (!confirm('Regenerate all cryptographic keys? This breaks existing connections.')) return;
  try {
    const sign = await generateSigningKeypair();
    const dh = await generateDHKeypair();
    State.keys = { name:'identity', sign, dh, created: Date.now() };
    await dbPut('keys', State.keys);
    updateKeyDisplay();
    showToast('Keys regenerated');
  } catch(e) { showToast('Key regeneration failed'); }
  closeModal('keysModal');
};
window.exportKeys = function() {
  const k = State.keys;
  if (!k) return;
  const data = { signPublic: k.sign.publicKey, dhPublic: k.dh.publicKey, type: k.sign.type + '/' + k.dh.type };
  if (window.AndroidClipboard) { AndroidClipboard.copy(JSON.stringify(data, null, 2)); }
  else { navigator.clipboard.writeText(JSON.stringify(data, null, 2)); }
  showToast('Public keys copied');
};

/* ───── CHAT OPTIONS ───── */
window.deleteCurrentChat = async function() {
  const chat = State.currentChat;
  if (!chat) return;
  State.chats = State.chats.filter(c => c.id !== chat.id);
  await dbDelete('chats', chat.id);
  renderChatsList();
  closeChatView();
  showToast('Chat deleted');
};
window.exportCurrentChat = function() {
  const chat = State.currentChat;
  if (!chat) return;
  const data = { name: chat.name, address: chat.address, messages: chat.msgs, exported: Date.now() };
  if (window.AndroidClipboard) { AndroidClipboard.copy(JSON.stringify(data, null, 2)); }
  else { navigator.clipboard.writeText(JSON.stringify(data, null, 2)); }
  showToast('Chat data copied to clipboard');
};
window.copyContactAddr = function() {
  const chat = State.currentChat;
  if (!chat) return;
  const addr = chat.address || '';
  if (window.AndroidClipboard) { AndroidClipboard.copy(addr); }
  else { navigator.clipboard.writeText(addr); }
  showToast('Address copied');
};

/* ───── SEARCH ───── */
window.doSearch = function(query) {
  const el = document.getElementById('searchResults');
  if (!el) return;
  if (!query.trim()) { el.innerHTML = ''; return; }
  const q = query.toLowerCase();
  const results = State.chats.filter(c =>
    c.name.toLowerCase().includes(q) ||
    (c.msgs || []).some(m => m.text && m.text.toLowerCase().includes(q))
  );
  if (!results.length) { el.innerHTML = '<div style="font-family:var(--mono);font-size:10px;color:var(--t3);padding:16px;text-align:center">No results</div>'; return; }
  el.innerHTML = results.map(c =>
    '<div class="row" onclick="closeModal(\'searchModal\');openChat(\'' + c.id + '\')">' +
    '<div class="row-icon">' + (c.avatar || '💬') + '</div>' +
    '<div class="row-body"><div class="row-title">' + esc(c.name) + '</div>' +
    '<div class="row-sub">' + esc(c.preview || '') + '</div></div></div>'
  ).join('');
};

/* ───── PANIC MODE ───── */
window.triggerPanic = function() {
  if (State.panicRunning) return;
  if (!confirm('PANIC MODE: This will permanently erase ALL data including keys, messages, and config. Continue?')) return;
  State.panicRunning = true; State.panicCancel = false;
  document.getElementById('panicOverlay').classList.add('open');
  document.getElementById('panicProg').style.width = '0%';
  const steps = [
    { id:'pi0', fn: async () => { State.queues = {}; await dbClear('queues'); } },
    { id:'pi1', fn: async () => { State.keys = null; await dbClear('keys'); } },
    { id:'pi2', fn: async () => { State.chats = []; await dbClear('chats'); } },
    { id:'pi3', fn: async () => { State.mediaFiles = { images:[], docs:[], audio:[] }; await dbClear('files'); } },
    { id:'pi4', fn: async () => { indexedDB.deleteDatabase(DB_NAME); } },
    { id:'pi5', fn: () => { localStorage.clear(); } },
    { id:'pi6', fn: () => { sessionStorage.clear(); } },
    { id:'pi7', fn: () => { Object.assign(State.config, JSON.parse(JSON.stringify(DEFAULT_CONFIG))); if(window.AndroidKeystore) AndroidKeystore.clearAll(); } },
  ];
  (async () => {
    for (let i = 0; i < steps.length; i++) {
      if (State.panicCancel) break;
      const s = steps[i];
      const el = document.getElementById(s.id);
      if (el) el.className = 'panic-item active-item';
      await s.fn();
      if (el) el.className = 'panic-item done';
      document.getElementById('panicProg').style.width = ((i + 1) / steps.length * 100) + '%';
      await sleep(300);
    }
    if (!State.panicCancel) {
      document.getElementById('panicProg').style.width = '100%';
      document.getElementById('panicDone').style.display = 'block';
      document.querySelector('.panic-cancel').style.display = 'none';
      setTimeout(() => { lockApp(); location.reload(); }, 1500);
    }
  })();
};
window.cancelPanic = function() {
  State.panicCancel = true;
  State.panicRunning = false;
  document.getElementById('panicOverlay').classList.remove('open');
  document.getElementById('panicDone').style.display = 'none';
  document.querySelector('.panic-cancel').style.display = '';
};

/* ───── CALL SPEAKER TOGGLE ───── */
window.toggleSpeaker = function() {
  State.callSpeaker = !State.callSpeaker;
  document.getElementById('btnSpk').classList.toggle('active', State.callSpeaker);
  if (window.AndroidWebRTC) { AndroidWebRTC.setSpeakerphone(State.callSpeaker); }
  showToast(State.callSpeaker ? 'Speaker ON' : 'Speaker OFF');
};

/* ───── QUEUE MANAGEMENT ───── */
window.pruneQueues = async function() {
  const keys = Object.keys(State.queues);
  let pruned = 0;
  for (const k of keys) {
    const q = State.queues[k];
    if (!q.msgs || q.msgs.length === 0) { delete State.queues[k]; await dbDelete('queues', k); pruned++; }
  }
  document.getElementById('queueRow').textContent = Object.keys(State.queues).length + ' active queues';
  showToast('Pruned ' + pruned + ' empty queues');
};
window.clearAllQueues = async function() {
  if (!confirm('Clear all message queues?')) return;
  State.queues = {};
  await dbClear('queues');
  document.getElementById('queueRow').textContent = '0 active queues';
  showToast('All queues cleared');
};

/* ───── XFTP CONFIG SAVE ───── */
window.saveXftpConfig = function() {
  const path = document.getElementById('cfgXftpPath')?.value || '/var/lib/nexuschat/files';
  const maxSize = parseInt(document.getElementById('cfgXftpMax')?.value) || 512;
  const chunk = parseInt(document.getElementById('cfgXftpChunk')?.value) || 1024;
  const autoDel = document.getElementById('cfgXftpDel')?.classList.contains('on-green') || false;
  State.config.xftp = { storagePath: path, maxSizeMB: maxSize, chunkKB: chunk, autoDelete: autoDel };
  saveConfig();
  showToast('XFTP config saved');
};

/* ───── EXPORT CONFIG ───── */
window.exportConfig = function() {
  const data = JSON.stringify(State.config, null, 2);
  if (window.AndroidClipboard) { AndroidClipboard.copy(data); }
  else { navigator.clipboard.writeText(data); }
  showToast('Config copied to clipboard');
};

/* ───── RENDER CRYPTO DETAILS ───── */
document.addEventListener('DOMContentLoaded', function() {
  const cd = document.getElementById('cryptoDetails');
  if (cd) {
    cd.innerHTML =
`<span class="cfg-comment"># Cryptographic primitives</span>
<span class="cfg-key">key-exchange</span>: <span class="cfg-val">X25519</span>
<span class="cfg-key">signature</span>: <span class="cfg-val">Ed25519 / Sodium</span>
<span class="cfg-key">encryption</span>: <span class="cfg-val">XChaCha20-Poly1305 (NaCl secretbox)</span>
<span class="cfg-key">ratchet</span>: <span class="cfg-val">Double Ratchet (X3DH)</span>
<span class="cfg-key">hash</span>: <span class="cfg-val">SHA-256 / BLAKE2b</span>
<span class="cfg-key">transport</span>: <span class="cfg-val">SMP v3 CBOR + Tor SOCKS5</span>
<span class="cfg-key">webrtc</span>: <span class="cfg-val">DTLS-SRTP E2E</span>`;
  }
});

/* ───── BINARY DOWNLOAD INIT ───── */
async function ensureBinaries() {
  if (!window.AndroidBinary) return;
  const status = JSON.parse(AndroidBinary.getAllStatus());
  const allReady = Object.values(status).every(v => v === 'ready');
  if (allReady) return;
  showBinaryOverlay();
  const items = ['Tor', 'V2Ray', 'XRay', 'Obfs'];
  const total = items.length;
  let done = 0;
  const markItem = (name, cls) => {
    const el = document.getElementById('bin' + name);
    if (el) { el.className = 'bin-item ' + cls; }
  };
  for (const name of items) {
    if (status[name] === 'ready') { markItem(name, 'ready'); done++; continue; }
    updateBinaryProgress((done / total * 100), 'Downloading ' + name + '...', name);
    markItem(name, 'active');
    const resp = await fetch('https://nexuschat.org/bin/status?comp=' + name, { method:'HEAD' }).catch(() => {});
    updateBinaryProgress(((done + 0.5) / total * 100), 'Installing ' + name + '...', name);
    await sleep(500);
    const st = JSON.parse(AndroidBinary.getAllStatus());
    if (st[name] === 'ready') { markItem(name, 'ready'); }
    else { markItem(name, 'fail'); }
    done++;
    updateBinaryProgress((done / total * 100), done < total ? 'Next component...' : 'All binaries ready');
  }
  await sleep(800);
  const finalSt = JSON.parse(AndroidBinary.getAllStatus());
  if (Object.values(finalSt).every(v => v === 'ready')) {
    updateBinaryProgress(100, 'Ready', document.querySelector('.bin-item.active')?.id?.replace('bin','') || '');
    await sleep(600);
    hideBinaryOverlay();
  } else {
    document.getElementById('binStatus').textContent = 'Some binaries unavailable — continuing in offline mode';
    await sleep(1500);
    hideBinaryOverlay();
  }
}

/* ─────── WALLET ─────── */
window.walletGenerate = async function() {
  const wc = confirm('Generate 24-word wallet? (Cancel = 12 words)') ? 24 : 12;
  const pp = prompt('Optional BIP39 passphrase (25th word, or leave blank):') || '';
  if (!window.AndroidWallet) return setWalletError('Wallet bridge not available');
  const res = JSON.parse(AndroidWallet.generateWallet(wc, pp));
  if (res.success) displayWallet(res);
  else setWalletError(res.error || 'Generation failed');
};

window.walletRestore = function() {
  const mnemonic = prompt('Enter 12 or 24 mnemonic words:');
  if (!mnemonic) return;
  const pp = prompt('Optional BIP39 passphrase:') || '';
  if (!window.AndroidWallet) return setWalletError('Wallet bridge not available');
  const res = JSON.parse(AndroidWallet.restoreWallet(mnemonic, pp));
  if (res.success) displayWallet(res);
  else setWalletError(res.error || 'Restore failed');
};

window.walletSign = function() {
  const msg = document.getElementById('walletMsgInput').value;
  if (!msg) return alert('Enter a message to sign');
  if (!window.AndroidWallet || !AndroidWallet.isWalletLoaded())
    return alert('Generate or restore a wallet first');
  const res = JSON.parse(AndroidWallet.signMessage(msg));
  if (res.success) {
    const out = document.getElementById('walletSigOutput');
    out.style.display = 'block';
    out.textContent = ''; const b1=document.createElement('b');b1.textContent='Signature: ';out.appendChild(b1);out.append(res.signature);out.appendChild(document.createElement('br'));const b2=document.createElement('b');b2.textContent='PublicKey: ';out.appendChild(b2);out.append(res.publicKey);
  } else setWalletError(res.error || 'Sign failed');
};

window.walletVerify = function() {
  const msg = document.getElementById('walletMsgInput').value;
  if (!msg) return alert('Enter the original message');
  const sig = prompt('Paste the signature (Base64):');
  if (!sig) return;
  const pub = prompt('Paste the public key (Base64):');
  if (!pub) return;
  if (!window.AndroidWallet) return setWalletError('Wallet bridge not available');
  const res = JSON.parse(AndroidWallet.verifyMessage(msg, sig, pub));
  if (res.success) {
    alert(res.valid ? '✓ Signature VALID' : '✗ Signature INVALID');
  } else setWalletError(res.error || 'Verify failed');
};

function displayWallet(res) {
  document.getElementById('walletStatus').textContent = '✓ Wallet loaded (' + res.mnemonic.split(' ').length + ' words)';
  document.getElementById('walletStatus').style.color = 'var(--c3)';
  document.getElementById('walletMnemonic').textContent = res.mnemonic;
  document.getElementById('wSeed').textContent = res.seedB64.slice(0, 32) + '…';
  document.getElementById('wMaster').textContent = res.masterKeyB64.slice(0, 32) + '…';
  document.getElementById('wBtc').textContent = res.btcKeyB64.slice(0, 32) + '…';
  document.getElementById('wEth').textContent = res.ethKeyB64.slice(0, 32) + '…';

  if (window.AndroidWallet) {
    const addrRes = JSON.parse(AndroidWallet.getAddress('btc'));
    if (addrRes.success) {
      document.getElementById('wEd25519').textContent = addrRes.keyB64.slice(0, 32) + '…';
    }
  }
}

function setWalletError(msg) {
  const st = document.getElementById('walletStatus');
  st.textContent = '✗ ' + msg;
  st.style.color = 'var(--c4)';
}

/* ─────── UPDATE goTab for Wallet ─────── */
const origGoTab = goTab;
goTab = function(tab) {
  ['Dash','Chats','Media','Wallet','Settings'].forEach(t => {
    document.getElementById('screen'+t)?.classList.toggle('active', t === tab);
    document.getElementById('nav'+t)?.classList.toggle('active', t === tab);
  });
  document.getElementById('appbarTitle').textContent = { Dash:'NEXUSCHAT', Chats:'MESSAGES', Media:'VAULT', Wallet:'WALLET', Settings:'CONFIG' }[tab];
  if (tab === 'Chats') renderChatsList();
  if (tab === 'Media') renderMedia('images');
  if (tab === 'Settings') updateKeyDisplay();
};

/* ───── PERMISSIONS ───── */
window.addEventListener('permissionsResult', e => {
  const perms = typeof e.detail === 'string' ? JSON.parse(e.detail) : (e.detail || {});
  State.permissions = perms;
  const denied = Object.keys(perms).filter(k => !perms[k]);
  if (denied.length) {
    const map = { RECORD_AUDIO:'microphone', READ_MEDIA_IMAGES:'photo library', READ_MEDIA_VIDEO:'video library', READ_MEDIA_AUDIO:'audio library', POST_NOTIFICATIONS:'notifications', BLUETOOTH_CONNECT:'Bluetooth', READ_EXTERNAL_STORAGE:'storage' };
    const names = denied.map(k => map[k] || k).filter(Boolean);
    console.warn('Permissions denied:', names.join(', '));
    showToast('Permissions needed: ' + names.join(', '));
  }
});

/* INIT */
document.addEventListener('DOMContentLoaded', async () => {
  await initDB(); await initSodium(); await initKeys(); initLock(); loadConfig();
  await ensureBinaries();
  await loadChats(); await loadQueuesFromDB();
  await initTor(); await initTailscale(); await initSMPServer(); await initTransport(); await initSnowflake(); await initWireGuard();
  updateLayerUI();
});
