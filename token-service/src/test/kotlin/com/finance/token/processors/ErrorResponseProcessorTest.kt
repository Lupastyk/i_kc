package com.finance.token.processors

import com.finance.token.model.TokenResponse
import org.apache.camel.Exchange
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.support.DefaultExchange
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ErrorResponseProcessorTest {

    private val ctx = DefaultCamelContext()
    private val p = ErrorResponseProcessor()

    @Test
    fun `returns json error and sets kc_error when token endpoint returned error`() {
        val ex = DefaultExchange(ctx)
        ex.message.body = TokenResponse(
            accessToken = null,
            error = "invalid_grant",
            errorDescription = "Wrong code"
        )

        p.process(ex)

        // тело заменено на json-ошибку
        val body = ex.message.body as Map<*, *>
        assertEquals("Wrong code", body["error"])

        // тип контента выставлен
        assertEquals("application/json", ex.message.getHeader(Exchange.CONTENT_TYPE))

        // маркер ошибки есть
        assertEquals(true, ex.getProperty("kc_error"))

        // HTTP код процессор НЕ ставит
        assertNull(ex.message.getHeader("CamelHttpResponseCode"))
    }

    @Test
    fun `passes through and sets accessToken property when no error`() {
        val ex = DefaultExchange(ctx)
        val token = TokenResponse(
            accessToken = "at-123",
            error = null,
            errorDescription = null
        )
        ex.message.body = token

        p.process(ex)

        // свойство установлено
        assertEquals("at-123", ex.getProperty("accessToken", String::class.java))

        // kc_error не выставлен
        assertNull(ex.getProperty("kc_error"))

        // тело не тронуто
        assertSame(token, ex.message.body)

        // HTTP код не трогаем
        assertNull(ex.message.getHeader("CamelHttpResponseCode"))
    }
}
