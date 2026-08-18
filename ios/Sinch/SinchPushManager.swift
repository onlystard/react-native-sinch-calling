import Foundation
import PushKit
import SinchRTC

@objc public protocol SinchPushManagerDelegate: AnyObject {
  func sinchPushManagerDidUpdateToken(_ tokenHex: String)
  // A configured custom incoming-call push was detected — distinct from a
  // Sinch-relayed push (e.g. an app-to-app incoming call), which is instead
  // forwarded straight to the Sinch client and surfaces as `onIncomingCall`.
  func sinchPushManagerDidDetectCustomIncomingCall(_ callId: String, displayName: String)
}

// A single `PKPushRegistry` can only have one delegate per push type, so this
// owns VoIP push end-to-end. By default every payload is relayed straight to
// Sinch (the library's original behavior). If the consumer app opts in via
// `configureCustomIncomingCallPush`, payloads containing both configured
// fields are instead reported as an external incoming call and skip the
// Sinch relay for that push.
@objc(SinchPushManager)
public final class SinchPushManager: NSObject {
  @objc public weak var delegate: SinchPushManagerDelegate?

  private weak var callManager: SinchCallManager?
  private var registry: PKPushRegistry?
  private var customIdField: String?
  private var customDisplayField: String?

  @objc public init(callManager: SinchCallManager) {
    self.callManager = callManager
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
      let displayName = customDisplayField.flatMap { dict[$0] as? String } ?? ""
      delegate?.sinchPushManagerDidDetectCustomIncomingCall(callId, displayName: displayName)
    } else {
      callManager?.relayPushNotification(dict)
    }
    completion()
  }
}
