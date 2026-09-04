package dev.haja.springtemplatesimplemixed.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

/**
 * 샘플 도메인 엔티티.
 * - allOpen 플러그인이 @Entity 클래스를 open 처리 (build.gradle.kts 참조)
 * - kotlin("plugin.jpa")가 no-arg 생성자 생성
 * - 도메인 계층은 다른 계층/Spring Web 에 의존하지 않는다 (ArchitectureTest 에서 검증)
 */
@Entity
class Memo(
    @Column(nullable = false)
    var title: String,

    @Column(nullable = false)
    var content: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
