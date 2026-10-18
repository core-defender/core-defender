// 디펜스 차원의 코어 엔티티.
// 피해 판정(웨이브 중 몹에게만 피해)은 server_scripts/coredefender/main.js 의 beforeHurt 에서 처리한다.

StartupEvents.registry('entity_type', event => {
  event.create('coredefender:core', 'entityjs:living')
    .sized(1.2, 2.2)
    .modelResource(() => 'coredefender:geo/entity/core.geo.json')
    .textureResource(() => 'coredefender:textures/entity/core.png')
    .animationResource(() => 'coredefender:animations/entity/core.animation.json')
    .addAnimationController('main', 1, e => {
      e.thenLoop('animation.core.idle')
      return true
    })
    .mobCategory('misc')
    .clientTrackingRange(16)
    .setSummonable(true)
    .isPushable(false)
    .fireImmune(true)
    .isAffectedByPotions(() => false)
    .isAffectedByFluids(() => false)
    .canChangeDimensions(() => false)
    .shouldDropLoot(() => false)})
