package com.meteocool.notifications

import com.google.gson.JsonParser

object NotificationCoverage {
    fun outside(body: String): Boolean? { return try {
        val domain = JsonParser.parseString(body).asJsonObject.get("domain") ?: return null
        when {
            domain.isJsonNull -> true
            domain.isJsonPrimitive && domain.asJsonPrimitive.isString && domain.asString.isNotBlank() -> false
            else -> null
        }
    } catch (_: Exception) { null } }
}
