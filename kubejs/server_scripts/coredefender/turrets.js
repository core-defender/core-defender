// Turret recipes: all three chassis and grade I modules are available before Create.
// KubeJS supplies these Java bindings and script globals at runtime.
//noinspection JSUnresolvedVariable
ServerEvents.recipes(event => {
  event.shaped('coredefender:stone_turret', ['SLS', 'IPI', 'CCC'], {
    S: 'minecraft:string', L: '#minecraft:logs', I: 'minecraft:iron_ingot', P: 'minecraft:piston', C: 'minecraft:cobblestone'
  }).id('coredefender:stone_turret')
  event.shaped('coredefender:flame_turret', ['ICI', 'BFB', 'CRC'], {
    I: 'minecraft:iron_ingot', C: 'minecraft:copper_ingot', B: 'minecraft:bucket', F: 'minecraft:furnace', R: 'minecraft:redstone'
  }).id('coredefender:flame_turret')
  event.shaped('coredefender:range_analyzer', [' C ', ' R ', ' I '], {
    C: 'minecraft:copper_ingot', R: 'minecraft:redstone', I: 'minecraft:iron_ingot'
  }).id('coredefender:range_analyzer')
  // KubeJS supplies these Java bindings and script globals at runtime.
  //noinspection JSUnresolvedVariable
  event.shaped(Item.of('coredefender:module_chassis', 4), ['CIC', ' R ', 'CIC'], {
    C: 'minecraft:copper_ingot', I: 'minecraft:iron_ingot', R: 'minecraft:redstone'
  }).id('coredefender:module_chassis')

  const materials = {
    damage: ['2x minecraft:iron_ingot', '2x minecraft:flint'],
    speed: ['2x minecraft:copper_ingot', '2x minecraft:redstone'],
    pierce: ['minecraft:iron_ingot', '4x minecraft:flint'],
    multishot: ['2x minecraft:string', '4x minecraft:arrow'],
    ricochet: ['minecraft:slime_ball', '2x minecraft:iron_ingot'],
    aim: ['2x minecraft:redstone', '2x minecraft:string'],
    stone_range: ['2x minecraft:string', 'minecraft:redstone'],
    blast: ['2x minecraft:gunpowder', '#minecraft:coals', '#minecraft:coals'],
    cluster: ['2x minecraft:gunpowder', '2x minecraft:flint'],
    flame_range: ['2x minecraft:copper_ingot', '2x minecraft:glass'],
    burn: ['#minecraft:coals', '#minecraft:coals', 'minecraft:gunpowder']
  }
  Object.keys(materials).forEach(name => {
    event.shapeless(`coredefender:module_${name}_1`, ['coredefender:module_chassis'].concat(materials[name]))
      .id(`coredefender:module_${name}_1`)
    // Grade effects are implemented now; progression recipes use the pack's existing engineering chain.
    event.shapeless(`coredefender:module_${name}_2`, [
      `coredefender:module_${name}_1`, 'create:precision_mechanism', '2x create:brass_ingot'
    ]).id(`coredefender:module_${name}_2`)
    event.shapeless(`coredefender:module_${name}_3`, [
      `coredefender:module_${name}_2`, 'immersiveengineering:component_electronic', '2x immersiveengineering:ingot_steel'
    ]).id(`coredefender:module_${name}_3`)
  })
})
