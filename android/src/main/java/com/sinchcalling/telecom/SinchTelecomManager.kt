package com.sinchcalling.telecom

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

interface SinchTelecomListener {
  fun onAnswer(callId: String)
  fun onReject(callId: String)
  fun onDisconnect(callId: String)
  // Fired instead of the three methods above when the call was reported via
  // `reportExternalIncomingCall` and hasn't been resolved into a real Sinch
  // call yet (see `remapCallId`) — answering/declining it is the consumer
  // app's business, not the native call API's.
  fun onAnswerExternal(callId: String)
  fun onDeclineExternal(callId: String)
}

object SinchTelecomManager {
  const val EXTRA_CALL_ID = "com.sinchcalling.EXTRA_CALL_ID"
  const val EXTRA_REMOTE_USER_ID = "com.sinchcalling.EXTRA_REMOTE_USER_ID"
  private const val ACCOUNT_ID = "sinch_calling_account"

  var listener: SinchTelecomListener? = null

  private val connections = mutableMapOf<String, SinchConnection>()
  private val externallyReportedCallIds = mutableSetOf<String>()
  // Set once from `registerPhoneAccount` (always called first, from
  // `SinchCallManager`'s constructor) — reused by the call-lifecycle methods
  // below so they don't all need a `Context` param just to cancel a notification.
  private var appContext: Context? = null

  // Lives here rather than on `SinchCallManager` so `SinchCallingBootstrap`
  // can configure it before any `SinchCallManager`/TurboModule instance
  // exists — see that class's header comment for why that matters.
  internal var customPushIdField: String? = null
    private set
  internal var customPushDisplayField: String? = null
    private set
  internal var customCancelTypeField: String? = null
    private set
  internal var customCancelValue: String? = null
    private set

  // Opts into recognizing the consumer app's own backend's incoming-call
  // push payloads. Until called, every payload is relayed straight to Sinch
  // (see `SinchCallManager.relayRemotePushNotification`).
  fun configureCustomIncomingCallPush(idField: String, displayField: String) {
    customPushIdField = idField
    customPushDisplayField = displayField
  }

  // Opts into recognizing a cancel/end push shaped like the configured
  // incoming-call push but carrying `typeField == cancelValue`.
  fun configureCustomCancelCallPush(typeField: String, cancelValue: String) {
    customCancelTypeField = typeField
    customCancelValue = cancelValue
  }

  fun phoneAccountHandle(context: Context): PhoneAccountHandle {
    val componentName = ComponentName(context, SinchConnectionService::class.java)
    return PhoneAccountHandle(componentName, ACCOUNT_ID)
  }

  fun registerPhoneAccount(context: Context) {
    appContext = context.applicationContext
    val telecomManager = context.getSystemService(TelecomManager::class.java) ?: return
    val account = PhoneAccount.builder(phoneAccountHandle(context), "SinchCalling")
      .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
      .build()
    telecomManager.registerPhoneAccount(account)
  }

  fun reportIncomingCall(context: Context, callId: String, remoteUserId: String) {
    val telecomManager = context.getSystemService(TelecomManager::class.java) ?: return
    val extras = Bundle().apply {
      putString(EXTRA_CALL_ID, callId)
      putString(EXTRA_REMOTE_USER_ID, remoteUserId)
    }
    telecomManager.addNewIncomingCall(phoneAccountHandle(context), extras)
    SinchIncomingCallNotification.show(context, callId, remoteUserId)
  }

  // Answer/Decline tapped on our own incoming-call notification (see
  // `SinchIncomingCallNotification`/`SinchCallActionReceiver`) — calling
  // these `Connection` callbacks directly (rather than only ever receiving
  // them from the framework) mirrors what the OS would do on our behalf if a
  // Bluetooth headset or Android Auto button were pressed instead.
  fun answerCall(callId: String) {
    connections[callId]?.onAnswer()
  }

  fun declineCall(callId: String) {
    connections[callId]?.onReject()
  }

  // A call the consumer app decided to show a system call UI for (a custom
  // push, a socket event, anything) — not a Sinch-native incoming call.
  // Answering/declining goes through the app's own logic instead of the
  // native call API until `remapCallId` ties it to a real call.
  fun reportExternalIncomingCall(context: Context, callId: String, displayName: String) {
    externallyReportedCallIds.add(callId)
    reportIncomingCall(context, callId, displayName)
  }

  internal fun isExternallyReported(callId: String): Boolean = externallyReportedCallIds.contains(callId)

  internal fun clearExternallyReported(callId: String) {
    externallyReportedCallIds.remove(callId)
  }

  // Re-associates the system call UI already showing for `oldCallId` (an
  // externally-reported, not-yet-real call) with the real Sinch call
  // created once the consumer app resolved it.
  fun remapCallId(oldCallId: String, newCallId: String) {
    val connection = connections.remove(oldCallId) ?: return
    connection.callId = newCallId
    connections[newCallId] = connection
    externallyReportedCallIds.remove(oldCallId)
    // The ringing notification was shown keyed by `oldCallId` — from here on
    // `reportCallActive`/`reportCallEnded` only know about `newCallId`, so it
    // would otherwise never get cancelled.
    appContext?.let { SinchIncomingCallNotification.cancel(it, oldCallId) }
  }

  // Overrides the caller name shown on the system call UI (e.g. once a
  // caller-ID lookup resolves) — and on our own notification, so the rep
  // doesn't keep seeing the raw phone number once it's resolved.
  fun updateCallerDisplayName(callId: String, displayName: String) {
    connections[callId]?.setCallerDisplayName(displayName, TelecomManager.PRESENTATION_ALLOWED)
    appContext?.let { SinchIncomingCallNotification.show(it, callId, displayName) }
  }

  fun reportCallActive(callId: String) {
    connections[callId]?.setActive()
    appContext?.let { SinchIncomingCallNotification.cancel(it, callId) }
  }

  fun reportCallEnded(callId: String) {
    connections[callId]?.let {
      it.setDisconnected(DisconnectCause(DisconnectCause.REMOTE))
      it.destroy()
    }
    connections.remove(callId)
    externallyReportedCallIds.remove(callId)
    appContext?.let { SinchIncomingCallNotification.cancel(it, callId) }
  }

  internal fun registerConnection(callId: String, connection: SinchConnection) {
    connections[callId] = connection
  }

  internal fun unregisterConnection(callId: String) {
    connections.remove(callId)
    appContext?.let { SinchIncomingCallNotification.cancel(it, callId) }
  }
}
