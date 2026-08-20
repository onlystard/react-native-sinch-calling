require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "SinchCalling"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => min_ios_version_supported }
  s.source       = { :git => "https://github.com/tristian-tan/react-native-sinch-calling.git", :tag => "#{s.version}" }

  s.swift_version = "5.0"
  s.source_files = "ios/**/*.{h,m,mm,swift,cpp}"
  # `SinchCalling.h` imports the Codegen-generated `SinchCallingSpec.h`,
  # which requires Objective-C++ — fine for `.mm` files that `#import` it
  # directly, but it breaks Clang's build of this pod's *public* Swift-facing
  # module interface if included there. Keep it private; `SinchCallingBootstrap.h`
  # (no such dependency) is the only header a consumer app should import
  # directly, e.g. from AppDelegate — see its own doc comment.
  s.private_header_files = "ios/SinchCalling.h"

  s.dependency "SinchRTC"

  s.pod_target_xcconfig = {
    "CLANG_ENABLE_MODULES" => "YES",
    "OTHER_CPLUSPLUSFLAGS" => "$(inherited) -fcxx-modules"
  }

  install_modules_dependencies(s)
end
