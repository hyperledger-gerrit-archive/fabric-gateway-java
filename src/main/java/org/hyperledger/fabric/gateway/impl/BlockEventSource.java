/*
 * Copyright 2019 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.gateway.impl;

/**
 * Allows observing received block events.
 */
public interface BlockEventSource {
    BlockListener addBlockListener(BlockListener listener);
    void removeBlockListener(BlockListener listener);
}
