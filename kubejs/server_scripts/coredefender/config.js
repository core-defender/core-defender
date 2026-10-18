// priority: 100
// 디펜스 월드(스테이지) 정의. 월드를 추가할 때는 dimension json 과 여기 항목만 추가하면 된다.
//
// waves[].mobs: { type, count, elite? }  elite = 체력/공격력 강화 + 이름표
// waves[].rewards: 기본 보상 (속도 배율, 반복 도전 배율이 곱해짐)

const CD_CONFIG = {
  // 직전 클리어 후 다음 웨이브 시작까지의 서버 가동 시간(초) → 보상 배율
  speedBonus: [
    { within: 30 * 60, mult: 1.5 },
    { within: 120 * 60, mult: 1.2 }
  ],
  replayRewardMult: 0.5,
  spawnInterval: 20,   // 몹 소환 간격(tick)
  retargetInterval: 10,
  playerAggroRange: 8, // 이 거리 밖의 플레이어는 무시하고 코어로 향함

  worlds: [
    {
      id: 'world_1',
      dimension: 'coredefender:world_1',
      name: '제1 월드: 초원 전초기지',
      tier: 'create',
      playerSpawn: [0.5, 5, 8.5],
      core: { pos: [0.5, 5, 0.5], hp: 500 },
      spawnPoints: [[40.5, 5, 0.5], [-39.5, 5, 0.5]],
      arena: [-64, -64, 63, 63], // forceload 범위 (x1 z1 x2 z2)
      repairCost: { item: 'create:andesite_alloy', count: 32 },
      waves: [
        { mobs: [{ type: 'minecraft:zombie', count: 6 }],
          rewards: [{ item: 'minecraft:iron_ingot', count: 4 }] },
        { mobs: [{ type: 'minecraft:zombie', count: 10 }],
          rewards: [{ item: 'minecraft:iron_ingot', count: 6 }] },
        { mobs: [{ type: 'minecraft:zombie', count: 8 }, { type: 'minecraft:skeleton', count: 4 }],
          rewards: [{ item: 'minecraft:iron_ingot', count: 8 }, { item: 'minecraft:copper_ingot', count: 8 }] },
        { mobs: [{ type: 'minecraft:zombie', count: 12 }, { type: 'minecraft:skeleton', count: 6 }, { type: 'minecraft:husk', count: 4 }],
          rewards: [{ item: 'minecraft:iron_ingot', count: 12 }, { item: 'minecraft:gold_ingot', count: 4 }] },
        { mobs: [{ type: 'minecraft:zombie', count: 15 }, { type: 'minecraft:skeleton', count: 8 }, { type: 'minecraft:zombie', count: 1, elite: true }],
          rewards: [{ item: 'minecraft:diamond', count: 2 }, { item: 'minecraft:gold_ingot', count: 8 }] }
      ]
    }
  ]
}
