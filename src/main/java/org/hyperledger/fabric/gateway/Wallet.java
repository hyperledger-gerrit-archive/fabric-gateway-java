/*
 * Copyright 2019 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.gateway;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.Set;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.hyperledger.fabric.gateway.impl.FileSystemWallet;
import org.hyperledger.fabric.gateway.impl.InMemoryWallet;
import org.hyperledger.fabric.gateway.impl.WalletIdentity;

/**
 *
 * Wallet defines the interface for storing and managing users' identities in a Fabric network.
 *
 * @see <a href="https://hyperledger-fabric.readthedocs.io/en/release-1.4/developapps/application.html#wallet">Developing Fabric Applications - Wallet</a>
 */
public interface Wallet {
	/**
	 * Creates a wallet instance that is backed by files on the local filesystem.
	 * If the directory specified by basePath does not exist, then it is created
	 * and an empty wallet is returned.
	 * If a wallet already exists at the directory specified by basePath then
	 * a wallet is returned that contains the identities that were stored on the filesystem.
	 * @param basePath
	 * @return A wallet backed by the file store.
	 * @throws IOException
	 */
	static Wallet createFileSystemWallet(Path basePath) throws IOException {
		return new FileSystemWallet(basePath);
	}

	/**
	 * Creates a wallet instance that is held in the client application heap.
	 * Note that this not persistent and is provided for test purposes.
	 *
	 * @return A newly created empty wallet
	 */
	static Wallet createInMemoryWallet() {
		return new InMemoryWallet();
	}

	/**
	 * Represents a user's identity that is required to connect to a Fabric network.
	 * An instance of identity can be created using the static method
	 * {@link #createIdentity(String, String, PrivateKey) createIdentity} and subsequently
	 * stored and retrieved in a wallet.
	 *
	 */
	interface Identity {
		/**
		 * Get the MSP ID.
		 *
		 * @return a MSP ID.
		 */
		String getMspId();

		/**
		 * Get the user's signed certificate.
		 *
		 * @return a certificate.
		 */
		String getCertificate();

		/**
		 * Get the user's private key
		 *
		 * @return private key.
		 */
		PrivateKey getPrivateKey();

		/**
		 * Creates an identity suitable for storing in a Wallet.
		 * @param mspId The MSPID associated with the identity
		 * @param certificate The public certificate PEM
		 * @param privateKey The private key
		 * @return the identity
		 */
		static Identity createIdentity(String mspId, String certificate, PrivateKey privateKey) {
			return new WalletIdentity(mspId, certificate, privateKey);
		}

		/**
		 * Creates an identity suitable for storing in a Wallet.  The certificate and private key PEMs
		 * are supplied using the {@link Reader} interface for ease of reading from a file system
		 * or other storage mechanism.
		 * @param mspId The MSPID associated with the identity
		 * @param certificate The public certificate PEM
		 * @param privateKey The private key PEM
		 * @return the identity
		 * @throws IOException
		 */
		static Identity createIdentity(String mspId, Reader certificate, Reader privateKey) throws IOException {
			try (PEMParser parser = new PEMParser(privateKey);
					BufferedReader certReader = new BufferedReader(certificate)) {
				Object key = parser.readObject();
				JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
				PrivateKey pk;
				if (key instanceof PrivateKeyInfo) {
					pk = converter.getPrivateKey((PrivateKeyInfo) key);
				} else {
					pk = converter.getPrivateKey(((PEMKeyPair) key).getPrivateKeyInfo());
				}
				StringBuilder contents = new StringBuilder();
				String line;
				while ((line = certReader.readLine()) != null) {
					contents.append(line);
					contents.append('\n');
				}

				return new WalletIdentity(mspId, contents.toString(), pk);
			}
		}
	}

	/**
	 * Inserts an identity into the wallet.
	 *
	 * @param label The label associated with the identity in the wallet.
	 * @param identity The identity to put in the wallet.
	 * @throws GatewayException
	 */
	void put(String label, Identity identity) throws GatewayException;

	/**
	 * Extracts an identity from the wallet.
	 *
	 * @param label The label associated with the identity in the wallet.
	 * @return The identity.
	 * @throws GatewayException
	 */
	Identity get(String label) throws GatewayException;

	/**
	 * Lists the labels of all identities in the wallet.
	 *
	 * @return The set of identity labels.
	 * @throws GatewayException
	 */
	Set<String> getAllLabels() throws GatewayException;

	/**
	 * Removes an identity from the wallet.
	 *
	 * @param label The label associated with the identity in the wallet.
	 * @throws GatewayException
	 */
	void remove(String label) throws GatewayException;

	/**
	 * Query the existence of an identity in the wallet.
	 *
	 * @param label The label associated with the identity in the wallet.
	 * @return true if the label exists, false otherwise.
	 * @throws GatewayException
	 */
	boolean exists(String label) throws GatewayException;
}
