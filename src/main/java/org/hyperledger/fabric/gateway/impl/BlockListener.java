/*
 * Copyright 2019 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.gateway.impl;

import org.hyperledger.fabric.sdk.BlockEvent;

/**
 * Functional interface for block listening.
 */
public interface BlockListener {
    void receivedBlock(BlockEvent blockEvent);
}
