package com.health.pop.core.tracker

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Quản trị viên thiết bị (không đặt chính sách nào): chỉ để hệ thống bắt buộc phải tắt quyền này
 * trước khi gỡ cài đặt POP AI, giúp bạn không lỡ tay gỡ app theo dõi khi đang bị cám dỗ.
 */
class PopDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Tắt quyền này sẽ cho phép gỡ POP AI và dừng việc nhắc nghỉ ngơi khi dùng TikTok, YouTube."
}
