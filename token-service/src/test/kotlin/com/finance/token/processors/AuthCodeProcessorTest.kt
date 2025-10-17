package com.finance.token.processors

import org.apache.camel.Exchange
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.support.DefaultExchange
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AuthCodeProcessorTest {

    private val ctx = DefaultCamelContext()
    private val p = AuthCodeProcessor()

    @Test
    fun `extracts code from header`() {
        val ex = DefaultExchange(ctx)
        ex.message.setHeader("code", "abc-123")

        p.process(ex)

        assertEquals("abc-123", ex.getProperty("authCode", String::class.java))
        // процессор не ставит HTTP-код
        assertNull(ex.message.getHeader("CamelHttpResponseCode"))
    }

    @Test
    fun `extracts and decodes code from query`() {
        val ex = DefaultExchange(ctx)
        ex.message.setHeader(Exchange.HTTP_QUERY, "state=xyz&code=a%2Bb%3D1")

        p.process(ex)

        assertEquals("a+b=1", ex.getProperty("authCode", String::class.java))
        assertNull(ex.message.getHeader("CamelHttpResponseCode"))
    }

    @Test
    fun `when authCode is missing sets empty and does not set http code`() {
        val ex = DefaultExchange(ctx)

        p.process(ex)

        assertEquals("", ex.getProperty("authCode", String::class.java))
        assertNull(ex.message.getHeader("CamelHttpResponseCode"))
    }
}
