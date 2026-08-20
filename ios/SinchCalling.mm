#import <AVFoundation/AVFoundation.h>

#import "SinchCalling.h"

#if __has_include("SinchCalling-Swift.h")
#import "SinchCalling-Swift.h"
#else
#import <SinchCalling/SinchCalling-Swift.h>
#endif

@interface SinchCalling () <SinchCallManagerDelegate, SinchCallKitManagerDelegate, SinchPushManagerDelegate>
@end

@implementation SinchCalling {
  SinchCallManager *_callManager;
  SinchPushManager *_pushManager;
  SinchCallKitManager *_callKitManager;
}

- (instancetype)init
{
  if (self = [super init]) {
    _callManager = [SinchCallManager new];
    _callManager.delegate = self;
    _pushManager = [[SinchPushManager alloc] initWithCallManager:_callManager];
    _pushManager.delegate = self;
    _callKitManager = [SinchCallKitManager new];
    _callKitManager.delegate = self;
  }
  return self;
}

- (void)configure:(NSString *)appKey environmentHost:(NSString *)environmentHost userId:(NSString *)userId
{
  [_callManager configureWithAppKey:appKey environmentHost:environmentHost userId:userId];
}

- (void)start
{
  [_callManager start];
}

- (void)stop
{
  [_callManager stop];
}

- (void)provideRegistrationCredentials:(NSString *)jwt
{
  [_callManager provideRegistrationCredentials:jwt];
}

- (void)failRegistration:(NSString *)message
{
  [_callManager failRegistration:message];
}

- (NSString *)callUser:(NSString *)userId
{
  return [_callManager callUser:userId];
}

- (NSString *)callPhoneNumber:(NSString *)phoneNumber callerId:(NSString *)callerId
{
  return [_callManager callPhoneNumber:phoneNumber];
}

- (NSString *)callConference:(NSString *)conferenceId callerId:(NSString *)callerId
{
  return [_callManager callConference:conferenceId];
}

- (BOOL)sendDTMF:(NSString *)callId key:(NSString *)key
{
  return [_callManager sendDTMF:callId key:key];
}

- (void)configureCustomIncomingCallPush:(NSString *)idField displayField:(NSString *)displayField
{
  [_pushManager configureCustomIncomingCallPushWithIdField:idField displayField:displayField];
}

- (void)configureCustomCancelCallPush:(NSString *)typeField cancelValue:(NSString *)cancelValue
{
  [_pushManager configureCustomCancelCallPushWithTypeField:typeField cancelValue:cancelValue];
}

- (void)reportIncomingCallUI:(NSString *)callId displayName:(NSString *)displayName
{
  [_callKitManager reportExternalIncomingCall:callId displayName:displayName];
  [self emitOnIncomingCallUIShown:@{ @"callId" : callId, @"displayName" : displayName }];
}

- (NSString *)resolveCallUIToConference:(NSString *)callId
                            conferenceId:(NSString *)conferenceId
                                callerId:(NSString *)callerId
{
  NSString *realCallId = [_callManager callConference:conferenceId];
  if (realCallId.length == 0) {
    [_callKitManager reportCallEnded:callId];
    return @"";
  }
  [_callKitManager remapCallId:callId toCallId:realCallId];
  return realCallId;
}

- (void)dismissCallUI:(NSString *)callId
{
  [_callKitManager reportCallEnded:callId];
}

- (void)updateIncomingCallDisplayName:(NSString *)callId displayName:(NSString *)displayName
{
  [_callKitManager updateCallerDisplayName:callId displayName:displayName];
}

- (void)answerCall:(NSString *)callId
{
  [_callManager answerCall:callId];
}

- (void)hangupCall:(NSString *)callId
{
  [_callManager hangupCall:callId];
}

- (void)setMuted:(BOOL)muted
{
  [_callManager setMuted:muted];
}

- (void)setSpeakerEnabled:(BOOL)enabled
{
  [_callManager setSpeakerEnabled:enabled];
}

- (void)registerFcmPush:(NSString *)senderId token:(NSString *)token
{
  // Android only — iOS uses SINManagedPush (see enablePushNotifications).
}

- (void)enablePushNotifications:(BOOL)useProductionAps
{
  [_pushManager enableWithUseProductionAps:useProductionAps];
}

- (void)relayRemotePushNotification:(NSDictionary *)payload
{
  // Android only — iOS relays VoIP pushes internally via SinchPushManager.
}

#pragma mark - SinchCallManagerDelegate

- (void)sinchCallManagerDidStartClient
{
  [self emitOnClientStarted];
}

- (void)sinchCallManagerDidFailWithMessage:(NSString *)message code:(NSInteger)code
{
  [self emitOnClientStartFailed:@{ @"message" : message, @"code" : @(code) }];
}

- (void)sinchCallManagerRequiresRegistrationCredentials
{
  [self emitOnRegistrationCredentialsRequired];
}

- (void)sinchCallManagerDidReceiveIncomingCall:(NSString *)callId remoteUserId:(NSString *)remoteUserId
{
  [_callKitManager reportIncomingCall:callId remoteUserId:remoteUserId];
  [self emitOnIncomingCall:@{ @"callId" : callId, @"remoteUserId" : remoteUserId }];
}

- (void)sinchCallManagerCallDidProgress:(NSString *)callId
{
  [self emitOnCallProgressing:@{ @"callId" : callId }];
}

- (void)sinchCallManagerCallDidEstablish:(NSString *)callId
{
  [self emitOnCallEstablished:@{ @"callId" : callId }];
}

- (void)sinchCallManagerCallDidEnd:(NSString *)callId endCause:(NSString *)endCause
{
  [_callKitManager reportCallEnded:callId];
  [self emitOnCallEnded:@{ @"callId" : callId, @"endCause" : endCause }];
}

#pragma mark - SinchCallKitManagerDelegate

- (void)sinchCallKitManagerDidAnswerCall:(NSString *)callId
{
  [_callManager answerCall:callId];
}

- (void)sinchCallKitManagerDidEndCall:(NSString *)callId
{
  [_callManager hangupCall:callId];
}

- (void)sinchCallKitManagerDidAnswerExternalCall:(NSString *)callId
{
  [self emitOnCallUIAnswered:@{ @"callId" : callId }];
}

- (void)sinchCallKitManagerDidDeclineExternalCall:(NSString *)callId
{
  [self emitOnCallUIDeclined:@{ @"callId" : callId }];
}

- (void)sinchCallKitManagerDidActivateAudioSession:(AVAudioSession *)audioSession
{
  [_callManager didActivateAudioSession:audioSession];
}

- (void)sinchCallKitManagerDidDeactivateAudioSession:(AVAudioSession *)audioSession
{
  [_callManager didDeactivateAudioSession:audioSession];
}

#pragma mark - SinchPushManagerDelegate

- (void)sinchPushManagerDidUpdateToken:(NSString *)tokenHex
{
  [self emitOnVoipPushTokenUpdated:@{ @"token" : tokenHex }];
}

- (void)sinchPushManagerDidDetectCustomIncomingCall:(NSString *)callId displayName:(NSString *)displayName
{
  [_callKitManager reportExternalIncomingCall:callId displayName:displayName];
  [self emitOnIncomingCallUIShown:@{ @"callId" : callId, @"displayName" : displayName }];
}

- (void)sinchPushManagerDidDetectCustomCancelCall:(NSString *)callId
{
  [_callKitManager reportCallEnded:callId];
  [self emitOnIncomingCallUICancelled:@{ @"callId" : callId }];
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativeSinchCallingSpecJSI>(params);
}

+ (NSString *)moduleName
{
  return @"SinchCalling";
}

@end
