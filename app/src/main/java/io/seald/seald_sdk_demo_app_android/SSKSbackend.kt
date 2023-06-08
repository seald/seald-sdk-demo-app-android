package io.seald.seald_sdk_demo_app_android

import android.util.Log
import io.seald.seald_sdk.AuthFactor
import kotlinx.coroutines.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class SSKSbackend(keyStorageURL: String, appId: String, appKey: String) {
    private val keyStorageURL: String
    private val appId: String
    private val appKey: String

    // Http client
    private val httpClient: OkHttpClient
    private val mediaType  = "application/json; charset=utf-8".toMediaType()

    init {
        this.keyStorageURL = keyStorageURL
        this.appId = appId
        this.appKey = appKey

        this.httpClient = OkHttpClient()
    }

    private fun post(endpoint: String, requestBody: RequestBody): String {
        val request = Request.Builder()
            .url(keyStorageURL + endpoint)
            .addHeader("X-SEALD-APPID", this.appId)
            .addHeader("X-SEALD-APIKEY", this.appKey)
            .post(requestBody)
            .build()

        println("SSKSbackend POST URL: ${keyStorageURL + endpoint}")
        val response = httpClient.newCall(request).execute()

        response.use {
            if (!response.isSuccessful) {
                println("HTTP response.code: ${response.code}")
                throw Error("Unexpected HTTP response: ${response.code}")
            }
            val responseBody = response.body?.string()
            println("Response body: $responseBody")
            return responseBody as String
        }

    }

    fun ChallengeSend(userId: String, authFactor: AuthFactor, createUser: Boolean, forceAuth: Boolean): Deferred<ChallengeSendResponse> = CoroutineScope(Dispatchers.Default).async {
        val jsonObject = """
    {
        "user_id": "$userId",
        "auth_factor": {
            "type": "${authFactor.type}",
            "value": "${authFactor.value}"
        },
        "create_user": "$createUser",
        "force_auth": "$forceAuth"
    }
""".trimIndent()

        val resp = post("tmr/back/challenge_send/", jsonObject.toRequestBody(mediaType))
        return@async Json.decodeFromString(resp)
    }
}

@Serializable
data class ChallengeSendResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("must_authenticate") val mustAuthenticate: Boolean,
)