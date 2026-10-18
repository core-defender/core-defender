# Core Defender

전투 콘텐츠와 공학(Create → Immersive Engineering → IE Petroleum → Mekanism)이 서로를 필요로 하는
디펜스 모드팩. 별도 디펜스 차원에서 코어를 지키며 웨이브를 막는다.

- Minecraft 1.21.1 / NeoForge 21.1.262 / KubeJS 7
- 설계: [DESIGN.md](DESIGN.md)

## 구성

| 경로 | 내용 |
|---|---|
| `kubejs/` | KubeJS 스크립트·데이터팩·리소스 (웨이브/코어/진행도 로직, 디펜스 차원) |
| `mod/` | 동반 모드 `coredefender` (메뉴 UI, KubeJS 브리지) |
| `tools/` | 개발 보조 스크립트 |

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
