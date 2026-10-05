const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require(process.env.TYPESCRIPT_PATH || '/Applications/DevEco-Studio.app/Contents/tools/ohpm/node_modules/typescript');
const source = fs.readFileSync(__dirname + '/../location-native/src/main/ets/LocationClient.ets', 'utf8');
let granted = true, enabled = true, listener, removes = 0;
const geoLocationManager = {
  LocationRequestPriority: { ACCURACY: 1 }, isLocationEnabled: () => enabled,
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
