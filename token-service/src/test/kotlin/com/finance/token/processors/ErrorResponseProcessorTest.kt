package com.finance.token.processors

import com.finance.token.model.response.TokenResponse
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
        //given
        val ex = DefaultExchange(ctx)
        ex.message.body = TokenResponse(
            accessToken = null,
            error = "invalid_grant",
            errorDescription = "Wrong code"
        )
        //when
        p.process(ex)
        //then
        val body = ex.message.body as Map<*, *>
        assertEquals("Wrong code", body["error"])
        assertEquals("application/json", ex.message.getHeader(Exchange.CONTENT_TYPE))
        assertEquals(true, ex.getProperty("kc_error"))
        assertNull(ex.message.getHeader("CamelHttpResponseCode"))
    }

    @Test
    fun `passes through and sets accessToken property when no error`() {
        //given
        val ex = DefaultExchange(ctx)
        val token = TokenResponse(
            accessToken = "at-123",
            error = null,
            errorDescription = null
        )
        ex.message.body = token
        //when
        p.process(ex)
        //then
        assertEquals("at-123", ex.getProperty("accessToken", String::class.java))
        assertNull(ex.getProperty("kc_error"))
        assertSame(token, ex.message.body)
        assertNull(ex.message.getHeader("CamelHttpResponseCode"))
    }
}
