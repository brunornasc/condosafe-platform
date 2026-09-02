package com.condosafe.plugins.cryptobridge

import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@CapacitorPlugin(name = "CryptoBridge")
class CryptoBridgePlugin : Plugin() {

    @PluginMethod
    fun generateQrPayload(call: PluginCall) {
        val subjectId = call.getString("subjectId")
        val unitId = call.getString("unitId")
        val secretKey = call.getString("secretKey")
        val timestamp = call.getLong("timestamp") ?: Instant.now().epochSecond

        if (subjectId.isNullOrEmpty() || unitId.isNullOrEmpty() || secretKey.isNullOrEmpty()) {
            call.reject("Os parâmetros 'subjectId', 'unitId' e 'secretKey' são obrigatórios.")
            return
        }

        try {
            val jti = UUID.randomUUID().toString()
            val rawData = "$subjectId:$unitId:$timestamp:$jti"
            val signature = calculateHmacSha256(rawData, secretKey)

            val ret = JSObject().apply {
                put("sub", subjectId)
                put("unt", unitId)
                put("tms", timestamp)
                put("jti", jti)
                put("sig", signature)

                val rawJson = JSObject().apply {
                    put("sub", subjectId)
                    put("unt", unitId)
                    put("tms", timestamp)
                    put("jti", jti)
                    put("sig", signature)
                }.toString()

                put("rawJson", rawJson)
            }

            call.resolve(ret)
        } catch (e: Exception) {
            call.reject("Erro ao gerar hash HMAC-SHA256 nativo: ${e.localizedMessage}", e)
        }
    }

    private fun calculateHmacSha256(data: String, key: String): String {
        val algorithm = "HmacSHA256"
        val secretKeySpec = SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), algorithm)
        val mac = Mac.getInstance(algorithm)
        mac.init(secretKeySpec)
        val hmacBytes = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))

        return hmacBytes.joinToString("") { "%02x".format(it) }
    }
}