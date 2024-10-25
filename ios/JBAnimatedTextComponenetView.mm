/*
 * Custom
 */

#ifdef RCT_NEW_ARCH_ENABLED

#import "JBAnimatedTextComponenetView.h"

#import <react/renderer/components/JBAnimatedText/ParagraphComponentDescriptor.h>
#import <react/renderer/components/JBAnimatedTextCodegen/RCTComponentViewHelpers.h>

#import "RCTFabricComponentsPlugins.h"

using namespace facebook::react;

@implementation JBAnimatedText

+ (ComponentDescriptorProvider)componentDescriptorProvider
{
  return concreteComponentDescriptorProvider<CParagraphComponentDescriptor>();
}

Class<RCTComponentViewProtocol> JBAnimatedTextCls(void)
{
  return JBAnimatedText.class;
}

@end

#endif
