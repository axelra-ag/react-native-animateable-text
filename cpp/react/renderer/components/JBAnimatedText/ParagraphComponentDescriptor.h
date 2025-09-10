/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

#pragma once

#include <react/renderer/components/JBAnimatedText/ParagraphShadowNode.h> // EDITED
#include <react/renderer/core/ConcreteComponentDescriptor.h>
#include <react/renderer/textlayoutmanager/TextLayoutManager.h>
#include <react/utils/ContextContainer.h>

namespace facebook::react {

extern const char TextLayoutManagerKey[];

/*
 * Descriptor for <Paragraph> component.
 */
class CParagraphComponentDescriptor final // EDITED
    : public ConcreteComponentDescriptor<CParagraphShadowNode> { // EDITED
 public:
  explicit CParagraphComponentDescriptor( // EDITED
      const ComponentDescriptorParameters& parameters)
      : ConcreteComponentDescriptor<CParagraphShadowNode>(parameters), // EDITED
        textLayoutManager_(getManagerByName<TextLayoutManager>(
            contextContainer_,
            TextLayoutManagerKey)) {}

 protected:
  void adopt(ShadowNode& shadowNode) const override {
    ConcreteComponentDescriptor::adopt(shadowNode);

    auto& paragraphShadowNode = static_cast<CParagraphShadowNode&>(shadowNode); // EDITED

    // `ParagraphShadowNode` uses `TextLayoutManager` to measure text content
    // and communicate text rendering metrics to mounting layer.
    paragraphShadowNode.setTextLayoutManager(textLayoutManager_);
  }

 private:
  // Every `ParagraphShadowNode` has a reference to a shared `TextLayoutManager`
  const std::shared_ptr<const TextLayoutManager> textLayoutManager_;
};

} // namespace facebook::react
