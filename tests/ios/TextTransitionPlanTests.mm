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
      JBTextTransitionPlan *plan = [JBTextTransitionPlan planFromText:oldText
                                                               toText:newText
                                                                 mode:[JBTextTransitionPlan modeFromString:columns[0]]
                                                            direction:columns[1]];
      NSString *direction = plan.direction > 0 ? @"up" : @"down";
      NSString *ops = [plan fixtureDescriptionWithOldText:oldText newText:newText];
      total++;
      if (![direction isEqualToString:columns[4]] || ![ops isEqualToString:columns[5]]) {
        failures++;
        printf("FAIL  %s  %s → %s\n  expected: %s  %s\n  actual:   %s  %s\n", columns[0].UTF8String,
               columns[2].UTF8String, columns[3].UTF8String, columns[4].UTF8String, columns[5].UTF8String,
               direction.UTF8String, ops.UTF8String);
      }
    }
    printf("iOS transition plan: %d/%d fixtures passed\n", total - failures, total);
    return failures == 0 ? 0 : 1;
  }
}
