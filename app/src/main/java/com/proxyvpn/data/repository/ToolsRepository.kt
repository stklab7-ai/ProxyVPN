package com.proxyvpn.data.repository

import com.proxyvpn.data.network.CloudflareApi
import com.proxyvpn.data.network.IpGeoResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.system.measureTimeMillis

class ToolsRepository(private val api: CloudflareApi) {
    fun getIpInfo(): Flow<Result<IpGeoResponse>> = flow {
        try {
            emit(Result.success(api.checkIp()))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    fun measurePingAndSpeed(): Flow<Result<Pair<Long, Long>>> = flow {
        try {
            val downloadTime = measureTimeMillis {
                val response = api.downloadSpeedtest()
                response.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (input.read(buffer) != -1) { /* reading */ }
                }
            }
            val speedKbps = (2048 * 8 * 1000L) / downloadTime.coerceAtLeast(1)
            emit(Result.success(Pair(downloadTime, speedKbps)))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
}
