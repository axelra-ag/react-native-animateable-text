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
@end

@interface JBTextTransitionPlan : NSObject
@property (nonatomic, readonly) NSArray<JBTextTransitionOp *> *ops;
/// 1: content moves up (counting up), -1: moves down.
@property (nonatomic, readonly) NSInteger direction;

+ (JBTextTransitionMode)modeFromString:(NSString *)value;

+ (instancetype)planFromText:(NSString *)oldText
                      toText:(NSString *)newText
                        mode:(JBTextTransitionMode)mode
                   direction:(NSString *)direction;

/// Whole-text cross-fade: one Removed op for all of the old text, one Inserted for the new.
+ (instancetype)wholeTextPlanFromText:(NSString *)oldText toText:(NSString *)newText;

/// Fixture format: "=kept -removed +inserted", spaces inside segments as "␠".
- (NSString *)fixtureDescriptionWithOldText:(NSString *)oldText newText:(NSString *)newText;
@end

NS_ASSUME_NONNULL_END
