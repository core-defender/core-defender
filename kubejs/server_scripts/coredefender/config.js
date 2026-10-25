// priority: 100
// First tuning pass for 4 -> 8 pre-Create turrets, with player support and no turret knockback.
// Enemy count, delivery rate and rewards are intentionally kept together per stage.
const CD_CONFIG = {
  dimension: 'coredefender:defense',
  speedBonus: [{ within: 30 * 60, mult: 1.5 }, { within: 120 * 60, mult: 1.2 }],
  // Pre-Create repairs must not require Create alloy. Ordinary iron is also an early wave reward.
  repair: { item: 'minecraft:iron_ingot', perCollapsed: 8, perHundredMissing: 1 },
  sideUpgrade: [
    { item: 'minecraft:iron_ingot', count: 32 }, { item: 'create:brass_ingot', count: 32 },
    { item: 'immersiveengineering:ingot_steel', count: 32 }, { item: 'mekanism:ingot_refined_obsidian', count: 16 }
  ],
  expand: { item: 'coredefender:wall_expansion_plan', maxHalf: 4 },
  // Uniform annulus around the base's circumscribed circle; preserve the surrounding swarm.
  spawnRadius: [
    { fromStage: 0, min: 12, max: 24 }, { fromStage: 5, min: 16, max: 28 },
    { fromStage: 10, min: 20, max: 36 }, { fromStage: 15, min: 24, max: 40 }, { fromStage: 20, min: 32, max: 48 }
  ],
  maxAlive: 60,
  spawnInterval: 12,
  endlessGrowth: 0.12,
  stages: [
    { spawnInterval: 20, maxAlive: 16, mobs: [{ type: 'minecraft:zombie', count: 12 }],
      rewards: [{ item: 'minecraft:iron_ingot', count: 6 }, { item: 'minecraft:copper_ingot', count: 6 }, { item: 'minecraft:arrow', count: 64 }] },
    { spawnInterval: 18, maxAlive: 20, mobs: [{ type: 'minecraft:zombie', count: 18 }],
      rewards: [{ item: 'minecraft:iron_ingot', count: 8 }, { item: 'minecraft:redstone', count: 8 }, { item: 'minecraft:arrow', count: 64 }, { item: 'minecraft:coal', count: 8 }] },
    { spawnInterval: 16, maxAlive: 24, mobs: [{ type: 'minecraft:zombie', count: 20 }, { type: 'minecraft:husk', count: 4 }],
      rewards: [{ item: 'minecraft:iron_ingot', count: 10 }, { item: 'minecraft:copper_ingot', count: 10 }, { item: 'minecraft:wheat_seeds', count: 20 }, { item: 'minecraft:flint', count: 8 }] },
    { spawnInterval: 15, maxAlive: 28, mobs: [{ type: 'minecraft:zombie', count: 24 }, { type: 'minecraft:husk', count: 4 }, { type: 'minecraft:skeleton', count: 4 }],
      rewards: [{ item: 'minecraft:iron_ingot', count: 12 }, { item: 'minecraft:arrow', count: 96 }, { item: 'minecraft:redstone', count: 12 }, { item: 'minecraft:slime_ball', count: 2 }] },
    { spawnInterval: 14, maxAlive: 32, mobs: [{ type: 'minecraft:zombie', count: 28 }, { type: 'minecraft:husk', count: 6 }, { type: 'minecraft:skeleton', count: 6 }],
      rewards: [{ item: 'minecraft:iron_ingot', count: 14 }, { item: 'minecraft:copper_ingot', count: 12 }, { item: 'minecraft:coal', count: 12 }, { item: 'minecraft:gunpowder', count: 8 }] },
    { spawnInterval: 13, maxAlive: 36, mobs: [{ type: 'minecraft:zombie', count: 32 }, { type: 'minecraft:husk', count: 8 }, { type: 'minecraft:skeleton', count: 8 }],
      rewards: [{ item: 'minecraft:iron_ingot', count: 16 }, { item: 'minecraft:arrow', count: 128 }, { item: 'minecraft:wheat_seeds', count: 30 }, { item: 'minecraft:gold_ingot', count: 4 }] },
    { spawnInterval: 12, maxAlive: 40, mobs: [{ type: 'minecraft:zombie', count: 36 }, { type: 'minecraft:husk', count: 10 }, { type: 'minecraft:skeleton', count: 10 }, { type: 'minecraft:zombie', count: 2, elite: true }],
      rewards: [{ item: 'minecraft:iron_ingot', count: 18 }, { item: 'minecraft:gunpowder', count: 12 }, { item: 'minecraft:coal', count: 16 }, { item: 'minecraft:gold_ingot', count: 6 }] },
    { spawnInterval: 11, maxAlive: 44, mobs: [{ type: 'minecraft:zombie', count: 42 }, { type: 'minecraft:husk', count: 12 }, { type: 'minecraft:skeleton', count: 12 }, { type: 'minecraft:vindicator', count: 2, breaker: true }, { type: 'minecraft:zombie', count: 2, elite: true }],
      rewards: [{ item: 'minecraft:iron_ingot', count: 20 }, { item: 'minecraft:arrow', count: 128 }, { item: 'minecraft:wheat_seeds', count: 40 }, { item: 'minecraft:diamond', count: 1 }] },
    { spawnInterval: 10, maxAlive: 48, mobs: [{ type: 'minecraft:zombie', count: 50 }, { type: 'minecraft:husk', count: 14 }, { type: 'minecraft:skeleton', count: 12 }, { type: 'minecraft:vindicator', count: 3, breaker: true }, { type: 'minecraft:creeper', count: 3 }, { type: 'minecraft:zombie', count: 2, elite: true }],
      rewards: [{ item: 'minecraft:iron_ingot', count: 24 }, { item: 'minecraft:copper_ingot', count: 24 }, { item: 'minecraft:gunpowder', count: 16 }, { item: 'minecraft:diamond', count: 2 }] },
    { spawnInterval: 10, maxAlive: 52, mobs: [{ type: 'minecraft:zombie', count: 58 }, { type: 'minecraft:husk', count: 16 }, { type: 'minecraft:skeleton', count: 16 }, { type: 'minecraft:vindicator', count: 4, breaker: true }, { type: 'minecraft:creeper', count: 3 }, { type: 'minecraft:zombie', count: 3, elite: true }],
      rewards: [{ item: 'minecraft:iron_ingot', count: 28 }, { item: 'minecraft:arrow', count: 192 }, { item: 'minecraft:coal', count: 24 }, { item: 'minecraft:diamond', count: 3 }, { item: 'minecraft:gold_ingot', count: 12 }] }
  ]
}
