package com.finance.token.mocks

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager

class WireMockTransactions : QuarkusTestResourceLifecycleManager {

    private lateinit var wm: WireMockServer

    override fun start(): MutableMap<String, String> {
        wm = WireMockServer(options().dynamicPort())
        wm.start()
        configureFor("localhost", wm.port())

        stubFor(
            get(urlPathEqualTo("/api/transactions"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                              "transactions": [
                                { "id": "t1", "amount": 10.50, "currency": "EUR" },
                                { "id": "t2", "amount":  5.00, "currency": "EUR" }
                              ]
                            }
                            """.trimIndent()
                        )
                )
        )

        stubFor(
            get(urlPathEqualTo("/transactions"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            { "transactions": [ { "id": "t0", "amount": 1.00, "currency": "EUR" } ] }
                            """.trimIndent()
                        )
                )
        )
        return mutableMapOf(
            "transactions.base-url" to "http://localhost:${wm.port()}",
            "transactions.baseUrl" to "http://localhost:${wm.port()}"
        )
    }

    override fun stop() {
        if (this::wm.isInitialized) wm.stop()
    }

    override fun inject(testInstance: Any?) {}
}
