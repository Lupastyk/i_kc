package com.finance.token.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class TokenResponse(
    @JsonProperty("access_token")
    val accessToken: String? = null,

    @JsonProperty("error")
    val error: String? = null,

    @JsonProperty("error_description")
    val errorDescription: String? = null
)