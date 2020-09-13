/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

#import "JBAnimatedTextManager.h"
#import "JBTextShadowView.h"

#import <React/RCTShadowView+Layout.h>
#import <React/RCTShadowView.h>
#import <React/RCTUIManager.h>
#import <React/RCTUIManagerUtils.h>
#import <React/RCTUIManagerObserverCoordinator.h>

#import <React/RCTTextView.h>

@interface JBAnimatedTextManager () <RCTUIManagerObserver>

@end

@implementation JBAnimatedTextManager
{
  NSHashTable<JBTextShadowView *> *_shadowViews;
}

RCT_EXPORT_MODULE(JBAnimatedText)

RCT_REMAP_SHADOW_PROPERTY(numberOfLines, maximumNumberOfLines, NSInteger)
RCT_REMAP_SHADOW_PROPERTY(ellipsizeMode, lineBreakMode, NSLineBreakMode)
RCT_REMAP_SHADOW_PROPERTY(adjustsFontSizeToFit, adjustsFontSizeToFit, BOOL)
RCT_REMAP_SHADOW_PROPERTY(minimumFontScale, minimumFontScale, CGFloat)

RCT_EXPORT_SHADOW_PROPERTY(onTextLayout, RCTDirectEventBlock)

RCT_EXPORT_VIEW_PROPERTY(selectable, BOOL)

- (void)setBridge:(RCTBridge *)bridge
{
  [super setBridge:bridge];
  _shadowViews = [NSHashTable weakObjectsHashTable];

  [bridge.uiManager.observerCoordinator addObserver:self];

  [[NSNotificationCenter defaultCenter] addObserver:self
                                           selector:@selector(handleDidUpdateMultiplierNotification)
                                               name:@"RCTAccessibilityManagerDidUpdateMultiplierNotification"
                                             object:[bridge moduleForName:@"AccessibilityManager"
                                                    lazilyLoadIfNecessary:YES]];
}

- (UIView *)view
{
  return [RCTTextView new];
}

- (RCTShadowView *)shadowView
{
  JBTextShadowView *shadowView = [[JBTextShadowView alloc] initWithBridge:self.bridge];
  shadowView.textAttributes.fontSizeMultiplier = [[[self.bridge moduleForName:@"AccessibilityManager"]
                                                   valueForKey:@"multiplier"] floatValue];
  [_shadowViews addObject:shadowView];
  return shadowView;
}

#pragma mark - RCTUIManagerObserver

- (void)uiManagerWillPerformMounting:(__unused RCTUIManager *)uiManager
{
  for (JBTextShadowView *shadowView in _shadowViews) {
    [shadowView uiManagerWillPerformMounting];
  }
}

#pragma mark - Font Size Multiplier

- (void)handleDidUpdateMultiplierNotification
{
  CGFloat fontSizeMultiplier = [[[self.bridge moduleForName:@"AccessibilityManager"]
                                 valueForKey:@"multiplier"] floatValue];

  NSHashTable<JBTextShadowView *> *shadowViews = _shadowViews;
  RCTExecuteOnUIManagerQueue(^{
    for (JBTextShadowView *shadowView in shadowViews) {
      shadowView.textAttributes.fontSizeMultiplier = fontSizeMultiplier;
      [shadowView dirtyLayout];
    }

    [self.bridge.uiManager setNeedsLayout];
  });
}

@end
