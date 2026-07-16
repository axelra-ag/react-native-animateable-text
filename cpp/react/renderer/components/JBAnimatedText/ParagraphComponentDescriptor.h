/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

#pragma once

#include <react/renderer/components/JBAnimatedText/ParagraphShadowNode.h> // EDITED
#include <react/renderer/components/text/BaseParagraphComponentDescriptor.h>

namespace facebook::react {
/*
 * Descriptor for <Paragraph> component.
 */
class CParagraphComponentDescriptor final // EDITED
    : public BaseParagraphComponentDescriptor<CParagraphShadowNode> { // EDITED
 public:
  using BaseParagraphComponentDescriptor::BaseParagraphComponentDescriptor;
};

} // namespace facebook::react
