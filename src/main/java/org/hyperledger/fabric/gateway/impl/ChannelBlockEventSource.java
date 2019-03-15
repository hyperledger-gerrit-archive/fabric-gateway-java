/*
 * Copyright 2019 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.gateway.impl;

import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * <p>Used to add and remove block listeners for an underlying Channel.</p>
 *
 * <p>This implementation is thread-safe.</p>
 */
public final class ChannelBlockEventSource implements BlockEventSource {
    private final Map<BlockListener, String> handleMap = new IdentityHashMap<>();
    private final Channel channel;

    public ChannelBlockEventSource(Channel channel) {
        this.channel = channel;
    }

    @Override
    public synchronized BlockListener addBlockListener(BlockListener listener) {
        if (!handleMap.containsKey(listener)) {
            String handle = registerChannelListener(listener);
            handleMap.put(listener, handle);
        }
        return listener;
    }

    private String registerChannelListener(BlockListener listener) {
        final String handle;
        try {
            handle = channel.registerBlockListener(blockEvent -> listener.receivedBlock(blockEvent));
        } catch (InvalidArgumentException e) {
            // Throws if channel has been shutdown
            throw new IllegalStateException(e);
        }

        return handle;
    }

    @Override
    public synchronized void removeBlockListener(BlockListener listener) {
        String handle = handleMap.remove(listener);
        if (handle != null) {
            unregisterChannelListener(handle);
        }
    }

    private void unregisterChannelListener(String handle) {
        try {
            channel.unregisterBlockListener(handle);
        } catch (InvalidArgumentException e) {
            // Throws if channel has been shutdown
            throw new IllegalStateException(e);
        }
    }
}
