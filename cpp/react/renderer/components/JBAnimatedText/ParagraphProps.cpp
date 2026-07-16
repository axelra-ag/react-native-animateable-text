/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

#include "ParagraphProps.h"

#include <react/featureflags/ReactNativeFeatureFlags.h>
#include <react/renderer/core/propsConversions.h>

namespace facebook::react {

CParagraphProps::CParagraphProps( // EDITED
    const PropsParserContext& context,
    const CParagraphProps& sourceProps, // EDITED
    const RawProps& rawProps)
    : ParagraphProps(context, sourceProps, rawProps), // EDITED
      // ADDED
      text(
          ReactNativeFeatureFlags::enableCppPropsIteratorSetter()
              ? sourceProps.text
              : convertRawProp(context, rawProps, "text", sourceProps.text, {}))
// END ADDED
{};

void CParagraphProps::setProp( // EDITED
    const PropsParserContext& context,
    RawPropsPropNameHash hash,
    const char* propName,
    const RawValue& value) {
  // All Props structs setProp methods must always, unconditionally,
  // call all super::setProp methods, since multiple structs may
  // reuse the same values.
  ParagraphProps::setProp(context, hash, propName, value); // EDITED

  static auto defaults = CParagraphProps{}; // EDITED

  // ADDED
  switch (hash) {
    RAW_SET_PROP_SWITCH_CASE_BASIC(text);
  }
  // END ADDED
}

#ifdef RN_SERIALIZABLE_STATE

folly::dynamic CParagraphProps::getDiffProps(const Props* prevProps) const { // EDITED
  static const auto defaultProps = CParagraphProps(); // EDITED

  const CParagraphProps* oldProps = prevProps == nullptr
      ? &defaultProps
      : static_cast<const CParagraphProps*>(prevProps); // EDITED

  folly::dynamic result = ParagraphProps::getDiffProps(oldProps); // EDITED

  // ADDED
  if (text != oldProps->text) {
    result["text"] = text;
  }
  // END ADDED

  return result;
}

#endif
} // namespace facebook::react
