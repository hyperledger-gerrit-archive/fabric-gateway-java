/*
 * Copyright 2019 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.gateway;

import java.util.Collection;
import java.util.EnumSet;

import org.hyperledger.fabric.gateway.impl.AllCommitStrategy;
import org.hyperledger.fabric.gateway.impl.AnyCommitStrategy;
import org.hyperledger.fabric.gateway.impl.CommitHandlerImpl;
import org.hyperledger.fabric.gateway.impl.CommitStrategy;
import org.hyperledger.fabric.gateway.impl.NoOpCommitHandler;
import org.hyperledger.fabric.gateway.spi.CommitHandler;
import org.hyperledger.fabric.gateway.spi.CommitHandlerFactory;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;

/**
 * Default commit handler implementations. Instances can be referenced directly or looked up by name, for example
 * {@code DefaultCommitHandlers.valueOf("NONE")}.
 */
public enum DefaultCommitHandlers implements CommitHandlerFactory {
    /**
     * Do not wait for any commit events to be received from peers after submitting a transaction.
     */
    NONE((transactionId, network) -> NoOpCommitHandler.INSTANCE),

    /**
     * Wait to receive commit events from all currently responding peers in the user's organization after submitting
     * a transaction.
     */
    MSPID_SCOPE_ALLFORTX((transactionId, network) -> {
        Collection<Peer> peers = getEventSourcePeersForOrganization(network);
        CommitStrategy strategy = new AllCommitStrategy(peers);
        return new CommitHandlerImpl(transactionId, network, strategy);
    }),

    /**
     * Wait to receive commit events from all currently responding peers in the network after submitting a transaction.
     */
    NETWORK_SCOPE_ALLFORTX((transactionId, network) -> {
        Collection<Peer> peers = getEventSourcePeers(network);
        CommitStrategy strategy = new AllCommitStrategy(peers);
        return new CommitHandlerImpl(transactionId, network, strategy);
    }),

    /**
     * Wait to receive a commit event from any currently responding peer in the user's organization after submitting
     * a transaction.
     */
    MSPID_SCOPE_ANYFORTX((transactionId, network) -> {
        Collection<Peer> peers = getEventSourcePeersForOrganization(network);
        CommitStrategy strategy = new AnyCommitStrategy(peers);
        return new CommitHandlerImpl(transactionId, network, strategy);
    }),

    /**
     * Wait to receive a commit event from any currently responding peer in the network after submitting a transaction.
     */
    NETWORK_SCOPE_ANYFORTX((transactionId, network) -> {
        Collection<Peer> peers = getEventSourcePeers(network);
        CommitStrategy strategy = new AnyCommitStrategy(peers);
        return new CommitHandlerImpl(transactionId, network, strategy);
    });

    private static final EnumSet<Peer.PeerRole> EVENT_SOURCE_ROLES = EnumSet.of(Peer.PeerRole.EVENT_SOURCE);

    private final CommitHandlerFactory factory;

    DefaultCommitHandlers(CommitHandlerFactory factory) {
        this.factory = factory;
    }

    private static Collection<Peer> getEventSourcePeersForOrganization(Network network) {
        Collection<Peer> eventSourcePeers = getEventSourcePeers(network);
        Collection<Peer> orgPeers = getPeersForOrganization(network);
        orgPeers.retainAll(eventSourcePeers);
        return orgPeers;
    }

    private static Collection<Peer> getPeersForOrganization(Network network) {
        String mspId = network.getGateway().getIdentity().getMspId();
        try {
            return network.getChannel().getPeersForOrganization(mspId);
        } catch (InvalidArgumentException e) {
            // This should never happen as mspId should not be null
            throw new RuntimeException(e);
        }
    }

    private static Collection<Peer> getEventSourcePeers(Network network) {
        return network.getChannel().getPeers(EVENT_SOURCE_ROLES);
    }

    public CommitHandler create(String transactionId, Network network) {
        return factory.create(transactionId, network);
    }
}
