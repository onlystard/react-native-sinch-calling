#import <SinchCallingSpec/SinchCallingSpec.h>

@interface SinchCalling : NativeSinchCallingSpecBase <NativeSinchCallingSpec>

// Registers for VoIP push (PKPushRegistry) and configures the custom
// incoming/cancel-call push field names immediately, independent of React
// Native and any consumer-app login state. Call this as early as possible —
// ideally the first line of `application(_:didFinishLaunchingWithOptions:)`
// — so a killed app can still ring and report to CallKit from a cold launch
// triggered by the push itself, before the JS bridge exists. Safe to call
// even if the RN-driven `SinchCalling` TurboModule instance is created
// later in the same process — both share the same underlying managers.
+ (void)eagerlyRegisterForVoipPush;

@end
