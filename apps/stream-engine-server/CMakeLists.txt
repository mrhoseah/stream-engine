add_executable(stream-engine-server src/main.cpp)
target_link_libraries(stream-engine-server PRIVATE
  stream-engine
  nlohmann_json::nlohmann_json
)
if(TARGET cpp-httplib)
  target_link_libraries(stream-engine-server PRIVATE cpp-httplib::cpp-httplib)
endif()
