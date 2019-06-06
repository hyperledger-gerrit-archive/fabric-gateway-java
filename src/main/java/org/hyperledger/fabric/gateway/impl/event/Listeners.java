/*
 * Copyright 2019 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.gateway.impl.event;

import org.hyperledger.fabric.gateway.ContractEvent;
import org.hyperledger.fabric.gateway.spi.Checkpointer;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.BlockInfo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

public final class Listeners {
    public static Consumer<BlockEvent> fromTransaction(Consumer<BlockEvent.TransactionEvent> listener) {
        return blockEvent -> blockEvent.getTransactionEvents().forEach(listener);
    }

    public static Consumer<BlockEvent> fromContract(Consumer<ContractEvent> listener) {
        return fromTransaction(transactionFromContract(listener));
    }

    private static Consumer<BlockEvent.TransactionEvent> transactionFromContract(Consumer<ContractEvent> listener) {
        return transactionEvent -> StreamSupport.stream(transactionEvent.getTransactionActionInfos().spliterator(), false)
                .map(BlockInfo.TransactionEnvelopeInfo.TransactionActionInfo::getEvent)
                .filter(Objects::nonNull)
                .map(chaincodeEvent -> new ContractEventImpl(transactionEvent, chaincodeEvent))
                .forEach(listener);
    }

    public static Consumer<BlockEvent> checkpointBlock(Checkpointer checkpointer, Consumer<BlockEvent> listener) {
        return blockEvent -> {
            try {
                synchronized (checkpointer) {
                    final long eventBlockNumber = blockEvent.getBlockNumber();
                    long checkpointBlockNumber = checkpointer.getBlockNumber();

                    if (Checkpointer.UNSET_BLOCK_NUMBER == checkpointBlockNumber) {
                        // Record a starting block in case we don't complete handling and checkpoint below
                        checkpointBlockNumber = eventBlockNumber;
                        checkpointer.setBlockNumber(checkpointBlockNumber);
                    }

                    if (eventBlockNumber == checkpointBlockNumber) {
                        listener.accept(blockEvent); // Process event before checkpointing
                        checkpointer.setBlockNumber(eventBlockNumber + 1);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    public static Consumer<BlockEvent> checkpointTransaction(Checkpointer checkpointer, Consumer<BlockEvent.TransactionEvent> listener) {
        return checkpointBlock(checkpointer, fromTransaction(transactionEvent -> {
            try {
                synchronized (checkpointer) {
                    String transactionId = transactionEvent.getTransactionID();
                    if (!checkpointer.getTransactionIds().contains(transactionId)) {
                        listener.accept(transactionEvent); // Process event before checkpointing
                        checkpointer.addTransactionId(transactionId);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }));
    }

    public static Consumer<BlockEvent> checkpointContract(Checkpointer checkpointer, Consumer<ContractEvent> listener) {
        return checkpointTransaction(checkpointer, transactionFromContract(listener));
    }

    public static Consumer<ContractEvent> contract(Consumer<ContractEvent> listener, String chaincodeId) {
        return contractEvent -> {
            if (contractEvent.getChaincodeId().equals(chaincodeId)) {
                listener.accept(contractEvent);
            }
        };
    }

    public static Consumer<ContractEvent> contract(Consumer<ContractEvent> listener, String chaincodeId, Pattern namePattern) {
        return contract(contractEvent -> {
            if (namePattern.matcher(contractEvent.getName()).matches()) {
                listener.accept(contractEvent);
            }
        }, chaincodeId);
    }
}
