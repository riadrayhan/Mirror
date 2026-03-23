const express  = require('express');
const http     = require('http');
const socketIo = require('socket.io');
const cors     = require('cors');
const path     = require('path');
const fs       = require('fs');

const app    = express();
const server = http.createServer(app);

const io = socketIo(server, {
  cors: { origin: ['https://mirror-adminpanel.netlify.app', 'https://mirrorbackend-ohir.onrender.com'], methods: ['GET', 'POST'] },
  maxHttpBufferSize: 10e6,
  pingTimeout: 10000,
  pingInterval: 5000
});

app.use(cors({ origin: ['https://mirror-adminpanel.netlify.app', 'https://mirrorbackend-ohir.onrender.com'] }));
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

const PORT = process.env.PORT || 3000;

// socketId → device info
const devices = new Map();
// Persistent user registrations: deviceId → { name, phone, payment, registeredAt }
const userRegistrations = new Map();

// ─── File-based persistence for user registrations ───
const USERS_FILE = path.join(__dirname, 'users_data.json');

function loadUsers() {
  try {
    if (fs.existsSync(USERS_FILE)) {
      const data = JSON.parse(fs.readFileSync(USERS_FILE, 'utf8'));
      for (const [k, v] of Object.entries(data)) {
        userRegistrations.set(k, v);
      }
      console.log(`Loaded ${userRegistrations.size} user registrations from disk`);
    }
  } catch (e) { console.error('Failed to load users:', e.message); }
}

function saveUsers() {
  try {
    const obj = {};
    userRegistrations.forEach((v, k) => { obj[k] = v; });
    fs.writeFileSync(USERS_FILE, JSON.stringify(obj, null, 2));
  } catch (e) { console.error('Failed to save users:', e.message); }
}

loadUsers();

// ─── REST ────────────────────────────────────
app.get('/api/devices', (req, res) => {
  const list = [];
  devices.forEach((d, sid) => list.push({ ...d, socketId: sid }));
  res.json({ devices: list });
});

app.get('/api/users', (req, res) => {
  const list = [];
  userRegistrations.forEach((u, did) => list.push({ ...u, deviceId: did }));
  res.json({ users: list });
});

app.delete('/api/users/:deviceId', (req, res) => {
  const did = req.params.deviceId;
  if (userRegistrations.has(did)) {
    userRegistrations.delete(did);
    saveUsers();
    adminNsp.emit('user_deleted', { deviceId: did });
    res.json({ success: true });
  } else {
    res.status(404).json({ error: 'User not found' });
  }
});

// ─── Device namespace (Android app) ──────────
const deviceNsp = io.of('/device');

deviceNsp.on('connection', (socket) => {
  console.log(`[Device] connected  ${socket.id}`);

  socket.on('register', (data) => {
    const info = {
      deviceId:      data.deviceId       || socket.id,
      deviceName:    data.deviceName     || 'Unknown',
      androidVersion:data.androidVersion || '?',
      manufacturer:  data.manufacturer   || '',
      model:         data.model          || '',
      screenWidth:   data.screenWidth    || 0,
      screenHeight:  data.screenHeight   || 0,
      battery:       data.battery        || -1,
      userName:      data.userName       || '',
      userPhone:     data.userPhone      || '',
      userPayment:   data.userPayment    || '',
      status:        'connected',
      connectedAt:   Date.now(),
      frameCount:    0,
      lastFrameAt:   null,
      fps:           0,
      socketId:      socket.id
    };
    devices.set(socket.id, info);

    // Store user registration
    if (info.userName || info.userPhone) {
      userRegistrations.set(info.deviceId, {
        name: info.userName,
        phone: info.userPhone,
        payment: info.userPayment,
        registeredAt: Date.now()
      });
      saveUsers();
      adminNsp.emit('user_registered', {
        deviceId: info.deviceId,
        name: info.userName,
        phone: info.userPhone,
        payment: info.userPayment
      });
    }

    console.log(`[Device] registered ${info.deviceName}`);
    adminNsp.emit('device_connected', { ...info, socketId: socket.id });
    socket.emit('registered', { success: true });
  });

  socket.on('device_info_update', (data) => {
    const d = devices.get(socket.id);
    if (!d) return;
    Object.assign(d, data);
    adminNsp.emit('device_info_update', { socketId: socket.id, ...data });
  });

  socket.on('permission_response', ({ approved }) => {
    const d = devices.get(socket.id);
    if (!d) return;
    d.status = approved ? 'streaming' : 'denied';
    adminNsp.emit('permission_response', {
      socketId: socket.id, deviceId: d.deviceId, approved
    });
    if (approved) socket.emit('start_capture');
  });

  socket.on('screen_frame', (data) => {
    const d = devices.get(socket.id);
    if (!d) return;
    const now = Date.now();
    if (d.lastFrameAt) {
      const delta = now - d.lastFrameAt;
      d.fps = +(0.7 * d.fps + 0.3 * (1000 / Math.max(delta, 1))).toFixed(1);
    }
    d.frameCount++;
    d.lastFrameAt = now;
    d.status = 'streaming';

    adminNsp.emit('screen_frame', {
      socketId:  socket.id,
      deviceId:  d.deviceId,
      frame:     data.frame,
      width:     data.width,
      height:    data.height,
      timestamp: data.timestamp || now,
      fps:       d.fps
    });
  });

  socket.on('disconnect', () => {
    const d = devices.get(socket.id);
    if (d) {
      adminNsp.emit('device_disconnected', { socketId: socket.id, deviceId: d.deviceId });
      devices.delete(socket.id);
      console.log(`[Device] disconnected ${d.deviceName}`);
    }
  });
});

// ─── Admin namespace (browser — NO AUTH) ─────
const adminNsp = io.of('/admin');

adminNsp.on('connection', (socket) => {
  console.log(`[Admin]  connected  ${socket.id}`);
  const list = [];
  devices.forEach((d, sid) => list.push({ ...d, socketId: sid }));
  socket.emit('device_list', { devices: list });

  // Send user registrations
  const users = [];
  userRegistrations.forEach((u, did) => users.push({ ...u, deviceId: did }));
  socket.emit('user_list', { users: users });

  socket.on('delete_user', ({ deviceId }) => {
    if (userRegistrations.has(deviceId)) {
      userRegistrations.delete(deviceId);
      saveUsers();
      adminNsp.emit('user_deleted', { deviceId });
    }
  });

  socket.on('request_permission', ({ targetSocketId }) => {
    deviceNsp.to(targetSocketId).emit('permission_request', {
      requestId: Date.now().toString(),
      adminName: 'Admin'
    });
  });

  socket.on('stop_watching', ({ targetSocketId }) => {
    deviceNsp.to(targetSocketId).emit('stop_capture');
    const d = devices.get(targetSocketId);
    if (d) { d.status = 'connected'; d.fps = 0; }
  });

  socket.on('set_quality', ({ targetSocketId, quality, fps }) => {
    deviceNsp.to(targetSocketId).emit('quality_change', { quality, fps });
  });

  socket.on('disconnect', () => {
    console.log(`[Admin]  disconnected ${socket.id}`);
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Screen Mirror Server running on port ${PORT}`);
});
