package com.finance.token

import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName

class KafkaResource : QuarkusTestResourceLifecycleManager {

    private lateinit var kafka: KafkaContainer

    override fun start(): MutableMap<String, String> {
        kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
        kafka.start()

        return mutableMapOf(
            "kafka.bootstrap.servers" to kafka.bootstrapServers,
            "kafka.enabled" to "true"
        )
    }

    override fun stop() {
        if (this::kafka.isInitialized) kafka.stop()
    }
}
