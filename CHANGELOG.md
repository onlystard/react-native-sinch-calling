# Changelog

All notable changes to this project are documented in this file.

## 0.7.0

### Fixed

- `SinchCalling.eagerlyRegisterForVoipPush()` (added in 0.6.0) was unreachable from a consumer app's Swift code (`Module 'SinchCalling' has no member named 'eagerlyRegisterForVoipPush'`) — the podspec marked its only header (`SinchCalling.h`) as private, which hid it from anything outside this pod's own target. The RN TurboModule bridge never needed it public (it dispatches via the ObjC runtime), but a plain `import SinchCalling` from an app's `AppDelegate` does.

## 0.6.0

### Added

- `SinchCalling.eagerlyRegisterForVoipPush()` (iOS, native — not exposed to JS). Call it as the first line of `application(_:didFinishLaunchingWithOptions:)` to register `PKPushRegistry` and configure the custom incoming/cancel-call push fields immediately, independent of React Native. Without this, VoIP push registration only happened once the JS bridge called `enablePushNotifications()` deep into app bootstrap (after the RN bundle loaded, state rehydrated, etc.) — too late for the app to reliably report a call when iOS relaunches it from killed purely to deliver that push.

### Changed

- `SinchPushManager` (iOS) now reports directly to `SinchCallKitManager` for a detected custom incoming/cancel push, instead of only notifying its delegate (which the JS-driven `SinchCalling` TurboModule instance implements). This means CallKit gets its report even if the delegate/JS bridge doesn't exist yet — the delegate now exists purely to notify JS (e.g. for a caller-ID lookup), not to perform the report itself.
- `SinchCallManager`, `SinchPushManager`, and `SinchCallKitManager` (iOS) are now shared singletons across the process rather than per-`SinchCalling`-instance, so the eager native call above and the later JS-driven TurboModule instance operate on the same `PKPushRegistry`/`CXProvider` instead of each creating their own (a single `PKPushRegistry` only supports one delegate per push type).

## 0.5.0

### Added

- `configureCustomCancelCallPush(typeField, cancelValue)` — opt in to recognizing your backend's cancel/end push payloads (e.g. `{ type: "call_cancelled", callId, ... }`), so a push carrying the configured cancel marker ends the already-shown call UI (`reportCallEnded` / `SinchTelecomManager.reportCallEnded`) instead of being reported as a new incoming call.
- `onIncomingCallUICancelled` event, fired once the cancel push has been handled and the system call UI torn down, so app code can clean up any state keyed by `callId`.

### Fixed

- Previously, a "cancel this call" push reusing the same `idField` as the configured incoming-call push (a natural payload design, since both need to identify which call they're about) was indistinguishable from a genuinely new incoming call — the push handler only checked for the id field's presence, so cancel pushes rang as phantom incoming calls instead of dismissing the real one.

## 0.3.0

### Added

- Automatic proximity-screen-off management during established earpiece calls, same behavior as the system Phone app: on while a call is `ESTABLISHED` and the speaker is off, off otherwise (speaker toggle or call end). Handled entirely inside `SinchCallManager` on both platforms — no new public API.

## 0.2.0

### Added

- `callPhoneNumber(phoneNumber, callerId)` — place a PSTN call to a real phone number.
- `callConference(conferenceId, callerId)` — join a Sinch conference room by id.
- `sendDTMF(callId, key)` — send DTMF tones on an active call.
- `updateIncomingCallDisplayName(callId, displayName)` — override the caller name shown on the system call UI.
- Generic custom incoming-call flow, for backends that park/bridge calls instead of routing them directly to a registered Sinch client:
  - `configureCustomIncomingCallPush(idField, displayField)` — opt in to recognizing your own backend's push payload shape.
  - `reportIncomingCallUI(callId, displayName)` — report a system call UI for any reason, independent of push.
  - `resolveCallUIToConference(callId, conferenceId, callerId)` — turn a shown call UI into a real call.
  - `dismissCallUI(callId)` — clean up a call UI that couldn't be resolved.
  - Events: `onIncomingCallUIShown`, `onCallUIAnswered`, `onCallUIDeclined`.
- `onVoipPushTokenUpdated` (iOS) — fires with the hex-encoded PushKit VoIP token on issuance/rotation.

### Changed

- iOS VoIP push handling now uses `PKPushRegistry` directly instead of `SINManagedPush`, so it can also support the custom incoming-call flow above on the same registration. Default behavior (no custom push configured) is unchanged — every push still relays straight to Sinch.
- CallKit (iOS) now reports phone-number handles with the `.phoneNumber` type (previously always `.generic`), so the system UI formats and matches PSTN caller IDs correctly.
- Android's self-managed `Connection` address now uses the `tel:` URI scheme for phone-number callers, matching the iOS change above.

## 0.1.0

Initial release: app-to-app calling (`callUser`), client registration lifecycle, CallKit/`ConnectionService` system call UI, mute/speaker controls, and Android FCM / iOS `SINManagedPush` push delivery.
