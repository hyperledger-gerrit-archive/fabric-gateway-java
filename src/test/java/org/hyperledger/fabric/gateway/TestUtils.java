/*
 * Copyright 2019 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.gateway;

import org.bouncycastle.operator.OperatorCreationException;
import org.hyperledger.fabric.gateway.impl.Enrollment;
import org.hyperledger.fabric.gateway.impl.GatewayImpl;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;

public final class TestUtils {
    private static final TestUtils INSTANCE = new TestUtils();

    private final Path networkConfigPath = Paths.get("src", "test", "java", "org", "hyperledger", "fabric", "gateway", "connection.json");

    public static TestUtils getInstance() {
        return INSTANCE;
    }

    private TestUtils() { }

    public GatewayImpl.Builder newGatewayBuilder() throws GatewayException, OperatorCreationException, CertificateException, NoSuchAlgorithmException, NoSuchProviderException, IOException {
        Enrollment enrollment = Enrollment.createTestEnrollment();
        PrivateKey privateKey = enrollment.getPrivateKey();
        String certificate = enrollment.getCertificate();

        GatewayImpl.Builder builder = (GatewayImpl.Builder)Gateway.createBuilder();
        Wallet wallet = Wallet.createInMemoryWallet();
        wallet.put("user", Wallet.Identity.createIdentity("msp1", certificate, privateKey));
        builder.identity(wallet, "user").networkConfig(networkConfigPath);
        return builder;
    }
}
