// Runs fixtures/text-transitions.tsv against ios/JBTextTransitionPlan.mm on macOS.
// Usage: scripts/test-transition-plan.sh

#import <Foundation/Foundation.h>
#import "JBTextTransitionPlan.h"

static NSString *fromFixture(NSString *value)
{
  return [value isEqualToString:@"∅"] ? @"" : value;
}

int main(int argc, const char *argv[])
{
  @autoreleasepool {
    NSString *path = [NSString stringWithUTF8String:argv[1]];
    NSString *contents = [NSString stringWithContentsOfFile:path encoding:NSUTF8StringEncoding error:nil];
    int failures = 0;
    int total = 0;
    for (NSString *line in [contents componentsSeparatedByString:@"\n"]) {
      if (line.length == 0 || [line hasPrefix:@"#"]) continue;
      NSArray<NSString *> *columns = [line componentsSeparatedByString:@"\t"];
      NSString *oldText = fromFixture(columns[2]);
      NSString *newText = fromFixture(columns[3]);
      // Mode column: "mode", "mode:unit" or "mode:unit:scope"; "reveal:mode[:unit]" for a reveal.
      NSArray<NSString *> *modeAndUnit = [columns[0] componentsSeparatedByString:@":"];
      JBTextTransitionPlan *plan;
      if ([modeAndUnit[0] isEqualToString:@"reveal"]) {
        NSString *unit = modeAndUnit.count > 2 ? modeAndUnit[2] : @"auto";
        if (oldText.length > 0) printf("note: reveal rows ignore the old text (%s)\n", columns[2].UTF8String);
        plan = [JBTextTransitionPlan revealPlanForText:newText
                                                  mode:[JBTextTransitionPlan modeFromString:modeAndUnit[1]]
                                             direction:columns[1]
                                                  unit:[JBTextTransitionPlan unitFromString:unit]];
      } else {
        NSString *unit = modeAndUnit.count > 1 ? modeAndUnit[1] : @"auto";
        plan = [JBTextTransitionPlan planFromText:oldText
                                           toText:newText
                                             mode:[JBTextTransitionPlan modeFromString:modeAndUnit[0]]
                                        direction:columns[1]
                                             unit:[JBTextTransitionPlan unitFromString:unit]
                                            scope:[JBTextTransitionPlan
                                                      scopeFromString:modeAndUnit.count > 2 ? modeAndUnit[2] : @"all"]];
      }
      NSString *direction = plan.direction > 0 ? @"up" : @"down";
      NSString *ops = [plan fixtureDescriptionWithOldText:oldText newText:newText];
      NSString *stagger = [plan staggerFixtureDescription];
      NSString *expectedStagger = columns.count > 6 ? columns[6] : @"(missing)";
      total++;
      if (![direction isEqualToString:columns[4]] || ![ops isEqualToString:columns[5]] ||
          ![stagger isEqualToString:expectedStagger]) {
        failures++;
        printf("FAIL  %s  %s → %s\n  expected: %s  %s  [%s]\n  actual:   %s  %s  [%s]\n", columns[0].UTF8String,
               columns[2].UTF8String, columns[3].UTF8String, columns[4].UTF8String, columns[5].UTF8String,
               expectedStagger.UTF8String, direction.UTF8String, ops.UTF8String, stagger.UTF8String);
      }
    }
    printf("iOS transition plan: %d/%d fixtures passed\n", total - failures, total);
    return failures == 0 ? 0 : 1;
  }
}
