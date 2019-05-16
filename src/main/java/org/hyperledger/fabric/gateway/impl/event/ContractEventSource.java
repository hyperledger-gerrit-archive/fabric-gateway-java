/*
 * Copyright 2019 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.gateway.impl.event;

import org.hyperledger.fabric.gateway.ContractListener;

import java.util.regex.Pattern;

/**
 * Allows observing received chaincode events.
 */
public interface ContractEventSource extends AutoCloseable {
    ContractListener addContractListener(ContractListener listener, Pattern chaincodeId, Pattern eventName);
    void removeContractListener(ContractListener listener);

    @Override
    void close();
}
