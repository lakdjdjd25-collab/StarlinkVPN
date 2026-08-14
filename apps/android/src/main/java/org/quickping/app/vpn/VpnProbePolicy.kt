package org.quickping.app.vpn

/**
 * Pure policy for the destinations that must be reachable before nimHUB can
 * publish Connected. Keeping this outside Android networking code makes the
 * release gate regression-testable.
 */
internal object VpnProbePolicy {
    fun requiredGroupNames(enabledGuardianCategories: Set<String>): Set<String> = buildSet {
        add("web")
        add("youtube")
        if ("socials" !in enabledGuardianCategories) {
            add("telegram")
            add("instagram")
        }
    }
}
