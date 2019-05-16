/*
 * Copyright 2019 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.gateway.impl.event;

import org.hyperledger.fabric.gateway.spi.ContractListener;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Used to add and remove contract listeners for an underlying Channel.
 * <p>
 * This implementation is thread-safe.
 * </p>
 */
public final class ChannelContractEventSource implements ContractEventSource {
    private final Map<ContractListener, String> handleMap = new ConcurrentHashMap<>();
    private final Channel channel;

    ChannelContractEventSource(Channel channel) {
        this.channel = channel;
    }

    @Override
    public ContractListener addContractListener(Pattern chaincodeId, Pattern eventName, ContractListener listener) {
        handleMap.computeIfAbsent(listener, l -> registerChannelListener(chaincodeId, eventName, l));
        return listener;
    }

    private String registerChannelListener(Pattern chaincodeId, Pattern eventName, ContractListener listener) {
        try {
            return channel.registerChaincodeEventListener(chaincodeId, eventName,
                    (handle, blockEvent, chaincodeEvent) -> listener.receivedEvent(new ContractEventImpl(chaincodeEvent, blockEvent)));
        } catch (InvalidArgumentException e) {
            // Throws if channel has been shutdown
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void removeContractListener(ContractListener listener) {
        handleMap.computeIfPresent(listener, (key, value) -> {
            unregisterChannelListener(value);
            return null;
        });
    }

    private void unregisterChannelListener(String handle) {
        try {
            channel.unregisterChaincodeEventListener(handle);
        } catch (InvalidArgumentException e) {
            // Ignore to ensure close() never throws
        }
    }

    @Override
    public void close() {
        handleMap.forEach((listener, handle) -> removeContractListener(listener));
    }
}
