// coredefender 모드 아이템 레시피. 공학 단계별 재료로 진행을 묶는다.

ServerEvents.recipes(event => {
  // 킬존 (공학 이전)
  event.shaped(Item.of('coredefender:barricade', 4), [
    'S S',
    'PPP',
    'PPP'
  ], { S: 'minecraft:stick', P: '#minecraft:planks' })

  event.shaped(Item.of('coredefender:spike_trap', 4), [
    'N N',
    'III'
  ], { N: 'minecraft:iron_nugget', I: 'minecraft:iron_ingot' })

  // 기초 포탑 (공학 1단계)
  event.shaped('coredefender:basic_turret', [
    'CCC',
    'DBD',
    'CRC'
  ], { C: 'minecraft:cobblestone', D: 'minecraft:dispenser', B: 'minecraft:bow', R: 'minecraft:redstone' })

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
