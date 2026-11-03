// priority: 75

function cdNewProgress(saved, delay) {
  if (saved.version === 5) return saved
  if (saved.version === 4) {
    saved.version = 5
    saved.stage = Math.min(10, Math.floor(saved.stage / 2))
    saved.wave = null
    saved.remaining = delay
    saved.warned = false
    return saved
  }
  let cleared = saved.version === 3 ? Math.max(0, Math.min(10, Number(saved.stage) || 0)) : 0
  return { version: 5, tier: 1, stage: cleared, repeats: 0, enabled: false,
    remaining: delay, warned: false, uptime: 0, wave: null }
}

function cdProgressLabel(state) {
  return `${state.tier}-${Math.min(10, state.stage + 1)}${state.stage >= 10 ? ` (반복 ${state.repeats + 1})` : ''}`
}

function cdBossKind(state) {
  let ordinal = state.stage >= 10 ? state.repeats + 1 : state.stage + 1
  return ordinal % 10 === 0 ? 'boss' : ordinal % 5 === 0 ? 'mini' : ''
}

function cdComposeWave(config, state) {
  let wave = cdComposeBase(config, state)
  cdApplyCulls(wave.stage, state.culls || {})
  return wave
}

function cdComposeBase(config, state) {
  let index = Math.min(9, state.stage)
  let base = config.tiers[state.tier - 1][index]
  let stage = JSON.parse(JSON.stringify(base))
  let kind = cdBossKind(state)
  if (kind) {
    let profile = config.bosses[state.tier - 1][kind === 'mini' && state.stage >= 10 ? 'repeatMini' : kind]
    let entry = JSON.parse(JSON.stringify(profile))
    entry.count = 1
    entry.breaker = true
    entry.boss = kind
    stage.mobs.push(entry)
  }
  return { index: index, stage: stage, label: cdProgressLabel(state), mult: 1 }
}

// 대기 중 처치한 같은 종류 몹만큼 다음 웨이브 항목을 줄인다. 항목마다 절반(올림)은 남기고 정예·보스는 줄이지 않는다.
/**
 * @param {{mobs: Array<{type: string, count: number, boss?: string, elite?: boolean, breaker?: boolean}>}} stage
 * @param {string} type
 */
function cdCullable(stage, type) {
  let entries = stage.mobs.filter(m => m.type === type && !m.boss && !m.elite)
  return entries.filter(m => !m.breaker).concat(entries.filter(m => m.breaker))
}

function cdCullLimit(stage, type) {
  return cdCullable(stage, type).reduce((sum, m) => sum + m.count - Math.ceil(m.count / 2), 0)
}

function cdApplyCulls(stage, culls) {
  Object.keys(culls).forEach(type => {
    let left = culls[type]
    cdCullable(stage, type).forEach(m => {
      let cut = Math.min(left, m.count - Math.ceil(m.count / 2))
      m.count -= cut
      left -= cut
    })
  })
}

// 반영되면 true. 다음 웨이브에 없는 종류이거나 이미 절반까지 줄었으면 false.
function cdRecordCull(config, state, type) {
  let limit = cdCullLimit(cdComposeBase(config, state).stage, type)
  state.culls = state.culls || {}
  let current = state.culls[type] || 0
  if (current >= limit) return false
  state.culls[type] = current + 1
  return true
}

function cdSchedule(state, delay) {
  state.remaining = delay
  state.warned = false
}

function cdAdvanceReason(state, active, collapsed) {
  if (active) return '웨이브 진행 중에는 난이도를 올릴 수 없습니다'
  if (state.tier >= 2) return '이번 구현의 최고 난이도는 2단계입니다'
  if (state.stage < 10) return '현재 난이도의 10웨이브를 먼저 클리어하세요'
  if (collapsed) return '무너진 성벽 복구가 필요합니다'
  return ''
}

// Called once per online second; completed waves and repairs explicitly reschedule.
function cdCountdown(state, active, collapsed) {
  if (!state.enabled || state.autoPaused || active || collapsed) return ''
  state.remaining = Math.max(0, state.remaining - 1)
  if (!state.warned && state.remaining <= 60) {
    state.warned = true
    return 'warn'
  }
  return state.remaining === 0 ? 'start' : ''
}
