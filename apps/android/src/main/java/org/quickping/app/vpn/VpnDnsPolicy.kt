package org.quickping.app.vpn

import org.json.JSONObject
import org.quickping.app.model.DnsProvider

/**
 * Default DNS follows the PasarGuard/provider policy only when the provider DNS
 * is self-contained after Android compilation. This prevents copying DNS
 * detours that refer to selector/urltest tags that no longer exist in the
 * selected per-node runtime config.
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

    val compiled = runCatching { JSONObject(compiledConfigJson) }.getOrNull() ?: return compiledConfigJson
    val availableTags = linkedSetOf<String>().apply {
        compiled.optJSONArray("outbounds")?.let { outbounds ->
            for (index in 0 until outbounds.length()) {
                outbounds.optJSONObject(index)?.optString("tag")
                    ?.takeIf(String::isNotBlank)
                    ?.let(::add)
            }
        }
        compiled.optJSONArray("endpoints")?.let { endpoints ->
            for (index in 0 until endpoints.length()) {
                endpoints.optJSONObject(index)?.optString("tag")
                    ?.takeIf(String::isNotBlank)
                    ?.let(::add)
            }
        }
    }

    for (index in 0 until servers.length()) {
        val server = servers.optJSONObject(index) ?: return compiledConfigJson
        if (server.optString("type").isBlank()) return compiledConfigJson
        val detour = server.optString("detour")
        if (detour.isNotBlank() && detour !in availableTags) return compiledConfigJson
        val resolver = server.optString("domain_resolver")
        if (resolver.isNotBlank()) {
            val dnsTags = (0 until servers.length())
                .mapNotNull { servers.optJSONObject(it)?.optString("tag")?.takeIf(String::isNotBlank) }
                .toSet()
            if (resolver !in dnsTags) return compiledConfigJson
        }
    }

    return runCatching {
        compiled.put("dns", JSONObject(providerDns.toString())).toString()
    }.getOrDefault(compiledConfigJson)
}
