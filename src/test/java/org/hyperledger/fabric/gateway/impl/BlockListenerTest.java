/*
 * Copyright 2019 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.gateway.impl;

import org.hyperledger.fabric.gateway.Gateway;
import org.hyperledger.fabric.gateway.Network;
import org.hyperledger.fabric.gateway.TestUtils;
import org.hyperledger.fabric.gateway.impl.event.StubBlockEventSource;
import org.hyperledger.fabric.gateway.spi.BlockListener;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.Peer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

public class BlockListenerTest {
    private static final TestUtils testUtils = TestUtils.getInstance();

    private Network network = null;
    private StubBlockEventSource stubBlockEventSource = null;
    private final Peer peer1 = testUtils.newMockPeer("peer1");
    private final Peer peer2 = testUtils.newMockPeer("peer2");

    @BeforeEach
    public void beforeEach() throws Exception {
        stubBlockEventSource = new StubBlockEventSource(); // Must be before network is created
        Gateway gateway = testUtils.newGatewayBuilder().connect();
        network = gateway.getNetwork("ch1");
    }

    @AfterEach
    public void afterEach() {
        stubBlockEventSource.close();
    }

    @Test
    public void add_listener_returns_the_listener() {
        BlockListener listener = blockEvent -> {};

        BlockListener result = network.addBlockListener(listener);

        assertThat(result).isSameAs(listener);
    }

    @Test
    public void listener_receives_events() {
        BlockListener listener = Mockito.mock(BlockListener.class);
        BlockEvent event = testUtils.newMockBlockEvent(peer1, 2);

        network.addBlockListener(listener);
        stubBlockEventSource.sendEvent(event);

        Mockito.verify(listener).receivedBlock(event);
    }

    @Test
    public void removed_listener_does_not_receive_events() {
        BlockListener listener = Mockito.mock(BlockListener.class);
        BlockEvent event = testUtils.newMockBlockEvent(peer1, 1);

        network.addBlockListener(listener);
        network.removeBlockListener(listener);
        stubBlockEventSource.sendEvent(event);

        Mockito.verify(listener, Mockito.never()).receivedBlock(event);
    }

    @Test
    public void duplicate_events_ignored() {
        BlockListener listener = Mockito.mock(BlockListener.class);
        BlockEvent event = testUtils.newMockBlockEvent(peer1, 1);
        BlockEvent duplicateEvent = testUtils.newMockBlockEvent(peer2, 1);

        network.addBlockListener(listener);
        stubBlockEventSource.sendEvent(event);
        stubBlockEventSource.sendEvent(duplicateEvent);

        Mockito.verify(listener, Mockito.never()).receivedBlock(duplicateEvent);
    }

    @Test
    public void listener_receives_events_in_order() {
        BlockListener listener = Mockito.mock(BlockListener.class);
        BlockEvent event1 = testUtils.newMockBlockEvent(peer1, 1);
        BlockEvent event2 = testUtils.newMockBlockEvent(peer1, 2);
        BlockEvent event3 = testUtils.newMockBlockEvent(peer1, 3);

        network.addBlockListener(listener);
        stubBlockEventSource.sendEvent(event1); // Prime the listener with an initial block number
        stubBlockEventSource.sendEvent(event3);
        stubBlockEventSource.sendEvent(event2);

        InOrder orderVerifier = Mockito.inOrder(listener);
        orderVerifier.verify(listener).receivedBlock(event1);
        orderVerifier.verify(listener).receivedBlock(event2);
        orderVerifier.verify(listener).receivedBlock(event3);
    }

    @Test
    public void old_events_ignored() {
        BlockListener listener = Mockito.mock(BlockListener.class);
        BlockEvent event1 = testUtils.newMockBlockEvent(peer1, 1);
        BlockEvent event2 = testUtils.newMockBlockEvent(peer1, 2);

        network.addBlockListener(listener);
        stubBlockEventSource.sendEvent(event2);
        stubBlockEventSource.sendEvent(event1);

        Mockito.verify(listener, Mockito.never()).receivedBlock(event1);
    }

    @Test
    public void still_receive_new_events_after_ignoring_old_events() {
        BlockListener listener = Mockito.mock(BlockListener.class);
        BlockEvent event1 = testUtils.newMockBlockEvent(peer1, 1);
        BlockEvent event2 = testUtils.newMockBlockEvent(peer1, 2);
        BlockEvent event3 = testUtils.newMockBlockEvent(peer1, 3);

        network.addBlockListener(listener);
        stubBlockEventSource.sendEvent(event2); // Prime the listener with an initial block number
        stubBlockEventSource.sendEvent(event1);
        stubBlockEventSource.sendEvent(event3);

        Mockito.verify(listener).receivedBlock(event3);
    }
}
