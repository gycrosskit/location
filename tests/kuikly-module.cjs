const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require(process.env.TYPESCRIPT_PATH || '/Applications/DevEco-Studio.app/Contents/tools/hvigor/hvigor/node_modules/typescript');
const requests = [];
class LocationOptions {}
class LocationClient {
  currentLocation(options) {
    let finish;
    const result = new Promise(resolve => { finish = resolve; });
    const request = { result, finish, options, cancels: 0, cancel() { this.cancels++; finish({ status: 'cancelled' }); } };
    requests.push(request);
    return request;
  }
}
const source = fs.readFileSync(`${__dirname}/../ohos/location-native/src/main/ets/GycLocationModule.ets`, 'utf8');
const output = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText;
const mod = { exports: {} };
vm.runInNewContext(output, { exports: mod.exports, require: key => key === './LocationClient'
  ? { LocationClient, LocationOptions } : { KuiklyRenderBaseModule: class { onDestroy() {} } } });
const { GycLocationModule: Module } = mod.exports;
const params = id => JSON.stringify({ requestId: id, timeoutMillis: 10000, maxAgeMillis: 300000, maxAccuracyMeters: 500 });
const flush = async () => { await Promise.resolve(); await Promise.resolve(); };
(async () => {
  const m = new Module(), values = [];
  m.call('currentLocation', params(1), value => values.push(value));
  assert.equal(requests[0].options.timeoutMillis, 10000);
  assert.equal(requests[0].options.maxAgeMillis, 300000);
  assert.equal(requests[0].options.maxAccuracyMeters, 500);
  m.call('cancelLocation', params(9), null);
  assert.equal(requests[0].cancels, 0);
  const busy = [];
  m.call('currentLocation', params(2), value => busy.push(value.status));
  assert.deepEqual(busy, ['error']);
  m.call('cancelLocation', params(1), null);
  assert.equal(requests[0].cancels, 1);
  // 同一调用栈立即替换，不等待旧取消 Promise 的回执 microtask。
  m.call('currentLocation', params(2), value => values.push(value));
  assert.equal(requests.length, 2, 'cancel must synchronously detach old request');
  await flush();
  assert.equal(values.length, 0, 'cancelled request cannot deliver into replacement');
  m.call('cancelLocation', params(1), null);
  assert.equal(requests[1].cancels, 0);
  requests[1].finish({ status: 'available', fix: { latitude: 30, longitude: 120, accuracyMeters: 100, timestampMillis: 123 } });
  await flush();
  assert.equal(values[0].timestampMillis, 123);
  m.call('currentLocation', params(3), value => values.push(value));
  m.onDestroy();
  await flush();
  assert.equal(requests[2].cancels, 1);
  assert.equal(values.length, 1);
  m.call('currentLocation', params(4), value => values.push(value));
  assert.equal(requests.length, 3);
  const other = new Module(), invalid = [];
  other.call('currentLocation', '{bad', value => invalid.push(value.status));
  other.call('currentLocation', JSON.stringify({ requestId: 0 }), value => invalid.push(value.status));
  assert.deepEqual(invalid, ['error', 'error']);
  console.log('Location bridge: host options, request ownership, cancellation, replacement, destroy and malformed input passed');
})().catch(error => { console.error(error); process.exitCode = 1; });
