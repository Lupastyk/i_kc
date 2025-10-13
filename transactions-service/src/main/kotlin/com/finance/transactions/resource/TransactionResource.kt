package com.finance.transactions.resource

import com.finance.transactions.model.Transaction
import com.finance.transactions.service.TransactionService
import io.quarkus.security.Authenticated
import org.eclipse.microprofile.jwt.JsonWebToken
import org.slf4j.LoggerFactory
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/api/transactions")
@ApplicationScoped
@Authenticated  // Requires valid JWT token
class TransactionResource {

    companion object {
        private val logger = LoggerFactory.getLogger(TransactionResource::class.java)
    }

    @Inject
    lateinit var transactionService: TransactionService

    @Inject
    lateinit var jwt: JsonWebToken

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getTransactions(
        @QueryParam("limit") @DefaultValue("20") limit: Int
    ): Response {

        val userId = extractUserIdFromToken()

        logger.info("Fetching transactions for user: {}, limit: {}", userId, limit)

        return try {
            val transactions = transactionService.getTransactionsForUser(userId, limit)

            val response = mapOf(
                "userId" to userId,
                "transactions" to transactions,
                "count" to transactions.size,
                "tokenInfo" to mapOf(
                    "subject" to jwt.subject,
                    "issuer" to jwt.issuer,
                    "expiresAt" to jwt.expirationTime
                )
            )

            Response.ok(response).build()

        } catch (e: Exception) {
            logger.error("Error fetching transactions for user: {}", userId, e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to "Failed to fetch transactions"))
                .build()
        }
    }

    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    fun health(): Map<String, Any?> {
        return mapOf(
            "status" to "UP",
            "service" to "Transactions Service",
            "timestamp" to java.time.Instant.now().toString(),
            "authenticated" to (jwt.subject != null),
            "userInfo" to if (jwt.subject != null) mapOf(
                "subject" to jwt.subject,
                "preferredUsername" to (jwt.getClaim<String>("preferred_username") ?: "unknown"),
                "email" to (jwt.getClaim<String>("email") ?: "unknown")
            ) else null
        )
    }

    private fun extractUserIdFromToken(): String {
        // Try to get user ID from different possible claims
        return jwt.getClaim<String>("preferred_username")
            ?: jwt.getClaim<String>("sub")
            ?: jwt.subject
            ?: throw WebApplicationException("Unable to extract user ID from token", Response.Status.BAD_REQUEST)
    }
}