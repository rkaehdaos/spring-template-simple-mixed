package dev.haja.springtemplatesimplemixed.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * ArchitectureTest.kt 의 자바 버전 — 규칙 내용은 동일하며 자바 소스셋에서도 동일하게 강제된다.
 *
 * <p>[역할 분담]
 * ArchUnit : 바이트코드 레벨 — 계층 의존 방향, 순환 금지, 어노테이션 위치 등 "구조" 규칙
 * Konsist  : 코틀린 소스 레벨 — 네이밍, 패키지-경로 일치, data class, 생성자 주입 등 "컨벤션" 규칙
 * (KonsistTest.kt 참조 — 두 도구 간 규칙 중복 금지)
 *
 * <p>[네이티브 이미지 미지원]
 * ArchUnit 은 클래스패스의 .class 바이트코드를 런타임에 읽어 분석하므로 네이티브 이미지에서 동작 불가하다
 * (네이티브 이미지에는 .class 파일이 존재하지 않음). 따라서 @DisabledInNativeImage 로 제외한다.
 * ArchUnit 전용 엔진(@AnalyzeClasses/@ArchTest)은 Jupiter 의 조건부 실행을 평가하지 않으므로,
 * 일반 JUnit Jupiter @Test + ArchUnit core API 방식으로 작성하여 @DisabledInNativeImage 가 적용되게 한다.
 */
@DisabledInNativeImage
public class ArchUnitTest {

    // 분석 대상 클래스는 한 번만 임포트하여 각 테스트에서 재사용 (테스트 클래스 제외)
    private static final JavaClasses IMPORTED_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("dev.haja.springtemplatesimplemixed");

    // 계층 규칙: controller → service → repository → domain (단방향)
    // consideringOnlyDependenciesInLayers() 로 stdlib/프레임워크 의존은 무시
    // 참고: "controller 가 repository 직접 접근 금지" 는 아래 Repository 규칙에 포함됨 (중복 규칙 없음)
    @Test
    @DisplayName("계층 의존은 단방향이다")
    void layerDependenciesAreUnidirectional() {
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
                .check(IMPORTED_CLASSES);
    }

    // 최상위 하위 패키지 간 순환 의존 금지
    @Test
    @DisplayName("패키지 간 순환 의존이 없다")
    void packagesAreFreeOfCycles() {
        slices()
                .matching("dev.haja.springtemplatesimplemixed.(*)..")
                .should().beFreeOfCycles()
                .check(IMPORTED_CLASSES);
    }

    @Test
    @DisplayName("@Service 는 service 패키지에 위치한다")
    void serviceAnnotationResidesInServicePackage() {
        classes()
                .that().areAnnotatedWith(Service.class)
                .should().resideInAPackage("..service..")
                .check(IMPORTED_CLASSES);
    }

    @Test
    @DisplayName("@RestController 는 controller 패키지에 위치한다")
    void restControllerAnnotationResidesInControllerPackage() {
        classes()
                .that().areAnnotatedWith(RestController.class)
                .should().resideInAPackage("..controller..")
                .check(IMPORTED_CLASSES);
    }

    @Test
    @DisplayName("@Entity 는 domain 패키지에 위치한다")
    void entityAnnotationResidesInDomainPackage() {
        classes()
                .that().areAnnotatedWith(Entity.class)
                .should().resideInAPackage("..domain..")
                .check(IMPORTED_CLASSES);
    }

    @Test
    @DisplayName("도메인 계층은 애플리케이션 계층에 의존해서는 안된다.")
    void domainLayerDoesNotDependOnApplicationLayer() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat().resideInAPackage("..application..")
                .check(IMPORTED_CLASSES);
    }

    // 도메인은 순수하게: 다른 계층 및 Spring Web/스테레오타입에 의존 금지
    @Test
    @DisplayName("domain 은 다른 계층과 Spring Web 에 의존하지 않는다")
    void domainLayerDoesNotDependOnSpringWebOrOtherLayer() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..controller..",
                        "..service..",
                        "..repository..",
                        "org.springframework.web..",
                        "org.springframework.stereotype..")
                .check(IMPORTED_CLASSES);
    }
}
