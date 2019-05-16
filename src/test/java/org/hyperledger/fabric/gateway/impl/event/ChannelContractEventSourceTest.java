/*
 * Copyright 2019 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.gateway.impl.event;

import org.hyperledger.fabric.gateway.spi.ContractEvent;
import org.hyperledger.fabric.gateway.spi.ContractListener;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.ChaincodeEvent;
import org.hyperledger.fabric.sdk.ChaincodeEventListener;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

public class ChannelContractEventSourceTest {
    private Channel channel;
    private ChannelContractEventSource contractEventSource;
    private final HashMap<String, ChaincodeEventListener> channelListenerMap = new HashMap<>();
    private final Pattern chaincodeId = Pattern.compile("chaincodeId");
    private final Pattern eventName = Pattern.compile("eventName");

    @BeforeEach
    public void beforeEach() throws Exception {
        channel = mock(Channel.class);
        when(channel.registerChaincodeEventListener(any(Pattern.class), any(Pattern.class), any(ChaincodeEventListener.class))).thenAnswer(invocation -> {
            ChaincodeEventListener listener = invocation.getArgument(2);
            String handle = getHandle(listener);
            channelListenerMap.put(handle, listener);
            return handle;
        });
        when(channel.unregisterChaincodeEventListener(any(String.class))).thenAnswer(invocation -> {
            String handle = invocation.getArgument(0);
            return channelListenerMap.remove(handle) != null;
        });

        contractEventSource = new ChannelContractEventSource(channel);
    }

    @AfterEach
    public void afterEach() {
        channelListenerMap.clear();
    }

    private String getHandle(ChaincodeEventListener listener) {
        return String.valueOf(listener.hashCode());
    }

    private void fireChaincodeEvent(BlockEvent blockEvent, ChaincodeEvent chaincodeEvent) {
        List<ChaincodeEventListener> listeners = new ArrayList<>(channelListenerMap.values());
        listeners.forEach(listener -> listener.received(getHandle(listener), blockEvent, chaincodeEvent));
    }

    @Test
    public void add_listener_registers_with_channel() throws Exception {
        contractEventSource.addContractListener(chaincodeId, eventName, contractEvent -> {});
        verify(channel).registerChaincodeEventListener(eq(chaincodeId), eq(eventName), (any(ChaincodeEventListener.class)));
    }

    @Test
    public void remove_listener_unregisters_with_channel() throws Exception {
        ContractListener listener = contractEventSource.addContractListener(chaincodeId, eventName, contractEvent -> {});
        contractEventSource.removeContractListener(listener);

        verify(channel).unregisterChaincodeEventListener(any(String.class));
    }

    @Test
    public void add_duplicate_listener_does_not_register_with_channel() throws Exception {
        ContractListener listener = contractEventSource.addContractListener(chaincodeId, eventName, contractEvent -> {});
        verify(channel, times(1)).registerChaincodeEventListener(eq(chaincodeId), eq(eventName), (any(ChaincodeEventListener.class)));

        contractEventSource.addContractListener(chaincodeId, eventName, listener);
        verify(channel, times(1)).registerChaincodeEventListener(eq(chaincodeId), eq(eventName), (any(ChaincodeEventListener.class)));
    }

    @Test
    public void remove_listener_that_was_not_added_does_not_unregister_with_channel() throws Exception {
        contractEventSource.removeContractListener(contractEvent -> {});

        verify(channel, never()).unregisterChaincodeEventListener(any(String.class));
    }

    @Test
    public void throws_unchecked_if_channel_register_throws() throws Exception {
        reset(channel);
        when(channel.registerChaincodeEventListener(eq(chaincodeId), eq(eventName), (any(ChaincodeEventListener.class)))).thenThrow(InvalidArgumentException.class);

        assertThatThrownBy(() -> contractEventSource.addContractListener(chaincodeId, eventName, contractEvent -> {}))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void does_not_throw_if_channel_unregister_throws() throws Exception {
        ContractListener listener = contractEventSource.addContractListener(chaincodeId, eventName, contractEvent -> {});

        reset(channel);
        when(channel.unregisterChaincodeEventListener(any(String.class))).thenThrow(InvalidArgumentException.class);

        contractEventSource.removeContractListener(listener);
    }

    @Test
    public void forwards_channel_events_to_listener() throws Exception {
        ContractListener listener = mock(ContractListener.class);
        ArgumentCaptor<ContractEvent> captor = ArgumentCaptor.forClass(ContractEvent.class);
        BlockEvent blockEvent = mock(BlockEvent.class);
        ChaincodeEvent chaincodeEvent = mock(ChaincodeEvent.class);
        when(chaincodeEvent.getChaincodeId()).thenReturn("chaincodeId");
        when(chaincodeEvent.getEventName()).thenReturn("eventName");
        when(chaincodeEvent.getPayload()).thenReturn("payload".getBytes(StandardCharsets.UTF_8));
        when(chaincodeEvent.getTxId()).thenReturn("transactionId");

        contractEventSource.addContractListener(chaincodeId, eventName, listener);
        fireChaincodeEvent(blockEvent, chaincodeEvent);

        verify(listener).receivedEvent(captor.capture());
        ContractEvent contractEvent = captor.getValue();
        assertThat(contractEvent.getBlockEvent()).isSameAs(blockEvent);
        assertThat(contractEvent.getChaincodeId()).isEqualTo(chaincodeEvent.getChaincodeId());
        assertThat(contractEvent.getName()).isEqualTo(chaincodeEvent.getEventName());
        assertThat(contractEvent.getPayload().orElse(new byte[0])).isEqualTo(chaincodeEvent.getPayload());
        assertThat(contractEvent.getTransactionId()).isEqualTo(chaincodeEvent.getTxId());
    }

    @Test
    public void close_unregisters_listener_with_channel() throws Exception {
        contractEventSource.addContractListener(chaincodeId, eventName, contractEvent -> {});
        contractEventSource.close();

        verify(channel).unregisterChaincodeEventListener(any(String.class));
    }

    @Test
    public void listener_can_unregister_during_event_handling() {
        ContractListener listener = mock(ContractListener.class);
        doAnswer(invocation -> {
            contractEventSource.removeContractListener(listener);
            return null;
        }).when(listener).receivedEvent(any());
        BlockEvent blockEvent = mock(BlockEvent.class);
        ChaincodeEvent chaincodeEvent = mock(ChaincodeEvent.class);

        contractEventSource.addContractListener(chaincodeId, eventName, listener);
        fireChaincodeEvent(blockEvent, chaincodeEvent);
        verify(listener, times(1)).receivedEvent(any());

        fireChaincodeEvent(blockEvent, chaincodeEvent);
        verify(listener, times(1)).receivedEvent(any());
    }
}
