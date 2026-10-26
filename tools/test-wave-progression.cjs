// Node.js supplies require in CommonJS modules.
//noinspection JSUnresolvedFunction
const assert = require('node:assert/strict');
// Node.js supplies require in CommonJS modules.
//noinspection JSUnresolvedFunction
const fs = require('node:fs');
// Node.js supplies require in CommonJS modules.
//noinspection JSUnresolvedFunction
const vm = require('node:vm');
// Node.js supplies require in CommonJS modules.
//noinspection JSUnresolvedFunction
const path = require('node:path');
// Node.js supplies __dirname in CommonJS modules.
//noinspection JSUnresolvedReference
const root = path.join(__dirname, '../kubejs/server_scripts/coredefender');
// Node.js supplies __dirname in CommonJS modules.
//noinspection JSUnresolvedReference
const configJson = fs.readFileSync(path.join(__dirname, '../kubejs/config/coredefender-waves.json'), 'utf8');
let editableConfig = configJson;
const hooks = {};
let active = false;
let collapsed = false;
let started = [];
const messages = [];
const persistent = new Map();
const player = { level: { dimension: 'coredefender:defense' }, tell: text => messages.push(text), give() {} };
const players = [player];
players.isEmpty = () => players.length === 0;
const server = { players, tickCount: 0, tell: text => messages.push(text), runCommandSilent: text => messages.push(text),
  persistentData: { getString: key => persistent.get(key) || '', putString: (key, value) => persistent.set(key, value), remove: key => persistent.delete(key) } };
player.server = server;
const context = vm.createContext({
  ServerEvents: { loaded: fn => hooks.loaded = fn, unloaded: fn => hooks.unloaded = fn, tick: fn => hooks.tick = fn, commandRegistry() {} },
  CoreDefenderEvents: { waveCleared: fn => hooks.clear = fn, waveFailed: fn => hooks.fail = fn },
  CoreDefenderWave: { defaultConfig: () => configJson, readConfig: () => editableConfig, isMobType: (_, id) => id.startsWith('minecraft:'), isItemType: id => typeof id === 'string' && id.includes(':'), isActive: () => active, start: (s, json) => { active = true; started.push(JSON.parse(json)); return ''; }, abort: () => active = false },
  CoreDefenderWall: { info: () => JSON.stringify({ segments: [{ id: 'N0', collapsed }] }) },
  Text: { gold: x => x, red: x => x, green: x => x, yellow: x => x, gray: x => x },
  Item: { of: (item, count) => ({ item, count }) }, server, player
});
for (const file of ['config.js', 'progression.js', 'main.js']) vm.runInContext(fs.readFileSync(path.join(root, file), 'utf8'), context, { filename: file });
const run = expression => vm.runInContext(expression, context);
const state = () => JSON.parse(run('JSON.stringify(cdState)'));
function seconds(count) { for (let i = 0; i < count; i++) { server.tickCount += 20; hooks.tick({ server }); } }
hooks.loaded({ server });
seconds(600);
assert.equal(started.length, 0, 'Entry/online time must not enable waves');
run('cdEnable(server)'); seconds(539);
assert.equal(state().remaining, 61);
run('cdEnable(server)');
assert.equal(state().remaining, 61, 'Repeated start must not reset the countdown');
seconds(1);
assert.equal(messages.filter(x => x.includes('1분 후 웨이브 1-1')).length, 1);
players.length = 0; seconds(100);
assert.equal(state().remaining, 60, 'No online players pauses the countdown');
players.push(player); seconds(60);
assert.equal(started.length, 1);
assert.equal(started[0].label, '웨이브 1-1');
seconds(600); assert.equal(started.length, 1, 'Active waves must not overlap');
active = false; hooks.clear({ server, label: '1-1' });
assert.equal(state().stage, 1); assert.equal(state().remaining, 600);
seconds(599); assert.equal(started.length, 1);
seconds(1); assert.equal(started.length, 2);
active = false; collapsed = true; hooks.fail({ server, label: '1-2' });
seconds(600); assert.equal(state().remaining, 600);
collapsed = false; seconds(60);
hooks.unloaded({ server }); hooks.loaded({ server });
assert.equal(state().remaining, 540, 'Waiting countdown survives restart');
run('cdState.wave = { index: 1, mult: 1 }; cdSave(server); cdInit(server)');
assert.equal(state().remaining, 600, 'Interrupted wave restarts with full preparation time');
run('cdState.stage = 9; cdState.wave = null');
assert.equal(run('cdNextStage().stage.mobs.at(-1).type'), 'minecraft:evoker');
hooks.clear({ server, label: '1-10' });
assert.equal(state().stage, 10);
assert.equal(run('cdBossKind(cdState)'), '');
for (let repeat = 1; repeat <= 40; repeat++) {
  assert.equal(run('cdBossKind(cdState)'), repeat % 10 === 0 ? 'boss' : repeat % 5 === 0 ? 'mini' : '');
  assert.equal(run('cdNextStage().stage.healthMultiplier'), 1.8);
  hooks.clear({ server, label: 'repeat' });
}
run('cdAdvance(server, player)');
assert.equal(state().tier, 2); assert.equal(state().stage, 0); assert.equal(state().remaining, 600);
run('cdAdvance(server, player)'); assert.equal(state().tier, 2, 'Duplicate promotion must be rejected');
run('cdState.stage = 10; cdAdvance(server, player)'); assert.equal(state().tier, 2, 'No tier 3 in this release');
const config = JSON.parse(run('JSON.stringify(CD)'));
assert.equal(config.tiers.length, 2);
for (const tier of config.tiers) {
  assert.equal(tier.length, 10);
  let previousTotal = 0, previousHealth = 0;
  for (const wave of tier) {
    const total = wave.mobs.reduce((n, m) => n + m.count, 0);
    assert.ok(total > previousTotal && wave.healthMultiplier > previousHealth);
    assert.ok(wave.spawnInterval >= 2 && wave.maxAlive <= 256);
    previousTotal = total; previousHealth = wave.healthMultiplier;
  }
}
assert.equal(run('cdNewProgress({ version: 3, stage: 7 }, 300).stage'), 7);
assert.equal(run('cdNewProgress({ version: 3, stage: 7 }, 300).enabled'), false);
console.log('PASS: command activation, 60-second alert, offline pause, completion timer, failure/restart, 20 wave profiles, repeat bosses, tier promotion and migration');

// Hot reload must validate before swapping and preserve the active wave snapshot.
active = false;
run('cdStartWave(server, player, true)');
const runningWave = JSON.stringify(started.at(-1));
const oldCountdown = state().remaining;
let changed = JSON.parse(configJson);
changed.waveDelaySeconds = 120;
changed.rules.wardSeconds = 30;
changed.tiers[1][9].healthMultiplier = 5;
editableConfig = JSON.stringify(changed);
assert.equal(run('cdReloadConfig(server, player)'), true);
assert.equal(JSON.stringify(started.at(-1)), runningWave);
assert.equal(state().wave.stage.healthMultiplier, 3);
assert.equal(state().remaining, oldCountdown);
assert.equal(run('cdNextStage().stage.healthMultiplier'), 5);
assert.equal(run('CD.rules.wardSeconds'), 30);
run('cdApplyConfigTimer(server, player)');
assert.equal(state().remaining, oldCountdown, 'Cannot reset preparation during combat');
active = false;
run('cdApplyConfigTimer(server, player)');
assert.equal(state().remaining, 120);
for (const bad of ['{bad json', JSON.stringify({...changed, waveDelaySeconds: 0}), JSON.stringify({...changed, tiers: []}), JSON.stringify({...changed, rules: {...changed.rules, wardReduction: 1}})]) {
  editableConfig = bad;
  assert.equal(run('cdReloadConfig(server, player)'), false);
  assert.equal(run('CD.waveDelaySeconds'), 120);
  assert.equal(state().remaining, 120);
}
console.log('PASS: live reload, rejected malformed/invalid settings, running-wave snapshot and explicit timer apply');

editableConfig = configJson;
active = false;
run('cdReloadConfig(server, player); cdAdminAuto(server, false)');
const beforePause = state().remaining;
const beforeForced = started.length;
seconds(1200);
assert.equal(started.length, beforeForced);
assert.equal(state().remaining, beforePause);
hooks.unloaded({server}); hooks.loaded({server});
assert.equal(state().autoPaused, true, 'Admin pause survives restart');
run('cdStartWave(server, player, true)');
assert.equal(started.length, beforeForced + 1, 'Force starts even while automatic starts are disabled');
run('cdStartWave(server, player, true)');
assert.equal(started.length, beforeForced + 1, 'Force must not overlap a running wave');
run('cdAdminAuto(server, false)');
assert.equal(active, true, 'Disabling automatic starts must not abort combat');
active = false;
hooks.clear({server, label: 'forced'});
seconds(1200);
assert.equal(started.length, beforeForced + 1, 'Clearing forced wave must not reactivate automatic starts');
run('cdAdminAuto(server, true)');
assert.equal(state().remaining, 600);
seconds(600);
assert.equal(started.length, beforeForced + 2);
active = false;
collapsed = true;
run('cdStartWave(server, player, true)');
assert.equal(started.length, beforeForced + 2, 'Force cannot start with collapsed walls');
console.log('PASS: persistent admin auto off/on, force while paused, no overlap, no implicit resume and collapsed-wall guard');
