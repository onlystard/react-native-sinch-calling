import type { EventSubscription } from 'react-native';
import NativeSinchCalling, {
  type CallEndedEvent,
  type CallEvent,
  type ClientStartFailedEvent,
  type IncomingCallEvent,
  type CallUIAnsweredEvent,
  type CallUIDeclinedEvent,
  type IncomingCallUIEvent,
  type IncomingCallUICancelledEvent,
  type VoipPushTokenEvent,
} from './NativeSinchCalling';
import type {
  RegistrationCredentialsProvider,
  SinchClientConfig,
} from './types';

class SinchCalling {
  private credentialsProviderSubscription: EventSubscription | null = null;

  configure(config: SinchClientConfig): void {
    NativeSinchCalling.configure(
      config.appKey,
      config.environmentHost,
      config.userId
    );
  }

  start(): void {
    NativeSinchCalling.start();
  }

  stop(): void {
    NativeSinchCalling.stop();
  }

  /**
   * Sinch requires a JWT (signed with your app secret on your backend) every
   * time the client (re)registers. Call this once after `configure()` so the
   * bridge can answer that challenge whenever the native SDK raises it.
   */
  setRegistrationCredentialsProvider(
    provider: RegistrationCredentialsProvider
  ): void {
    this.credentialsProviderSubscription?.remove();
    this.credentialsProviderSubscription =
      NativeSinchCalling.onRegistrationCredentialsRequired(async () => {
        try {
          const jwt = await provider();
          NativeSinchCalling.provideRegistrationCredentials(jwt);
        } catch (error) {
          NativeSinchCalling.failRegistration(
            error instanceof Error ? error.message : String(error)
          );
        }
      });
  }

  onClientStarted(listener: () => void): EventSubscription {
    return NativeSinchCalling.onClientStarted(listener);
  }

  onClientStartFailed(
    listener: (event: ClientStartFailedEvent) => void
  ): EventSubscription {
    return NativeSinchCalling.onClientStartFailed(listener);
  }

  callUser(userId: string): string {
    return NativeSinchCalling.callUser(userId);
  }

  /**
   * Places a PSTN call to a real phone number (E.164 format, e.g. "+14155550101").
   * `callerId` is the caller-ID number shown to the recipient — required on
   * Android, ignored on iOS (derived from the application's provisioned
   * voice number instead).
   */
  callPhoneNumber(phoneNumber: string, callerId: string): string {
    return NativeSinchCalling.callPhoneNumber(phoneNumber, callerId);
  }

  /**
   * Joins a Sinch conference room by id — e.g. to answer a call your
   * backend parked and bridged into a conference (see `reportIncomingCallUI`
   * / `resolveCallUIToConference`). `callerId` is Android-only, same
   * caveat as `callPhoneNumber`.
   */
  callConference(conferenceId: string, callerId: string): string {
    return NativeSinchCalling.callConference(conferenceId, callerId);
  }

  answerCall(callId: string): void {
    NativeSinchCalling.answerCall(callId);
  }

  hangupCall(callId: string): void {
    NativeSinchCalling.hangupCall(callId);
  }

  setMuted(muted: boolean): void {
    NativeSinchCalling.setMuted(muted);
  }

  setSpeakerEnabled(enabled: boolean): void {
    NativeSinchCalling.setSpeakerEnabled(enabled);
  }

  /** `key` must be one of [0-9, #, *, A-D]. Returns false if it couldn't be sent. */
  sendDTMF(callId: string, key: string): boolean {
    return NativeSinchCalling.sendDTMF(callId, key);
  }

  onIncomingCall(
    listener: (event: IncomingCallEvent) => void
  ): EventSubscription {
    return NativeSinchCalling.onIncomingCall(listener);
  }

  onCallProgressing(listener: (event: CallEvent) => void): EventSubscription {
    return NativeSinchCalling.onCallProgressing(listener);
  }

  onCallEstablished(listener: (event: CallEvent) => void): EventSubscription {
    return NativeSinchCalling.onCallEstablished(listener);
  }

  onCallEnded(listener: (event: CallEndedEvent) => void): EventSubscription {
    return NativeSinchCalling.onCallEnded(listener);
  }

  /** Android only. Call before `configure()`. No-op on iOS. */
  registerFcmPush(senderId: string, token: string): void {
    NativeSinchCalling.registerFcmPush(senderId, token);
  }

  /** iOS only. Sets up SINManagedPush (VoIP push). No-op on Android. */
  enablePushNotifications(useProductionAps: boolean): void {
    NativeSinchCalling.enablePushNotifications(useProductionAps);
  }

  /**
   * Android only. Forward the raw FCM data payload here (e.g. from
   * `messaging().onMessage()` / `setBackgroundMessageHandler()`). By
   * default this always relays to Sinch; call
   * `configureCustomIncomingCallPush` first to also recognize your own
   * backend's payload shape. No-op on iOS, which applies the same logic
   * internally via PushKit.
   */
  relayRemotePushNotification(payload: Record<string, string>): void {
    NativeSinchCalling.relayRemotePushNotification(payload);
  }

  /**
   * Opts into recognizing your own backend's incoming-call push payloads,
   * on both platforms. `idField`/`displayField` are the keys your payload
   * uses for the call id and the caller's display name/number — e.g.
   * `configureCustomIncomingCallPush('callId', 'callerNumber')` for a
   * payload shaped `{ callId, callerNumber }`. Until this is called, pushes
   * always relay straight to Sinch (see `relayRemotePushNotification`).
   */
  configureCustomIncomingCallPush(idField: string, displayField: string): void {
    NativeSinchCalling.configureCustomIncomingCallPush(idField, displayField);
  }

  /**
   * Opts into recognizing your own backend's cancel/end push payloads, on
   * both platforms — e.g. `configureCustomCancelCallPush('type', 'call_cancelled')`
   * for a payload shaped `{ type: 'call_cancelled', callId, ... }`. Checked
   * before the `configureCustomIncomingCallPush` id-field match, so a push
   * carrying both the id field and this cancel marker ends the already-shown
   * call UI instead of being reported as a new incoming call. Has no effect
   * until `configureCustomIncomingCallPush` has also been called.
   */
  configureCustomCancelCallPush(typeField: string, cancelValue: string): void {
    NativeSinchCalling.configureCustomCancelCallPush(typeField, cancelValue);
  }

  /**
   * Reports a system call UI (CallKit / Telecom) for a call your own app
   * logic decided to show — from a custom push, a Socket.IO event, or
   * anything else. Independent of `configureCustomIncomingCallPush`; call
   * this directly whenever you want to ring.
   */
  reportIncomingCallUI(callId: string, displayName: string): void {
    NativeSinchCalling.reportIncomingCallUI(callId, displayName);
  }

  onPushTokenRegistered(listener: () => void): EventSubscription {
    return NativeSinchCalling.onPushTokenRegistered(listener);
  }

  onPushTokenRegistrationFailed(
    listener: (event: ClientStartFailedEvent) => void
  ): EventSubscription {
    return NativeSinchCalling.onPushTokenRegistrationFailed(listener);
  }

  /**
   * Turns a call shown via `reportIncomingCallUI` (or a configured custom
   * push) into a real call once you know which Sinch conference to join.
   * Returns the real callId, or "" on failure.
   */
  resolveCallUIToConference(
    callId: string,
    conferenceId: string,
    callerId: string
  ): string {
    return NativeSinchCalling.resolveCallUIToConference(
      callId,
      conferenceId,
      callerId
    );
  }

  /** Cleans up a system call UI that couldn't be resolved into a real call. */
  dismissCallUI(callId: string): void {
    NativeSinchCalling.dismissCallUI(callId);
  }

  /** Overrides the caller name shown on the system call UI (e.g. after a caller-ID lookup resolves). */
  updateIncomingCallDisplayName(callId: string, displayName: string): void {
    NativeSinchCalling.updateIncomingCallDisplayName(callId, displayName);
  }

  /** iOS only: hex-encoded PushKit VoIP token, issued on start and on rotation. */
  onVoipPushTokenUpdated(
    listener: (event: VoipPushTokenEvent) => void
  ): EventSubscription {
    return NativeSinchCalling.onVoipPushTokenUpdated(listener);
  }

  /**
   * A system call UI was reported — either you called `reportIncomingCallUI`
   * yourself, or a configured custom push was auto-detected. Useful for
   * kicking off a caller-ID lookup the moment a call starts ringing.
   */
  onIncomingCallUIShown(
    listener: (event: IncomingCallUIEvent) => void
  ): EventSubscription {
    return NativeSinchCalling.onIncomingCallUIShown(listener);
  }

  /** The system call UI shown via `reportIncomingCallUI` was answered. */
  onCallUIAnswered(
    listener: (event: CallUIAnsweredEvent) => void
  ): EventSubscription {
    return NativeSinchCalling.onCallUIAnswered(listener);
  }

  /** The system call UI shown via `reportIncomingCallUI` was declined. */
  onCallUIDeclined(
    listener: (event: CallUIDeclinedEvent) => void
  ): EventSubscription {
    return NativeSinchCalling.onCallUIDeclined(listener);
  }

  /**
   * A configured cancel push (see `configureCustomCancelCallPush`) arrived
   * for a call previously shown via a configured custom incoming-call push.
   * The system call UI has already been torn down natively by the time this
   * fires — use it to clean up any app-side state keyed by `callId`.
   */
  onIncomingCallUICancelled(
    listener: (event: IncomingCallUICancelledEvent) => void
  ): EventSubscription {
    return NativeSinchCalling.onIncomingCallUICancelled(listener);
  }
}

export default new SinchCalling();
