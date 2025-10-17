package com.finance.token.profiles

import io.quarkus.test.junit.QuarkusTestProfile

class KafkaFlowProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): MutableMap<String, String> = mutableMapOf(
        "kafka.enabled" to "true",
        "kafka.topic.user-transactions" to "user-transactions-test",

        "quarkus.arc.exclude-types" to listOf(
            "com.finance.token.routes.HealthRoute",
            "com.finance.token.TestHttpAliasRoutes"
        ).joinToString(",")
    )
}
