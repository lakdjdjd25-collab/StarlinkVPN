package org.quickping.app.data.auth

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

data class AppSigningIdentity(
    val packageName: String,
    val sha1: String,
)

fun currentAppSigningIdentity(context: Context): AppSigningIdentity? = runCatching {
    val packageManager = context.packageManager
    val packageName = context.packageName
    val certificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val info = packageManager.getPackageInfo(
            packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        val signingInfo = info.signingInfo ?: return@runCatching null
        signingInfo.apkContentsSigners
            .orEmpty()
            .takeIf { it.isNotEmpty() }
            ?.toList()
            ?: signingInfo.signingCertificateHistory.orEmpty().toList()
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            .signatures
            .orEmpty()
            .toList()
    }
    val certificate = certificates.firstOrNull() ?: return@runCatching null
    AppSigningIdentity(
        packageName = packageName,
        sha1 = formatCertificateSha1(MessageDigest.getInstance("SHA-1").digest(certificate.toByteArray())),
    )
}.getOrNull()

internal fun formatCertificateSha1(bytes: ByteArray): String =
    bytes.joinToString(":") { byte -> "%02X".format(byte.toInt() and 0xff) }
