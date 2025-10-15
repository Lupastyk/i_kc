package com.finance.token

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager

class WireMockKeycloak : QuarkusTestResourceLifecycleManager {
    private lateinit var server: WireMockServer

    override fun start(): MutableMap<String, String> {
        server = WireMockServer(WireMockConfiguration.options().dynamicPort())
        server.start()

        val tokenPath = "/realms/finance-app/protocol/openid-connect/token"

        // Вариант 1: client_secret
        server.stubFor(
            post(urlPathEqualTo(tokenPath))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                .withRequestBody(containing("grant_type=authorization_code"))
                .withRequestBody(containing("code=AUTH_CODE"))
                .withRequestBody(containing("client_id=finance-client"))
                .withRequestBody(containing("redirect_uri=http%3A%2F%2Flocalhost%3A8081%2Ftoken"))
                // есть client_secret (порядок параметров неважен)
                .withRequestBody(matching(".*(?:^|&)client_secret=[^&=]+.*"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"access_token":"ey...sig"}""")
                )
        )

        // Вариант 2: private_key_jwt (оба параметра обязательны)
        server.stubFor(
            post(urlPathEqualTo(tokenPath))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                .withRequestBody(containing("grant_type=authorization_code"))
                .withRequestBody(containing("code=AUTH_CODE"))
                .withRequestBody(containing("client_id=finance-client"))
                .withRequestBody(containing("redirect_uri=http%3A%2F%2Flocalhost%3A8081%2Ftoken"))
                .withRequestBody(containing("client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer"))
                .withRequestBody(matching(".*(?:^|&)client_assertion=[^&=]+.*"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"access_token":"ey...sig"}""")
                )
        )

        return mutableMapOf(
            "keycloak.tokenUrl" to "http://localhost:${server.port()}$tokenPath"
        )
    }

    override fun stop() {
        if (this::server.isInitialized) server.stop()
    }
}
