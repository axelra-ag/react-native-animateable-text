// Runs fixtures/transition-keys.tsv against cpp/.../JBAnimatedText/TransitionKey.h on macOS.
// TransitionKey.h works on folly::dynamic in the app; here on a minimal value with the same
// accessors, built from the fixture's JSON.
// Usage: scripts/test-transition-plan.sh

#import <Foundation/Foundation.h>

#include <string>
#include <vector>

#include "TransitionKey.h"

namespace {

struct TestValue {
  enum Kind { Null, Bool, Number, String, Array, Object } kind = Null;
  bool boolean = false;
  double number = 0;
  std::string string;
  std::vector<TestValue> items;

  bool isNull() const { return kind == Null; }
  bool isBool() const { return kind == Bool; }
  bool isNumber() const { return kind == Number; }
  bool isString() const { return kind == String; }
  bool isArray() const { return kind == Array; }
  bool getBool() const { return boolean; }
  double asDouble() const { return number; }
  const std::string &getString() const { return string; }
  size_t size() const { return items.size(); }
  const TestValue &operator[](size_t index) const { return items[index]; }
};

TestValue fromJSON(id object)
{
  TestValue value;
  if ([object isKindOfClass:[NSNumber class]]) {
    if (CFGetTypeID((__bridge CFTypeRef)object) == CFBooleanGetTypeID()) {
      value.kind = TestValue::Bool;
      value.boolean = [object boolValue];
    } else {
      value.kind = TestValue::Number;
      value.number = [object doubleValue];
    }
  } else if ([object isKindOfClass:[NSString class]]) {
    value.kind = TestValue::String;
    value.string = [object UTF8String];
  } else if ([object isKindOfClass:[NSArray class]]) {
    value.kind = TestValue::Array;
    for (id item in object) value.items.push_back(fromJSON(item));
  } else if ([object isKindOfClass:[NSDictionary class]]) {
    value.kind = TestValue::Object;
  }
  return value;
}

} // namespace

int main(int argc, const char *argv[])
{
  @autoreleasepool {
    NSString *contents = [NSString stringWithContentsOfFile:[NSString stringWithUTF8String:argv[1]]
                                                   encoding:NSUTF8StringEncoding
                                                      error:nil];
    int failures = 0;
    int total = 0;
    for (NSString *line in [contents componentsSeparatedByString:@"\n"]) {
      if (line.length == 0 || [line hasPrefix:@"#"]) continue;
      NSArray<NSString *> *columns = [line componentsSeparatedByString:@"\t"];
      id json = [NSJSONSerialization JSONObjectWithData:[columns[0] dataUsingEncoding:NSUTF8StringEncoding]
                                                options:NSJSONReadingFragmentsAllowed
                                                  error:nil];
      std::string actual = facebook::react::encodeTransitionKey(fromJSON(json));
      total++;
      if (actual != std::string(columns[1].UTF8String)) {
        failures++;
        printf("FAIL  %s\n  expected: %s\n  actual:   %s\n", columns[0].UTF8String, columns[1].UTF8String, actual.c_str());
      }
    }
    printf("iOS transition keys: %d/%d fixtures passed\n", total - failures, total);
    return failures == 0 ? 0 : 1;
  }
}
