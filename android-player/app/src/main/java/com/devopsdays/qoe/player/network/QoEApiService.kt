package com.devopsdays.qoe.player.network

import com.devopsdays.qoe.player.models.QoEMetricPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class QoEApiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "http://10.0.2.2:8080/api/v1" // Android emulator localhost

    suspend fun sendMetrics(payload: QoEMetricPayload) = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("platform", payload.platform)
            put("videoId", payload.videoId)
            put("sessionId", payload.sessionId)
            put("timestamp", payload.timestamp)
            put("deviceInfo", JSONObject(payload.deviceInfo))
            put("metrics", JSONObject(payload.metrics))
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/metrics")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to send metrics: ${response.code}")
            }
        }
    }
}
