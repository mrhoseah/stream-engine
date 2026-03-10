#pragma once

/// Single header for recastly / sibling project integration.
/// Include this in consumer projects.
///
/// Example:
///   #include <stream-engine.hpp>
///   auto config = stream_engine::StreamingServiceConfig{...};
///   auto service = stream_engine::StreamingService::create(config);
#include "stream-engine/types.hpp"
#include "stream-engine/config.hpp"
#include "stream-engine/callbacks.hpp"
#include "stream-engine/streaming_service.hpp"
