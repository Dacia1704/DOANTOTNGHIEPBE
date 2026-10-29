import assert from 'node:assert/strict';
import http from 'node:http';
import { once } from 'node:events';
import test from 'node:test';
import { createVnPayIpnProxy } from './vnpay-ipn-proxy.mjs';

async function listen(server) {
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  return server.address().port;
}

async function close(server) {
  server.closeAllConnections();
  await new Promise(resolve => server.close(resolve));
}

test('forwards only the IPN path, preserves signed query encoding, and drops client credentials', async () => {
  let received;
  const backend = http.createServer((request, response) => {
    received = { url: request.url, headers: request.headers, method: request.method };
    response.writeHead(200, { 'Content-Type': 'application/json' });
    response.end(JSON.stringify({ RspCode: '97', Message: 'Invalid signature' }));
  });
  const backendPort = await listen(backend);
  const proxy = createVnPayIpnProxy({ backendPort, log() {} });
  const proxyPort = await listen(proxy);
  try {
    const path = '/api/payments/vnpay/ipn?vnp_OrderInfo=Thanh%20toan%20test&vnp_TxnRef=test123&vnp_SecureHash=invalid';
    const response = await fetch(`http://127.0.0.1:${proxyPort}${path}`, {
      headers: { Authorization: 'Bearer test-only', Cookie: 'test-only=secret', 'X-Forwarded-For': '203.0.113.1' },
    });
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { RspCode: '97', Message: 'Invalid signature' });
    assert.equal(received.url, path);
    assert.equal(received.method, 'GET');
    assert.equal(received.headers.authorization, undefined);
    assert.equal(received.headers.cookie, undefined);
    assert.equal(received.headers['x-forwarded-for'], undefined);
  } finally {
    await close(proxy);
    await close(backend);
  }
});

test('does not expose payment, return, actuator, or other backend routes or methods', async () => {
  let calls = 0;
  const backend = http.createServer((request, response) => { calls++; response.end('{}'); });
  const backendPort = await listen(backend);
  const proxy = createVnPayIpnProxy({ backendPort, log() {} });
  const proxyPort = await listen(proxy);
  try {
    for (const path of ['/', '/api/payments', '/actuator/env', '/api/payments/vnpay/return', '/api/payments/vnpay/ipn/']) {
      assert.equal((await fetch(`http://127.0.0.1:${proxyPort}${path}`)).status, 404);
    }
    const response = await fetch(`http://127.0.0.1:${proxyPort}/api/payments/vnpay/ipn`, { method: 'POST' });
    assert.equal(response.status, 405);
    assert.equal(response.headers.get('allow'), 'GET');
    assert.equal(calls, 0);
  } finally {
    await close(proxy);
    await close(backend);
  }
});

test('returns a retryable acknowledgement without backend error details when the backend is down', async () => {
  const placeholder = http.createServer();
  const backendPort = await listen(placeholder);
  await close(placeholder);
  const proxy = createVnPayIpnProxy({ backendPort, log() {} });
  const proxyPort = await listen(proxy);
  try {
    const response = await fetch(`http://127.0.0.1:${proxyPort}/api/payments/vnpay/ipn`);
    assert.equal(response.status, 502);
    assert.deepEqual(await response.json(), { RspCode: '99', Message: 'Backend unavailable' });
  } finally {
    await close(proxy);
  }
});
