#include "stream-engine/stream_engine.hpp"
#include <cassert>
#include <iostream>

int main() {
  stream_engine::StreamEngine engine;

  assert(!engine.is_ready());
  assert(engine.initialize());
  assert(engine.is_ready());
  assert(engine.start("test_stream"));
  engine.stop();
  assert(!engine.is_ready());

  std::cout << "All tests passed.\n";
  return 0;
}
