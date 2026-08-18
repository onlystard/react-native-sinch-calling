package com.sinchcalling

import android.content.Context
import android.os.PowerManager
import com.sinch.android.rtc.AudioController
import com.sinch.android.rtc.ClientRegistration
import com.sinch.android.rtc.PushConfiguration
import com.sinch.android.rtc.SinchClient
import com.sinch.android.rtc.SinchClientListener
import com.sinch.android.rtc.SinchError
import com.sinch.android.rtc.SinchPush
import com.sinch.android.rtc.calling.Call
import com.sinch.android.rtc.calling.CallController
import com.sinch.android.rtc.calling.CallControllerListener
import com.sinch.android.rtc.calling.CallEndCause
import com.sinch.android.rtc.calling.CallListener
import com.sinch.android.rtc.calling.MediaConstraints
import com.sinchcalling.telecom.SinchTelecomListener
import com.sinchcalling.telecom.SinchTelecomManager
import java.io.IOException

interface SinchCallManagerListener {
  fun onClientStarted()
  fun onClientFailed(message: String, code: Int)
  fun onRegistrationCredentialsRequired()

  fun onIncomingCall(callId: String, remoteUserId: String)
  fun onCallProgressing(callId: String)
  fun onCallEstablished(callId: String)
  fun onCallEnded(callId: String, endCause: String)

  fun onPushTokenRegistered()
  fun onPushTokenRegistrationFailed(message: String, code: Int)

  // A system call UI was reported — either the consumer app called
  // `reportIncomingCallUI` directly, or a configured custom push was
  // auto-detected (see `configureCustomIncomingCallPush`). Distinct from a
  // Sinch-relayed push (e.g. an app-to-app incoming call), which instead
  // surfaces as `onIncomingCall`.
  fun onIncomingCallUIShown(callId: String, displayName: String)
  fun onCallUIAnswered(callId: String)
  fun onCallUIDeclined(callId: String)
}

private fun endCauseString(endCause: CallEndCause): String = when (endCause) {
  CallEndCause.NONE -> "none"
  CallEndCause.TIMEOUT -> "timeout"
  CallEndCause.DENIED -> "denied"
  CallEndCause.NO_ANSWER -> "noAnswer"
  CallEndCause.FAILURE -> "error"
  CallEndCause.HUNG_UP -> "hungUp"
  CallEndCause.CANCELED -> "canceled"
  CallEndCause.OTHER_DEVICE_ANSWERED -> "otherDeviceAnswered"
  CallEndCause.INACTIVE -> "inactive"
  CallEndCause.VOIP_CALL_DETECTED -> "voipCallDetected"
  CallEndCause.GSM_CALL_DETECTED -> "gsmCallDetected"
  else -> "none"
}

class SinchCallManager(private val context: Context) {
  var listener: SinchCallManagerListener? = null

  private var client: SinchClient? = null
  private var pendingRegistration: ClientRegistration? = null
  private var pendingPushConfiguration: PushConfiguration? = null
  private val calls = mutableMapOf<String, Call>()
  private var customPushIdField: String? = null
  private var customPushDisplayField: String? = null

  // Proximity-screen-off during an established earpiece call, same as the
  // system Phone app: on while at least one call is established and the
  // speaker isn't in use, off otherwise.
  private val establishedCallIds = mutableSetOf<String>()
  private var isSpeakerEnabled = false
  private var proximityWakeLock: PowerManager.WakeLock? = null

  private val callListener = object : CallListener {
    override fun onCallProgressing(call: Call) {
      listener?.onCallProgressing(call.callId)
    }

    override fun onCallEstablished(call: Call) {
      SinchTelecomManager.reportCallActive(call.callId)
      establishedCallIds.add(call.callId)
      updateProximityMonitoring()
      listener?.onCallEstablished(call.callId)
    }

    override fun onCallEnded(call: Call) {
      calls.remove(call.callId)
      establishedCallIds.remove(call.callId)
      updateProximityMonitoring()
      SinchTelecomManager.reportCallEnded(call.callId)
      listener?.onCallEnded(call.callId, endCauseString(call.details.endCause))
    }
  }

  init {
    SinchTelecomManager.registerPhoneAccount(context.applicationContext)
    SinchTelecomManager.listener = object : SinchTelecomListener {
      override fun onAnswer(callId: String) {
        answerCall(callId)
      }

      override fun onReject(callId: String) {
        hangupCall(callId)
      }

      override fun onDisconnect(callId: String) {
        hangupCall(callId)
      }

      override fun onAnswerExternal(callId: String) {
        listener?.onCallUIAnswered(callId)
      }

      override fun onDeclineExternal(callId: String) {
        listener?.onCallUIDeclined(callId)
      }
    }
  }

  fun registerFcmPush(senderId: String, token: String) {
    pendingPushConfiguration = PushConfiguration.fcmPushConfigurationBuilder()
      .senderID(senderId)
      .registrationToken(token)
      .build()
  }

  // Opts into recognizing the consumer app's own backend's incoming-call
  // push payloads. Until called, every payload is relayed straight to
  // Sinch (see `relayRemotePushNotification`).
  fun configureCustomIncomingCallPush(idField: String, displayField: String) {
    customPushIdField = idField
    customPushDisplayField = displayField
  }

  fun relayRemotePushNotification(payload: Map<String, String>) {
    val idField = customPushIdField
    val callId = idField?.let { payload[it] }
    if (callId != null) {
      val displayName = customPushDisplayField?.let { payload[it] } ?: ""
      reportIncomingCallUI(callId, displayName)
      return
    }

    val result = SinchPush.queryPushNotificationPayload(context.applicationContext, payload)
    client?.relayRemotePushNotification(result)
  }

  // Reports a system call UI (CallKit / Telecom) for a call the consumer
  // app decided to show — from a custom push, a Socket.IO event, or
  // anything else. Independent of `configureCustomIncomingCallPush`.
  fun reportIncomingCallUI(callId: String, displayName: String) {
    SinchTelecomManager.reportExternalIncomingCall(context.applicationContext, callId, displayName)
    listener?.onIncomingCallUIShown(callId, displayName)
  }

  fun configure(appKey: String, environmentHost: String, userId: String) {
    val builder = SinchClient.builder()
      .context(context.applicationContext)
      .applicationKey(appKey)
      .environmentHost(environmentHost)
      .userId(userId)

    pendingPushConfiguration?.let { builder.pushConfiguration(it) }

    val sinchClient = try {
      builder.build()
    } catch (error: IOException) {
      listener?.onClientFailed(error.message ?: "Failed to build Sinch client", -1)
      return
    }

    sinchClient.audioController.enableAutomaticAudioRouting(
      AudioController.AudioRoutingConfig(AudioController.UseSpeakerphone.SPEAKERPHONE_AUTO, true)
    )

    sinchClient.addSinchClientListener(object : SinchClientListener {
      override fun onClientStarted(client: SinchClient) {
        listener?.onClientStarted()
      }

      override fun onClientFailed(client: SinchClient, error: SinchError) {
        listener?.onClientFailed(error.message ?: "Unknown Sinch client error", error.code)
      }

      override fun onCredentialsRequired(clientRegistration: ClientRegistration) {
        pendingRegistration = clientRegistration
        listener?.onRegistrationCredentialsRequired()
      }

      override fun onPushTokenRegistered() {
        listener?.onPushTokenRegistered()
      }

      override fun onPushTokenRegistrationFailed(error: SinchError) {
        listener?.onPushTokenRegistrationFailed(error.message ?: "Unknown Sinch push error", error.code)
      }

      override fun onPushTokenUnregistered() {}
      override fun onPushTokenUnregistrationFailed(error: SinchError) {}

      override fun onUserRegistered() {}
      override fun onUserRegistrationFailed(error: SinchError) {}
    })

    sinchClient.callController.addCallControllerListener(object : CallControllerListener {
      override fun onIncomingCall(callController: CallController, call: Call) {
        track(call)
        SinchTelecomManager.reportIncomingCall(
          context.applicationContext,
          call.callId,
          call.remoteUserId
        )
        listener?.onIncomingCall(call.callId, call.remoteUserId)
      }
    })

    client = sinchClient
  }

  fun start() {
    client?.start()
  }

  fun stop() {
    client?.terminateGracefully()
    client = null
    calls.clear()
    establishedCallIds.clear()
    isSpeakerEnabled = false
    releaseProximityWakeLock()
  }

  fun provideRegistrationCredentials(jwt: String) {
    pendingRegistration?.register(jwt)
    pendingRegistration = null
  }

  fun failRegistration() {
    pendingRegistration?.registerFailed()
    pendingRegistration = null
  }

  fun callUser(userId: String): String {
    val call = client?.callController?.callUser(userId, MediaConstraints(false)) ?: return ""
    track(call)
    return call.callId
  }

  fun callPhoneNumber(phoneNumber: String, callerId: String): String {
    val call = client?.callController?.callPhoneNumber(phoneNumber, callerId) ?: return ""
    track(call)
    return call.callId
  }

  fun callConference(conferenceId: String, callerId: String): String {
    val call = client?.callController?.callConference(conferenceId, callerId) ?: return ""
    track(call)
    return call.callId
  }

  // Turns a call shown via `reportIncomingCallUI` into a real call once you
  // know which Sinch conference to join: joins the conference and
  // re-associates the already-visible system call UI with the resulting
  // real callId. Returns the real callId, or "" on failure.
  fun resolveCallUIToConference(callId: String, conferenceId: String, callerId: String): String {
    val call = client?.callController?.callConference(conferenceId, callerId)
    if (call == null) {
      SinchTelecomManager.reportCallEnded(callId)
      return ""
    }
    track(call)
    SinchTelecomManager.remapCallId(callId, call.callId)
    return call.callId
  }

  // Cleans up a system call UI reported via `reportIncomingCallUI` that
  // couldn't be resolved into a real call.
  fun dismissCallUI(callId: String) {
    SinchTelecomManager.reportCallEnded(callId)
  }

  fun updateIncomingCallDisplayName(callId: String, displayName: String) {
    SinchTelecomManager.updateCallerDisplayName(callId, displayName)
  }

  fun answerCall(callId: String) {
    calls[callId]?.answer()
  }

  fun hangupCall(callId: String) {
    calls[callId]?.hangup()
  }

  fun sendDTMF(callId: String, key: String): Boolean {
    val call = calls[callId] ?: return false
    call.sendDTMF(key)
    return true
  }

  fun setMuted(muted: Boolean) {
    val audioController = client?.audioController ?: return
    if (muted) audioController.mute() else audioController.unmute()
  }

  fun setSpeakerEnabled(enabled: Boolean) {
    val audioController = client?.audioController ?: return
    if (enabled) audioController.enableSpeaker() else audioController.disableSpeaker()
    isSpeakerEnabled = enabled
    updateProximityMonitoring()
  }

  private fun updateProximityMonitoring() {
    val shouldEnable = establishedCallIds.isNotEmpty() && !isSpeakerEnabled
    if (shouldEnable) acquireProximityWakeLock() else releaseProximityWakeLock()
  }

  private fun acquireProximityWakeLock() {
    val lock = proximityWakeLock ?: run {
      val powerManager =
        context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
          ?: return
      if (!powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
        return
      }
      @Suppress("DEPRECATION")
      powerManager.newWakeLock(
        PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
        "SinchCalling:ProximityWakeLock",
      ).also { proximityWakeLock = it }
    }
    if (!lock.isHeld) {
      lock.acquire(10 * 60 * 1000L)
    }
  }

  private fun releaseProximityWakeLock() {
    proximityWakeLock?.let { if (it.isHeld) it.release() }
  }

  private fun track(call: Call) {
    calls[call.callId] = call
    call.addCallListener(callListener)
  }
}
