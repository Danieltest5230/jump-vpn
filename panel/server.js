const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const jwt = require('jsonwebtoken');
const { exec } = require('child_process');

const app = express();
const PORT = process.env.PORT || 3000;
const JWT_SECRET = process.env.JWT_SECRET || 'jump_vpn_super_secret_jwt_key_2026';
const DEFAULT_ADMIN_USER = process.env.ADMIN_USER || 'admin';
const DEFAULT_ADMIN_PASS = process.env.ADMIN_PASS || 'jumpadmin2026';

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// Directorios y Archivos de Base de Datos
const DATA_DIR = path.join(__dirname, 'data');
const DB_FILE = path.join(DATA_DIR, 'users.json');
const ADMIN_FILE = path.join(DATA_DIR, 'admin.json');

if (!fs.existsSync(DATA_DIR)) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

// Helpers de Credenciales Maestras del Administrador
function getAdminCredentials() {
  if (fs.existsSync(ADMIN_FILE)) {
    try {
      return JSON.parse(fs.readFileSync(ADMIN_FILE, 'utf8'));
    } catch (e) {}
  }
  return { username: DEFAULT_ADMIN_USER, password: DEFAULT_ADMIN_PASS };
}

function saveAdminCredentials(creds) {
  fs.writeFileSync(ADMIN_FILE, JSON.stringify(creds, null, 2), 'utf8');
}

// Inicializar Base de Datos de Usuarios con Cuenta Admin Ilimitada
if (!fs.existsSync(DB_FILE)) {
  fs.writeFileSync(DB_FILE, JSON.stringify([
    {
      username: 'admin',
      password: 'jumpadmin2026',
      createdAt: new Date().toISOString(),
      expiresAt: null,
      isUnlimited: true,
      daysGranted: 0,
      status: 'ACTIVE',
      maxConnections: 10,
      note: '👑 Cuenta Administrador (Acceso Ilimitado / Permanente)'
    },
    {
      username: 'usuario_demo',
      password: 'demo123',
      createdAt: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
      isUnlimited: false,
      daysGranted: 30,
      status: 'ACTIVE',
      maxConnections: 1,
      note: 'Cuenta de prueba cliente (30 días)'
    }
  ], null, 2));
}

// Helpers de Base de Datos
function getUsers() {
  try {
    const raw = fs.readFileSync(DB_FILE, 'utf8');
    return JSON.parse(raw);
  } catch (err) {
    return [];
  }
}

function saveUsers(users) {
  fs.writeFileSync(DB_FILE, JSON.stringify(users, null, 2), 'utf8');
}

// Helper para sincronizar usuario en Linux VPS
function syncLinuxUser(username, password, expireDateISO) {
  if (process.platform === 'win32') {
    console.log(`[Windows Dev] Simulación Linux: ${username} (Vencimiento: ${expireDateISO || 'ILIMITADO'})`);
    return;
  }

  let cmd = `
    id -u ${username} >/dev/null 2>&1 || useradd -M -s /bin/false ${username}
    echo "${username}:${password}" | chpasswd
  `;

  if (expireDateISO) {
    const dateFormatted = expireDateISO.split('T')[0];
    cmd += `\nusermod -e ${dateFormatted} ${username}`;
  } else {
    // Cuenta ilimitada sin vencimiento
    cmd += `\nusermod -e "" ${username}`;
  }

  exec(cmd, (error) => {
    if (error) {
      console.error(`Error al sincronizar usuario Linux ${username}:`, error.message);
    } else {
      console.log(`Usuario Linux ${username} actualizado.`);
    }
  });
}

function removeLinuxUser(username) {
  if (process.platform === 'win32') return;
  exec(`userdel -r ${username}`, (err) => {
    if (err) console.error(`Error al borrar usuario Linux ${username}:`, err.message);
  });
}

// Middleware de autenticación para Administrador
function authAdmin(req, res, next) {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Acceso no autorizado' });
  }

  const token = authHeader.split(' ')[1];
  try {
    const decoded = jwt.verify(token, JWT_SECRET);
    if (decoded.role !== 'admin') throw new Error('Rol inválido');
    req.user = decoded;
    next();
  } catch (err) {
    return res.status(401).json({ error: 'Token inválido o expirado' });
  }
}

// ========================================================
// RUTAS DE ADMINISTRADOR
// ========================================================

// Login de Admin
app.post('/api/admin/login', (req, res) => {
  const { username, password } = req.body;
  const adminCreds = getAdminCredentials();

  if (username === adminCreds.username && password === adminCreds.password) {
    const token = jwt.sign({ username, role: 'admin' }, JWT_SECRET, { expiresIn: '7d' });
    return res.json({ success: true, token });
  }
  return res.status(401).json({ error: 'Usuario o contraseña de administrador incorrectos' });
});

// Cambiar la contraseña maestra del Panel Admin
app.post('/api/admin/change-panel-password', authAdmin, (req, res) => {
  const { currentPassword, newPassword } = req.body;
  const adminCreds = getAdminCredentials();

  if (currentPassword !== adminCreds.password) {
    return res.status(401).json({ error: 'La contraseña maestra actual es incorrecta' });
  }

  if (!newPassword || newPassword.length < 5) {
    return res.status(400).json({ error: 'La nueva contraseña debe tener al menos 5 caracteres' });
  }

  adminCreds.password = newPassword.trim();
  saveAdminCredentials(adminCreds);

  // También actualizar la cuenta VPN del admin si existe
  const users = getUsers();
  const adminUser = users.find(u => u.username === adminCreds.username);
  if (adminUser) {
    adminUser.password = newPassword.trim();
    saveUsers(users);
    syncLinuxUser(adminUser.username, adminUser.password, null);
  }

  res.json({ success: true, message: '¡Contraseña maestra de administrador cambiada con éxito!' });
});

// Listar todos los usuarios y calcular días restantes
app.get('/api/admin/users', authAdmin, (req, res) => {
  const users = getUsers();
  const now = new Date();

  const formatted = users.map(u => {
    // Si la cuenta es ilimitada (como la del admin)
    if (u.isUnlimited || !u.expiresAt) {
      return {
        username: u.username,
        password: u.password,
        createdAt: u.createdAt,
        expiresAt: null,
        daysGranted: 0,
        remainingDays: '♾️ Ilimitado',
        isUnlimited: true,
        status: 'ACTIVE',
        note: u.note || '👑 Acceso Permanente'
      };
    }

    const exp = new Date(u.expiresAt);
    const diffMs = exp - now;
    const remainingDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24));
    const isExpired = remainingDays <= 0;

    return {
      username: u.username,
      password: u.password,
      createdAt: u.createdAt,
      expiresAt: u.expiresAt,
      daysGranted: u.daysGranted,
      remainingDays: isExpired ? 0 : remainingDays,
      isUnlimited: false,
      status: isExpired ? 'EXPIRED' : (u.status || 'ACTIVE'),
      note: u.note || ''
    };
  });

  res.json(formatted);
});

// Crear nuevo usuario (con días o ILIMITADO)
app.post('/api/admin/users', authAdmin, (req, res) => {
  const { username, password, days, note, isUnlimited } = req.body;

  if (!username || !password) {
    return res.status(400).json({ error: 'Usuario y contraseña son obligatorios' });
  }

  const cleanUser = username.trim().toLowerCase().replace(/[^a-z0-9_]/g, '');
  if (cleanUser.length < 3) {
    return res.status(400).json({ error: 'El usuario debe tener al menos 3 caracteres' });
  }

  const users = getUsers();
  if (users.some(u => u.username === cleanUser)) {
    return res.status(400).json({ error: 'Ese nombre de usuario ya existe' });
  }

  const now = new Date();
  const unlimitedMode = isUnlimited === true || days === 'unlimited' || parseInt(days, 10) === 0;

  let expiresAt = null;
  let daysNum = 0;

  if (!unlimitedMode) {
    daysNum = parseInt(days, 10) || 30;
    expiresAt = new Date(now.getTime() + daysNum * 24 * 60 * 60 * 1000).toISOString();
  }

  const newUser = {
    username: cleanUser,
    password: password.trim(),
    createdAt: now.toISOString(),
    expiresAt,
    isUnlimited: unlimitedMode,
    daysGranted: daysNum,
    status: 'ACTIVE',
    maxConnections: unlimitedMode ? 5 : 1,
    note: note ? note.trim() : (unlimitedMode ? '👑 Cuenta VIP / Ilimitada' : '')
  };

  users.push(newUser);
  saveUsers(users);
  syncLinuxUser(newUser.username, newUser.password, newUser.expiresAt);

  res.json({ success: true, user: newUser });
});

// Renovar usuario (+N días)
app.put('/api/admin/users/:username/renew', authAdmin, (req, res) => {
  const { username } = req.params;
  const { extraDays } = req.body;
  const daysToAdd = parseInt(extraDays || 30, 10);

  const users = getUsers();
  const user = users.find(u => u.username === username);

  if (!user) {
    return res.status(404).json({ error: 'Usuario no encontrado' });
  }

  if (user.isUnlimited) {
    return res.json({ success: true, message: 'Esta cuenta ya tiene acceso ilimitado permanente.', user });
  }

  const now = new Date();
  const currentExp = new Date(user.expiresAt);
  const baseDate = currentExp > now ? currentExp : now;
  const newExp = new Date(baseDate.getTime() + daysToAdd * 24 * 60 * 60 * 1000).toISOString();

  user.expiresAt = newExp;
  user.status = 'ACTIVE';
  user.daysGranted = (user.daysGranted || 0) + daysToAdd;

  saveUsers(users);
  syncLinuxUser(user.username, user.password, user.expiresAt);

  res.json({ success: true, message: `Usuario renovado por ${daysToAdd} días`, user });
});

// Eliminar usuario
app.delete('/api/admin/users/:username', authAdmin, (req, res) => {
  const { username } = req.params;
  let users = getUsers();
  const initialLength = users.length;

  users = users.filter(u => u.username !== username);
  if (users.length === initialLength) {
    return res.status(404).json({ error: 'Usuario no encontrado' });
  }

  saveUsers(users);
  removeLinuxUser(username);

  res.json({ success: true, message: `Usuario ${username} eliminado` });
});

// ========================================================
// RUTAS PÚBLICAS DE AUTOGESTIÓN DE USUARIOS
// ========================================================

// Cambiar contraseña (funciona para clientes y para el admin en su teléfono)
app.post('/api/user/change-password', (req, res) => {
  const { username, currentPassword, newPassword } = req.body;

  if (!username || !currentPassword || !newPassword) {
    return res.status(400).json({ error: 'Todos los campos son obligatorios' });
  }

  if (newPassword.length < 4) {
    return res.status(400).json({ error: 'La nueva contraseña debe tener al menos 4 caracteres' });
  }

  const cleanUser = username.trim().toLowerCase();
  const users = getUsers();
  const user = users.find(u => u.username === cleanUser);

  if (!user) {
    return res.status(404).json({ error: 'Usuario no encontrado' });
  }

  // Verificar contraseña actual
  if (user.password !== currentPassword.trim()) {
    return res.status(401).json({ error: 'La contraseña actual es incorrecta' });
  }

  // Si la cuenta NO es ilimitada, verificar que no haya expirado
  if (!user.isUnlimited && user.expiresAt) {
    const now = new Date();
    if (new Date(user.expiresAt) <= now) {
      return res.status(403).json({ error: 'Tu cuenta ha expirado. Contacta al administrador para renovar.' });
    }
  }

  // Actualizar contraseña
  user.password = newPassword.trim();
  saveUsers(users);
  syncLinuxUser(user.username, user.password, user.expiresAt);

  return res.json({
    success: true,
    message: '¡Contraseña cambiada exitosamente! Ya puedes conectar Jump VPN con tu nueva clave.'
  });
});

// Consultar vigencia de cuenta
app.get('/api/user/check/:username', (req, res) => {
  const { username } = req.params;
  const users = getUsers();
  const user = users.find(u => u.username === username.trim().toLowerCase());

  if (!user) {
    return res.status(404).json({ error: 'Usuario no encontrado' });
  }

  // Cuenta Ilimitada (Admin / VIP)
  if (user.isUnlimited || !user.expiresAt) {
    return res.json({
      username: user.username,
      isExpired: false,
      isUnlimited: true,
      remainingDays: '♾️ Ilimitado',
      expiresAt: null
    });
  }

  const now = new Date();
  const exp = new Date(user.expiresAt);
  const diffMs = exp - now;
  const remainingDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24));
  const isExpired = remainingDays <= 0;

  res.json({
    username: user.username,
    isExpired,
    isUnlimited: false,
    remainingDays: isExpired ? 0 : remainingDays,
    expiresAt: user.expiresAt
  });
});

// Rutas de páginas HTML
app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.get('/admin', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'admin.html'));
});

app.get('/cambiar-clave', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'cambiar-clave.html'));
});

// Descarga directa del APK de la App Jump VPN
app.get('/descargar', (req, res) => {
  res.redirect('https://github.com/Danieltest5230/jump-vpn/releases/download/v4.6.2/Jump-VPN-v4.6.2.apk');
});

app.get('/download', (req, res) => {
  res.redirect('https://github.com/Danieltest5230/jump-vpn/releases/download/v4.6.2/Jump-VPN-v4.6.2.apk');
});

app.listen(PORT, () => {
  console.log(`====================================================`);
  console.log(`  🚀 Panel Jump VPN corriendo en puerto ${PORT}`);
  console.log(`  - Inicio Oficial:      http://localhost:${PORT}/`);
  console.log(`  - Panel Administrador: http://localhost:${PORT}/admin`);
  console.log(`  - Portal Usuarios:     http://localhost:${PORT}/cambiar-clave`);
  console.log(`====================================================`);
});
