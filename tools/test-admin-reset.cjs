// Run: node tools/test-admin-reset.cjs
// KubeJS command registration and persisted progression regression checks.
// Node.js CommonJS runtime provides require and __dirname.
//noinspection JSUnresolvedFunction
const assert = require('node:assert/strict');
// Node.js CommonJS runtime provides require and __dirname.
//noinspection JSUnresolvedFunction
const fs = require('node:fs');
// Node.js CommonJS runtime provides require and __dirname.
//noinspection JSUnresolvedFunction
const vm = require('node:vm');
// Node.js CommonJS runtime provides require and __dirname.
//noinspection JSUnresolvedFunction
const path = require('node:path');

function setup(bridgeAvailable = true) {
  const data = new Map();
  data.set('coredefender', JSON.stringify({version: 3, stage: 7, uptime: 300, lastClearUptime: 250, wave: {index: 7, mult: 2}}));
  const calls = [];
  let root;
  const node = name => ({name, children: [], then(child) {this.children.push(child); return this;},
    executes(fn) {this.run = fn; return this;}, requires(fn) {this.permission = fn; return this;}});
  const server = {persistentData: {
    getString: key => data.get(key) || '', putString: (key, value) => data.set(key, value), remove: key => data.delete(key)
  }, tell: () => {}};
  const player = {tell: message => calls.push(['message', message])};
  const context = vm.createContext({
    CD_CONFIG: {}, Text: {yellow: s => s, red: s => s},
    CoreDefenderWave: {abort: () => calls.push(['abort']), isActive: () => true},
    CoreDefenderWall: {reset: (_, scope) => {calls.push(['reset', scope]); return bridgeAvailable;}},
    CoreDefenderEvents: {waveCleared() {}, waveFailed() {}},
    ServerEvents: {loaded() {}, unloaded() {}, tick() {}, commandRegistry(fn) {
      fn({commands: {literal: node, argument: node}, arguments: {INTEGER: {create() {}, getResult() {}}}, register: tree => {root = tree;}});
    }}
  });
  // Node.js CommonJS runtime provides require and __dirname.
  //noinspection JSUnresolvedVariable
  vm.runInContext(fs.readFileSync(path.join(__dirname, '../kubejs/server_scripts/coredefender/main.js'), 'utf8'), context);
  const admin = root.children.find(n => n.name === 'admin');
  assert.equal(admin.permission({hasPermission: level => level <= 1}), false);
  assert.equal(admin.permission({hasPermission: level => level <= 2}), true);
  const reset = admin.children.find(n => n.name === 'reset');
  return {calls, data, reset, execute(scope) {
    const command = scope ? reset.children.find(n => n.name === scope) : reset;
    assert.ok(command, `Missing command ${scope}`);
    command.run({source: {server, playerOrException: player}});
    return JSON.parse(data.get('coredefender'));
  }};
}

for (const scope of [undefined, 'all', 'progress', 'wall', 'size', 'levels', 'hp', 'facilities', 'storage', 'supply']) {
  const test = setup();
  const saved = test.execute(scope);
  assert.deepEqual(test.calls.slice(0, 2), [['abort'], ['reset', scope || 'default']]);
  assert.equal(saved.wave, null, `${scope}: stale wave must be removed`);
  const resetProgress = !scope || scope === 'all' || scope === 'progress';
  assert.equal(saved.stage, resetProgress ? 0 : 7);
  assert.equal(saved.uptime, resetProgress ? 0 : 300);
  assert.equal(saved.lastClearUptime, resetProgress ? -1 : 250);
}
const failed = setup(false);
const saved = failed.execute();
assert.equal(saved.stage, 7, 'Missing dimension must not erase progression');
assert.equal(saved.wave, null, 'Aborted wave must not remain persisted');
assert.ok(failed.calls.some(c => c[0] === 'message'));
console.log('PASS: 10 reset variants, permissions, persistence, and missing-dimension failure');
