/**
 * Jump VPN - Simulador Web Interactivo y Generador de Payloads
 * Implementa la lógica de estados de RayFlash / SSH T PROJECT
 */

// Estado global de la aplicación
const state = {
  connectionStatus: 'DISCONNECTED', // DISCONNECTED, CONNECTING, INJECTING, AUTHENTICATING, CONNECTED
  profile: {
    name: 'Servidor LATAM #1',
    serverHost: '198.51.100.45',
    serverPort: 22,
    udpPort: 7300,
    sshUser: 'jump',
    sshPass: 'jump123',
    sniHost: 'c.whatsapp.net',
    tunnelMode: 'SSH_PROXY_PAYLOAD',
    payload: 'CONNECT [host_port] [protocol][crlf]Host: whatsapp.net[crlf]X-Online-Host: whatsapp.net[crlf]Connection: Keep-Alive[crlf]User-Agent: [ua][crlf][crlf]'
  },
  stats: {
    downloadSpeed: 0,
    uploadSpeed: 0,
    totalDownMB: 0,
    totalUpMB: 0,
    ping: 0
  },
  trafficInterval: null
};

// Elementos del DOM
const elements = {
  mobileClock: document.getElementById('mobileClock'),
  vpnKeyIcon: document.getElementById('vpnKeyIcon'),
  appStatusBadge: document.getElementById('appStatusBadge'),
  appTunnelModeSelect: document.getElementById('appTunnelModeSelect'),
  appServerName: document.getElementById('appServerName'),
  appServerSub: document.getElementById('appServerSub'),
  appPingTag: document.getElementById('appPingTag'),
  appConnectBtn: document.getElementById('appConnectBtn'),
  appBtnText: document.getElementById('appBtnText'),
  metricDownload: document.getElementById('metricDownload'),
  metricUpload: document.getElementById('metricUpload'),
  metricTotalDown: document.getElementById('metricTotalDown'),
  metricTotalUp: document.getElementById('metricTotalUp'),
  logsTerminal: document.getElementById('logsTerminal'),
  liveDot: document.getElementById('liveDot'),
  clearLogsBtn: document.getElementById('clearLogsBtn'),

  // Generador Payloads
  bugHostInput: document.getElementById('bugHostInput'),
  methodSelect: document.getElementById('methodSelect'),
  injectionSelect: document.getElementById('injectionSelect'),
  cbOnlineHost: document.getElementById('cbOnlineHost'),
  cbKeepAlive: document.getElementById('cbKeepAlive'),
  cbForwardHost: document.getElementById('cbForwardHost'),
  cbUserAgent: document.getElementById('cbUserAgent'),
  btnGenHttp: document.getElementById('btnGenHttp'),
  btnGenWs: document.getElementById('btnGenWs'),
  payloadResultText: document.getElementById('payloadResultText'),
  btnApplyToApp: document.getElementById('btnApplyToApp'),

  // Configuración Servidor
  srvHost: document.getElementById('srvHost'),
  srvPort: document.getElementById('srvPort'),
  srvUdpPort: document.getElementById('srvUdpPort'),
  srvUser: document.getElementById('srvUser'),
  srvPass: document.getElementById('srvPass'),
  srvSni: document.getElementById('srvSni'),
  btnUpdateServer: document.getElementById('btnUpdateServer'),

  // Import / Export
  btnExportJump: document.getElementById('btnExportJump'),
  btnImportJump: document.getElementById('btnImportJump'),
  fileInputJump: document.getElementById('fileInputJump')
};

// Inicialización
function init() {
  updateClock();
  setInterval(updateClock, 1000);
  bindEvents();
  generateHttpPayload();
  updateUiFromProfile();
}

function updateClock() {
  const now = new Date();
  const hours = String(now.getHours()).padStart(2, '0');
  const minutes = String(now.getMinutes()).padStart(2, '0');
  elements.mobileClock.textContent = `${hours}:${minutes}`;
}

function bindEvents() {
  elements.appConnectBtn.addEventListener('click', toggleConnection);
  elements.clearLogsBtn.addEventListener('click', clearLogs);

  elements.btnGenHttp.addEventListener('click', generateHttpPayload);
  elements.btnGenWs.addEventListener('click', generateWsPayload);
  elements.btnApplyToApp.addEventListener('click', applyPayloadToApp);

  elements.appTunnelModeSelect.addEventListener('change', (e) => {
    state.profile.tunnelMode = e.target.value;
    updateUiFromProfile();
    addLog(`Modo de túnel cambiado a: ${e.target.options[e.target.selectedIndex].text}`, 'system');
  });

  elements.btnUpdateServer.addEventListener('click', updateServerSettings);

  // Presets
  document.querySelectorAll('.btn-preset').forEach(btn => {
    btn.addEventListener('click', () => {
      const mode = btn.dataset.mode;
      const bug = btn.dataset.bug;
      const port = btn.dataset.port;

      state.profile.tunnelMode = mode;
      elements.appTunnelModeSelect.value = mode;
      if (bug) {
        elements.bugHostInput.value = bug;
        elements.srvSni.value = bug;
        state.profile.sniHost = bug;
      }
      elements.srvPort.value = port;
      state.profile.serverPort = parseInt(port);

      if (mode === 'SSH_WEBSOCKET_CDN') {
        generateWsPayload();
      } else {
        generateHttpPayload();
      }
      applyPayloadToApp();
      addLog(`Preset aplicado: ${btn.textContent}`, 'info');
    });
  });

  // Exportar / Importar .jump
  elements.btnExportJump.addEventListener('click', exportJumpProfile);
  elements.btnImportJump.addEventListener('click', () => elements.fileInputJump.click());
  elements.fileInputJump.addEventListener('change', handleImportFile);
}

function updateUiFromProfile() {
  elements.appServerName.textContent = state.profile.name;
  elements.appServerSub.textContent = `${state.profile.serverHost}:${state.profile.serverPort} • BadVPN ${state.profile.udpPort}`;
}

// Log Utility
function addLog(msg, type = 'info') {
  const now = new Date();
  const time = now.toTimeString().split(' ')[0];
  const line = document.createElement('div');
  line.className = `log-line ${type}`;
  line.textContent = `[${time}] ${msg}`;
  elements.logsTerminal.appendChild(line);
  elements.logsTerminal.scrollTop = elements.logsTerminal.scrollHeight;
}

function clearLogs() {
  elements.logsTerminal.innerHTML = '';
  addLog('Logs limpiados por el usuario.', 'system');
}

// Conexión y Simulación de Ciclo de Vida
function toggleConnection() {
  if (state.connectionStatus === 'CONNECTED') {
    disconnectVpn();
  } else if (state.connectionStatus === 'DISCONNECTED') {
    connectVpn();
  }
}

function connectVpn() {
  state.connectionStatus = 'CONNECTING';
  updateConnectionUi();
  addLog('Iniciando servicio Jump VPN v4.6.2...', 'system');
  addLog('Configurando interfaz TUN 10.0.0.2/24 (MTU: 1500)...', 'info');

  setTimeout(() => {
    if (state.connectionStatus !== 'CONNECTING') return;

    if (state.profile.tunnelMode === 'SSH_PROXY_PAYLOAD') {
      state.connectionStatus = 'INJECTING';
      updateConnectionUi();
      addLog(`Conectando a proxy: 104.16.1.1:8080...`, 'info');
      addLog(`Inyectando Payload HTTP [${state.profile.payload.slice(0, 35)}...]`, 'warn');
    } else if (state.profile.tunnelMode === 'SSH_SSL_SNI') {
      state.connectionStatus = 'INJECTING';
      updateConnectionUi();
      addLog(`Iniciando handshake SSL/TLS con SNI Bug: '${state.profile.sniHost}'`, 'info');
    } else if (state.profile.tunnelMode === 'SSH_WEBSOCKET_CDN') {
      state.connectionStatus = 'INJECTING';
      updateConnectionUi();
      addLog('Enviando HTTP Upgrade 101 WebSocket a Cloudflare CDN...', 'warn');
    }

    setTimeout(() => {
      if (state.connectionStatus === 'DISCONNECTED') return;

      state.connectionStatus = 'AUTHENTICATING';
      updateConnectionUi();
      addLog(`Autenticando SSH con usuario '${state.profile.sshUser}' en ${state.profile.serverHost}:${state.profile.serverPort}...`, 'system');

      setTimeout(() => {
        if (state.connectionStatus === 'DISCONNECTED') return;

        state.connectionStatus = 'CONNECTED';
        updateConnectionUi();
        addLog(`✓ CONECTADO: Túnel SSH activo. Proxy SOCKS5 en 127.0.0.1:1080`, 'success');
        addLog(`BadVPN UDPGW listo en puerto ${state.profile.udpPort} (VoIP y juegos habilitados).`, 'success');
        startTrafficSimulation();
      }, 1200);

    }, 1000);

  }, 800);
}

function disconnectVpn() {
  state.connectionStatus = 'DISCONNECTED';
  stopTrafficSimulation();
  updateConnectionUi();
  addLog('Desconectado: Conexión terminada por el usuario.', 'warn');
}

function updateConnectionUi() {
  const badge = elements.appStatusBadge;
  const btn = elements.appConnectBtn;
  const btnText = elements.appBtnText;
  const keyIcon = elements.vpnKeyIcon;
  const liveDot = elements.liveDot;

  btn.className = 'hero-power-btn';

  switch (state.connectionStatus) {
    case 'DISCONNECTED':
      badge.textContent = 'DESCONECTADO';
      badge.className = 'status-badge disconnected';
      btn.classList.add('state-disconnected');
      btnText.textContent = 'CONECTAR';
      keyIcon.style.display = 'none';
      liveDot.className = 'live-dot';
      elements.appPingTag.textContent = '-- ms';
      break;

    case 'CONNECTING':
      badge.textContent = 'CONECTANDO...';
      badge.className = 'status-badge connecting';
      btn.classList.add('state-connecting');
      btnText.textContent = 'CANCELAR';
      keyIcon.style.display = 'inline';
      break;

    case 'INJECTING':
      badge.textContent = 'INYECTANDO';
      badge.className = 'status-badge connecting';
      btn.classList.add('state-connecting');
      btnText.textContent = 'INYECCIÓN';
      break;

    case 'AUTHENTICATING':
      badge.textContent = 'SSH AUTH';
      badge.className = 'status-badge connecting';
      btn.classList.add('state-connecting');
      btnText.textContent = 'AUTENTICANDO';
      break;

    case 'CONNECTED':
      badge.textContent = 'CONECTADO';
      badge.className = 'status-badge connected';
      btn.classList.add('state-connected');
      btnText.textContent = 'DESCONECTAR';
      keyIcon.style.display = 'inline';
      liveDot.className = 'live-dot active';
      break;
  }
}

// Simulación de Tráfico en Tiempo Real
function startTrafficSimulation() {
  stopTrafficSimulation();
  state.trafficInterval = setInterval(() => {
    const downSpeed = (Math.random() * 2200 + 450).toFixed(1);
    const upSpeed = (Math.random() * 850 + 120).toFixed(1);
    const ping = Math.floor(Math.random() * 30 + 35);

    state.stats.totalDownMB += parseFloat(downSpeed) / 1024 / 2;
    state.stats.totalUpMB += parseFloat(upSpeed) / 1024 / 2;

    elements.metricDownload.textContent = `${downSpeed} KB/s`;
    elements.metricUpload.textContent = `${upSpeed} KB/s`;
    elements.metricTotalDown.textContent = `${state.stats.totalDownMB.toFixed(1)} MB`;
    elements.metricTotalUp.textContent = `${state.stats.totalUpMB.toFixed(1)} MB`;
    elements.appPingTag.textContent = `${ping} ms`;
  }, 1000);
}

function stopTrafficSimulation() {
  if (state.trafficInterval) {
    clearInterval(state.trafficInterval);
    state.trafficInterval = null;
  }
  elements.metricDownload.textContent = '0.0 KB/s';
  elements.metricUpload.textContent = '0.0 KB/s';
}

// Generación de Payloads
function generateHttpPayload() {
  const bug = elements.bugHostInput.value.trim() || 'portal.operadora.com';
  const method = elements.methodSelect.value;
  const injection = elements.injectionSelect.value;

  let payload = '';

  if (injection === 'NORMAL') {
    payload = `${method} [host_port] [protocol][crlf]Host: ${bug}[crlf]`;
    if (elements.cbOnlineHost.checked) payload += `X-Online-Host: ${bug}[crlf]`;
    if (elements.cbForwardHost.checked) payload += `X-Forward-Host: ${bug}[crlf]`;
  } else if (injection === 'FRONT') {
    payload = `GET http://${bug}/ [protocol][crlf]Host: ${bug}[crlf][crlf]${method} [host_port] [protocol][crlf]Host: ${bug}[crlf]`;
  } else if (injection === 'BACK') {
    payload = `${method} [host_port] [protocol][crlf]Host: ${bug}[crlf][crlf]GET http://${bug}/ [protocol][crlf]Host: ${bug}[crlf]`;
  }

  if (elements.cbKeepAlive.checked) {
    payload += 'Connection: Keep-Alive[crlf]Proxy-Connection: Keep-Alive[crlf]';
  }
  if (elements.cbUserAgent.checked) {
    payload += 'User-Agent: [ua][crlf]';
  }

  payload += '[crlf]';
  elements.payloadResultText.value = payload;
}

function generateWsPayload() {
  const bug = elements.bugHostInput.value.trim() || 'zoom.us';
  const wsPayload = `GET / HTTP/1.1[crlf]Host: ${bug}[crlf]Upgrade: websocket[crlf]Connection: Upgrade[crlf]Sec-WebSocket-Key: [ua][crlf]Sec-WebSocket-Version: 13[crlf][crlf]`;
  elements.payloadResultText.value = wsPayload;
}

function applyPayloadToApp() {
  const p = elements.payloadResultText.value;
  state.profile.payload = p;
  addLog('Nuevo payload inyectado en el perfil activo.', 'system');
  alert('¡Payload aplicado a Jump VPN con éxito!');
}

function updateServerSettings() {
  state.profile.serverHost = elements.srvHost.value.trim();
  state.profile.serverPort = parseInt(elements.srvPort.value) || 22;
  state.profile.udpPort = parseInt(elements.srvUdpPort.value) || 7300;
  state.profile.sshUser = elements.srvUser.value.trim();
  state.profile.sshPass = elements.srvPass.value;
  state.profile.sniHost = elements.srvSni.value.trim();

  updateUiFromProfile();
  addLog(`Servidor actualizado: ${state.profile.serverHost}:${state.profile.serverPort}`, 'info');
  alert('Ajustes del servidor SSH guardados.');
}

// Exportación / Importación .jump
function exportJumpProfile() {
  const configData = JSON.stringify(state.profile, null, 2);
  const blob = new Blob([configData], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `jump_config_${Date.now()}.jump`;
  a.click();
  URL.revokeObjectURL(url);
  addLog('Archivo de configuración .jump exportado.', 'system');
}

function handleImportFile(event) {
  const file = event.target.files[0];
  if (!file) return;

  const reader = new FileReader();
  reader.onload = (e) => {
    try {
      const parsed = JSON.parse(e.target.result);
      Object.assign(state.profile, parsed);

      elements.srvHost.value = state.profile.serverHost || '';
      elements.srvPort.value = state.profile.serverPort || 22;
      elements.srvUdpPort.value = state.profile.udpPort || 7300;
      elements.srvUser.value = state.profile.sshUser || '';
      elements.srvPass.value = state.profile.sshPass || '';
      elements.srvSni.value = state.profile.sniHost || '';
      elements.appTunnelModeSelect.value = state.profile.tunnelMode || 'SSH_PROXY_PAYLOAD';
      elements.payloadResultText.value = state.profile.payload || '';

      updateUiFromProfile();
      addLog(`Perfil .jump importado con éxito: ${file.name}`, 'success');
      alert(`Configuración '${file.name}' cargada correctamente.`);
    } catch (err) {
      alert('Error: Archivo de configuración inválido.');
    }
  };
  reader.readAsText(file);
}

// Ejecutar al cargar
document.addEventListener('DOMContentLoaded', init);
