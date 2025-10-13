apply(plugin = "org.jetbrains.kotlin.plugin.allopen")

dependencies {
    implementation("io.quarkus:quarkus-resteasy-reactive-jackson")
    implementation("io.quarkus:quarkus-resteasy-reactive")

    // OIDC for token validation
    implementation("io.quarkus:quarkus-oidc")
    implementation("io.quarkus:quarkus-security")
}

allOpen {
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("io.quarkus.test.junit.QuarkusTest")
}