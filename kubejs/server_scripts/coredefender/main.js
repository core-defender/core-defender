// priority: 50
// 디펜스 월드 진행: 상태 저장, 코어 관리, 웨이브 진행, 명령어.
// 상태는 server.persistentData 에 JSON 문자열로 저장한다.

const CD = CD_CONFIG
const CD_KEY = 'coredefender'
const CD_BOSSBAR = 'coredefender:core'
const CD_MOB_TAG = 'cd_wave'
const CD_CORE_TAG = 'cd_core'
const CD_EQUIPMENT = {
  'minecraft:skeleton': 'minecraft:bow',
  'minecraft:stray': 'minecraft:bow'
}

let cdState = null
// 저장하지 않는 런타임 정보 (재시작 시 진행 중 웨이브는 취소됨)
let cdRuntime = { queue: [], mobs: [], spawnTimer: 0, spawnIndex: 0, core: null, tick: 0 }


// ---------- 상태 ----------

function cdLoad(server) {
  let raw = server.persistentData.getString(CD_KEY)
  cdState = raw ? JSON.parse(raw) : { worlds: {}, uptime: 0, lastClearUptime: -1, active: null }
  CD.worlds.forEach((cfg, i) => {
    if (!cdState.worlds[cfg.id]) {
      cdState.worlds[cfg.id] = {
        unlocked: i == 0, cleared: false, waveIndex: 0, replayIndex: 0, coreBroken: false, coreId: null
      }
    }
  })
}

function cdSave(server) {
  server.persistentData.putString(CD_KEY, JSON.stringify(cdState))
}

function cdWorldCfg(id) {
  return CD.worlds.find(w => w.id == id)
}

function cdWorldCfgByDimension(dimension) {
  let dim = String(dimension)
  return CD.worlds.find(w => w.dimension == dim)
}

function cdInDimension(server, cfg, command) {
  server.runCommandSilent(`execute in ${cfg.dimension} run ${command}`)
}

// ---------- 코어 ----------

function cdFindCore(server, cfg) {
  let ws = cdState.worlds[cfg.id]
  if (ws.coreId) {
    let core = server.getEntityByUUID(UUID.fromString(ws.coreId))
    if (core && core.alive) return core
  }
  let level = server.getLevel(cfg.dimension)
  let found = level.entities.filter(e => e.tags.contains(CD_CORE_TAG) && e.alive)
  if (found.isEmpty()) return null
  // 중복 소환된 코어 정리
  for (let i = 1; i < found.size(); i++) found.get(i).discard()
  let core = found.get(0)
  ws.coreId = String(core.uuid)
  return core
}

function cdSpawnCore(server, cfg) {
  let level = server.getLevel(cfg.dimension)
  let p = cfg.core.pos
  let core = level.createEntity('coredefender:core')
  core.setPosition(p[0], p[1], p[2])
  core.addTag(CD_CORE_TAG)
  core.spawn()
  core.setAttributeBaseValue('minecraft:generic.knockback_resistance', 1)
  core.setMaxHealth(cfg.core.hp)
  core.health = cfg.core.hp
  cdState.worlds[cfg.id].coreId = String(core.uuid)
  cdSave(server)
  return core
}

function cdEnsureCore(server, cfg) {
  if (cdState.worlds[cfg.id].coreBroken) return null
  return cdFindCore(server, cfg) || cdSpawnCore(server, cfg)
}

// ---------- 보스바 ----------

function cdBossbar(server, cfg, core) {
  if (!cfg || !core) {
    server.runCommandSilent(`bossbar set ${CD_BOSSBAR} visible false`)
    return
  }
  server.runCommandSilent(`bossbar set ${CD_BOSSBAR} max ${Math.ceil(core.maxHealth)}`)
  server.runCommandSilent(`bossbar set ${CD_BOSSBAR} value ${Math.ceil(core.health)}`)
  cdInDimension(server, cfg, `bossbar set ${CD_BOSSBAR} players @a[distance=0..]`)
  server.runCommandSilent(`bossbar set ${CD_BOSSBAR} visible true`)
}

// ---------- 표시용 텍스트 ----------

// 아이템 이름은 클라이언트 언어로 번역되도록 번역 키 컴포넌트를 그대로 사용한다
function cdItemText(item, count) {
  return Text.of(Item.of(item).hoverName).append(` x${count}`)
}

function cdEntityText(type) {
  let [ns, path] = String(type).split(':')
  return Text.translate(`entity.${ns}.${path}`)
}

// 지정이 없을 때 기본 월드: 해금된 월드 중 가장 앞선 미클리어 월드, 없으면 마지막 해금 월드
function cdDefaultWorld() {
  return CD.worlds.find(w => cdState.worlds[w.id].unlocked && !cdState.worlds[w.id].cleared)
    || CD.worlds.filter(w => cdState.worlds[w.id].unlocked).pop()
}

// 다음에 시작될 웨이브 정보
function cdNextWave(cfg) {
  let ws = cdState.worlds[cfg.id]
  let replay = ws.cleared
  let index = replay ? ws.replayIndex : ws.waveIndex
  return { index: index, replay: replay, wave: cfg.waves[index], mult: cdRewardMultiplier(replay) }
}

// ---------- 웨이브 ----------

function cdRewardMultiplier(replay) {
  if (replay) return CD.replayRewardMult
  if (cdState.lastClearUptime < 0) return 1
  let elapsed = cdState.uptime - cdState.lastClearUptime
  let bonus = CD.speedBonus.find(b => elapsed <= b.within)
  return bonus ? bonus.mult : 1
}

function cdStartWave(server, player) {
  let cfg = cdWorldCfgByDimension(player.level.dimension)
  if (!cfg) return player.tell(Text.red('디펜스 월드 안에서만 웨이브를 시작할 수 있습니다.'))
  if (cdState.active) return player.tell(Text.red('이미 웨이브가 진행 중입니다.'))
  let ws = cdState.worlds[cfg.id]
  if (ws.coreBroken) {
    return player.tell(Text.red('코어가 파괴된 상태입니다. 복구가 필요합니다. (필요: ')
      .append(cdItemText(cfg.repairCost.item, cfg.repairCost.count)).append(')'))
  }
  let core = cdEnsureCore(server, cfg)
  if (!core) return player.tell(Text.red('코어를 찾을 수 없습니다.'))

  let next = cdNextWave(cfg)
  let replay = next.replay
  let waveIndex = next.index
  let wave = next.wave
  let mult = next.mult

  cdState.active = { world: cfg.id, wave: waveIndex, replay: replay, mult: mult }
  cdRuntime = { queue: [], mobs: [], spawnTimer: 40, spawnIndex: 0, core: core, tick: 0 }
  wave.mobs.forEach(group => {
    for (let i = 0; i < group.count; i++) cdRuntime.queue.push({ type: group.type, elite: !!group.elite })
  })

  let a = cfg.arena
  cdInDimension(server, cfg, `forceload add ${a[0]} ${a[1]} ${a[2]} ${a[3]}`)
  core.health = core.maxHealth
  cdSave(server)

  let label = replay ? `반복 도전 웨이브 ${waveIndex + 1}` : `웨이브 ${waveIndex + 1}/${cfg.waves.length}`
  server.tell(Text.gold(`[${cfg.name}] ${label} 시작! (보상 x${mult})`))
}

function cdSpawnNext(server, cfg) {
  let entry = cdRuntime.queue.shift()
  let level = server.getLevel(cfg.dimension)
  let p = cfg.spawnPoints[cdRuntime.spawnIndex++ % cfg.spawnPoints.length]
  let mob = level.createEntity(entry.type)
  mob.setPosition(p[0], p[1], p[2])
  mob.addTag(CD_MOB_TAG)
  mob.setPersistenceRequired()
  if (CD_EQUIPMENT[entry.type]) mob.mainHandItem = Item.of(CD_EQUIPMENT[entry.type])
  mob.spawn()
  mob.setAttributeBaseValue('minecraft:generic.follow_range', 64)
  if (entry.elite) {
    mob.setMaxHealth(80)
    mob.health = 80
    mob.setAttributeBaseValue('minecraft:generic.attack_damage', 8)
    mob.customName = Text.red('정예')
    mob.customNameVisible = true
  }
  mob.setTarget(cdRuntime.core)
  cdRuntime.mobs.push(mob)
}

function cdRetarget(core) {
  cdRuntime.mobs = cdRuntime.mobs.filter(m => m.alive)
  cdRuntime.mobs.forEach(mob => {
    let t = mob.target
    if (t && t.alive && t.tags.contains(CD_CORE_TAG)) return
    // 가까이 있거나 자신을 공격한 플레이어는 계속 상대한다
    if (t && t.alive && t.player) {
      let hurtBy = mob.lastHurtByMob
      if (mob.distanceTo(t) <= CD.playerAggroRange) return
      if (hurtBy && hurtBy.uuid.equals(t.uuid)) return
    }
    mob.setTarget(core)
  })
}

function cdEndWave(server, cfg) {
  let a = cfg.arena
  cdInDimension(server, cfg, `kill @e[tag=${CD_MOB_TAG}]`)
  cdInDimension(server, cfg, `forceload remove ${a[0]} ${a[1]} ${a[2]} ${a[3]}`)
  // 코어 청크는 항상 로드 유지
  cdInDimension(server, cfg, `forceload add ${Math.floor(cfg.core.pos[0])} ${Math.floor(cfg.core.pos[2])}`)
  cdState.active = null
  cdRuntime = { queue: [], mobs: [], spawnTimer: 0, spawnIndex: 0, core: null, tick: 0 }
  cdBossbar(server, null, null)
}

function cdClearWave(server, cfg) {
  let active = cdState.active
  let ws = cdState.worlds[cfg.id]
  let wave = cfg.waves[active.wave]
  let players = server.getLevel(cfg.dimension).players

  wave.rewards.forEach(r => {
    let count = Math.max(1, Math.floor(r.count * active.mult))
    players.forEach(p => p.give(Item.of(r.item, count)))
  })

  if (active.replay) {
    ws.replayIndex = (ws.replayIndex + 1) % cfg.waves.length
  } else {
    ws.waveIndex++
    if (ws.waveIndex >= cfg.waves.length) {
      ws.cleared = true
      let idx = CD.worlds.indexOf(cfg)
      let next = CD.worlds[idx + 1]
      if (next) {
        cdState.worlds[next.id].unlocked = true
        server.tell(Text.aqua(`[${cfg.name}] 모든 웨이브 클리어! ${next.name} 이(가) 해금되었습니다.`))
      } else {
        server.tell(Text.aqua(`[${cfg.name}] 모든 웨이브 클리어!`))
      }
    }
  }
  cdState.lastClearUptime = cdState.uptime
  server.tell(Text.green(`[${cfg.name}] 웨이브 클리어! 보상 x${active.mult} 지급`))

  let core = cdRuntime.core
  cdEndWave(server, cfg)
  if (core && core.alive) core.health = core.maxHealth
  cdSave(server)
}

function cdFailWave(server, cfg) {
  let ws = cdState.worlds[cfg.id]
  ws.coreBroken = true
  ws.coreId = null
  cdEndWave(server, cfg)
  cdSave(server)
  server.tell(Text.red(`[${cfg.name}] 코어가 파괴되었습니다! 웨이브 실패.`))
  server.tell(Text.red('복구 비용: ').append(cdItemText(cfg.repairCost.item, cfg.repairCost.count)))
}

function cdAbortWave(server, reason) {
  let cfg = cdWorldCfg(cdState.active.world)
  cdEndWave(server, cfg)
  cdSave(server)
  server.tell(Text.yellow(`[${cfg.name}] 웨이브가 취소되었습니다. (${reason})`))
}

function cdTickWave(server) {
  let cfg = cdWorldCfg(cdState.active.world)
  cdRuntime.tick++

  if (cdRuntime.queue.length > 0 && --cdRuntime.spawnTimer <= 0) {
    cdSpawnNext(server, cfg)
    cdRuntime.spawnTimer = CD.spawnInterval
  }

  if (cdRuntime.tick % CD.retargetInterval != 0) return

  let core = cdRuntime.core
  if (!core || !core.alive) return cdFailWave(server, cfg)

  cdRetarget(core)
  cdBossbar(server, cfg, core)
  if (cdRuntime.queue.length == 0 && cdRuntime.mobs.length == 0) cdClearWave(server, cfg)
}

// ---------- 이벤트 ----------

// 월드 로드 시, 또는 /reload 로 스크립트 변수가 초기화된 뒤 처음 필요할 때 호출된다
function cdInit(server, reason) {
  cdLoad(server)
  server.runCommandSilent(`bossbar add ${CD_BOSSBAR} "코어"`)
  server.runCommandSilent(`bossbar set ${CD_BOSSBAR} color red`)
  server.runCommandSilent(`bossbar set ${CD_BOSSBAR} visible false`)
  CD.worlds.forEach(cfg => {
    cdInDimension(server, cfg, `forceload add ${Math.floor(cfg.core.pos[0])} ${Math.floor(cfg.core.pos[2])}`)
  })
  // 런타임 정보가 없으므로 진행 중이던 웨이브는 페널티 없이 취소
  if (cdState.active) cdAbortWave(server, reason)
}

ServerEvents.loaded(event => cdInit(event.server, '서버 재시작'))

// 코어는 웨이브 진행 중 웨이브 몹에게만 피해를 받는다.
// 플레이어, 포탑(IE/CBC 등), 환경 피해는 모두 무시 → 출처가 cd_wave 태그 몹인지로만 판단
function cdIsWaveMob(entity) {
  return entity != null && entity.tags.contains(CD_MOB_TAG)
}

// 피해 처리 초입(무적시간/피격 연출 이전)에서 취소해야 빨간 깜빡임도 생기지 않는다.
// KubeJS 는 DamageSource.getEntity()/getDirectEntity() 를 actual/immediate 로 리매핑한다.
NativeEvents.onEvent(Java.loadClass('net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent'), event => {
  let victim = event.entity
  let attacker = event.source.actual

  // 웨이브 몹끼리는 서로 피해 없음 (오사격/폭발 → 내분 방지)
  if (cdIsWaveMob(victim) && cdIsWaveMob(attacker)) {
    event.setCanceled(true)
    return
  }

  if (victim.type != 'coredefender:core') return
  let core = victim
  let source = event.source
  let fromWaveMob = cdIsWaveMob(attacker)
  let blocked = !cdState || !cdState.active || !fromWaveMob
  // DEBUG: 코어 피해 판정 확인용 (확인 후 제거)
  console.info(`[CD-DEBUG] core hurt: dmg=${event.amount} type=${source.type} attacker=${attacker} immediate=${source.immediate} waveMob=${fromWaveMob} blocked=${blocked} hp=${core.health}/${core.maxHealth}`)
  if (blocked) event.setCanceled(true)
})

// 코어는 무적시간 없음 → 코어에 도달한 몹 수만큼 피해가 그대로 누적된다.
// afterHurt 는 바닐라가 invulnerableTime 을 설정한 뒤에 호출되므로 여기서 초기화한다.
EntityEvents.afterHurt('coredefender:core', event => {
  event.entity.invulnerableTime = 0
})

// 웨이브 몹이 쏜 투사체는 다른 웨이브 몹을 통과한다 (충돌 취소 → 계속 날아감)
NativeEvents.onEvent(Java.loadClass('net.neoforged.neoforge.event.entity.ProjectileImpactEvent'), event => {
  let result = event.rayTraceResult
  if (String(result.type) != 'ENTITY') return
  if (cdIsWaveMob(event.projectile.owner) && cdIsWaveMob(result.entity)) event.setCanceled(true)
})

ServerEvents.unloaded(event => {
  if (cdState) cdSave(event.server)
})

ServerEvents.tick(event => {
  let server = event.server
  if (!cdState) cdInit(server, '스크립트 리로드')
  if (server.tickCount % 20 == 0) {
    cdState.uptime++
    if (cdState.uptime % 30 == 0) cdSave(server)
  }
  if (cdState.active) cdTickWave(server)
})

// ---------- 명령어 ----------

function cdCmdEnter(server, player, worldId) {
  let cfg = worldId ? cdWorldCfg(worldId) : null
  if (!cfg && !worldId) cfg = cdDefaultWorld()
  if (!cfg) return player.tell(Text.red('존재하지 않는 월드입니다.'))
  if (!cdState.worlds[cfg.id].unlocked) return player.tell(Text.red('아직 해금되지 않은 월드입니다.'))

  if (!cdWorldCfgByDimension(player.level.dimension)) {
    player.persistentData.putString('cd_return', JSON.stringify({
      dim: String(player.level.dimension), x: player.x, y: player.y, z: player.z
    }))
  }
  let s = cfg.playerSpawn
  player.teleportTo(cfg.dimension, s[0], s[1], s[2], 180, 0)
  player.tell(Text.gold(`${cfg.name} 에 입장했습니다. 메뉴(K)에서 웨이브를 시작하세요.`))
  server.scheduleInTicks(20, () => cdEnsureCore(server, cfg))
}

function cdCmdLeave(server, player) {
  if (!cdWorldCfgByDimension(player.level.dimension)) return player.tell(Text.red('디펜스 월드에 있지 않습니다.'))
  let raw = player.persistentData.getString('cd_return')
  if (raw) {
    let r = JSON.parse(raw)
    player.teleportTo(r.dim, r.x, r.y, r.z, player.yaw, player.pitch)
  } else {
    let spawn = server.overworld.sharedSpawnPos
    player.teleportTo('minecraft:overworld', spawn.x + 0.5, spawn.y, spawn.z + 0.5, 0, 0)
  }
}

function cdCmdRepair(server, player) {
  let cfg = cdWorldCfgByDimension(player.level.dimension)
  if (!cfg) return player.tell(Text.red('디펜스 월드 안에서만 코어를 복구할 수 있습니다.'))
  let ws = cdState.worlds[cfg.id]
  if (!ws.coreBroken) return player.tell(Text.yellow('코어가 파괴된 상태가 아닙니다.'))
  let cost = cfg.repairCost
  if (player.inventory.count(cost.item) < cost.count) {
    return player.tell(Text.red('재료가 부족합니다: ').append(cdItemText(cost.item, cost.count)))
  }
  server.runCommandSilent(`clear ${player.username} ${cost.item} ${cost.count}`)
  ws.coreBroken = false
  cdSpawnCore(server, cfg)
  server.tell(Text.green(`[${cfg.name}] ${player.username} 님이 코어를 복구했습니다.`))
}

function cdCmdStatus(player) {
  CD.worlds.forEach(cfg => {
    let ws = cdState.worlds[cfg.id]
    let progress = ws.cleared ? '클리어 (반복 도전 가능)' : `웨이브 ${ws.waveIndex}/${cfg.waves.length}`
    let flags = (ws.unlocked ? '' : ' [잠김]') + (ws.coreBroken ? ' [코어 파괴됨]' : '')
    player.tell(Text.gray(`${cfg.name}: ${progress}${flags}`))
  })
  if (cdState.lastClearUptime >= 0) {
    let mins = Math.floor((cdState.uptime - cdState.lastClearUptime) / 60)
    player.tell(Text.gray(`마지막 클리어 후 ${mins}분 경과 → 다음 보상 x${cdRewardMultiplier(false)}`))
  }
}

// 진행 중인 웨이브를 페널티/보상 없이 중단
function cdCmdStop(server, player) {
  if (!cdState.active) return player.tell(Text.yellow('진행 중인 웨이브가 없습니다.'))
  cdAbortWave(server, `${player.username} 님이 중단`)
}

// 진행도 초기화. worldId 가 없으면 전체 초기화.
// 해당 월드의 코어/웨이브 몹도 제거하고, 코어는 다음 입장 시 새로 소환된다.
function cdCmdReset(server, worldId) {
  let targets = worldId ? [cdWorldCfg(worldId)] : CD.worlds
  if (!targets[0]) return server.tell(Text.red(`존재하지 않는 월드입니다: ${worldId}`))

  if (cdState.active && targets.some(cfg => cfg.id == cdState.active.world)) cdAbortWave(server, '초기화')
  targets.forEach(cfg => {
    cdInDimension(server, cfg, `kill @e[tag=${CD_CORE_TAG}]`)
    cdInDimension(server, cfg, `kill @e[tag=${CD_MOB_TAG}]`)
  })

  if (worldId) {
    let idx = CD.worlds.indexOf(targets[0])
    let prev = CD.worlds[idx - 1]
    cdState.worlds[worldId] = {
      unlocked: idx == 0 || cdState.worlds[prev.id].cleared,
      cleared: false, waveIndex: 0, replayIndex: 0, coreBroken: false, coreId: null
    }
  } else {
    server.persistentData.remove(CD_KEY)
    cdLoad(server)
  }
  cdSave(server)
  server.tell(Text.yellow(worldId ? `[${targets[0].name}] 진행도가 초기화되었습니다.` : 'Core Defender 전체 진행도가 초기화되었습니다.'))
}

ServerEvents.commandRegistry(event => {
  const { commands: Commands, arguments: Arguments } = event
  const run = fn => ctx => {
    let server = ctx.source.server
    if (!cdState) cdInit(server, '스크립트 리로드')
    fn(server, ctx.source.playerOrException, ctx)
    return 1
  }

  event.register(Commands.literal('cd')
    .then(Commands.literal('enter')
      .executes(run((server, player) => cdCmdEnter(server, player, null)))
      .then(Commands.argument('world', Arguments.WORD.create(event))
        .executes(run((server, player, ctx) => cdCmdEnter(server, player, Arguments.WORD.getResult(ctx, 'world'))))))
    .then(Commands.literal('menu').executes(run((server, player) => cdOpenMenu(player))))
    .then(Commands.literal('leave').executes(run((server, player) => cdCmdLeave(server, player))))
    .then(Commands.literal('start').executes(run((server, player) => cdStartWave(server, player))))
    .then(Commands.literal('repair').executes(run((server, player) => cdCmdRepair(server, player))))
    .then(Commands.literal('status').executes(run((server, player) => cdCmdStatus(player))))
    .then(Commands.literal('admin')
      .requires(src => src.hasPermission(2))
      .then(Commands.literal('abort').executes(run((server, player) => cdCmdStop(server, player))))
      .then(Commands.literal('win').executes(run((server, player) => {
        if (!cdState.active) return player.tell(Text.yellow('진행 중인 웨이브가 없습니다.'))
        cdClearWave(server, cdWorldCfg(cdState.active.world))
      })))
      .then(Commands.literal('reset')
        .executes(run(server => cdCmdReset(server, null)))
        .then(Commands.argument('world', Arguments.WORD.create(event))
          .executes(run((server, player, ctx) => cdCmdReset(server, Arguments.WORD.getResult(ctx, 'world'))))))))
})
