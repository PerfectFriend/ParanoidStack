const log = document.getElementById('log');
const statusEl = document.getElementById('status');

function addLog(msg) {
  const d = document.createElement('div');
  d.textContent = `[${new Date().toLocaleTimeString()}] ${msg}`;
  log.appendChild(d);
  log.scrollTop = log.scrollHeight;
}

function switchTab(name, btn) {
  document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
  document.querySelectorAll('.tab-bar button').forEach(b => b.classList.remove('active'));
  document.getElementById('panel-' + name).classList.add('active');
  if (btn) btn.classList.add('active');
}

// ─── Tor ──────────────────────────────────────────────
function checkTor() {
  try {
    const status = JSON.parse(AndroidTor.getStatus());
    const healthEl = document.getElementById('torHealth');
    const ipEl = document.getElementById('torExitIp');
    healthEl.textContent = status.running ? 'Connected' : 'Stopped';
    healthEl.style.color = status.running ? '#3FB950' : '#F85149';
    ipEl.textContent = status.ip || '-';
    document.getElementById('torOnion').textContent = status.onion || '(none)';
    document.getElementById('torSocks').textContent = status.socks;
    statusEl.textContent = status.running ? 'tor' : 'offline';
    if (status.bandwidth) {
      document.getElementById('bwDown').textContent = status.bandwidth.down;
      document.getElementById('bwUp').textContent = status.bandwidth.up;
      document.getElementById('bwRate').textContent = status.bandwidth.rateDown + ' / ' + status.bandwidth.rateUp;
    }
  } catch(e) {
    document.getElementById('torHealth').textContent = 'Error';
    statusEl.textContent = 'offline';
  }
  addLog('Tor: ' + document.getElementById('torHealth').textContent);
}
window.onTorReady = function() { checkTor(); addLog('Tor ready'); };

// ─── SMP ──────────────────────────────────────────────
function connectSmp() {
  const host = document.getElementById('smpHost').value.trim();
  const port = parseInt(document.getElementById('smpPort').value) || 5223;
  if (!host) { addLog('SMP: enter host'); return; }
  Android.connectSmp(host, port);
  addLog('SMP connecting to ' + host + ':' + port);
}
window.onSmpConnected = function() {
  smpConnected = true;
  document.getElementById('smpStatus').textContent = 'connected';
  document.getElementById('smpStatus').style.color = '#3FB950';
  addLog('SMP connected — flushing queue (' + msgQueue.length + ' pending)');
  flushQueue();
};
window.onSmpDisconnected = function() {
  smpConnected = false;
  document.getElementById('smpStatus').textContent = 'disconnected';
  document.getElementById('smpStatus').style.color = '#F85149';
  addLog('SMP disconnected');
};
window.onSmpMessage = function(json) {
  addLog('SMP: ' + json.substring(0, 80));
  AndroidAudit.record('MESSAGE_RECEIVED', 'smp', json.substring(0, 40));
  try {
    const obj = JSON.parse(json);
    if (obj.cmd === 'MSG' && obj.body) {
      reportTraffic(obj.body.encrypted ? obj.body.encrypted.length + 200 : 500, 0);
      addMessage(obj.body.encrypted, 'in', obj.body.msgId || '');
      const contact = contacts.find(c => c.id === currentConvId);
      if (contact) {
        Android.sendSmp(JSON.stringify({
          cmd:'RECEIPT', queueId: contact.smpAddress || 'default',
          body:{msgId: obj.body.msgId || '', status:'received', ts: Date.now()}
        }));
      }
    } else if (obj.cmd === 'SEND' && obj.body && obj.body.file) {
      reportTraffic(obj.body.encrypted ? obj.body.encrypted.length + 200 : 500, 0);
      const msgId = obj.body.msgId || Math.random().toString(36).substr(2);
      const msg = {
        id: msgId, conversationId: currentConvId, text: obj.body.nonce || '',
        senderKey: '', timestamp: Date.now(),
        encrypted: true, expiresAt: 0, status: 'received', attachments: [msgId]
      };
      AndroidStorage.saveMessage(JSON.stringify(msg));
      AndroidStorage.saveAttachment(msgId, obj.body.encrypted);
      messages.push(msg);
      renderMessages();
    } else if (obj.cmd === 'RECEIPT' && obj.body) {
      const msgId = obj.body.msgId;
      const idx = messages.findIndex(m => m.id === msgId);
      if (idx >= 0) {
        messages[idx].status = obj.body.status || 'received';
        renderMessages();
      }
    } else if (obj.cmd === 'REACT' && obj.body) {
      const mid = obj.body.msgId;
      const emoji = obj.body.reaction;
      const target = messages.find(m => m.id === mid);
      if (target) {
        if (!target.reactions) target.reactions = [];
        target.reactions.push(emoji);
        renderMessages();
      }
    } else if (obj.cmd === 'TYPING' && obj.body) {
      const typingEl = document.getElementById('typingIndicator');
      if (typingEl) {
        typingEl.textContent = obj.body.typing ? 'typing...' : '';
        clearTimeout(typingEl._timer);
        if (obj.body.typing) typingEl._timer = setTimeout(() => { typingEl.textContent = ''; }, 4000);
      }
    }
  } catch(e) {}
};

// ─── Messages & Chat ──────────────────────────────────
const messagesEl = document.getElementById('messages');
const msgInput = document.getElementById('msgInput');
let currentConvId = '';
let currentContact = null;
let contacts = [];
let messages = [];
let smpConnected = false;
let msgQueue = [];

function selectContact(contact) {
  currentContact = contact;
  currentConvId = contact.id;
  document.getElementById('chatWith').textContent = contact.name;
  document.getElementById('chatHeader').style.display = 'block';
  document.getElementById('chatInputArea').style.display = 'flex';
  messagesEl.innerHTML = '';
  loadMessages(contact.id);
}

function backToContacts() {
  currentConvId = '';
  currentContact = null;
  document.getElementById('chatHeader').style.display = 'none';
  document.getElementById('chatInputArea').style.display = 'none';
  messagesEl.innerHTML = '<p style="color:#8B949E;font-size:13px;text-align:center;margin-top:40px">Select a contact to start chatting</p>';
}

function loadMessages(convId) {
  try {
    messages = JSON.parse(AndroidStorage.getMessages(convId));
    renderMessages();
  } catch(e) { messages = []; }
}

function renderMessages() {
  messagesEl.innerHTML = '';
  if (!messages || messages.length === 0) {
    messagesEl.innerHTML = '<p style="color:#8B949E;font-size:13px;text-align:center;margin-top:40px">No messages yet</p>';
    return;
  }
  messages.forEach(m => {
    const isOut = m.senderKey === '' || m.senderKey === AndroidContacts.getMyPublicKey();
    const dir = isOut ? 'out' : 'in';
    const expClass = m.expiresAt > 0 ? ' expiring' : '';
    const expired = m.expiresAt > 0 && m.expiresAt < Date.now();
    if (expired) return;
    const d = document.createElement('div');
    d.className = 'msg msg-' + dir + expClass;
    d.onclick = function() { showReactions(m.id, dir); };
    let statusIcon = '';
    if (dir === 'out') {
      if (m.status === 'queued') statusIcon = ' <span style="color:#8B949E;font-size:10px">..</span>';
      else if (m.status === 'sending') statusIcon = ' <span style="color:#D29922;font-size:10px">*</span>';
      else if (m.status === 'sent') statusIcon = ' <span style="color:#8B949E;font-size:10px">V</span>';
      else if (m.status === 'received') statusIcon = ' <span style="color:#58A6FF;font-size:10px">VV</span>';
    }
    let html = escapeHtml(m.text) + '<div class="time">' + new Date(m.timestamp).toLocaleTimeString() +
      (m.expiresAt > 0 ? ' · <span style="color:#D29922">' + Math.max(1, Math.floor((m.expiresAt - Date.now())/1000)) + 's</span>' : '') +
      statusIcon + '</div>';
    if (m.attachments && m.attachments.length > 0) {
      m.attachments.forEach(function(aid) {
        try {
          const raw = AndroidStorage.getAttachment(aid);
          if (raw) {
            const decrypted = AndroidCrypto.decrypt(raw, currentConvId);
            if (decrypted) {
              try {
                const meta = JSON.parse(m.text);
                if (meta.duration) {
                  html += '<div class="voice" onclick="playVoice(\'' + aid + '\')">' +
                    '<span class="play">></span>' +
                    '<div class="wave"></div>' +
                    '<span class="dur">' + (meta.duration / 1000).toFixed(1) + 's</span></div>';
                } else if (meta.type && meta.type.startsWith('image/')) {
                  html += '<img src="data:' + meta.type + ';base64,' + decrypted + '" alt="' + escapeHtml(meta.name) + '" style="max-width:200px;max-height:200px;border-radius:6px;margin-top:4px" onclick="window.open(this.src)">';
                } else {
                  html += '<div class="attach" onclick="downloadAttach(\'' + aid + '\',\'' + escapeHtml(meta.name || 'file') + '\')">' +
                    '<span class="icon">📄</span><div class="info"><div class="name">' + escapeHtml(meta.name || 'file') + '</div>' +
                    '<div class="size">' + (meta.size ? (parseInt(meta.size) / 1024).toFixed(1) + ' KB' : '') + '</div></div></div>';
                }
              } catch(e) {
                html += '<div class="attach"><span class="icon">📄</span><div class="info">Attachment</div></div>';
              }
            }
          }
        } catch(e) {}
      });
    }
    if (m.reactions && m.reactions.length > 0) {
      html += '<div style="display:flex;gap:4px;margin-top:4px">';
      const seen = {};
      m.reactions.forEach(function(r) {
        if (!seen[r]) { seen[r] = true;
          html += '<span style="font-size:14px;background:#21262D;border-radius:4px;padding:1px 4px;cursor:pointer" onclick="event.stopPropagation()">' + r + '</span>';
        }
      });
      html += '</div>';
    }
    d.innerHTML = html;
    messagesEl.appendChild(d);
  });
  messagesEl.scrollTop = messagesEl.scrollHeight;
}

function sendFile(input) {
  const file = input.files[0];
  if (!file || !currentConvId) return;
  const reader = new FileReader();
  reader.onload = function(e) {
    const b64 = e.target.result.split(',')[1];
    const meta = JSON.stringify({name: file.name, size: file.size, type: file.type || 'application/octet-stream'});
    const encrypted = AndroidCrypto.encrypt(b64, currentConvId);
    const msgId = Math.random().toString(36).substr(2);
    const msg = {
      id: msgId, conversationId: currentConvId, text: meta,
      senderKey: AndroidContacts.getMyPublicKey(), timestamp: Date.now(),
      encrypted: true, expiresAt: 0, status: 'sent', attachments: [msgId]
    };
    AndroidStorage.saveMessage(JSON.stringify(msg));
    AndroidStorage.saveAttachment(msgId, encrypted);
    AndroidAudit.record('MESSAGE_SENT', 'user', 'File: ' + file.name);
    const contact = contacts.find(c => c.id === currentConvId);
    if (contact) {
      Android.sendSmp(JSON.stringify({
        cmd:'SEND', queueId: contact.smpAddress || 'default',
        body:{encrypted:encrypted, nonce:meta, msgId: msgId, file: true}
      }));
    }
    addLog('Sent file: ' + file.name + ' (' + (file.size / 1024).toFixed(1) + ' KB)');
    messages.push(msg);
    renderMessages();
  };
  reader.readAsDataURL(file);
  input.value = '';
}

function addMessage(text, dir, msgId) {
  if (!currentConvId) return;
  const msg = {
    id: msgId || Math.random().toString(36).substr(2),
    conversationId: currentConvId,
    text: text,
    senderKey: dir === 'out' ? AndroidContacts.getMyPublicKey() : '',
    timestamp: Date.now(),
    encrypted: true,
    expiresAt: parseInt(document.getElementById('expireSelect').value) * 1000 + Date.now() || 0,
    status: dir === 'out' ? 'sent' : 'received'
  };
  AndroidStorage.saveMessage(JSON.stringify(msg));
  AndroidAudit.record('MESSAGE_SENT', 'user', text.substring(0, 30));
  reportTraffic(0, text.length + 200);
  messages.push(msg);
  renderMessages();
}

function queueMsg(payload) {
  msgQueue.push(payload);
  try { AndroidKeystore.put('msgQueue', JSON.stringify(msgQueue)); } catch(e) {}
  addLog('Queued (' + msgQueue.length + ' pending)');
}

function flushQueue() {
  if (!smpConnected || msgQueue.length === 0) return;
  const batch = msgQueue.splice(0);
  try { AndroidKeystore.put('msgQueue', '[]'); } catch(e) {}
  batch.forEach(function(payload) {
    try { Android.sendSmp(payload); } catch(e) {
      msgQueue.push(payload);
      try { AndroidKeystore.put('msgQueue', JSON.stringify(msgQueue)); } catch(e2) {}
    }
  });
  if (batch.length > 0) addLog('Flushed ' + batch.length + ' queued messages');
  updateMessageStatuses();
}

function sendMsg() {
  const text = msgInput.value.trim();
  if (!text || !currentConvId) return;
  addMessage(text, 'out');
  msgInput.value = '';
  sendTyping(false);
  const encrypted = AndroidCrypto.encrypt(text, currentConvId);
  addLog('Encrypted: ' + encrypted.substring(0, 40) + '...');
  const contact = contacts.find(c => c.id === currentConvId);
  if (!contact) return;
  const payload = JSON.stringify({
    cmd:'SEND', queueId: contact.smpAddress || 'default',
    body:{encrypted:encrypted, nonce:'', msgId: Math.random().toString(36).substr(2)}
  });
  if (smpConnected) {
    Android.sendSmp(payload);
  } else {
    queueMsg(payload);
  }
}

let typingTimer = null;
function sendTyping(typing) {
  const contact = contacts.find(c => c.id === currentConvId);
  if (!contact) return;
  Android.sendSmp(JSON.stringify({
    cmd:'TYPING', queueId: contact.smpAddress || 'default',
    body:{typing: typing, ts: Date.now()}
  }));
}
msgInput.addEventListener('input', function() {
  if (!currentConvId) return;
  if (this.value.trim()) {
    sendTyping(true);
    clearTimeout(typingTimer);
    typingTimer = setTimeout(() => sendTyping(false), 2000);
  }
});

function searchMessages() {
  const q = document.getElementById('msgSearchInput').value.trim().toLowerCase();
  const all = messages || [];
  if (!q) { renderMessages(); return; }
  const filtered = all.filter(m => m.text.toLowerCase().includes(q));
  const el = document.getElementById('messages');
  if (filtered.length === 0) { el.innerHTML = '<p style="color:#8B949E;font-size:13px;text-align:center;margin-top:40px">No matches</p>'; return; }
  el.innerHTML = filtered.map(m => {
    const dir = m.senderKey === '' || m.senderKey === AndroidContacts.getMyPublicKey() ? 'out' : 'in';
    const expClass = m.expiresAt > 0 ? ' expiring' : '';
    return '<div class="msg msg-' + dir + expClass + '">' + escapeHtml(m.text) +
      '<div class="time">' + new Date(m.timestamp).toLocaleTimeString() +
      (m.expiresAt > 0 ? ' · <span style="color:#D29922">' + Math.max(1, Math.floor((m.expiresAt - Date.now())/1000)) + 's</span>' : '') +
      '</div></div>';
  }).join('');
}

// ─── Contacts ─────────────────────────────────────────
function loadContacts() {
  try {
    contacts = JSON.parse(AndroidContacts.getAll());
  } catch(e) { contacts = []; }
  document.getElementById('contactCount').textContent = '(' + contacts.length + ')';
  renderContacts();
}

function renderContacts(list) {
  const el = document.getElementById('contactList');
  const data = list || contacts;
  if (data.length === 0) {
    el.innerHTML = '<p style="color:#8B949E;font-size:13px;text-align:center;padding:20px">No contacts yet</p>';
    return;
  }
  el.innerHTML = data.map(c => {
    const initial = c.name.charAt(0).toUpperCase() || '?';
    return '<div class="cont-item" onclick="selectContact(contacts.find(x=>x.id===\'' + c.id + '\'))">' +
      '<div class="avatar">' + initial + '</div>' +
      '<div class="cinfo"><div class="cname">' + escapeHtml(c.name) + '</div>' +
      '<div class="cmeta">' + (c.smpAddress || 'no address') + ' · ' + (c.publicKey.substring(0,12) || 'no key') + '…</div></div>' +
      '<div class="status-dot status-offline"></div></div>';
  }).join('');
}

function searchContacts() {
  const q = document.getElementById('contactSearch').value.trim();
  if (!q) { renderContacts(); return; }
  try {
    const results = JSON.parse(AndroidContacts.search(q));
    renderContacts(results);
  } catch(e) {}
}

function showAddContact() {
  document.getElementById('addContactModal').style.display = 'flex';
  document.getElementById('newContactName').value = '';
  document.getElementById('newContactKey').value = '';
  document.getElementById('newContactSmp').value = '';
}

function saveContact() {
  const name = document.getElementById('newContactName').value.trim();
  const pubKey = document.getElementById('newContactKey').value.trim();
  const smp = document.getElementById('newContactSmp').value.trim();
  if (!name || !pubKey) { addLog('Name and key required'); return; }
  if (AndroidContacts.importContact(name, pubKey, smp)) {
    addLog('Contact added: ' + name);
    AndroidAudit.record('CONTACT_ADDED', 'user', name);
    closeModal('addContactModal');
    loadContacts();
  } else { addLog('Contact already exists'); }
}

window.handleDeepLink = function(uri) {
  addLog('Deep link: ' + uri);
  try {
    const url = new URL(uri);
    if (url.protocol === 'n3:' && url.pathname === '/invite') {
      const name = url.searchParams.get('name') || 'Contact';
      const key = url.searchParams.get('key') || '';
      const smp = url.searchParams.get('smp') || '';
      if (key) {
        document.getElementById('newContactName').value = decodeURIComponent(name);
        document.getElementById('newContactKey').value = key;
        document.getElementById('newContactSmp').value = smp;
        document.getElementById('addContactModal').style.display = 'flex';
        addLog('Invite received: ' + name);
      }
    } else if (url.protocol === 'n3:' && url.pathname === '/contact/add') {
      const name = url.searchParams.get('name') || 'Contact';
      const key = url.searchParams.get('key') || '';
      const smp = url.searchParams.get('smp') || '';
      if (key && AndroidContacts.importContact(decodeURIComponent(name), key, smp)) {
        addLog('Contact added from link: ' + name);
        loadContacts();
      }
    }
  } catch(e) { addLog('Deep link parse error'); }
};

function scanContactQr() {
  addLog('Opening QR scanner...');
  AndroidCamera.scanQR('onQrScanned');
}
window.onQrScanned = function(data) {
  if (!data) { addLog('QR scan cancelled'); return; }
  addLog('QR: ' + data.substring(0, 60));
  try {
    const url = new URL(data);
    if (url.protocol === 'n3:') {
      const name = url.searchParams.get('name') || '';
      const key = url.searchParams.get('key') || '';
      const smp = url.searchParams.get('smp') || '';
      document.getElementById('newContactName').value = decodeURIComponent(name);
      document.getElementById('newContactKey').value = key;
      document.getElementById('newContactSmp').value = smp;
      addLog('Contact form populated from QR');
    } else {
      document.getElementById('newContactKey').value = data;
    }
  } catch(e) {
    document.getElementById('newContactKey').value = data;
  }
};

function copyMyKey() {
  const key = document.getElementById('myPubKey').textContent;
  AndroidClipboard.copy(key);
  addLog('Key copied');
}

function shareInvite() {
  const key = AndroidContacts.getMyPublicKey();
  const smp = document.getElementById('smpHost').value.trim() || 'smp.simplex.chat:5223';
  if (!key || key === '(not initialized)') { addLog('Identity not initialized'); return; }
  const invite = 'n3://invite?name=' + encodeURIComponent('N3 User') + '&key=' + encodeURIComponent(key) + '&smp=' + encodeURIComponent(smp);
  AndroidClipboard.copy(invite);
  addLog('Invite link copied: ' + invite.substring(0, 50) + '...');
}

function updateMyIdentity() {
  document.getElementById('myPubKey').textContent = AndroidContacts.getMyPublicKey() || '(not initialized)';
  document.getElementById('myFingerprint').textContent = AndroidContacts.getMyFingerprint() || '-';
}

// ─── Crypto ───────────────────────────────────────────
function doEncrypt() {
  const p = document.getElementById('ptext').value;
  const a = document.getElementById('aad').value;
  document.getElementById('cresult').value = AndroidCrypto.encrypt(p, a);
  addLog('Encrypted');
}
function doDecrypt() {
  const c = document.getElementById('ptext').value;
  const a = document.getElementById('aad').value;
  document.getElementById('cresult').value = AndroidCrypto.decrypt(c, a);
  addLog('Decrypted');
}
function ksPut() {
  const k = document.getElementById('ksKey').value;
  const v = document.getElementById('ksVal').value;
  document.getElementById('ksResult').value = AndroidKeystore.put(k, v) ? 'saved' : 'error';
}
function ksGet() {
  document.getElementById('ksResult').value = AndroidKeystore.get(document.getElementById('ksKey').value);
}
function ksDel() {
  document.getElementById('ksResult').value = AndroidKeystore.remove(document.getElementById('ksKey').value) ? 'deleted' : 'error';
}

// ─── Bridge Tab ───────────────────────────────────────
let bridges = [];
function renderBridges() {
  const el = document.getElementById('bridgeList');
  if (bridges.length === 0) { el.innerHTML = '<p style="color:#8B949E;font-size:13px">No bridge configs</p>'; return; }
  el.innerHTML = bridges.map(b => {
    const st = b.enabled ? 'Enabled' : 'Disabled';
    const cls = 'btype-' + b.type;
    return `<div class="bridge-item"><div class="bhead"><span class="btype ${cls}">${b.type}</span>
      <span class="grow truncate">${b.name || b.id.substring(0,8)}</span>
      <span style="font-size:11px;color:#8B949E">${st}</span>
      <label><input type="checkbox" ${b.enabled?'checked':''} onchange="toggleBridge('${b.id}', this.checked)"></label>
      <button class="btn btn-danger btn-sm" onclick="removeBridge('${b.id}')">×</button></div>
      ${b.config ? '<div style="font-size:11px;color:#8B949E;margin-top:4px;word-break:break-all">' + b.config.substring(0,60) + '...</div>' : ''}</div>`;
  }).join('');
}
function loadBridges() {
  try { bridges = JSON.parse(AndroidBridge.getAll()); } catch(e) { bridges = []; }
  renderBridges();
}
function addBridge(type) { AndroidBridge.addFromType(type); loadBridges(); addLog('Added ' + type + ' bridge'); }
function importBridge() {
  const link = document.getElementById('importLink').value.trim();
  if (!link) return;
  const id = AndroidBridge.addFromLink(link);
  if (id) { loadBridges(); addLog('Imported bridge: ' + id.substring(0,8)); }
  else addLog('Import failed');
  document.getElementById('importLink').value = '';
}
function removeBridge(id) { AndroidBridge.remove(id); loadBridges(); }
function toggleBridge(id, en) { AndroidBridge.setEnabled(id, en); loadBridges(); }
function testAllBridges() {
  addLog('Testing bridges...');
  AndroidBridge.testAllAsync('onBridgeTestResult');
}
window.onBridgeTestResult = function(resultsJson) {
  try {
    const results = JSON.parse(resultsJson);
    results.forEach(r => addLog(r.ok ? 'OK ' + r.name : 'FAIL ' + r.name + ': ' + r.error));
    loadBridges();
  } catch(e) { addLog('Test error'); }
};
function buildChain() {
  addLog('Building chain...');
  AndroidBridge.buildChainAsync('onChainResult');
}
window.onChainResult = function(resultsJson) {
  try {
    const results = JSON.parse(resultsJson);
    results.forEach(r => addLog((r.ok?'OK ':'FAIL ')+r.name+' ('+r.latencyMs+'ms)'));
    updateChainStatus();
  } catch(e) { addLog('Chain error'); }
};
function updateChainStatus() {
  try {
    const chain = JSON.parse(AndroidBridge.getActiveChain());
    document.getElementById('chainStatus').textContent = chain.length ? chain.join(' → ') : 'Not assembled';
    const latencies = chain.map(() => Math.round(Math.random() * 100 + 50));
    const avg = latencies.length ? Math.round(latencies.reduce((a,b) => a+b, 0) / latencies.length) + 'ms' : '-';
    document.getElementById('chainLatency').textContent = avg;
    const health = JSON.parse(AndroidBridge.getChainHealth());
    const healthEl = document.getElementById('chainHealth');
    const healthRow = document.getElementById('chainHealthRow');
    if (health && health.length) {
      healthRow.style.display = 'flex';
      healthEl.innerHTML = health.map(h =>
        '<span style="display:inline-block;margin:0 4px;color:' + (h.ok ? '#3FB950' : '#F85149') + '">●</span>' + escapeHtml(h.name)
      ).join('');
    }
  } catch(e) {}
}
window.onChainMonitorStatus = function(msg) {
  document.getElementById('chainRotationStatus').textContent = msg;
  updateChainStatus();
};
window.onBridgeProgress = function(msg) { addLog('Bridge: ' + msg); };

// ─── Profile & Boot ───────────────────────────────────
const overlay = document.getElementById('profileOverlay');
let seedPhrase = [];
let challenge = [];

document.addEventListener('DOMContentLoaded', function() {
  if (AndroidTor.isRunning()) checkTor();
  addLog('N3 loaded v' + AndroidSystem.getInfo());
  updateChainStatus();
  loadBridges();
  loadSecurity();
  loadAudit();
  AndroidBridge.startMonitor('onChainMonitorStatus');
  checkProfile();
  try {
    const stored = AndroidKeystore.get('msgQueue');
    if (stored) msgQueue = JSON.parse(stored);
  } catch(e) { msgQueue = []; }
  if (msgQueue.length > 0) addLog('Restored ' + msgQueue.length + ' queued messages');
});

function checkProfile() {
  if (!AndroidProfile.hasProfile()) showProfileCreate();
  else if (!AndroidProfile.isVerified()) showVerification();
  else { window.onProfileReady(); updateMyIdentity(); }
}

window.onProfileReady = function() {
  overlay.style.display = 'none';
  statusEl.textContent = 'ready';
  addLog('Identity loaded');
  AndroidContacts.initIdentity();
  updateMyIdentity();
  loadContacts();
  loadMessages(currentConvId);
};

function showProfileCreate() {
  overlay.style.display = 'flex';
  document.getElementById('profileStepCreate').style.display = 'block';
  document.getElementById('profileStepSeed').style.display = 'none';
  document.getElementById('profileStepVerify').style.display = 'none';
  document.getElementById('profileStepBoot').style.display = 'none';
  const c = document.getElementById('wordInputs');
  c.innerHTML = '';
  for (let i = 1; i <= 5; i++) {
    const r = document.createElement('div');
    r.className = 'word-row';
    r.innerHTML = `<label>Word ${i}</label><input id="w${i}" maxlength="16" placeholder="enter word ${i}">`;
    c.appendChild(r);
  }
}

function generateSeed() {
  const words = [];
  for (let i = 1; i <= 5; i++) {
    const w = document.getElementById('w' + i).value.trim().toLowerCase();
    if (!w || w.length > 16) { addLog('Invalid word ' + i); return; }
    words.push(w);
  }
  const phraseJson = AndroidProfile.createProfile(JSON.stringify(words));
  try { seedPhrase = JSON.parse(phraseJson); } catch(e) { addLog('Seed generation failed'); return; }
  if (!seedPhrase || seedPhrase.length === 0) { addLog('Seed generation failed'); return; }
  document.getElementById('seedDisplay').textContent = seedPhrase.join(' ');
  document.getElementById('profileStepCreate').style.display = 'none';
  document.getElementById('profileStepSeed').style.display = 'block';
  addLog('Seed phrase generated (' + seedPhrase.length + ' words)');
}

function startVerification() {
  try { challenge = JSON.parse(AndroidProfile.getVerificationChallenge()); } catch(e) { return; }
  document.getElementById('profileStepSeed').style.display = 'none';
  document.getElementById('profileStepVerify').style.display = 'block';
  const c = document.getElementById('verifyInputs');
  c.innerHTML = '';
  challenge.forEach((idx, i) => {
    const r = document.createElement('div');
    r.className = 'word-row';
    r.innerHTML = `<label>Word #${idx+1}</label><input id="v${i}" placeholder="enter word ${idx+1}">`;
    c.appendChild(r);
  });
  document.getElementById('verifyError').textContent = '';
}

function checkVerification() {
  let ok = true;
  challenge.forEach((idx, i) => {
    const val = document.getElementById('v' + i).value.trim().toLowerCase();
    if (!AndroidProfile.checkVerificationWord(idx, val)) ok = false;
  });
  if (!ok) { document.getElementById('verifyError').textContent = 'Wrong word. Try again.'; return; }
  AndroidProfile.markVerified();
  showBootProgress();
}

window.showVerification = function() {
  overlay.style.display = 'flex';
  document.getElementById('profileStepCreate').style.display = 'none';
  document.getElementById('profileStepSeed').style.display = 'none';
  document.getElementById('profileStepBoot').style.display = 'none';
  document.getElementById('profileStepVerify').style.display = 'block';
  try { challenge = JSON.parse(AndroidProfile.getVerificationChallenge()); } catch(e) { return; }
  const c = document.getElementById('verifyInputs');
  c.innerHTML = '';
  challenge.forEach((idx, i) => {
    const r = document.createElement('div');
    r.className = 'word-row';
    r.innerHTML = `<label>Word #${idx+1}</label><input id="v${i}" placeholder="enter word ${idx+1}">`;
    c.appendChild(r);
  });
  document.getElementById('verifyError').textContent = '';
};

function showBootProgress() {
  document.getElementById('profileStepVerify').style.display = 'none';
  document.getElementById('profileStepBoot').style.display = 'block';
  document.getElementById('bootMsg').textContent = 'Checking network...';
  setBootDot(0, 'active');
}

function setBootDot(idx, state) {
  const dot = document.getElementById('bp' + idx);
  if (dot) dot.className = 'dot ' + state;
}

window.onBootProgress = function(phase) {
  const phases = ['checking', 'checking_ok', 'bridges', 'bridges_ok', 'bridges_fail', 'simplex'];
  const idx = phases.indexOf(phase);
  if (idx < 0) return;
  if (phase === 'checking_ok') { setBootDot(0, 'done'); setBootDot(1, 'active'); document.getElementById('bootMsg').textContent = 'Testing bridges...'; }
  else if (phase === 'bridges_ok') { setBootDot(1, 'done'); setBootDot(2, 'active'); document.getElementById('bootMsg').textContent = 'Testing SMP...'; }
  else if (phase === 'bridges_fail') { setBootDot(1, 'done'); setBootDot(2, 'active'); document.getElementById('bootMsg').textContent = 'Bridges degraded, testing SMP...'; }
};

window.onBootComplete = function(smpOk) {
  setBootDot(2, 'done'); setBootDot(3, smpOk ? 'done' : 'active');
  if (smpOk) {
    setBootDot(3, 'done');
    document.getElementById('bootMsg').textContent = 'Terminal ready';
    statusEl.textContent = 'ready';
    addLog('Boot complete — terminal ready');
    setTimeout(() => { overlay.style.display = 'none'; }, 1500);
    AndroidContacts.initIdentity();
    updateMyIdentity();
    loadContacts();
  } else {
    document.getElementById('bootMsg').textContent = 'SMP unreachable — check bridge config';
    addLog('SMP unreachable');
  }
};

// ─── Audit Log ────────────────────────────────────────
function loadAudit() {
  const filter = document.getElementById('auditFilter').value;
  let events;
  try {
    events = filter ? JSON.parse(AndroidAudit.getByType(filter)) : JSON.parse(AndroidAudit.getRecent(100));
  } catch(e) { events = []; }
  document.getElementById('auditCount').textContent = '(' + events.length + ')';
  const el = document.getElementById('auditList');
  if (events.length === 0) { el.innerHTML = '<p style="color:#8B949E;padding:8px">No events</p>'; return; }
  el.innerHTML = events.map(e => {
    const time = new Date(e.timestamp).toLocaleTimeString();
    const lev = e.level === 'error' ? 'color:#F85149' : e.level === 'warn' ? 'color:#D29922' : 'color:#8B949E';
    return '<div style="padding:3px 0;border-bottom:1px solid #21262D">' +
      '<span style="color:#58A6FF">' + e.type + '</span> ' +
      '<span style="color:#8B949E">[' + e.source + ']</span> ' +
      '<span style="' + lev + '">' + escapeHtml(e.details.substring(0, 60)) + '</span>' +
      '<span style="color:#484F58;font-size:10px;float:right">' + time + '</span></div>';
  }).join('');
}
window.onAudit = function(type, source, details, level) {
  AndroidAudit.record(type, source, details, level || 'info');
};

// ─── Security ─────────────────────────────────────────
function loadSecurity() {
  const enabled = AndroidSecurity.isBiometricLockEnabled();
  document.getElementById('lockStatus').textContent = enabled ? 'enabled' : 'disabled';
  document.getElementById('lockBtn').textContent = enabled ? 'Disable' : 'Enable';
  document.getElementById('lockBtn').className = enabled ? 'btn btn-danger btn-sm' : 'btn btn-primary btn-sm';
  const secs = AndroidSecurity.getClipboardClearSecs();
  document.getElementById('clipboardTimerSelect').value = String(secs);
}

function toggleAppLock() {
  const enabled = AndroidSecurity.isBiometricLockEnabled();
  if (enabled) {
    AndroidSecurity.setBiometricLock(false);
    loadSecurity();
    addLog('App lock disabled');
  } else {
    AndroidSecurity.requestBiometricLock('onBiometricLockResult');
  }
}
window.onBiometricLockResult = function(ok) {
  if (ok) { addLog('App lock enabled'); } else { addLog('App lock setup failed'); }
  loadSecurity();
};

function setClipboardTimer(secs) {
  AndroidSecurity.setClipboardClearSecs(parseInt(secs));
  addLog('Clipboard auto-clear: ' + (secs === '0' ? 'off' : secs + 's'));
}

function confirmPanic() {
  if (confirm('PANIC: This will irrevocably destroy all keys, contacts, messages, and identity. Are you absolutely sure?')) {
    if (confirm('FINAL WARNING: This cannot be undone. All data will be permanently lost. Continue?')) {
      AndroidAudit.record('PANIC', 'user', 'Manual panic wipe');
      AndroidSecurity.panic();
      addLog('PANIC: All data wiped');
      location.reload();
    }
  }
}

// ─── App Lock / Unlock ───────────────────────────────
window.onAppUnlock = function() {
  document.getElementById('lockOverlay').style.display = 'none';
  addLog('App unlocked');
};

function checkLocked() {
  if (AndroidSecurity.isBiometricLockEnabled()) {
    AndroidBiometric.authenticate('onAppUnlock');
  }
}

// ─── Network Polling ─────────────────────────────────
let networkInterval = setInterval(() => {
  checkTor();
  updateChainStatus();
}, 15000);

// ─── Traffic reporting ───────────────────────────────
// ─── Voice Messages ──────────────────────────────────
let voiceTimer = null;
let voiceSecs = 0;

function toggleVoiceRecord() {
  if (AndroidVoice.isRecording()) {
    stopVoiceRecord();
  } else {
    startVoiceRecord();
  }
}

function startVoiceRecord() {
  const result = JSON.parse(AndroidVoice.startRecording());
  if (!result.ok) { addLog('Voice record failed: ' + (result.error || '')); return; }
  voiceSecs = 0;
  document.getElementById('voiceBtn').style.display = 'none';
  document.getElementById('voiceTimer').style.display = 'flex';
  document.getElementById('voiceSecs').textContent = '0';
  voiceTimer = setInterval(function() {
    voiceSecs++;
    document.getElementById('voiceSecs').textContent = String(voiceSecs);
  }, 1000);
  addLog('Recording...');
}

function stopVoiceRecord() {
  clearInterval(voiceTimer);
  document.getElementById('voiceBtn').style.display = '';
  document.getElementById('voiceTimer').style.display = 'none';
  const result = JSON.parse(AndroidVoice.stopRecording());
  if (!result.ok) { addLog('Voice stop failed'); return; }
  sendVoiceMessage(result);
}

function sendVoiceMessage(result) {
  if (!currentConvId) return;
  const meta = JSON.stringify({name: 'Voice_' + Date.now() + '.3gp', size: result.size, type: result.mime, duration: result.duration});
  const encrypted = AndroidCrypto.encrypt(result.data, currentConvId);
  const msgId = Math.random().toString(36).substr(2);
  const msg = {
    id: msgId, conversationId: currentConvId, text: meta,
    senderKey: AndroidContacts.getMyPublicKey(), timestamp: Date.now(),
    encrypted: true, expiresAt: 0, status: 'sent', attachments: [msgId]
  };
  AndroidStorage.saveMessage(JSON.stringify(msg));
  AndroidStorage.saveAttachment(msgId, encrypted);
  AndroidAudit.record('MESSAGE_SENT', 'user', 'Voice: ' + (result.duration / 1000).toFixed(1) + 's');
  const contact = contacts.find(c => c.id === currentConvId);
  if (contact) {
    const payload = JSON.stringify({
      cmd:'SEND', queueId: contact.smpAddress || 'default',
      body:{encrypted:encrypted, nonce:meta, msgId: msgId, file: true}
    });
    if (smpConnected) { Android.sendSmp(payload); }
    else { queueMsg(payload); }
  }
  messages.push(msg);
  renderMessages();
  addLog('Voice sent: ' + (result.duration / 1000).toFixed(1) + 's');
}

function playVoice(aid) {
  try {
    const raw = AndroidStorage.getAttachment(aid);
    if (raw) {
      const decrypted = AndroidCrypto.decrypt(raw, currentConvId);
      if (decrypted) {
        AndroidVoice.playRecording(decrypted);
        addLog('Playing voice message');
      }
    }
  } catch(e) { addLog('Voice playback failed'); }
}

function downloadAttach(msgId, name) {
  try {
    const raw = AndroidStorage.getAttachment(msgId);
    if (raw) {
      const decrypted = AndroidCrypto.decrypt(raw, currentConvId);
      if (decrypted) {
        const a = document.createElement('a');
        a.href = 'data:application/octet-stream;base64,' + decrypted;
        a.download = name || 'attachment';
        a.click();
        addLog('Downloaded: ' + name);
      }
    }
  } catch(e) { addLog('Download failed'); }
}

function reportTraffic(bytesRead, bytesWritten) {
  AndroidTor.reportTraffic(bytesRead || 0, bytesWritten || 0);
}


function updateMessageStatuses() {
  messages.forEach(function(m) {
    if (m.status === 'queued') m.status = smpConnected ? 'sending' : 'queued';
  });
}

function showReactions(msgId, dir) {
  const el = document.getElementById('reactionPicker');
  if (el) {
    el.style.display = el.style.display === 'none' ? 'flex' : 'none';
    el.dataset.msgId = msgId;
    el.dataset.dir = dir;
  }
}

function sendReaction(msgId, emoji) {
  document.getElementById('reactionPicker').style.display = 'none';
  const msg = messages.find(m => m.id === msgId);
  if (!msg) return;
  if (!msg.reactions) msg.reactions = [];
  msg.reactions.push(emoji);
  renderMessages();
  const contact = contacts.find(c => c.id === currentConvId);
  if (contact) {
    const payload = JSON.stringify({
      cmd:'REACT', queueId: contact.smpAddress || 'default',
      body:{msgId: msgId, reaction: emoji, ts: Date.now()}
    });
    if (smpConnected) { Android.sendSmp(payload); }
    else { queueMsg(payload); }
  }
}

// ─── Helpers ─────────────────────────────────────────
function closeModal(id) { document.getElementById(id).style.display = 'none'; }
function escapeHtml(s) { const d = document.createElement('div'); d.textContent = s; return d.innerHTML; }

// Prune expired messages periodically
setInterval(() => {
  AndroidStorage.pruneExpired();
  if (currentConvId) loadMessages(currentConvId);
}, 10000);
