/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

#import "JBTextShadowView.h"

#import <React/RCTBridge.h>
#import <React/RCTShadowView+Layout.h>
#import <React/RCTUIManager.h>
#import <yoga/Yoga.h>

#import <React/NSTextStorage+FontScaling.h>
#import <React/RCTTextView.h>


#import "JBTextShadowView.h"

@implementation JBTextShadowView

- (NSAttributedString *)attributedTextWithMeasuredAttachmentsThatFitSize:(CGSize)size
{
    static UIImage *placeholderImage;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        placeholderImage = [UIImage new];
    });
    
    NSMutableAttributedString *attributedText =
    [[NSMutableAttributedString alloc] initWithAttributedString:[self attributedTextWithBaseTextAttributes:nil]];
    
    // EDITED
    if (self.text.length) {
        NSAttributedString *propertyAttributedText =
        [[NSAttributedString alloc] initWithString:self.text attributes:self.textAttributes.effectiveTextAttributes];
        [attributedText insertAttributedString:propertyAttributedText atIndex:0];
    }
    // END EDITED
    
    
    return [attributedText copy];
}
@end


