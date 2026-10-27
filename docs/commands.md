# 명령어

모든 명령은 `/cd` 아래에 있다. 관리자 명령 (`/cd admin …`)은 권한 레벨 2가 필요하다.
포탑·장벽 동작은 [포탑](turrets.md), 웨이브 규칙과 설정 파일은 [웨이브](waves.md)를 참조한다.

## 플레이어 명령

| 명령                | 동작                                                                            |
|---------------------|---------------------------------------------------------------------------------|
| `/cd enter`         | 디펜스 기지 코어 앞으로 이동 (입장 전 위치 저장)                                |
| `/cd leave`         | 입장 전 위치로 복귀 (저장된 위치가 없으면 오버월드 스폰)                        |
| `/cd menu`          | 관리 화면 열기. K 키, 코어 우클릭과 같다. 코어 웅크려 우클릭은 코어 창고        |
| `/cd start`         | 자동 웨이브 진행 활성화 (이미 활성화됐거나 관리자가 중지한 경우 거부)           |
| `/cd difficulty up` | 10웨이브 클리어 후 기지 안에서 다음 난이도로 승급 (메뉴의 '난이도 상승'과 같음) |
| `/cd repair`        | 자원을 소모해 손상·붕괴한 성벽 수복 (웨이브 중 불가)                            |
| `/cd status`        | 현재 웨이브·대기 시간·성벽 구간 HP 출력                                         |

## 관리자: 웨이브 제어

- `/cd admin auto off`: 자동 시작 중지. 진행 중인 전투는 유지하며 재접속·서버 재시작 후에도 중지 상태를 보존한다. 일반 `/cd start`로 해제할 수 없다.
- `/cd admin auto on`: 자동 시작 재개. 대기 중이면 설정된 준비 시간 전체를 다시 부여하고, 전투 중이면 클리어 이후부터 적용한다.
- `/cd admin start`: 현재 다음 웨이브를 대기 없이 시작. `/cd admin start 5`처럼 현재 난이도의 웨이브 번호도 지정할 수 있다. 자동 시작 중지 상태는 유지한다. 기존 전투와
  중첩하거나 붕괴한 성벽으로 시작하지 않는다.
- `/cd admin abort`: 현재 전투·테스트 적을 보상 없이 정리한다. 자동 시작 설정 자체는 바꾸지 않는다. 전투와 자동 시작을 모두 멈추려면 `auto off` 후 `abort`를 사용한다.
- `/cd admin stage <n>`: 현재 난이도에서 다음에 시작할 웨이브 번호를 지정한다 (1~10). 반복 횟수를 초기화하고 준비 시간을 다시 부여한다. 전투 중에는 무시한다.

## 관리자: 웨이브 설정

- `/cd admin config reload`: `kubejs/config/coredefender-waves.json`을 검증 후 적용한다. 자세한 규칙은 [웨이브 설정](waves.md#설정-파일)을 참조한다.
- `/cd admin config timer`: 대기 중인 준비 타이머에 새 간격을 적용한다. 전투 중에는 거부한다.

## 관리자: 성벽

자원을 소모하지 않는다.

- `/cd admin wall info`: 현재 경계·높이·구간 ID·HP 확인.
- `/cd admin wall damage N0 100`: 지정 구간 HP를 정확히 100 감소. `damage`는 구간 ID만 받으며 면 이름이나 `all`은 받지 않는다.
- `/cd admin wall repair`: 전체 완전 수복.
- `/cd admin wall repair N` 또는 `... repair N0`: 해당 면 전체 또는 단일 구간 완전 수복.
- `/cd admin wall repair all 100`, `... repair N 100`, `... repair N0 100`: 선택한 각 구간에 최대 HP를 넘지 않게 100씩 수복.
- `/cd admin wall expand`: 성벽 확장. `/cd admin wall rebuild`: 성벽 재구축. 테스트 월드에서 사용한다.
- `/cd admin tp`: 코어 앞으로 이동한다. 입장 전 위치를 저장하지 않으므로 `/cd leave`는 오버월드 스폰으로 보낸다.

## 관리자: 초기화와 테스트 적

모두 관리자 권한 (레벨 2)이 필요하다. 초기화 명령은 진행 중 웨이브와 테스트 적을 보상 없이 중단한다.

| 명령                         | 범위                                                                           |
|------------------------------|--------------------------------------------------------------------------------|
| `/cd admin reset`            | 난이도·웨이브·자동 진행/준비 타이머 + 성벽 크기·강화·HP 초기화. 시설/창고 보존 |
| `/cd admin reset progress`   | 스테이지·보상 타이머만 초기화                                                  |
| `/cd admin reset wall`       | 성벽 전체 초기화 (2×2청크, 강화 0, 구간 HP 500)                                |
| `/cd admin reset size`       | 성벽 크기 초기화 및 완전 수복, 강화 유지                                       |
| `/cd admin reset levels`     | 성벽 강화 초기화 및 완전 수복, 크기 유지                                       |
| `/cd admin reset hp`         | 성벽 완전 수복, 크기·강화 유지                                                 |
| `/cd admin reset facilities` | 기지/킬존의 지상 시설과 내부 아이템 삭제 (코어/성벽 유지)                      |
| `/cd admin reset storage`    | 코어 창고 삭제                                                                 |
| `/cd admin reset supply`     | 차원 간 공용 보급 버퍼 삭제                                                    |
| `/cd admin reset all`        | 위 진행·성벽·시설·창고·보급 버퍼 모두 초기화                                   |

시설 삭제 범위는 기록된 최대 성벽 크기 + 바깥 킬존 12블록, 성벽 바닥 높이부터 월드 상단까지다.
지하와 이 범위 밖 건축물, 오버월드 시설, 플레이어 소지품은 대상이 아니다.
장벽 확장/축소는 경계 데이터만 변경하므로 일반 시설은 유지한다.
디펜스 차원의 플레이어는 축소 시 코어 앞으로 이동한다.

테스트 적은 디펜스 차원에서 `/cd admin spawn <엔티티 ID> [수량] [x y z] [종류] [NBT]`로 소환한다.
위치 생략 시 명령어 실행 위치의 X/Y/Z를 그대로 사용한다. 종류는 `normal`, `elite`, `breaker`, `elite_breaker`다.
NBT는 엔티티 ID·수량·좌표·종류 중 마지막으로 지정한 인자 뒤에 `{...}` 형식으로 붙인다. 기본/변종 설정 위에 적용되며, `id`, `UUID`, `UUIDMost`, `UUIDLeast`, `Pos`,
`Passengers`는 무시한다. 소환 위치·개체 식별자·테스트 적/파괴자 표식·지속성은 명령어가 관리한다.
성벽에서 64블록 이내, 회당 1~100마리, 동시 테스트 적 최대 200마리로 제한한다.

```text
/cd admin spawn minecraft:zombie
/cd admin spawn minecraft:skeleton 3 ~ ~ ~5
/cd admin spawn minecraft:zombie 1 ~ ~ ~5 elite
/cd admin spawn minecraft:vindicator 1 ~ ~ ~5 breaker
/cd admin spawn minecraft:creeper {powered:1b,ExplosionRadius:5b,Fuse:60s}
/cd admin spawn minecraft:zombie 3 {IsBaby:1b}
/cd admin spawn minecraft:zombie 1 ~ ~ ~ normal {NoAI:1b,Health:10f}
/cd admin spawn clear
```

보스는 `/cd admin spawn <boss|mini|repeat_mini> [단계] [x y z]`로 1마리 소환한다. 단계 기본값은 1이다.
웨이브 설정의 보스 능력치와 해당 웨이브(미니 보스는 5번째, 보스·반복 미니 보스는 10번째)의 배율·규칙을 적용하며,
소환사 보스의 방호와 졸개 소환도 동작한다. 졸개도 테스트 적으로 취급한다.

```text
/cd admin spawn boss
/cd admin spawn mini 2 ~ ~ ~10
/cd admin spawn repeat_mini 1
```

테스트 적은 실제 웨이브 AI/피해 규칙을 사용하지만 웨이브 잔여 수·클리어 보상에는 포함하지 않는다.
성벽을 실제로 공격하므로 진행 중 웨이브의 패배에는 영향을 줄 수 있다.
`spawn clear`, `abort`, 초기화 또는 서버 종료로 제거되며 재접속 후 잔존하지 않는다.
일반 방식으로 설치한 MmmMmmMmmMmm 모드의 `dummmmmmy:target_dummy`도 포탑이 공격한다.
Dummy는 웨이브 적으로 등록하지 않으며 초기화/웨이브 정리 대상에 포함되지 않는다.

명령어/진행도 회귀 검사: `node tools/test-admin-reset.cjs`와 `node tools/test-wave-progression.cjs`. 실제 월드 변경·AI·명중은 GameTest 및 게임
내 검증이 필요하다.
