package com.finance.token.processors

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@ApplicationScoped
@Named("authCodeProcessor")
class AuthCodeProcessor @Inject constructor(
) : Processor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun process(exchange: Exchange) {
        val codeFromHeader = exchange.message.getHeader("code", String::class.java)
        val rawQuery = exchange.message.getHeader(Exchange.HTTP_QUERY, String::class.java)
        val fromQuery = rawQuery
            ?.split('&')
            ?.mapNotNull {
                val pair = it.split('=', limit = 2)
                if (pair.size == 2) pair[0] to pair[1] else null
            }
            ?.firstOrNull { it.first == "code" }
            ?.second
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }

        val code = (codeFromHeader ?: fromQuery)?.trim().orEmpty()
        exchange.setProperty("authCode", code)
        log.info("Received /token with code={}", if (code.isBlank()) "<empty>" else "***")
    }
}
