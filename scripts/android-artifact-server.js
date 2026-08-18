const http = require('http');
const fs = require('fs');

const apkPath = '/app/NimHUB-Vpn.apk';
const metadataPath = '/app/metadata.json';
const port = Number(process.env.PORT || 8080);

function sendJson(res, status, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(body),
    'cache-control': 'no-store',
  });
  res.end(body);
}

const server = http.createServer((req, res) => {
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    res.writeHead(405, { allow: 'GET, HEAD' });
    res.end();
    return;
  }

  if (req.url === '/health') {
    sendJson(res, 200, {
      ok: fs.existsSync(apkPath) && fs.existsSync(metadataPath),
      service: 'nimhub-android-builder',
    });
    return;
  }

  if (req.url === '/metadata') {
    if (!fs.existsSync(metadataPath)) {
      sendJson(res, 404, { error: 'metadata_not_found' });
      return;
    }
    res.writeHead(200, {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store',
    });
    if (req.method === 'HEAD') return res.end();
    fs.createReadStream(metadataPath).pipe(res);
    return;
  }

  if (req.url === '/apk' || req.url === '/') {
    if (!fs.existsSync(apkPath)) {
      sendJson(res, 404, { error: 'apk_not_found' });
      return;
    }
    const stat = fs.statSync(apkPath);
    res.writeHead(200, {
      'content-type': 'application/vnd.android.package-archive',
      'content-length': stat.size,
      'content-disposition': 'attachment; filename="NimHUB-Vpn-2.6.19-release.apk"',
      'cache-control': 'public, max-age=300',
    });
    if (req.method === 'HEAD') return res.end();
    fs.createReadStream(apkPath).pipe(res);
    return;
  }

  sendJson(res, 404, { error: 'not_found' });
});

server.listen(port, '0.0.0.0', () => {
  console.log(`NimHUB Android artifact server listening on ${port}`);
});
