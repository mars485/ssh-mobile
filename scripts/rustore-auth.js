// Формирует тело запроса POST /public/auth для RuStore Public API.
// Использование: node rustore-auth.js <keyId> <файл-с-ключом>
// Ключ — Base64 PKCS#8 из консоли RuStore (тот же, что выдаётся при создании доступа к API).
// Печатает JSON {"keyId","timestamp","signature"}; сам запрос выполняет вызывающая сторона.
const crypto = require('crypto');
const fs = require('fs');

const [, , keyId, keyPath] = process.argv;
if (!keyId || !keyPath) {
  console.error('usage: node rustore-auth.js <keyId> <keyFile>');
  process.exit(2);
}

let key;
try {
  const raw = fs.readFileSync(keyPath, 'utf8').replace(/\s+/g, '');
  key = crypto.createPrivateKey({ key: Buffer.from(raw, 'base64'), format: 'der', type: 'pkcs8' });
} catch (e) {
  console.error('Не удалось прочитать приватный ключ: ' + e.message);
  process.exit(2);
}

const timestamp = new Date().toISOString();
const signature = crypto.sign('sha512', Buffer.from(keyId + timestamp), key).toString('base64');

process.stdout.write(JSON.stringify({ keyId, timestamp, signature }));
