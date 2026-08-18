package com.sinchcalling.telecom

import android.telecom.Connection
import android.telecom.DisconnectCause

class SinchConnection(var callId: String) : Connection() {
  init {
    setConnectionProperties(PROPERTY_SELF_MANAGED)
    setAudioModeIsVoip(true)
  }

  override fun onAnswer() {
    if (SinchTelecomManager.isExternallyReported(callId)) {
      // Not a real call yet — stay ringing until the consumer app resolves
      // it and `remapCallId` + `reportCallActive` bring it to the active state.
      SinchTelecomManager.listener?.onAnswerExternal(callId)
    } else {
      setActive()
      SinchTelecomManager.listener?.onAnswer(callId)
    }
  }

  override fun onReject() {
    setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
    destroy()
    val wasExternallyReported = SinchTelecomManager.isExternallyReported(callId)
    SinchTelecomManager.unregisterConnection(callId)
    SinchTelecomManager.clearExternallyReported(callId)
    if (wasExternallyReported) {
      SinchTelecomManager.listener?.onDeclineExternal(callId)
    } else {
      SinchTelecomManager.listener?.onReject(callId)
    }
  }

  override fun onDisconnect() {
    setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
    destroy()
    SinchTelecomManager.unregisterConnection(callId)
    SinchTelecomManager.clearExternallyReported(callId)
    SinchTelecomManager.listener?.onDisconnect(callId)
  }
}
