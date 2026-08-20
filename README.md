# react-native-sinch-calling

React Native TurboModule wrapper for the Sinch Voice Calling SDK (Android & iOS): app-to-app calls, PSTN calls, and Sinch conference rooms — voice only, no video.

## Scope & architecture

This library wraps the native Sinch SDKs (`SinchRTC` on iOS, `com.sinch:voice-video-android` on Android) behind a single TurboModule. Incoming/outgoing calls always get reported to the OS call UI — CallKit on iOS, a self-managed `ConnectionService` on Android — so calls behave like a real phone call (lock-screen UI, works while backgrounded) without any extra code on your end.

The library never tries to guess what your backend's push payloads mean. By default it only recognizes Sinch's own push format (app-to-app calls, standard PSTN-to-registered-user routing). If your backend has its own "ring the app" signal — e.g. it parks an inbound PSTN call and pushes a custom payload telling the app to show a call screen before bridging into a Sinch conference — you opt into that explicitly (see [Custom incoming-call flows](#custom-incoming-call-flows) below) rather than the library inferring it from your payload shape.

## Prerequisites

Before wiring this into your app, you need:

1. **A Sinch account** with an Application Key/Secret (Sinch Dashboard).
2. **A backend endpoint that mints a registration JWT.** Sinch does not allow signing this JWT inside the app — the Application Secret must stay server-side. Your backend needs an endpoint (behind your existing auth) that:
   - Identifies the calling user from your own session/auth token.
   - Signs a JWT with:
     ```
     header: { alg: "HS256", kid: "hkdfv1-YYYYMMDD" }   // YYYYMMDD = current UTC date
     claims: {
       iss: "//rtc.sinch.com/applications/{applicationKey}",
       sub: "//rtc.sinch.com/applications/{applicationKey}/users/{userId}",
       iat, exp, nonce
     }
     signingKey = HMAC-SHA256(applicationSecret, "YYYYMMDD")
     ```
   - Returns the JWT to the app.
3. **A stable per-user identifier** (`userId`) — any string matching `[A-Za-z0-9-_=]{1,255}`. Use your own user id (e.g. a database id), not something that can change (email/phone).
4. **Push credentials on the Sinch Dashboard**: an APNs key/cert for VoIP push (iOS), and your Firebase Sender ID (Android) if you want calls to be delivered while the app is backgrounded.

## Installation

```sh
npm install react-native-sinch-calling
```

### iOS

- Minimum deployment target: iOS 15.
- Run `pod install` after installing (pulls in the `SinchRTC` pod).
- In your app's Info.plist:
  ```xml
  <key>NSMicrophoneUsageDescription</key>
  <string>We need microphone access to make calls.</string>
  <key>UIBackgroundModes</key>
  <array>
    <string>audio</string>
    <string>voip</string>
  </array>
  ```
- Enable the **Push Notifications** capability in Xcode (adds `aps-environment` to your entitlements) — required for `enablePushNotifications()`.

### Android

- Minimum SDK: 24.
- Permissions and the `ConnectionService` declaration are already bundled in this library's manifest — you don't need to add anything for the base call flow.
- If you want incoming calls while the app is backgrounded, wire your own FCM setup (e.g. `@react-native-firebase/messaging`) and forward messages to `registerFcmPush` / `relayRemotePushNotification` (see below).

## Usage

```tsx
import { SinchCalling } from 'react-native-sinch-calling';

// 1. Provide a way to fetch a registration JWT from your backend.
//    Called automatically whenever the SDK (re)registers the user.
SinchCalling.setRegistrationCredentialsProvider(async () => {
  const response = await fetch('https://your-api.example.com/sinch/registration-jwt', {
    headers: { Authorization: `Bearer ${yourAppAccessToken}` },
  });
  const { jwt } = await response.json();
  return jwt;
});

// 2. (Optional) Android: attach your FCM sender id + token before configure(),
//    so incoming calls can be delivered while the app is backgrounded.
SinchCalling.registerFcmPush(fcmSenderId, fcmToken);

// 2b. (Optional) iOS: enable VoIP push via PushKit/SINManagedPush.
SinchCalling.enablePushNotifications(/* useProductionAps */ false);

// 3. Configure + start the client.
SinchCalling.configure({
  appKey: 'YOUR_SINCH_APPLICATION_KEY',
  environmentHost: 'ocra.api.sinch.com',
  userId: currentUser.id,
});
SinchCalling.start();

// 4. Listen for lifecycle + call events.
SinchCalling.onClientStarted(() => console.log('registered'));
SinchCalling.onClientStartFailed(({ message, code }) => console.warn(message, code));

SinchCalling.onIncomingCall(({ callId, remoteUserId }) => { /* update UI */ });
SinchCalling.onCallProgressing(({ callId }) => { /* ringing/dialing */ });
SinchCalling.onCallEstablished(({ callId }) => { /* connected */ });
SinchCalling.onCallEnded(({ callId, endCause }) => { /* 'hungUp' | 'denied' | 'noAnswer' | 'error' | ... */ });

// 5. Place / control calls.
const callId = SinchCalling.callUser('other-user-id');
SinchCalling.answerCall(callId);
SinchCalling.hangupCall(callId);
SinchCalling.setMuted(true);
SinchCalling.setSpeakerEnabled(true);
```

### Calling a phone number or joining a conference

```ts
// PSTN: call a real phone number (E.164, e.g. "+14155550101").
// `callerId` is the caller-ID shown to the recipient — required on
// Android, ignored on iOS (derived from your application's provisioned
// voice number instead). Same caveat applies to `callConference` below.
const callId = SinchCalling.callPhoneNumber('+14155550101', '+14155550100');

// Join a Sinch conference room by id (e.g. one your backend created and
// bridged a PSTN leg into).
const callId = SinchCalling.callConference('conf_abc123', '+14155550100');

// Send DTMF tones on an active call (0-9, #, *, A-D).
SinchCalling.sendDTMF(callId, '5');
```

### Custom incoming-call flows

Some backends don't let Sinch route calls directly to a registered client — e.g. an inbound PSTN call gets parked and bridged into a Sinch conference only after your backend decides to connect it (fraud checks, business hours routing, a "ring multiple agents" pattern, etc). For that, your backend sends your app its own push/socket signal, and you tell this library about it explicitly:

```ts
// Recognize your own backend's push payload shape (both platforms).
// idField/displayField are the keys your payload uses. Until this is
// called, every push is treated as a normal Sinch-relayed push.
SinchCalling.configureCustomIncomingCallPush('callId', 'callerNumber');
// A payload like { callId: 'abc', callerNumber: '+1415...' } arriving via
// relayRemotePushNotification (Android) or the native VoIP push handler
// (iOS) now reports a system call UI immediately instead of relaying to Sinch.

// You can also report a call UI directly, independent of any push — e.g.
// from a Socket.IO event received while the app is in the foreground:
SinchCalling.reportIncomingCallUI('abc', '+14155550101');

// Fires whenever a call UI is shown (from either path above) — a good
// place to kick off a caller-ID lookup and upgrade the display name.
SinchCalling.onIncomingCallUIShown(({ callId, displayName }) => {
  lookupCallerName(displayName).then(name => {
    SinchCalling.updateIncomingCallDisplayName(callId, name);
  });
});

// When the rep answers/declines from the system call UI, call your own
// backend, then resolve or dismiss the call UI accordingly.
SinchCalling.onCallUIAnswered(async ({ callId }) => {
  const { conferenceId, callerNumber } = await acceptOnYourBackend(callId);
  const realCallId = SinchCalling.resolveCallUIToConference(callId, conferenceId, callerNumber);
  if (!realCallId) {
    // your backend accepted but joining failed — clean up the call UI
    SinchCalling.dismissCallUI(callId);
  }
});

SinchCalling.onCallUIDeclined(({ callId }) => {
  declineOnYourBackend(callId);
});

// If your backend can also cancel/end a call that's already ringing on this
// device (caller hung up, answered on another device, forwarded elsewhere,
// etc.), it needs a way to tell a "cancel" push apart from a "new call" push
// — reusing the same `idField` for both (a natural design, since both need
// to say which call they're about) would otherwise make every cancel push
// look like a new incoming call. Configure the field/value your backend
// uses to mark a cancel, e.g. `{ type: "call_cancelled", callId, reason }`:
SinchCalling.configureCustomCancelCallPush('type', 'call_cancelled');

// Fires once a cancel push has ended the system call UI natively — clean up
// any app-side state keyed by `callId` (a pending caller-ID lookup, etc.).
SinchCalling.onIncomingCallUICancelled(({ callId }) => {
  cleanUpPendingCallState(callId);
});
```

This is the one place iOS can't be fully "hands off": Apple requires every VoIP push to synchronously trigger a CallKit report from inside the native push handler, so the payload-shape check happens natively (using the field names you configured) rather than round-tripping through JS first.

### Android: forwarding FCM pushes

Incoming calls arrive over the app's live connection to Sinch while it's in the foreground. For calls to arrive while backgrounded, forward FCM messages to the bridge from your own Firebase setup:

```ts
import messaging from '@react-native-firebase/messaging';
import { SinchCalling } from 'react-native-sinch-calling';

messaging().setBackgroundMessageHandler(async (remoteMessage) => {
  SinchCalling.relayRemotePushNotification(remoteMessage.data ?? {});
});
messaging().onMessage(async (remoteMessage) => {
  SinchCalling.relayRemotePushNotification(remoteMessage.data ?? {});
});
```

This only works while the app **process** is alive (backgrounded, not killed) — the JS runtime has to be running to receive the FCM callback. iOS doesn't have this limitation: VoIP push wakes the app process via PushKit even when fully killed.

### Retrying after a failure

The SDK doesn't expose a distinct "reconnecting" callback — only `onClientStartFailed` (client-level) and `onCallEnded` with `endCause: 'error'` (call-level). Both events include a `code` (native error code) you can use to decide whether to retry:

```ts
SinchCalling.onClientStartFailed(({ code }) => {
  // e.g. retry SinchCalling.start() with backoff for network-ish codes,
  // surface a hard error to the user for anything else.
});
```

## API

| Method | Platforms | Notes |
| --- | --- | --- |
| `configure({ appKey, environmentHost, userId })` | both | |
| `start()` / `stop()` | both | |
| `setRegistrationCredentialsProvider(fn)` | both | Answers the JWT challenge automatically |
| `callUser(userId)` → `callId` | both | App-to-app call |
| `callPhoneNumber(phoneNumber, callerId)` → `callId` | both | PSTN call. `callerId` is Android-only |
| `callConference(conferenceId, callerId)` → `callId` | both | Joins a Sinch conference room. `callerId` is Android-only |
| `sendDTMF(callId, key)` → `boolean` | both | `key` is one of `[0-9, #, *, A-D]` |
| `answerCall(callId)` / `hangupCall(callId)` | both | |
| `setMuted(muted)` / `setSpeakerEnabled(enabled)` | both | |
| `registerFcmPush(senderId, token)` | Android | Call before `configure()`. No-op on iOS |
| `enablePushNotifications(useProductionAps)` | iOS | No-op on Android |
| `relayRemotePushNotification(payload)` | Android | No-op on iOS |
| `configureCustomIncomingCallPush(idField, displayField)` | both | Opt-in — see [Custom incoming-call flows](#custom-incoming-call-flows) |
| `configureCustomCancelCallPush(typeField, cancelValue)` | both | Opt-in — distinguishes a cancel/end push from a new-call push, see [Custom incoming-call flows](#custom-incoming-call-flows) |
| `reportIncomingCallUI(callId, displayName)` | both | Shows a call UI for any reason you decide to |
| `resolveCallUIToConference(callId, conferenceId, callerId)` → `callId` | both | Turns a shown call UI into a real call |
| `dismissCallUI(callId)` | both | Cleans up a call UI that couldn't be resolved |
| `updateIncomingCallDisplayName(callId, displayName)` | both | Overrides the shown caller name |

Events: `onClientStarted`, `onClientStartFailed`, `onRegistrationCredentialsRequired` (internal — handled by `setRegistrationCredentialsProvider`), `onIncomingCall`, `onCallProgressing`, `onCallEstablished`, `onCallEnded`, `onPushTokenRegistered`, `onPushTokenRegistrationFailed`, `onVoipPushTokenUpdated` (iOS — forward to your backend), `onIncomingCallUIShown`, `onCallUIAnswered`, `onCallUIDeclined`, `onIncomingCallUICancelled`.

## Known limitations

- If the app is fully killed (not just backgrounded), Android incoming calls won't arrive — `relayRemotePushNotification` needs the JS runtime alive. iOS handles this correctly via PushKit.
- CallKit/ConnectionService setup happens lazily when JS first requires the native module. For maximum reliability on a killed app, a production app should also register the VoIP push handler from the host app's `AppDelegate`, which is outside the scope of a pure JS-facing library — see Sinch's own CallKit guide if you need this.
- If a call is answered/ended from your own JS UI while the native CallKit/Telecom screen is also showing (e.g. app was foregrounded when the push arrived), the system call UI may not always dismiss in perfect sync.
- `configureCustomIncomingCallPush` detection on iOS happens synchronously inside the native VoIP push handler (an Apple requirement) — it can't wait on any JS logic, only the field names you configured up front.

## Contributing

- [Development workflow](CONTRIBUTING.md#development-workflow)
- [Sending a pull request](CONTRIBUTING.md#sending-a-pull-request)
- [Code of conduct](CODE_OF_CONDUCT.md)

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
