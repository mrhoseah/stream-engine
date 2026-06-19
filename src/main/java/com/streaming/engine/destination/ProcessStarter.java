package com.streaming.engine.destination;

import java.io.IOException;
import java.util.List;

@FunctionalInterface
public interface ProcessStarter {

    ProcessStarter DEFAULT = command -> new ProcessBuilder(command).start();

    Process start(List<String> command) throws IOException;
}
