if(NOT TARGET cpp-httplib::cpp-httplib AND NOT TARGET httplib::httplib)
  message(FATAL_ERROR
    "stream-engine-server requires cpp-httplib. Install via vcpkg: vcpkg install cpp-httplib")
endif()

add_executable(stream-engine-server src/main.cpp src/server_config.cpp)
target_include_directories(stream-engine-server PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/include)
target_link_libraries(stream-engine-server PRIVATE
  stream-engine
  nlohmann_json::nlohmann_json
)
if(TARGET cpp-httplib::cpp-httplib)
  target_link_libraries(stream-engine-server PRIVATE cpp-httplib::cpp-httplib)
elseif(TARGET httplib::httplib)
  target_link_libraries(stream-engine-server PRIVATE httplib::httplib)
endif()
