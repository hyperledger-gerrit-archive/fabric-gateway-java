/*
 * Copyright 2019 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.gateway;

import org.hyperledger.fabric.gateway.impl.event.PeerDisconnectEvent;
import org.hyperledger.fabric.gateway.impl.event.StubBlockEventSource;
import org.hyperledger.fabric.gateway.impl.event.StubPeerDisconnectEventSource;
import org.hyperledger.fabric.gateway.impl.event.TransactionEventSourceImpl;
import org.hyperledger.fabric.gateway.spi.CommitHandler;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class NetworkScopeAllForTxTest {
    private static final long TIMEOUT_MILLIS = 2000;

    private final String transactionId = "txId";
    private StubBlockEventSource blockSource;
    private Peer peer1;
    private Peer peer2;
    private CommitHandler commitHandler;

    private Map<Peer, StubPeerDisconnectEventSource> peerDisconnectSources = new HashMap<>();

    @Before
    public void beforeEach() throws Exception {
        blockSource = new StubBlockEventSource();

        peer1 = newMockPeer("peer1");
        peerDisconnectSources.put(peer1, new StubPeerDisconnectEventSource(peer1));

        peer2 = newMockPeer("peer2");
        peerDisconnectSources.put(peer2, new StubPeerDisconnectEventSource(peer2));

        Channel mockChannel = newMockChannel("channel");
        mockChannel.addPeer(peer1);
        mockChannel.addPeer(peer2);

        Network network = Mockito.mock(Network.class);
        Mockito.when(network.getChannel()).thenReturn(mockChannel);
        Mockito.when(network.getTransactionEventSource()).thenReturn(new TransactionEventSourceImpl(blockSource));

        commitHandler = DefaultCommitHandlers.NETWORK_SCOPE_ALLFORTX.create(transactionId, network);
    }

    @After
    public void afterEach() {
        blockSource.close();
        peerDisconnectSources.values().forEach(StubPeerDisconnectEventSource::close);
        peerDisconnectSources.clear();
        commitHandler.cancelListening();
    }

    private Channel newMockChannel(String name) {
        Channel mockChannel = Mockito.mock(Channel.class);

        Mockito.when(mockChannel.getName()).thenReturn(name);

        Set<Peer> peers = new HashSet<>();
        Mockito.when(mockChannel.getPeers()).thenAnswer(invocation -> new HashSet<>(peers));
        try {
            Mockito.when(mockChannel.addPeer(Mockito.any())).thenAnswer(invocation -> {
                Peer peer = invocation.getArgument(0);
                peers.add(peer);
                return invocation.getMock();
            });
        } catch (InvalidArgumentException e) {
            throw new RuntimeException(e);
        }

        return mockChannel;
    }

    private Peer newMockPeer(String name) {
        Peer mockPeer = Mockito.mock(Peer.class);
        Mockito.doReturn(name).when(mockPeer).getName();
        return mockPeer;
    }

    private BlockEvent newMockBlockEvent(Peer peer, BlockEvent.TransactionEvent... transactionEvents) {
        BlockEvent mockEvent = Mockito.mock(BlockEvent.class);
        Mockito.when(mockEvent.getPeer()).thenReturn(peer);
        Mockito.when(mockEvent.getTransactionEvents()).thenReturn(Arrays.asList(transactionEvents));
        return mockEvent;
    }

    private BlockEvent.TransactionEvent newValidMockTransactionEvent(Peer peer, String transactionId) {
        BlockEvent.TransactionEvent txEvent = newMockTransactionEvent(peer, transactionId);
        Mockito.when(txEvent.isValid()).thenReturn(true);
        return txEvent;
    }

    private BlockEvent.TransactionEvent newInvalidMockTransactionEvent(Peer peer, String transactionId) {
        BlockEvent.TransactionEvent txEvent = newMockTransactionEvent(peer, transactionId);
        Mockito.when(txEvent.isValid()).thenReturn(false);
        return txEvent;
    }

    private BlockEvent.TransactionEvent newMockTransactionEvent(Peer peer, String transactionId) {
        BlockEvent.TransactionEvent txEvent = Mockito.mock(BlockEvent.TransactionEvent.class);
        Mockito.when(txEvent.getPeer()).thenReturn(peer);
        Mockito.when(txEvent.getTransactionID()).thenReturn(transactionId);
        return txEvent;
    }

    private void sendPeerDisconnectEvent(Peer peer) {
        StubPeerDisconnectEventSource disconnectSource = peerDisconnectSources.get(peer);
        if (disconnectSource == null) {
            throw new IllegalArgumentException("No disconnect source for peer: " + peer.getName());
        }
        PeerDisconnectEvent event = newPeerDisconnectedEvent(peer);
        disconnectSource.sendEvent(event);
    }

    private PeerDisconnectEvent newPeerDisconnectedEvent(Peer peer) {
        return new PeerDisconnectEvent() {
            @Override
            public Peer getPeer() {
                return peer;
            }

            @Override
            public Throwable getCause() {
                return null;
            }
        };
    }

    private void sendValidTransactionEvent(Peer peer) {
        BlockEvent.TransactionEvent txEvent = newValidMockTransactionEvent(peer, transactionId);
        BlockEvent blockEvent = newMockBlockEvent(peer, txEvent);
        blockSource.sendEvent(blockEvent);
    }

    private void sendInvalidTransactionEvent(Peer peer) {
        BlockEvent.TransactionEvent txEvent = newInvalidMockTransactionEvent(peer, transactionId);
        BlockEvent blockEvent = newMockBlockEvent(peer, txEvent);
        blockSource.sendEvent(blockEvent);
    }

    private interface NoArgsConsumer {
        void run() throws Exception;
    }

    private void runWithTimeout(NoArgsConsumer consumer) throws Throwable {
        CompletableFuture<?> future = CompletableFuture.runAsync(() -> {
            try {
                consumer.run();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });

        try {
            future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    @Test
    public void unblocks_if_all_peers_commit() throws Throwable {
        commitHandler.startListening();

        sendValidTransactionEvent(peer1);
        sendValidTransactionEvent(peer2);

        runWithTimeout(() -> commitHandler.waitForEvents());
    }

    @Test(expected = GatewayException.class)
    public void throws_if_all_peers_disconnect() throws Throwable {
        commitHandler.startListening();

        sendPeerDisconnectEvent(peer1);
        sendPeerDisconnectEvent(peer2);

        runWithTimeout(() -> commitHandler.waitForEvents());
    }

    @Test
    public void unblocks_if_one_peer_commits_and_one_disconnects() throws Throwable {
        commitHandler.startListening();

        sendValidTransactionEvent(peer1);
        sendPeerDisconnectEvent(peer2);

        runWithTimeout(() -> commitHandler.waitForEvents());
    }

    @Test(expected = GatewayException.class)
    public void throws_if_peer_rejects_transaction() throws Throwable {
        commitHandler.startListening();

        sendInvalidTransactionEvent(peer1);

        runWithTimeout(() -> commitHandler.waitForEvents());
    }

    @Test
    public void handles_duplicate_commit_events() throws Throwable {
        commitHandler.startListening();

        sendValidTransactionEvent(peer1);
        sendValidTransactionEvent(peer1);
        sendValidTransactionEvent(peer2);
        sendValidTransactionEvent(peer2);

        runWithTimeout(() -> commitHandler.waitForEvents());
    }

    @Test
    public void handles_duplicate_disconnects() throws Throwable {
        commitHandler.startListening();

        sendValidTransactionEvent(peer1);
        sendPeerDisconnectEvent(peer2);
        sendPeerDisconnectEvent(peer2);

        runWithTimeout(() -> commitHandler.waitForEvents());
    }

    @Test
    public void handles_disconnects_from_peers_that_already_responded() throws Throwable {
        commitHandler.startListening();

        sendValidTransactionEvent(peer1);
        sendPeerDisconnectEvent(peer1);
        sendValidTransactionEvent(peer2);
        sendPeerDisconnectEvent(peer2);

        runWithTimeout(() -> commitHandler.waitForEvents());
    }
}
