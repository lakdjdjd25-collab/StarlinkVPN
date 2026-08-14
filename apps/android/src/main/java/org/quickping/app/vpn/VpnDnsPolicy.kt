package org.quickping.app.vpn

import org.json.JSONObject
import org.quickping.app.model.DnsProvider

/**
 * Default DNS follows the PasarGuard/provider policy only when the provider DNS
 * is self-contained after Android compilation.
 *
 * Provider subscriptions commonly attach their DNS server to the selected proxy
 * via `detour`. That is unsafe during Android bootstrap: the selected proxy can
 * itself require DNS resolution before the detoured DNS transport is usable.
 * Keep nimHUB's compiled local DNS in that case so the proxy endpoint can be
 * resolved on the physical network first. Explicit Google/Cloudflare choices
 * are compiled separately and never pass through this provider override.
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

    val providerDnsTags = (0 until servers.length())
        .mapNotNull { servers.optJSONObject(it)?.optString("tag")?.takeIf(String::isNotBlank) }
        .toSet()

    for (index in 0 until servers.length()) {
        val server = servers.optJSONObject(index) ?: return compiledConfigJson
        if (server.optString("type").isBlank()) return compiledConfigJson

        // Do not let DNS bootstrap depend on the very proxy whose hostname may
        // still need DNS. A provider DNS detour is restored only after the user
        // explicitly chooses a custom provider configuration in a future flow.
        if (server.optString("detour").isNotBlank()) return compiledConfigJson

        val resolver = server.optString("domain_resolver")
        if (resolver.isNotBlank() && resolver !in providerDnsTags) return compiledConfigJson
    }

    return runCatching {
        val compiled = JSONObject(compiledConfigJson)
        compiled.put("dns", JSONObject(providerDns.toString())).toString()
    }.getOrDefault(compiledConfigJson)
}
