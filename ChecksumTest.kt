fun calculateIpChecksum(packet: ByteArray, headerLen: Int) {
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

fun main() {
    val packet = intArrayOf(
        0x45, 0x00, 0x00, 0x3c,
        0x1c, 0x46, 0x40, 0x00,
        0x40, 0x06, 0xb1, 0xe6,
        0xac, 0x10, 0x0a, 0x63,
        0xac, 0x10, 0x0a, 0x0c
    ).map { it.toByte() }.toByteArray()

    val originalChecksum = ((packet[10].toInt() and 0xFF) shl 8) or (packet[11].toInt() and 0xFF)
    calculateIpChecksum(packet, 20)
    val newChecksum = ((packet[10].toInt() and 0xFF) shl 8) or (packet[11].toInt() and 0xFF)
    
    println("Original Checksum: " + originalChecksum.toString(16))
    println("Calculated Checksum: " + newChecksum.toString(16))
    println("Match: " + (originalChecksum == newChecksum))
}
