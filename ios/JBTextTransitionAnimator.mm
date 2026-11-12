/*
 * Custom
 */

#import "JBTextTransitionAnimator.h"

#import <Accelerate/Accelerate.h>

#include <cmath>

@implementation JBTextSnapshot

- (NSString *)string
{
  return _textStorage.string;
}

- (NSUInteger)lineCount
{
  NSLayoutManager *layoutManager = _textStorage.layoutManagers.firstObject;
  NSTextContainer *textContainer = layoutManager.textContainers.firstObject;
  NSRange glyphRange = [layoutManager glyphRangeForTextContainer:textContainer];
  __block NSUInteger count = 0;
  [layoutManager enumerateLineFragmentsForGlyphRange:glyphRange
                                          usingBlock:^(CGRect, CGRect, NSTextContainer *, NSRange, BOOL *) {
                                            count++;
                                          }];
  return count;
}

@end

namespace {

// Keep in sync with android/.../TextTransitionTiming.kt.
const CGFloat kDefaultDuration = 0.5;
const CGFloat kSettleOmegaTime = 9.23; // (1 + x) e^-x < 0.001
const CGFloat kHurryDuration = 0.15; // segments already leaving when a new change arrives
const CGFloat kHurryDriftFactor = 0.25;
// The delays of a staggered change never add up to more than this, however long the text.
const CGFloat kMaxStaggerSpread = 0.3;
const CGFloat kFramesPerSecond = 60;
const int kBlurLevels = 4;
// Leaving segments a text keeps at most; the oldest go first. Bounds work and memory when
// changes arrive every frame (scrubbing).
const NSUInteger kMaxLeavingPerSegment = 2;
const NSUInteger kMinMaxLeaving = 8;
const CGFloat kInvisibleOpacity = 0.01;
// Image caches, bounded by bytes. Entries are evicted LRU-ish by NSCache.
const NSUInteger kSharpCacheBytes = 6 * 1024 * 1024;
const NSUInteger kBlurCacheBytes = 6 * 1024 * 1024;

struct Look {
  CGFloat translateX = 0;
  CGFloat translateY = 0;
  CGFloat scale = 1;
  CGFloat opacity = 1;
  CGFloat blur = 0; // 0…1 of the segment's maximum blur
};

// Keep in sync with SegmentMotion in android/.../TextTransitionTiming.kt.
struct Motion {
  CGFloat offsetFactor; // of the line height
  CGFloat minScale;
  CGFloat blurScale; // of the maximum blur; 0: no blur
  // An arriving segment that is replaced while still fainter than this hands its timeline to
  // its replacement instead of leaving, so values that change every frame still get to show.
  CGFloat takeoverOpacity;
  // How long a segment needs to (nearly) settle, of the duration; 0: no minimum interval.
  // With one, a change that arrives before the previous transition has landed (stagger
  // spread + this) is held back and the latest one is shown when it has.
  CGFloat minIntervalFactor;

  bool hasBlur() const { return blurScale > 0; }
};

Motion motionForMode(JBTextTransitionMode mode)
{
  switch (mode) {
    case JBTextTransitionModeNumeric:
      return {0.5, 0.7, 1, 0, 0};
    case JBTextTransitionModeRoll:
      // 0.25 × line height ≈ 0.3 em. 0.65 × duration: a segment is ~90% settled.
      return {0.25, 1, 0.2, 0.9, 0.65};
    case JBTextTransitionModeBlur:
      return {0, 1, 1, 0, 0};
    default:
      return {0, 1, 0, 0, 0};
  }
}

CGFloat clamp01(CGFloat value)
{
  return MIN(1, MAX(0, value));
}

CGFloat omegaFor(CGFloat duration)
{
  return 2 * M_PI / (duration > 0 ? duration : kDefaultDuration);
}

CGFloat springProgress(CGFloat time, CGFloat duration)
{
  if (time <= 0) return 0;
  CGFloat omegaTime = omegaFor(duration) * time;
  return 1 - (1 + omegaTime) * std::exp(-omegaTime);
}

CGFloat totalTimeFor(CGFloat duration)
{
  return kSettleOmegaTime / omegaFor(duration);
}

// Delay between neighbouring positions, shortened so the whole change fits the spread cap.
CGFloat staggerStepFor(CGFloat stagger, NSInteger maxIndex)
{
  if (stagger <= 0 || maxIndex <= 0) return 0;
  return MIN(stagger, kMaxStaggerSpread / maxIndex);
}

// Keep in sync with android/.../TextTransitionTiming.kt.
// Leave: from wherever it is, drift by `drift`, shrink to `minScale`, fade out, blur in.
Look leaveLook(const Look &from, CGFloat p, CGFloat drift, CGFloat minScale, bool hasBlur)
{
  Look look;
  look.translateX = from.translateX;
  look.translateY = from.translateY + drift * p;
  look.scale = from.scale + (minScale - from.scale) * p;
  look.opacity = from.opacity * (1 - clamp01(p / 0.7));
  look.blur = hasBlur ? from.blur + (1 - from.blur) * clamp01(p / 0.7) : 0;
  return look;
}

// Settle: from wherever it is to rest (identity, opaque, sharp).
Look settleLook(const Look &from, CGFloat p)
{
  Look look;
  look.translateX = from.translateX * (1 - p);
  look.translateY = from.translateY * (1 - p);
  look.scale = from.scale + (1 - from.scale) * p;
  look.opacity = from.opacity + (1 - from.opacity) * clamp01(p / 0.6);
  look.blur = from.blur * (1 - clamp01(p / 0.8));
  return look;
}

bool isRestLook(const Look &look)
{
  return std::fabs(look.translateX) < 0.01 && std::fabs(look.translateY) < 0.01 && std::fabs(look.scale - 1) < 0.001 &&
      std::fabs(look.opacity - 1) < 0.001 && look.blur < 0.001;
}

// Whether two glyph rects sit on top of each other on screen (same line, clearly overlapping).
bool overlapsOnScreen(CGRect a, CGRect b)
{
  CGFloat overlapX = MIN(CGRectGetMaxX(a), CGRectGetMaxX(b)) - MAX(CGRectGetMinX(a), CGRectGetMinX(b));
  CGFloat overlapY = MIN(CGRectGetMaxY(a), CGRectGetMaxY(b)) - MAX(CGRectGetMinY(a), CGRectGetMinY(b));
  return overlapX > 0.3 * MIN(a.size.width, b.size.width) && overlapY > 0.5 * MIN(a.size.height, b.size.height);
}

CATransform3D transformFor(const Look &look)
{
  CATransform3D transform = CATransform3DMakeTranslation(look.translateX, look.translateY, 0);
  return CATransform3DScale(transform, look.scale, look.scale, 1);
}

CGRect pixelAligned(CGRect rect, CGFloat scale)
{
  CGFloat minX = std::floor(CGRectGetMinX(rect) * scale) / scale;
  CGFloat minY = std::floor(CGRectGetMinY(rect) * scale) / scale;
  CGFloat maxX = std::ceil(CGRectGetMaxX(rect) * scale) / scale;
  CGFloat maxY = std::ceil(CGRectGetMaxY(rect) * scale) / scale;
  return CGRectMake(minX, minY, maxX - minX, maxY - minY);
}

NSRange glyphRangeFor(JBTextSnapshot *snapshot, NSRange characterRange)
{
  NSLayoutManager *layoutManager = snapshot.textStorage.layoutManagers.firstObject;
  return [layoutManager glyphRangeForCharacterRange:characterRange actualCharacterRange:NULL];
}

CGRect textRectFor(JBTextSnapshot *snapshot, NSRange characterRange)
{
  NSLayoutManager *layoutManager = snapshot.textStorage.layoutManagers.firstObject;
  NSTextContainer *textContainer = layoutManager.textContainers.firstObject;
  CGRect rect = [layoutManager boundingRectForGlyphRange:glyphRangeFor(snapshot, characterRange)
                                         inTextContainer:textContainer];
  return CGRectOffset(rect, snapshot.textOrigin.x, snapshot.textOrigin.y);
}

void drawGlyphs(JBTextSnapshot *snapshot, NSRange characterRange)
{
  NSLayoutManager *layoutManager = snapshot.textStorage.layoutManagers.firstObject;
  NSRange glyphRange = glyphRangeFor(snapshot, characterRange);
  [layoutManager drawBackgroundForGlyphRange:glyphRange atPoint:snapshot.textOrigin];
  [layoutManager drawGlyphsForGlyphRange:glyphRange atPoint:snapshot.textOrigin];
}

UIImage *renderImage(CGRect canvas, CGFloat scale, void (^draw)(void))
{
  UIGraphicsImageRendererFormat *format = [UIGraphicsImageRendererFormat preferredFormat];
  format.scale = scale;
  format.opaque = NO;
  format.preferredRange = UIGraphicsImageRendererFormatRangeStandard;
  UIGraphicsImageRenderer *renderer = [[UIGraphicsImageRenderer alloc] initWithSize:canvas.size format:format];
  return [renderer imageWithActions:^(UIGraphicsImageRendererContext *context) {
    CGContextTranslateCTM(context.CGContext, -canvas.origin.x, -canvas.origin.y);
    draw();
  }];
}

const vImage_CGImageFormat kBlurFormat = {
    .bitsPerComponent = 8,
    .bitsPerPixel = 32,
    .colorSpace = NULL,
    .bitmapInfo = (CGBitmapInfo)kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little,
    .version = 0,
    .decode = NULL,
    .renderingIntent = kCGRenderingIntentDefault,
};

// Blur levels are computed on a half-size copy (detail is blurred away anyway) with two tent
// passes, which approximate a Gaussian; the layer scales them back up.
NSArray *createBlurLevels(CGImageRef source, CGFloat maxSigma, int levels)
{
  NSMutableArray *images = [NSMutableArray array];
  vImage_Buffer full;
  if (vImageBuffer_InitWithCGImage(&full, (vImage_CGImageFormat *)&kBlurFormat, NULL, source, kvImageNoFlags) !=
      kvImageNoError) {
    return images;
  }
  vImage_Buffer half, blurred, scratch;
  vImagePixelCount width = MAX((vImagePixelCount)1, full.width / 2);
  vImagePixelCount height = MAX((vImagePixelCount)1, full.height / 2);
  vImageBuffer_Init(&half, height, width, 32, kvImageNoFlags);
  vImageBuffer_Init(&blurred, height, width, 32, kvImageNoFlags);
  vImageBuffer_Init(&scratch, height, width, 32, kvImageNoFlags);
  vImageScale_ARGB8888(&full, &half, NULL, kvImageNoFlags);

  Pixel_8888 clear = {0, 0, 0, 0};
  for (int level = 1; level < levels; level++) {
    CGFloat sigma = maxSigma * level / (levels - 1) / 2;
    uint32_t boxWidth = (uint32_t)std::floor(std::sqrt(3 * sigma * sigma + 1));
    uint32_t kernel = MAX((uint32_t)3, (boxWidth * 2 - 1) | 1);
    vImageTentConvolve_ARGB8888(&half, &scratch, NULL, 0, 0, kernel, kernel, clear, kvImageBackgroundColorFill);
    vImageTentConvolve_ARGB8888(&scratch, &blurred, NULL, 0, 0, kernel, kernel, clear, kvImageBackgroundColorFill);
    vImage_Error error = kvImageNoError;
    CGImageRef image =
        vImageCreateCGImageFromBuffer(&blurred, (vImage_CGImageFormat *)&kBlurFormat, NULL, NULL, kvImageNoFlags, &error);
    if (error != kvImageNoError || !image) break;
    [images addObject:(__bridge_transfer id)image];
  }
  free(full.data);
  free(half.data);
  free(blurred.data);
  free(scratch.data);
  return images;
}

NSCache *costCache(NSUInteger bytes)
{
  NSCache *cache = [NSCache new];
  cache.totalCostLimit = bytes;
  return cache;
}

NSCache<NSString *, id> *sharpCache()
{
  static NSCache *cache;
  static dispatch_once_t once;
  dispatch_once(&once, ^{
    cache = costCache(kSharpCacheBytes);
  });
  return cache;
}

NSCache<NSString *, NSArray *> *blurCache()
{
  static NSCache *cache;
  static dispatch_once_t once;
  dispatch_once(&once, ^{
    cache = costCache(kBlurCacheBytes);
  });
  return cache;
}

NSUInteger imageBytes(CGImageRef image)
{
  return CGImageGetBytesPerRow(image) * CGImageGetHeight(image);
}

// Everything about a segment's glyphs that changes how they are drawn.
NSString *appearanceKey(JBTextSnapshot *snapshot, NSRange range)
{
  NSDictionary<NSAttributedStringKey, id> *attributes = [snapshot.textStorage attributesAtIndex:range.location
                                                                                  effectiveRange:NULL];
  UIFont *font = attributes[NSFontAttributeName];
  return [NSString stringWithFormat:@"%@|%@|%.2f|%@|%@|%@|%@|%@",
                                    [snapshot.string substringWithRange:range],
                                    font.fontName,
                                    font.pointSize,
                                    attributes[NSForegroundColorAttributeName],
                                    attributes[NSKernAttributeName],
                                    attributes[NSShadowAttributeName],
                                    attributes[NSUnderlineStyleAttributeName],
                                    attributes[NSStrikethroughStyleAttributeName]];
}

} // namespace

#pragma mark - Segment

typedef NS_ENUM(NSInteger, JBSegmentRole) {
  JBSegmentRoleSettle,
  JBSegmentRoleLeave,
};

/// One piece of text on screen, with its own timeline.
@interface JBTextSegment : NSObject
@property (nonatomic, strong) CALayer *layer;
/// Index 0 is sharp; the rest are increasingly blurred.
@property (nonatomic, copy) NSArray *blurLevels;
/// Glyph bounds at rest, in the view's coordinates.
@property (nonatomic, assign) CGRect restRect;
/// Range in the text this segment belongs to.
@property (nonatomic, assign) NSRange range;
@property (nonatomic, assign) JBSegmentRole role;
@property (nonatomic, assign) Look from;
@property (nonatomic, assign) CGFloat drift;
@property (nonatomic, assign) CGFloat minScale;
@property (nonatomic, assign) BOOL hasBlur;
@property (nonatomic, assign) CFTimeInterval startTime;
@property (nonatomic, assign) CGFloat duration;
/// A staggered leave: the segment carries on as it is until `leaveStart`, then leaves.
@property (nonatomic, assign) BOOL hasPendingLeave;
/// Leaving segments never move sideways: they rise/fade from where they were when they were
/// told to leave, even while still waiting for their turn.
@property (nonatomic, assign) BOOL hasFixedX;
@property (nonatomic, assign) CGFloat fixedTranslateX;
@property (nonatomic, assign) CFTimeInterval leaveStart;
@property (nonatomic, assign) CGFloat leaveDuration;
/// Where the layer's animations begin (the start of the timeline, or when it was retargeted).
@property (nonatomic, assign) CFTimeInterval animationStart;
/// For an arriving segment: the leaving segment it replaces in its slot.
@property (nonatomic, weak) JBTextSegment *partner;
/// appearanceKey() of the drawn glyphs; equal keys can share images and layers.
@property (nonatomic, copy) NSString *appearance;
/// Set when the timeline changed and the layer's animations must be rebuilt.
@property (nonatomic, assign) BOOL needsAnimation;
@property (nonatomic, assign) BOOL isHurrying;
@end

@implementation JBTextSegment

- (CFTimeInterval)endTime
{
  return _hasPendingLeave ? _leaveStart + totalTimeFor(_leaveDuration) : _startTime + totalTimeFor(_duration);
}

- (Look)baseLookAt:(CFTimeInterval)time
{
  CGFloat elapsed = time - _startTime;
  CGFloat p = elapsed >= totalTimeFor(_duration) ? 1 : springProgress(elapsed, _duration);
  return _role == JBSegmentRoleLeave ? leaveLook(_from, p, _drift, _minScale, _hasBlur) : settleLook(_from, p);
}

- (Look)lookAt:(CFTimeInterval)time
{
  Look look;
  if (_hasPendingLeave && time >= _leaveStart) {
    CGFloat elapsed = time - _leaveStart;
    CGFloat p = elapsed >= totalTimeFor(_leaveDuration) ? 1 : springProgress(elapsed, _leaveDuration);
    look = leaveLook([self baseLookAt:_leaveStart], p, _drift, _minScale, _hasBlur);
  } else {
    look = [self baseLookAt:time];
  }
  if (_hasFixedX) look.translateX = _fixedTranslateX;
  return look;
}

- (BOOL)isLeavingAt:(CFTimeInterval)time
{
  return _role == JBSegmentRoleLeave || (_hasPendingLeave && time >= _leaveStart);
}

/// Arriving and not started yet (staggered or delayed): invisible, waiting for its turn. A kept
/// segment waiting for a delayed start is visible (the old text), so it isn't waiting.
- (BOOL)isWaitingAt:(CFTimeInterval)time
{
  return _role == JBSegmentRoleSettle && !_hasPendingLeave && _startTime > time && _from.opacity <= kInvisibleOpacity;
}

- (void)startRole:(JBSegmentRole)role from:(Look)from at:(CFTimeInterval)time duration:(CGFloat)duration
{
  _role = role;
  _from = from;
  _startTime = time;
  _duration = duration;
  _hasPendingLeave = NO;
  _animationStart = time;
  _needsAnimation = YES;
}

/// Leaves at `time` (in the future) from whatever it looks like then.
- (void)leaveAt:(CFTimeInterval)time duration:(CGFloat)duration now:(CFTimeInterval)now
{
  _hasPendingLeave = YES;
  _leaveStart = time;
  _leaveDuration = duration;
  _animationStart = now;
  _needsAnimation = YES;
}

- (void)applyAnimation
{
  _needsAnimation = NO;
  if (_role == JBSegmentRoleSettle && !_hasPendingLeave && isRestLook(_from)) {
    // Already at rest: no animation, just the sharp glyphs.
    [_layer removeAllAnimations];
    _layer.transform = CATransform3DIdentity;
    _layer.opacity = 1;
    _layer.contents = _blurLevels.firstObject;
    return;
  }
  CGFloat total = MAX(0.001, self.endTime - _animationStart);
  size_t frameCount = MAX((size_t)2, (size_t)std::ceil(total * kFramesPerSecond) + 1);
  NSMutableArray *transforms = [NSMutableArray arrayWithCapacity:frameCount];
  NSMutableArray *opacities = [NSMutableArray arrayWithCapacity:frameCount];
  NSMutableArray *contents = [NSMutableArray arrayWithCapacity:frameCount];
  NSMutableArray *keyTimes = [NSMutableArray arrayWithCapacity:frameCount];
  Look last;
  for (size_t i = 0; i < frameCount; i++) {
    CGFloat fraction = (CGFloat)i / (frameCount - 1);
    last = [self lookAt:_animationStart + fraction * total];
    [transforms addObject:[NSValue valueWithCATransform3D:transformFor(last)]];
    [opacities addObject:@(last.opacity)];
    NSUInteger level = (NSUInteger)std::lround(last.blur * (_blurLevels.count - 1));
    [contents addObject:_blurLevels[MIN(level, _blurLevels.count - 1)]];
    [keyTimes addObject:@(fraction)];
  }

  // Model values are the end state; the animations play from the start state.
  _layer.transform = transformFor(last);
  _layer.opacity = (float)last.opacity;
  _layer.contents = contents.lastObject;

  CFTimeInterval beginTime = [_layer convertTime:_animationStart fromLayer:nil];
  NSDictionary<NSString *, NSArray *> *tracks = @{@"transform" : transforms, @"opacity" : opacities, @"contents" : contents};
  for (NSString *keyPath in tracks) {
    CAKeyframeAnimation *animation = [CAKeyframeAnimation animationWithKeyPath:keyPath];
    animation.values = tracks[keyPath];
    animation.keyTimes = keyTimes;
    animation.duration = total;
    animation.beginTime = beginTime;
    animation.calculationMode = kCAAnimationLinear;
    animation.fillMode = kCAFillModeBackwards;
    [_layer addAnimation:animation forKey:keyPath];
  }
}

@end

#pragma mark - Animator

@implementation JBTextTransitionAnimator {
  __weak CALayer *_parent;
  void (^_onFinish)(void);
  CALayer *_container;
  /// Segments of the latest text, settling into place.
  NSMutableArray<JBTextSegment *> *_current;
  /// Segments of earlier text, on their way out.
  NSMutableArray<JBTextSegment *> *_leaving;
  NSUInteger _generation;
  /// When the latest transition starts (its change + its delay). Changes that arrive before
  /// then join it: they keep this start instead of delaying it again.
  CFTimeInterval _transitionStart;
}

+ (CGFloat)progressAtTime:(CGFloat)time duration:(CGFloat)duration
{
  return springProgress(time, duration);
}

+ (CGFloat)totalTimeForDuration:(CGFloat)duration
{
  return totalTimeFor(duration);
}

+ (CGFloat)settleTimeForMode:(JBTextTransitionMode)mode duration:(CGFloat)duration
{
  return motionForMode(mode).minIntervalFactor * (duration > 0 ? duration : kDefaultDuration);
}

- (instancetype)initWithParentLayer:(CALayer *)parent onFinish:(void (^)(void))onFinish
{
  if (self = [super init]) {
    _parent = parent;
    _onFinish = [onFinish copy];
    _current = [NSMutableArray array];
    _leaving = [NSMutableArray array];
  }
  return self;
}

- (BOOL)isRunning
{
  return _container != nil;
}

- (CFTimeInterval)transitionStart
{
  return _transitionStart;
}

- (BOOL)isWaitingToStart
{
  return _container != nil && CACurrentMediaTime() < _transitionStart;
}

- (JBTextSegment *)segmentFor:(JBTextSnapshot *)snapshot range:(NSRange)range motion:(Motion)motion scale:(CGFloat)scale
{
  JBTextSegment *segment = [JBTextSegment new];
  segment.restRect = textRectFor(snapshot, range);
  segment.range = range;
  segment.appearance = appearanceKey(snapshot, range);
  CGFloat lineHeight = segment.restRect.size.height;
  CGFloat maxBlur = motion.blurScale * MIN(10, MAX(2, lineHeight * 0.15));
  // Room for ink outside the line box (diacritics, italics) and for the blur.
  CGFloat padding = MAX(3 * maxBlur, 0.35 * lineHeight);
  CGRect canvas = pixelAligned(CGRectInset(segment.restRect, -padding, -padding), scale);

  // Same glyphs at the same sub-pixel offset in the same canvas draw identically.
  NSString *sharpKey = [NSString stringWithFormat:@"%@|%.0fx%.0f|%.3f,%.3f|%.1f",
                                                  segment.appearance,
                                                  canvas.size.width * scale,
                                                  canvas.size.height * scale,
                                                  segment.restRect.origin.x - canvas.origin.x,
                                                  segment.restRect.origin.y - canvas.origin.y,
                                                  scale];
  id sharp = [sharpCache() objectForKey:sharpKey];
  if (!sharp) {
    CGImageRef image = renderImage(canvas, scale, ^{
                         drawGlyphs(snapshot, range);
                       }).CGImage;
    sharp = (__bridge id)image;
    [sharpCache() setObject:sharp forKey:sharpKey cost:imageBytes(image)];
  }
  NSMutableArray *levels = [NSMutableArray arrayWithObject:sharp];
  if (motion.hasBlur()) {
    NSString *blurKey = [NSString stringWithFormat:@"%@|%.2f", sharpKey, maxBlur];
    NSArray *blurred = [blurCache() objectForKey:blurKey];
    if (!blurred) {
      blurred = createBlurLevels((__bridge CGImageRef)sharp, maxBlur * scale, kBlurLevels);
      NSUInteger bytes = 0;
      for (id image in blurred) bytes += imageBytes((__bridge CGImageRef)image);
      [blurCache() setObject:blurred forKey:blurKey cost:bytes];
    }
    [levels addObjectsFromArray:blurred];
  }
  segment.blurLevels = levels;
  segment.hasBlur = motion.hasBlur();

  CALayer *layer = [CALayer layer];
  layer.frame = canvas;
  layer.contentsScale = scale;
  layer.contents = levels.firstObject;
  segment.layer = layer;
  [_container addSublayer:layer];
  return segment;
}

- (void)leave:(JBTextSegment *)segment
           at:(CFTimeInterval)now
    direction:(CGFloat)direction
       motion:(Motion)motion
     duration:(CGFloat)duration
        delay:(CGFloat)delay
{
  Look from = [segment lookAt:now];
  // From here on it only rises/fades where it is (a waiting leave doesn't follow its slot).
  segment.hasFixedX = YES;
  segment.fixedTranslateX = from.translateX;
  segment.drift = -direction * segment.restRect.size.height * motion.offsetFactor;
  segment.minScale = motion.minScale;
  segment.hasBlur = segment.hasBlur && motion.hasBlur();
  segment.isHurrying = NO;
  if (delay > 0) {
    // Staggered: it keeps doing what it does until its turn.
    [segment leaveAt:now + delay duration:duration now:now];
  } else {
    [segment startRole:JBSegmentRoleLeave from:from at:now duration:duration];
  }
  [_leaving addObject:segment];
}

- (void)transitionFrom:(JBTextSnapshot *)from
                    to:(JBTextSnapshot *)to
                  plan:(JBTextTransitionPlan *)plan
                  mode:(JBTextTransitionMode)mode
              duration:(CGFloat)duration
               stagger:(CGFloat)stagger
                 delay:(CGFloat)delay
                bounds:(CGRect)bounds
            viewOffset:(CGPoint)viewOffset
                 scale:(CGFloat)scale
{
  Motion motion = motionForMode(mode);
  CGFloat direction = plan.direction;
  CFTimeInterval now = CACurrentMediaTime();
  CGFloat staggerStep = staggerStepFor(stagger, plan.maxStaggerIndex);
  _lastStaggerSpread = staggerStep * plan.maxStaggerIndex;
  CGFloat hurryDuration = MIN(kHurryDuration, duration > 0 ? duration : kDefaultDuration);
  // Every position's schedule (leaving, kept, arriving) counts from `base`: `delay` after the
  // change, or the start of the running transition if it is still waiting for it.
  CFTimeInterval base = (_container && now < _transitionStart) ? _transitionStart : now + MAX(0, delay);
  _transitionStart = base;
  CGFloat baseDelay = base - now;

  [CATransaction begin];
  [CATransaction setDisableActions:YES];

  if (!_container) {
    _container = [CALayer layer];
    _container.masksToBounds = NO;
    [_parent addSublayer:_container];
  } else {
    // Keep what is on screen in place while the view moves under it.
    for (JBTextSegment *segment in [_current arrayByAddingObjectsFromArray:_leaving]) {
      CGPoint position = segment.layer.position;
      segment.layer.position = CGPointMake(position.x + viewOffset.x, position.y + viewOffset.y);
      segment.restRect = CGRectOffset(segment.restRect, viewOffset.x, viewOffset.y);
    }
  }
  _container.frame = bounds;

  // Earlier text that is still leaving hurries out, once: restarting the hurry on every
  // change would keep it on screen forever while changes arrive every frame.
  for (JBTextSegment *segment in [_leaving copy]) {
    Look current = [segment lookAt:now];
    if (segment.endTime <= now || current.opacity <= kInvisibleOpacity) {
      [segment.layer removeFromSuperlayer];
      [_leaving removeObject:segment];
      continue;
    }
    // A staggered segment whose turn hasn't come is still what its slot shows: it keeps
    // its schedule, so it leaves when its replacement arrives.
    if (!segment.isHurrying && [segment isLeavingAt:now]) {
      segment.isHurrying = YES;
      segment.drift = segment.drift * kHurryDriftFactor;
      [segment startRole:JBSegmentRoleLeave from:current at:now duration:hurryDuration];
    }
  }

  // Positions that get a new segment, and what they replace (see Removed/Inserted below).
  NSMutableSet<NSNumber *> *insertedIndices = [NSMutableSet set];
  for (JBTextTransitionOp *op in plan.ops) {
    if (op.change == JBTextSegmentChangeInserted) [insertedIndices addObject:@(op.staggerIndex)];
  }
  NSMutableDictionary<NSNumber *, JBTextSegment *> *takenOver = [NSMutableDictionary dictionary];
  NSMutableDictionary<NSNumber *, JBTextSegment *> *leftByIndex = [NSMutableDictionary dictionary];

  NSMutableDictionary<NSValue *, JBTextSegment *> *byRange = [NSMutableDictionary dictionary];
  for (JBTextSegment *segment in _current) {
    byRange[[NSValue valueWithRange:segment.range]] = segment;
  }

  NSMutableArray<JBTextSegment *> *settling = [NSMutableArray array];
  for (JBTextTransitionOp *op in plan.ops) {
    switch (op.change) {
      case JBTextSegmentChangeKept: {
        NSValue *key = [NSValue valueWithRange:op.oldRange];
        JBTextSegment *existing = byRange[key];
        CGRect newRect = textRectFor(to, op.newRange);
        if (existing) {
          [byRange removeObjectForKey:key];
          CGFloat deltaX = existing.restRect.origin.x - newRect.origin.x;
          CGFloat deltaY = existing.restRect.origin.y - newRect.origin.y;
          BOOL isPixelShift = std::fabs(deltaX * scale - std::round(deltaX * scale)) < 0.01 &&
              std::fabs(deltaY * scale - std::round(deltaY * scale)) < 0.01;
          if ([existing.appearance isEqualToString:appearanceKey(to, op.newRange)] && isPixelShift) {
            // Same glyphs: keep the layer and its images, move it to the new rest position.
            Look current = [existing lookAt:now];
            existing.range = op.newRange;
            // Kept segments that are moving re-aim at once; one at rest moves when the
            // transition starts; one still waiting for its start keeps it.
            CFTimeInterval keptStart =
                existing.startTime > now ? existing.startTime : (isRestLook(current) ? base : now);
            if (std::fabs(deltaX) > 0.001 || std::fabs(deltaY) > 0.001) {
              CGPoint position = existing.layer.position;
              existing.layer.position = CGPointMake(position.x - deltaX, position.y - deltaY);
              existing.restRect = newRect;
              current.translateX += deltaX;
              current.translateY += deltaY;
              [existing startRole:JBSegmentRoleSettle from:current at:keptStart duration:duration];
            } else if (existing.role != JBSegmentRoleSettle) {
              [existing startRole:JBSegmentRoleSettle from:current at:now duration:duration];
            }
            [settling addObject:existing];
            break;
          }
        }
        // Different glyphs or style (e.g. colour), or a sub-pixel move: redraw from the new
        // text, starting from wherever the old piece is now.
        Look current;
        CGRect oldRect = textRectFor(from, op.oldRange);
        CFTimeInterval startTime = base;
        if (existing) {
          current = [existing lookAt:now];
          oldRect = existing.restRect;
          startTime = existing.startTime > now ? existing.startTime : (isRestLook(current) ? base : now);
          [existing.layer removeFromSuperlayer];
        }
        JBTextSegment *segment = [self segmentFor:to range:op.newRange motion:motion scale:scale];
        segment.partner = existing.partner;
        current.translateX += oldRect.origin.x - segment.restRect.origin.x;
        current.translateY += oldRect.origin.y - segment.restRect.origin.y;
        [segment startRole:JBSegmentRoleSettle from:current at:startTime duration:duration];
        [settling addObject:segment];
        break;
      }
      case JBTextSegmentChangeInserted: {
        JBTextSegment *segment = [self segmentFor:to range:op.newRange motion:motion scale:scale];
        Look start;
        start.translateY = direction * segment.restRect.size.height * motion.offsetFactor;
        start.scale = motion.minScale;
        start.opacity = 0;
        start.blur = motion.hasBlur() ? 1 : 0;
        CFTimeInterval startTime = base + op.staggerIndex * staggerStep;
        CGFloat segmentDuration = duration;
        JBTextSegment *young = takenOver[@(op.staggerIndex)];
        if (young) {
          // The slot's previous arrival never got to show. Don't push the slot back: carry
          // on with its rise if it has started, otherwise keep its (earlier) start.
          if (young.startTime <= now) {
            start = young.from;
            startTime = young.startTime;
            segmentDuration = young.duration;
          } else {
            startTime = MIN(startTime, young.startTime);
          }
          JBTextSegment *partner = young.partner;
          segment.partner = partner;
          if (partner.hasPendingLeave && partner.leaveStart > MAX(startTime, now)) {
            [partner leaveAt:MAX(startTime, now) duration:partner.leaveDuration now:now];
          }
        } else {
          segment.partner = leftByIndex[@(op.staggerIndex)];
        }
        // The segment it replaces stays where it is (even if the slot moved); this one
        // arrives straight at its final place.
        [segment startRole:JBSegmentRoleSettle from:start at:startTime duration:segmentDuration];
        [settling addObject:segment];
        break;
      }
      case JBTextSegmentChangeRemoved: {
        NSValue *key = [NSValue valueWithRange:op.oldRange];
        JBTextSegment *segment = byRange[key];
        if (segment) {
          [byRange removeObjectForKey:key];
          BOOL isWaiting = [segment isWaitingAt:now];
          BOOL isYoung = isWaiting || (segment.role == JBSegmentRoleSettle && segment.from.opacity < 0.001 &&
                                       [segment lookAt:now].opacity < motion.takeoverOpacity);
          if (isYoung && [insertedIndices containsObject:@(op.staggerIndex)]) {
            // Replaced before it showed: its replacement takes over its timeline.
            takenOver[@(op.staggerIndex)] = segment;
            [segment.layer removeFromSuperlayer];
            break;
          }
          if (isWaiting) {
            [segment.layer removeFromSuperlayer];
            break;
          }
        } else if (_current.count > 0) {
          // Not a segment of the text on screen (e.g. a whole-text fallback range); the
          // current segments it covers leave below.
          break;
        } else {
          segment = [self segmentFor:from range:op.oldRange motion:motion scale:scale];
        }
        [self leave:segment
                 at:now
          direction:direction
             motion:motion
           duration:duration
              delay:baseDelay + op.staggerIndex * staggerStep];
        leftByIndex[@(op.staggerIndex)] = segment;
        break;
      }
    }
  }
  // Text on screen that the plan no longer refers to leaves too.
  for (JBTextSegment *segment in byRange.allValues) {
    if ([segment isWaitingAt:now]) {
      [segment.layer removeFromSuperlayer];
      continue;
    }
    [self leave:segment at:now direction:direction motion:motion duration:duration delay:baseDelay];
  }
  _current = settling;

  // Leaving segments stay at their own x, so when the width changes an arrival can land on an
  // old glyph that is still waiting for its turn. That glyph leaves no later than the arrival.
  for (JBTextSegment *arriving in _current) {
    if (arriving.role != JBSegmentRoleSettle || arriving.from.opacity > kInvisibleOpacity) continue;
    CFTimeInterval arrival = MAX(now, arriving.startTime);
    for (JBTextSegment *leaving in _leaving) {
      if (!leaving.hasPendingLeave || leaving.leaveStart <= arrival) continue;
      CGRect onScreen = CGRectOffset(leaving.restRect, leaving.fixedTranslateX, 0);
      if (overlapsOnScreen(onScreen, arriving.restRect)) {
        [leaving leaveAt:arrival duration:leaving.leaveDuration now:now];
      }
    }
  }

  // Bound the leaving pile.
  NSUInteger maxLeaving = MAX(kMinMaxLeaving, kMaxLeavingPerSegment * _current.count);
  if (_leaving.count > maxLeaving) {
    // Drop the most faded first, so what disappears is already (nearly) invisible.
    [_leaving sortUsingComparator:^NSComparisonResult(JBTextSegment *a, JBTextSegment *b) {
      CGFloat opacityA = [a lookAt:now].opacity;
      CGFloat opacityB = [b lookAt:now].opacity;
      return opacityA < opacityB ? NSOrderedAscending : opacityA > opacityB ? NSOrderedDescending : NSOrderedSame;
    }];
    while (_leaving.count > maxLeaving) {
      [_leaving.firstObject.layer removeFromSuperlayer];
      [_leaving removeObjectAtIndex:0];
    }
  }

  // Only segments whose timeline changed get new animations.
  CFTimeInterval endTime = now;
  for (JBTextSegment *segment in [_current arrayByAddingObjectsFromArray:_leaving]) {
    if (segment.needsAnimation) [segment applyAnimation];
    endTime = MAX(endTime, segment.endTime);
  }

  [CATransaction commit];
  [self scheduleFinishAt:endTime];
}

- (void)shiftBy:(CGPoint)offset bounds:(CGRect)bounds
{
  if (!_container) return;
  [CATransaction begin];
  [CATransaction setDisableActions:YES];
  if (std::fabs(offset.x) > 0.001 || std::fabs(offset.y) > 0.001) {
    for (JBTextSegment *segment in [_current arrayByAddingObjectsFromArray:_leaving]) {
      CGPoint position = segment.layer.position;
      segment.layer.position = CGPointMake(position.x + offset.x, position.y + offset.y);
      segment.restRect = CGRectOffset(segment.restRect, offset.x, offset.y);
    }
  }
  _container.frame = bounds;
  [CATransaction commit];
}

- (void)scheduleFinishAt:(CFTimeInterval)endTime
{
  NSUInteger generation = ++_generation;
  CFTimeInterval delay = MAX(0, endTime - CACurrentMediaTime()) + 0.02;
  __weak __typeof(self) weakSelf = self;
  dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(delay * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
    __typeof(self) strongSelf = weakSelf;
    if (strongSelf && strongSelf->_generation == generation && strongSelf->_container) {
      [strongSelf removeAll];
      if (strongSelf->_onFinish) strongSelf->_onFinish();
    }
  });
}

- (void)removeAll
{
  [_container removeFromSuperlayer];
  _container = nil;
  [_current removeAllObjects];
  [_leaving removeAllObjects];
}

- (void)cancel
{
  _generation++;
  [self removeAll];
}

@end
