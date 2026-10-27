# 개발 환경과 검증

명령은 저장소 루트에서 실행한다. 작업 시 [작업 규칙](workflow.md)을 따른다.

## 구성

| 경로      | 내용                                                                              |
|-----------|-----------------------------------------------------------------------------------|
| `kubejs/` | KubeJS 스크립트·설정 (자동 웨이브 진행·보상·메뉴·레시피, 웨이브 구성 JSON)        |
| `mod/`    | 동반 모드 `coredefender` (디펜스 차원·장벽·포탑·몹 AI·스폰·보급·UI·KubeJS 브리지) |
| `tools/`  | 개발 보조 스크립트                                                                |

## 개발 환경

1. CurseForge 로 모드팩 인스턴스를 만든다.
2. 인스턴스의 `kubejs/` 를 이 저장소로 연결한다 (게임을 끈 상태에서):
   ```powershell
   powershell -ExecutionPolicy Bypass -File tools/link-instance.ps1 -Instance "<인스턴스 경로>"
   ```
3. 게임을 종료하고 `mod/gradle.properties`의 `mod_version` 패치 번호를 배포마다 1 올린다. 기존 JAR를 별도 폴더에 백업한 뒤 빌드·복사한다:
   ```powershell
   .\mod\gradlew.bat -p mod build deployToInstance -PinstanceDir="<인스턴스 경로>"
   ```
   복사된 JAR의 버전과 해시를 확인한 후 이전 `coredefender-*.jar`를 mods 폴더에서 제거한다. 배포 작업은 실행 중 게임에 수행하지 않는다.

변경 반영: `server_scripts` 는 `/reload`, `startup_scripts`·`mod/` 는 클라이언트 재시작.

## 검증

검증 명령:

```powershell
.\mod\gradlew.bat -p mod build --offline --no-daemon
node tools/test-turret-recipes.cjs
node tools/test-admin-reset.cjs
node tools/test-wave-progression.cjs
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
