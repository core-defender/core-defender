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
