/*
 * Copyright 2019 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.gateway.impl.event;

import org.hyperledger.fabric.gateway.BlockListener;
import org.hyperledger.fabric.sdk.BlockEvent;

import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Listens to an existing block event source and ensures that its own listeners receive block events in order and
 * without duplicates.
 */
public class OrderedBlockEventSource implements BlockEventSource {
    private static final Comparator<BlockEvent> eventComparator = Comparator.comparingLong(BlockEvent::getBlockNumber);

    private final BlockEventSource blockSource;
    private final ListenerSet<BlockListener> listeners = new ListenerSet<>();
    private final BlockListener blockListener;
    private long lastBlockNumber = -1;
    private final SortedSet<BlockEvent> queuedEvents = new TreeSet<>(eventComparator);
    private final Object eventHandlingLock = new Object();

    public OrderedBlockEventSource(BlockEventSource blockSource) {
        this.blockSource = blockSource;
        this.blockListener = blockSource.addBlockListener(this::receivedBlock);
    }

    @Override
    public BlockListener addBlockListener(BlockListener listener) {
        return listeners.add(listener);
    }

    @Override
    public void removeBlockListener(BlockListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void close() {
        listeners.clear();
        blockSource.removeBlockListener(blockListener);
    }

    private void receivedBlock(BlockEvent event) {
        synchronized (eventHandlingLock) {
            if (isOldBlockNumber(event.getBlockNumber())) {
                return;
            }

            queuedEvents.add(event);
            notifyListeners();
        }
    }

    private boolean isOldBlockNumber(long blockNumber) {
        return blockNumber <= lastBlockNumber;
    }

    private void notifyListeners() {
        for (Iterator<BlockEvent> eventIter = queuedEvents.iterator(); eventIter.hasNext(); ) {
            BlockEvent event = eventIter.next();
            long blockNumber = event.getBlockNumber();

            if (!isNextBlockNumber(blockNumber)) {
                break;
            }

            eventIter.remove();
            lastBlockNumber = blockNumber;
            listeners.forEach(listener -> listener.receivedBlock(event));
        }
    }

    private boolean isNextBlockNumber(long blockNumber) {
        return lastBlockNumber < 0 || blockNumber == lastBlockNumber + 1;
    }
}