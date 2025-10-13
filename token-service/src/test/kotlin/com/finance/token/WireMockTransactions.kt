package com.finance.token

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager

class WireMockTransactions : QuarkusTestResourceLifecycleManager {
    private lateinit var server: WireMockServer

    override fun start(): MutableMap<String, String> {
        server = WireMockServer(WireMockConfiguration.options().dynamicPort())
        server.start()

        server.stubFor(
            get(urlPathEqualTo("/api/transactions"))
                .withHeader("Authorization", matching("Bearer .*"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"userId":"user123","transactions":[{"id":"t1","amount":10.0}],"count":1}""")
                        .withStatus(200)
                )
        )

        return mutableMapOf(
            "transactions.baseUrl" to "http://localhost:${server.port()}"
        )
    }

    override fun stop() {
        server.stop()
    }
}
