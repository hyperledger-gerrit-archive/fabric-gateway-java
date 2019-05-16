/*
 * Copyright 2019 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.gateway.impl;

import org.hyperledger.fabric.gateway.Contract;
import org.hyperledger.fabric.gateway.Gateway;
import org.hyperledger.fabric.gateway.Network;
import org.hyperledger.fabric.gateway.TestUtils;
import org.hyperledger.fabric.gateway.impl.event.StubContractEventSource;
import org.hyperledger.fabric.gateway.spi.ContractEvent;
import org.hyperledger.fabric.gateway.spi.ContractListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class ContractListenerTest {
    private Contract contract;
    private StubContractEventSource stubContractEventSource;
    private Pattern eventNamePattern = Pattern.compile(".*");

    @BeforeEach
    public void beforeEach() throws Exception {
        stubContractEventSource = new StubContractEventSource(); // Must be before network is created
        Gateway gateway = TestUtils.getInstance().newGatewayBuilder().connect();
        Network network = gateway.getNetwork("ch1");
        contract = network.getContract("contract");
    }

    @AfterEach
    public void afterEach() {
        stubContractEventSource.close();
    }

    @Test
    public void add_listener_returns_the_listener() {
        ContractListener listener = contractEvent -> {};

        ContractListener result = contract.addContractListener(eventNamePattern, listener);

        assertThat(result).isSameAs(listener);
    }

    @Test
    public void listener_receives_events() {
        ContractEvent event = Mockito.mock(ContractEvent.class);
        ContractListener listener = Mockito.mock(ContractListener.class);

        contract.addContractListener(eventNamePattern, listener);
        stubContractEventSource.sendEvent(event);

        Mockito.verify(listener).receivedEvent(event);
    }

    @Test public void removed_listener_does_not_receive_events() {
        ContractEvent event = Mockito.mock(ContractEvent.class);
        ContractListener listener = Mockito.mock(ContractListener.class);

        contract.addContractListener(eventNamePattern, listener);
        contract.removeContractListener(listener);
        stubContractEventSource.sendEvent(event);

        Mockito.verify(listener, Mockito.never()).receivedEvent(event);
    }
}
