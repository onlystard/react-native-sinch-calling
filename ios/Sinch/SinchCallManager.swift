import AVFoundation
import Foundation
import SinchRTC

@objc public protocol SinchCallManagerDelegate: AnyObject {
  func sinchCallManagerDidStartClient()
  func sinchCallManagerDidFailWithMessage(_ message: String, code: Int)
  func sinchCallManagerRequiresRegistrationCredentials()

  func sinchCallManagerDidReceiveIncomingCall(_ callId: String, remoteUserId: String)
  func sinchCallManagerCallDidProgress(_ callId: String)
  func sinchCallManagerCallDidEstablish(_ callId: String)
  func sinchCallManagerCallDidEnd(_ callId: String, endCause: String)
}

private func endCauseString(_ endCause: SINCallEndCause) -> String {
  switch endCause {
  case .none: return "none"
  case .timeout: return "timeout"
  case .denied: return "denied"
  case .noAnswer: return "noAnswer"
  case .error: return "error"
  case .hungUp: return "hungUp"
  case .canceled: return "canceled"
  case .otherDeviceAnswered: return "otherDeviceAnswered"
  case .inactive: return "inactive"
  case .voIPCallDetected: return "voipCallDetected"
  case .gsmCallDetected: return "gsmCallDetected"
  @unknown default: return "none"
  }
}

@objc(SinchCallManager)
public final class SinchCallManager: NSObject {
  @objc public weak var delegate: SinchCallManagerDelegate?

  private var client: SINClient?
  private var pendingRegistration: SINClientRegistration?
  private var calls: [String: SINCall] = [:]

  @objc public func configure(appKey: String, environmentHost: String, userId: String) {
    do {
      let sinchClient = try Sinch.client(
        withApplicationKey: appKey,
        environmentHost: environmentHost,
        userId: userId
      )
      sinchClient.delegate = self
      sinchClient.call().delegate = self
      client = sinchClient
    } catch {
      let nsError = error as NSError
      delegate?.sinchCallManagerDidFailWithMessage(nsError.localizedDescription, code: nsError.code)
    }
  }

  @objc public func start() {
    client?.start()
  }

  @objc public func stop() {
    client?.terminateGracefully()
    client = nil
    calls.removeAll()
  }

  @objc public func provideRegistrationCredentials(_ jwt: String) {
    pendingRegistration?.register(withJWT: jwt)
    pendingRegistration = nil
  }

  @objc public func failRegistration(_ message: String) {
    let error = NSError(
      domain: "SinchCalling",
      code: -1,
      userInfo: [NSLocalizedDescriptionKey: message]
    )
    pendingRegistration?.registerDidFail(error)
    pendingRegistration = nil
  }

  @objc public func callUser(_ userId: String) -> String {
    guard let call = client?.call().callUser(withId: userId) else {
      return ""
    }
    track(call)
    return call.callId
  }

  @objc public func callPhoneNumber(_ phoneNumber: String) -> String {
    guard let call = client?.call().callPhoneNumber(phoneNumber) else {
      return ""
    }
    track(call)
    return call.callId
  }

  @objc public func callConference(_ conferenceId: String) -> String {
    guard let call = client?.call().callConference(withId: conferenceId) else {
      return ""
    }
    track(call)
    return call.callId
  }

  @objc public func answerCall(_ callId: String) {
    calls[callId]?.answer()
  }

  @objc public func hangupCall(_ callId: String) {
    calls[callId]?.hangup()
  }

  @objc public func sendDTMF(_ callId: String, key: String) -> Bool {
    return calls[callId]?.sendDTMF(key) ?? false
  }

  @objc public func setMuted(_ muted: Bool) {
    if muted {
      client?.audioController().mute()
    } else {
      client?.audioController().unmute()
    }
  }

  @objc public func setSpeakerEnabled(_ enabled: Bool) {
    if enabled {
      client?.audioController().enableSpeaker()
    } else {
      client?.audioController().disableSpeaker()
    }
  }

  @objc public func enableManagedPushNotifications() {
    client?.enableManagedPushNotifications()
  }

  @objc public func relayPushNotification(_ payload: [AnyHashable: Any]) {
    _ = client?.relayPushNotification(payload)
  }

  @objc public func didActivateAudioSession(_ audioSession: AVAudioSession) {
    client?.call().didActivate(audioSession)
  }

  @objc public func didDeactivateAudioSession(_ audioSession: AVAudioSession) {
    client?.call().didDeactivate(audioSession)
  }

  private func track(_ call: SINCall) {
    calls[call.callId] = call
    call.delegate = self
  }
}

extension SinchCallManager: SINClientDelegate {
  public func clientDidStart(_ client: SINClient) {
    delegate?.sinchCallManagerDidStartClient()
  }

  public func clientDidFail(_ client: SINClient, error: Error) {
    let nsError = error as NSError
    delegate?.sinchCallManagerDidFailWithMessage(nsError.localizedDescription, code: nsError.code)
  }

  public func client(
    _ client: SINClient,
    requiresRegistrationCredentials registrationCallback: SINClientRegistration
  ) {
    pendingRegistration = registrationCallback
    delegate?.sinchCallManagerRequiresRegistrationCredentials()
  }
}

extension SinchCallManager: SINCallClientDelegate {
  public func client(_ client: SINCallClient, didReceiveIncomingCall call: SINCall) {
    track(call)
    delegate?.sinchCallManagerDidReceiveIncomingCall(call.callId, remoteUserId: call.remoteUserId)
  }
}

extension SinchCallManager: SINCallDelegate {
  public func callDidProgress(_ call: SINCall) {
    delegate?.sinchCallManagerCallDidProgress(call.callId)
  }

  public func callDidEstablish(_ call: SINCall) {
    delegate?.sinchCallManagerCallDidEstablish(call.callId)
  }

  public func callDidEnd(_ call: SINCall) {
    calls.removeValue(forKey: call.callId)
    delegate?.sinchCallManagerCallDidEnd(call.callId, endCause: endCauseString(call.details.endCause))
  }
}
