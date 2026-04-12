package com.streaming.engine.session;

import java.util.function.Supplier;

public interface StreamMutex {

    <T> T executeWithStreamLock(String streamId, Supplier<T> action);
}
