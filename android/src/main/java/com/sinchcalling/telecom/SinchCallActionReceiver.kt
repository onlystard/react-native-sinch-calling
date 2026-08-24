package com.sinchcalling.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles Answer/Decline tapped directly on the incoming-call notification
 * (see `SinchIncomingCallNotification`). These operate purely at the
 * Telecom/native layer via `SinchTelecomManager` — the same layer a
 * Bluetooth headset or Android Auto button press goes through — so they
 * work even if the app's JS hasn't started yet (e.g. the process was woken
 * up just to relay the incoming-call push).
 */
class SinchCallActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val callId = intent.getStringExtra(SinchTelecomManager.EXTRA_CALL_ID) ?: return
    when (intent.action) {
      SinchIncomingCallNotification.ACTION_ANSWER -> SinchTelecomManager.answerCall(callId)
      SinchIncomingCallNotification.ACTION_DECLINE -> SinchTelecomManager.declineCall(callId)
    }
  }
}
