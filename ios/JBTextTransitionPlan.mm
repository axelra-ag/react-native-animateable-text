/*
 * Custom
 */

#import "JBTextTransitionPlan.h"

#include <vector>

static const NSRange JBNoRange = {NSNotFound, 0};

@interface JBTextTransitionOp ()
@property (nonatomic, readwrite) NSInteger staggerIndex;
- (instancetype)initWithChange:(JBTextSegmentChange)change oldRange:(NSRange)oldRange newRange:(NSRange)newRange;
@end

@implementation JBTextTransitionOp

- (instancetype)initWithChange:(JBTextSegmentChange)change oldRange:(NSRange)oldRange newRange:(NSRange)newRange
{
  if (self = [super init]) {
    _change = change;
    _oldRange = oldRange;
    _newRange = newRange;
  }
  return self;
}

@end

@interface JBTextTransitionPlan ()
- (instancetype)initWithOps:(NSArray<JBTextTransitionOp *> *)ops direction:(NSInteger)direction;
@end

namespace {

struct Segment {
  NSRange range;
  NSString *text;
};

// Letters of scripts whose glyphs join their neighbours. Splitting these per
// character would break the shaping, so they are grouped into words.
bool isJoiningLetter(unichar c)
{
  return (c >= 0x0620 && c <= 0x064A) || (c >= 0x066E && c <= 0x06D3) || c == 0x06D5 ||
      (c >= 0x06EE && c <= 0x06EF) || (c >= 0x06FA && c <= 0x06FF) || (c >= 0x0750 && c <= 0x077F) ||
      (c >= 0x08A0 && c <= 0x08FF) || (c >= 0xFB50 && c <= 0xFDFF) || (c >= 0xFE70 && c <= 0xFEFF);
}

bool isWhitespace(unichar c)
{
  return [[NSCharacterSet whitespaceAndNewlineCharacterSet] characterIsMember:c];
}

std::vector<Segment> segmentText(NSString *text, JBTextTransitionUnit unit)
{
  __block std::vector<Segment> segments;
  if (unit == JBTextTransitionUnitWhole) {
    if (text.length > 0) segments.push_back({NSMakeRange(0, text.length), text});
    return segments;
  }
  __block bool previousJoins = false;
  [text enumerateSubstringsInRange:NSMakeRange(0, text.length)
                           options:NSStringEnumerationByComposedCharacterSequences
                        usingBlock:^(NSString *grapheme, NSRange range, NSRange, BOOL *) {
                          unichar first = [grapheme characterAtIndex:0];
                          // Auto: joining letters group. Word: everything but whitespace groups.
                          bool joins = unit == JBTextTransitionUnitWord ? !isWhitespace(first) : isJoiningLetter(first);
                          if (joins && previousJoins && !segments.empty()) {
                            Segment &last = segments.back();
                            last.range.length = NSMaxRange(range) - last.range.location;
                            last.text = [text substringWithRange:last.range];
                          } else {
                            segments.push_back({range, grapheme});
                          }
                          previousJoins = joins;
                        }];
  return segments;
}

int digitValue(unichar c)
{
  if (c >= '0' && c <= '9') return c - '0';
  if (c >= 0x0660 && c <= 0x0669) return c - 0x0660;
  if (c >= 0x06F0 && c <= 0x06F9) return c - 0x06F0;
  return -1;
}

// Reads the number in a display string: digits in any of the supported sets,
// "." or "٫" as decimal point, "-" or "−" before the first digit as sign.
// Separators and labels ("SAR", "ر.س", ",") are ignored.
bool parseNumber(NSString *text, double *outValue)
{
  bool hasDigits = false;
  bool isNegative = false;
  bool inFraction = false;
  double value = 0;
  double scale = 0.1;
  for (NSUInteger i = 0; i < text.length; i++) {
    unichar c = [text characterAtIndex:i];
    int digit = digitValue(c);
    if (digit >= 0) {
      hasDigits = true;
      if (inFraction) {
        value += digit * scale;
        scale /= 10;
      } else {
        value = value * 10 + digit;
      }
    } else if (!hasDigits && (c == '-' || c == 0x2212)) {
      isNegative = true;
    } else if (hasDigits && !inFraction && (c == '.' || c == 0x066B)) {
      inFraction = true;
    }
  }
  *outValue = isNegative ? -value : value;
  return hasDigits;
}

NSInteger resolveDirection(NSString *oldText, NSString *newText, NSString *direction)
{
  if ([direction isEqualToString:@"up"]) return 1;
  if ([direction isEqualToString:@"down"]) return -1;
  double oldValue = 0;
  double newValue = 0;
  if (!parseNumber(oldText, &oldValue) || !parseNumber(newText, &newValue)) return 1;
  return newValue >= oldValue ? 1 : -1;
}

void add(NSMutableArray *ops, JBTextSegmentChange change, NSRange oldRange, NSRange newRange)
{
  [ops addObject:[[JBTextTransitionOp alloc] initWithChange:change oldRange:oldRange newRange:newRange]];
}

bool containsDigit(NSString *text)
{
  for (NSUInteger i = 0; i < text.length; i++) {
    if (digitValue([text characterAtIndex:i]) >= 0) return true;
  }
  return false;
}

// Shared non-digit prefix and suffix (labels like "SAR ", "%", " ر.س") stay put;
// the rest is aligned from the trailing end so units stay over units.
// The slots are the same for every scope. Changed: a slot whose segment did not change is
// kept. Numbers: the shared prefix and suffix are kept, every slot in between is replaced
// (Removed + Inserted) even when its segment is the same. All: every slot is replaced.
NSArray<JBTextTransitionOp *> *numericOps(const std::vector<Segment> &oldSegments,
                                          const std::vector<Segment> &newSegments,
                                          JBTextTransitionScope scope)
{
  bool keepsLabels = scope != JBTextTransitionScopeAll;
  bool keepsUnchanged = scope == JBTextTransitionScopeChanged;
  size_t n = oldSegments.size();
  size_t m = newSegments.size();
  size_t prefix = 0;
  while (prefix < n && prefix < m && [oldSegments[prefix].text isEqualToString:newSegments[prefix].text] &&
         !containsDigit(oldSegments[prefix].text)) {
    prefix++;
  }
  size_t suffix = 0;
  while (suffix < n - prefix && suffix < m - prefix &&
         [oldSegments[n - 1 - suffix].text isEqualToString:newSegments[m - 1 - suffix].text] &&
         !containsDigit(oldSegments[n - 1 - suffix].text)) {
    suffix++;
  }

  NSMutableArray *ops = [NSMutableArray array];
  auto addUnchanged = ^(NSRange oldRange, NSRange newRange) {
    if (keepsLabels) {
      add(ops, JBTextSegmentChangeKept, oldRange, newRange);
    } else {
      add(ops, JBTextSegmentChangeRemoved, oldRange, JBNoRange);
      add(ops, JBTextSegmentChangeInserted, JBNoRange, newRange);
    }
  };
  for (size_t k = 0; k < prefix; k++) {
    addUnchanged(oldSegments[k].range, newSegments[k].range);
  }

  NSMutableArray *reversedMiddle = [NSMutableArray array];
  size_t oldMiddle = n - prefix - suffix;
  size_t newMiddle = m - prefix - suffix;
  for (size_t k = 0; k < MAX(oldMiddle, newMiddle); k++) {
    const Segment *o = k < oldMiddle ? &oldSegments[n - suffix - 1 - k] : nullptr;
    const Segment *w = k < newMiddle ? &newSegments[m - suffix - 1 - k] : nullptr;
    if (keepsUnchanged && o && w && [o->text isEqualToString:w->text]) {
      add(reversedMiddle, JBTextSegmentChangeKept, o->range, w->range);
      continue;
    }
    // Reversed below, so Inserted goes first to end up after Removed.
    if (w) add(reversedMiddle, JBTextSegmentChangeInserted, JBNoRange, w->range);
    if (o) add(reversedMiddle, JBTextSegmentChangeRemoved, o->range, JBNoRange);
  }
  [ops addObjectsFromArray:[[reversedMiddle reverseObjectEnumerator] allObjects]];

  for (size_t k = suffix; k-- > 0;) {
    addUnchanged(oldSegments[n - 1 - k].range, newSegments[m - 1 - k].range);
  }
  return ops;
}

// Longest common subsequence; ties prefer removing from the old text first.
NSArray<JBTextTransitionOp *> *lcsOps(const std::vector<Segment> &oldSegments, const std::vector<Segment> &newSegments)
{
  size_t n = oldSegments.size();
  size_t m = newSegments.size();
  std::vector<std::vector<int>> lcs(n + 1, std::vector<int>(m + 1, 0));
  for (size_t i = n; i-- > 0;) {
    for (size_t j = m; j-- > 0;) {
      lcs[i][j] = [oldSegments[i].text isEqualToString:newSegments[j].text] ? lcs[i + 1][j + 1] + 1
                                                                             : MAX(lcs[i + 1][j], lcs[i][j + 1]);
    }
  }
  NSMutableArray *ops = [NSMutableArray array];
  size_t i = 0;
  size_t j = 0;
  while (i < n || j < m) {
    if (i < n && j < m && [oldSegments[i].text isEqualToString:newSegments[j].text]) {
      add(ops, JBTextSegmentChangeKept, oldSegments[i].range, newSegments[j].range);
      i++;
      j++;
    } else if (i < n && (j == m || lcs[i + 1][j] >= lcs[i][j + 1])) {
      add(ops, JBTextSegmentChangeRemoved, oldSegments[i].range, JBNoRange);
      i++;
    } else {
      add(ops, JBTextSegmentChangeInserted, JBNoRange, newSegments[j].range);
      j++;
    }
  }
  return ops;
}

const size_t kMaxLcsSegments = 400;

// Positions in logical order: a Kept op is one position; a run of Removed ops followed by
// Inserted ops shares positions, aligned at the run's trailing end when more were removed
// (numeric slots) and at its start otherwise. Indices count from the first changed position.
NSInteger assignStaggerIndices(NSArray<JBTextTransitionOp *> *ops)
{
  NSInteger position = 0;
  NSInteger firstChanged = -1;
  NSUInteger i = 0;
  while (i < ops.count) {
    if (ops[i].change == JBTextSegmentChangeKept) {
      position++;
      i++;
      continue;
    }
    NSUInteger runStart = i;
    while (i < ops.count && ops[i].change == JBTextSegmentChangeRemoved) i++;
    NSInteger removed = i - runStart;
    NSUInteger insertedStart = i;
    while (i < ops.count && ops[i].change == JBTextSegmentChangeInserted) i++;
    NSInteger inserted = i - insertedStart;
    if (firstChanged < 0) firstChanged = position;
    for (NSInteger k = 0; k < removed; k++) ops[runStart + k].staggerIndex = position + k - firstChanged;
    NSInteger insertedOffset = MAX((NSInteger)0, removed - inserted);
    for (NSInteger k = 0; k < inserted; k++) {
      ops[insertedStart + k].staggerIndex = position + insertedOffset + k - firstChanged;
    }
    position += MAX(removed, inserted);
  }
  NSInteger maxIndex = 0;
  for (JBTextTransitionOp *op in ops) maxIndex = MAX(maxIndex, op.staggerIndex);
  return maxIndex;
}

} // namespace

@implementation JBTextTransitionPlan

- (instancetype)initWithOps:(NSArray<JBTextTransitionOp *> *)ops direction:(NSInteger)direction
{
  if (self = [super init]) {
    _ops = ops;
    _direction = direction;
    _maxStaggerIndex = assignStaggerIndices(ops);
  }
  return self;
}

+ (JBTextTransitionMode)modeFromString:(NSString *)value
{
  if ([value isEqualToString:@"numeric"]) return JBTextTransitionModeNumeric;
  if ([value isEqualToString:@"blur"]) return JBTextTransitionModeBlur;
  if ([value isEqualToString:@"fade"]) return JBTextTransitionModeFade;
  if ([value isEqualToString:@"roll"]) return JBTextTransitionModeRoll;
  return JBTextTransitionModeNone;
}

+ (BOOL)isNumericMode:(JBTextTransitionMode)mode
{
  return mode == JBTextTransitionModeNumeric || mode == JBTextTransitionModeRoll;
}

+ (JBTextTransitionUnit)unitFromString:(NSString *)value
{
  if ([value isEqualToString:@"word"]) return JBTextTransitionUnitWord;
  if ([value isEqualToString:@"whole"]) return JBTextTransitionUnitWhole;
  return JBTextTransitionUnitAuto;
}

+ (JBTextTransitionScope)scopeFromString:(NSString *)value
{
  if ([value isEqualToString:@"numbers"]) return JBTextTransitionScopeNumbers;
  if ([value isEqualToString:@"changed"]) return JBTextTransitionScopeChanged;
  return JBTextTransitionScopeAll;
}

+ (instancetype)planFromText:(NSString *)oldText
                      toText:(NSString *)newText
                        mode:(JBTextTransitionMode)mode
                   direction:(NSString *)direction
                        unit:(JBTextTransitionUnit)unit
                       scope:(JBTextTransitionScope)scope
{
  std::vector<Segment> oldSegments = segmentText(oldText, unit);
  std::vector<Segment> newSegments = segmentText(newText, unit);
  NSInteger resolvedDirection = resolveDirection(oldText, newText, direction);
  if (unit == JBTextTransitionUnitWhole) {
    JBTextTransitionPlan *plan = [self wholeTextPlanFromText:oldText toText:newText];
    return [[self alloc] initWithOps:plan.ops direction:resolvedDirection];
  }
  if ([self isNumericMode:mode]) {
    return [[self alloc] initWithOps:numericOps(oldSegments, newSegments,
                                             mode == JBTextTransitionModeRoll ? scope : JBTextTransitionScopeChanged)
                           direction:resolvedDirection];
  }
  if (oldSegments.size() > kMaxLcsSegments || newSegments.size() > kMaxLcsSegments) {
    return [self wholeTextPlanFromText:oldText toText:newText];
  }
  return [[self alloc] initWithOps:lcsOps(oldSegments, newSegments) direction:resolvedDirection];
}

+ (instancetype)revealPlanForText:(NSString *)text
                             mode:(JBTextTransitionMode)mode
                        direction:(NSString *)direction
                             unit:(JBTextTransitionUnit)unit
{
  // From empty text every segment is inserted: numeric slots, LCS and whole text agree.
  return [self planFromText:@""
                     toText:text
                       mode:mode
                  direction:[direction isEqualToString:@"down"] ? @"down" : @"up"
                       unit:unit
                      scope:JBTextTransitionScopeAll];
}

+ (instancetype)wholeTextPlanFromText:(NSString *)oldText toText:(NSString *)newText
{
  NSMutableArray *ops = [NSMutableArray array];
  if (oldText.length > 0) add(ops, JBTextSegmentChangeRemoved, NSMakeRange(0, oldText.length), JBNoRange);
  if (newText.length > 0) add(ops, JBTextSegmentChangeInserted, JBNoRange, NSMakeRange(0, newText.length));
  return [[self alloc] initWithOps:ops direction:1];
}

- (NSString *)fixtureDescriptionWithOldText:(NSString *)oldText newText:(NSString *)newText
{
  NSMutableArray<NSString *> *tokens = [NSMutableArray array];
  for (JBTextTransitionOp *op in _ops) {
    NSString *segment = op.change == JBTextSegmentChangeInserted ? [newText substringWithRange:op.newRange]
                                                                  : [oldText substringWithRange:op.oldRange];
    NSString *prefix = op.change == JBTextSegmentChangeKept ? @"=" : op.change == JBTextSegmentChangeRemoved ? @"-" : @"+";
    [tokens addObject:[prefix stringByAppendingString:[segment stringByReplacingOccurrencesOfString:@" "
                                                                                          withString:@"␠"]]];
  }
  return [tokens componentsJoinedByString:@" "];
}

- (NSString *)staggerFixtureDescription
{
  NSMutableArray<NSString *> *tokens = [NSMutableArray array];
  for (JBTextTransitionOp *op in _ops) {
    if (op.change != JBTextSegmentChangeKept) [tokens addObject:[NSString stringWithFormat:@"%ld", (long)op.staggerIndex]];
  }
  return tokens.count > 0 ? [tokens componentsJoinedByString:@" "] : @"∅";
}

@end
