#import "SinchCallingBootstrap.h"
#import "SinchCalling.h"

@implementation SinchCallingBootstrap

+ (void)eagerlyRegisterForVoipPush
{
  [SinchCalling eagerlyRegisterForVoipPush];
}

@end
