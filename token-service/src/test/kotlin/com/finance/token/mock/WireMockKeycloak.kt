package com.finance.token.mocks

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import java.nio.charset.StandardCharsets
import java.util.*

class WireMockKeycloak : QuarkusTestResourceLifecycleManager {

    private lateinit var server: WireMockServer

    override fun start(): MutableMap<String, String> {
        server = WireMockServer(options().dynamicPort())
        server.start()
        configureFor("localhost", server.port())

        val headerJson = """{"alg":"none","typ":"JWT"}"""
        val header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(headerJson.toByteArray(StandardCharsets.UTF_8))

        val payloadJson = """
          {
            "subject":"u1",
            "preferred_username":"",
            "email":"user@test.dev",
            "iss":"kc",
            "exp": 9999999999
          }
        """.trimIndent()
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payloadJson.toByteArray(StandardCharsets.UTF_8))

        val accessToken = "$header.$payload"

        stubFor(
            post(urlPathEqualTo("/realms/finance-app/protocol/openid-connect/token"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                              "access_token": "$accessToken",
                              "token_type": "Bearer",
                              "expires_in": 300
                            }
                            """.trimIndent()
                        )
                )
        )

        stubFor(
            get(urlPathEqualTo("/realms/finance-app/.well-known/openid-configuration"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                              "issuer": "http://localhost:${server.port()}/realms/finance-app",
                              "token_endpoint": "http://localhost:${server.port()}/realms/finance-app/protocol/openid-connect/token"
                            }
                            """.trimIndent()
                        )
                )
        )

        return mutableMapOf(
            "keycloak.tokenUrl" to "http://localhost:${server.port()}/realms/finance-app/protocol/openid-connect/token",
            "quarkus.oidc.auth-server-url" to "http://localhost:${server.port()}/realms/finance-app"
        )
    }

    override fun stop() {
        if (this::server.isInitialized) server.stop()
    }

    override fun inject(testInstance: Any?) {
    }
}
