// priority: 40
// 디펜스 메뉴. 화면은 coredefender 모드(CoreDefenderScreen)가 그리고, 내용은 여기서 상태 JSON 으로 만든다.
//
// 흐름: 메뉴 키(K) → 모드가 'open' 동작 전송 → CoreDefenderEvents.menuAction → CoreDefenderUI.open(player, json)
// 화면이 열려 있는 동안 모드가 1초마다 'refresh' 를 보내 진행 상황을 갱신한다.

// 아이템/엔티티 이름은 클라이언트가 ID 로 번역하므로 ID 만 보낸다
function cdWaveState(wave, label, mult) {
  return {
    label: label,
    mult: Number(mult),
    mobs: wave.mobs.map(m => ({ type: m.type, count: m.count, elite: !!m.elite })),
    rewards: wave.rewards.map(r => ({ item: r.item, count: Math.max(1, Math.floor(r.count * mult)) }))
  }
}

function cdBuildMenuState(player) {
  let here = cdWorldCfgByDimension(player.level.dimension)
  let cfg = here || cdDefaultWorld()
  let inWorld = !!here
  let ws = cdState.worlds[cfg.id]
  let active = cdState.active && cdState.active.world == cfg.id ? cdState.active : null

  let core = inWorld && !ws.coreBroken ? cdFindCore(player.server, cfg) : null
  let state = {
    title: cfg.name,
    progress: ws.cleared ? '클리어 완료' : `${ws.waveIndex} / ${cfg.waves.length}`,
    core: {
      broken: ws.coreBroken,
      hp: core ? Number(core.health) : cfg.core.hp,
      maxHp: core ? Number(core.maxHealth) : cfg.core.hp
    },
    worlds: CD.worlds.map(w => {
      let s = cdState.worlds[w.id]
      return {
        id: w.id,
        name: w.name,
        unlocked: s.unlocked,
        current: !!(here && here.id == w.id),
        status: !s.unlocked ? '잠김' : s.cleared ? '클리어 (반복 도전 가능)' : `웨이브 ${s.waveIndex}/${w.waves.length}`
      }
    }),
    travel: inWorld ? { action: 'leave', world: '' } : { action: 'enter', world: cfg.id }
  }

  if (active) {
    let total = cfg.waves[active.wave].mobs.reduce((sum, m) => sum + m.count, 0)
    state.active = { remaining: cdRuntime.queue.length + cdRuntime.mobs.length, total: total }
    state.wave = cdWaveState(cfg.waves[active.wave], `웨이브 ${active.wave + 1} 진행 중`, active.mult)
  } else {
    let next = cdNextWave(cfg)
    let label = next.replay ? `반복 도전 웨이브 ${next.index + 1}` : `다음 웨이브 ${next.index + 1}/${cfg.waves.length}`
    state.wave = cdWaveState(next.wave, label, next.mult)
  }

  let startReason = cdState.active ? '이미 웨이브가 진행 중입니다'
    : ws.coreBroken ? '코어 복구가 필요합니다'
    : !inWorld ? '디펜스 월드 안에서만 시작할 수 있습니다' : ''
  state.start = { enabled: startReason == '', reason: startReason }

  let cost = cfg.repairCost
  let have = Number(player.inventory.count(cost.item))
  let repairReason = !ws.coreBroken ? '코어가 정상입니다'
    : !inWorld ? '디펜스 월드 안에서만 복구할 수 있습니다'
    : have < cost.count ? '재료가 부족합니다' : ''
  state.repair = { enabled: repairReason == '', reason: repairReason, item: cost.item, count: cost.count, have: have }

  return state
}

function cdOpenMenu(player) {
  if (!cdState) cdInit(player.server, '스크립트 리로드')
  CoreDefenderUI.open(player, JSON.stringify(cdBuildMenuState(player)))
}

function cdUpdateMenu(player) {
  CoreDefenderUI.update(player, JSON.stringify(cdBuildMenuState(player)))
}

CoreDefenderEvents.menuAction(event => {
  let player = event.player
  let server = player.server
  if (!cdState) cdInit(server, '스크립트 리로드')

  switch (String(event.action)) {
    case 'open':
      cdOpenMenu(player)
      break
    case 'refresh':
      cdUpdateMenu(player)
      break
    case 'start':
      cdStartWave(server, player)
      cdUpdateMenu(player)
      break
    case 'repair':
      cdCmdRepair(server, player)
      cdUpdateMenu(player)
      break
    case 'enter':
      // 화면은 클라이언트에서 이미 닫힘
      cdCmdEnter(server, player, String(event.arg) || null)
      break
    case 'leave':
      cdCmdLeave(server, player)
      break
  }
})
