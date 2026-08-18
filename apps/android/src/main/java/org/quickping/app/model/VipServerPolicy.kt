package org.quickping.app.model

private const val VIP_TIE_WINDOW_MS = 12

internal fun selectBestServerForAuto(servers: List<Server>): Server? {
    val eligible = servers.filter { it.selectable }
    if (eligible.isEmpty()) return null
    val measured = eligible.filter { it.pingMs != null }
    if (measured.isEmpty()) return eligible.first()
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
