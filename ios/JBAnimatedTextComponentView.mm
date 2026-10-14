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
#import "RCTConversions.h"
#import "RCTFabricComponentsPlugins.h"

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

  JBTextTransitionAnimator *_animator;
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

  if ((updateMask & RNComponentViewUpdateMaskState) && [self shouldTransition]) {
    [self startTransition];
  }

  _committedState = _state;
  _committedLayoutMetrics = _layoutMetrics;
  _committedParagraphAttributes = _paragraphAttributes;
}

- (void)prepareForRecycle
{
  [self cancelTransition];
  _committedState = nullptr;
  _state = nullptr;
  [super prepareForRecycle];
}

#pragma mark - Transitions

- (BOOL)shouldTransition
{
  if (_transitionMode == JBTextTransitionModeNone || !_committedState || !_state ||
      UIAccessibilityIsReduceMotionEnabled() || self.window == nil) {
    return NO;
  }
  return _committedState->getData().attributedString.getString() != _state->getData().attributedString.getString();
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

- (void)startTransition
{
  // The old text sits where the view used to be; express it in the new view's coordinates.
  CGPoint viewOffset = CGPointMake(
      _committedLayoutMetrics.frame.origin.x - _layoutMetrics.frame.origin.x,
      _committedLayoutMetrics.frame.origin.y - _layoutMetrics.frame.origin.y);
  JBTextSnapshot *from = [self snapshotOf:_committedState->getData()
                      paragraphAttributes:_committedParagraphAttributes
                            layoutMetrics:_committedLayoutMetrics
                               viewOffset:viewOffset];
  JBTextSnapshot *to = [self snapshotOf:_state->getData()
                    paragraphAttributes:_paragraphAttributes
                          layoutMetrics:_layoutMetrics
                             viewOffset:CGPointZero];
  if (!from || !to) {
    return;
  }

  JBTextTransitionMode mode = _transitionMode;
  JBTextTransitionPlan *plan;
  BOOL needsWholeText = _paragraphAttributes.adjustsFontSizeToFit || _committedParagraphAttributes.adjustsFontSizeToFit ||
      _paragraphAttributes.maximumNumberOfLines > 0 ||
      (mode == JBTextTransitionModeNumeric && (from.lineCount > 1 || to.lineCount > 1));
  if (needsWholeText) {
    plan = [JBTextTransitionPlan wholeTextPlanFromText:from.string toText:to.string];
    mode = JBTextTransitionModeFade;
  } else {
    plan = [JBTextTransitionPlan planFromText:from.string toText:to.string mode:mode direction:_transitionDirection];
  }

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
                   duration:_transitionDuration
                     bounds:self.bounds
                 viewOffset:viewOffset
                      scale:self.traitCollection.displayScale];
}

- (void)cancelTransition
{
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
