package com.finance.token.config

import jakarta.enterprise.context.ApplicationScoped
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

@ApplicationScoped
class PemLoader {
    fun readPem(path: String): String {
        val cp = Thread.currentThread().contextClassLoader
            .getResourceAsStream(path)
            ?.bufferedReader(StandardCharsets.UTF_8)
            ?.use { it.readText() }
        if (cp != null) return cp

        return Files.readString(Paths.get(path), StandardCharsets.UTF_8)
    }
}
