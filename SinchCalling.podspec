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
  # `SinchCalling.h` (the only header in this pod) must stay public — the
  # RN TurboModule bridge itself doesn't need this (it dispatches via the
  # ObjC runtime, not compile-time imports), but `eagerlyRegisterForVoipPush`
  # is meant to be called directly from a consumer app's native AppDelegate
  # via `import SinchCalling`, which only sees public headers.

  s.dependency "SinchRTC"

  s.pod_target_xcconfig = {
    "CLANG_ENABLE_MODULES" => "YES",
    "OTHER_CPLUSPLUSFLAGS" => "$(inherited) -fcxx-modules"
  }

  install_modules_dependencies(s)
end
