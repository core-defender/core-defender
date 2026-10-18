# Core Defender — 설계 문서

인스턴스 경로: `C:\Users\user\curseforge\minecraft\Instances\CoreDefender`
환경: Minecraft 1.21.1 / NeoForge 21.1.262 / KubeJS 7 (2101.7.2) / EntityJS 1.5.2

## 목표
전투 진행에는 공학이, 공학 진행에는 전투 파밍이 필요한 순환 구조.
멀티플레이 시 각자 원하는 콘텐츠를 하면서도 서로 연결된 느낌을 준다.

## 디펜스 차원
- 서버 전체 공유. 월드(스테이지) 단위로 구성: `coredefender:world_1`, `world_2`, ...
- 한 월드의 모든 웨이브를 클리어하면 다음 월드 해금. 클리어한 월드는 반복 도전 가능(보상 감소).
- 월드 ↔ 공학 티어 1:1 매핑 (아래 표).
- 디펜스 차원에서는 일반 건축 불가. 지정 포탑 구역에서만 허용 블록 설치(혼합 방식).
- 맵은 개발 월드에서 건축 → 구조물 NBT 저장 → 최초 진입 시 배치(48³ 조각 타일링).

## 코어
- EntityJS 커스텀 living 엔티티(`coredefender:core`), 움직이지 않음. HP는 bossbar로 표시.
- 웨이브 몹은 주기적으로 `setTarget(core)` 갱신 → 코어로 이동/공격.
- 한 월드에 코어 1개 이상, 하나라도 파괴되면 웨이브 실패.

## 웨이브
- 플레이어가 자율 시작. 직전 클리어 후 빠르게 시작할수록 보상 배율↑ (서버 가동 tick 기준).
- 실패 시(롤백 A안): 남은 웨이브 몹 제거, 웨이브 번호 유지, 보상 없음. 몹이 부순 구조물은 복구되지 않음.
- 실패 후 코어 복구에 현재 공학 티어 자원 소모 → 복구 전엔 웨이브 시작 불가.

## 포탑 (혼합 방식)
- 기본: 포탑 받침대 + 포탑 모듈 아이템(오버월드 공학 라인에서 제작) → 커스텀 포탑.
- 일부 대형 슬롯: IE 포탑/Tesla Coil, CBC 대포(+Create Radar) 실제 조립 허용.

## 전투 콘텐츠
- 레벨링 없음. CtE 스타일 스킬/장비 → 후보: Apotheosis(접사 장비), Iron's Spells 'n Spellbooks, Better Combat.
- 드롭 분리: 포탑 처치 = 공학 재료(scrap), 플레이어 처치 = 희귀 재료 (LootJS `killed_by_player`).
- 포탑 내성 몹/엘리트/보스로 플레이어 개입 강제.

## 공학 진행 × 월드 매핑 (초안)
| 월드 | 공학 단계 | 방어 수단 | 다음 해금 드롭 |
|---|---|---|---|
| 1 | Create | 수동 CBC, 플레이어 전투 | IE 시작 재료 |
| 2 | IE | IE Turret, Tesla Coil | IP 해금 재료 |
| 3 | IE Petroleum | Chemthrower(IP 연료), 오토캐논 | Mekanism 해금 재료 |
| 4 | Mekanism | Radar 자동조준 Big Cannon | 최종 코어 강화 재료 |

## 정리된 결정
- Actually Additions 제거됨. AE2는 저장소 용도로 유지.

## 리스크
- 장거리 경로 탐색 → 짧은 레인 설계 / 웨이포인트 goal.
- 플레이어 없는 차원의 청크 로딩 → 웨이브 중 강제 로딩 또는 일시정지.
