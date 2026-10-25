// priority: 50
// 디펜스 진행: 스테이지 상태, 웨이브 시작/종료, 성벽 수복/강화/확장, 입장/퇴장, 명령어.
// 성벽/코어/웨이브 엔진은 coredefender 모드 (바인딩: CoreDefenderWall, CoreDefenderWave).
// 상태는 server.persistentData 에 JSON 문자열로 저장한다.

const CD = CD_CONFIG
const CD_KEY = 'coredefender'

let cdState = null

// ---------- 상태 ----------

function cdLoad(server) {
  let raw = server.persistentData.getString(CD_KEY)
  let saved = raw ? JSON.parse(raw) : {}
  // v2(월드 단위) 저장 데이터는 버린다
  cdState = saved.version === 3 ? saved : { version: 3, stage: 0, uptime: 0, lastClearUptime: -1, wave: null }
}

function cdSave(server) {
  server.persistentData.putString(CD_KEY, JSON.stringify(cdState))
}

// 월드 로드 시, 또는 /reload 로 스크립트 변수가 초기화된 뒤 처음 필요할 때 호출된다
function cdInit(server) {
  cdLoad(server)
  // 웨이브 엔진은 저장되지 않으므로 재시작 전 진행 중이던 웨이브 기록은 지운다 (페널티 없음)
  // KubeJS supplies these Java bindings and script globals at runtime.
  //noinspection JSUnresolvedVariable
  if (cdState.wave && !CoreDefenderWave.isActive()) {
    cdState.wave = null
    cdSave(server)
  }
}

function cdWall(server) {
  // KubeJS supplies these Java bindings and script globals at runtime.
  //noinspection JSUnresolvedVariable
  return JSON.parse(String(CoreDefenderWall.info(server)))
}

function cdWaveInfo() {
  // KubeJS supplies these Java bindings and script globals at runtime.
  //noinspection JSUnresolvedVariable
  return JSON.parse(String(CoreDefenderWave.info()))
}

function cdInDefense(player) {
  return String(player.level.dimension) === CD.dimension
}

function cdItemText(item, count) {
  // KubeJS supplies these Java bindings and script globals at runtime.
  //noinspection JSUnresolvedVariable
  return Text.of(Item.of(item).hoverName).append(` x${count}`)
}

function cdHas(player, cost) {
  return Number(player.inventory.count(cost.item)) >= cost.count
}

function cdPay(server, player, cost) {
  server.runCommandSilent(`clear ${player.username} ${cost.item} ${cost.count}`)
}

// ---------- 스테이지 ----------

function cdRewardMultiplier() {
  if (cdState.lastClearUptime < 0) return 1
  let elapsed = cdState.uptime - cdState.lastClearUptime
  let bonus = CD.speedBonus.find(b => elapsed <= b.within)
  return bonus ? bonus.mult : 1
}

// 다음 스테이지 구성 (마지막 이후는 마지막 구성 반복 + 몹 수 증가)
function cdNextStage() {
  let index = cdState.stage
  let last = CD.stages.length - 1
  let base = CD.stages[Math.min(index, last)]
  let growth = 1 + Math.max(0, index - last) * CD.endlessGrowth
  let stage = {
    mobs: base.mobs.map(m => ({
      type: m.type, count: Math.max(1, Math.round(m.count * growth)), elite: !!m.elite, breaker: !!m.breaker
    })),
    rewards: base.rewards,
    maxAlive: base.maxAlive || CD.maxAlive,
    spawnInterval: base.spawnInterval || CD.spawnInterval
  }
  return { index: index, stage: stage, mult: cdRewardMultiplier() }
}

function cdSpawnRadius(index) {
  let match = CD.spawnRadius[0]
  CD.spawnRadius.forEach(r => { if (index >= r.fromStage) match = r })
  return { min: match.min, max: match.max }
}

function cdCollapsedSegments(server) {
  return cdWall(server).segments.filter(s => s.collapsed).map(s => String(s.id))
}

// 시작 불가 사유 (가능하면 빈 문자열). anywhere = 관리자 시작 (위치 제한 없음)
function cdStartReason(server, player, anywhere) {
  // KubeJS supplies these Java bindings and script globals at runtime.
  //noinspection JSUnresolvedVariable
  if (CoreDefenderWave.isActive()) return '이미 웨이브가 진행 중입니다'
  if (cdCollapsedSegments(server).length > 0) return '무너진 성벽 복구가 필요합니다'
  if (!anywhere && !cdInDefense(player)) return '디펜스 기지 안에서만 시작할 수 있습니다'
  return ''
}

function cdStartWave(server, player, anywhere) {
  let reason = cdStartReason(server, player, anywhere)
  if (reason) return player.tell(Text.red(reason))

  let next = cdNextStage()
  let label = `스테이지 ${next.index + 1}`
  let spec = {
    label: label,
    mobs: next.stage.mobs,
    spawnRadius: cdSpawnRadius(next.index),
    maxAlive: next.stage.maxAlive,
    spawnInterval: next.stage.spawnInterval
  }
  // KubeJS supplies these Java bindings and script globals at runtime.
  //noinspection JSUnresolvedVariable
  let error = String(CoreDefenderWave.start(server, JSON.stringify(spec)))
  if (error) return player.tell(Text.red(`웨이브를 시작할 수 없습니다: ${error}`))
  cdState.wave = { index: next.index, mult: next.mult }
  cdSave(server)
  // KubeJS supplies these Java bindings and script globals at runtime.
  //noinspection JSUnresolvedFunction
  server.tell(Text.gold(`[디펜스] ${label} 시작! 보상 x${next.mult}`))
}

// ---------- 성벽 관리 ----------

// 붕괴 구간 + 손상 HP 기준 수복 비용
function cdRepairCost(wall) {
  let collapsed = 0
  let missing = 0
  wall.segments.forEach(s => {
    if (s.collapsed) collapsed++
    else missing += s.maxHp - s.hp
  })
  let count = collapsed * CD.repair.perCollapsed + Math.ceil(missing / 100) * CD.repair.perHundredMissing
  return { item: CD.repair.item, count: count }
}

function cdCmdRepair(server, player) {
  // KubeJS supplies these Java bindings and script globals at runtime.
  //noinspection JSUnresolvedVariable
  if (CoreDefenderWave.isActive()) return player.tell(Text.red('웨이브 중에는 수복할 수 없습니다.'))
  let cost = cdRepairCost(cdWall(server))
  if (cost.count === 0) return player.tell(Text.yellow('수복할 성벽이 없습니다.'))
  if (!cdHas(player, cost)) return player.tell(Text.red('재료가 부족합니다: ').append(cdItemText(cost.item, cost.count)))
  cdPay(server, player, cost)
  // KubeJS supplies these Java bindings and script globals at runtime.
  //noinspection JSUnresolvedVariable
  CoreDefenderWall.repair(server, '', 0)
  server.tell(Text.green(`[디펜스] ${player.username} 님이 성벽을 수복했습니다.`))
}

function cdSideUpgradeCost(wall, side) {
  let level = wall.sides.find(s => s.side === side).level
  return CD.sideUpgrade[level] || null
}

function cdCmdUpgrade(server, player, side) {
  let cost = cdSideUpgradeCost(cdWall(server), side)
  if (!cost) return player.tell(Text.yellow('더 이상 강화할 수 없습니다.'))
  if (!cdHas(player, cost)) return player.tell(Text.red('재료가 부족합니다: ').append(cdItemText(cost.item, cost.count)))
  cdPay(server, player, cost)
  // KubeJS supplies these Java bindings and script globals at runtime.
  //noinspection JSUnresolvedVariable
  CoreDefenderWall.upgradeSide(server, side)
  server.tell(Text.green(`[디펜스] ${player.username} 님이 성벽 ${side}면을 강화했습니다.`))
}

function cdExpandCost(wall) {
  let half = wall.interiorChunks / 2
  if (half >= CD.expand.maxHalf) return null
  return { item: CD.expand.item, count: half }
}

function cdCmdExpand(server, player) {
  // KubeJS supplies these Java bindings and script globals at runtime.
  //noinspection JSUnresolvedVariable
  if (CoreDefenderWave.isActive()) return player.tell(Text.red('웨이브 중에는 확장할 수 없습니다.'))
  let cost = cdExpandCost(cdWall(server))
  if (!cost) return player.tell(Text.yellow('성벽이 최대 크기입니다.'))
  if (!cdHas(player, cost)) return player.tell(Text.red('재료가 부족합니다: ').append(cdItemText(cost.item, cost.count)))
  cdPay(server, player, cost)
  // KubeJS supplies these Java bindings and script globals at runtime.
  //noinspection JSUnresolvedVariable
  CoreDefenderWall.expand(server)
  let wall = cdWall(server)
  server.tell(Text.green(`[디펜스] ${player.username} 님이 성벽을 ${wall.interiorChunks}x${wall.interiorChunks} 청크로 확장했습니다.`))
}

// ---------- 입장 / 퇴장 ----------

function cdCmdEnter(server, player) {
  if (!cdInDefense(player)) {
    player.persistentData.putString('cd_return', JSON.stringify({
      dim: String(player.level.dimension), x: player.x, y: player.y, z: player.z
    }))
  }
  let wall = cdWall(server)
  // CoreDefenderWall.info returns these core coordinates in its JSON object.
  //noinspection JSUnresolvedVariable
  player.teleportTo(CD.dimension, wall.coreX + 0.5, wall.coreY, wall.coreZ + 3.5, 180, 0)
  // KubeJS supplies these Java bindings and script globals at runtime.
  //noinspection JSUnresolvedFunction
  player.tell(Text.gold('디펜스 기지에 입장했습니다. 코어 우클릭 또는 메뉴(K)로 관리하세요.'))
}

function cdCmdLeave(server, player) {
  if (!cdInDefense(player)) return player.tell(Text.red('디펜스 기지에 있지 않습니다.'))
  let raw = player.persistentData.getString('cd_return')
  if (raw) {
    let r = JSON.parse(raw)
    player.teleportTo(r.dim, r.x, r.y, r.z, player.yaw, player.pitch)
  } else {
    let spawn = server.overworld.sharedSpawnPos
    player.teleportTo('minecraft:overworld', spawn.x + 0.5, spawn.y, spawn.z + 0.5, 0, 0)
  }
}

function cdCmdStatus(server, player) {
  let wall = cdWall(server)
  let wave = cdWaveInfo()
  // KubeJS supplies these Java bindings and script globals at runtime.
  //noinspection JSUnresolvedFunction
  player.tell(Text.gray(wave.active ? `${wave.label} 진행 중 - 남은 적 ${wave.remaining}/${wave.total}`
    : `스테이지 ${cdState.stage + 1} 대기 중`))
  // KubeJS supplies these Java bindings and script globals at runtime.
  //noinspection JSUnresolvedFunction
  player.tell(Text.gray(`성벽 내부 ${wall.interiorChunks}x${wall.interiorChunks} 청크`))
  // KubeJS supplies these Java bindings and script globals at runtime.
  //noinspection JSUnresolvedFunction
  wall.segments.forEach(s => player.tell(Text.gray(`  ${s.id}: ${s.hp}/${s.maxHp}${s.collapsed ? ' [붕괴]' : ''}`)))
}

// ---------- 이벤트 ----------

// KubeJS supplies these Java bindings and script globals at runtime.
//noinspection JSUnresolvedVariable
ServerEvents.loaded(event => cdInit(event.server))

// KubeJS supplies these Java bindings and script globals at runtime.
//noinspection JSUnresolvedVariable
ServerEvents.unloaded(event => {
  if (cdState) cdSave(event.server)
})

// KubeJS supplies these Java bindings and script globals at runtime.
//noinspection JSUnresolvedVariable
ServerEvents.tick(event => {
  let server = event.server
  if (!cdState) cdInit(server)
  // KubeJS supplies these Java bindings and script globals at runtime.
  //noinspection JSUnresolvedVariable
  if (server.tickCount % 20 !== 0) return

  cdState.uptime++
  if (cdState.uptime % 30 === 0) cdSave(server)
})

// KubeJS supplies these Java bindings and script globals at runtime.
//noinspection JSUnresolvedVariable
CoreDefenderEvents.waveCleared(event => {
  let server = event.server
  let wave = cdState.wave || { index: cdState.stage, mult: 1 }
  let stage = cdNextStage().stage
  // KubeJS supplies these Java bindings and script globals at runtime.
  //noinspection JSUnresolvedVariable
  server.players.forEach(p => {
    // KubeJS supplies these Java bindings and script globals at runtime.
    //noinspection JSUnresolvedVariable
    stage.rewards.forEach(r => p.give(Item.of(r.item, Math.max(1, Math.floor(r.count * wave.mult)))))
  })
  cdState.stage = wave.index + 1
  cdState.lastClearUptime = cdState.uptime
  cdState.wave = null
  cdSave(server)
  server.tell(Text.green(`[디펜스] ${event.label} 클리어! 보상 x${wave.mult} 지급 (접속 중인 모든 플레이어)`))
})

// KubeJS supplies these Java bindings and script globals at runtime.
//noinspection JSUnresolvedVariable
CoreDefenderEvents.waveFailed(event => {
  let server = event.server
  cdState.wave = null
  cdSave(server)
  server.tell(Text.red(`[디펜스] ${event.label} 실패 - 성벽이 무너졌습니다. 코어 메뉴에서 수복 후 다시 도전하세요.`))
})

// ---------- 명령어 ----------
// 관리자 성벽/이동 명령(/cd admin wall ..., /cd admin tp)은 coredefender 모드가 같은 /cd 트리에 등록한다.

function cdAdminSetStage(server, stage) {
  cdState.stage = Math.max(0, Number(stage) - 1)
  cdSave(server)
  server.tell(Text.yellow(`[디펜스] 다음 스테이지가 ${cdState.stage + 1}(으)로 설정되었습니다.`))
}

// 기본 초기화는 진행도 + 성벽. 시설/창고 삭제는 명시적으로 선택한다.
function cdAdminReset(server, player, scope) {
  // KubeJS supplies these Java bindings and script globals at runtime.
  //noinspection JSUnresolvedVariable
  CoreDefenderWave.abort(server)
  // KubeJS supplies these Java bindings and script globals at runtime.
  //noinspection JSUnresolvedVariable
  if (!CoreDefenderWall.reset(server, scope)) {
    player.tell(Text.red('디펜스 차원을 찾을 수 없어 초기화하지 못했습니다.'))
    cdState.wave = null
    cdSave(server)
    return
  }
  if (scope === 'default' || scope === 'all' || scope === 'progress') {
    server.persistentData.remove(CD_KEY)
    cdLoad(server)
  }
  cdState.wave = null
  cdSave(server)
  const labels = {
    default: '진행도와 성벽 전체 (시설/창고 보존)',
    all: '진행도, 성벽, 지상 시설, 코어 창고, 공용 보급 버퍼',
    progress: '스테이지와 보상 타이머', wall: '성벽 크기/강화/HP',
    size: '성벽 크기와 HP (강화 유지)', levels: '성벽 강화와 HP (크기 유지)',
    hp: '성벽 HP/붕괴', facilities: '현재 기지와 킬존의 지상 시설 및 내부 아이템',
    storage: '코어 창고', supply: '공용 보급 버퍼'
  }
  server.tell(Text.yellow(`[디펜스] 초기화: ${labels[scope]}`))
}

function cdResetCommand(Commands, run) {
  let command = Commands.literal('reset').executes(run((server, player) => cdAdminReset(server, player, 'default')))
  const scopes = ['all', 'progress', 'wall', 'size', 'levels', 'hp', 'facilities', 'storage', 'supply']
  scopes.forEach(scope => {
    command.then(Commands.literal(scope).executes(run((server, player) => cdAdminReset(server, player, scope))))
  })
  return command
}

// KubeJS supplies these Java bindings and script globals at runtime.
//noinspection JSUnresolvedVariable
ServerEvents.commandRegistry(event => {
  const { commands: Commands, arguments: Arguments } = event
  const run = fn => ctx => {
    let server = ctx.source.server
    if (!cdState) cdInit(server)
    fn(server, ctx.source.playerOrException, ctx)
    return 1
  }

  event.register(Commands.literal('cd')
    .then(Commands.literal('enter').executes(run((server, player) => cdCmdEnter(server, player))))
    .then(Commands.literal('leave').executes(run((server, player) => cdCmdLeave(server, player))))
    .then(Commands.literal('menu').executes(run((server, player) => cdOpenMenu(player))))
    .then(Commands.literal('start').executes(run((server, player) => cdStartWave(server, player, false))))
    .then(Commands.literal('repair').executes(run((server, player) => cdCmdRepair(server, player))))
    .then(Commands.literal('status').executes(run((server, player) => cdCmdStatus(server, player))))
    .then(Commands.literal('admin')
      .requires(src => src.hasPermission(2))
      // 다음에 시작할 스테이지 지정 (1부터)
      .then(Commands.literal('stage')
        .then(Commands.argument('stage', Arguments.INTEGER.create(event))
          .executes(run((server, player, ctx) => cdAdminSetStage(server, Arguments.INTEGER.getResult(ctx, 'stage'))))))
      // 즉시 시작 (위치 제한 없음). 스테이지를 주면 그 스테이지로
      .then(Commands.literal('start')
        .executes(run((server, player) => cdStartWave(server, player, true)))
        .then(Commands.argument('stage', Arguments.INTEGER.create(event))
          .executes(run((server, player, ctx) => {
            cdAdminSetStage(server, Arguments.INTEGER.getResult(ctx, 'stage'))
            cdStartWave(server, player, true)
          }))))
      .then(Commands.literal('abort').executes(run(server => {
        // KubeJS supplies these Java bindings and script globals at runtime.
        //noinspection JSUnresolvedVariable
        CoreDefenderWave.abort(server)
        cdState.wave = null
        cdSave(server)
        server.tell(Text.yellow('[디펜스] 웨이브가 관리자에 의해 중단되었습니다.'))
      })))
      .then(cdResetCommand(Commands, run))))
})
