package com.health.pop.core.tracker

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** Các quyền giúp tracker chạy nền ổn định: bỏ tối ưu pin, tự khởi động, quản trị viên thiết bị. */
object BackgroundAccess {

    private fun adminComponent(context: Context) =
        ComponentName(context.applicationContext, PopDeviceAdminReceiver::class.java)

    /* ───── Bỏ tối ưu pin ───── */

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Hộp thoại hệ thống hỏi "Cho phép chạy nền không hạn chế"; nếu máy không hỗ trợ thì mở danh sách tối ưu pin. */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!tryStart(context, direct)) {
            val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!tryStart(context, list)) openAppDetails(context)
        }
    }

    /* ───── Quản trị viên thiết bị ───── */

    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(adminComponent(context))
    }

    fun requestDeviceAdmin(context: Context) {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(context))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Kích hoạt để POP AI không bị gỡ nhầm và luôn theo dõi, nhắc bạn nghỉ ngơi khi dùng TikTok, YouTube quá hạn mức."
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!tryStart(context, intent)) openAppDetails(context)
    }

    fun removeDeviceAdmin(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        try {
            dpm.removeActiveAdmin(adminComponent(context))
        } catch (_: Exception) {
        }
    }

    /* ───── Tự khởi động (tuỳ hãng máy) ───── */

    private val autoStartScreens = listOf(
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
        "com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity"
    )

    /**
     * Mở màn "Tự khởi động" của hãng máy nếu có (Xiaomi, Oppo, Vivo, Huawei, Samsung, Asus);
     * nếu không có thì mở trang thông tin ứng dụng để người dùng tự bật.
     * Trả về true nếu mở được màn riêng của hãng.
     */
    fun openAutoStartSettings(context: Context): Boolean {
        for ((pkg, cls) in autoStartScreens) {
            val intent = Intent().setComponent(ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (tryStart(context, intent)) return true
        }
        openAppDetails(context)
        return false
    }

    fun openAppDetails(context: Context) {
        tryStart(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}
