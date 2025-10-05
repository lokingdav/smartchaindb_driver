package com.bigchaindb.smartchaindb.driver;

import com.bigchaindb.api.TransactionsApi;
import com.bigchaindb.builders.BigchainDbTransactionBuilder;
import com.bigchaindb.constants.Operations;
import com.bigchaindb.model.FulFill;
import com.bigchaindb.model.MetaData;
import com.bigchaindb.model.Transaction;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import com.bigchaindb.util.KeyPairUtils;

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

    /**
     * Creates a BUY_OFFER transaction (FIXED for SHACL compatibility)
     * @param driver BigchainDB driver instance
     * @param paymentAssetId The buyer's payment asset ID (CREATE transaction)
     * @param advertisementId The advertisement transaction ID
     * @param metaData Metadata containing offer details
     * @param buyerKeys Buyer's keypair
     * @return Transaction object
     */
    public static Transaction doBuyOffer(BigchainDBJavaDriver driver, String paymentAssetId, String advertisementId, 
                                         MetaData metaData, KeyPair buyerKeys) throws Exception {
        Transaction transaction = null;
        
        // FIXED: Ensure required metadata fields
        if (!metaData.getMetadata().containsKey("buyer_public_key")) {
            metaData.setMetaData("buyer_public_key", 
                KeyPairUtils.encodePublicKeyInBase58((EdDSAPublicKey) buyerKeys.getPublic()));
        }
        if (!metaData.getMetadata().containsKey("payment_asset_id")) {
            metaData.setMetaData("payment_asset_id", paymentAssetId);
        }
        
        // Validate required fields
        validateBuyOfferMetadata(metaData);
        
        try {
            // SIMPLIFIED: For BUY_OFFER, everything goes in asset.data
            Map<String, Object> assetData = new TreeMap<String, Object>();
            assetData.put("id", paymentAssetId);
            assetData.put("advertisement_id", advertisementId);
            
            // BUY_OFFER should NOT spend an input (it's an announcement, like CREATE)
            BigchainDbTransactionBuilder.IBuild builder = BigchainDbTransactionBuilder
                    .init()
                    .addAssets(assetData, TreeMap.class)  // This creates asset.data with both fields
                    .addMetaData(metaData)
                    .operation(Operations.BUY_OFFER)
                    .buildAndSign((EdDSAPublicKey) buyerKeys.getPublic(), (EdDSAPrivateKey) buyerKeys.getPrivate());

            // Get the transaction  
            transaction = builder.getTransaction();
            
            // Send the transaction
            TransactionsApi.sendTransaction(transaction, driver.handleServerResponse("BUY_OFFER", metaData, null));
            System.out.println("(*) BUY_OFFER Transaction sent.. - " + transaction.getId());
            
            ObjectMapper mapper = new ObjectMapper();
            String transactionJson = mapper.writeValueAsString(transaction);
            int sizeInBytes = transactionJson.getBytes(StandardCharsets.UTF_8).length;
            double sizeInKB = sizeInBytes / 1024.0;
            System.out.printf("BUY_OFFER Transaction size: %d Bytes (%.3f KB)%n", sizeInBytes, sizeInKB);
            
            waitForCommit(driver, transaction); 
        } catch (IOException e) {
            e.printStackTrace();
        }

        return transaction;
    }


    public static Transaction doReturnSell(BigchainDBJavaDriver driver, String sellTransactionId, MetaData metaData, KeyPair buyerKeys) throws Exception {

        Transaction transaction = null;
        Map<String, Object> assetData = new TreeMap<>();
        assetData.put("id", sellTransactionId);  // Asset ID for REQUEST_RETURN
        assetData.put("sell_transaction_id", sellTransactionId);  // Reference to the SELL transaction
    
        try {
            // Create REQUEST_RETURN transaction
            BigchainDbTransactionBuilder.IBuild builder = BigchainDbTransactionBuilder
                    .init()
                    .addInput(null, null, (EdDSAPublicKey) buyerKeys.getPublic())  // One input for authorization
                    .addAssets(assetData, TreeMap.class)
                    .addMetaData(metaData)
                    .operation(Operations.REQUEST_RETURN)  // Use correct operation
                    .buildAndSign((EdDSAPublicKey) buyerKeys.getPublic(), (EdDSAPrivateKey) buyerKeys.getPrivate());
    
            // Send the REQUEST_RETURN transaction
            transaction = builder.sendTransaction(driver.handleServerResponse("REQUEST_RETURN", metaData, null));
            System.out.println("(*) REQUEST_RETURN Transaction sent.. - " + transaction.getId());
            ObjectMapper mapper = new ObjectMapper();
                String transactionJson = mapper.writeValueAsString(transaction); // Serialize to JSON
                int sizeInBytes = transactionJson.getBytes(StandardCharsets.UTF_8).length;
                double sizeInKB = sizeInBytes / 1024.0;

                System.out.printf("REQUEST_RETURN Transaction size: %d Bytes (%.3f KB)%n", sizeInBytes, sizeInKB);
            // wait until transaction is commited, or retry every second until 5 seconds
            waitForCommit(driver, transaction); 
        } catch (IOException e) {
            e.printStackTrace();
        }
    
        return transaction;
    }
    


    /**
     * Creates an ADVERTISEMENT transaction (FIXED for SHACL compatibility)
     * @param driver BigchainDB driver instance
     * @param assetId The CREATE transaction ID of the asset being advertised
     * @param metaData Metadata containing advertisement details
     * @param keys Advertiser's keypair
     * @return Transaction object
     */
    public static Transaction doAdvertisement(BigchainDBJavaDriver driver, String assetId, MetaData metaData, KeyPair keys) throws Exception {
        Transaction transaction = null;
        
        // FIXED: Ensure required metadata fields
        if (!metaData.getMetadata().containsKey("status")) {
            metaData.setMetaData("status", "OPEN");
        }
        if (!metaData.getMetadata().containsKey("advertiser_public_key")) {
            metaData.setMetaData("advertiser_public_key", 
                KeyPairUtils.encodePublicKeyInBase58((EdDSAPublicKey) keys.getPublic()));
        }
        // Validate required fields
        validateAdvertisementMetadata(metaData);
        
        try {
            // FIXED: For ADVERTISEMENT, asset should just be the ID string, not a map
            // This will create: "asset": {"id": "..."}
            BigchainDbTransactionBuilder.IBuild builder = BigchainDbTransactionBuilder
                    .init()
                    .addAssets(assetId, String.class)  // Pass ID directly, not as a map
                    .addMetaData(metaData)
                    .operation(Operations.ADVERTISEMENT)  // FIXED: Changed from ADV
                    .buildAndSign((EdDSAPublicKey) keys.getPublic(), (EdDSAPrivateKey) keys.getPrivate());

            transaction = builder.sendTransaction(driver.handleServerResponse("ADVERTISEMENT", metaData, null));
            System.out.println("(*) ADVERTISEMENT Transaction sent.. - " + transaction.getId());
            
            ObjectMapper mapper = new ObjectMapper();
            String transactionJson = mapper.writeValueAsString(transaction);
            int sizeInBytes = transactionJson.getBytes(StandardCharsets.UTF_8).length;
            double sizeInKB = sizeInBytes / 1024.0;
            System.out.printf("ADVERTISEMENT Transaction size: %d Bytes (%.3f KB)%n", sizeInBytes, sizeInKB);
            
            waitForCommit(driver, transaction); 
        } catch (IOException e) {
            e.printStackTrace();
        }

        return transaction;
    }
    
    // Keep old method for backward compatibility
    @Deprecated
    public static Transaction doAdv(BigchainDBJavaDriver driver, String txId, MetaData metaData, KeyPair keys) throws Exception {
        return doAdvertisement(driver, txId, metaData, keys);
    }

    public static Transaction updateAdv(BigchainDBJavaDriver driver, String advertisementId, MetaData metaData, KeyPair advertiserKeys) throws Exception {
		Transaction transaction = null;
		Map<String, Object> assetData = new TreeMap<>();
		assetData.put("id", advertisementId);

		// Ensure updater_public_key present
		if (!metaData.getMetadata().containsKey("updater_public_key")) {
			metaData.setMetaData("updater_public_key",
				KeyPairUtils.encodePublicKeyInBase58((EdDSAPublicKey) advertiserKeys.getPublic()));
		}

		try {
			BigchainDbTransactionBuilder.IBuild builder = BigchainDbTransactionBuilder
					.init()
					.addInput(null, null, (EdDSAPublicKey) advertiserKeys.getPublic())
					.addAssets(assetData, TreeMap.class) // creates asset.data
					.addMetaData(metaData)
					.operation(Operations.UPDATE_ADV)
					.buildAndSign((EdDSAPublicKey) advertiserKeys.getPublic(), (EdDSAPrivateKey) advertiserKeys.getPrivate());

			transaction = builder.getTransaction();
			TransactionsApi.sendTransaction(transaction, driver.handleServerResponse("UPDATE_ADV", metaData, null));
			System.out.println("(*) UPDATE_ADV Transaction sent.. - " + transaction.getId());
			waitForCommit(driver, transaction);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return transaction;
	}

    /**
     * Creates a SELL transaction (FIXED for SHACL compatibility)
     * @param driver BigchainDB driver instance
     * @param assetId The CREATE transaction ID of the asset
     * @param buyOfferId The buy offer transaction ID
     * @param metaData Metadata containing sale details
     * @param sellerKeys Seller's keypair
     * @param buyerKeys Buyer's keypair (for receiving asset)
     * @return Transaction object
     */
    public static Transaction doSell(BigchainDBJavaDriver driver, String assetId, String buyOfferId, 
                                     MetaData metaData, KeyPair sellerKeys, KeyPair buyerKeys) throws Exception {
        Transaction transaction = null;
        
        // FIXED: Ensure required metadata fields
        if (!metaData.getMetadata().containsKey("seller_public_key")) {
            metaData.setMetaData("seller_public_key", 
                KeyPairUtils.encodePublicKeyInBase58((EdDSAPublicKey) sellerKeys.getPublic()));
        }
        if (!metaData.getMetadata().containsKey("buyer_public_key")) {
            metaData.setMetaData("buyer_public_key", 
                KeyPairUtils.encodePublicKeyInBase58((EdDSAPublicKey) buyerKeys.getPublic()));
        }
        
        // Validate required fields
        validateSellMetadata(metaData);
        
        try {
            // SIMPLIFIED: For SELL under new schema, everything goes in asset.data
            Map<String, Object> assetData = new TreeMap<>();
            assetData.put("id", assetId);
            assetData.put("buy_offer_id", buyOfferId);
            
            FulFill fulfill = new FulFill();
            fulfill.setOutputIndex(0);
            fulfill.setTransactionId(assetId);  // Spend the seller's asset

            String saleAmount = String.valueOf(metaData.getMetadata().get("sale_amount"));
            BigchainDbTransactionBuilder.IBuild builder = BigchainDbTransactionBuilder
                    .init()
                    .addInput(null, fulfill, (EdDSAPublicKey) sellerKeys.getPublic())
                    .addOutput("1", (EdDSAPublicKey) buyerKeys.getPublic())  // Asset -> buyer
                    .addOutput(saleAmount, (EdDSAPublicKey) sellerKeys.getPublic())  // Payment -> seller
                    .addAssets(assetData, TreeMap.class)  // Creates asset.data with id and buy_offer_id
                    .addMetaData(metaData)
                    .operation(Operations.SELL)
                    .buildAndSign((EdDSAPublicKey) sellerKeys.getPublic(), (EdDSAPrivateKey) sellerKeys.getPrivate());

            // Get the transaction
            transaction = builder.getTransaction();
            
            // Step 4: Send the modified transaction
            TransactionsApi.sendTransaction(transaction, driver.handleServerResponse("SELL", metaData, null));
            System.out.println("(*) SELL Transaction sent.. - " + transaction.getId());
            
            ObjectMapper mapper = new ObjectMapper();
            String transactionJson = mapper.writeValueAsString(transaction);
            int sizeInBytes = transactionJson.getBytes(StandardCharsets.UTF_8).length;
            double sizeInKB = sizeInBytes / 1024.0;
            System.out.printf("SELL Transaction size: %d Bytes (%.3f KB)%n", sizeInBytes, sizeInKB);
            
            waitForCommit(driver, transaction); 
        } catch (IOException e) {
            e.printStackTrace();
        }

        return transaction;
    }

    public static Transaction doSellerAcceptReturn(BigchainDBJavaDriver driver, String requestReturnId, MetaData metaData, KeyPair keys) throws Exception {

        Transaction transaction = null;
        Map<String, Object> assetData = new TreeMap<>();
        
        assetData.put("id", requestReturnId);  // Asset ID for SELLER_ACCEPT_RETURN
        assetData.put("request_return_id", requestReturnId);  // Reference to the REQUEST_RETURN transaction
        
        try {
            // Create SELLER_ACCEPT_RETURN transaction
            BigchainDbTransactionBuilder.IBuild builder = BigchainDbTransactionBuilder
                    .init()
                    .addInput(null, null, (EdDSAPublicKey) keys.getPublic())  // One input for authorization
                    .addAssets(assetData, TreeMap.class)
                    .addMetaData(metaData)
                    .operation(Operations.SELLER_ACCEPT_RETURN)  // Use correct operation
                    .buildAndSign((EdDSAPublicKey) keys.getPublic(), (EdDSAPrivateKey) keys.getPrivate());

            transaction = builder.sendTransaction(driver.handleServerResponse("SELLER_ACCEPT_RETURN", metaData, null));
            System.out.println("(*) SELLER_ACCEPT_RETURN Transaction sent.. - " + transaction.getId());
            ObjectMapper mapper = new ObjectMapper();
            String transactionJson = mapper.writeValueAsString(transaction); // Serialize to JSON
            int sizeInBytes = transactionJson.getBytes(StandardCharsets.UTF_8).length;
            double sizeInKB = sizeInBytes / 1024.0;

            System.out.printf("SELLER_ACCEPT_RETURN Transaction size: %d Bytes (%.3f KB)%n", sizeInBytes, sizeInKB);
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
    
    // ===== Validation Methods for SHACL Compatibility =====
    
    /**
     * Validates that advertisement metadata contains all required fields
     */
    private static void validateAdvertisementMetadata(MetaData metaData) throws IllegalArgumentException {
        String[] required = {"status", "advertiser_public_key", "price", "description", 
                            "category", "condition", "expiry_date", "contact_info", "location"};
        
        for (String field : required) {
            if (!metaData.getMetadata().containsKey(field)) {
                throw new IllegalArgumentException("ADVERTISEMENT metadata missing required field: " + field);
            }
        }
    }
    
    /**
     * Validates that buy offer metadata contains all required fields
     */
    private static void validateBuyOfferMetadata(MetaData metaData) throws IllegalArgumentException {
        String[] required = {"buyer_public_key", "offer_amount", "offer_expiry", "escrow_public_key"};
        
        for (String field : required) {
            if (!metaData.getMetadata().containsKey(field)) {
                throw new IllegalArgumentException("BUY_OFFER metadata missing required field: " + field);
            }
        }
    }
    
    /**
     * Validates that sell metadata contains all required fields
     */
    private static void validateSellMetadata(MetaData metaData) throws IllegalArgumentException {
        String[] required = {"seller_public_key", "buyer_public_key", "sale_amount", "sale_currency"};
        
        for (String field : required) {
            if (!metaData.getMetadata().containsKey(field)) {
                throw new IllegalArgumentException("SELL metadata missing required field: " + field);
            }
        }
    }
}
