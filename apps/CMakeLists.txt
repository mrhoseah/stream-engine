add_subdirectory(stream-client)

# stream-engine-server: HTTP API for Recastly integration (requires nlohmann_json, cpp-httplib)
find_package(nlohmann_json CONFIG QUIET)
find_package(httplib CONFIG QUIET)
find_package(cpp-httplib CONFIG QUIET)
if(nlohmann_json_FOUND AND (httplib_FOUND OR cpp-httplib_FOUND))
  add_subdirectory(stream-engine-server)
endif()
