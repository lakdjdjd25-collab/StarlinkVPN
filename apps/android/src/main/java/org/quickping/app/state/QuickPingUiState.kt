package org.quickping.app.state

import org.quickping.app.model.AppSettings
import org.quickping.app.model.ConnectionStatus
import org.quickping.app.model.GuardianCategory
import org.quickping.app.model.InstalledApp
import org.quickping.app.model.NotificationItem
import org.quickping.app.model.Server
import org.quickping.app.model.Service
import org.quickping.app.model.UserInfo

data class QuickPingUiState(
    val initialized: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val selectedServerId: String = "",
    val servers: List<Server> = emptyList(),
    val service: Service = emptyService,
    val user: UserInfo = guestUser,
    val settings: AppSettings = AppSettings(),
    val guardianCategories: List<GuardianCategory> = defaultGuardian,
    val notifications: List<NotificationItem> = emptyList(),
    val signedIn: Boolean = false,
    val busy: Boolean = false,
    val pendingEmail: String = "",
    val loginChallengeId: String? = null,
    val loginDebugCode: String? = null,
    val loginError: String? = null,
    val connectionError: String? = null,
    val connectionErrorCode: String? = null,
    val installedApps: List<InstalledApp> = emptyList(),
    val loadingInstalledApps: Boolean = false,
)

internal val emptyService = Service(
    id = "",
    name = "سرویس رایگان",
    plan = "رایگان",
    license = "—",
    totalBytes = 0,
    usedBytes = 0,
    daysLeft = 0,
    usersCount = 1,
    isFree = true,
)

private val guestUser = UserInfo(
    id = "guest",
    email = "guest@quickping.local",
    emailVerified = false,
    telegramBound = false,
    balance = 0,
    language = "fa",
)

private val defaultGuardian = listOf(
    GuardianCategory("malware", "بدافزارها", "محافظت در برابر دامنه‌های مخرب", "malware", true),
    GuardianCategory("ads", "تبلیغات و ردیاب‌ها", "مسدودکردن تبلیغات و ابزارهای ردیابی", "ads", true),
    GuardianCategory("youtube", "تبلیغات یوتیوب", "کاهش تبلیغات و دامنه‌های مزاحم", "youtube", true),
    GuardianCategory("phishing", "فیشینگ", "جلوگیری از بازشدن صفحات جعل هویت", "phishing", true),
    GuardianCategory("porn", "محتوای بزرگسال", "فیلتر دامنه‌های نامناسب", "porn", false),
    GuardianCategory("government", "وب‌سایت‌های دولتی", "کنترل دسترسی به دامنه‌های دولتی", "government", false),
    GuardianCategory("payment", "درگاه‌های پرداخت", "کنترل دسترسی به درگاه‌های مالی", "payment", false),
    GuardianCategory("socials", "شبکه‌های اجتماعی", "مدیریت دسترسی به شبکه‌های اجتماعی", "socials", false),
    GuardianCategory("crypto", "رمزارز", "کنترل سایت‌ها و سرویس‌های رمزارز", "crypto", false),
    GuardianCategory("fake-news", "اخبار جعلی", "فیلتر منابع شناخته‌شدهٔ گمراه‌کننده", "fake_news", false),
)
