/*
 * Copyright 2019 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.gateway.impl;

import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ChannelBlockEventSourceTest {
    private Channel channel;
    private ChannelBlockEventSource blockEventSource;

    @Before
    public void beforeEach() {
        channel = mock(Channel.class);
        blockEventSource = new ChannelBlockEventSource(channel);
    }

    @Test
    public void add_listener_registers_with_channel() throws Exception {
        blockEventSource.addBlockListener(blockEvent -> {});
        verify(channel).registerBlockListener(any());
    }

    @Test
    public void remove_listener_unregisters_with_channel() throws Exception {
        String handle = "handle";
        when(channel.registerBlockListener(any())).thenReturn(handle);

        BlockListener listener = blockEventSource.addBlockListener(blockEvent -> {});
        blockEventSource.removeBlockListener(listener);

        verify(channel).unregisterBlockListener(handle);
    }

    @Test
    public void add_duplicate_listener_does_not_register_with_channel() throws Exception {
        BlockListener listener = blockEventSource.addBlockListener(blockEvent -> {});
        verify(channel, times(1)).registerBlockListener(any());

        blockEventSource.addBlockListener(listener);
        verify(channel, times(1)).registerBlockListener(any());
    }

    @Test
    public void remove_listener_that_was_not_added_does_not_unregister_with_channel() throws Exception {
        BlockListener listener = blockEvent -> {};
        blockEventSource.removeBlockListener(listener);

        verify(channel, never()).registerBlockListener(any());
    }

    @Test(expected = IllegalStateException.class)
    public void throws_unchecked_if_channel_register_throws() throws Exception {
        when(channel.registerBlockListener(any())).thenThrow(InvalidArgumentException.class);

        blockEventSource.addBlockListener(blockEvent -> {});
    }

    @Test
    public void does_not_throw_if_channel_unregister_throws() throws Exception {
        when(channel.unregisterBlockListener(any())).thenThrow(InvalidArgumentException.class);

        blockEventSource.addBlockListener(blockEvent -> {});
    }

    @Test
    public void forwards_channel_block_events_to_listener() throws Exception {
        Collection<org.hyperledger.fabric.sdk.BlockListener> channelListeners = new ArrayList<>();
        when(channel.registerBlockListener(any())).thenAnswer(answer -> {
           channelListeners.add(answer.getArgument(0));
           return "handle";
        });
        BlockListener listener = mock(BlockListener.class);
        BlockEvent blockEvent = mock(BlockEvent.class);

        blockEventSource.addBlockListener(listener);
        channelListeners.forEach(channelListener -> channelListener.received(blockEvent));

        verify(listener).receivedBlock(blockEvent);
    }
}
