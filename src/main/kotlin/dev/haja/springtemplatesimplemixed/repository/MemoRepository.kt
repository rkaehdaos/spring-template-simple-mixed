package dev.haja.springtemplatesimplemixed.repository

import dev.haja.springtemplatesimplemixed.domain.Memo
import org.springframework.data.jpa.repository.JpaRepository

interface MemoRepository : JpaRepository<Memo, Long>
