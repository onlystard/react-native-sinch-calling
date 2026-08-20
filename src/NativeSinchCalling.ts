import { TurboModuleRegistry, type TurboModule } from 'react-native';
import type { CodegenTypes } from 'react-native';

export interface ClientStartFailedEvent {
  message: string;
  code: number;
}

export interface IncomingCallEvent {
  callId: string;
  remoteUserId: string;
}

export interface CallEvent {
  callId: string;
}

export interface CallEndedEvent {
  callId: string;
  endCause: string;
}

export interface VoipPushTokenEvent {
  token: string;
}

export interface CallUIAnsweredEvent {
  callId: string;
}

export interface CallUIDeclinedEvent {
  callId: string;
}

export interface IncomingCallUIEvent {
  callId: string;
  displayName: string;
}

export interface IncomingCallUICancelledEvent {
  callId: string;
}

export interface Spec extends TurboModule {
  configure(appKey: string, environmentHost: string, userId: string): void;
  start(): void;
  stop(): void;

  provideRegistrationCredentials(jwt: string): void;
  failRegistration(message: string): void;

  callUser(userId: string): string;
  // PSTN call. `callerId` is the caller-ID number shown to the recipient
  // (Android only — the iOS SDK derives it from the application's
  // provisioned voice number and ignores this argument).
  callPhoneNumber(phoneNumber: string, callerId: string): string;
  // Joins a Sinch conference room by id (e.g. a backend-bridged PSTN leg).
  // `callerId` is Android-only, same caveat as `callPhoneNumber`.
  callConference(conferenceId: string, callerId: string): string;
  answerCall(callId: string): void;
  hangupCall(callId: string): void;
  setMuted(muted: boolean): void;
  setSpeakerEnabled(enabled: boolean): void;
  // Sends a DTMF tone on an active call. `key` must be one of [0-9, #, *, A-D].
  // Returns false if the call doesn't exist or the tone couldn't be sent.
  sendDTMF(callId: string, key: string): boolean;

  // Android only. Must be called before `configure()`. No-op on iOS, which
  // uses SINManagedPush (see `enablePushNotifications`) instead of a
  // caller-supplied token.
  registerFcmPush(senderId: string, token: string): void;

  // iOS only. Sets up SINManagedPush (VoIP push via PushKit) so incoming
  // calls can be relayed while the app is backgrounded. No-op on Android,
  // which relies on `registerFcmPush` + `relayRemotePushNotification`.
  enablePushNotifications(useProductionAps: boolean): void;

  // Android only. Forward the raw FCM data payload here (e.g. from
  // `messaging().onMessage()` / `setBackgroundMessageHandler()`). By
  // default every payload is treated as a Sinch-relayed push and turned
  // into a normal `onIncomingCall` event. If `configureCustomIncomingCallPush`
  // has been called, payloads containing both configured fields instead call
  // `reportIncomingCallUI` directly and skip the Sinch relay for that push.
  // No-op on iOS, which applies the same logic internally via PushKit.
  relayRemotePushNotification(payload: Object): void;

  // Opts into recognizing your own backend's incoming-call push payloads.
  // `idField`/`displayField` are the keys your payload uses for the call id
  // and the caller's display name/number — e.g. `configureCustomIncomingCallPush("callId", "callerNumber")`
  // for a payload shaped `{ callId, callerNumber }`. Until this is called,
  // `relayRemotePushNotification` and the iOS VoIP push handler always
  // relay to Sinch (the library's default, backend-agnostic behavior).
  configureCustomIncomingCallPush(idField: string, displayField: string): void;

  // Opts into recognizing your own backend's cancel/end push payloads —
  // e.g. `configureCustomCancelCallPush("type", "call_cancelled")` for a
  // payload shaped `{ type: "call_cancelled", callId, ... }`. Checked before
  // the `configureCustomIncomingCallPush` id-field match, so a push carrying
  // both the id field and this cancel marker ends the already-shown call UI
  // instead of being reported as a new incoming call. Has no effect until
  // `configureCustomIncomingCallPush` has also been called (there'd be no
  // `callId` field to key off of otherwise).
  configureCustomCancelCallPush(typeField: string, cancelValue: string): void;

  // Reports a system call UI (CallKit / Telecom) for a call your own app
  // logic decided to show — from a custom push, a Socket.IO event, or
  // anything else. Not tied to any particular transport: call this directly
  // whenever you want to ring, independent of `configureCustomIncomingCallPush`.
  reportIncomingCallUI(callId: string, displayName: string): void;

  // Turns a call shown via `reportIncomingCallUI` (or a configured custom
  // push) into a real call once you know which Sinch conference to join:
  // joins the conference and re-associates the already-visible system call
  // UI with the resulting real callId. Returns the real callId, or "" on
  // failure.
  resolveCallUIToConference(
    callId: string,
    conferenceId: string,
    callerId: string
  ): string;

  // Cleans up a system call UI reported via `reportIncomingCallUI` that
  // couldn't be resolved into a real call (e.g. your backend rejected it).
  dismissCallUI(callId: string): void;

  // Overrides the caller name shown on the system call UI (e.g. once a
  // caller-ID lookup resolves) without disturbing the phone number already
  // reported as the handle.
  updateIncomingCallDisplayName(callId: string, displayName: string): void;

  onClientStarted: CodegenTypes.EventEmitter<void>;
  onClientStartFailed: CodegenTypes.EventEmitter<ClientStartFailedEvent>;
  onRegistrationCredentialsRequired: CodegenTypes.EventEmitter<void>;

  onIncomingCall: CodegenTypes.EventEmitter<IncomingCallEvent>;
  onCallProgressing: CodegenTypes.EventEmitter<CallEvent>;
  onCallEstablished: CodegenTypes.EventEmitter<CallEvent>;
  onCallEnded: CodegenTypes.EventEmitter<CallEndedEvent>;

  onPushTokenRegistered: CodegenTypes.EventEmitter<void>;
  onPushTokenRegistrationFailed: CodegenTypes.EventEmitter<ClientStartFailedEvent>;

  // iOS only: fires with the hex-encoded PushKit VoIP token whenever it's
  // issued or rotated. Forward this to your backend so it can address VoIP
  // pushes to this device.
  onVoipPushTokenUpdated: CodegenTypes.EventEmitter<VoipPushTokenEvent>;

  // A system call UI was reported — either because you called
  // `reportIncomingCallUI` yourself, or because a configured custom push
  // was auto-detected (see `configureCustomIncomingCallPush`). Useful for
  // e.g. kicking off a caller-ID lookup the moment a call starts ringing.
  onIncomingCallUIShown: CodegenTypes.EventEmitter<IncomingCallUIEvent>;

  // A configured cancel push (see `configureCustomCancelCallPush`) arrived
  // for a call previously shown via a configured custom incoming-call push.
  // The system call UI has already been torn down natively by the time this
  // fires — use it to clean up any app-side state keyed by `callId` (e.g. a
  // pending caller-ID lookup).
  onIncomingCallUICancelled: CodegenTypes.EventEmitter<IncomingCallUICancelledEvent>;

  // The system call UI shown via `reportIncomingCallUI` (or a configured
  // custom push) was answered/declined. Handle these however your backend
  // expects — e.g. call your own accept/decline endpoint, then
  // `resolveCallUIToConference` or `dismissCallUI`.
  onCallUIAnswered: CodegenTypes.EventEmitter<CallUIAnsweredEvent>;
  onCallUIDeclined: CodegenTypes.EventEmitter<CallUIDeclinedEvent>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('SinchCalling');
