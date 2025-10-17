package com.finance.token.routes

import com.finance.token.model.HealthResponse
import com.finance.token.model.HealthStatus
import jakarta.enterprise.context.ApplicationScoped
import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.model.dataformat.JsonLibrary
import org.slf4j.LoggerFactory

@ApplicationScoped
open class HealthRoute(
) : RouteBuilder() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun configure() {
        rest("/api")
            .get("health")
            .produces("application/json")
            .to("direct:health")

        from("direct:health")
            .setHeader(Exchange.CONTENT_TYPE).constant("application/json")
            .setBody().constant(HealthResponse(HealthStatus.UP))
            .marshal().json(JsonLibrary.Jackson)
    }

}
