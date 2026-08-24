package com.sinchcalling

import android.content.Context
import com.sinchcalling.telecom.SinchTelecomManager

/**
 * Mirrors iOS's `SinchCallingBootstrap.eagerlyRegisterForVoipPush` — a
 * plain entry point a consumer app's `Application.onCreate()` can call
 * before React Native (or any Activity) exists.
 *
 * A killed app woken purely by an incoming-call FCM push runs its
 * background handler (e.g. `messaging().setBackgroundMessageHandler` on the
 * JS side) in a process that never mounts the app's React component tree —
 * so a JS-side `useEffect` that would otherwise call
 * `SinchCalling.configureCustomIncomingCallPush(...)` (typically on login,
 * deep inside the navigation tree) never runs there, and
 * `SinchCallManager.relayRemotePushNotification` has no way to recognize
 * the custom incoming/cancel-call push shape. `Application.onCreate()` runs
 * for every process launch — including one spun up solely to handle a
 * background push — so configuring it there guarantees it's set before any
 * push can arrive, matching the iOS bootstrap's guarantee.
 */
object SinchCallingBootstrap {
  @JvmStatic
  fun eagerlyConfigureCustomIncomingCallPush(context: Context) {
    SinchTelecomManager.registerPhoneAccount(context.applicationContext)
    SinchTelecomManager.configureCustomIncomingCallPush("callId", "callerNumber")
    SinchTelecomManager.configureCustomCancelCallPush("type", "call_cancelled", "reason")
  }
}
