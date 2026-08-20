# Changelog

All notable changes to this project are documented in this file.

## 0.4.0

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
