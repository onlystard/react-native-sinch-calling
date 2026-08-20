package com.sinchcalling

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap

class SinchCallingModule(reactContext: ReactApplicationContext) :
  NativeSinchCallingSpec(reactContext) {

  private val callManager = SinchCallManager(reactContext).apply {
    listener = object : SinchCallManagerListener {
      override fun onClientStarted() {
        emitOnClientStarted()
      }

      override fun onClientFailed(message: String, code: Int) {
        emitOnClientStartFailed(
          Arguments.createMap().apply {
            putString("message", message)
            putInt("code", code)
          }
        )
      }

      override fun onRegistrationCredentialsRequired() {
        emitOnRegistrationCredentialsRequired()
      }

      override fun onIncomingCall(callId: String, remoteUserId: String) {
        emitOnIncomingCall(
          Arguments.createMap().apply {
            putString("callId", callId)
            putString("remoteUserId", remoteUserId)
          }
        )
      }

      override fun onCallProgressing(callId: String) {
        emitOnCallProgressing(Arguments.createMap().apply { putString("callId", callId) })
      }

      override fun onCallEstablished(callId: String) {
        emitOnCallEstablished(Arguments.createMap().apply { putString("callId", callId) })
      }

      override fun onCallEnded(callId: String, endCause: String) {
        emitOnCallEnded(
          Arguments.createMap().apply {
            putString("callId", callId)
            putString("endCause", endCause)
          }
        )
      }

      override fun onPushTokenRegistered() {
        emitOnPushTokenRegistered()
      }

      override fun onPushTokenRegistrationFailed(message: String, code: Int) {
        emitOnPushTokenRegistrationFailed(
          Arguments.createMap().apply {
            putString("message", message)
            putInt("code", code)
          }
        )
      }

      override fun onIncomingCallUIShown(callId: String, displayName: String) {
        emitOnIncomingCallUIShown(
          Arguments.createMap().apply {
            putString("callId", callId)
            putString("displayName", displayName)
          }
        )
      }

      override fun onCallUIAnswered(callId: String) {
        emitOnCallUIAnswered(Arguments.createMap().apply { putString("callId", callId) })
      }

      override fun onCallUIDeclined(callId: String) {
        emitOnCallUIDeclined(Arguments.createMap().apply { putString("callId", callId) })
      }

      override fun onIncomingCallUICancelled(callId: String) {
        emitOnIncomingCallUICancelled(Arguments.createMap().apply { putString("callId", callId) })
      }
    }
  }

  override fun configure(appKey: String, environmentHost: String, userId: String) {
    callManager.configure(appKey, environmentHost, userId)
  }

  override fun start() {
    callManager.start()
  }

  override fun stop() {
    callManager.stop()
  }

  override fun provideRegistrationCredentials(jwt: String) {
    callManager.provideRegistrationCredentials(jwt)
  }

  override fun failRegistration(message: String) {
    callManager.failRegistration()
  }

  override fun callUser(userId: String): String {
    return callManager.callUser(userId)
  }

  override fun callPhoneNumber(phoneNumber: String, callerId: String): String {
    return callManager.callPhoneNumber(phoneNumber, callerId)
  }

  override fun callConference(conferenceId: String, callerId: String): String {
    return callManager.callConference(conferenceId, callerId)
  }

  override fun configureCustomIncomingCallPush(idField: String, displayField: String) {
    callManager.configureCustomIncomingCallPush(idField, displayField)
  }

  override fun configureCustomCancelCallPush(typeField: String, cancelValue: String) {
    callManager.configureCustomCancelCallPush(typeField, cancelValue)
  }

  override fun reportIncomingCallUI(callId: String, displayName: String) {
    callManager.reportIncomingCallUI(callId, displayName)
  }

  override fun resolveCallUIToConference(
    callId: String,
    conferenceId: String,
    callerId: String
  ): String {
    return callManager.resolveCallUIToConference(callId, conferenceId, callerId)
  }

  override fun dismissCallUI(callId: String) {
    callManager.dismissCallUI(callId)
  }

  override fun updateIncomingCallDisplayName(callId: String, displayName: String) {
    callManager.updateIncomingCallDisplayName(callId, displayName)
  }

  override fun answerCall(callId: String) {
    callManager.answerCall(callId)
  }

  override fun hangupCall(callId: String) {
    callManager.hangupCall(callId)
  }

  override fun sendDTMF(callId: String, key: String): Boolean {
    return callManager.sendDTMF(callId, key)
  }

  override fun setMuted(muted: Boolean) {
    callManager.setMuted(muted)
  }

  override fun setSpeakerEnabled(enabled: Boolean) {
    callManager.setSpeakerEnabled(enabled)
  }

  override fun registerFcmPush(senderId: String, token: String) {
    callManager.registerFcmPush(senderId, token)
  }

  override fun enablePushNotifications(useProductionAps: Boolean) {
    // iOS only — Android push is wired via registerFcmPush + relayRemotePushNotification.
  }

  override fun relayRemotePushNotification(payload: ReadableMap) {
    val data = mutableMapOf<String, String>()
    val iterator = payload.keySetIterator()
    while (iterator.hasNextKey()) {
      val key = iterator.nextKey()
      data[key] = payload.getString(key) ?: continue
    }
    callManager.relayRemotePushNotification(data)
  }

  companion object {
    const val NAME = NativeSinchCallingSpec.NAME
  }
}
