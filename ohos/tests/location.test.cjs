const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require(process.env.TYPESCRIPT_PATH || '/Applications/DevEco-Studio.app/Contents/tools/ohpm/node_modules/typescript');
const source = fs.readFileSync(__dirname + '/../location-native/src/main/ets/LocationClient.ets', 'utf8');
let granted = true, enabled = true, listener, removes = 0, systemCache, cacheError = 3301200, cacheReads = 0;
const geoLocationManager = {
  LocationRequestPriority: { ACCURACY: 1 }, isLocationEnabled: () => enabled,
  getLastLocation: () => {
    cacheReads++;
    if (!systemCache) throw Object.assign(new Error('No cached location'), {code: cacheError});
    return systemCache;
  },
  on: (_, options, callback) => { listener = callback; },
  off: (_, callback) => { assert.equal(callback, listener); removes++; },
};
const moduleExports = {};
vm.runInNewContext(ts.transpileModule(source, {compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020}}).outputText,
  {exports: moduleExports, require: (name) => name === '@kit.LocationKit' ? {geoLocationManager} : {
    abilityAccessCtrl: {createAtManager: () => ({getSelfPermissionStatus: () => granted ? 1 : 0}), PermissionStatus: {GRANTED: 1}}
  }, setTimeout, clearTimeout, Date, Number, Error});
(async () => {
  const {LocationClient, LocationOptions} = moduleExports;
  granted = false;
  assert.equal((await new LocationClient().currentLocation().result).status, 'permission_missing');
  granted = true; enabled = false;
  assert.equal((await new LocationClient().currentLocation().result).status, 'service_disabled');
  enabled = true;
  systemCache = {latitude: 30, longitude: 120, accuracy: 20, timeStamp: Date.now()};
  assert.equal((await new LocationClient().currentLocation().result).status, 'available');
  assert.equal(cacheReads, 1, 'a new client reads the system cache after permission/service checks');
  assert.equal(listener, undefined, 'acceptable system cache does not start a listener');
  for (const fix of [
    {...systemCache, timeStamp: Date.now() - 400000}, {...systemCache, accuracy: 501},
  ]) {
    systemCache = fix;
    const waiting = new LocationClient().currentLocation(), late = listener;
    waiting.cancel();
    late({latitude: 30, longitude: 120, accuracy: 1, timeStamp: Date.now()});
    assert.equal((await waiting.result).status, 'cancelled', 'rejected system cache keeps cancellable live lookup');
  }
  systemCache = undefined; removes = 0;
  for (const [code, status] of [[201, 'permission_missing'], [3301100, 'service_disabled']]) {
    cacheError = code;
    assert.equal((await new LocationClient().currentLocation().result).status, status);
  }
  cacheError = 3301200;
  const client = new LocationClient();
  const cancelled = client.currentLocation(); cancelled.cancel(); cancelled.cancel();
  assert.equal((await cancelled.result).status, 'cancelled'); assert.equal(removes, 1);
  const options = new LocationOptions(); options.timeoutMillis = 10;
  assert.equal((await client.currentLocation(options).result).status, 'timed_out'); assert.equal(removes, 2);
  const pending = client.currentLocation();
  listener({latitude: 30, longitude: 120, accuracy: 1, timeStamp: Date.now() - 400000});
  listener({latitude: 30, longitude: 120, accuracy: 501, timeStamp: Date.now()});
  assert.equal(removes, 2);
  listener({latitude: 30, longitude: 120, accuracy: 20, timeStamp: Date.now()});
  assert.equal((await pending.result).status, 'available'); assert.equal(removes, 3);
  assert.equal((await client.currentLocation().result).status, 'available'); assert.equal(removes, 3);
  const isolated = new LocationClient();
  const old = isolated.currentLocation(), oldListener = listener;
  old.cancel();
  oldListener({latitude: 30, longitude: 120, accuracy: 1, timeStamp: Date.now()});
  assert.equal((await old.result).status, 'cancelled');
  const replacement = isolated.currentLocation();
  assert.notEqual(listener, oldListener, 'cancelled read cannot populate cache for successor');
  oldListener({latitude: 30, longitude: 120, accuracy: 1, timeStamp: Date.now()});
  const beforeReplacement = removes;
  listener({latitude: Infinity, longitude: 120, accuracy: 1, timeStamp: Date.now()});
  listener({latitude: 30, longitude: 120, accuracy: 1, timeStamp: Date.now() + 10000});
  assert.equal(removes, beforeReplacement);
  replacement.cancel();
  assert.equal((await replacement.result).status, 'cancelled');
  for (const [field, value] of [['timeoutMillis', 0], ['timeoutMillis', 2147483648],
    ['timeoutMillis', 1.5], ['maxAgeMillis', -1], ['maxAgeMillis', NaN],
    ['maxAccuracyMeters', -1], ['maxAccuracyMeters', Infinity]]) {
    const bad = new LocationOptions(); bad[field] = value;
    assert.throws(() => client.currentLocation(bad), /Invalid location options/);
  }
  const ancientOptions = new LocationOptions(); ancientOptions.maxAgeMillis = Number.MAX_SAFE_INTEGER;
  const ancient = new LocationClient().currentLocation(ancientOptions), beforeAncient = removes;
  listener({latitude: 30, longitude: 120, accuracy: 1, timeStamp: -1});
  assert.equal(removes, beforeAncient, 'negative Unix timestamp cannot become valid through a large cache budget');
  ancient.cancel(); assert.equal((await ancient.result).status, 'cancelled');
  const revoked = new LocationClient().currentLocation(options);
  granted = false;
  assert.equal((await revoked.result).status, 'permission_missing', 'timeout rechecks revoked permission');
  granted = true;
  const switchedOff = new LocationClient().currentLocation(options);
  enabled = false;
  assert.equal((await switchedOff.result).status, 'service_disabled', 'timeout rechecks system service');
  enabled = true;
  console.log('location HAR contract checks passed');
})().catch(error => { console.error(error); process.exitCode = 1; });
