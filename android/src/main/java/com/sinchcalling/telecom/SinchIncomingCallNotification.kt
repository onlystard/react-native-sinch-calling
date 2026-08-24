package com.sinchcalling.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * A `PhoneAccount.CAPABILITY_SELF_MANAGED` connection (see
 * `SinchTelecomManager`) gets no system-drawn incoming-call UI — Android only
 * tracks its call/audio-focus state. Per Android's own docs for self-managed
 * ConnectionServices, the app must post its own high-priority notification
 * with a full-screen intent so the call is visible (and answerable) while
 * backgrounded or the device is locked — Sinch's own Android guide does the
 * same thing manually rather than relying on the SDK for it.
 *
 * Content is configurable via `configure()` (see `SinchCallingModule`'s
 * `configureIncomingCallNotification`) and persisted to `SharedPreferences`
 * rather than kept in memory — a call can arrive and need to show this
 * before JS has started again (e.g. the process was killed and only woken
 * up to relay the push), so the config must survive that with no JS involved.
 */
internal object SinchIncomingCallNotification {
  private const val CHANNEL_ID = "sinch_incoming_calls"
  internal const val ACTION_ANSWER = "com.sinchcalling.action.ANSWER_CALL"
  internal const val ACTION_DECLINE = "com.sinchcalling.action.DECLINE_CALL"

  private const val PREFS_NAME = "sinch_calling_incoming_call_notification"
  private const val PREF_ENABLED = "enabled"
  private const val PREF_TITLE = "title"
  private const val PREF_ANSWER_LABEL = "answerLabel"
  private const val PREF_DECLINE_LABEL = "declineLabel"
  private const val PREF_SMALL_ICON_NAME = "smallIconName"

  private const val DEFAULT_TITLE = "Incoming call"
  private const val DEFAULT_ANSWER_LABEL = "Answer"
  private const val DEFAULT_DECLINE_LABEL = "Decline"

  private data class Config(
    val enabled: Boolean,
    val title: String,
    val answerLabel: String,
    val declineLabel: String,
    val smallIconName: String?
  )

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  // `null` for a given param leaves that setting unchanged — lets a consumer
  // call e.g. `configureIncomingCallNotification({ enabled: false })` without
  // clobbering a title/labels they configured earlier.
  fun configure(
    context: Context,
    enabled: Boolean?,
    title: String?,
    answerLabel: String?,
    declineLabel: String?,
    smallIconName: String?
  ) {
    prefs(context).edit().apply {
      enabled?.let { putBoolean(PREF_ENABLED, it) }
      title?.let { putString(PREF_TITLE, it) }
      answerLabel?.let { putString(PREF_ANSWER_LABEL, it) }
      declineLabel?.let { putString(PREF_DECLINE_LABEL, it) }
      smallIconName?.let { putString(PREF_SMALL_ICON_NAME, it) }
    }.apply()
  }

  private fun readConfig(context: Context): Config {
    val prefs = prefs(context)
    return Config(
      enabled = prefs.getBoolean(PREF_ENABLED, true),
      title = prefs.getString(PREF_TITLE, DEFAULT_TITLE) ?: DEFAULT_TITLE,
      answerLabel = prefs.getString(PREF_ANSWER_LABEL, DEFAULT_ANSWER_LABEL) ?: DEFAULT_ANSWER_LABEL,
      declineLabel = prefs.getString(PREF_DECLINE_LABEL, DEFAULT_DECLINE_LABEL) ?: DEFAULT_DECLINE_LABEL,
      smallIconName = prefs.getString(PREF_SMALL_ICON_NAME, null)
    )
  }

  // Falls back to the host app's launcher icon if no override was
  // configured, or the configured drawable name doesn't resolve.
  private fun resolveSmallIcon(context: Context, iconName: String?): Int {
    if (!iconName.isNullOrBlank()) {
      val resId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
      if (resId != 0) return resId
    }
    return context.applicationInfo.icon
  }

  private fun notificationId(callId: String): Int = callId.hashCode()

  private fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(CHANNEL_ID) != null) return
    val channel = NotificationChannel(
      CHANNEL_ID,
      "Incoming calls",
      NotificationManager.IMPORTANCE_HIGH
    ).apply {
      description = "Incoming voice calls"
      setBypassDnd(true)
      enableVibration(true)
      lockscreenVisibility = Notification.VISIBILITY_PUBLIC
    }
    manager.createNotificationChannel(channel)
  }

  private fun actionPendingIntent(context: Context, action: String, callId: String): PendingIntent {
    val intent = Intent(context, SinchCallActionReceiver::class.java).apply {
      this.action = action
      putExtra(SinchTelecomManager.EXTRA_CALL_ID, callId)
    }
    return PendingIntent.getBroadcast(
      context,
      notificationId(callId),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  // `null` if the host app has no launcher activity (shouldn't happen) —
  // callers fall back to a heads-up-only notification (no tap/full-screen action).
  private fun launchPendingIntent(context: Context, callId: String): PendingIntent? {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
      ?: return null
    launchIntent.apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      putExtra(SinchTelecomManager.EXTRA_CALL_ID, callId)
    }
    return PendingIntent.getActivity(
      context,
      notificationId(callId),
      launchIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  // Re-showing with the same notification id (derived from `callId`) replaces
  // the existing notification in place — used both for the initial ring and
  // to refresh the caller name once a caller-intel lookup resolves.
  fun show(context: Context, callId: String, displayName: String) {
    val appContext = context.applicationContext
    val config = readConfig(appContext)
    if (!config.enabled) return
    ensureChannel(appContext)

    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Notification.Builder(appContext, CHANNEL_ID)
    } else {
      @Suppress("DEPRECATION")
      Notification.Builder(appContext)
    }

    builder
      .setSmallIcon(resolveSmallIcon(appContext, config.smallIconName))
      .setContentTitle(config.title)
      .setContentText(displayName.ifBlank { "Unknown" })
      .setCategory(Notification.CATEGORY_CALL)
      .setPriority(Notification.PRIORITY_MAX)
      .setOngoing(true)
      .setAutoCancel(false)
      .addAction(0, config.declineLabel, actionPendingIntent(appContext, ACTION_DECLINE, callId))
      .addAction(0, config.answerLabel, actionPendingIntent(appContext, ACTION_ANSWER, callId))

    launchPendingIntent(appContext, callId)?.let { launchIntent ->
      builder.setFullScreenIntent(launchIntent, true)
      builder.setContentIntent(launchIntent)
    }

    val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
    manager.notify(notificationId(callId), builder.build())
  }

  fun cancel(context: Context, callId: String) {
    val manager = context.applicationContext.getSystemService(NotificationManager::class.java) ?: return
    manager.cancel(notificationId(callId))
  }
}
