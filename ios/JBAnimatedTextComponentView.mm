/*
 * Custom
 */

#ifdef RCT_NEW_ARCH_ENABLED

#import "JBAnimatedTextComponentView.h"

#import <react/renderer/components/JBAnimatedText/ParagraphComponentDescriptor.h>
#import <react/renderer/components/JBAnimatedTextCodegen/RCTComponentViewHelpers.h>

#import "RCTFabricComponentsPlugins.h"

using namespace facebook::react;

@implementation JBAnimatedTextComponentView

+ (ComponentDescriptorProvider)componentDescriptorProvider
{
  return concreteComponentDescriptorProvider<CParagraphComponentDescriptor>();
}

Class<RCTComponentViewProtocol> JBAnimatedTextCls(void)
{
  return JBAnimatedTextComponentView.class;
}

@end

#endif
