// priority: 100
// 디펜스 진행 설정. 성벽/코어/몹 AI 는 coredefender 모드가 처리하고, 여기서는 스테이지 구성과 수치/비용을 정한다.
//
// stages[].mobs: { type, count, elite?, breaker? }
//   elite   = 체력/공격력 강화 + 이름표
//   breaker = 킬존 바리케이드를 우선 공격
// 주의: 거미는 성벽을 타고 넘어오므로 넣지 않는다.

const CD_CONFIG = {
  dimension: 'coredefender:defense',

  // 직전 클리어 후 다음 웨이브 시작까지의 서버 가동 시간(초) → 보상 배율
  speedBonus: [
    { within: 30 * 60, mult: 1.5 },
    { within: 120 * 60, mult: 1.2 }
  ],

  // 성벽 수복 비용: 붕괴 구간당 + 손상 HP 100 당
  repair: {
    item: 'create:andesite_alloy',
    perCollapsed: 32,
    perHundredMissing: 2
  },

  // 변별 강화 비용 (강화 단계 → 비용). 목록을 넘어서면 더 강화할 수 없다.
  sideUpgrade: [
    { item: 'minecraft:iron_ingot', count: 32 },
    { item: 'create:brass_ingot', count: 32 },
    { item: 'immersiveengineering:ingot_steel', count: 32 },
    { item: 'mekanism:ingot_refined_obsidian', count: 16 }
  ],

  // 성벽 확장: 현재 내부 크기(청크/2) 만큼 설계도 소모. maxHalf = 최대 내부 크기(청크/2)
  expand: { item: 'coredefender:wall_expansion_plan', maxHalf: 4 },

  // 스폰 반경 (성벽 바깥면 기준): fromStage(0부터) 이상에서 적용, 마지막으로 일치하는 항목 사용
  spawnRadius: [
    { fromStage: 0, min: 16, max: 32 },
    { fromStage: 5, min: 24, max: 40 },
    { fromStage: 10, min: 32, max: 48 },
    { fromStage: 15, min: 40, max: 56 },
    { fromStage: 20, min: 48, max: 64 }
  ],
  maxAlive: 60,
  spawnInterval: 10,

  // 마지막 스테이지 이후는 마지막 스테이지 구성을 반복하며 몹 수가 스테이지당 15% 증가한다
  endlessGrowth: 0.15,

  stages: [
    { mobs: [{ type: 'minecraft:zombie', count: 6 }],
      rewards: [{ item: 'minecraft:iron_ingot', count: 4 }] },
    { mobs: [{ type: 'minecraft:zombie', count: 10 }],
      rewards: [{ item: 'minecraft:iron_ingot', count: 6 }] },
    { mobs: [{ type: 'minecraft:zombie', count: 8 }, { type: 'minecraft:skeleton', count: 4 }],
      rewards: [{ item: 'minecraft:iron_ingot', count: 8 }, { item: 'minecraft:copper_ingot', count: 8 }] },
    { mobs: [{ type: 'minecraft:zombie', count: 12 }, { type: 'minecraft:skeleton', count: 6 }, { type: 'minecraft:vindicator', count: 2, breaker: true }],
      rewards: [{ item: 'minecraft:iron_ingot', count: 12 }, { item: 'minecraft:gold_ingot', count: 4 }] },
    { mobs: [{ type: 'minecraft:zombie', count: 15 }, { type: 'minecraft:skeleton', count: 8 }, { type: 'minecraft:creeper', count: 3 },
             { type: 'minecraft:vindicator', count: 3, breaker: true }, { type: 'minecraft:zombie', count: 1, elite: true }],
      rewards: [{ item: 'minecraft:diamond', count: 2 }, { item: 'minecraft:gold_ingot', count: 8 }] }
  ]
}
