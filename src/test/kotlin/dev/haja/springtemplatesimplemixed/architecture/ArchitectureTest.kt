package dev.haja.springtemplatesimplemixed.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import jakarta.persistence.Entity
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledInNativeImage
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.RestController

/**
 * [역할 분담]
 * ArchUnit : 바이트코드 레벨 — 계층 의존 방향, 순환 금지, 어노테이션 위치 등 "구조" 규칙
 * Konsist  : 코틀린 소스 레벨 — 네이밍, 패키지-경로 일치, data class, 생성자 주입 등 "컨벤션" 규칙
 * (KonsistTest.kt 참조 — 두 도구 간 규칙 중복 금지)
 *
 * [네이티브 이미지 미지원]
 * ArchUnit 은 클래스패스의 .class 바이트코드를 런타임에 읽어 분석하므로 네이티브 이미지에서 동작 불가하다
 * (네이티브 이미지에는 .class 파일이 존재하지 않음). 따라서 @DisabledInNativeImage 로 제외한다.
 * ArchUnit 전용 엔진(@AnalyzeClasses/@ArchTest)은 Jupiter 의 조건부 실행을 평가하지 않으므로,
 * 일반 JUnit Jupiter @Test + ArchUnit core API 방식으로 작성하여 @DisabledInNativeImage 가 적용되게 한다.
 */
@DisabledInNativeImage
class ArchitectureTest {

    // 분석 대상 클래스는 한 번만 임포트하여 각 테스트에서 재사용 (테스트 클래스 제외)
    private val importedClasses: JavaClasses = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("dev.haja.springtemplatesimplemixed")

    // 계층 규칙: controller → service → repository → domain (단방향)
    // consideringOnlyDependenciesInLayers() 로 stdlib/프레임워크 의존은 무시
    // 참고: "controller 가 repository 직접 접근 금지" 는 아래 Repository 규칙에 포함됨 (중복 규칙 없음)
    @Test
    fun `계층 의존은 단방향이다`() {
        layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Controller").definedBy("..controller..")
            .layer("Service").definedBy("..service..")
            .layer("Repository").definedBy("..repository..")
            .layer("Domain").definedBy("..domain..")
            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
            .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Controller", "Service", "Repository")
            .check(importedClasses)
    }

    // 최상위 하위 패키지 간 순환 의존 금지
    @Test
    fun `패키지 간 순환 의존이 없다`() {
        slices()
            .matching("dev.haja.springtemplatesimplemixed.(*)..")
            .should().beFreeOfCycles()
            .check(importedClasses)
    }

    @Test
    fun `@Service 는 service 패키지에 위치한다`() {
        classes()
            .that().areAnnotatedWith(Service::class.java)
            .should().resideInAPackage("..service..")
            .check(importedClasses)
    }

    @Test
    fun `@RestController 는 controller 패키지에 위치한다`() {
        classes()
            .that().areAnnotatedWith(RestController::class.java)
            .should().resideInAPackage("..controller..")
            .check(importedClasses)
    }

    @Test
    fun `@Entity 는 domain 패키지에 위치한다`() {
        classes()
            .that().areAnnotatedWith(Entity::class.java)
            .should().resideInAPackage("..domain..")
            .check(importedClasses)
    }

    // 도메인은 순수하게: 다른 계층 및 Spring Web/스테레오타입에 의존 금지
    @Test
    fun `domain 은 다른 계층과 Spring Web 에 의존하지 않는다`() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..controller..", "..service..", "..repository..",
                "org.springframework.web..", "org.springframework.stereotype..",
            )
            .check(importedClasses)
    }
}
