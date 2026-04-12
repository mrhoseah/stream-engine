package com.streaming.engine.distributed;

/**
 * Tracks request nonces for replay protection. Implementations must be safe under concurrent use.
 */
public interface NonceStore {

    /**
     * Registers a nonce if it has not been seen within the retention window.
     *
     * @param nonce        client-provided nonce
     * @param nowEpochSec  current unix time (seconds)
     * @param ttlSeconds   how long the nonce must be remembered
     * @return true if the nonce is new; false if replay
     */
    boolean tryRegister(String nonce, long nowEpochSec, long ttlSeconds);
}
