package org.quickping.app.model

private const val VIP_TIE_WINDOW_MS = 12

internal fun selectBestServerForAuto(servers: List<Server>): Server? {
    if (servers.isEmpty()) return null
    val measured = servers.filter { it.pingMs != null }
    if (measured.isEmpty()) return servers.first()
    val fastestPing = measured.minOf { it.pingMs ?: Int.MAX_VALUE }
    return measured
        .filter { (it.pingMs ?: Int.MAX_VALUE) <= fastestPing + VIP_TIE_WINDOW_MS }
        .sortedWith(
            compareByDescending<Server> { it.isVip }
                .thenBy { it.pingMs ?: Int.MAX_VALUE },
        )
        .firstOrNull()
        ?: measured.minByOrNull { it.pingMs ?: Int.MAX_VALUE }
}
