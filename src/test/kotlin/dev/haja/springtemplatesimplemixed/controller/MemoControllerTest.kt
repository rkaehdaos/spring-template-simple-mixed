package dev.haja.springtemplatesimplemixed.controller

import dev.haja.springtemplatesimplemixed.service.MemoNotFoundException
import dev.haja.springtemplatesimplemixed.service.MemoService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledInNativeImage
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@DisabledInNativeImage // Mockito(@MockitoBean)는 런타임 바이트코드 생성이 필요해 네이티브 이미지에서 동작 불가
@WebMvcTest(MemoController::class)
class MemoControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
) {
    @MockitoBean
    private lateinit var memoService: MemoService

    @Test
    fun `존재하지 않는 메모 조회는 404 ProblemDetail 응답`() {
        given(memoService.findById(999L))
            .willThrow(MemoNotFoundException(999))

        mockMvc.perform(get("/api/memos/999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("Memo not found: id=999"))
    }
}
