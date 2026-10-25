// coredefender 모드 아이템 레시피. 공학 단계별 재료로 진행을 묶는다.

// KubeJS supplies these Java bindings and script globals at runtime.
//noinspection JSUnresolvedVariable
ServerEvents.recipes(event => {
  // 수동 압착통: 씨앗 10개 -> 250mB. Create 기본 압착(씨앗 1개 -> 100mB)은 유지한다.
  event.custom({
    type: 'create:emptying',
    ingredients: [{ item: 'coredefender:seed_oil_bottle' }],
    results: [{ id: 'minecraft:glass_bottle', count: 1 }, { id: 'createaddition:seed_oil', amount: 250 }]
  }).id('coredefender:emptying/seed_oil_bottle')
  event.custom({
    type: 'create:filling',
    ingredients: [{ item: 'minecraft:glass_bottle' }, { type: 'neoforge:single', fluid: 'createaddition:seed_oil', amount: 250 }],
    results: [{ id: 'coredefender:seed_oil_bottle', count: 1 }]
  }).id('coredefender:filling/seed_oil_bottle')
  event.shapeless('createaddition:seed_oil_bucket', [
    'minecraft:bucket', 'coredefender:seed_oil_bottle', 'coredefender:seed_oil_bottle',
    'coredefender:seed_oil_bottle', 'coredefender:seed_oil_bottle'
  ]).id('coredefender:seed_oil_bucket_from_bottles')

  // 킬존 (공학 이전)
  // KubeJS supplies these Java bindings and script globals at runtime.
  //noinspection JSUnresolvedVariable
  event.shaped(Item.of('coredefender:barricade', 4), [
    'S S',
    'PPP',
    'PPP'
  ], { S: 'minecraft:stick', P: '#minecraft:planks' })

  // KubeJS supplies these Java bindings and script globals at runtime.
  //noinspection JSUnresolvedVariable
  event.shaped(Item.of('coredefender:spike_trap', 4), [
    'N N',
    'III'
  ], { N: 'minecraft:iron_nugget', I: 'minecraft:iron_ingot' })

  // 화살 포탑 (공학 이전)
  event.shaped('coredefender:basic_turret', [
    'SBS',
    'IRI',
    'CCC'
  ], { S: 'minecraft:stick', I: 'minecraft:iron_ingot', C: 'minecraft:cobblestone', B: 'minecraft:bow', R: 'minecraft:redstone' }).id('coredefender:basic_turret')

  // 보급 송수신기
  event.shaped('coredefender:supply_sender', [
    'OEO',
    'ECE',
    'OEO'
  ], { O: 'minecraft:obsidian', E: 'minecraft:ender_pearl', C: 'minecraft:chest' })

  event.shaped('coredefender:supply_receiver', [
    'OCO',
    'EHE',
    'OEO'
  ], { O: 'minecraft:obsidian', E: 'minecraft:ender_pearl', C: 'minecraft:chest', H: 'minecraft:hopper' })

  // 보급 강화 부품: Create → IE → Mekanism
  event.shaped('coredefender:supply_upgrade_1', [
    'ABA',
    'BPB',
    'ABA'
  ], { A: 'create:andesite_alloy', B: 'create:brass_ingot', P: 'create:precision_mechanism' })

  event.shaped('coredefender:supply_upgrade_2', [
    'SCS',
    'EUE',
    'SCS'
  ], { S: 'immersiveengineering:ingot_steel', C: 'immersiveengineering:component_steel',
       E: 'immersiveengineering:component_electronic', U: 'coredefender:supply_upgrade_1' })

  event.shaped('coredefender:supply_upgrade_3', [
    'AXA',
    'TUT',
    'AXA'
  ], { A: 'mekanism:alloy_atomic', X: 'mekanism:ultimate_control_circuit', T: 'mekanism:teleportation_core',
       U: 'coredefender:supply_upgrade_2' })

  // 성벽 확장 설계도 (TODO: 웨이브 전용 드롭 재료가 생기면 추가)
  event.shaped('coredefender:wall_expansion_plan', [
    'PSP',
    'SMS',
    'PSP'
  ], { P: 'minecraft:paper', S: 'minecraft:stone_bricks', M: 'minecraft:map' })
})
