require 'json'

package = JSON.parse(File.read(File.join(__dir__, 'package.json')))

Pod::Spec.new do |s|
  s.name         = "ReactNativeCameraKit"
  s.version      = "1.0.0"
  s.summary      = "Advanced native camera and gallery controls and device photos API"
  s.license      = "MIT"

  s.authors      = "BachirKhiati"
  s.homepage     = "https://github.com/BachirKhiati/rn-kit"
  s.platform     = :ios, "10.0"

  s.source       = { :git => "https://github.com/wix/rn-kit.git", :tag => "v#{s.version}" }
  s.source_files  = "ios/lib/**/*.{h,m}"

  s.dependency 'React'
end
