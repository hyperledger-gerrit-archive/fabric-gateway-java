/*
 * Copyright 2019 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.gateway.spi;

/**
 * Functional interface for listening to events emitted by a contract.
 */
@FunctionalInterface
public interface ContractListener {
    void receivedEvent(ContractEvent contractEvent);
}
