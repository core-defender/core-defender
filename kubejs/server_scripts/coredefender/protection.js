// v2 프로토타입 차원(world_1) 건축/파괴 금지. 크리에이티브 플레이어(맵 제작용)는 예외.
// v3 디펜스 차원(coredefender:defense)의 건설 규칙(성벽 안 자유/밖 금지)은 coredefender 모드가 처리한다.

function cdIsDefenseLevel(level) {
  return String(level.dimension) == 'coredefender:world_1'
}

function cdBlocksEdit(player) {
  return player && player.player && !player.isCreative()
}

BlockEvents.placed(event => {
  if (cdIsDefenseLevel(event.level) && cdBlocksEdit(event.entity)) event.cancel()
})

BlockEvents.broken(event => {
  if (cdIsDefenseLevel(event.level) && cdBlocksEdit(event.player)) event.cancel()
})

// 양동이로 유체를 놓는 것은 블록 설치 이벤트가 발생하지 않으므로 별도 차단
ItemEvents.rightClicked(event => {
  let id = String(event.item.id)
  if (!id.endsWith('_bucket') || id == 'minecraft:milk_bucket') return
  if (cdIsDefenseLevel(event.level) && cdBlocksEdit(event.player)) event.cancel()
})

// 폭발로 인한 지형 파괴 방지 (피해는 그대로)
LevelEvents.afterExplosion(event => {
  if (cdIsDefenseLevel(event.level)) event.removeAllAffectedBlocks()
})
