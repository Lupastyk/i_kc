package com.finance.token

import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@QuarkusTest
@QuarkusTestResource(WireMockKeycloak::class)
@QuarkusTestResource(WireMockTransactions::class)
class TokenRoutesTest {

    @Test
    fun `end-to-end token flow returns transactions json`() {
        val body = given()
            .queryParam("code", "AUTH_CODE")
            .`when`()
            .get("/token")
            .then()
            .statusCode(200)
            .extract()
            .asString()

        assertTrue(body.contains("\"transactions\""), "transactions array expected")
        assertTrue(body.contains("\"userId\""), "userId expected")
    }
}
