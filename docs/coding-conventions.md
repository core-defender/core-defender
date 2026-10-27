# Java 코딩 규약

계획서·예시 스니펫에도 동일하게 적용한다.

## FQCN 금지 — import 후 simple name
`new java.util.ArrayList<>()`·`java.util.List<String>` 같은 완전한정명 직접 사용을 금지한다. 파일 상단에 `import` 를 선언하고 `new ArrayList<>()`·`List<String>` 으로 쓴다.

- 예외 1 — 같은 파일에서 동명의 두 클래스를 써야 하는 Java 언어 제약 상황. 한쪽만 FQCN 으로 둔다.
- 예외 2 — `{@link}`·`{@code}` 등 **Javadoc 참조만을 위해** import 가 필요해지는 경우(코드 본문에서 그 타입을 쓰지 않음). import 를 추가하지 말고 Javadoc 안에서만 FQCN 을 허용한다. 코드 본문에서 이미 그 타입을 쓴다면 Javadoc 도 simple name 으로 쓴다.


## `this.` 의무 사용
인스턴스 필드·메서드를 참조할 때 항상 `this.` 를 붙인다 (`name` → `this.name`, `calculate()` → `this.calculate()`). static 멤버에는 붙이지 않는다.

생성자·setter 의 이름 충돌 해소뿐 아니라 **모든 인스턴스 멤버 접근**에 적용된다. 기존 파일이 `this.` 를 생략하고 있어도 새로 쓰거나 고치는 라인은 규칙을 따른다.

**왜** — 로컬 변수·파라미터·static 멤버와 인스턴스 멤버를 한눈에 가르기 위해서다. 누락되면 읽는 사람이 선언 위치를 되짚어야 한다.

## 중괄호는 생략하지 않는다
`if`·`else`·`for`·`while`·`do` 본문이 한 줄이어도 중괄호를 쓴다 (`if (x) return;` 금지).

```text
if (value == null) {
    return this;
}

this.append(value);
```

- **블록을 닫은 `}` 다음에는 빈 줄을 둔다** — 다음 줄이 `return`·`break`·`throw` 여도 같다. 예외: `} else {`·`} catch`·`} finally`·do-while 의 `} while (…);` 는 지금처럼 같은 줄에 붙이고, 다음 줄이 `}`·`case`·`default` 이거나 `});`·`},` 처럼 식이 이어지는 경우.
- **switch** — case 가 전부 한 줄이면 중괄호 없이 `case A -> foo();` 로 쓴다. **case 하나라도 물리적으로 두 줄 이상**(줄바꿈된 단일 식 포함)이면 그 switch 의 모든 case 에 중괄호를 쓰고, switch 식은 `yield` 로 값을 낸다.

## 주석은 코드로 알 수 없는 이유만 남긴다
코드를 되읽는 주석(`// 저장한다`, `/** 사용자 이름 */`), 구획·단계 번호, 바로 위 주석과 겹치는 설명, 주석 처리한 코드는 쓰지 않는다. 남기는 것은 비자명한 이유·제약·불변식·순서 의존, 공개 API 계약, 서버·클라·play-core 미러 관계, 억제 사유다.

- **변경 이력을 적지 않는다** — 날짜·QA 회차·"실측"·"예전에는"·이슈/작업 번호(`BL-015`, `S-06`) 같은 경위는 git 이 맡는다. 이유가 그 사고에서 나왔으면 현재형 제약으로만 쓴다(`정상 입력이 400 으로 거절된다(2026-09-10 실측)` → `CPU 가 포화되면 정상 입력이 400 으로 거절된다`).
- **Javadoc 바로 뒤에 `//` 를 덧붙이지 않는다** — 설명은 그 Javadoc 하나에 합친다.

## `var` 금지
로컬 변수 타입 추론(`var`)을 쓰지 않는다. 명시적 타입을 쓴다.

## labeled break / continue 금지
`outer: while (...) { ... continue outer; }` 같은 구문을 쓰지 않는다 (goto 로 간주).

## Lombok 이 있으면 보일러플레이트는 Lombok 으로
Lombok 이 빌드 의존성에 든 모듈에서는 getter/setter·생성자·builder·toString·equals/hashCode·logger 를 손으로 쓰지 않는다.

- immutable value object 는 record 를 먼저 검토한다 (Lombok 으로 강제하지 않는다).
- `@EqualsAndHashCode` 를 JPA 엔티티에 무비판적으로 붙이지 않는다 — lazy proxy·id 충돌을 부른다.
- **setter 가 있으면 안 되는 상태 보호 객체**(`ApiClient.currentGameId`, `DefinedColor` 공유 인스턴스, `FieldState` 식별자 필드 등)에는 `@Setter` 를 부활시키지 않는다. 규칙이 충돌하면 각 저장소 `AGENTS.md` 가 우선한다.
- 기존 코드에 손으로 쓴 보일러플레이트가 보여도 본 작업과 무관하면 대량 치환하지 말고 같은 plan/PR 범위 안에서만 정리한다.

## null 표기는 JSpecify 하나로

null 가능 표기는 `org.jspecify.annotations.Nullable` 만 쓴다(`jakarta.annotation.Nullable`·`org.jetbrains.annotations.Nullable` 금지). 패키지는 `package-info.java` 의 `@NullMarked` 로 "기본 non-null" 을 선언하고, NullAway 가 그 패키지만 ERROR 로 검사한다 — `@NullMarked` 는 하위 패키지에 상속되지 않고, main 의 표시는 같은 패키지 테스트에도 적용된다.

- **TYPE_USE 위치**: `@Nullable Outer.Inner` 는 컴파일 오류 → `Outer.@Nullable Inner`. 배열 자체가 null 가능이면 `byte @Nullable []`(`@Nullable byte[]` 는 "원소가 null" 이다).
- **`jakarta.validation.constraints.NotNull` 은 null 표기가 아니라 Bean Validation 이다** — 요청 검증(400)용으로만 쓴다.
- Minecraft·NeoForge 오버라이드와 Mixin 대상의 null 허용 계약을 우선 보존한다. 엔진이 소유한 Level은 빌려 쓰는 객체이므로 close하지 않는다.
