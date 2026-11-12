/*
 * Custom
 *
 * Pure Foundation (no React Native) so it can be unit-tested on macOS:
 * scripts/test-transition-plan.sh. Android mirrors this in TextTransitionPlan.kt;
 * both must pass fixtures/text-transitions.tsv.
 */

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

typedef NS_ENUM(NSInteger, JBTextTransitionMode) {
  JBTextTransitionModeNone = 0,
  JBTextTransitionModeNumeric,
  JBTextTransitionModeBlur,
  JBTextTransitionModeFade,
  /// Numeric's segmentation and slots with its own look (short rise, no shrink, staggered).
  /// Which segments are replaced on a change is its scope (default: all of them).
  JBTextTransitionModeRoll,
};

/// What animates as one piece.
typedef NS_ENUM(NSInteger, JBTextTransitionUnit) {
  /// Graphemes; joining scripts (Arabic) grouped into words.
  JBTextTransitionUnitAuto = 0,
  /// Whitespace-separated words, every script.
  JBTextTransitionUnitWord,
  /// The entire text.
  JBTextTransitionUnitWhole,
};

/// Roll only: which segments are replaced on a change.
typedef NS_ENUM(NSInteger, JBTextTransitionScope) {
  /// Every segment, unchanged ones too.
  JBTextTransitionScopeAll = 0,
  /// The number: everything but a shared non-digit prefix and suffix (labels like "SAR ").
  JBTextTransitionScopeNumbers,
  /// Only segments that changed (what numeric does).
  JBTextTransitionScopeChanged,
};

typedef NS_ENUM(NSInteger, JBTextSegmentChange) {
  JBTextSegmentChangeKept = 0,
  JBTextSegmentChangeRemoved,
  JBTextSegmentChangeInserted,
};

@interface JBTextTransitionOp : NSObject
@property (nonatomic, readonly) JBTextSegmentChange change;
/// Range in the old text (Kept, Removed); {NSNotFound, 0} otherwise.
@property (nonatomic, readonly) NSRange oldRange;
/// Range in the new text (Kept, Inserted); {NSNotFound, 0} otherwise.
@property (nonatomic, readonly) NSRange newRange;
/// Position of a Removed/Inserted op in logical order, counted from the first changed
/// position (unchanged positions in between count too). A Removed and the Inserted that
/// replaces it share a position. 0 for Kept. Its start is delayed by index × stagger.
@property (nonatomic, readonly) NSInteger staggerIndex;
@end

@interface JBTextTransitionPlan : NSObject
@property (nonatomic, readonly) NSArray<JBTextTransitionOp *> *ops;
/// 1: content moves up (counting up), -1: moves down.
@property (nonatomic, readonly) NSInteger direction;

/// Largest staggerIndex in `ops`.
@property (nonatomic, readonly) NSInteger maxStaggerIndex;

+ (JBTextTransitionMode)modeFromString:(NSString *)value;
/// Roll shares numeric's segmentation, slots and fallbacks.
+ (BOOL)isNumericMode:(JBTextTransitionMode)mode;
+ (JBTextTransitionUnit)unitFromString:(NSString *)value;
+ (JBTextTransitionScope)scopeFromString:(NSString *)value;

+ (instancetype)planFromText:(NSString *)oldText
                      toText:(NSString *)newText
                        mode:(JBTextTransitionMode)mode
                   direction:(NSString *)direction
                        unit:(JBTextTransitionUnit)unit
                       scope:(JBTextTransitionScope)scope;

/// Reveal: every segment of `text` arrives (there is no old text), in logical order, so its
/// stagger index counts from the first segment. Same segmentation as `planFromText:` with
/// `mode` and `unit`. `direction` is "up" or "down" (anything else: up).
+ (instancetype)revealPlanForText:(NSString *)text
                             mode:(JBTextTransitionMode)mode
                        direction:(NSString *)direction
                             unit:(JBTextTransitionUnit)unit;

/// Whole-text cross-fade: one Removed op for all of the old text, one Inserted for the new.
+ (instancetype)wholeTextPlanFromText:(NSString *)oldText toText:(NSString *)newText;

/// Fixture format: "=kept -removed +inserted", spaces inside segments as "␠".
- (NSString *)fixtureDescriptionWithOldText:(NSString *)oldText newText:(NSString *)newText;
/// Fixture format: staggerIndex of every Removed/Inserted op in order, "∅" when there is none.
- (NSString *)staggerFixtureDescription;
@end

NS_ASSUME_NONNULL_END
