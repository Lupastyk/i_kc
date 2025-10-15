package com.finance.token

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.common.QuarkusTestResource
import io.restassured.RestAssured
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

@QuarkusTest
@QuarkusTestResource(WireMockKeycloak::class)
@QuarkusTestResource(WireMockTransactions::class)
@QuarkusTestResource(KafkaResource::class)
class TokenFlowClientAssertionIT {

    @Test
    fun `token endpoint works with client_assertion`() {
        val resp =
            RestAssured.given()
                .queryParam("code", "AUTH_CODE")
                .`when`()
                .get("/token")
                .then()
                .statusCode(200)
                .extract().asString()

        assertEquals(true, resp.contains("transactions"))
    }
}
