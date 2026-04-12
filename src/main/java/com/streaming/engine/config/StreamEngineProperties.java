package com.streaming.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stream-engine")
public class StreamEngineProperties {

    private final Distributed distributed = new Distributed();

    public Distributed getDistributed() {
        return distributed;
    }

    public static class Distributed {
        /**
         * When true, Redis must be configured (see spring.data.redis.*) for shared nonces and sessions.
         */
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
