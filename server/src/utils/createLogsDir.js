const fs = require('fs');
const path = require('path');

const logsDir = path.join(__dirname, '../../logs');

if (!fs.existsSync(logsDir)) {
  fs.mkdirSync(logsDir, { recursive: true });
}

module.exports = logsDir;

