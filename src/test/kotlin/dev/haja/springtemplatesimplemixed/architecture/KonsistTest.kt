package dev.haja.springtemplatesimplemixed.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withAnnotationOf
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledInNativeImage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.RestController

/**
 * [역할 분담] Konsist: 코틀린 소스 레벨 컨벤션 규칙 (구조 규칙은 ArchitectureTest.kt = ArchUnit 담당)
 *
 * 주의:
 * - Konsist 는 소스 파일을 직접 파싱하므로 프로젝트 루트에서 실행되어야 한다 (Gradle test 기본 동작 OK).
 * - assertTrue/assertFalse 는 선언 리스트가 비어 있으면 예외를 던진다. 샘플 코드(Memo, MemoService,
 *   MemoController 등)가 존재하므로 문제없다. 샘플 삭제 시 해당 규칙도 함께 정리할 것.
 */
@DisabledInNativeImage // Konsist 는 네이티브 이미지에서 동작 불가 (파일 파싱/리플렉션)
class KonsistTest {

    // scope 생성은 파일 시스템 전체를 파싱하므로 필드로 한 번만 초기화해 공유한다.
    private val production = Konsist.scopeFromProduction()
    private val project = Konsist.scopeFromProject()
    private val test = Konsist.scopeFromTest()

    @Test
    fun `@Service 클래스는 이름이 Service 로 끝난다`() {
        production.classes()
            .withAnnotationOf(Service::class)
            .assertTrue { it.name.endsWith("Service") }
    }

    @Test
    fun `@RestController 클래스는 이름이 Controller 로 끝난다`() {
        production.classes()
            .withAnnotationOf(RestController::class)
            .assertTrue { it.name.endsWith("Controller") }
    }

    @Test
    fun `JpaRepository 상속 인터페이스는 이름이 Repository 로 끝난다`() {
        production.interfaces()
            .filter { it.hasParentOf(JpaRepository::class, indirectParents = true) }
            .assertTrue { it.name.endsWith("Repository") }
    }

    @Test
    fun `패키지 선언은 디렉터리 경로와 일치한다`() {
        project
            .packages
            .assertTrue { it.hasMatchingPath }
    }

    @Test
    fun `필드 주입 금지 - @Autowired 프로퍼티 없음 (생성자 주입만 허용)`() {
        project
            .properties()
            .assertFalse { it.hasAnnotationOf(Autowired::class) }
    }

    @Test
    fun `DTO(Request-Response 접미사)는 data class 로 작성한다`() {
        production.classes()
            .withNameEndingWith("Request", "Response")
            .assertTrue { it.hasDataModifier }
    }

    @Test
    fun `와일드카드 import 금지`() {
        project
            .files
            .flatMap { it.imports }
            .assertFalse { it.isWildcard }
    }

    @Test
    fun `테스트 클래스 이름은 Test 또는 Tests 로 끝난다`() {
        // Kotest 스펙은 `@Test`를 쓰지 않으므로 이 규칙에 걸리지 않는다.
        test
            .classes()
            .filter { cls -> cls.functions().any { it.hasAnnotationOf(Test::class) } }
            .assertTrue { it.name.endsWith("Test") || it.name.endsWith("Tests") }
    }
}
