package org.quickping.app.vpn

import org.json.JSONObject
import org.quickping.app.model.DnsProvider

/**
 * The user's "Default" DNS choice means follow the service/provider policy.
 * PasarGuard can deliver a complete sing-box DNS section. Replacing that with
 * Android's local resolver can re-introduce ISP filtering/poisoning even while
 * the proxy tunnel itself is healthy.
 *
 * Only preserve new-style DNS server objects (sing-box 1.12+ shape). Legacy
 * DNS shapes are left to VpnConfigCompiler's safe local fallback so an old
 * subscription cannot make the runtime config invalid.
 */
internal fun applyProviderDnsPolicy(
    rawConfigJson: String,
    compiledConfigJson: String,
    provider: DnsProvider,
): String {
    if (provider != DnsProvider.Default) return compiledConfigJson

    val providerDns = runCatching { JSONObject(rawConfigJson).optJSONObject("dns") }.getOrNull()
        ?: return compiledConfigJson
    val servers = providerDns.optJSONArray("servers") ?: return compiledConfigJson
    if (servers.length() == 0) return compiledConfigJson

    for (index in 0 until servers.length()) {
        val server = servers.optJSONObject(index) ?: return compiledConfigJson
        if (server.optString("type").isBlank()) return compiledConfigJson
    }

    return runCatching {
        JSONObject(compiledConfigJson)
            .put("dns", JSONObject(providerDns.toString()))
            .toString()
    }.getOrDefault(compiledConfigJson)
}
