# AGENTS.md

이 저장소에서 작업하는 AI 코딩 에이전트가 반드시 지켜야 할 규칙과 컨텍스트.

## 프로젝트 개요

Spring Boot **4.1.1** + Kotlin **2.4.10** + JDK **25(GraalVM)** 기반 프로젝트.

- 빌드: Gradle 9.7.1 (Kotlin DSL) + 버전 카탈로그 `gradle/libs.versions.toml`, 구성 캐시 활성화(`gradle.properties`)
- DB: H2 (in-memory) + Spring Data JPA
- 웹: Spring MVC (`spring-boot-starter-webmvc`)
- 네이티브 이미지: GraalVM Native Build Tools 지원
- 정적분석: PMD 7.27.0 (커스텀 룰셋 `.github/pmd/ruleset.xml`, 메서드 NCSS 30줄 제한, main 소스셋만)
- 커버리지: Kover (라인 30% 미만이면 check 실패) + SonarCloud 연동 (`.github/workflows/build.yml`, `SONAR_TOKEN` secret 필요)
- 샘플 도메인: `Memo` (최소 CRUD — 아키텍처 규칙 예시용)

## 필수 명령

```bash
mise install              # oracle-graalvm-25.0.3 설치 (mise.toml)
./gradlew build           # 컴파일 + 전체 테스트 + PMD
./gradlew test            # 테스트만
./gradlew koverHtmlReport # 커버리지 HTML 리포트 (build/reports/kover/html)
./gradlew sonar           # SonarCloud 분석 (SONAR_TOKEN 필요)
./gradlew bootRun         # 로컬 실행 (http://localhost:8080)
./gradlew nativeCompile   # (선택) 네이티브 실행 파일 빌드 — 장시간 소요
./gradlew nativeTest      # (선택) 네이티브 이미지 테스트
```

## 아키텍처 (ArchitectureTest.kt가 강제)

베이스 패키지: `dev.haja.springtemplatesimplemixed`

계층은 단방향 의존만 허용: `controller → service → repository → domain`

- `domain`은 순수 계층 — 다른 계층 및 Spring Web/stereotype 의존 금지
- 패키지 간 순환 의존 금지
- `@Service`/`@RestController`/`@Entity`는 각각 service/controller/domain 패키지에만 위치

## 코드 컨벤션 (KonsistTest.kt가 강제)

- **생성자 주입만** 사용 — `@Autowired` 필드/프로퍼티 주입 금지
- DTO는 `Request`/`Response` 접미사 + **data class**
- `@Service` → 이름 `*Service`, `@RestController` → `*Controller`, JpaRepository 상속 → `*Repository`
- 와일드카드 import 금지
- 패키지 선언은 디렉터리 경로와 일치
- 테스트 클래스 이름은 `Test`/`Tests`로 끝남 (Kotest 스펙 제외)

## 커밋 / PR 컨벤션

- 커밋 메시지는 `.gitmessage.txt` 규칙: `<이모지 type>(<scope>): <subject>` (예: `✨ feat(api): ...`), subject 50자 이내·소문자 시작·마침표 금지, body 72자/줄
- PR은 `.github/PULL_REQUEST_TEMPLATE.md` 준수

## 주의사항

- **Spring Boot 4.x 신규 명칭을 3.x 스타일로 "교정"하지 말 것**:
  - `spring-boot-starter-webmvc` (3.x의 `starter-web` 아님), `spring-boot-h2console`
  - `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`
  - `org.springframework.test.context.bean.override.mockito.MockitoBean`
  - Jackson 3: `tools.jackson.module:jackson-module-kotlin` (`com.fasterxml` 아님)
- 의존성 버전은 반드시 `gradle/libs.versions.toml` 버전 카탈로그로 관리
- `build.gradle.kts`의 GraalVM `buildArgs`와 주석은 네이티브 빌드 실패 회피용 — 임의 삭제 금지
- Mockito 기반 테스트(`@MockitoBean`/`@WebMvcTest` 등)는 런타임 바이트코드 생성이 필요해 네이티브 이미지(`nativeTest`)에서 동작 불가 → `@DisabledInNativeImage` 필수 (ArchUnit/Konsist 테스트도 동일)
- 샘플 `Memo` 도메인 삭제 시 KonsistTest 규칙도 함께 정리할 것 (Konsist `assertTrue`는 빈 리스트에서 예외 발생)
- `mise.toml`, `HELP.md`는 `.gitignore` 대상 (커밋되지 않는 것이 정상)
- 구성 캐시(`org.gradle.configuration-cache=true`)가 켜져 있다. 빌드 스크립트에서 **실행 시점에 `project`/`Task.project`를 참조하면 빌드가 실패**하므로, 값은 구성 시점에 `Provider`/`layout`/`providers`로 캡처할 것. 태스크 그래프가 다르면 캐시 엔트리도 분리되므로 `build koverXmlReport`와 `sonar`는 서로 재사용되지 않는다. 문제 진단은 `build/reports/configuration-cache/`의 HTML 리포트를, 일시 우회는 `--no-configuration-cache`를 사용
- CI에서 구성 캐시가 실제로 재사용되려면 `GRADLE_ENCRYPTION_KEY` secret이 필요하다(`setup-gradle`은 암호화 키 없이는 구성 캐시 데이터를 저장/복원하지 않음). 미설정이어도 빌드는 정상 동작하며 매 실행마다 구성 단계를 새로 계산할 뿐이다. 키 생성: `openssl rand -base64 16`
- `gradle/verification-metadata.xml`의 `<trusted-artifacts>`는 인텔리제이 sync 전용 아티팩트(sources jar, Gradle 임베디드 Kotlin의 `kotlin-reflect`) 검증 실패 방지용 — 임의 삭제 금지. 인텔리제이에서만 `Dependency verification failed`가 나면 검증을 끄지 말고 실패 로그의 아티팩트를 `<trust>` 항목으로 좁게 추가할 것
- **Gradle 래퍼 버전을 올릴 때는 `<trusted-artifacts>`의 `kotlin-reflect` `<trust>` 버전도 함께 갱신할 것.** 이 버전은 IDE가 아니라 **Gradle 임베디드 Kotlin**이며 `./gradlew --version | grep '^Kotlin:'`으로 확인한다(실측: Gradle 9.6.1→Kotlin 2.3.21, 9.7.1→2.4.0). 재생성 명령으로 자동 반영되지 않는 수동 단계이고 터미널 빌드는 그대로 통과하므로, 빠뜨리면 **인텔리제이 sync에서만** `kotlin-reflect-<버전>.pom` 검증 실패로 뒤늦게 드러난다. `version` 없이 `kotlin-reflect` 전체를 신뢰하는 방식은 실제 런타임 의존성 검증까지 무력화하므로 금지
- 의존성 추가/버전 변경 시 `./gradlew --write-verification-metadata sha256 --refresh-dependencies clean build koverXmlReport`로 `gradle/verification-metadata.xml`을 재생성할 것. `--refresh-dependencies`가 없으면 웜 캐시에 이미 있는 아티팩트(특히 플러그인 classpath의 BOM `.module`/`.pom`, kotlin build-tools 메타데이터)를 다시 내려받지 않아 체크섬이 누락되고, 콜드 캐시인 CI의 `configuration 'classpath'` 검증에서만 `Dependency verification failed`로 실패한다(터미널 로컬 빌드는 통과). 이 명령은 append-only라 구버전 항목이 남으므로 stale `<component>`를 수동 제거하고, 잔존 확인은 정규식 오탐(`.`이 sha256 hex에 매칭)을 피해 `grep -Fc '<구버전>"'`(0이어야 함)으로 할 것. **파일 전체 재생성 금지(네이티브 전용 아티팩트 유실), 구버전 `<component>`만 선택 삭제**. `<trusted-artifacts>` 블록은 보존 확인
