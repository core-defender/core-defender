// priority: 100
// The JSON file is the editable source; the bundled copy bootstraps new installations.
const CD_CONFIG = JSON.parse(String(CoreDefenderWave.defaultConfig()))

function cdValidateConfig(config, server) {
  function requireValue(ok, path) { if (!ok) throw new Error('설정값 확인: ' + path) }
  function number(value, min, max, path, integer) {
    requireValue(typeof value === 'number' && isFinite(value) && value >= min && value <= max && (!integer || Math.floor(value) === value), path)
  }
  const checkedTypes = {}
  function mob(type, path) {
    requireValue(typeof type === 'string', path)
    if (!checkedTypes[type]) {
      requireValue(CoreDefenderWave.isMobType(server, type), path + ' (' + type + ')')
      checkedTypes[type] = true
    }
  }
  requireValue(config.schemaVersion === 1, 'schemaVersion = 1')
  requireValue(config.dimension === 'coredefender:defense', 'dimension')
  requireValue(config.maxTier === 2, 'maxTier = 2')
  number(config.waveDelaySeconds, 60, 86400, 'waveDelaySeconds', true)
  const rules = config.rules
  requireValue(!!rules, 'rules')
  const limits = {
    spawnMinDistance: [16, 128], spawnMaxDistance: [16, 128], spawnAcceleration: [0, .9],
    bossRemainingFraction: [0, 1], wardReduction: [0, .99], wardSeconds: [0, 600],
    summonInitialSeconds: [0, 600], summonIntervalSeconds: [1, 600], summonMaxAlive: [0, 128],
    summonBatchSize: [1, 16], summonerStandOff: [2, 64]
  }
  Object.keys(limits).forEach(key => number(rules[key], limits[key][0], limits[key][1], 'rules.' + key,
    key === 'summonMaxAlive' || key === 'summonBatchSize'))
  requireValue(rules.spawnMaxDistance >= rules.spawnMinDistance, 'spawnMaxDistance >= spawnMinDistance')
  requireValue(Array.isArray(config.tiers) && config.tiers.length === 2, 'tiers: 2개 난이도')
  config.tiers.forEach((tier, ti) => {
    requireValue(Array.isArray(tier) && tier.length === 10, 'tiers[' + ti + ']: 10개 웨이브')
    tier.forEach((wave, wi) => {
      let path = 'tiers[' + ti + '][' + wi + ']'
      number(wave.healthMultiplier, 0.1, 32, path + '.healthMultiplier')
      number(wave.damageMultiplier, 1, 32, path + '.damageMultiplier')
      number(wave.speedMultiplier, 1, 2, path + '.speedMultiplier')
      number(wave.maxAlive, 1, 512, path + '.maxAlive', true)
      number(wave.spawnInterval, 1, 1200, path + '.spawnInterval', true)
      number(wave.spawnBatchSize, 1, 16, path + '.spawnBatchSize', true)
      requireValue(Array.isArray(wave.mobs) && wave.mobs.length > 0, path + '.mobs')
      let total = 0
      wave.mobs.forEach((entry, mi) => {
        mob(entry.type, path + '.mobs[' + mi + '].type')
        number(entry.count, 1, 10000, path + '.mobs[' + mi + '].count', true)
        requireValue(!entry.boss, path + '.mobs: 보스는 bosses에서 지정')
        if (entry.elite !== undefined) requireValue(typeof entry.elite === 'boolean', path + '.elite')
        if (entry.breaker !== undefined) requireValue(typeof entry.breaker === 'boolean', path + '.breaker')
        total += entry.count
      })
      requireValue(total <= 10000, path + ': 총수 10000 이하')
      requireValue(Array.isArray(wave.rewards), path + '.rewards')
      wave.rewards.forEach(reward => {
        requireValue(CoreDefenderWave.isItemType(reward.item), path + '.rewards.item')
        number(reward.count, 1, 4096, path + '.rewards.count', true)
      })
    })
  })
  requireValue(Array.isArray(config.bosses) && config.bosses.length === 2, 'bosses')
  config.bosses.forEach((tier, ti) => ['mini', 'repeatMini', 'boss'].forEach(kind => {
    let entry = tier[kind], path = 'bosses[' + ti + '].' + kind
    requireValue(!!entry, path)
    mob(entry.type, path + '.type')
    number(entry.health, 1, 1024, path + '.health')
    number(entry.attack, 0, 2048, path + '.attack')
    number(entry.armor, 0, 30, path + '.armor')
  }))
  requireValue(!!config.repair && CoreDefenderWave.isItemType(config.repair.item), 'repair.item')
  number(config.repair.perCollapsed, 0, 4096, 'repair.perCollapsed', true)
  number(config.repair.perHundredMissing, 0, 4096, 'repair.perHundredMissing', true)
  requireValue(Array.isArray(config.sideUpgrade) && config.sideUpgrade.length === 4, 'sideUpgrade')
  config.sideUpgrade.forEach(cost => {
    // Optional mod materials may be absent in the minimal server test runtime.
    requireValue(typeof cost.item === 'string' && /^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(cost.item), 'sideUpgrade.item')
    number(cost.count, 1, 4096, 'sideUpgrade.count', true)
  })
  requireValue(!!config.expand && CoreDefenderWave.isItemType(config.expand.item), 'expand.item')
  requireValue(config.expand.maxHalf === 4, 'expand.maxHalf = 4')
}
