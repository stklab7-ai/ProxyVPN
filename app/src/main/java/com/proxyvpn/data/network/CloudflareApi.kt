package com.proxyvpn.data.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming

interface CloudflareApi {
    @GET("/api/check-ip")
    suspend fun checkIp(): IpGeoResponse

    @GET("/api/dns")
    suspend fun resolveDns(@Query("name") host: String): DnsResponse

    @Streaming
    @GET("/api/speedtest")
    suspend fun downloadSpeedtest(): ResponseBody
}

data class IpGeoResponse(
    val ip: String,
    val country: String,
    val city: String,
    val asn: String,
    val asnOrganization: String
)

data class DnsResponse(
    val Status: Int,
    val Answer: List<DnsAnswer>?
)

data class DnsAnswer(
    val name: String,
    val data: String
)
