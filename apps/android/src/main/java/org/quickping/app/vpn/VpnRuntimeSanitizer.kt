package org.quickping.app.vpn

import org.json.JSONArray
import org.json.JSONObject
import org.quickping.app.model.AppSettings

/**
 * Normalizes provider-owned routing before the app adds its own sing-box rules.
 *
 * Subscription panels often ship convenience `direct` rules. Those rules are
 * appropriate for a generic sing-box client, but they must not silently bypass
 * a full-tunnel Android VPN. User-requested split tunneling is applied later by
 * [VpnConfigCompiler] and remains authoritative.
 */
internal object VpnRuntimeSanitizer {
    fun sanitize(rawConfigJson: String, settings: AppSettings): String {
        if (settings.splitTunnelingEnabled) return rawConfigJson

        val config = JSONObject(rawConfigJson)
        val route = config.optJSONObject("route") ?: return rawConfigJson
        val directTags = directOutboundTags(config)

        route.optJSONArray("rules")?.let { providerRules ->
            route.put("rules", removeDirectProviderRules(providerRules, directTags))
        }

        val currentFinal = route.optString("final")
        if (currentFinal.isBlank() || currentFinal in directTags) {
            firstProxyTag(config)?.let { route.put("final", it) }
        }
        return config.toString()
    }

    private fun directOutboundTags(config: JSONObject): Set<String> {
        val result = linkedSetOf<String>()
        val outbounds = config.optJSONArray("outbounds") ?: return result
        for (index in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(index) ?: continue
            if (outbound.optString("type") != "direct") continue
            outbound.optString("tag").takeIf(String::isNotBlank)?.let(result::add)
        }
        return result
    }

    private fun firstProxyTag(config: JSONObject): String? {
        config.optJSONArray("outbounds")?.let { outbounds ->
            for (index in 0 until outbounds.length()) {
                val outbound = outbounds.optJSONObject(index) ?: continue
                if (outbound.optString("type") in NON_PROXY_OUTBOUND_TYPES) continue
                outbound.optString("tag").takeIf(String::isNotBlank)?.let { return it }
            }
        }
        config.optJSONArray("endpoints")?.let { endpoints ->
            for (index in 0 until endpoints.length()) {
                endpoints.optJSONObject(index)?.optString("tag")
                    ?.takeIf(String::isNotBlank)
                    ?.let { return it }
            }
        }
        return null
    }

    private fun removeDirectProviderRules(source: JSONArray, directTags: Set<String>): JSONArray {
        if (directTags.isEmpty()) return JSONArray(source.toString())
        val sanitized = JSONArray()
        for (index in 0 until source.length()) {
            val rule = source.optJSONObject(index) ?: continue
            if (rule.optString("outbound") in directTags) continue

            val nested = rule.optJSONArray("rules")
            if (nested != null) {
                val filteredNested = removeDirectProviderRules(nested, directTags)
                if (filteredNested.length() == 0) continue
                rule.put("rules", filteredNested)
            }
            sanitized.put(rule)
        }
        return sanitized
    }

    private val NON_PROXY_OUTBOUND_TYPES = setOf("direct", "block", "dns")
}
