package com.proxyvpn.tunnel

import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class TunPacketInterceptor(
    private val tunFd: ParcelFileDescriptor,
    private val proxyFd: ParcelFileDescriptor, // Конец Pipe, ведущий к C-библиотеке
    private val dohResolver: DohResolver
) {
    companion object {
        const val TAG = "TunPacketInterceptor"
    }

    private val tunInput = FileInputStream(tunFd.fileDescriptor)
    private val tunOutput = FileOutputStream(tunFd.fileDescriptor)
    
    private val proxyInput = FileInputStream(proxyFd.fileDescriptor)
    private val proxyOutput = FileOutputStream(proxyFd.fileDescriptor)

    fun start(scope: CoroutineScope) {
        // Корутина 1: Чтение из TUN (перехват DNS и отправка остального в прокси)
        scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(32767)
            try {
                while (isActive) {
                    val length = tunInput.read(buffer)
                    if (length > 0) {
                        val packet = buffer.copyOfRange(0, length)
                        if (packet.size > 20 && (packet[0].toInt() and 0xF0) == 0x40 && packet[9].toInt() == 17) {
                            val ipHL = (packet[0].toInt() and 0x0F) * 4
                            val dp = ((packet[ipHL + 2].toInt() and 0xFF) shl 8) or (packet[ipHL + 3].toInt() and 0xFF)
                            Log.d(TAG, "UDP Packet DstPort: $dp Len: $length")
                        }
                        if (isIpv4UdpPort53(packet)) {
                            // Запускаем обработку в новой корутине, чтобы не блокировать цикл
                            scope.launch(Dispatchers.IO) {
                                handleDnsRequest(packet)
                            }
                        } else {
                            // Пробрасываем в C-библиотеку
                            proxyOutput.write(packet)
                        }
                    }
                }
            } catch (e: Exception) {
                if (DohResolver.DEBUG) Log.d(TAG, "TUN Read loop stopped: ${e.message}")
            }
        }

        // Корутина 2: Чтение из прокси (возврат пакетов обратно в TUN)
        scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(32767)
            try {
                while (isActive) {
                    val length = proxyInput.read(buffer)
                    if (length > 0) {
                        tunOutput.write(buffer, 0, length)
                    }
                }
            } catch (e: Exception) {
                if (DohResolver.DEBUG) Log.d(TAG, "Proxy Read loop stopped: ${e.message}")
            }
        }
    }

    private fun isIpv4UdpPort53(packet: ByteArray): Boolean {
        if (packet.size < 20) return false
        val version = (packet[0].toInt() and 0xFF) shr 4
        if (version != 4) return false
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return false // Не UDP
        
        val ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
        if (packet.size < ipHeaderLen + 8) return false
        
        val dstPort = ((packet[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or (packet[ipHeaderLen + 3].toInt() and 0xFF)
        return dstPort == 53
    }

    private suspend fun handleDnsRequest(packet: ByteArray) {
        val ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
        val udpPayloadOffset = ipHeaderLen + 8
        val udpPayloadLen = packet.size - udpPayloadOffset
        
        if (udpPayloadLen <= 0) return
        
        val dnsRequest = packet.copyOfRange(udpPayloadOffset, packet.size)
        var dnsResponse = dohResolver.resolve(dnsRequest)
        
        if (dnsResponse == null) {
            dnsResponse = createServfailResponse(dnsRequest)
        }
        
        val responsePacket = craftIpv4UdpResponse(packet, ipHeaderLen, dnsResponse)
        try {
            tunOutput.write(responsePacket)
        } catch (e: Exception) {
            if (DohResolver.DEBUG) Log.d(TAG, "Failed to write DNS response to TUN: ${e.message}")
        }
    }

    private fun createServfailResponse(request: ByteArray): ByteArray {
        if (request.size < 12) return request
        val response = request.copyOf()
        response[2] = (response[2].toInt() or 0x80).toByte() // QR = 1
        response[3] = ((response[3].toInt() and 0xF0) or 0x02).toByte() // RCODE = 2 (SERVFAIL)
        return response
    }

    private fun craftIpv4UdpResponse(originalPacket: ByteArray, ipHeaderLen: Int, dnsResponse: ByteArray): ByteArray {
        val newTotalLen = ipHeaderLen + 8 + dnsResponse.size
        val response = ByteArray(newTotalLen)
        
        // Копируем IP и UDP заголовки
        System.arraycopy(originalPacket, 0, response, 0, ipHeaderLen + 8)
        
        // Обновляем Total Length в IP заголовке (байты 2 и 3)
        response[2] = (newTotalLen shr 8).toByte()
        response[3] = (newTotalLen and 0xFF).toByte()
        
        // Меняем местами Src IP (12-15) и Dst IP (16-19)
        for (i in 0..3) {
            val tmp = response[12 + i]
            response[12 + i] = response[16 + i]
            response[16 + i] = tmp
        }
        
        // Обновляем UDP Length (байты ipHeaderLen + 4, ipHeaderLen + 5)
        val udpLen = 8 + dnsResponse.size
        response[ipHeaderLen + 4] = (udpLen shr 8).toByte()
        response[ipHeaderLen + 5] = (udpLen and 0xFF).toByte()
        
        // Меняем местами Src Port (ipHeaderLen + 0,1) и Dst Port (ipHeaderLen + 2,3)
        val sp0 = response[ipHeaderLen]
        val sp1 = response[ipHeaderLen + 1]
        response[ipHeaderLen] = response[ipHeaderLen + 2]
        response[ipHeaderLen + 1] = response[ipHeaderLen + 3]
        response[ipHeaderLen + 2] = sp0
        response[ipHeaderLen + 3] = sp1
        
        // Обнуляем UDP Checksum (байты ipHeaderLen + 6, ipHeaderLen + 7) - для IPv4 это допускается!
        response[ipHeaderLen + 6] = 0
        response[ipHeaderLen + 7] = 0
        
        // Копируем полезную нагрузку
        System.arraycopy(dnsResponse, 0, response, ipHeaderLen + 8, dnsResponse.size)
        
        // Считаем IP Checksum
        calculateIpChecksum(response, ipHeaderLen)
        
        return response
    }

    private fun calculateIpChecksum(packet: ByteArray, headerLen: Int) {
        packet[10] = 0
        packet[11] = 0
        var sum = 0
        var i = 0
        while (i < headerLen - 1) {
            val word = ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < headerLen) {
            sum += (packet[i].toInt() and 0xFF) shl 8
        }
        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        sum = sum.inv() and 0xFFFF
        packet[10] = (sum shr 8).toByte()
        packet[11] = (sum and 0xFF).toByte()
    }
}
