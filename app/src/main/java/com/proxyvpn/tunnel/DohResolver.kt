package com.proxyvpn.tunnel

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class DohResolver(private val endpointUrl: String = "https://dns.dns-ai.ru/dns-query") {

    companion object {
        var DEBUG = true // Отключаемое логирование
        private const val TAG = "DohResolver"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/dns-message".toMediaType()

    suspend fun resolve(dnsQuery: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
        if (DEBUG) Log.d(TAG, "Отправка DoH запроса (${dnsQuery.size} байт)")
        try {
            val body = dnsQuery.toRequestBody(mediaType)
            val request = Request.Builder()
                .url(endpointUrl)
                .post(body)
                .addHeader("Accept", "application/dns-message")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBytes = response.body?.bytes()
                if (DEBUG) Log.d(TAG, "DoH успешно ответил (${responseBytes?.size ?: 0} байт)")
                return@withContext responseBytes
            } else {
                if (DEBUG) Log.d(TAG, "DoH вернул ошибку: ${response.code}")
            }
        } catch (e: Exception) {
            if (DEBUG) Log.d(TAG, "Ошибка DoH запроса: ${e.message}")
        }
        return@withContext null
    }
}
