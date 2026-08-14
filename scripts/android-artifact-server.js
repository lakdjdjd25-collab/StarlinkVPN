const http = require('http');
const fs = require('fs');
const path = require('path');

const apkDir = process.env.APK_DIR || '/app/apk';
const port = Number(process.env.PORT) || 3000;

function listApks(callback) {
  fs.readdir(apkDir, (error, files) => {
    if (error) return callback(error);
    callback(null, files.filter((file) => file.endsWith('.apk')));
  });
}

http.createServer((request, response) => {
  if (request.url === '/' || request.url === '/health') {
    listApks((error, apks) => {
      if (error) {
        response.writeHead(500, { 'content-type': 'application/json' });
        return response.end(JSON.stringify({ ok: false, error: 'APK directory unavailable' }));
      }
      response.writeHead(apks.length ? 200 : 503, { 'content-type': 'application/json' });
      return response.end(JSON.stringify({ ok: apks.length > 0, apks }));
    });
    return;
  }

  const name = path.basename(request.url || '');
  if (!name.endsWith('.apk')) {
    response.writeHead(404);
    return response.end('Not found');
  }

  fs.readFile(path.join(apkDir, name), (error, data) => {
    if (error) {
      response.writeHead(404);
      return response.end('APK not found');
    }
    response.writeHead(200, {
      'content-type': 'application/vnd.android.package-archive',
      'content-disposition': `attachment; filename="${name}"`,
    });
    response.end(data);
  });
}).listen(port, '0.0.0.0');
