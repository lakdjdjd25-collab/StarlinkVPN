package org.quickping.app.vpn

import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Locale

internal enum class SplitAddressKind {
    IpCidr,
    DomainSuffix,
}

internal data class SplitAddress(
    val value: String,
    val kind: SplitAddressKind,
)

/**
 * Parses user-entered split-tunnel destinations without performing a DNS
 * lookup.  Domains are converted to their ASCII/IDNA form so that the same
 * value is persisted, displayed and passed to sing-box.
 */
internal fun parseSplitAddress(raw: String): SplitAddress? {
    val candidate = raw.trim().removePrefix("*.").removeSuffix(".")
    if (candidate.isBlank() || candidate.length > MAX_DOMAIN_LENGTH) return null

    val slash = candidate.indexOf('/')
    if (slash >= 0 && slash != candidate.lastIndexOf('/')) return null
    val address = if (slash >= 0) candidate.substring(0, slash) else candidate
    val prefix = if (slash >= 0) candidate.substring(slash + 1).toIntOrNull() ?: return null else null

    parseIpv4(address)?.let { normalized ->
        if (prefix != null && prefix !in 0..32) return null
        return SplitAddress(
            value = normalized + (prefix?.let { "/$it" }.orEmpty()),
            kind = SplitAddressKind.IpCidr,
        )
    }

    if (':' in address) {
        if ('%' in address || prefix != null && prefix !in 0..128) return null
        val valid = runCatching { InetAddress.getByName(address) is Inet6Address }.getOrDefault(false)
        if (!valid) return null
        return SplitAddress(
            value = address.lowercase(Locale.US) + (prefix?.let { "/$it" }.orEmpty()),
            kind = SplitAddressKind.IpCidr,
        )
    }

    if (prefix != null || address.all { it.isDigit() || it == '.' }) return null
    val ascii = runCatching {
        IDN.toASCII(address, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.US)
    }.getOrNull() ?: return null
    if (ascii.length > MAX_DOMAIN_LENGTH || '.' !in ascii) return null
    val labels = ascii.split('.')
    if (labels.any { label ->
            label.isEmpty() || label.length > MAX_LABEL_LENGTH || label.startsWith('-') || label.endsWith('-')
        }
    ) return null
    return SplitAddress(ascii, SplitAddressKind.DomainSuffix)
}

internal fun normalizeSplitAddress(raw: String): String? = parseSplitAddress(raw)?.value

private fun parseIpv4(value: String): String? {
    val octets = value.split('.')
    if (octets.size != 4) return null
    val values = octets.map { octet ->
        if (octet.isEmpty() || octet.any { !it.isDigit() } || octet.length > 3) return null
        octet.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
    }
    return values.joinToString(".")
}

private const val MAX_DOMAIN_LENGTH = 253
private const val MAX_LABEL_LENGTH = 63
