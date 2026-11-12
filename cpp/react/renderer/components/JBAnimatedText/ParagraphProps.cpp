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
              : convertRawProp(context, rawProps, "text", sourceProps.text, {})),
      contentTransitionType(
          ReactNativeFeatureFlags::enableCppPropsIteratorSetter()
              ? sourceProps.contentTransitionType
              : convertRawProp(
                    context,
                    rawProps,
                    "contentTransitionType",
                    sourceProps.contentTransitionType,
                    {"none"})),
      contentTransitionDuration(
          ReactNativeFeatureFlags::enableCppPropsIteratorSetter()
              ? sourceProps.contentTransitionDuration
              : convertRawProp(
                    context,
                    rawProps,
                    "contentTransitionDuration",
                    sourceProps.contentTransitionDuration,
                    {0})),
      contentTransitionDirection(
          ReactNativeFeatureFlags::enableCppPropsIteratorSetter()
              ? sourceProps.contentTransitionDirection
              : convertRawProp(
                    context,
                    rawProps,
                    "contentTransitionDirection",
                    sourceProps.contentTransitionDirection,
                    {"auto"})),
      contentTransitionUnit(
          ReactNativeFeatureFlags::enableCppPropsIteratorSetter()
              ? sourceProps.contentTransitionUnit
              : convertRawProp(
                    context,
                    rawProps,
                    "contentTransitionUnit",
                    sourceProps.contentTransitionUnit,
                    {"auto"})),
      contentTransitionStagger(
          ReactNativeFeatureFlags::enableCppPropsIteratorSetter()
              ? sourceProps.contentTransitionStagger
              : convertRawProp(
                    context,
                    rawProps,
                    "contentTransitionStagger",
                    sourceProps.contentTransitionStagger,
                    {0})),
      contentTransitionScope(
          ReactNativeFeatureFlags::enableCppPropsIteratorSetter()
              ? sourceProps.contentTransitionScope
              : convertRawProp(
                    context,
                    rawProps,
                    "contentTransitionScope",
                    sourceProps.contentTransitionScope,
                    {"all"})),
      contentTransitionMinInterval(
          ReactNativeFeatureFlags::enableCppPropsIteratorSetter()
              ? sourceProps.contentTransitionMinInterval
              : convertRawProp(
                    context,
                    rawProps,
                    "contentTransitionMinInterval",
                    sourceProps.contentTransitionMinInterval,
                    {-1})),
      contentTransitionDelay(
          ReactNativeFeatureFlags::enableCppPropsIteratorSetter()
              ? sourceProps.contentTransitionDelay
              : convertRawProp(
                    context,
                    rawProps,
                    "contentTransitionDelay",
                    sourceProps.contentTransitionDelay,
                    {0})),
      transitionKey(
          ReactNativeFeatureFlags::enableCppPropsIteratorSetter()
              ? sourceProps.transitionKey
              : convertRawProp(
                    context,
                    rawProps,
                    "transitionKey",
                    sourceProps.transitionKey,
                    JBTransitionKey{})),
      revealType(
          ReactNativeFeatureFlags::enableCppPropsIteratorSetter()
              ? sourceProps.revealType
              : convertRawProp(
                    context,
                    rawProps,
                    "revealType",
                    sourceProps.revealType,
                    {"none"})),
      revealDuration(
          ReactNativeFeatureFlags::enableCppPropsIteratorSetter()
              ? sourceProps.revealDuration
              : convertRawProp(
                    context,
                    rawProps,
                    "revealDuration",
                    sourceProps.revealDuration,
                    {0})),
      revealDirection(
          ReactNativeFeatureFlags::enableCppPropsIteratorSetter()
              ? sourceProps.revealDirection
              : convertRawProp(
                    context,
                    rawProps,
                    "revealDirection",
                    sourceProps.revealDirection,
                    {"up"})),
      revealUnit(
          ReactNativeFeatureFlags::enableCppPropsIteratorSetter()
              ? sourceProps.revealUnit
              : convertRawProp(
                    context,
                    rawProps,
                    "revealUnit",
                    sourceProps.revealUnit,
                    {"auto"})),
      revealStagger(
          ReactNativeFeatureFlags::enableCppPropsIteratorSetter()
              ? sourceProps.revealStagger
              : convertRawProp(
                    context,
                    rawProps,
                    "revealStagger",
                    sourceProps.revealStagger,
                    {0})),
      revealDelay(
          ReactNativeFeatureFlags::enableCppPropsIteratorSetter()
              ? sourceProps.revealDelay
              : convertRawProp(
                    context,
                    rawProps,
                    "revealDelay",
                    sourceProps.revealDelay,
                    {0}))
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
    RAW_SET_PROP_SWITCH_CASE_BASIC(contentTransitionType);
    RAW_SET_PROP_SWITCH_CASE_BASIC(contentTransitionDuration);
    RAW_SET_PROP_SWITCH_CASE_BASIC(contentTransitionDirection);
    RAW_SET_PROP_SWITCH_CASE_BASIC(contentTransitionUnit);
    RAW_SET_PROP_SWITCH_CASE_BASIC(contentTransitionStagger);
    RAW_SET_PROP_SWITCH_CASE_BASIC(contentTransitionScope);
    RAW_SET_PROP_SWITCH_CASE_BASIC(contentTransitionMinInterval);
    RAW_SET_PROP_SWITCH_CASE_BASIC(contentTransitionDelay);
    RAW_SET_PROP_SWITCH_CASE_BASIC(transitionKey);
    RAW_SET_PROP_SWITCH_CASE_BASIC(revealType);
    RAW_SET_PROP_SWITCH_CASE_BASIC(revealDuration);
    RAW_SET_PROP_SWITCH_CASE_BASIC(revealDirection);
    RAW_SET_PROP_SWITCH_CASE_BASIC(revealUnit);
    RAW_SET_PROP_SWITCH_CASE_BASIC(revealStagger);
    RAW_SET_PROP_SWITCH_CASE_BASIC(revealDelay);
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
  if (contentTransitionType != oldProps->contentTransitionType) {
    result["contentTransitionType"] = contentTransitionType;
  }
  if (contentTransitionDuration != oldProps->contentTransitionDuration) {
    result["contentTransitionDuration"] = contentTransitionDuration;
  }
  if (contentTransitionDirection != oldProps->contentTransitionDirection) {
    result["contentTransitionDirection"] = contentTransitionDirection;
  }
  if (contentTransitionUnit != oldProps->contentTransitionUnit) {
    result["contentTransitionUnit"] = contentTransitionUnit;
  }
  if (contentTransitionStagger != oldProps->contentTransitionStagger) {
    result["contentTransitionStagger"] = contentTransitionStagger;
  }
  if (contentTransitionScope != oldProps->contentTransitionScope) {
    result["contentTransitionScope"] = contentTransitionScope;
  }
  if (contentTransitionMinInterval != oldProps->contentTransitionMinInterval) {
    result["contentTransitionMinInterval"] = contentTransitionMinInterval;
  }
  if (contentTransitionDelay != oldProps->contentTransitionDelay) {
    result["contentTransitionDelay"] = contentTransitionDelay;
  }
  if (transitionKey != oldProps->transitionKey) {
    // Android re-reads it as a string; equal keys still give equal strings.
    result["transitionKey"] = transitionKey.value.empty() ? folly::dynamic(nullptr) : folly::dynamic(transitionKey.value);
  }
  if (revealType != oldProps->revealType) {
    result["revealType"] = revealType;
  }
  if (revealDuration != oldProps->revealDuration) {
    result["revealDuration"] = revealDuration;
  }
  if (revealDirection != oldProps->revealDirection) {
    result["revealDirection"] = revealDirection;
  }
  if (revealUnit != oldProps->revealUnit) {
    result["revealUnit"] = revealUnit;
  }
  if (revealStagger != oldProps->revealStagger) {
    result["revealStagger"] = revealStagger;
  }
  if (revealDelay != oldProps->revealDelay) {
    result["revealDelay"] = revealDelay;
  }
  // END ADDED

  return result;
}

#endif
} // namespace facebook::react
