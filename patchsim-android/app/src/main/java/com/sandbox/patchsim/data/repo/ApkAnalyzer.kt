package com.sandbox.patchsim.data.repo

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

/** Read-only, structured information about an application package. */
data class ApkReport(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val isSystemApp: Boolean,
    val isDebuggable: Boolean,
    val installerPackage: String?,
    val permissions: List<String>,
    val activities: List<String>,
    val services: List<String>,
    val receivers: List<String>,
    val providers: List<String>,
    val nativeLibsAbis: List<String>,
    val billingSignals: List<String>
) {
    val usesBilling: Boolean get() = billingSignals.isNotEmpty()
}

/**
 * Analyzes installed apps or standalone .apk files via the platform
 * [PackageManager]. It only READS metadata (permissions, components, libraries,
 * billing signals). It never modifies, repackages or resigns any APK.
 */
class ApkAnalyzer(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    /** List installed, launchable + user-visible packages (best effort). */
    fun listInstalled(includeSystem: Boolean = false): List<ApkReport> {
        val flags = PackageManager.GET_PERMISSIONS or
            PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS
        return pm.getInstalledPackages(flags)
            .asSequence()
            .filter { includeSystem || !isSystem(it.applicationInfo) }
            .mapNotNull { runCatching { toReport(it) }.getOrNull() }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Analyze a single installed package by name. */
    fun analyzeInstalled(packageName: String): ApkReport? {
        val flags = PackageManager.GET_PERMISSIONS or
            PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS
        return runCatching { toReport(pm.getPackageInfo(packageName, flags)) }.getOrNull()
    }

    /** Analyze a standalone .apk file on disk (metadata only; file is never altered). */
    fun analyzeFile(apkPath: String): ApkReport? {
        val flags = PackageManager.GET_PERMISSIONS or
            PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS
        val info = pm.getPackageArchiveInfo(apkPath, flags) ?: return null
        info.applicationInfo?.sourceDir = apkPath
        info.applicationInfo?.publicSourceDir = apkPath
        return toReport(info)
    }

    private fun isSystem(info: ApplicationInfo?): Boolean =
        info != null && (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0

    private fun toReport(info: PackageInfo): ApkReport {
        val appInfo = info.applicationInfo
        val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: info.packageName
        val permissions = info.requestedPermissions?.toList() ?: emptyList()
        val activities = info.activities?.mapNotNull { it.name } ?: emptyList()
        val services = info.services?.mapNotNull { it.name } ?: emptyList()
        val receivers = info.receivers?.mapNotNull { it.name } ?: emptyList()
        val providers = info.providers?.mapNotNull { it.name } ?: emptyList()

        val billingSignals = buildList {
            if (permissions.any { it.contains("BILLING", ignoreCase = true) }) add("permission: com.android.vending.BILLING")
            if (services.any { it.contains("billing", ignoreCase = true) || it.contains("vending", ignoreCase = true) }) add("service references billing")
            if (activities.any { it.contains("ProxyBillingActivity", ignoreCase = true) }) add("Play Billing ProxyBillingActivity")
        }

        val abis = runCatching {
            val dir = appInfo?.nativeLibraryDir
            if (dir != null) java.io.File(dir).parentFile?.list()?.toList().orEmpty() else emptyList()
        }.getOrDefault(emptyList())

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()

        return ApkReport(
            packageName = info.packageName,
            label = label,
            versionName = info.versionName ?: "",
            versionCode = versionCode,
            minSdk = appInfo?.minSdkVersion ?: 0,
            targetSdk = appInfo?.targetSdkVersion ?: 0,
            isSystemApp = isSystem(appInfo),
            isDebuggable = appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
            installerPackage = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    pm.getInstallSourceInfo(info.packageName).installingPackageName
                else @Suppress("DEPRECATION") pm.getInstallerPackageName(info.packageName)
            }.getOrNull(),
            permissions = permissions,
            activities = activities,
            services = services,
            receivers = receivers,
            providers = providers,
            nativeLibsAbis = abis,
            billingSignals = billingSignals
        )
    }
}
