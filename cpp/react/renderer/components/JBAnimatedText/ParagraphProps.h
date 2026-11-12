/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

#pragma once

#include <string>

// EDITED: since RN 0.86, ParagraphProps is split into BaseParagraphProps and
// platform-specific HostPlatformParagraphProps (`ParagraphProps` is an alias).
// Instead of copying all of that, we subclass it and only add the `text` prop.
#include <react/renderer/components/text/ParagraphProps.h>
#include <react/renderer/core/PropsParserContext.h>
// ADDED
#include <react/renderer/core/RawValue.h>
#include "TransitionKey.h"
// END ADDED

namespace facebook::react {

// ADDED
// transitionKey, normalised to its canonical string (TransitionKey.h). Empty: no key.
struct JBTransitionKey {
  std::string value{};
  bool operator==(const JBTransitionKey &other) const = default;
};

inline void fromRawValue(const PropsParserContext & /*context*/, const RawValue &rawValue, JBTransitionKey &result)
{
  result.value = rawValue.hasValue() ? encodeTransitionKey((folly::dynamic)rawValue) : std::string{};
}
// END ADDED

/*
 * Props of <Paragraph> component.
 * Most of the props are directly stored in composed `ParagraphAttributes`
 * object.
 */
class CParagraphProps : public ParagraphProps { // EDITED
 public:
  CParagraphProps() = default; // EDITED
  CParagraphProps( // EDITED
      const PropsParserContext& context,
      const CParagraphProps& sourceProps, // EDITED
      const RawProps& rawProps);

  void setProp(
      const PropsParserContext& context,
      RawPropsPropNameHash hash,
      const char* propName,
      const RawValue& value);

#pragma mark - Props

  // ADDED
  std::string text{};
  std::string contentTransitionType{"none"};
  Float contentTransitionDuration{0};
  std::string contentTransitionDirection{"auto"};
  std::string contentTransitionUnit{"auto"};
  Float contentTransitionStagger{0};
  std::string contentTransitionScope{"all"};
  Float contentTransitionMinInterval{-1};
  Float contentTransitionDelay{0};
  JBTransitionKey transitionKey{};
  std::string revealType{"none"};
  Float revealDuration{0};
  std::string revealDirection{"up"};
  std::string revealUnit{"auto"};
  Float revealStagger{0};
  Float revealDelay{0};
  // END ADDED

#ifdef RN_SERIALIZABLE_STATE
  folly::dynamic getDiffProps(const Props* prevProps) const override;
#endif
};

} // namespace facebook::react
