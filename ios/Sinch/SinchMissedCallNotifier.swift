import Foundation
import UserNotifications

// A still-ringing call was called off by a custom cancel push (see
// `SinchPushManager`) — CallKit's system call screen has already been
// dismissed by the time this runs, silently, with nothing explaining why the
// phone stopped ringing (unlike Android's ringing notification, which leaves
// a distinct notice behind — see `SinchIncomingCallNotification` on that
// platform). Posts a plain local notification instead: "Call forwarded" when
// `reason == "FORWARDED"`, "Missed call" for everything else (answered
// elsewhere, declined, hung up, ...).
//
// Relies on notification authorization the host app already obtained for
// its own remote-push setup — a library has no business prompting for
// permissions on its own, and `UNUserNotificationCenter.add` is a silent
// no-op if authorization was never granted.
@objc(SinchMissedCallNotifier)
public final class SinchMissedCallNotifier: NSObject {
  @objc public static func postCancelledNotice(callId: String, reason: String?, displayName: String?) {
    let content = UNMutableNotificationContent()
    content.title = reason == "FORWARDED" ? "Call forwarded" : "Missed call"
    content.body = (displayName?.isEmpty == false) ? displayName! : "Unknown"
    content.sound = .default

    let request = UNNotificationRequest(
      identifier: "sinch-cancelled-\(callId)",
      content: content,
      trigger: nil
    )
    UNUserNotificationCenter.current().add(request, withCompletionHandler: nil)
  }
}
