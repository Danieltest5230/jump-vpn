const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const jwt = require('jsonwebtoken');
const { exec } = require('child_process');

const app = express();
const PORT = process.env.PORT || 3000;
const JWT_SECRET = process.env.JWT_SECRET || 'jump_vpn_super_secret_jwt_key_2026';
const ADMIN_USER = process.env.ADMIN_USER || 'admin';
const ADMIN_PASS = process.env.ADMIN_PASS || 'jumpadmin2026';

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// Archivo de base de datos JSON local
const DATA_DIR = path.join(__dirname, 'data');
const DB_FILE = path.join(DATA_DIR, 'users.json');

if (!fs.existsSync(DATA_DIR)) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

if (!fs.existsSync(DB_FILE)) {
  fs.writeFileSync(DB_FILE, JSON.stringify([
    {
      username: 'usuario_demo',
      password: 'demo123',
      createdAt: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
      daysGranted: 30,
      status: 'ACTIVE',
      maxConnections: 1,
      note: 'Cuenta de prueba inicial'
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

// Helper para sincronizar usuario en el sistema operativo Linux (si corre en VPS)
function syncLinuxUser(username, password, expireDateISO) {
  if (process.platform === 'win32') {
    console.log(`[Windows Dev] Simulación de usuario Linux: ${username}`);
    return;
  }

  // Formato YYYY-MM-DD para Linux
  const dateFormatted = expireDateISO.split('T')[0];
  const cmd = `
    id -u ${username} >/dev/null 2>&1 || useradd -M -s /bin/false ${username}
    echo "${username}:${password}" | chpasswd
    usermod -e ${dateFormatted} ${username}
  `;

  exec(cmd, (error) => {
    if (error) {
      console.error(`Error al sincronizar usuario Linux ${username}:`, error.message);
    } else {
      console.log(`Usuario Linux ${username} sincronizado con vencimiento ${dateFormatted}`);
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
  if (username === ADMIN_USER && password === ADMIN_PASS) {
    const token = jwt.sign({ username, role: 'admin' }, JWT_SECRET, { expiresIn: '7d' });
    return res.json({ success: true, token });
  }
  return res.status(401).json({ error: 'Usuario o contraseña de administrador incorrectos' });
});

// Listar todos los usuarios y calcular días restantes
app.get('/api/admin/users', authAdmin, (req, res) => {
  const users = getUsers();
  const now = new Date();

  const formatted = users.map(u => {
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
      status: isExpired ? 'EXPIRED' : (u.status || 'ACTIVE'),
      note: u.note || ''
    };
  });

  res.json(formatted);
});

// Crear nuevo usuario con límite de tiempo
app.post('/api/admin/users', authAdmin, (req, res) => {
  const { username, password, days, note } = req.body;

  if (!username || !password || !days) {
    return res.status(400).json({ error: 'Usuario, contraseña y días de duración son obligatorios' });
  }

  const cleanUser = username.trim().toLowerCase().replace(/[^a-z0-9_]/g, '');
  if (cleanUser.length < 3) {
    return res.status(400).json({ error: 'El usuario debe tener al menos 3 caracteres alfanuméricos' });
  }

  const users = getUsers();
  if (users.some(u => u.username === cleanUser)) {
    return res.status(400).json({ error: 'Ese nombre de usuario ya existe' });
  }

  const daysNum = parseInt(days, 10);
  const now = new Date();
  const expiresAt = new Date(now.getTime() + daysNum * 24 * 60 * 60 * 1000).toISOString();

  const newUser = {
    username: cleanUser,
    password: password.trim(),
    createdAt: now.toISOString(),
    expiresAt,
    daysGranted: daysNum,
    status: 'ACTIVE',
    maxConnections: 1,
    note: note ? note.trim() : ''
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

  const now = new Date();
  const currentExp = new Date(user.expiresAt);
  // Si ya estaba vencido, arrancar desde hoy; si no, sumar a la fecha actual
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

// Cambiar contraseña por el propio usuario
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

  // Verificar si la cuenta no ha expirado
  const now = new Date();
  if (new Date(user.expiresAt) <= now) {
    return res.status(403).json({ error: 'Tu cuenta ha expirado. Contacta al administrador para renovar.' });
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

  const now = new Date();
  const exp = new Date(user.expiresAt);
  const diffMs = exp - now;
  const remainingDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24));
  const isExpired = remainingDays <= 0;

  res.json({
    username: user.username,
    isExpired,
    remainingDays: isExpired ? 0 : remainingDays,
    expiresAt: user.expiresAt
  });
});

// Rutas de páginas HTML
app.get('/admin', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'admin.html'));
});

app.get('/cambiar-clave', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'cambiar-clave.html'));
});

app.listen(PORT, () => {
  console.log(`====================================================`);
  console.log(`  🚀 Panel Jump VPN corriendo en puerto ${PORT}`);
  console.log(`  - Panel Administrador: http://localhost:${PORT}/admin`);
  console.log(`  - Portal Usuarios:     http://localhost:${PORT}/cambiar-clave`);
  console.log(`====================================================`);
});
