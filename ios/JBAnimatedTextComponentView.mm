/*
 * Custom
 */

#ifdef RCT_NEW_ARCH_ENABLED

#import "JBAnimatedTextComponentView.h"

#import <react/renderer/components/JBAnimatedText/ParagraphComponentDescriptor.h>
#import <react/renderer/components/JBAnimatedText/ParagraphProps.h>
#import <react/renderer/components/JBAnimatedTextCodegen/RCTComponentViewHelpers.h>
#import <react/renderer/components/text/ParagraphState.h>
#import <react/renderer/textlayoutmanager/RCTTextLayoutManager.h>
#import <react/renderer/textlayoutmanager/TextLayoutManager.h>
#import <react/utils/ManagedObjectWrapper.h>

#import "JBTextTransitionAnimator.h"
#import "JBTextTransitionPlan.h"
#import <React/RCTConversions.h>
#import <React/RCTFabricComponentsPlugins.h>

using namespace facebook::react;

// RN-internal RCTTextLayoutManager methods. They build the exact NSTextStorage RN draws with,
// so transition segments match <Text> pixel for pixel. Checked with respondsToSelector:.
@interface RCTTextLayoutManager (JBTextTransitions)
- (NSAttributedString *)_nsAttributedStringFromAttributedString:(AttributedString)attributedString;
- (NSTextStorage *)_textStorageAndLayoutManagerWithAttributesString:(NSAttributedString *)attributedString
                                                paragraphAttributes:(ParagraphAttributes)paragraphAttributes
                                                               size:(CGSize)size;
- (void)processTruncatedAttributedText:(NSTextStorage *)textStorage
                         textContainer:(NSTextContainer *)textContainer
                         layoutManager:(NSLayoutManager *)layoutManager;
@end

using ParagraphConcreteState = ConcreteState<ParagraphState>;

namespace {
// One set of transition settings: contentTransition's or reveal's.
struct JBTransitionOptions {
  JBTextTransitionMode mode = JBTextTransitionModeNone;
  CGFloat duration = 0;
  NSString *direction = @"auto";
  JBTextTransitionUnit unit = JBTextTransitionUnitAuto;
  CGFloat stagger = 0;
  JBTextTransitionScope scope = JBTextTransitionScopeAll;
  /// < 0 automatic, 0 off, > 0 that long (seconds).
  CGFloat minInterval = -1;
  CGFloat delay = 0;
};
} // namespace

@implementation JBAnimatedTextComponentView {
  // What is on screen after the last mount transaction.
  std::shared_ptr<const ParagraphConcreteState> _committedState;
  LayoutMetrics _committedLayoutMetrics;
  ParagraphAttributes _committedParagraphAttributes;

  std::shared_ptr<const ParagraphConcreteState> _state;
  ParagraphAttributes _paragraphAttributes;
  JBTextTransitionMode _transitionMode;
  CGFloat _transitionDuration;
  NSString *_transitionDirection;
  JBTextTransitionUnit _transitionUnit;
  CGFloat _transitionStagger;
  CGFloat _transitionDelay;
  JBTextTransitionScope _transitionScope;
  JBTransitionOptions _revealOptions;

  // transitionKey (canonical string; nil: no key) now, and when the text last changed.
  NSString *_transitionKey;
  NSString *_keyAtLastTextChange;
  // The reveal runs once per mounted view, the first time it shows non-empty text. Pending
  // until the view is in a window (on mount, finalizeUpdates runs before it is inserted).
  BOOL _hasRevealed;
  BOOL _isRevealPending;

  JBTextTransitionAnimator *_animator;
  // Settings of the change being started, and of the transition on screen.
  JBTransitionOptions _changeOptions;
  JBTransitionOptions _runningOptions;

  // What the animator's segments show. Differs from the committed state while changes are
  // held back (they arrived sooner than the mode's minimum interval).
  std::shared_ptr<const ParagraphConcreteState> _displayedState;
  LayoutMetrics _displayedLayoutMetrics;
  ParagraphAttributes _displayedParagraphAttributes;
  CFTimeInterval _nextTransitionTime;
  CGFloat _transitionMinInterval;
  BOOL _hasHeldChange;
  NSUInteger _heldGeneration;
}

+ (ComponentDescriptorProvider)componentDescriptorProvider
{
  return concreteComponentDescriptorProvider<CParagraphComponentDescriptor>();
}

- (void)updateProps:(const Props::Shared &)props oldProps:(const Props::Shared &)oldProps
{
  const auto &newProps = static_cast<const CParagraphProps &>(*props);
  _paragraphAttributes = newProps.paragraphAttributes;
  _transitionMode = [JBTextTransitionPlan modeFromString:RCTNSStringFromString(newProps.contentTransitionType)];
  _transitionDuration = newProps.contentTransitionDuration / 1000.0;
  _transitionDirection = RCTNSStringFromString(newProps.contentTransitionDirection);
  _transitionUnit = [JBTextTransitionPlan unitFromString:RCTNSStringFromString(newProps.contentTransitionUnit)];
  _transitionStagger = MAX(0, newProps.contentTransitionStagger) / 1000.0;
  _transitionMinInterval = newProps.contentTransitionMinInterval / 1000.0;
  _transitionDelay = MAX(0, newProps.contentTransitionDelay) / 1000.0;
  _transitionScope = [JBTextTransitionPlan scopeFromString:RCTNSStringFromString(newProps.contentTransitionScope)];
  _transitionKey = newProps.transitionKey.value.empty() ? nil : RCTNSStringFromString(newProps.transitionKey.value);
  JBTransitionOptions reveal;
  reveal.mode = [JBTextTransitionPlan modeFromString:RCTNSStringFromString(newProps.revealType)];
  reveal.duration = newProps.revealDuration / 1000.0;
  reveal.direction = RCTNSStringFromString(newProps.revealDirection);
  reveal.unit = [JBTextTransitionPlan unitFromString:RCTNSStringFromString(newProps.revealUnit)];
  reveal.stagger = MAX(0, newProps.revealStagger) / 1000.0;
  reveal.delay = MAX(0, newProps.revealDelay) / 1000.0;
  reveal.minInterval = 0;
  _revealOptions = reveal;
  [super updateProps:props oldProps:oldProps];
}

- (void)updateState:(const State::Shared &)state oldState:(const State::Shared &)oldState
{
  _state = std::static_pointer_cast<const ParagraphConcreteState>(state);
  [super updateState:state oldState:oldState];
}

- (void)finalizeUpdates:(RNComponentViewUpdateMask)updateMask
{
  [super finalizeUpdates:updateMask];

  if ((updateMask & RNComponentViewUpdateMaskState) && _state) {
    [self textMayHaveChanged];
  }

  _committedState = _state;
  _committedLayoutMetrics = _layoutMetrics;
  _committedParagraphAttributes = _paragraphAttributes;
}

- (void)didMoveToWindow
{
  [super didMoveToWindow];
  if (_isRevealPending && self.window) {
    [self startReveal];
  }
}

- (void)prepareForRecycle
{
  [self cancelTransition];
  _hasRevealed = NO;
  _isRevealPending = NO;
  _keyAtLastTextChange = nil;
  _committedState = nullptr;
  _state = nullptr;
  [super prepareForRecycle];
}

#pragma mark - Transitions

- (JBTransitionOptions)contentOptions
{
  JBTransitionOptions options;
  options.mode = _transitionMode;
  options.duration = _transitionDuration;
  options.direction = _transitionDirection ?: @"auto";
  options.unit = _transitionUnit;
  options.stagger = _transitionStagger;
  options.scope = _transitionScope;
  options.minInterval = _transitionMinInterval;
  options.delay = _transitionDelay;
  return options;
}

// Called for every state update; decides what a text change does.
- (void)textMayHaveChanged
{
  const auto &newText = _state->getData().attributedString.getString();
  if (_committedState && _committedState->getData().attributedString.getString() == newText) {
    return;
  }

  // transitionKey: animate only if the key changed since the last text change (no key: always).
  BOOL keyAllows = _transitionKey == nil || ![_transitionKey isEqualToString:_keyAtLastTextChange];
  _keyAtLastTextChange = _transitionKey;

  // The first non-empty text is the reveal's, whatever the key.
  if (!_hasRevealed && !newText.empty()) {
    _hasRevealed = YES;
    if (_revealOptions.mode != JBTextTransitionModeNone) {
      [self scheduleReveal];
      return;
    }
  }
  if (_isRevealPending) {
    // The reveal shows whatever the text is when it starts.
    return;
  }

  // A change that doesn't animate swaps instantly from rest, but retargets a running
  // transition: snapping what is on screen would jump.
  BOOL isRunning = _animator.isRunning;
  JBTransitionOptions options;
  if (_transitionMode != JBTextTransitionModeNone && (keyAllows || isRunning)) {
    options = [self contentOptions];
    // A change that doesn't animate never waits: it retargets at once, or joins the running
    // transition's start if that is still in its delay.
    if (!keyAllows) options.delay = 0;
  } else if (isRunning) {
    // e.g. a reveal with contentTransition 'none': carry on with the reveal's look.
    options = _runningOptions;
    options.delay = 0;
  } else {
    return;
  }
  if (!_committedState || UIAccessibilityIsReduceMotionEnabled() || self.window == nil) {
    if (isRunning) {
      [self cancelTransition];
    }
    return;
  }

  _changeOptions = options;
  // The old text sits where the view used to be; express it in the new view's coordinates.
  CGPoint viewOffset = CGPointMake(
      _committedLayoutMetrics.frame.origin.x - _layoutMetrics.frame.origin.x,
      _committedLayoutMetrics.frame.origin.y - _layoutMetrics.frame.origin.y);
  if ([self shouldHoldChange]) {
    [self holdChangeWithViewOffset:viewOffset];
  } else {
    [self startTransitionWithViewOffset:viewOffset];
  }
}

- (void)scheduleReveal
{
  [self cancelTransition];
  if (UIAccessibilityIsReduceMotionEnabled()) {
    return;
  }
  _isRevealPending = YES;
  [self paragraphTextView].hidden = YES;
  if (self.window) {
    [self startReveal];
  }
}

- (void)startReveal
{
  _isRevealPending = NO;
  JBTextSnapshot *to = _state ? [self snapshotOf:_state->getData()
                               paragraphAttributes:_paragraphAttributes
                                     layoutMetrics:_layoutMetrics
                                        viewOffset:CGPointZero]
                              : nil;
  if (!to || to.string.length == 0 || UIAccessibilityIsReduceMotionEnabled()) {
    [self paragraphTextView].hidden = NO;
    return;
  }
  JBTransitionOptions options = _revealOptions;
  JBTextTransitionMode mode = options.mode;
  JBTextTransitionPlan *plan;
  if (_paragraphAttributes.adjustsFontSizeToFit || _paragraphAttributes.maximumNumberOfLines > 0) {
    // Same fallback as a change: truncation and font scaling are whole-text.
    plan = [JBTextTransitionPlan wholeTextPlanFromText:@"" toText:to.string];
    mode = JBTextTransitionModeFade;
  } else {
    plan = [JBTextTransitionPlan revealPlanForText:to.string mode:mode direction:options.direction unit:options.unit];
  }
  [self runPlan:plan mode:mode options:options from:to to:to viewOffset:CGPointZero];
  // A change right after mount retargets at once instead of waiting for the reveal.
  _nextTransitionTime = CACurrentMediaTime();
}

- (nullable RCTTextLayoutManager *)textLayoutManagerFor:(const ParagraphState &)data
{
  auto textLayoutManager = data.layoutManager.lock();
  if (!textLayoutManager) {
    return nil;
  }
  RCTTextLayoutManager *manager =
      (RCTTextLayoutManager *)unwrapManagedObject(textLayoutManager->getNativeTextLayoutManager());
  BOOL hasInternals = [manager respondsToSelector:@selector(_nsAttributedStringFromAttributedString:)] &&
      [manager respondsToSelector:@selector(_textStorageAndLayoutManagerWithAttributesString:
                                                                          paragraphAttributes:size:)] &&
      [manager respondsToSelector:@selector(processTruncatedAttributedText:textContainer:layoutManager:)];
  return hasInternals ? manager : nil;
}

- (nullable JBTextSnapshot *)snapshotOf:(const ParagraphState &)data
                    paragraphAttributes:(const ParagraphAttributes &)paragraphAttributes
                          layoutMetrics:(const LayoutMetrics &)layoutMetrics
                             viewOffset:(CGPoint)viewOffset
{
  RCTTextLayoutManager *manager = [self textLayoutManagerFor:data];
  if (!manager) {
    return nil;
  }
  CGRect contentFrame = RCTCGRectFromRect(layoutMetrics.getContentFrame());
  NSAttributedString *attributedString = [manager _nsAttributedStringFromAttributedString:data.attributedString];
  NSTextStorage *textStorage = [manager _textStorageAndLayoutManagerWithAttributesString:attributedString
                                                                     paragraphAttributes:paragraphAttributes
                                                                                    size:contentFrame.size];
  NSLayoutManager *layoutManager = textStorage.layoutManagers.firstObject;
  NSTextContainer *textContainer = layoutManager.textContainers.firstObject;
  [manager processTruncatedAttributedText:textStorage textContainer:textContainer layoutManager:layoutManager];
  [layoutManager ensureLayoutForTextContainer:textContainer];

  JBTextSnapshot *snapshot = [JBTextSnapshot new];
  snapshot.textStorage = textStorage;
  snapshot.textOrigin = CGPointMake(contentFrame.origin.x + viewOffset.x, contentFrame.origin.y + viewOffset.y);
  return snapshot;
}

// Modes with a minimum interval (roll) don't start a transition for every change: until the
// last one has landed the change is held back, and one transition to the latest text runs
// then. The last change is always shown. A transition still in its delay hasn't moved
// anything yet, so a change then re-aims it instead (keeping its start).
- (BOOL)shouldHoldChange
{
  return _animator.isRunning && _displayedState && CACurrentMediaTime() < _nextTransitionTime &&
      !_animator.isWaitingToStart;
}

// contentTransitionMinInterval: < 0 automatic (the transition has landed: its stagger spread
// plus a segment's settle time), 0 off, > 0 that long. Counted from the transition's start
// (after its delay).
- (CGFloat)minimumInterval
{
  const JBTransitionOptions &options = _changeOptions;
  CGFloat settleTime = [JBTextTransitionAnimator settleTimeForMode:options.mode duration:options.duration];
  if (settleTime <= 0 || options.minInterval == 0) return 0;
  return options.minInterval > 0 ? options.minInterval : _animator.lastStaggerSpread + settleTime;
}

- (void)holdChangeWithViewOffset:(CGPoint)viewOffset
{
  // RN's text view (hidden) already has the new text; the segments keep showing the old one.
  [_animator shiftBy:viewOffset bounds:self.bounds];
  if (_hasHeldChange) {
    return;
  }
  _hasHeldChange = YES;
  NSUInteger generation = ++_heldGeneration;
  CFTimeInterval delay = MAX(0, _nextTransitionTime - CACurrentMediaTime());
  __weak __typeof(self) weakSelf = self;
  dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(delay * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
    __typeof(self) strongSelf = weakSelf;
    if (strongSelf && strongSelf->_heldGeneration == generation) {
      [strongSelf showHeldChange];
    }
  });
}

- (void)showHeldChange
{
  if (!_hasHeldChange || !_state || !_displayedState) {
    _hasHeldChange = NO;
    return;
  }
  if (_displayedState->getData().attributedString.getString() == _state->getData().attributedString.getString()) {
    // The text went back to what is on screen.
    _hasHeldChange = NO;
    return;
  }
  // The segments were already moved with the view while the change was held.
  [self startTransitionWithViewOffset:CGPointZero];
}

- (void)startTransitionWithViewOffset:(CGPoint)viewOffset
{
  // A held change transitions from what the segments show, not from the last committed text.
  BOOL fromDisplayed = _hasHeldChange && _displayedState;
  JBTextSnapshot *from = [self snapshotOf:(fromDisplayed ? _displayedState : _committedState)->getData()
                      paragraphAttributes:fromDisplayed ? _displayedParagraphAttributes : _committedParagraphAttributes
                            layoutMetrics:fromDisplayed ? _displayedLayoutMetrics : _committedLayoutMetrics
                               viewOffset:viewOffset];
  JBTextSnapshot *to = [self snapshotOf:_state->getData()
                    paragraphAttributes:_paragraphAttributes
                          layoutMetrics:_layoutMetrics
                             viewOffset:CGPointZero];
  if (!from || !to) {
    return;
  }

  const JBTransitionOptions options = _changeOptions;
  JBTextTransitionMode mode = options.mode;
  JBTextTransitionPlan *plan;
  BOOL needsWholeText = _paragraphAttributes.adjustsFontSizeToFit || _committedParagraphAttributes.adjustsFontSizeToFit ||
      _paragraphAttributes.maximumNumberOfLines > 0 ||
      ([JBTextTransitionPlan isNumericMode:mode] && options.unit != JBTextTransitionUnitWhole &&
       (from.lineCount > 1 || to.lineCount > 1));
  if (needsWholeText) {
    plan = [JBTextTransitionPlan wholeTextPlanFromText:from.string toText:to.string];
    mode = JBTextTransitionModeFade;
  } else {
    plan = [JBTextTransitionPlan planFromText:from.string
                                       toText:to.string
                                         mode:mode
                                    direction:options.direction
                                         unit:options.unit
                                        scope:options.scope];
  }
  [self runPlan:plan mode:mode options:options from:from to:to viewOffset:viewOffset];
  _nextTransitionTime = _animator.transitionStart + [self minimumInterval];
}

- (void)runPlan:(JBTextTransitionPlan *)plan
           mode:(JBTextTransitionMode)mode
        options:(const JBTransitionOptions &)options
           from:(JBTextSnapshot *)from
             to:(JBTextSnapshot *)to
     viewOffset:(CGPoint)viewOffset
{
  if (!_animator) {
    __weak __typeof(self) weakSelf = self;
    _animator = [[JBTextTransitionAnimator alloc] initWithParentLayer:self.layer
                                                             onFinish:^{
                                                               [weakSelf paragraphTextView].hidden = NO;
                                                             }];
  }
  [self paragraphTextView].hidden = YES;
  // A change mid-transition retargets from what is on screen.
  [_animator transitionFrom:from
                         to:to
                       plan:plan
                       mode:mode
                   duration:options.duration
                    stagger:options.stagger
                      delay:options.delay
                     bounds:self.bounds
                 viewOffset:viewOffset
                      scale:self.traitCollection.displayScale];
  _runningOptions = options;
  _changeOptions = options;
  _displayedState = _state;
  _displayedLayoutMetrics = _layoutMetrics;
  _displayedParagraphAttributes = _paragraphAttributes;
  _hasHeldChange = NO;
  _heldGeneration++;
}

- (void)cancelTransition
{
  _isRevealPending = NO;
  _hasHeldChange = NO;
  _heldGeneration++;
  _displayedState = nullptr;
  [_animator cancel];
  [self paragraphTextView].hidden = NO;
}

// RN's private RCTParagraphTextView, which draws the text.
- (nullable UIView *)paragraphTextView
{
  static Class textViewClass = NSClassFromString(@"RCTParagraphTextView");
  for (UIView *subview in self.subviews) {
    if ([subview isKindOfClass:textViewClass]) {
      return subview;
    }
  }
  return nil;
}

Class<RCTComponentViewProtocol> JBAnimatedTextCls(void)
{
  return JBAnimatedTextComponentView.class;
}

@end

#endif
