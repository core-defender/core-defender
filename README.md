# Core Defender

전투 콘텐츠와 공학 (Create → Immersive Engineering → IE Petroleum → Mekanism)이 서로를 필요로 하는
디펜스 모드팩. 별도 디펜스 차원에서 코어를 지키며 웨이브를 막는다.

- Minecraft 1.21.1 / NeoForge 21.1.262 / KubeJS 7
- 설계: [DESIGN.md](DESIGN.md)

## 구성

| 경로      | 내용                                                                   |
|-----------|------------------------------------------------------------------------|
| `kubejs/` | KubeJS 스크립트·데이터팩·리소스 (웨이브/코어/진행도 로직, 디펜스 차원) |
| `mod/`    | 동반 모드 `coredefender` (메뉴 UI, KubeJS 브리지)                      |
| `tools/`  | 개발 보조 스크립트                                                     |

## 개발 환경

1. CurseForge 로 모드팩 인스턴스를 만든다.
2. 인스턴스의 `kubejs/` 를 이 저장소로 연결한다 (게임을 끈 상태에서):
   ```powershell
   powershell -ExecutionPolicy Bypass -File tools/link-instance.ps1 -Instance "<인스턴스 경로>"
   ```
3. 모드 빌드 후 인스턴스에 복사:
   ```sh
   cd mod
   ./gradlew build deployToInstance -PinstanceDir="<인스턴스 경로>"
   ```

변경 반영: `server_scripts` 는 `/reload`, `startup_scripts`·`mod/` 는 클라이언트 재시작.

## 관리자 테스트 명령

모두 관리자 권한 (레벨 2)이 필요하다. 초기화 명령은 진행 중 웨이브와 테스트 적을 보상 없이 중단한다.

| 명령                         | 범위                                                            |
|------------------------------|-----------------------------------------------------------------|
| `/cd admin reset`            | 스테이지·보상 타이머 + 성벽 크기·강화·HP 초기화. 시설/창고 보존 |
| `/cd admin reset progress`   | 스테이지·보상 타이머만 초기화                                   |
| `/cd admin reset wall`       | 성벽 전체 초기화 (2×2청크, 강화 0, 구간 HP 500)                 |
| `/cd admin reset size`       | 성벽 크기 초기화 및 완전 수복, 강화 유지                        |
| `/cd admin reset levels`     | 성벽 강화 초기화 및 완전 수복, 크기 유지                        |
| `/cd admin reset hp`         | 성벽 완전 수복, 크기·강화 유지                                  |
| `/cd admin reset facilities` | 기지/킬존의 지상 시설과 내부 아이템 삭제 (코어/성벽 유지)       |
| `/cd admin reset storage`    | 코어 창고 삭제                                                  |
| `/cd admin reset supply`     | 차원 간 공용 보급 버퍼 삭제                                     |
| `/cd admin reset all`        | 위 진행·성벽·시설·창고·보급 버퍼 모두 초기화                    |

시설 삭제 범위는 기록된 최대 성벽 크기 + 바깥 킬존 12블록, 성벽 바닥 높이부터 월드 상단까지다.
지하와 이 범위 밖 건축물, 오버월드 시설, 플레이어 소지품은 대상이 아니다.
장벽 확장/축소는 경계 데이터만 변경하므로 일반 시설은 유지한다.
디펜스 차원의 플레이어는 축소 시 코어 앞으로 이동한다.

테스트 적은 디펜스 차원에서 `/cd admin spawn <엔티티 ID> [수량] [x y z] [종류]`로 소환한다.
위치 생략 시 플레이어 전방 4블록의 지표면. 종류는 `normal`, `elite`, `breaker`, `elite_breaker`다.
성벽에서 64블록 이내, 회당 1~100마리, 동시 테스트 적 최대 200마리로 제한한다.

```text
/cd admin spawn minecraft:zombie
/cd admin spawn minecraft:skeleton 3 ~ ~ ~5
/cd admin spawn minecraft:zombie 1 ~ ~ ~5 elite
/cd admin spawn minecraft:vindicator 1 ~ ~ ~5 breaker
/cd admin spawn clear
```

테스트 적은 실제 웨이브 AI/피해 규칙을 사용하지만 웨이브 잔여 수·클리어 보상에는 포함하지 않는다.
성벽을 실제로 공격하므로 진행 중 웨이브의 패배에는 영향을 줄 수 있다.
`spawn clear`, `abort`, 초기화 또는 서버 종료로 제거되며 재접속 후 잔존하지 않는다.
일반 방식으로 설치한 MmmMmmMmmMmm 모드의 `dummmmmmy:target_dummy`도 포탑이 공격한다.
Dummy는 웨이브 적으로 등록하지 않으며 초기화/웨이브 정리 대상에 포함되지 않는다.

명령어/진행도 회귀 검사: `node tools/test-admin-reset.cjs`. 실제 월드 변경·AI·명중은 게임 내 검증이 필요하다.

## Vinery 수동 종자유 압착

Vinery 1.5.6의 `vinery:grapevine_pot`에 씨앗 제작 경로를 추가한다. 기존 포도 제작은 유지한다.
빈 통에 `c:seeds`의 같은 씨앗을 10개 넣고 플레이어가 12번 착지하면 일반 유리병으로
`coredefender:seed_oil_bottle` 1개 (250mB)를 회수한다. 우클릭은 1개, 웅크려 우클릭은 남은 공간까지 투입한다.
씨앗당 25mB로 Create Crafts & Additions 기본 압착 (씨앗당 100mB)의 1/4 수율이다.
압착 전 통을 파괴하면 씨앗이 반환되고, 압착을 시작한 내용물은 파괴 시 소실된다.
제작 진행도는 저장·동기화되며, 몹 착지는 압착으로 인정하지 않는다.
Create 주입/추출 레시피는 병당 250mB이며, 종자유 병 4개와 빈 양동이로 1,000mB 양동이를 제작할 수 있다.
제작 재료로 사용한 종자유 병은 빈 유리병을 반환한다. 새 아이템 등록을 위해 게임 재시작이 필요하다.

## 에너지 장벽 프로토타입

실체 성벽 대신 반투명 장벽을 사용한다. 기존 월드를 새 모드로 열면 구 성벽 위 시설을 코어 창고로 회수하고
성벽 블록을 제거한다. 성벽 크기·강화·구간 HP는 유지한다. 이 변환은 저장 데이터에 기록되어 한 번만 수행된다.

- 플레이어와 포탑 화살은 통과하고, 웨이브 몹의 실제 이동은 장벽에서 제한된다.
- 적 화살은 장벽에 피해를 주고 소멸한다. 몹끼리 피해 없음/아군 몹 투사체 통과 규칙은 유지한다.
- 포탑은 내부 지면이나 직접 만든 사격대에 설치한다. 화살 발사 위치를 장벽 밖으로 옮기는 처리는 제거했다.
- 장벽 구간이 붕괴하면 그 구간의 시각 효과와 충돌이 꺼지고, 웨이브 중에는 패배한다.
- 초기 장벽은 정확히 32×32칸이다. 구 성벽 두께가 남은 저장 데이터도 로드 시 보정한다.
- 구간 HP 75% 미만부터 균열이 나타나고, 손상이 커지면 일부 표면 조각이 사라진다. 수복하면 회복된다.
- 충돌 기하 및 상태 저장 검사: `mod/gradlew.bat -p mod test`.

게임 내 확인 순서: 기존 월드 전환/시설 회수 → 지면 포탑으로 장벽 밖 Dummy 사격 →
좀비 소환 후 장벽 접근·공격·넉백 → 스켈레톤의 아군 통과/장벽 피해 → 크리퍼 폭발 → 수복·확장·재접속.
기하 테스트는 실제 게임의 Mixin 적용, 렌더링, 서버 틱 성능이나 다른 모드 포탄 호환을 보장하지 않는다.

## 포탑 개편 1차 구현 (2026-10-26)

추천안 기반으로 화살/투석/화염 포탑, 2개 모듈 슬롯, 11종 × I~III 모듈, 증폭 자원,
실제 탄도/관통/다중발사/도탄/집속탄/부채꼴 분사/포탑별 잔류 화상을 구현했다.
`basic_turret` ID는 화살 포탑으로 유지하고 기존 9칸 탄약을 보존한다.

- 포탑 우클릭: 자원 인벤토리와 운용/모듈/설정 UI. 웅크려 우클릭: 빠른 자원 투입.
- 방어 분석기로 우클릭: 15초간 해당 포탑 사거리 표시. 여러 포탑을 각각 표시할 수 있다.
- 모든 자동화 면은 기본 차단. 설정에서 아래/위/북/남/서/동의 입력/출력/입출력을 지정한다.
- 자동화 입력 상한은 탄약 전체/연료/증폭 아이템/종자유/용암별로 설정한다. 모듈 자동 삽입은 지원하지 않는다.
- 자동 출력은 빈 용기이며 탄약·연료·모듈·저장 유체는 추출되지 않는다. UI의 수동 유체 회수 결과는 직접 꺼낸다.
- 화살 탄약 9칸, 투석 돌 8칸+연료 1칸, 화염 종자유 9000mB+용기 투입/회수. 증폭 아이템 1칸 또는 유체 1000mB.
- 파괴 회수 시 자원·모듈·설정·남은 연료/증폭 횟수·장전 시각을 포탑 아이템에 저장한다.
- 포탑은 디펜스 차원 성벽 내부에서만 공격하며 웨이브/관리자 테스트 적/Dummy만 타격한다.
- 화살/물약 효과/투석/파편/화염/잔류 화상은 넉백 없이 피해를 준다. 기존 몹끼리 피해 방지 규칙은 유지한다.
- 빨강 상태등은 필수 자원 부족, 금색은 증폭 소진 또는 용기 출력 막힘, 회색은 레드스톤 정지다. 바라보면 사유가 표시된다.
- 실제 DPS는 최근 10초의 실제 HP 감소량 합계/10이다. 장갑 경감과 초과 피해를 반영하며 화상/파편/물약 효과를 원래 포탑에 귀속한다.

초반 웨이브는 10단계, 몹 수 12/18/24/32/40/48/58/70/84/100으로 조정했다.
스켈레톤 4단계, 정예 7단계, 파괴자 8단계, 크리퍼 9단계부터 등장한다.
철·화살·석탄·씨앗 등을 보상에 분산했고, 성벽 수복은 철 (붕괴 구간당 8개 + 손실 HP 100당 1개)로 변경했다.
기존 세이브의 진행 단계는 자동 초기화하지 않는다. 초기 밸런스 테스트에는 `/cd admin reset progress`를 사용한다.

조정 위치:

- 기본 피해/주기/사거리/모듈 합산: `mod/src/main/java/lkd/namsic/coredefender/turret/TurretStats.java`
- 분사 소모량/자원량/슬롯/기본 설정: `turret/BasicTurretBlockEntity.java`
- 제작법: `kubejs/server_scripts/coredefender/turrets.js`, `recipes.js`
- 웨이브/보상/수복 비용: `kubejs/server_scripts/coredefender/config.js`
- 일반 돌/심층암 허용: `mod/src/main/resources/data/coredefender/tags/item/turret_stones.json`, `turret_deepslate.json`

투석 사거리 모듈은 물리적으로 늘어난 사거리에 도달하도록 발사 속도에도 `sqrt(1+사거리 보너스)`를 적용한다.
화약의 탄속 +15%는 별도 적용된다. II는 하위 모듈+Create 정밀 기계장치+황동 2개,
III는 하위 모듈+IE 전자 부품+강철 2개로 초기 제작법을 잡았다. 둘 다 추후 밸런스 조정 대상이다.
화염은 기본 10틱 분사+20틱 휴지기이며 공속 모듈은 휴지기만 줄인다.

검증 명령:

```powershell
.\mod\gradlew.bat -p mod build --offline --no-daemon
node tools/test-turret-recipes.cjs
node tools/test-admin-reset.cjs
.\mod\gradlew.bat -p mod -PturretGameTests '-PtestInstanceDir=C:/Users/user/curseforge/minecraft/Instances/CoreDefender' runTurretGameTest --offline --no-daemon
```

게임테스트는 `mod/build/turret-gametest`의 독립 월드에서 실행하며 사용자 세이브를 열지 않는다.
`src/gameTest/java`와 `src/gameTest/resources`는 옵션 없이도 Gradle 소스 세트와 IntelliJ 테스트 폴더로 등록된다.
IntelliJ에서 Gradle 프로젝트를 다시 동기화하면 적용된다. 일반 `build`/`check`는 GameTest 코드를 컴파일하지만
서버를 실행하지 않으며, 테스트 코드와 리소스는 배포 JAR에 포함하지 않는다.
GameTest는 JUnit 실행 버튼 대신 위의 `runTurretGameTest` 명령으로 실행한다.
`turretGameTests`와 `testInstanceDir` 옵션은 서버 실행 설정과 전용 런타임 의존성을 활성화할 때만 필요하다.
최소 런타임 (KubeJS/Rhino/Architectury)에서 실제 NeoForge/Mixin과 전투/자원/저장을 검사한다.
Create 종자유 유체 통합, 전체 모드팩의 JEI 제작법, 클라이언트 UI/애니메이션 및 4~8대 실전 웨이브 난이도는
모드팩에서 추가 확인할 항목이다. 숫자와 단계 구성은 플레이 검증 전 1차 조정값이다.
