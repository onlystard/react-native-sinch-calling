#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * Deliberately kept free of any dependency on the Codegen-generated
 * `SinchCallingSpec.h` (which `SinchCalling.h` imports and which requires
 * Objective-C++) — this class exists specifically so it can be a public,
 * plain-Objective-C entry point that a consumer app's `AppDelegate.swift`
 * can `import SinchCalling` and call directly, before React Native boots.
 */
@interface SinchCallingBootstrap : NSObject

/**
 * Registers for VoIP push (PKPushRegistry) and configures the custom
 * incoming/cancel-call push field names immediately, independent of React
 * Native and any consumer-app login state. Call this as early as possible —
 * ideally the first line of `application(_:didFinishLaunchingWithOptions:)`
 * — so a killed app can still ring and report to CallKit from a cold launch
 * triggered by the push itself, before the JS bridge exists. Safe to call
 * even though the RN-driven `SinchCalling` TurboModule instance is created
 * later in the same process — both share the same underlying managers.
 */
+ (void)eagerlyRegisterForVoipPush;

@end

NS_ASSUME_NONNULL_END
