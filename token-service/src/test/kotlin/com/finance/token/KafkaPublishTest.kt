package com.finance.token

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.finance.token.mocks.WireMockKeycloak
import com.finance.token.mocks.WireMockTransactions
import com.finance.token.model.UserAndTransactionsPayload
import com.finance.token.profiles.KafkaFlowProfile
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured.given
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.eclipse.microprofile.config.ConfigProvider
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.time.Duration
import java.util.*

@Execution(ExecutionMode.SAME_THREAD)
@QuarkusTest
@TestProfile(KafkaFlowProfile::class)
@QuarkusTestResource(WireMockKeycloak::class)
@QuarkusTestResource(WireMockTransactions::class)
@QuarkusTestResource(KafkaResource::class)
class KafkaPublishTest {

    @Test
    fun `kafka payload contains userId user and transactions`() {
        //given
        given()
            .queryParam("code", "AUTH_CODE")
            .`when`()
            .get("/token")
            .then()
            .statusCode(200)

        val cfg = ConfigProvider.getConfig()
        val bootstrap = cfg.getValue("kafka.bootstrap.servers", String::class.java)
        val topic = cfg.getValue("kafka.topic.user-transactions", String::class.java)

        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap)
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID())
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        }

        val mapper = jacksonObjectMapper()
        var parsed: List<UserAndTransactionsPayload> = emptyList()

        //when
        KafkaConsumer<String, String>(props).use { consumer ->
            consumer.subscribe(listOf(topic))
            val deadline = System.currentTimeMillis() + 8000
            while (System.currentTimeMillis() < deadline && parsed.isEmpty()) {
                val records = consumer.poll(Duration.ofMillis(400))
                if (!records.isEmpty) {
                    parsed = records.map { rec -> mapper.readValue<UserAndTransactionsPayload>(rec.value()) }
                }
            }
        }

        //then
        assertTrue(parsed.isNotEmpty(), "No messages parsed from Kafka topic '$topic'")

        val first = parsed.first()
        assertTrue(!first.userId.isNullOrBlank(), "userId must be present in kafka payload")
        assertTrue(first.transactions.isNotEmpty(), "transactions list must not be empty")
        assertTrue(!first.user.email.isNullOrBlank(), "user.email must be present")
    }
}
