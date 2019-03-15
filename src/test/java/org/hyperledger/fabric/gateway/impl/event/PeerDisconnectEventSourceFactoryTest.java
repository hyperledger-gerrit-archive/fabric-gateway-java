/*
 * Copyright 2019 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.gateway.impl.event;

import org.hyperledger.fabric.sdk.Peer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;

public class PeerDisconnectEventSourceFactoryTest {
    private final PeerDisconnectEventSourceFactory factory = PeerDisconnectEventSourceFactory.getInstance();
    private Peer peer;

    @Before
    public void beforeEach() {
        peer = Mockito.mock(Peer.class);
    }

    @Test
    public void single_event_source_per_channel() {
        PeerDisconnectEventSource first = factory.getPeerDisconnectEventSource(peer);
        PeerDisconnectEventSource second = factory.getPeerDisconnectEventSource(peer);

        assertThat(first, sameInstance(second));
    }

    @Test
    public void can_override_event_source_for_peer() {
        PeerDisconnectEventSource override = Mockito.mock(PeerDisconnectEventSource.class);

        factory.setPeerDisconnectEventSource(peer, override);
        PeerDisconnectEventSource result = factory.getPeerDisconnectEventSource(peer);

        assertThat(result, sameInstance(override));
    }

    @Test
    public void override_closes_existing_event_source() {
        PeerDisconnectEventSource existing = Mockito.mock(PeerDisconnectEventSource.class);
        PeerDisconnectEventSource override = Mockito.mock(PeerDisconnectEventSource.class);

        factory.setPeerDisconnectEventSource(peer, existing);
        factory.setPeerDisconnectEventSource(peer, override);

        Mockito.verify(existing).close();
    }
}
