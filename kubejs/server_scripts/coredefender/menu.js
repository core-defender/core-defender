// priority: 40
// 디펜스 관리 메뉴. 화면은 coredefender 모드(CoreDefenderScreen)가 그리고, 내용은 여기서 상태 JSON 으로 만든다.
//
// 흐름: 메뉴 키(K) 또는 코어 우클릭 → 모드가 'open' 동작 전송 → CoreDefenderEvents.menuAction → CoreDefenderUI.open(player, json)
// 화면이 열려 있는 동안 모드가 1초마다 'refresh' 를 보내 진행 상황을 갱신한다.

// 아이템/엔티티 이름은 클라이언트가 ID 로 번역하므로 ID 만 보낸다
function cdWaveState(stage, label, mult) {
  return {
    label: label,
    mult: Number(mult),
    mobs: stage.mobs.map(m => ({ type: m.type, count: m.count, elite: !!m.elite, boss: m.boss || '' })),
    rewards: stage.rewards.map(r => ({ item: r.item, count: Math.max(1, Math.floor(r.count * mult)) }))
  }
}

// 비용이 드는 동작의 버튼 상태: { enabled, reason, item?, count?, have? }
function cdCostAction(player, cost, blockedReason) {
  if (blockedReason) return { enabled: false, reason: blockedReason }
  let have = Number(player.inventory.count(cost.item))
  return { enabled: have >= cost.count, reason: have >= cost.count ? '' : '재료가 부족합니다', item: cost.item, count: cost.count, have: have }
}

function cdBuildMenuState(player) {
  let server = player.server
  let inDefense = cdInDefense(player)
  let wall = cdWall(server)
  let waveInfo = cdWaveInfo()
  let next = cdNextStage()

  let state = {
    title: '디펜스 기지',
    progress: next.label,
    travel: { action: inDefense ? 'leave' : 'enter' }
  }

  // 성벽: 구간 HP + 변별 강화 버튼
  state.wall = {
    interiorChunks: wall.interiorChunks,
    segmentsPerSide: wall.segmentsPerSide,
    segments: wall.segments,
    sides: wall.sides.map(s => {
      let cost = cdSideUpgradeCost(wall, s.side)
      return { side: s.side, level: s.level, upgrade: cdCostAction(player, cost, cost ? '' : '최대 강화') }
    })
  }

  if (waveInfo.active) {
    let wave = cdState.wave || { index: next.index, mult: next.mult }
    state.active = { remaining: waveInfo.remaining, total: waveInfo.total }
    state.wave = cdWaveState(wave.stage || next.stage, `${waveInfo.label} 진행 중`, wave.mult)
  } else {
    state.wave = cdWaveState(next.stage, `${next.label} / ${cdWaitingText(server)}`, next.mult)
  }

  let advanceReason = cdAdvanceReason(cdState, waveInfo.active, wall.segments.some(s => s.collapsed))
  if (!inDefense) advanceReason = '기지 안에서 난이도를 올려주세요'
  state.advance = { enabled: advanceReason === '', reason: advanceReason }

  let repairCost = cdRepairCost(wall)
  state.repair = cdCostAction(player, repairCost,
    waveInfo.active ? '웨이브 중에는 수복할 수 없습니다' : repairCost.count === 0 ? '수복할 성벽이 없습니다' : '')

  let expandCost = cdExpandCost(wall)
  state.expand = cdCostAction(player, expandCost,
    waveInfo.active ? '웨이브 중에는 확장할 수 없습니다' : !expandCost ? '최대 크기' : '')

  return state
}

function cdOpenMenu(player) {
  if (!cdState) cdInit(player.server)
  // Java bindings and JSON properties are supplied by CoreDefender at runtime.
  //noinspection JSUnresolvedReference
  CoreDefenderUI.open(player, JSON.stringify(cdBuildMenuState(player)))
}

function cdUpdateMenu(player) {
  // Java bindings and JSON properties are supplied by CoreDefender at runtime.
  //noinspection JSUnresolvedReference
  CoreDefenderUI.update(player, JSON.stringify(cdBuildMenuState(player)))
}

// Java bindings and JSON properties are supplied by CoreDefender at runtime.
//noinspection JSUnresolvedReference
CoreDefenderEvents.menuAction(event => {
  let player = event.player
  let server = player.server
  if (!cdState) cdInit(server)

  switch (String(event.action)) {
    case 'open':
      cdOpenMenu(player)
      return
    case 'refresh':
      break
    case 'advance':
      cdAdvance(server, player)
      break
    case 'repair':
      cdCmdRepair(server, player)
      break
    case 'upgrade':
      cdCmdUpgrade(server, player, String(event.arg))
      break
    case 'expand':
      cdCmdExpand(server, player)
      break
    case 'enter':
      // 화면은 클라이언트에서 이미 닫힘
      cdCmdEnter(server, player)
      return
    case 'leave':
      cdCmdLeave(server, player)
      return
  }
  cdUpdateMenu(player)
})
