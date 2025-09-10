require "json"

react_native_node_modules_dir = ENV['REACT_NATIVE_NODE_MODULES_DIR'] || File.join(File.dirname(`cd "#{Pod::Config.instance.installation_root.to_s}" && node --print "require.resolve('react-native/package.json')"`), '..')
react_native_json = JSON.parse(File.read(File.join(react_native_node_modules_dir, 'react-native/package.json')))
react_native_minor_version = react_native_json['version'].split('.')[1].to_i

package = JSON.parse(File.read(File.join(__dir__, "package.json")))
folly_compiler_flags = '-DFOLLY_NO_CONFIG -DFOLLY_MOBILE=1 -DFOLLY_USE_LIBCPP=1 -Wno-comma -Wno-shorten-64-to-32'

Pod::Spec.new do |s|
  s.name         = "react-native-animateable-text"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => min_ios_version_supported }
  s.source       = { :git => "https://github.com/JonnyBurger/react-native-animateable-text.git", :tag => "#{s.version}" }

  if ENV['RCT_NEW_ARCH_ENABLED'] == '1'
    s.source_files = ["ios/**/*.{h,m,mm}", "cpp/**/*.{cpp,h}"]
    s.header_dir = "JBAnimatedText"
    s.pod_target_xcconfig = {
      "HEADER_SEARCH_PATHS" => "\"$(PODS_TARGET_SRCROOT)/cpp\" \"$(PODS_TARGET_SRCROOT)/ios\"",
      "OTHER_CPLUSPLUSFLAGS" => "-DRCT_NEW_ARCH_ENABLED=1",
      "OTHER_CFLAGS" => "$(inherited) -DREACT_NATIVE_MINOR_VERSION=#{react_native_minor_version} -DRCT_NEW_ARCH_ENABLED=1"
    }
  else
    s.source_files = "ios/**/*.{h,m,mm}"
    s.xcconfig = {
      "OTHER_CFLAGS" => "$(inherited) -DREACT_NATIVE_MINOR_VERSION=#{react_native_minor_version}"
    }
  end

  s.dependency "hermes-engine"

  install_modules_dependencies(s)

  if ENV['USE_FRAMEWORKS'] != nil && ENV['RCT_NEW_ARCH_ENABLED'] == '1'
    add_dependency(s, "React-FabricComponents", :additional_framework_paths => [
      "react/renderer/textlayoutmanager/platform/ios",
      "react/renderer/components/text/platform/ios",
      'react/renderer/components/text/BaseTextProps',
      'react/renderer/components/JBAnimatedText'
    ])
  end
end
