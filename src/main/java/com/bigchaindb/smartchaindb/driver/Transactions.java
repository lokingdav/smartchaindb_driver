package com.bigchaindb.smartchaindb.driver;

import com.bigchaindb.api.TransactionsApi;
import com.bigchaindb.builders.BigchainDbTransactionBuilder;
import com.bigchaindb.constants.Operations;
import com.bigchaindb.model.FulFill;
import com.bigchaindb.model.MetaData;
import com.bigchaindb.model.Transaction;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;

import java.io.IOException;
import java.security.KeyPair;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.fasterxml.jackson.databind.ObjectMapper; // Jackson for JSON serialization
import java.nio.charset.StandardCharsets;


public class Transactions {

    /**
     * performs CREATE transactions on BigchainDB network
     *
     * @param assetData data to store as asset
     * @param metaData  data to store as metadata
     * @param keys      keys to sign and verify transaction
     * @return id of CREATED asset
     */
    public static String doCreate(BigchainDBJavaDriver driver, Map<String, Object> assetData, MetaData metaData, KeyPair keys) throws Exception {

        Transaction transaction = null;
        try {
            BigchainDbTransactionBuilder.IBuild builder = BigchainDbTransactionBuilder
                    .init()
                    .addAssets(assetData, TreeMap.class)
                    .addMetaData(metaData)
                    .operation(Operations.CREATE)
                    .buildAndSign((EdDSAPublicKey) keys.getPublic(), (EdDSAPrivateKey) keys.getPrivate());

            transaction = builder.sendTransaction(driver.handleServerResponse("CREATE", null, null));
            System.out.println("(*) CREATE Transaction sent.. - " + transaction.getId());
            ObjectMapper mapper = new ObjectMapper();
                String transactionJson = mapper.writeValueAsString(transaction); // Serialize to JSON
                int sizeInBytes = transactionJson.getBytes(StandardCharsets.UTF_8).length;
                double sizeInKB = sizeInBytes / 1024.0;

                System.out.printf("CREATE Transaction size: %d Bytes (%.3f KB)%n", sizeInBytes, sizeInKB);
            // wait until transaction is commited, or retry every second until 5 seconds
            waitForCommit(driver, transaction); 
        } catch (IOException e) {
            e.printStackTrace();
        }

        return transaction != null ? transaction.getId() : null;
    }

    /**
     * performs TRANSFER operations on CREATED assets
     *
     * @param txId     id of transaction/asset
     * @param metaData data to append for this transaction
     * @param keys     keys to sign and verify transactions
     */
    public static Transaction doTransfer(BigchainDBJavaDriver driver, String txId, MetaData metaData, KeyPair keys, KeyPair transferKeys) throws Exception {
       
        Transaction transaction = null;
        Map<String, String> assetData = new TreeMap<String, String>();
        assetData.put("id", txId);

        try {

            FulFill fulfill = new FulFill();
            fulfill.setOutputIndex(0);
            fulfill.setTransactionId(txId);

            BigchainDbTransactionBuilder.IBuild builder = BigchainDbTransactionBuilder
                    .init()
                    .addInput(null, fulfill, (EdDSAPublicKey) keys.getPublic())
                    .addOutput("1", (EdDSAPublicKey) transferKeys.getPublic())
                    .addAssets(txId, String.class)
                    .addMetaData(metaData)
                    .operation(Operations.TRANSFER)
                    .buildAndSign((EdDSAPublicKey) keys.getPublic(), (EdDSAPrivateKey) keys.getPrivate());

            transaction = builder.sendTransaction(driver.handleServerResponse("TRANSFER", null, null));
            System.out.println("(*) TRANSFER Transaction sent.. - " + transaction.getId());

            // wait until transaction is commited, or retry every second until 5 seconds
            waitForCommit(driver, transaction); 
        } catch (IOException e) {
            e.printStackTrace();
        }
        return transaction ;
    }

    public static String doPreRequest(BigchainDBJavaDriver driver, MetaData metaData, KeyPair keys,
                                      String skey, String pkey, String payload) throws Exception {

        Transaction transaction = null;
        Map<String, String> assetData = new TreeMap<String, String>() {{
            put("", "");
        }};

        List<String> capabilityList = (List<String>) metaData.getMetadata().get("capability");
        metaData.setMetaData("inferredCapabilities", Simulation.mediate(capabilityList));

        try {
            BigchainDbTransactionBuilder.IBuild builder;
            if (DriverConstants.GROUP_SIGNATURE) {
                builder = BigchainDbTransactionBuilder
                        .init()
                        .addAssets(assetData, TreeMap.class)
                        .addMetaData(metaData)
                        .operation(Operations.REQUEST_FOR_QUOTE)
                        .buildAndSignG(skey, pkey, payload);
            } else {
                builder = BigchainDbTransactionBuilder
                        .init()
                        .addAssets(assetData, TreeMap.class)
                        .addMetaData(metaData)
                        .operation(Operations.REQUEST_FOR_QUOTE)
                        .buildAndSign((EdDSAPublicKey) keys.getPublic(), (EdDSAPrivateKey) keys.getPrivate());
            }

            transaction = builder.sendTransaction(driver.handleServerResponse("PRE_REQUEST", null, null));
            System.out.println("(*) PRE-REQUEST Transaction sent.. - " + transaction.getId());

            // wait until transaction is commited, or retry every second until 5 seconds
            waitForCommit(driver, transaction); 
        } catch (IOException e) {
            e.printStackTrace();
        }

        return transaction != null ? transaction.getId() : null;
    }

    public static String doInterest(BigchainDBJavaDriver driver, String txId, String preRequestId, MetaData metaData, KeyPair keys) throws Exception {

        Map<String, String> assetData = new TreeMap<String, String>();
        assetData.put("id", txId);
        assetData.put("pre_request_id", preRequestId);

        try {
            Transaction transaction = BigchainDbTransactionBuilder
                    .init()
                    .addAssets(assetData, TreeMap.class)
                    .addMetaData(metaData)
                    .operation(Operations.INTEREST)
                    .buildAndSign((EdDSAPublicKey) keys.getPublic(), (EdDSAPrivateKey) keys.getPrivate())
                    .sendTransaction(driver.handleServerResponse("INTEREST", null, null));

            System.out.println("(*) INTEREST Transaction sent.. - " + transaction.getId());

            // wait until transaction is commited, or retry every second until 5 seconds
            waitForCommit(driver, transaction); 
            return transaction.getId();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static Transaction doRequest(BigchainDBJavaDriver driver, String _txId, MetaData metaData, KeyPair keys,
                                        String skey, String pkey, String payload) throws Exception {

        Transaction transaction = null;
        Map<String, String> assetData = new TreeMap<String, String>() {{
            put("pre_request_id", _txId);
            put("deadline", "2021-02-21");
        }};

        List<String> capabilityList = (List<String>) metaData.getMetadata().get("capability");
        //metaData.setMetaData("inferredCapabilities", Simulation.mediate(capabilityList));

        try {
            BigchainDbTransactionBuilder.IBuild builder;
            if (DriverConstants.GROUP_SIGNATURE) {
                builder = BigchainDbTransactionBuilder
                        .init()
                        .addAssets(assetData, TreeMap.class)
                        .addMetaData(metaData)
                        .operation(Operations.REQUEST_FOR_QUOTE)
                        .buildAndSignG(skey, pkey, payload);
            } else {
                builder = BigchainDbTransactionBuilder
                        .init()
                        .addAssets(assetData, TreeMap.class)
                        .addMetaData(metaData)
                        .operation(Operations.REQUEST_FOR_QUOTE)
                        .buildAndSign((EdDSAPublicKey) keys.getPublic(), (EdDSAPrivateKey) keys.getPrivate());
            }

            String txId = builder.getTransaction().getId();
            transaction = builder.sendTransaction(driver.handleServerResponse("REQUEST_FOR_QUOTE", metaData, txId));
            System.out.println("(*) REQUEST Transaction sent.. - " + txId);

            // wait until transaction is commited, or retry every second until 5 seconds
            waitForCommit(driver, transaction); 
        } catch (IOException e) {
            e.printStackTrace();
        }

        return transaction;
    }

    public static Transaction doBid(BigchainDBJavaDriver driver, String txId, String rfqTxId, MetaData metaData, KeyPair keys) throws Exception {

        Transaction transaction = null;
        Map<String, String> assetData = new TreeMap<String, String>();
        assetData.put("id", txId);
        assetData.put("rfq_id", rfqTxId);

        try {

            FulFill fulfill = new FulFill();
            fulfill.setOutputIndex(0);
            fulfill.setTransactionId(txId);

            BigchainDbTransactionBuilder.IBuild builder = BigchainDbTransactionBuilder
                    .init()
                    .addInput(null, fulfill, (EdDSAPublicKey) keys.getPublic())
                    .addOutput("1", DriverConstants.SMARTCHAINDB_PUBKEY)
                    .addAssets(assetData, TreeMap.class)
                    .addMetaData(metaData)
                    .operation(Operations.BID)
                    .buildAndSign((EdDSAPublicKey) keys.getPublic(), (EdDSAPrivateKey) keys.getPrivate());

            transaction = builder.sendTransaction(driver.handleServerResponse("BID", metaData, null));
            System.out.println("(*) BID Transaction sent.. - " + transaction.getId());

            // wait until transaction is commited, or retry every second until 5 seconds
            waitForCommit(driver, transaction); 
        } catch (IOException e) {
            e.printStackTrace();
        }

        return transaction;
    }

    public static Transaction doBuyOffer(BigchainDBJavaDriver driver, String txId, String advTxId, MetaData metaData, KeyPair buyerKeys) throws Exception {

        Transaction transaction = null;
        Map<String, String> assetData = new TreeMap<String, String>();
        assetData.put("id", txId);
        assetData.put("adv_id", advTxId);

        try {

            FulFill fulfill = new FulFill();
            fulfill.setOutputIndex(0);
            fulfill.setTransactionId(txId);

            BigchainDbTransactionBuilder.IBuild builder = BigchainDbTransactionBuilder
                    .init()
                    .addInput(null, fulfill, (EdDSAPublicKey) buyerKeys.getPublic())
                    .addOutput("1", DriverConstants.SMARTCHAINDB_PUBKEY)
                    .addAssets(assetData, TreeMap.class)
                    .addMetaData(metaData)
                    .operation(Operations.BUYOFFER)
                    .buildAndSign((EdDSAPublicKey) buyerKeys.getPublic(), (EdDSAPrivateKey) buyerKeys.getPrivate());

            transaction = builder.sendTransaction(driver.handleServerResponse("BUYOFFER", metaData, null));
            System.out.println("(*) BUYOFFER Transaction sent.. - " + transaction.getId());
            ObjectMapper mapper = new ObjectMapper();
                String transactionJson = mapper.writeValueAsString(transaction); // Serialize to JSON
                int sizeInBytes = transactionJson.getBytes(StandardCharsets.UTF_8).length;
                double sizeInKB = sizeInBytes / 1024.0;

                System.out.printf("BUYOFFER Transaction size: %d Bytes (%.3f KB)%n", sizeInBytes, sizeInKB);
            // wait until transaction is commited, or retry every second until 5 seconds
            waitForCommit(driver, transaction); 
        } catch (IOException e) {
            e.printStackTrace();
        }

        return transaction;
    }


    public static Transaction doReturnSell(BigchainDBJavaDriver driver, String originalAssetId, String transferTxnId, MetaData metaData, KeyPair buyerKeys) throws Exception {

        Transaction transaction = null;
        Map<String, String> assetData = new TreeMap<>();
        assetData.put("asset_id", originalAssetId);  // Use the original asset ID, not the transfer transaction ID
        assetData.put("sell_id", transferTxnId);  // Track the sell transaction ID if needed
    
        try {
            // Use the last transfer transaction as the input reference
            FulFill fulfill = new FulFill();
            fulfill.setOutputIndex(0);  // Assuming the output index is 0
            fulfill.setTransactionId(transferTxnId);  // The transfer transaction ID where ownership was moved to buyer
    
            // Create the return transaction targeting the escrow/seller account
            BigchainDbTransactionBuilder.IBuild builder = BigchainDbTransactionBuilder
                    .init()
                    .addInput(null, fulfill, (EdDSAPublicKey) buyerKeys.getPublic())  // Use buyer’s public key to sign
                    .addOutput("1", DriverConstants.SMARTCHAINDB_PUBKEY)  // Transfer ownership to escrow
                    .addAssets(assetData, TreeMap.class)
                    .addMetaData(metaData)
                    .operation(Operations.PRE_REQUEST)  // Ensure operation type is correct
                    .buildAndSign((EdDSAPublicKey) buyerKeys.getPublic(), (EdDSAPrivateKey) buyerKeys.getPrivate());
    
            // Send the return transaction
            transaction = builder.sendTransaction(driver.handleServerResponse("INVERSE_TXN", metaData, null));
            System.out.println("(*) INVERSE_TXN Transaction sent.. - " + transaction.getId());
            ObjectMapper mapper = new ObjectMapper();
                String transactionJson = mapper.writeValueAsString(transaction); // Serialize to JSON
                int sizeInBytes = transactionJson.getBytes(StandardCharsets.UTF_8).length;
                double sizeInKB = sizeInBytes / 1024.0;

                System.out.printf("INVERSE_TXN Transaction size: %d Bytes (%.3f KB)%n", sizeInBytes, sizeInKB);
            // wait until transaction is commited, or retry every second until 5 seconds
            waitForCommit(driver, transaction); 
        } catch (IOException e) {
            e.printStackTrace();
        }
    
        return transaction;
    }
    


    public static Transaction doAdv(BigchainDBJavaDriver driver, String txId, MetaData metaData, KeyPair keys) throws Exception {

        Transaction transaction = null;
        Map<String, String> assetData = new TreeMap<String, String>();
        
        assetData.put("asset_id", txId);
        //assetData.put("status", "open");
        try {
                // FulFill fulfill = new FulFill();
                // fulfill.setOutputIndex(0);
                // fulfill.setTransactionId(txId);

                BigchainDbTransactionBuilder.IBuild builder = BigchainDbTransactionBuilder
                        .init()
                        
                        .addAssets(assetData, TreeMap.class)
                        .addMetaData(metaData)
                        .operation(Operations.ADV)
                        .buildAndSign((EdDSAPublicKey) keys.getPublic(), (EdDSAPrivateKey) keys.getPrivate());

                transaction = builder.sendTransaction(driver.handleServerResponse("ADV", metaData, null));
                System.out.println("(*) ADV Transaction sent.. - " + transaction.getId());

                ObjectMapper mapper = new ObjectMapper();
                String transactionJson = mapper.writeValueAsString(transaction); // Serialize to JSON
                int sizeInBytes = transactionJson.getBytes(StandardCharsets.UTF_8).length;
                double sizeInKB = sizeInBytes / 1024.0;

                System.out.printf("ADV Transaction size: %d Bytes (%.3f KB)%n", sizeInBytes, sizeInKB);

                // wait until transaction is commited, or retry every second until 5 seconds
                waitForCommit(driver, transaction); 

                //.addInput(null, fulfill, (EdDSAPublicKey) keys.getPublic())
                //.addOutput("1", DriverConstants.SMARTCHAINDB_PUBKEY)
        } catch (IOException e) {
            e.printStackTrace();
        }

        return transaction;
    }

    public static Transaction updateAdv(BigchainDBJavaDriver driver, String txId, String advId, MetaData metaData, KeyPair keys) throws Exception {

        Transaction transaction = null;
        Map<String, String> assetData = new TreeMap<String, String>();
        
        assetData.put("asset_id", txId);
        assetData.put("adv_id", advId);
        //assetData.put("status", "open");
        try {
                // FulFill fulfill = new FulFill();
                // fulfill.setOutputIndex(0);
                // fulfill.setTransactionId(txId);

                BigchainDbTransactionBuilder.IBuild builder = BigchainDbTransactionBuilder
                        .init()
                        
                        .addAssets(assetData, TreeMap.class)
                        .addMetaData(metaData)
                        .operation(Operations.REQUEST_FOR_QUOTE)
                        .buildAndSign((EdDSAPublicKey) keys.getPublic(), (EdDSAPrivateKey) keys.getPrivate());

                transaction = builder.sendTransaction(driver.handleServerResponse("UPDATE_ADV", metaData, null));
                System.out.println("(*) UPDATE_ADV Transaction sent.. - " + transaction.getId());

                // wait until transaction is commited, or retry every second until 5 seconds
                waitForCommit(driver, transaction); 
                //.addInput(null, fulfill, (EdDSAPublicKey) keys.getPublic())
                //.addOutput("1", DriverConstants.SMARTCHAINDB_PUBKEY)
        } catch (IOException e) {
            e.printStackTrace();
        }

        return transaction;
    }

    public static Transaction doSell(BigchainDBJavaDriver driver, String txId,String advId, String buyOfferId, MetaData metaData, KeyPair keys) throws Exception {

        Transaction transaction = null;
        Map<String, String> assetData = new TreeMap<String, String>();
        
        assetData.put("asset_id", txId);
        assetData.put("ref1_id", advId);
        assetData.put("ref2_id", buyOfferId);
        try {
                FulFill fulfill = new FulFill();
                fulfill.setOutputIndex(0);
                fulfill.setTransactionId(txId);

                BigchainDbTransactionBuilder.IBuild builder = BigchainDbTransactionBuilder
                        .init()
                        .addInput(null, fulfill, (EdDSAPublicKey) keys.getPublic())
                        .addOutput("1", DriverConstants.SMARTCHAINDB_PUBKEY)
                        .addAssets(assetData, TreeMap.class)
                        .addMetaData(metaData)
                        .operation(Operations.SELL)
                        .buildAndSign((EdDSAPublicKey) keys.getPublic(), (EdDSAPrivateKey) keys.getPrivate());

                transaction = builder.sendTransaction(driver.handleServerResponse("SELL", metaData, null));
                System.out.println("(*) SELL Transaction sent.. - " + transaction.getId());
                ObjectMapper mapper = new ObjectMapper();
                String transactionJson = mapper.writeValueAsString(transaction); // Serialize to JSON
                int sizeInBytes = transactionJson.getBytes(StandardCharsets.UTF_8).length;
                double sizeInKB = sizeInBytes / 1024.0;

                System.out.printf("SELL Transaction size: %d Bytes (%.3f KB)%n", sizeInBytes, sizeInKB);
                // wait until transaction is commited, or retry every second until 5 seconds
                waitForCommit(driver, transaction); 
        } catch (IOException e) {
            e.printStackTrace();
        }

        return transaction;
    }

    public static Transaction doAcceptReturn(BigchainDBJavaDriver driver, String originalAssetId, String transferTxnId, String inverseId, MetaData metaData, KeyPair keys) throws Exception {

        Transaction transaction = null;
        Map<String, String> assetData = new TreeMap<String, String>();
        
        assetData.put("asset_id", originalAssetId);
        assetData.put("ref1_id", transferTxnId);
        assetData.put("ref2_id", inverseId);
        try {
                FulFill fulfill = new FulFill();
                fulfill.setOutputIndex(0);
                fulfill.setTransactionId(transferTxnId);

                BigchainDbTransactionBuilder.IBuild builder = BigchainDbTransactionBuilder
                        .init()
                        .addInput(null, fulfill, (EdDSAPublicKey) keys.getPublic())
                        .addOutput("1", DriverConstants.SMARTCHAINDB_PUBKEY)
                        .addAssets(assetData, TreeMap.class)
                        .addMetaData(metaData)
                        .operation(Operations.INTEREST)
                        .buildAndSign((EdDSAPublicKey) keys.getPublic(), (EdDSAPrivateKey) keys.getPrivate());

                transaction = builder.sendTransaction(driver.handleServerResponse("ACCEPT_RETURN", metaData, null));
                System.out.println("(*) ACCEPT_RETURN Transaction sent.. - " + transaction.getId());
                ObjectMapper mapper = new ObjectMapper();
                String transactionJson = mapper.writeValueAsString(transaction); // Serialize to JSON
                int sizeInBytes = transactionJson.getBytes(StandardCharsets.UTF_8).length;
                double sizeInKB = sizeInBytes / 1024.0;

                System.out.printf("ACCEPT RETURN Transaction size: %d Bytes (%.3f KB)%n", sizeInBytes, sizeInKB);
                // wait until transaction is commited, or retry every second until 5 seconds
                waitForCommit(driver, transaction); 
        } catch (IOException e) {
            e.printStackTrace();
        }

        return transaction;
    }

    public static void doAccept(BigchainDBJavaDriver driver, String winningBidTxId, String rfqTxId, MetaData metaData, KeyPair keys) throws Exception {

        Map<String, String> assetData = new TreeMap<String, String>();
        assetData.put("rfq_id", rfqTxId);
        assetData.put("winner_bid_id", winningBidTxId);

        try {
            Transaction transaction = BigchainDbTransactionBuilder
                    .init()
                    .addAssets(assetData, TreeMap.class)
                    .addMetaData(metaData)
                    .operation(Operations.ACCEPT)
                    .buildAndSign((EdDSAPublicKey) keys.getPublic(), (EdDSAPrivateKey) keys.getPrivate())
                    .sendTransaction(driver.handleServerResponse("ACCEPT", metaData, null));

                System.out.println("(*) ACCEPT Transaction sent.. - " + transaction.getId());
                // wait until transaction is commited, or retry every second until 5 seconds
                waitForCommit(driver, transaction); 

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Transaction validateTransaction(Map<String, String> assetData, MetaData metaData, KeyPair keys, Operations operation) throws Exception {

        BigchainDbTransactionBuilder.IBuild builder = BigchainDbTransactionBuilder
                .init()
                .addAssets(assetData, TreeMap.class)
                .addMetaData(metaData)
                .operation(operation)
                .buildAndSign((EdDSAPublicKey) keys.getPublic(), (EdDSAPrivateKey) keys.getPrivate());

        return builder.validateTransaction();
    }

    public static void displayPrettyTx(Transaction tx) {
        String msg = "DETAILS OF TX " + tx.getId();
        System.out.println("\n\n================ " + msg + " ===================");
        System.out.println(tx);
        System.out.println("================ END " + msg + " ===================\n\n");
    }

    private static void waitForCommit(BigchainDBJavaDriver driver, Transaction transaction) {
        if (transaction != null) {
            if (driver.COMMIT_TX) {
                TransactionsApi.waitForCommit(transaction.getId());
            }
        }
    }
}
