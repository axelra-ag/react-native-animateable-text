/*
 * Custom
 */

#ifdef RCT_NEW_ARCH_ENABLED
#import <React/RCTViewComponentView.h>
#import <UIKit/UIKit.h>
#import <React/RCTParagraphComponentView.h>

#ifndef JBAnimatedTextFabricViewNativeComponent_h
#define JBAnimatedTextFabricViewNativeComponent_h

NS_ASSUME_NONNULL_BEGIN

@interface JBAnimatedTextComponentView : RCTParagraphComponentView

+ (ComponentDescriptorProvider)componentDescriptorProvider;

@end

NS_ASSUME_NONNULL_END

#endif /* JBAnimatedTextFabricViewNativeComponent_h */
#endif /* RCT_NEW_ARCH_ENABLED */
