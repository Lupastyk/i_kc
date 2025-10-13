package com.finance.token

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.finance.token.model.UserAndTransactions
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.eclipse.microprofile.config.ConfigProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.*

@QuarkusTest
@QuarkusTestResource(WireMockKeycloak::class)
@QuarkusTestResource(WireMockTransactions::class)
@QuarkusTestResource(KafkaResource::class)
class KafkaPublishTest {

    @Test
    fun `flow publishes user+transactions with userId`() {
        val cfg = ConfigProvider.getConfig()
        val bootstrap = cfg.getValue("kafka.bootstrap.servers", String::class.java)
        val topic = cfg.getValue("kafka.topic.user-transactions", String::class.java)

        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap)
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID())
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        }

        KafkaConsumer<String, String>(props).use { consumer ->
            consumer.subscribe(listOf(topic))

            RestAssured.given()
                .queryParam("code", "AUTH_CODE")
                .`when`()
                .get("/token")
                .then()
                .statusCode(200)

            val records = consumer.poll(Duration.ofSeconds(10))
            assertFalse(records.isEmpty(), "expected at least one Kafka record")

            val mapper = jacksonObjectMapper()
            val events: List<UserAndTransactions> = records.map { mapper.readValue<UserAndTransactions>(it.value()) }

            val anyGood = events.any { ev ->
                ev.userId == "user123" && ev.transactions.isNotEmpty() && ev.user.email?.isNotBlank() == true
            }
            assertTrue(anyGood, "expected event with userId=user123, user and transactions present")

            val first = events.first()
            assertEquals("user123", first.userId)
            assertTrue(first.transactions.isNotEmpty())
            assertTrue(first.user.email?.isNotBlank() == true)
        }
    }
}
