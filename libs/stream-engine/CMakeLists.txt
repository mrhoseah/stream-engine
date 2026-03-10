add_library(stream-engine STATIC
  src/stream_engine.cpp
  src/config.cpp
  src/streaming_service.cpp
)
target_include_directories(stream-engine PUBLIC
  $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
  $<INSTALL_INTERFACE:include>
)

# Optional Red5 Core SDK integration
if(RED5_SDK_OPTIONAL)
  find_package(r5common QUIET)
  find_package(r5core QUIET)
  if(r5common_FOUND AND r5core_FOUND)
    target_link_libraries(stream-engine PUBLIC r5core r5common)
    target_compile_definitions(stream-engine PUBLIC STREAM_ENGINE_HAS_RED5=1)
  endif()
endif()

# vcpkg / other dependencies (example: nlohmann-json for config)
find_package(nlohmann_json CONFIG QUIET)
if(nlohmann_json_FOUND)
  target_link_libraries(stream-engine PUBLIC nlohmann_json::nlohmann_json)
  target_compile_definitions(stream-engine PUBLIC STREAM_ENGINE_HAS_JSON=1)
endif()

# Install for recastly / sibling project integration
include(GNUInstallDirs)
install(TARGETS stream-engine
  EXPORT stream-engine-targets
  ARCHIVE DESTINATION ${CMAKE_INSTALL_LIBDIR}
  INCLUDES DESTINATION ${CMAKE_INSTALL_INCLUDEDIR}
)
install(DIRECTORY include/
  DESTINATION ${CMAKE_INSTALL_INCLUDEDIR}
  FILES_MATCHING PATTERN "*.hpp"
)
install(EXPORT stream-engine-targets
  NAMESPACE stream-engine::
  DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/stream-engine
)
include(CMakePackageConfigHelpers)
configure_package_config_file(
  ${CMAKE_CURRENT_SOURCE_DIR}/../cmake/stream-engineConfig.cmake.in
  ${CMAKE_CURRENT_BINARY_DIR}/stream-engineConfig.cmake
  INSTALL_DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/stream-engine
)
install(FILES ${CMAKE_CURRENT_BINARY_DIR}/stream-engineConfig.cmake
  DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/stream-engine
)
