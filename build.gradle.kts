plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.graalvm.native)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kover)
    alias(libs.plugins.sonarqube)
    pmd  // Gradle 내장 core 플러그인 — 버전 표기 불필요
}

group = "dev.haja"
version = "0.0.1-SNAPSHOT"
description = "spring-template-simple-mixed"

// PMD: Java 소스 정적분석. 현재 Kotlin 전용이라 pmdMain은 NO-SOURCE로 스킵되며,
// 향후 Java 소스가 추가되면 자동으로 룰이 적용된다.
pmd {
    toolVersion = libs.versions.pmd.get()           // 버전 카탈로그로 고정 — Gradle 내장 기본값 대신 최신 PMD 사용
    ruleSetFiles = files(".github/pmd/ruleset.xml")
    ruleSets = listOf()                             // 기본 룰셋(errorprone) 비활성화 명시
    sourceSets = listOf(project.sourceSets["main"]) // test/aot/aotTest 제외 — main만 check에 연결
    isConsoleOutput = true
}

// Kover: 코틀린 코드 커버리지 — XML(Sonar 연동)/HTML 리포트 + 최소 기준 검증(check에 자동 연결)
kover {
    reports {
        filters {
            includes {
                classes("dev.haja.springtemplatesimplemixed.*")
            }
            excludes {
                // 부트스트랩 클래스는 커버리지 대상에서 제외
                classes("dev.haja.springtemplatesimplemixed.SpringTemplateSimpleMixedApplication*")
                // Spring AOT 생성 클래스(…__BeanDefinitions, …__TestContext*, …__AotRepository 등) 제외
                classes("*__*")
            }
        }
        verify {
            rule {
                minBound(30) // 라인 커버리지 30% 미만이면 check/build 실패 (현재 30.8% — 테스트 보강 시 상향)
            }
        }
    }
}

sonar {
    properties {
        // -PsonarProjectKey / -PsonarOrganization 으로 오버라이드 가능
        property("sonar.projectKey", providers.gradleProperty("sonarProjectKey")
            .getOrElse("rkaehdaos_spring-template-simple-mixed"))
        property("sonar.organization", providers.gradleProperty("sonarOrganization")
            .getOrElse("rkaehdaos"))
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.coverage.jacoco.xmlReportPaths",
            layout.buildDirectory.file("reports/kover/report.xml").get().asFile.path)
    }
}

// sonar 분석 전에 Kover XML 리포트 생성 보장
tasks.named("sonar") {
    dependsOn(tasks.named("koverXmlReport"))
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.h2console)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)
    developmentOnly(libs.spring.boot.devtools)
    runtimeOnly(libs.h2)
    annotationProcessor(libs.spring.boot.configuration.processor)
    testImplementation(libs.spring.boot.starter.data.jpa.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.kotlin.test.junit5)
    // 아키텍처 테스트: ArchUnit(바이트코드 구조 규칙) + Konsist(코틀린 소스 컨벤션 규칙)
    testImplementation(libs.archunit)
    testImplementation(libs.konsist)
    // Kotest: 코틀린 친화적 테스트 프레임워크(스펙 스타일 + 매처)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testRuntimeOnly(libs.junit.platform.launcher)

    // ## Java
    // Lombok - Java → Kotlin 마이그레이션 시 전체 제거
    // NOTE: MapStruct와 함께 사용 시 Lombok이 먼저 처리되어야 함 (순서 중요)
    compileOnly("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}


allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
// 일반 Java 컴파일에서는 경고 활성화
tasks.named("compileJava", JavaCompile::class) {
    options.compilerArgs.add("-Xlint:unchecked")
}

// AOT 컴파일 태스크에서는 생성된 코드의 경고 완전 제거
tasks.named("compileAotJava", JavaCompile::class) {
    options.compilerArgs.addAll(listOf(
        "-Xlint:none"  // 모든 경고 완전 제거
    ))
}

// NOTE: processTestAot는 활성화 유지.
// 네이티브 테스트(nativeTest)에서 Spring TestContext 프레임워크가 동작하려면
// 테스트 AOT가 생성하는 리플렉션/리소스 메타데이터가 필요하다.
// (비활성화 시 BootstrapUtils 초기화 실패 → WebAppConfiguration ClassNotFoundException)


// GraalVM 네이티브 이미지: Hibernate ByteBuddy BytecodeProvider 서비스 디스크립터를 이미지에서 제외.
// 최신 GraalVM(JDK 25)은 서비스 디스크립터 리소스를 무조건 이미지에 포함하는데, spring-orm은
// ServiceLoaderFeature 등록만 배제하므로 런타임 ServiceLoader가 디스크립터는 읽되 클래스는 못 찾아
// "BytecodeProviderImpl not found"로 JPA 컨텍스트 로드가 실패한다(spring-framework#35118).
// 리소스 자체를 제외하면 ServiceLoader 결과가 비고, Hibernate 7.x가 no-op(none) BytecodeProvider로
// 폴백한다(BytecodeProviderInitiator.getBytecodeProvider: 빈 iterator → new none.BytecodeProviderImpl).
// 네이티브 런타임은 런타임 바이트코드 생성이 불가하므로 none provider가 정상 경로다.
graalvmNative {
    binaries.all {
        buildArgs.add("-H:ExcludeResources=META-INF/services/org\\.hibernate\\.bytecode\\.spi\\.BytecodeProvider")

        // Konsist 가 끌어오는 kotlin-compiler-embeddable jar 에는 jline native-image.properties 가
        // 번들돼 있으나, 그 properties 가 참조하는 reflection/resource-config.json 은 shading 시 누락돼 있다.
        // native-image 가 클래스패스의 native-image.properties 를 자동 로드하다
        // "Could not find reflection configuration resource ...jline-terminal/reflection-config.json" 으로
        // nativeTestCompile 초기화 단계에서 실패하므로, 해당 jar 의 내장 네이티브 설정을 통째로 무시한다.
        // (ArchitectureTest/KonsistTest 자체는 @DisabledInNativeImage 로 이미 네이티브 실행에서 제외됨)
        buildArgs.add("--exclude-config")
        buildArgs.add(".*kotlin-compiler-embeddable.*\\.jar")
        buildArgs.add("META-INF/native-image/.*")
    }
}
