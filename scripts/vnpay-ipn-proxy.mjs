import http from 'node:http';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const IPN_PATH = '/api/payments/vnpay/ipn';

export function createVnPayIpnProxy({ backendPort = 8080, log = console.log } = {}) {
  return http.createServer({ maxHeaderSize: 8192, requestTimeout: 15000 }, (request, response) => {
    if (!request.url?.startsWith('/') || request.url.length > 8192) {
      response.writeHead(400).end();
      return;
    }
    const url = new URL(request.url, 'http://localhost');
    if (url.pathname !== IPN_PATH) {
      response.writeHead(404).end();
      return;
    }
    if (request.method !== 'GET') {
      response.writeHead(405, { Allow: 'GET' }).end();
      return;
    }
    const reference = url.searchParams.get('vnp_TxnRef');
    const safeReference = /^[a-zA-Z0-9]{1,100}$/.test(reference ?? '') ? reference : '<missing-or-invalid>';
    const fail = () => {
      if (!response.headersSent) response.writeHead(502, { 'Content-Type': 'application/json' });
      if (!response.writableEnded) response.end(JSON.stringify({ RspCode: '99', Message: 'Backend unavailable' }));
      log(`VNPay IPN proxy: reference=${safeReference}, backend=unavailable`);
    };
    // The destination and path are fixed. Client cookies, Authorization, and forwarded headers are discarded.
    const upstream = http.request({
      hostname: '127.0.0.1',
      port: backendPort,
      path: IPN_PATH + url.search,
      method: 'GET',
      headers: { Accept: 'application/json' },
      timeout: 10000,
    }, upstreamResponse => {
      response.writeHead(upstreamResponse.statusCode ?? 502, {
        'Content-Type': upstreamResponse.headers['content-type'] ?? 'application/json',
        'Cache-Control': 'no-store',
      });
      let reply = '';
      upstreamResponse.on('data', chunk => {
        if (reply.length < 1024) reply += chunk.toString('utf8').slice(0, 1024 - reply.length);
      });
      upstreamResponse.on('end', () => {
        let acknowledgement = '<invalid>';
        try {
          const parsed = JSON.parse(reply);
          if (/^\d{2}$/.test(parsed.RspCode)) acknowledgement = parsed.RspCode;
        } catch {}
        log(`VNPay IPN proxy: reference=${safeReference}, httpStatus=${upstreamResponse.statusCode}, acknowledgement=${acknowledgement}`);
      });
      upstreamResponse.on('error', () => response.destroy());
      upstreamResponse.pipe(response);
    });
    upstream.on('timeout', () => upstream.destroy(new Error('IPN backend timeout')));
    upstream.on('error', fail);
    request.on('aborted', () => upstream.destroy());
    upstream.end();
  });
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  const port = Number(process.env.VNPAY_IPN_PROXY_PORT ?? 18080);
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid VNPAY_IPN_PROXY_PORT');
  createVnPayIpnProxy().listen(port, '127.0.0.1', () => {
    console.log(`VNPay IPN proxy ready at http://127.0.0.1:${port}${IPN_PATH}`);
  });
}
