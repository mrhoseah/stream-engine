cmake_minimum_required(VERSION 3.21)
project(stream-engine
  VERSION 1.0.0
  LANGUAGES CXX
  DESCRIPTION "C++ streaming engine using Red5 Pro Core SDK"
)

# C++17 standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# vcpkg: run before project() if using toolchain file
# -DCMAKE_TOOLCHAIN_FILE=[vcpkg root]/scripts/buildsystems/vcpkg.cmake
if(DEFINED ENV{VCPKG_ROOT} AND NOT DEFINED CMAKE_TOOLCHAIN_FILE)
  set(CMAKE_TOOLCHAIN_FILE "$ENV{VCPKG_ROOT}/scripts/buildsystems/vcpkg.cmake"
      CACHE STRING "Vcpkg toolchain")
endif()

# Export compile commands for IDE and clang-tidy
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Red5 Core SDK: set CMAKE_PREFIX_PATH to Red5 SDK cmake folder, e.g.:
# -DCMAKE_PREFIX_PATH=C:/Users/$USER/Red5Core/<distribution>/cmake
set(RED5_SDK_OPTIONAL ON CACHE BOOL "Build with Red5 Core SDK when available")

# Compiler flags
add_compile_options(
  $<$<CXX_COMPILER_ID:MSVC>:/W4 /utf-8>
  $<$<NOT:$<CXX_COMPILER_ID:MSVC>>:-Wall -Wextra -Wpedantic>
)

# Debug flags
add_compile_options(
  $<$<CONFIG:Debug>:$<$<CXX_COMPILER_ID:MSVC>:/Od>>
  $<$<CONFIG:Debug>:$<$<NOT:$<CXX_COMPILER_ID:MSVC>>:-O0 -g>>
)

# Subproject mode for sibling integration (e.g. recastly via add_subdirectory)
option(STREAM_ENGINE_SUBPROJECT "Build only lib (skip apps/tests) for sibling integration" OFF)

add_subdirectory(libs)

if(NOT STREAM_ENGINE_SUBPROJECT)
  add_subdirectory(apps)

  option(BUILD_TESTS "Build unit tests" ON)
  if(BUILD_TESTS)
    enable_testing()
    add_subdirectory(tests)
  endif()
endif()
