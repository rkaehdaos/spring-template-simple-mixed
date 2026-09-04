package dev.haja.springtemplatesimplemixed.service

import dev.haja.springtemplatesimplemixed.domain.Memo
import dev.haja.springtemplatesimplemixed.repository.MemoRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 존재하지 않는 메모 조회 시 발생 — GlobalExceptionHandler 에서 404 로 매핑 */
class MemoNotFoundException(id: Long) : RuntimeException("Memo not found: id=$id")

/** 생성자 주입만 사용 (필드 @Autowired 금지 — KonsistTest 에서 검증) */
@Service
@Transactional(readOnly = true)
class MemoService(
    private val memoRepository: MemoRepository,
) {
    fun findAll(): List<Memo> = memoRepository.findAll()

    fun findById(id: Long): Memo =
        memoRepository.findByIdOrNull(id)
            ?: throw MemoNotFoundException(id)

    @Transactional
    fun create(title: String, content: String): Memo =
        memoRepository.save(Memo(title = title, content = content))
}
