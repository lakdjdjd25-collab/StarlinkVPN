package org.quickping.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.quickping.app.BuildConfig
import org.quickping.app.R
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.core.design.Unbounded
import org.quickping.app.core.design.quickText
import org.quickping.app.model.AppRelease
import org.quickping.app.model.NotificationItem
import org.quickping.app.model.Service
import org.quickping.app.ui.components.DashedDivider
import org.quickping.app.ui.components.GlassCard
import org.quickping.app.ui.components.PrimaryButton
import org.quickping.app.ui.components.QuickPingScreen
import org.quickping.app.ui.components.QuickPingTopBar

@Composable
fun NotificationsScreen(notifications: List<NotificationItem>, onBack: () -> Unit) {
    QuickPingScreen {
        QuickPingTopBar(title = quickText("اعلان‌ها", "Notifications"), onBack = onBack)
        if (notifications.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painterResource(R.drawable.header_no_notification),
                    contentDescription = null,
                    modifier = Modifier.size(140.dp),
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    quickText("اعلانی وجود ندارد", "No notifications"),
                    color = QuickPingColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    quickText(
                        "وقتی خبر یا تغییر مهمی وجود داشته باشد، اینجا نمایش داده می‌شود.",
                        "Important news and changes will appear here.",
                    ),
                    color = QuickPingColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(notifications.size) { index ->
                    val item = notifications[index]
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(item.title, color = QuickPingColors.TextPrimary)
                            Spacer(Modifier.height(5.dp))
                            Text(
                                item.body,
                                color = QuickPingColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VersionScreen(
    release: AppRelease?,
    onBack: (() -> Unit)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val availableRelease = release?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    val updateAvailable = availableRelease != null
    val metadataValid = availableRelease?.let {
        it.downloadUrl.startsWith("https://", ignoreCase = true) && SHA256_REGEX.matches(it.sha256.trim())
    } == true

    var downloading by remember(availableRelease?.versionCode) { mutableStateOf(false) }
    var verifiedApk by remember(availableRelease?.versionCode) { mutableStateOf<File?>(null) }
    var updateError by remember(availableRelease?.versionCode) { mutableStateOf<String?>(null) }

    val integrityError = quickText(
        "هش فایل دانلودشده با نسخه منتشرشده تطبیق ندارد؛ نصب متوقف شد.",
        "The downloaded APK hash does not match the published release. Installation was stopped.",
    )
    val downloadError = quickText(
        "دانلود یا تأیید نسخه جدید انجام نشد؛ دوباره تلاش کنید.",
        "The update could not be downloaded or verified. Please try again.",
    )
    val permissionMessage = quickText(
        "برای ادامه، مجوز نصب برنامه از nimHUB را فعال کنید و سپس «ادامه نصب» را بزنید.",
        "Allow app installation from nimHUB, then tap Continue installation.",
    )
    val installerError = quickText(
        "باز کردن نصب‌کننده Android انجام نشد.",
        "Android Package Installer could not be opened.",
    )
    val invalidMetadata = quickText(
        "اطلاعات امنیتی این نسخه کامل نیست؛ دانلود برای حفاظت از دستگاه غیرفعال شد.",
        "This release is missing valid security metadata, so download is disabled.",
    )

    QuickPingScreen {
        QuickPingTopBar(title = quickText("نسخه", "Version"), onBack = onBack)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(
                painterResource(R.drawable.update_last_version_ovals),
                contentDescription = null,
                modifier = Modifier.size(300.dp),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (updateAvailable) availableRelease!!.versionName else BuildConfig.VERSION_NAME.substringBefore('-'),
                    color = QuickPingColors.Background,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = Unbounded,
                        fontWeight = FontWeight.Normal,
                    ),
                    modifier = Modifier
                        .background(QuickPingColors.TextPrimary, CircleShape)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(56.dp))
                Text(
                    if (updateAvailable) {
                        quickText("نسخهٔ جدید nimHUB آماده است", "A new nimHUB version is available")
                    } else {
                        quickText("تبریک! nimHUB شما به‌روز است", "nimHUB is up to date")
                    },
                    color = QuickPingColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (updateAvailable) {
                        availableRelease!!.changelog.ifBlank {
                            quickText("بهبود پایداری و امنیت اتصال", "Connection stability and security improvements")
                        }
                    } else {
                        quickText("آماده‌اید برای اتصال سریع و پایدار!", "Ready for a fast and stable connection!")
                    },
                    modifier = Modifier.padding(horizontal = 30.dp),
                    color = QuickPingColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )

                if (updateAvailable) {
                    Spacer(Modifier.height(20.dp))
                    when {
                        !metadataValid -> Text(
                            invalidMetadata,
                            modifier = Modifier.padding(horizontal = 30.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                        downloading -> Text(
                            quickText("در حال دانلود و تأیید فایل…", "Downloading and verifying…"),
                            color = QuickPingColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        else -> PrimaryButton(
                            text = if (verifiedApk != null) {
                                quickText("ادامه نصب", "Continue installation")
                            } else {
                                quickText("دریافت نسخهٔ جدید", "Download update")
                            },
                            onClick = {
                                val readyFile = verifiedApk?.takeIf(File::exists)
                                if (readyFile != null) {
                                    val installResult = runCatching { launchVerifiedApk(context, readyFile) }
                                    updateError = installResult.fold(
                                        onSuccess = { launched -> if (launched) null else permissionMessage },
                                        onFailure = { installerError },
                                    )
                                } else {
                                    scope.launch {
                                        downloading = true
                                        updateError = null
                                        val result = runCatching {
                                            downloadVerifiedApk(context, availableRelease!!)
                                        }
                                        downloading = false
                                        result.onSuccess { file ->
                                            verifiedApk = file
                                            val installResult = runCatching { launchVerifiedApk(context, file) }
                                            updateError = installResult.fold(
                                                onSuccess = { launched -> if (launched) null else permissionMessage },
                                                onFailure = { installerError },
                                            )
                                        }.onFailure { error ->
                                            verifiedApk = null
                                            updateError = if (error is UpdateIntegrityException) integrityError else downloadError
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.padding(horizontal = 46.dp),
                        )
                    }
                    updateError?.let { message ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            message,
                            modifier = Modifier.padding(horizontal = 30.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

private suspend fun downloadVerifiedApk(context: Context, release: AppRelease): File = withContext(Dispatchers.IO) {
    val expectedHash = release.sha256.trim().lowercase()
    if (!SHA256_REGEX.matches(expectedHash)) throw UpdateIntegrityException()

    val url = URL(release.downloadUrl)
    if (!url.protocol.equals("https", ignoreCase = true)) throw UpdateIntegrityException()

    val updateDirectory = File(context.cacheDir, "updates").apply { mkdirs() }
    val target = File(updateDirectory, "nimHUB-${release.versionCode}.apk")
    val temporary = File(updateDirectory, "${target.name}.part")
    temporary.delete()
    updateDirectory.listFiles()
        ?.filter { it != target && it != temporary }
        ?.forEach { it.delete() }

    var connection: HttpsURLConnection? = null
    try {
        connection = url.openConnection() as? HttpsURLConnection ?: throw UpdateIntegrityException()
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/vnd.android.package-archive,application/octet-stream,*/*")
        connection.setRequestProperty("User-Agent", "nimHUB/${BuildConfig.VERSION_NAME}")
        connection.connect()

        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("update_http_${connection.responseCode}")
        }
        if (!connection.url.protocol.equals("https", ignoreCase = true)) {
            throw UpdateIntegrityException()
        }
        val advertisedLength = connection.contentLength.toLong()
        if (advertisedLength > MAX_UPDATE_BYTES) throw UpdateTooLargeException()

        val digest = MessageDigest.getInstance("SHA-256")
        var totalBytes = 0L
        connection.inputStream.buffered().use { input ->
            temporary.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    totalBytes += count
                    if (totalBytes > MAX_UPDATE_BYTES) throw UpdateTooLargeException()
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            }
        }
        if (totalBytes == 0L) throw IllegalStateException("empty_update")

        val actualHash = digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
        if (!actualHash.equals(expectedHash, ignoreCase = true)) throw UpdateIntegrityException()

        if (target.exists() && !target.delete()) throw IllegalStateException("stale_update")
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        target
    } catch (error: Throwable) {
        temporary.delete()
        throw error
    } finally {
        connection?.disconnect()
    }
}

private fun launchVerifiedApk(context: Context, apk: File): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return false
    }

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apk,
    )
    context.startActivity(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    return true
}

private class UpdateIntegrityException : Exception()
private class UpdateTooLargeException : Exception()

private val SHA256_REGEX = Regex("^[a-fA-F0-9]{64}$")
private const val MAX_UPDATE_BYTES = 250L * 1024L * 1024L

@Composable
fun ServicesScreen(service: Service, onBack: () -> Unit) {
    QuickPingScreen {
        QuickPingTopBar(title = quickText("سرویس‌ها", "Services"), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, QuickPingColors.TextSecondary, RoundedCornerShape(22.dp)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(20.dp).border(2.dp, QuickPingColors.TextSecondary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(12.dp).background(QuickPingColors.Primary, CircleShape))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(service.license, color = QuickPingColors.TextPrimary, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (service.isFree) quickText("رایگان", "Free") else service.plan,
                        color = QuickPingColors.Success,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                DashedDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ServiceMetric(
                        Modifier.weight(1f),
                        "${bytesToGb(service.usedBytes)}GB از ${bytesToGb(service.totalBytes)}GB",
                        quickText("دادهٔ استفاده‌شده", "Data used"),
                    )
                    ServiceMetric(
                        Modifier.weight(1f),
                        "${service.daysLeft} ${quickText("روز", "days")}",
                        quickText("اعتبار", "Validity"),
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceMetric(modifier: Modifier, value: String, label: String) {
    Column(
        modifier = modifier
            .height(48.dp)
            .background(QuickPingColors.BackgroundRaised, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(value, color = QuickPingColors.TextPrimary, style = MaterialTheme.typography.labelMedium)
        Text(label, color = QuickPingColors.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

private fun bytesToGb(bytes: Long): String = "%.1f".format(bytes / 1024.0 / 1024.0 / 1024.0)
