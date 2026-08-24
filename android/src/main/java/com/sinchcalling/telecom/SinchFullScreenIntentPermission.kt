package com.sinchcalling.telecom

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * `USE_FULL_SCREEN_INTENT` (declared in the manifest, used by
 * `SinchIncomingCallNotification` to ring over the lock screen) is
 * auto-revoked on Android 14+ for apps the OS doesn't recognize as a
 * "calling" app, as of the January 2025 Play policy change — silently, with
 * no callback. A revoked permission doesn't fail loudly either: the
 * notification still posts, it just never launches the full-screen intent,
 * so a call arriving while locked shows nothing until the user unlocks and
 * pulls down the shade. Consumer apps should check this (e.g. on launch, or
 * right before going to the background) and prompt the user to re-grant it
 * if needed.
 */
object SinchFullScreenIntentPermission {
  fun isGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
    val manager = context.getSystemService(NotificationManager::class.java) ?: return true
    return manager.canUseFullScreenIntent()
  }

  // Opens the system settings screen where the user can grant/re-grant the
  // permission. No-op pre-14, where the permission is granted automatically
  // at install time and can't be revoked this way.
  fun request(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
      data = Uri.fromParts("package", context.packageName, null)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.applicationContext.startActivity(intent)
  }
}
