/*
 * Copyright 2019 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.gateway.impl.event;

import org.hyperledger.fabric.gateway.spi.Checkpointer;
import org.hyperledger.fabric.sdk.BlockEvent;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Checkpointing of events supplied by a block source. Only blocks with the number expected by the checkpointer are
 * passed to the listener, and the checkpointer block number is updated after events are processed.
 */
public final class CheckpointBlockListenerSession implements ListenerSession {
    private final BlockEventSource blockSource;
    private final Consumer<BlockEvent> listener;
    private final Checkpointer checkpointer;
    private final Consumer<BlockEvent> internalListener;

    public CheckpointBlockListenerSession(BlockEventSource blockSource, Consumer<BlockEvent> listener, Checkpointer checkpointer) {
        this.blockSource = blockSource;
        this.listener = listener;
        this.checkpointer = checkpointer;
        this.internalListener = blockSource.addBlockListener(this::acceptBlock);
    }

    private synchronized void acceptBlock(BlockEvent event) {
        final long eventBlockNumber = event.getBlockNumber();
        System.out.println("CheckpointBlockListenerSession@" + hashCode() + " received block: " + eventBlockNumber);
        try {
            final long checkpointBlockNumber = checkpointer.getBlockNumber();
            System.out.println("CheckpointBlockListenerSession@" + hashCode() + " checkpoint block: " + checkpointBlockNumber);
            if (checkpointBlockNumber == Checkpointer.UNSET_BLOCK_NUMBER || eventBlockNumber == checkpointBlockNumber) {
                System.out.println("CheckpointBlockListenerSession@" + hashCode() + " invoking listener: " + listener);
                listener.accept(event); // Process event before checkpointing
                System.out.println("CheckpointBlockListenerSession@" + hashCode() + " invoked listener: " + listener);
                checkpointer.setBlockNumber(eventBlockNumber + 1);
                System.out.println("CheckpointBlockListenerSession@" + hashCode() + " updated checkpoint block: " + (eventBlockNumber + 1));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        blockSource.removeBlockListener(internalListener);
        // Don't close checkpointer as this may have been called by the listener on receiving an event, so before
        // the last block number has been checkpointed
    }
}
