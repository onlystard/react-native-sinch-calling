import AVFoundation
import CallKit
import Foundation

@objc public protocol SinchCallKitManagerDelegate: AnyObject {
  func sinchCallKitManagerDidAnswerCall(_ callId: String)
  func sinchCallKitManagerDidEndCall(_ callId: String)
  // Fired instead of the two methods above when the call was reported via
  // `reportExternalIncomingCall` and hasn't been resolved into a real Sinch
  // call yet (see `resolveCallUIToConference`) — answering/declining it is
  // the consumer app's business, not the native call API's.
  func sinchCallKitManagerDidAnswerExternalCall(_ callId: String)
  func sinchCallKitManagerDidDeclineExternalCall(_ callId: String)
  func sinchCallKitManagerDidActivateAudioSession(_ audioSession: AVAudioSession)
  func sinchCallKitManagerDidDeactivateAudioSession(_ audioSession: AVAudioSession)
}

// A PSTN incoming call's `remoteUserId` is the caller's phone number;
// an app-to-app call's is our own opaque Sinch userId. Distinguishing them
// lets CallKit show/format the caller ID correctly.
private func isPhoneNumber(_ value: String) -> Bool {
  return value.range(of: "^\\+?[0-9]{6,15}$", options: .regularExpression) != nil
}

@objc(SinchCallKitManager)
public final class SinchCallKitManager: NSObject {
  @objc public weak var delegate: SinchCallKitManagerDelegate?

  private let provider: CXProvider
  private var callIdToUUID: [String: UUID] = [:]
  private var uuidToCallId: [UUID: String] = [:]
  private var externallyReportedCallIds: Set<String> = []

  @objc override public init() {
    let configuration = CXProviderConfiguration()
    configuration.supportsVideo = false
    configuration.maximumCallGroups = 1
    configuration.maximumCallsPerCallGroup = 1
    configuration.supportedHandleTypes = [.generic, .phoneNumber]

    provider = CXProvider(configuration: configuration)
    super.init()
    provider.setDelegate(self, queue: nil)
  }

  @objc public func reportIncomingCall(_ callId: String, remoteUserId: String) {
    reportNewIncomingCall(callId, remoteUserId: remoteUserId)
  }

  // A call the consumer app decided to show a system call UI for (a custom
  // push, a socket event, anything) — not a Sinch-native incoming call.
  // Answering/declining goes through the app's own logic instead of the
  // native call API until `remapCallId` ties it to a real call.
  @objc public func reportExternalIncomingCall(_ callId: String, displayName: String) {
    externallyReportedCallIds.insert(callId)
    reportNewIncomingCall(callId, remoteUserId: displayName)
  }

  // Re-associates the system call UI already showing for `oldCallId` (an
  // externally-reported, not-yet-real call) with the real Sinch call
  // created once the consumer app resolved it.
  @objc public func remapCallId(_ oldCallId: String, toCallId newCallId: String) {
    guard let uuid = callIdToUUID[oldCallId] else { return }
    callIdToUUID.removeValue(forKey: oldCallId)
    callIdToUUID[newCallId] = uuid
    uuidToCallId[uuid] = newCallId
    externallyReportedCallIds.remove(oldCallId)
  }

  // Overrides the caller name shown on the system call UI (e.g. once a
  // caller-ID lookup resolves) without disturbing the phone number already
  // reported as the handle.
  @objc public func updateCallerDisplayName(_ callId: String, displayName: String) {
    guard let uuid = callIdToUUID[callId] else { return }
    let update = CXCallUpdate()
    update.localizedCallerName = displayName
    provider.reportCall(with: uuid, updated: update)
  }

  @objc public func reportCallEnded(_ callId: String) {
    guard let uuid = callIdToUUID[callId] else { return }
    provider.reportCall(with: uuid, endedAt: nil, reason: .remoteEnded)
    callIdToUUID.removeValue(forKey: callId)
    uuidToCallId.removeValue(forKey: uuid)
    externallyReportedCallIds.remove(callId)
  }

  private func reportNewIncomingCall(_ callId: String, remoteUserId: String) {
    if callIdToUUID[callId] != nil {
      return
    }

    let uuid = UUID()
    callIdToUUID[callId] = uuid
    uuidToCallId[uuid] = callId

    let update = CXCallUpdate()
    let handleType: CXHandle.HandleType = isPhoneNumber(remoteUserId) ? .phoneNumber : .generic
    update.remoteHandle = CXHandle(type: handleType, value: remoteUserId)
    update.hasVideo = false

    provider.reportNewIncomingCall(with: uuid, update: update) { [weak self] error in
      guard error != nil else { return }
      self?.callIdToUUID.removeValue(forKey: callId)
      self?.uuidToCallId.removeValue(forKey: uuid)
      self?.externallyReportedCallIds.remove(callId)
    }
  }
}

extension SinchCallKitManager: CXProviderDelegate {
  public func providerDidReset(_ provider: CXProvider) {
    callIdToUUID.removeAll()
    uuidToCallId.removeAll()
  }

  public func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
    guard let callId = uuidToCallId[action.callUUID] else {
      action.fail()
      return
    }
    if externallyReportedCallIds.contains(callId) {
      delegate?.sinchCallKitManagerDidAnswerExternalCall(callId)
    } else {
      delegate?.sinchCallKitManagerDidAnswerCall(callId)
    }
    action.fulfill()
  }

  public func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
    guard let callId = uuidToCallId[action.callUUID] else {
      action.fail()
      return
    }
    let wasExternallyReported = externallyReportedCallIds.contains(callId)
    callIdToUUID.removeValue(forKey: callId)
    uuidToCallId.removeValue(forKey: action.callUUID)
    externallyReportedCallIds.remove(callId)
    if wasExternallyReported {
      delegate?.sinchCallKitManagerDidDeclineExternalCall(callId)
    } else {
      delegate?.sinchCallKitManagerDidEndCall(callId)
    }
    action.fulfill()
  }

  public func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession) {
    delegate?.sinchCallKitManagerDidActivateAudioSession(audioSession)
  }

  public func provider(_ provider: CXProvider, didDeactivate audioSession: AVAudioSession) {
    delegate?.sinchCallKitManagerDidDeactivateAudioSession(audioSession)
  }
}
