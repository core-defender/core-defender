// IDE 전용 선언. KubeJS 는 .js 만 로드하며, 아래 이름은 KubeJS 와 coredefender 모드가 런타임에 주입한다.

declare const ServerEvents: any
declare const Item: any
declare const CoreDefenderEvents: any
declare const CoreDefenderUI: any
declare const CoreDefenderWall: any
declare const CoreDefenderWave: any

/** coredefender-waves.json 과 CoreDefenderWall.info() JSON 의 필드. */
interface CdJsonFields {
  hoverName: any
  maxHp: number
  perCollapsed: number
  perHundredMissing: number
  maxHalf: number
  sideUpgrade: any
  coreX: number
  coreY: number
  coreZ: number
}
