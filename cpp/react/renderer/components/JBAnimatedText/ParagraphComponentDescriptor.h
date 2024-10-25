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

/*
 * Descriptor for <Paragraph> component.
 */
class CParagraphComponentDescriptor final // EDITED
    : public ConcreteComponentDescriptor<CParagraphShadowNode> { // EDITED
 public:
  CParagraphComponentDescriptor(const ComponentDescriptorParameters& parameters) // EDITED
      : ConcreteComponentDescriptor<CParagraphShadowNode>(parameters) { // EDITED
    // Every single `ParagraphShadowNode` will have a reference to
    // a shared `TextLayoutManager`.
    textLayoutManager_ = std::make_shared<TextLayoutManager>(contextContainer_);
  }

 protected:
  void adopt(ShadowNode& shadowNode) const override {
    ConcreteComponentDescriptor::adopt(shadowNode);

    auto& paragraphShadowNode = static_cast<CParagraphShadowNode&>(shadowNode); // EDITED

    // `ParagraphShadowNode` uses `TextLayoutManager` to measure text content
    // and communicate text rendering metrics to mounting layer.
    paragraphShadowNode.setTextLayoutManager(textLayoutManager_);
  }

 private:
  std::shared_ptr<const TextLayoutManager> textLayoutManager_;
};

} // namespace facebook::react
