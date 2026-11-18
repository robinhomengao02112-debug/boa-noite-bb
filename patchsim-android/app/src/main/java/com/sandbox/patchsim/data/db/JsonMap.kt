package com.sandbox.patchsim.data.db

import org.json.JSONObject

/** Minimal Map<String,String> JSON codec using the platform org.json (no extra deps). */
object JsonMap {
    fun encode(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    fun decode(json: String): Map<String, String> {
        if (json.isBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            buildMap {
                obj.keys().forEach { key -> put(key, obj.optString(key)) }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
