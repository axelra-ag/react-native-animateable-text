/*
 * Custom
 */

#pragma once

#include <ReactCommon/JavaTurboModule.h>
#include <ReactCommon/TurboModule.h>
#include <jsi/jsi.h>
#include <react/renderer/components/JBAnimatedText/ParagraphComponentDescriptor.h>

namespace facebook::react {

JSI_EXPORT
std::shared_ptr<TurboModule> JBAnimatedText_ModuleProvider(
        const std::string &moduleName,
        const JavaTurboModule::InitParams &params) {

    // Ensure moduleName matches the expected name
    return nullptr;
}

} // namespace facebook::react