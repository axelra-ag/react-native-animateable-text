/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

#import <React/RCTShadowView.h>

#import "JBTextAttributes.h"

NS_ASSUME_NONNULL_BEGIN

extern NSString *const RCTBaseTextShadowViewEmbeddedShadowViewAttributeName;

@interface JBBaseTextShadowView : RCTShadowView {
  @protected NSAttributedString *_Nullable cachedAttributedText;
  @protected JBTextAttributes *_Nullable cachedTextAttributes;
}

@property (nonatomic, strong) JBTextAttributes *textAttributes;

- (NSAttributedString *)attributedTextWithBaseTextAttributes:(nullable JBTextAttributes *)baseTextAttributes;

@end

NS_ASSUME_NONNULL_END
