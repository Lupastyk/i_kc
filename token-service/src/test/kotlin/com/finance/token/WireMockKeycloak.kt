package com.finance.token

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import java.util.Base64

class WireMockKeycloak : QuarkusTestResourceLifecycleManager {
    private lateinit var server: WireMockServer

    override fun start(): MutableMap<String, String> {
        server = WireMockServer(WireMockConfiguration.options().dynamicPort()).also { it.start() }

        val header = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"none"}""".toByteArray())
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"sub":"u123","preferred_username":"john","email":"john@example.com","iss":"http://issuer","exp":9999999999}""".toByteArray()
        )
        val jwt = "$header.$payload."

        server.stubFor(
            post(urlPathEqualTo("/realms/finance-app/protocol/openid-connect/token"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"access_token":"$jwt"}""")
                        .withStatus(200)
                )
        )

        return mutableMapOf(
            "keycloak.tokenUrl" to "http://localhost:${server.port()}/realms/finance-app/protocol/openid-connect/token",
            "kafka.enabled" to "false"
        )
    }

    override fun stop() { server.stop() }
}
