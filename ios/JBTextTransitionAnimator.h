/*
 * Custom
 */

#import <UIKit/UIKit.h>

#import "JBTextTransitionPlan.h"

NS_ASSUME_NONNULL_BEGIN

/// Text laid out exactly as RN draws it (same NSTextStorage setup as RCTTextLayoutManager).
@interface JBTextSnapshot : NSObject
@property (nonatomic, strong) NSTextStorage *textStorage;
/// Where the glyphs are drawn, in the coordinate space of the view at the end of the transition.
@property (nonatomic, assign) CGPoint textOrigin;
@property (nonatomic, readonly) NSString *string;
@property (nonatomic, readonly) NSUInteger lineCount;
@end

/// Draws text transitions on a layer above RN's (hidden) text. Each segment animates on its
/// own timeline, so a change that arrives mid-transition retargets from what is on screen.
/// Timing and looks are mirrored in Android's TextTransitionTiming; keep both in sync.
@interface JBTextTransitionAnimator : NSObject

/// Progress (0…1) of a critically damped spring that matches SwiftUI's `.smooth(duration:)`.
+ (CGFloat)progressAtTime:(CGFloat)time duration:(CGFloat)duration;
+ (CGFloat)totalTimeForDuration:(CGFloat)duration;

/// `onFinish` runs when every segment has settled or left; the caller shows RN's text again.
- (instancetype)initWithParentLayer:(CALayer *)parent onFinish:(void (^)(void))onFinish;

@property (nonatomic, readonly) BOOL isRunning;

/// Starts a transition, or retargets the running one. `from` is the text the plan was made
/// from (the previous text), `to` the new text. `viewOffset` is how far the view's old origin
/// is from its new one; segments already on screen are shifted by it so they don't jump.
- (void)transitionFrom:(JBTextSnapshot *)from
                    to:(JBTextSnapshot *)to
                  plan:(JBTextTransitionPlan *)plan
                  mode:(JBTextTransitionMode)mode
              duration:(CGFloat)duration
                bounds:(CGRect)bounds
            viewOffset:(CGPoint)viewOffset
                 scale:(CGFloat)scale;

/// Removes everything immediately (no `onFinish`).
- (void)cancel;

@end

NS_ASSUME_NONNULL_END
