package com.sinchcalling.telecom

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Android 14 (API 34) restricts starting/continuing microphone capture from
 * a backgrounded app to processes running a foreground service declared with
 * `FOREGROUND_SERVICE_TYPE_MICROPHONE` (also `PHONE_CALL` here, since that's
 * literally what this is). A `CAPABILITY_SELF_MANAGED` Telecom connection
 * gets no such service for free — the ringing notification
 * (`SinchIncomingCallNotification`) only covers the ringing phase, so once a
 * call goes active this service takes over for as long as the call runs,
 * started/stopped in lockstep with the connection lifecycle by
 * `SinchTelecomManager.reportCallActive`/`reportCallEnded`.
 */
class SinchCallForegroundService : Service() {
  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val callId = intent?.getStringExtra(SinchTelecomManager.EXTRA_CALL_ID) ?: ""
    val displayName = intent?.getStringExtra(EXTRA_DISPLAY_NAME)
    val notification = SinchIncomingCallNotification.buildOngoingCallNotification(this, callId, displayName)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
    return START_NOT_STICKY
  }

  companion object {
    // Arbitrary, fixed id — unlike the per-call ringing/missed-call
    // notifications, only one call is ever active at a time, so there's
    // nothing to disambiguate.
    private const val NOTIFICATION_ID = 837_612_991
    private const val EXTRA_DISPLAY_NAME = "com.sinchcalling.EXTRA_DISPLAY_NAME"

    fun start(context: Context, callId: String, displayName: String?) {
      val intent = Intent(context, SinchCallForegroundService::class.java).apply {
        putExtra(SinchTelecomManager.EXTRA_CALL_ID, callId)
        putExtra(EXTRA_DISPLAY_NAME, displayName)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, SinchCallForegroundService::class.java))
    }
  }
}
