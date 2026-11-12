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
/// How long a segment of `mode` needs to (nearly) settle; 0: the mode has no minimum interval.
/// For modes that have one, the view holds back changes that arrive before the previous
/// transition has landed (its stagger spread + this), then transitions once to the latest text.
+ (CGFloat)settleTimeForMode:(JBTextTransitionMode)mode duration:(CGFloat)duration;

/// `onFinish` runs when every segment has settled or left; the caller shows RN's text again.
- (instancetype)initWithParentLayer:(CALayer *)parent onFinish:(void (^)(void))onFinish;

@property (nonatomic, readonly) BOOL isRunning;

/// How long after its start the last position of the latest transition starts (its stagger spread).
@property (nonatomic, readonly) CGFloat lastStaggerSpread;
/// When the latest transition starts (media time): its change + its delay.
@property (nonatomic, readonly) CFTimeInterval transitionStart;
/// Running, and the latest transition is still in its delay (nothing of it has moved yet).
@property (nonatomic, readonly) BOOL isWaitingToStart;

/// Starts a transition, or retargets the running one. `from` is the text the plan was made
/// from (the previous text), `to` the new text. `viewOffset` is how far the view's old origin
/// is from its new one; segments already on screen are shifted by it so they don't jump.
/// `stagger` (seconds) delays each changed position after the first one that changed.
/// `delay` (seconds) shifts the whole schedule (leaving, kept and arriving segments); the text
/// on screen stays as it is meanwhile. While the running transition is still in its delay, a
/// new one joins it: it keeps that start and ignores its own `delay`.
- (void)transitionFrom:(JBTextSnapshot *)from
                    to:(JBTextSnapshot *)to
                  plan:(JBTextTransitionPlan *)plan
                  mode:(JBTextTransitionMode)mode
              duration:(CGFloat)duration
               stagger:(CGFloat)stagger
                 delay:(CGFloat)delay
                bounds:(CGRect)bounds
            viewOffset:(CGPoint)viewOffset
                 scale:(CGFloat)scale;

/// Moves everything on screen by `offset` (the view moved under it) without retargeting.
- (void)shiftBy:(CGPoint)offset bounds:(CGRect)bounds;

/// Removes everything immediately (no `onFinish`).
- (void)cancel;

@end

NS_ASSUME_NONNULL_END
