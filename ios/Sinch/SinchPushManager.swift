import Foundation
import PushKit
import SinchRTC

@objc public protocol SinchPushManagerDelegate: AnyObject {
  func sinchPushManagerDidUpdateToken(_ tokenHex: String)
  // A configured custom incoming-call push was detected — distinct from a
  // Sinch-relayed push (e.g. an app-to-app incoming call), which is instead
  // forwarded straight to the Sinch client and surfaces as `onIncomingCall`.
  // The system call UI has already been reported by the time this fires
  // (see `callKitManager` below) — this is purely for observers (e.g. the
  // JS bridge) that want to react, such as kicking off a caller-ID lookup.
  func sinchPushManagerDidDetectCustomIncomingCall(_ callId: String, displayName: String)
  // A configured cancel push was detected for a call already reported via
  // `sinchPushManagerDidDetectCustomIncomingCall` — see
  // `configureCustomCancelCallPush`. The call UI has already been ended by
  // the time this fires.
  func sinchPushManagerDidDetectCustomCancelCall(_ callId: String)
}

// A single `PKPushRegistry` can only have one delegate per push type, so this
// owns VoIP push end-to-end. By default every payload is relayed straight to
// Sinch (the library's original behavior). If the consumer app opts in via
// `configureCustomIncomingCallPush`, payloads containing both configured
// fields are instead reported as an external incoming call and skip the
// Sinch relay for that push.
//
// Reports directly to `callKitManager` (rather than only notifying
// `delegate`) so a killed app can ring from a cold launch triggered by the
// push itself, before React Native has booted and any delegate/JS bridge
// exists to receive it — see `SinchCalling.eagerlyRegisterForVoipPush`.
@objc(SinchPushManager)
public final class SinchPushManager: NSObject {
  @objc public weak var delegate: SinchPushManagerDelegate?

  private weak var callManager: SinchCallManager?
  private weak var callKitManager: SinchCallKitManager?
  private var registry: PKPushRegistry?
  private var customIdField: String?
  private var customDisplayField: String?
  private var customCancelTypeField: String?
  private var customCancelValue: String?

  @objc public init(callManager: SinchCallManager, callKitManager: SinchCallKitManager) {
    self.callManager = callManager
    self.callKitManager = callKitManager
    super.init()
  }

  @objc public func enable(useProductionAps: Bool) {
    callManager?.enableManagedPushNotifications()

    let registry = PKPushRegistry(queue: .main)
    registry.delegate = self
    registry.desiredPushTypes = [.voIP]
    self.registry = registry
  }

  @objc public func configureCustomIncomingCallPush(idField: String, displayField: String) {
    customIdField = idField
    customDisplayField = displayField
  }

  // Opts into recognizing a cancel/end push shaped like the configured
  // incoming-call push but carrying `typeField == cancelValue` — e.g. the
  // backend reuses the same `idField` but sets `type: "call_cancelled"`
  // instead of `"incoming_call"`. Checked before the incoming-call match in
  // `didReceiveIncomingPushWith`, so a push satisfying both is treated as a
  // cancel.
  @objc public func configureCustomCancelCallPush(typeField: String, cancelValue: String) {
    customCancelTypeField = typeField
    customCancelValue = cancelValue
  }
}

extension SinchPushManager: PKPushRegistryDelegate {
  public func pushRegistry(
    _ registry: PKPushRegistry,
    didUpdate pushCredentials: PKPushCredentials,
    for type: PKPushType
  ) {
    guard type == .voIP else { return }
    let tokenHex = pushCredentials.token.map { String(format: "%02x", $0) }.joined()
    delegate?.sinchPushManagerDidUpdateToken(tokenHex)
  }

  public func pushRegistry(_ registry: PKPushRegistry, didInvalidatePushTokenFor type: PKPushType) {}

  public func pushRegistry(
    _ registry: PKPushRegistry,
    didReceiveIncomingPushWith payload: PKPushPayload,
    for type: PKPushType,
    completion: @escaping () -> Void
  ) {
    guard type == .voIP else {
      completion()
      return
    }

    let dict = payload.dictionaryPayload
    if let idField = customIdField, let callId = dict[idField] as? String {
      if let typeField = customCancelTypeField,
        let cancelValue = customCancelValue,
        let typeValue = dict[typeField] as? String,
        typeValue == cancelValue
      {
        callKitManager?.reportCallEnded(callId)
        delegate?.sinchPushManagerDidDetectCustomCancelCall(callId)
      } else {
        let displayName = customDisplayField.flatMap { dict[$0] as? String } ?? ""
        callKitManager?.reportExternalIncomingCall(callId, displayName: displayName)
        delegate?.sinchPushManagerDidDetectCustomIncomingCall(callId, displayName: displayName)
      }
    } else {
      callManager?.relayPushNotification(dict)
    }
    completion()
  }
}
