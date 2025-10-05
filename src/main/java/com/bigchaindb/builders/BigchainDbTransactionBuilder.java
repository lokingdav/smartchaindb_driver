/*
 * Copyright BigchainDB GmbH and BigchainDB contributors
 * SPDX-License-Identifier: (Apache-2.0 AND CC-BY-4.0)
 * Code is Apache-2.0 and docs are CC-BY-4.0
 */
package com.bigchaindb.builders;

import com.bigchaindb.api.TransactionsApi;
import com.bigchaindb.builders.BigchainDbConfigBuilder.ConfigBuilder;
import com.bigchaindb.constants.Operations;
import com.bigchaindb.cryptoconditions.types.Ed25519Sha256Condition;
import com.bigchaindb.cryptoconditions.types.Ed25519Sha256Fulfillment;
import com.bigchaindb.json.strategy.TransactionDeserializer;
import com.bigchaindb.json.strategy.TransactionsDeserializer;
import com.bigchaindb.model.*;
import com.bigchaindb.util.Base58;
import com.bigchaindb.util.DriverUtils;
import com.bigchaindb.util.JsonUtils;
import com.bigchaindb.util.KeyPairUtils;
import com.google.api.client.util.Base64;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializer;
import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import okhttp3.Response;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * The Class BigchainDbTransactionBuilder.
 */
public class BigchainDbTransactionBuilder {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(BigchainDbTransactionBuilder.class);

    /**
     * Instantiates a new bigchain db transaction builder.
     */
    private BigchainDbTransactionBuilder() {
    }

    /**
     * Inits the.
     *
     * @return the builder
     */
    public static Builder init() {
        return new BigchainDbTransactionBuilder.Builder();
    }

    /**
     * The Interface IAssetMetaData.
     */
    public interface ITransactionAttributes {

        /**
         * Operation.
         *
         * @param operation the operation
         * @return the i asset meta data
         */
        ITransactionAttributes operation(Operations operation);

        /*
         * Adds the asset.
         *
         * @param key
         *            the key
         * @param value
         *            the value
         * @return the i asset meta data
         */
        //ITransactionAttributes addAsset(String key, String value);

        ITransactionAttributes addAssetDataClass(Class assetDataClass, JsonDeserializer<?> jsonDeserializer);

        ITransactionAttributes addOutput(String amount, EdDSAPublicKey... publicKey);

        ITransactionAttributes addOutput(String amount);

        ITransactionAttributes addOutput(String amount, EdDSAPublicKey publicKey);

        ITransactionAttributes addOutput(String amount, String publicKey);

        ITransactionAttributes addOutputG(String amount, String keyG);

        ITransactionAttributes addInput(String fullfillment, FulFill fullFill, EdDSAPublicKey... publicKey);

        ITransactionAttributes addInput(String fullfillment, FulFill fullFill);

        ITransactionAttributes addInput(String fullfillment, FulFill fullFill, EdDSAPublicKey publicKey);

        ITransactionAttributes addInput(Details fullfillment, FulFill fullFill, EdDSAPublicKey... publicKey);

        ITransactionAttributes addInputG(String fullfillment, FulFill fullFill, String keyG);

        /**
         * Adds the assets.
         *
         * @param assets          the assets
         * @param assetsDataClass class if asset data
         * @return the i asset meta data
         */
        ITransactionAttributes addAssets(Object assets, Class assetsDataClass);

        /**
         * Adds the meta data.
         *
         * @param metaData the json object
         * @return the i asset meta data
         */
        ITransactionAttributes addMetaData(Object metaData);

        /**
         * Add the class and deserializer for metadata
         *
         * @param metaDataClass    the class of the metadata object
         * @param jsonDeserializer the deserializer
         * @return
         */
        ITransactionAttributes addMetaDataClassDeserializer(Class metaDataClass, JsonDeserializer<?> jsonDeserializer);

        /**
         * Add the class and serializer for metadata
         *
         * @param metaDataClass  the class of the metadata object
         * @param jsonSerializer the deserializer
         * @return
         */
        ITransactionAttributes addMetaDataClassSerializer(Class metaDataClass, JsonSerializer<?> jsonSerializer);

        /**
         * Builds the.
         *
         * @param publicKey the public key
         * @return the i build
         * @throws Exception
         */
        IBuild build(EdDSAPublicKey publicKey) throws Exception;

        IBuild buildG(String publicKeyG) throws Exception;

        /**
         * Builds the and sign.
         *
         * @param publicKey  the public key
         * @param privateKey the private key
         * @return the i build
         * @throws Exception
         */
        IBuild buildAndSign(EdDSAPublicKey publicKey, EdDSAPrivateKey privateKey) throws Exception;

        IBuild buildAndSignG(String publicKey, String payload, String privateKey) throws Exception;

        /**
         * Builds the and sign and return.
         *
         * @param publicKey the public key
         * @return the transaction
         * @throws Exception
         */
        Transaction buildOnly(EdDSAPublicKey publicKey) throws Exception;

        /**
         * Builds the and sign and return.
         *
         * @param publicKey  the public key
         * @param privateKey the private key
         * @return the transaction
         * @throws Exception
         */
        Transaction buildAndSignOnly(EdDSAPublicKey publicKey, EdDSAPrivateKey privateKey) throws Exception;


    }

    /**
     * The Interface IBuild.
     */
    public interface IBuild {

        /**
         * Send transaction.
         *
         * @return the transaction
         * @throws TimeoutException exception on timeout
         */
        Transaction sendTransaction() throws TimeoutException;

        Transaction validateTransaction() throws TimeoutException;

        /**
         * Send transaction.
         *
         * @param callback the callback
         * @return the transaction
         * @throws TimeoutException exception on timeout
         */
        Transaction sendTransaction(GenericCallback callback) throws TimeoutException;


        Transaction getTransaction() throws Exception;

    }

    /**
     * The Class Builder.
     */
    public static class Builder implements ITransactionAttributes, IBuild {

        private final ConfigBuilder configBuilder = new ConfigBuilder();

        /**
         * The metadata.
         */
        private Object metadata = null;

        /**
         * The assets.
         */
        private Object assets = null;
        private Class assetsDataClass = null;

        /**
         * The inputs.
         */
        private final List<Input> inputs = new ArrayList<>();

        /**
         * The outputs.
         */
        private final List<Output> outputs = new ArrayList<>();

        /**
         * The public key.
         */
        private EdDSAPublicKey publicKey;

        private String publicKeyG;

        /**
         * The transaction.
         */
        private Transaction transaction;

        /**
         * The operation.
         */
        private Operations operation;

        @Override
        public ITransactionAttributes addOutput(String amount) {
            return addOutput(amount, this.publicKey);
        }

        @Override
        public ITransactionAttributes addOutput(String amount, EdDSAPublicKey publicKey) {
            EdDSAPublicKey[] keys = new EdDSAPublicKey[]{publicKey};
            return addOutput(amount, keys);
        }

        @Override
        public ITransactionAttributes addOutput(String amount, EdDSAPublicKey... publicKeys) {
            for (EdDSAPublicKey publicKey : publicKeys) {
                Output output = new Output();
                Ed25519Sha256Condition sha256Condition = new Ed25519Sha256Condition(publicKey);
                output.setAmount(amount);
                output.addPublicKey(KeyPairUtils.encodePublicKeyInBase58(publicKey));
                Details details = new Details();
                details.setPublicKey(KeyPairUtils.encodePublicKeyInBase58(publicKey));
                details.setType("ed25519-sha-256");
                output.setCondition(new Condition(details, sha256Condition.getUri().toString()));
                this.outputs.add(output);
            }
            return this;
        }

        @Override
        public ITransactionAttributes addOutput(String amount, String publicKey) {
            Output output = new Output();
            Ed25519Sha256Condition sha256Condition = new Ed25519Sha256Condition(Base58.decode(publicKey), 131072L);
            output.setAmount(amount);
            output.addPublicKey(publicKey);
            Details details = new Details();
            details.setPublicKey(publicKey);
            details.setType("ed25519-sha-256");
            output.setCondition(new Condition(details, sha256Condition.getUri().toString()));
            this.outputs.add(output);

            return this;
        }

        @Override
        public ITransactionAttributes addOutputG(String amount, String gpkey) {
            Output output = new Output();
            output.setAmount(amount);
            output.addPublicKey(gpkey);
            Details details = new Details();
            details.setPublicKey(gpkey);
            details.setType("group_signature");
            output.setCondition(new Condition(details, null));
            this.outputs.add(output);
            return this;
        }

        @Override
        public ITransactionAttributes addInput(String fullfillment, FulFill fullFill) {
            return addInput(fullfillment, fullFill, this.publicKey);
        }

        @Override
        public ITransactionAttributes addInput(String fullfillment, FulFill fullFill, EdDSAPublicKey publicKey) {
            EdDSAPublicKey[] keys = new EdDSAPublicKey[]{publicKey};
            return addInput(fullfillment, fullFill, keys);
        }

        @Override
        public ITransactionAttributes addInput(String fullfillment, FulFill fullFill, EdDSAPublicKey... publicKeys) {
            for (EdDSAPublicKey publicKey : publicKeys) {
                Input input = new Input();
                input.setFullFillment(fullfillment);
                input.setFulFills(fullFill);
                input.addOwner(KeyPairUtils.encodePublicKeyInBase58(publicKey));
                this.inputs.add(input);
            }
            return this;
        }

        @Override
        public ITransactionAttributes addInput(Details fullfillment, FulFill fullFill, EdDSAPublicKey... publicKeys) {
            for (EdDSAPublicKey publicKey : publicKeys) {
                Input input = new Input();
                input.setFullFillment(fullfillment);
                input.setFulFills(fullFill);
                input.addOwner(KeyPairUtils.encodePublicKeyInBase58(publicKey));
                this.inputs.add(input);
            }
            return this;
        }

        @Override
        public ITransactionAttributes addInputG(String fullfillment, FulFill fullFill, String gpkey) {
            Input input = new Input();
            input.setFullFillment(fullfillment);
            input.setFulFills(fullFill);
            input.addOwner(gpkey);
            this.inputs.add(input);
            return this;
        }

        public ITransactionAttributes addAssetDataClass(Class assetDataClass, JsonDeserializer<?> jsonDeserializer) {
            return this;
        }

        /**
         * Add
         *
         * @param metaDataClass    the class of the metadata object
         * @param jsonDeserializer the deserializer
         * @return self
         */
        @Override
        public ITransactionAttributes addMetaDataClassDeserializer(Class metaDataClass, JsonDeserializer<?> jsonDeserializer) {
            TransactionDeserializer.setMetaDataClass(metaDataClass);
            TransactionsDeserializer.setMetaDataClass(metaDataClass);
            JsonUtils.addTypeAdapterDeserializer(metaDataClass, jsonDeserializer);
            return this;
        }

        public ITransactionAttributes addMetaDataClassSerializer(Class metaDataClass, JsonSerializer<?> jsonSerializer) {
            JsonUtils.addTypeAdapterSerializer(metaDataClass, jsonSerializer);
            return this;
        }

        public ITransactionAttributes addMetaData(Object object) {
            this.metadata = object;
            return this;
        }

        private void buildHelper() throws Exception {
            this.transaction = new Transaction();
            if (this.operation == Operations.CREATE
                    || this.operation == Operations.TRANSFER
                    || this.operation == Operations.REQUEST_FOR_QUOTE
                    || this.operation == Operations.INTEREST
                    || this.operation == Operations.PRE_REQUEST
                    || this.operation == Operations.BID
                    || this.operation == Operations.ACCEPT
                    || this.operation == Operations.ADV
                    || this.operation == Operations.ADVERTISEMENT  // FIXED: Added for SHACL compatibility
                    || this.operation == Operations.UPDATE_ADV
                    || this.operation == Operations.BUYOFFER
                    || this.operation == Operations.BUY_OFFER  // FIXED: Added for SHACL compatibility
                    || this.operation == Operations.INVERSE_TXN
                    || this.operation == Operations.SELL
                    || this.operation == Operations.REQUEST_RETURN
                    || this.operation == Operations.ACCEPT_RETURN
                    || this.operation == Operations.SELLER_ACCEPT_RETURN) {
                this.transaction.setOperation(this.operation.name());
            } else {
                throw new Exception("Invalid Operations value. Accepted values are "
                        + "[Operations.CREATE, Operations.TRANSFER, Operations.REQUEST_FOR_QUOTE, "
                        + "Operations.INTEREST, Operations.PRE_REQUEST, Operations.BID, Operations.ACCEPT, "
                        + "Operations.ADVERTISEMENT, Operations.BUY_OFFER, Operations.SELL, "
                        + "Operations.REQUEST_RETURN, Operations.ACCEPT_RETURN, Operations.SELLER_ACCEPT_RETURN]");
            }

            if (this.assets != null) {
                if (String.class.isAssignableFrom(this.assets.getClass())) {
                    // interpret as an asset ID
                    this.transaction.setAsset(new Asset((String) this.assets));
                } else {
                    // otherwise it's an asset
                    this.transaction.setAsset(new Asset(this.assets, this.assetsDataClass));
                }
            }
            this.transaction.setMetaData(this.metadata);
            this.transaction.setVersion("2.0");

            this.transaction.setId(null);
        }

        /*
         * (non-Javadoc)
         *
         * @see
         * com.bigchaindb.builders.BigchainDbTransactionBuilder.IAssetMetaData#
         * build(net.i2p.crypto.eddsa.EdDSAPublicKey)
         */
        @Override
        public IBuild build(EdDSAPublicKey publicKey) throws Exception {
            buildHelper();
            this.publicKey = publicKey;

            if (this.outputs.isEmpty()) {
                this.addOutput("1");
            }
            for (Output output : this.outputs) {
                this.transaction.addOutput(output);
            }

            if (this.inputs.isEmpty()) {
                this.addInput(null, null);
            }
            for (Input input : this.inputs) {
                this.transaction.addInput(input);
            }

            return this;
        }

        @Override
        public IBuild buildG(String publicKeyG) throws Exception {
            buildHelper();
            this.publicKeyG = publicKeyG;

            if (this.outputs.isEmpty()) {
                this.addOutputG("1", publicKeyG);
            }
            for (Output output : this.outputs) {
                this.transaction.addOutput(output);
            }

            if (this.inputs.isEmpty()) {
                this.addInputG(null, null, publicKeyG);
            }
            for (Input input : this.inputs) {
                this.transaction.addInput(input);
            }

            return this;
        }


        /**
         * Sign.
         *
         * @param privateKey the private key
         * @throws InvalidKeyException      the invalid key exception
         * @throws SignatureException       the signature exception
         * @throws NoSuchAlgorithmException the no such algorithm exception
         */
        private void sign(EdDSAPrivateKey privateKey)
                throws InvalidKeyException, SignatureException, NoSuchAlgorithmException {
            String temp = this.transaction.toHashInput();
            JsonObject transactionJObject = DriverUtils.makeSelfSortingGson(temp);

            byte[] sha3Hash;
            if (Operations.TRANSFER.name().equals(this.transaction.getOperation())
                    || Operations.BID.name().equals(this.transaction.getOperation()) 
                    || Operations.BUYOFFER.name().equals(this.transaction.getOperation()) 
                    || Operations.SELL.name().equals(this.transaction.getOperation()) 
                    || Operations.PRE_REQUEST.name().equals(this.transaction.getOperation())  
                    || Operations.INTEREST.name().equals(this.transaction.getOperation())
                    || Operations.REQUEST_RETURN.name().equals(this.transaction.getOperation())
                    || Operations.SELLER_ACCEPT_RETURN.name().equals(this.transaction.getOperation())) {
                // it's a transfer operation: make sure to update the hash pre-image with
                // the fulfilling transaction IDs and output indexes
                StringBuilder preimage = new StringBuilder(transactionJObject.toString());
                for (Input in : this.transaction.getInputs()) {
                    if (in.getFulFills() != null) {
                        FulFill fulfill = in.getFulFills();
                        String txBlock = fulfill.getTransactionId() + fulfill.getOutputIndex();
                        preimage.append(txBlock);
                    }
                }
                sha3Hash = DriverUtils.getSha3HashRaw(preimage.toString().getBytes());
            } else {
                // otherwise, just get the message digest
                sha3Hash = DriverUtils.getSha3HashRaw(transactionJObject.toString().getBytes());
            }

            // signing the transaction
            Signature edDsaSigner = new EdDSAEngine(MessageDigest.getInstance("SHA-512"));
            edDsaSigner.initSign(privateKey);
            edDsaSigner.update(sha3Hash);
            byte[] signature = edDsaSigner.sign();
            Ed25519Sha256Fulfillment fulfillment = new Ed25519Sha256Fulfillment(this.publicKey, signature);
            this.transaction.getInputs().get(0)
                    .setFullFillment(Base64.encodeBase64URLSafeString(fulfillment.getEncoded()));
            this.transaction.setSigned(true);

            String id = DriverUtils.getSha3HashHex(
                    DriverUtils.makeSelfSortingGson(this.transaction.toHashInput()).toString().getBytes());
            this.transaction.setId(id);
        }

        public void Gsign(String groupSecretKey, String seralization, String groupPublicKey) throws java.io.IOException {

            String jsonPayload = '"' + seralization.replaceAll(",", "comma") + '"';

            String clean = groupSecretKey.replace("comma \"", "comma '");
            String cleanString = clean.replace("\")", "')");
            String cleanString2 = cleanString.replace("\"", "'");
            String cleanString3 = cleanString2.replace("') '", "') \"")
                    .replace("'('", "\"('");

            String clean2 = groupPublicKey.replace("comma \"", "comma '");
            String cleanGroupKey = clean2.replace("\")", "')");
            String currentDirIn = System.getProperty("user.dir");
            String callRust = "cd " + currentDirIn + "/ursa_Master/libzmix;cargo test test_scenario_1 --release " +
                    "--no-default-features --features PS_Signature_G1 -- GSign," + cleanString3 + "," + seralization +
                    "," + cleanGroupKey + " --nocapture;";

            ProcessBuilder builder = new ProcessBuilder("bash", "-c", callRust);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            InputStream is = process.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));

            List<String> results = new ArrayList<>();
            String line = null;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Signature is:")) {
                    results.add(line);
                }
            }

            String signature = results.get(0);
            this.transaction.getInputs().get(0).setFullFillment(signature);
            this.transaction.setSigned(true);

            String id = DriverUtils.getSha3HashHex(
                    DriverUtils.makeSelfSortingGson(this.transaction.toHashInput()).toString().getBytes());
            this.transaction.setId(id);
        }

        /*
         * (non-Javadoc)
         *
         * @see
         * com.bigchaindb.builders.BigchainDbTransactionBuilder.IAssetMetaData#
         * buildAndSign(net.i2p.crypto.eddsa.EdDSAPublicKey,
         * net.i2p.crypto.eddsa.EdDSAPrivateKey)
         */
        @Override
        public IBuild buildAndSign(EdDSAPublicKey publicKey, EdDSAPrivateKey privateKey) throws Exception {
            try {
                this.build(publicKey);
                this.sign(privateKey);
            } catch (InvalidKeyException | SignatureException | NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
            return this;
        }

        @Override
        public IBuild buildAndSignG(String secretKeyG, String publicKeyG, String payloadString) throws Exception {
            try {
                this.buildG(publicKeyG);
                this.Gsign(secretKeyG, payloadString, publicKeyG);
            } catch (InvalidKeyException | SignatureException | NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
            return this;
        }

        /*
         * (non-Javadoc)
         *
         * @see
         * com.bigchaindb.builders.BigchainDbTransactionBuilder.IAssetMetaData#
         * buildAndSignAndReturn(net.i2p.crypto.eddsa.EdDSAPublicKey)
         */
        @Override
        public Transaction buildOnly(EdDSAPublicKey publicKey) throws Exception {
            this.build(publicKey);
            return this.transaction;
        }

        /*
         * (non-Javadoc)
         *
         * @see
         * com.bigchaindb.builders.BigchainDbTransactionBuilder.IAssetMetaData#
         * buildAndSignAndReturn(net.i2p.crypto.eddsa.EdDSAPublicKey,
         * net.i2p.crypto.eddsa.EdDSAPrivateKey)
         */
        @Override
        public Transaction buildAndSignOnly(EdDSAPublicKey publicKey, EdDSAPrivateKey privateKey) throws Exception {
            this.buildAndSign(publicKey, privateKey);
            return this.transaction;
        }

        /*
         * (non-Javadoc)
         *
         * @see com.bigchaindb.builders.BigchainDbTransactionBuilder.IBuild#
         * sendTransaction(com.bigchaindb.model.GenericCallback)
         */
        @Override
        public Transaction sendTransaction(GenericCallback callback) throws TimeoutException {
            if (!BigChainDBGlobals.isConnected()) {
                configBuilder.processConnectionFailure(BigChainDBGlobals.getCurrentNode());
                configBuilder.configureNodeToConnect();
            }
            TransactionsApi.sendTransaction(this.transaction, callback);
            configBuilder.processConnectionSuccess(BigChainDBGlobals.getCurrentNode());
            return this.transaction;
        }

        /*
         * (non-Javadoc)
         *
         * @see com.bigchaindb.builders.BigchainDbTransactionBuilder.IBuild#
         * sendTransaction()
         */
        @Override
        public Transaction sendTransaction() throws TimeoutException {
            if (!BigChainDBGlobals.isConnected()) {
                configBuilder.processConnectionFailure(BigChainDBGlobals.getCurrentNode());
                configBuilder.configureNodeToConnect();
            }
            try {
                TransactionsApi.sendTransaction(this.transaction);
                configBuilder.processConnectionSuccess(BigChainDBGlobals.getCurrentNode());
            } catch (IOException e) {
                sendTransaction();
            }
            return this.transaction;
        }

        @Override
        public Transaction validateTransaction() throws TimeoutException {
            if (!BigChainDBGlobals.isConnected()) {
                configBuilder.processConnectionFailure(BigChainDBGlobals.getCurrentNode());
                configBuilder.configureNodeToConnect();
            }

            Response response = null;
            try {
                response = TransactionsApi.validateTransaction(this.transaction);
                configBuilder.processConnectionSuccess(BigChainDBGlobals.getCurrentNode());
            } catch (IOException e) {
                validateTransaction();
            }

            return response != null && response.code() == 202 ? this.transaction : null;
        }

        /**
         * Add an asset along with the assetDataClass
         *
         * @param obj             the asset data
         * @param assetsDataClass the type of the asset data class
         * @return self
         */
        public ITransactionAttributes addAssets(Object obj, Class assetsDataClass) {
            this.assets = obj;
            this.assetsDataClass = assetsDataClass;
            return this;
        }

        /*
         * (non-Javadoc)
         *
         * @see
         * com.bigchaindb.builders.BigchainDbTransactionBuilder.IAssetMetaData#
         * operation(com.bigchaindb.constants.Operations)
         */
        @Override
        public ITransactionAttributes operation(Operations operation) {
            this.operation = operation;
            return this;
        }

        public Transaction getTransaction() {
            return this.transaction;
        }
    }
}
