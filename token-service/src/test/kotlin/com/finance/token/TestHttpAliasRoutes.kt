package com.finance.token

import io.quarkus.arc.profile.IfBuildProfile
import jakarta.enterprise.context.ApplicationScoped
import org.apache.camel.builder.RouteBuilder

//@IfBuildProfile("test")
//@ApplicationScoped
class TestHttpAliasRoutes : RouteBuilder() {
    override fun configure() {
        from("platform-http:/token?httpMethodRestrict=GET")
            .routeId("token-plain-test")
            .to("direct:tokenFlow")

        from("platform-http:/api/health?httpMethodRestrict=GET")
            .routeId("health-plain-test")
            .to("direct:health")
    }
}