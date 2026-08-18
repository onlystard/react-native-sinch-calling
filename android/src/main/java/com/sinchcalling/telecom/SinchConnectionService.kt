package com.sinchcalling.telecom

import android.net.Uri
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

// A PSTN incoming call's remote id is the caller's phone number; an
// app-to-app call's is our own opaque Sinch userId. Distinguishing them lets
// the system dialer show/format the caller ID correctly.
private val PHONE_NUMBER_REGEX = Regex("^\\+?[0-9]{6,15}$")

class SinchConnectionService : ConnectionService() {
  override fun onCreateIncomingConnection(
    connectionManagerPhoneAccount: PhoneAccountHandle,
    request: ConnectionRequest
  ): Connection {
    val callId = request.extras?.getString(SinchTelecomManager.EXTRA_CALL_ID) ?: ""
    val remoteUserId = request.extras?.getString(SinchTelecomManager.EXTRA_REMOTE_USER_ID) ?: ""

    val connection = SinchConnection(callId)
    connection.setCallerDisplayName(remoteUserId, TelecomManager.PRESENTATION_ALLOWED)
    val addressUri = if (PHONE_NUMBER_REGEX.matches(remoteUserId)) {
      Uri.fromParts(PhoneAccount.SCHEME_TEL, remoteUserId, null)
    } else {
      Uri.fromParts("sinch", remoteUserId, null)
    }
    connection.setAddress(addressUri, TelecomManager.PRESENTATION_ALLOWED)
    connection.setRinging()

    SinchTelecomManager.registerConnection(callId, connection)
    return connection
  }
}
