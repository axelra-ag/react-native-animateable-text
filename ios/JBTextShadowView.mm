/*
 * Custom - attributedTextWithMeasuredAttachmentsThatFitSize (forked)
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

- (void)setText:(NSString *)text
{
  NSLog(@"setText called! Old value: '%@', New value: '%@'", _text, text);
  _text = [text copy];
  [self dirtyLayout];
}

- (void)didSetProps:(NSArray<NSString *> *)changedProps
{
  [super didSetProps:changedProps];
  NSLog(@"JBTextShadowView didSetProps called with changed props: %@", changedProps);
  
  if ([changedProps containsObject:@"text"]) {
    NSLog(@"Text property changed! New text value: %@", self.text);
  }
}

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

  [attributedText beginEditing];

  [attributedText enumerateAttribute:RCTBaseTextShadowViewEmbeddedShadowViewAttributeName
                             inRange:NSMakeRange(0, attributedText.length)
                             options:0
                          usingBlock:^(RCTShadowView *shadowView, NSRange range, __unused BOOL *stop) {
                            if (!shadowView) {
                              return;
                            }

                            CGSize fittingSize = [shadowView sizeThatFitsMinimumSize:CGSizeZero maximumSize:size];
                            NSTextAttachment *attachment = [NSTextAttachment new];
                            attachment.bounds = (CGRect){CGPointZero, fittingSize};
                            attachment.image = placeholderImage;
                            [attributedText addAttribute:NSAttachmentAttributeName value:attachment range:range];
                          }];

  [attributedText endEditing];

  return [attributedText copy];
}

@end


