package com.proxyvpn

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ProxyWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val finder = ProxyFinder(applicationContext)
            // It will load from cache, test them, and if < MIN_PROXIES, it will scrape new ones
            finder.loadProxies(forceRefresh = true, callback = null, proxyType = ProxyType.SOCKS5)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
