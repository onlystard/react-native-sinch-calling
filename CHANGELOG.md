# Changelog

All notable changes to this project are documented in this file.

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
