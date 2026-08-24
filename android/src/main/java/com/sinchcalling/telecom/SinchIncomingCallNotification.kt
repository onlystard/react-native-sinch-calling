package com.sinchcalling.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
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
  // Separate, lower-priority channel for the "you missed a call" / "call was
  // forwarded" notice left behind once a cancel push tears down the ringing
  // notification above — a plain heads-up, not full-screen/ongoing, so it
  // behaves like any other missed-call notification instead of ringing again.
  private const val MISSED_CHANNEL_ID = "sinch_missed_calls"
  // Silent, low-importance channel for the ongoing-call notification a
  // foreground service must show while a call is active — see
  // `SinchCallForegroundService`. Distinct from the ringing channel above,
  // which needs to be loud and heads-up; this one should be as unobtrusive
  // as a normal "call in progress" system notification.
  private const val ACTIVE_CALL_CHANNEL_ID = "sinch_active_call"
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
  internal fun resolveSmallIcon(context: Context, iconName: String?): Int {
    if (!iconName.isNullOrBlank()) {
      val resId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
      if (resId != 0) return resId
    }
    return context.applicationInfo.icon
  }

  private fun notificationId(callId: String): Int = callId.hashCode()

  // Distinct from `notificationId` so the missed/forwarded notice (posted
  // after the ringing one has already been cancelled) never collides with
  // — or gets silently clobbered by a race with — the ringing notification
  // for the same callId.
  private fun missedNoticeId(callId: String): Int = callId.hashCode() xor MISSED_NOTICE_ID_SALT
  private const val MISSED_NOTICE_ID_SALT = -0x4d495353 // "MISS", arbitrary distinguishing bit pattern

  // The ringing notification only carries the caller name for as long as
  // it's showing (see `show`/`cancel`) — captured here so
  // `SinchTelecomManager.reportCallCancelled` can read it before tearing the
  // ringing notification (and this map entry) down via `reportCallEnded`.
  private val activeDisplayNames = mutableMapOf<String, String>()

  fun displayNameFor(callId: String): String? = activeDisplayNames[callId]

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
      // The device's actual ringtone (ringer stream), not the default
      // notification blip — this is only reachable while foregrounded
      // Socket.IO isn't already driving the in-app ring UI (see
      // pushNotificationService.ts's foreground handler), so there's no
      // double-ring risk with the JS-side ringtone.
      setSound(
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        AudioAttributes.Builder()
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
          .build()
      )
    }
    manager.createNotificationChannel(channel)
  }

  private fun ensureMissedChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(MISSED_CHANNEL_ID) != null) return
    val channel = NotificationChannel(
      MISSED_CHANNEL_ID,
      "Missed calls",
      NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
      description = "Missed and forwarded call notices"
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
    activeDisplayNames[callId] = displayName
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
    activeDisplayNames.remove(callId)
    val manager = context.applicationContext.getSystemService(NotificationManager::class.java) ?: return
    manager.cancel(notificationId(callId))
  }

  // A still-ringing call was called off by a custom cancel push (see
  // `SinchTelecomManager.reportCallCancelled`) — the ringing notification is
  // already gone by the time this runs, so post a plain, dismissible notice
  // explaining why the phone stopped ringing instead of leaving the rep to
  // wonder. `reason == "FORWARDED"` gets a distinct "call forwarded" notice;
  // every other reason (answered elsewhere, declined, hung up, ...) reads as
  // a plain missed call.
  fun showCancelledNotice(context: Context, callId: String, reason: String?, displayName: String?) {
    val appContext = context.applicationContext
    val config = readConfig(appContext)
    if (!config.enabled) return
    ensureMissedChannel(appContext)

    val forwarded = reason == "FORWARDED"
    val title = if (forwarded) "Call forwarded" else "Missed call"
    val text = displayName?.takeIf { it.isNotBlank() } ?: "Unknown"

    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Notification.Builder(appContext, MISSED_CHANNEL_ID)
    } else {
      @Suppress("DEPRECATION")
      Notification.Builder(appContext)
    }

    builder
      .setSmallIcon(resolveSmallIcon(appContext, config.smallIconName))
      .setContentTitle(title)
      .setContentText(text)
      .setCategory(Notification.CATEGORY_CALL)
      .setAutoCancel(true)
      .setOngoing(false)

    launchPendingIntent(appContext, callId)?.let { builder.setContentIntent(it) }

    val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
    manager.notify(missedNoticeId(callId), builder.build())
  }

  private fun ensureActiveCallChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(ACTIVE_CALL_CHANNEL_ID) != null) return
    val channel = NotificationChannel(
      ACTIVE_CALL_CHANNEL_ID,
      "Ongoing call",
      NotificationManager.IMPORTANCE_LOW
    ).apply {
      description = "Shown while a call is in progress"
    }
    manager.createNotificationChannel(channel)
  }

  // Built for `SinchCallForegroundService.onStartCommand` — every foreground
  // service must call `startForeground` with a notification within a few
  // seconds of starting, regardless of whether the ringing notification is
  // already showing (it isn't, by the time a call is active).
  internal fun buildOngoingCallNotification(context: Context, callId: String, displayName: String?): Notification {
    val appContext = context.applicationContext
    val config = readConfig(appContext)
    ensureActiveCallChannel(appContext)

    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Notification.Builder(appContext, ACTIVE_CALL_CHANNEL_ID)
    } else {
      @Suppress("DEPRECATION")
      Notification.Builder(appContext)
    }

    builder
      .setSmallIcon(resolveSmallIcon(appContext, config.smallIconName))
      .setContentTitle("Call in progress")
      .setContentText(displayName?.takeIf { it.isNotBlank() })
      .setCategory(Notification.CATEGORY_CALL)
      .setOngoing(true)

    launchPendingIntent(appContext, callId)?.let { builder.setContentIntent(it) }

    return builder.build()
  }
}
