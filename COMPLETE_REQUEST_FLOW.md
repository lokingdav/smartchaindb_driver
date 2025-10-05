# Complete Driver Request Flow

**How the Java Driver Sends Transactions to BigchainDB Server**

---

## 📋 Table of Contents

1. [High-Level Flow](#high-level-flow)
2. [Detailed Step-by-Step Execution](#detailed-step-by-step-execution)
3. [Code Flow with Examples](#code-flow-with-examples)
4. [HTTP Request Details](#http-request-details)
5. [Complete Call Stack](#complete-call-stack)
6. [What Changed in This Version](#what-changed-in-this-version)
7. [Required Metadata per Operation](#required-metadata-per-operation)
8. [Invalid Scenarios and Expected Behavior](#invalid-scenarios-and-expected-behavior)

---

## 🎯 High-Level Flow

```
User Code (main)
    ↓
Create Tasks (Seller Assets, Advertisements, Buy Offers, Sells)
    ↓
Simulation Helper Methods (createAdv, createBuyOffer, createSell)
    ↓
Transaction Builder (Transactions.doAdvertisement, doBuyOffer, doSell)
    ↓
Build & Sign Transaction
    ↓
Send HTTP POST to Server
    ↓
Server Response Handler (Callback)
    ↓
Wait for Commitment (Poll GET endpoint)
```

---

## 🔄 Detailed Step-by-Step Execution

### **Phase 1: Initialization**

**File:** `BigchainDBJavaDriver.java` → `main()`

```java
// Step 1: Configure connection
setConfig();  // Sets baseUrl to http://127.0.0.1:9984/

// Step 2: Generate keypairs
KeyPair sellerKeyPair = getKeys();
KeyPair buyerKeyPair = getKeys();

// Step 3: Create driver instance
BigchainDBJavaDriver driver = new BigchainDBJavaDriver();

// Step 4: Set transaction counts
int validAssetCount = 3;  // Number of transactions to create
```

---

### **Phase 2: Task Creation & Execution**

**File:** `BigchainDBJavaDriver.java` → `main()` (continued)

The driver creates tasks in a sequential pipeline:

```java
// Step 1: Create Seller Assets (CREATE transactions)
executeAndProcess(
    createSellerAssetsTasks(3, driver, sellerKeyPair),
    "Seller Asset",
    sellerCreateIds  // Stores transaction IDs
)

// Step 2: Short delay (~10 seconds)
.thenCompose(v -> delay(10 seconds))

// Step 3: Create Advertisements
.thenCompose(v -> executeAndProcess(
    createAdvertisementsTasks(sellerCreateIds, driver, sellerKeyPair),
    "Advertisement",
    advIds
))

// Step 4: Short delay (~10 seconds)
.thenCompose(v -> delay(10 seconds))

// Step 5: Create Buy Offers
.thenCompose(v -> executeAndProcess(
    createBuyOfferTasks(advIds, buyerCreateIds, driver, buyerKeyPair),
    "Buy Offer",
    buyOfferIds
))

// Step 6: Short delay (~10 seconds)
.thenCompose(v -> delay(10 seconds))

// Step 7: Create Sell Transactions
.thenCompose(v -> executeAndProcess(
    createSellTasks(sellerCreateIds, advIds, buyOfferIds, driver, sellerKeyPair),
    "Sell Transaction",
    sellIds
))

// Step 8: Short delay (~10 seconds)
.thenCompose(v -> delay(10 seconds))

// Step 9: Create Invalid Advertisements (negative tests)
.thenCompose(v -> executeAndProcess(
    createInvalidAdvertisementsTasks(sellerCreateIds, driver, sellerKeyPair, invalidAssetCount),
    "Invalid Advertisement",
    invalidAdvIds
))

// Step 10: Create Invalid Buy Offers (against closed ads)
.thenCompose(v -> executeAndProcess(
    createInvalidBuyOfferTasks(advIds, buyerCreateIds, driver, buyerKeyPair, sellerKeyPair, sellerCreateIds, invalidAssetCount),
    "invalid Buy Offer",
    invalidBuyOfferIds
))

// Step 11: Create Invalid Sells (using invalid buy offers)
.thenCompose(v -> executeAndProcess(
    createInvalidSellTasks(sellerCreateIds, advIds, invalidBuyOfferIds, driver, sellerKeyPair, invalidAssetCount),
    "invalid Sell Transaction",
    invalidSellIds
))

// Step 12: Return handling (inverse + accept return)
.thenCompose(v -> executeAndProcess(
    createReturnTasks(sellIds, buyOfferIds, driver, sellerKeyPair, buyerKeyPair, validAssetCount),
    "Return Handling",
    null
))
```

---

### **Phase 3: Transaction Creation (Example: ADVERTISEMENT)**

#### **3.1: Task Creation**

**File:** `BigchainDBJavaDriver.java` → `createAdvertisementsTasks()`

```java
private static List<Supplier<String>> createAdvertisementsTasks(
    List<String> sellerCreateIds,
    BigchainDBJavaDriver driver,
    KeyPair sellerKeyPair
) {
    return createTasks(sellerCreateIds.size(), "Advertisement", i -> {
        try {
            String createId = sellerCreateIds.get(i);  // Get asset ID
            
            // Call Simulation helper
            Transaction advTransaction = Simulation.createAdv(
                driver, 
                sellerKeyPair, 
                createId, 
                "Open"  // Status (will be uppercased to "OPEN")
            );
            
            if (advTransaction != null && advTransaction.getId() != null) {
                println("Advertisement " + (i + 1) + " Created: " + advTransaction.getId());
                transactionCount.incrementAndGet();
                return advTransaction.getId();
            } else {
                printerr("Failed to create Advertisement " + (i + 1));
                return null;
            }
        } catch (Exception e) {
            printerr("Exception creating Advertisement: " + e.getMessage());
            return null;
        }
    });
}
```

#### **3.2: Simulation Helper**

**File:** `Simulation.java` → `createAdv()`

```java
public static Transaction createAdv(
    BigchainDBJavaDriver driver, 
    KeyPair keys, 
    String createId, 
    String status
) {
    Transaction adv = null;

    try {
        // Create metadata object
        MetaData metaData2 = new MetaData();
        metaData2.setMetaData("kafkaInTimestamp", LocalDateTime.now().toString());
        metaData2.setMetaData("requestCreationTimestamp", 
            LocalDateTime.now(Clock.systemUTC()).format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
            ));
        
        // Add required SHACL fields
        metaData2.setMetaData("status", status.toUpperCase());  // "OPEN"
        metaData2.setMetaData("advertiser_public_key", 
            KeyPairUtils.encodePublicKeyInBase58((EdDSAPublicKey) keys.getPublic()));
        metaData2.setMetaData("price", "5000.00");
        metaData2.setMetaData("description", "Industrial asset for sale");
        metaData2.setMetaData("category", "Manufacturing");
        metaData2.setMetaData("condition", "Used");
        metaData2.setMetaData("expiry_date", 
            LocalDateTime.now(Clock.systemUTC()).plusDays(30).toString());
        metaData2.setMetaData("contact_info", "seller@example.com");
        metaData2.setMetaData("location", "Factory District");
        
        // Call Transaction builder
        adv = Transactions.doAdvertisement(driver, createId, metaData2, keys);
    } catch (Exception e) {
        e.printStackTrace();
    }

    return adv;
}
```

#### **3.3: Transaction Builder**

**File:** `Transactions.java` → `doAdvertisement()`

```java
public static Transaction doAdvertisement(
    BigchainDBJavaDriver driver, 
    String assetId, 
    MetaData metaData, 
    KeyPair keys
) throws Exception {
    Transaction transaction = null;
    
    // Ensure required metadata fields (double-check)
    if (!metaData.getMetadata().containsKey("status")) {
        metaData.setMetaData("status", "OPEN");
    }
    if (!metaData.getMetadata().containsKey("advertiser_public_key")) {
        metaData.setMetaData("advertiser_public_key", 
            KeyPairUtils.encodePublicKeyInBase58((EdDSAPublicKey) keys.getPublic()));
    }
    
    // Validate all required fields are present
    validateAdvertisementMetadata(metaData);
    
    try {
        // Build transaction
        BigchainDbTransactionBuilder.IBuild builder = BigchainDbTransactionBuilder
                .init()
                .addAssets(assetId, String.class)  // Asset ID only
                .addMetaData(metaData)
                .operation(Operations.ADVERTISEMENT)
                .buildAndSign(
                    (EdDSAPublicKey) keys.getPublic(), 
                    (EdDSAPrivateKey) keys.getPrivate()
                );

        // Send transaction with callback
        transaction = builder.sendTransaction(
            driver.handleServerResponse("ADVERTISEMENT", metaData, null)
        );
        
        System.out.println("(*) ADVERTISEMENT Transaction sent.. - " + transaction.getId());
        
        // Log transaction size
        ObjectMapper mapper = new ObjectMapper();
        String transactionJson = mapper.writeValueAsString(transaction);
        int sizeInBytes = transactionJson.getBytes(StandardCharsets.UTF_8).length;
        double sizeInKB = sizeInBytes / 1024.0;
        System.out.printf("ADVERTISEMENT Transaction size: %d Bytes (%.3f KB)%n", 
            sizeInBytes, sizeInKB);
        
        // Wait for commitment
        waitForCommit(driver, transaction); 
    } catch (IOException e) {
        e.printStackTrace();
    }

    return transaction;
}
```

---

### **Phase 4: Building the Transaction**

```java
// BigchainDbTransactionBuilder
// - build(publicKey): assembles inputs/outputs and basic fields
// - sign(privateKey): signs the preimage, sets fulfillment and final id
// - buildAndSign(pub, priv): build + sign, returning a sender that can send/validate
```

---

### **Phase 5: Sending HTTP POST Request**

#### **5.1: Send Transaction**

**File:** `BigchainDbTransactionBuilder.java` → `sendTransaction()`

```java
@Override
public Transaction sendTransaction(GenericCallback callback) throws TimeoutException {
    // Check connection
    if (!BigChainDBGlobals.isConnected()) {
        configBuilder.processConnectionFailure(BigChainDBGlobals.getCurrentNode());
        configBuilder.configureNodeToConnect();
    }
    
    // Send transaction via API
    TransactionsApi.sendTransaction(this.transaction, callback);
    
    configBuilder.processConnectionSuccess(BigChainDBGlobals.getCurrentNode());
    
    return this.transaction;  // Note: Missing in original code
}
```

#### **5.2: Transactions API**

**File:** `TransactionsApi.java` → `sendTransaction()`

```java
public static void sendTransaction(Transaction transaction, final GenericCallback callback) {
    log.debug("sendTransaction Call :" + transaction);
    
    // Create JSON request body
    RequestBody body = RequestBody.create(JSON, transaction.toString());
    
    // Send POST request
    NetworkUtils.sendPostRequest(
        BigChainDBGlobals.getBaseUrl() + BigchainDbApi.TRANSACTIONS, 
        body, 
        callback
    );
}
```

**Where:**
- `BigChainDBGlobals.getBaseUrl()` = `"http://127.0.0.1:9984/"`
- `BigchainDbApi.TRANSACTIONS` = `"/api/v1/transactions"`
- **Full URL:** `http://127.0.0.1:9984/api/v1/transactions`

#### **5.3: Network Utils (HTTP Client)**

**File:** `NetworkUtils.java` → `sendPostRequest()`

```java
public static void sendPostRequest(String url, RequestBody body, final GenericCallback callback) {
    // Create HTTP request
    Request request = new Request.Builder()
        .url(url)
        .post(body)
        .build();

    // Send asynchronously with OkHttp
    BigChainDBGlobals.getHttpClient().newCall(request).enqueue(new Callback() {
        @Override
        public void onFailure(Call call, IOException e) {
            BigChainDBGlobals.setConnected(false);
        }

        @Override
        public void onResponse(Call call, Response response) {
            // Handle response codes
            if (response.code() == 202) {
                callback.pushedSuccessfully(response);
            } else if (response.code() == 400) {
                callback.transactionMalformed(response);
            } else {
                callback.otherError(response);
            }
            
            BigChainDBGlobals.setConnected(true);
            response.close();
        }
    });
}
```

---

### **Phase 6: Response Handling**

**File:** `BigchainDBJavaDriver.java` → `handleServerResponse()`

```java
public GenericCallback handleServerResponse(String operation, MetaData metaData, String txId) {
    return new GenericCallback() {
        public void transactionMalformed(Response response) {
            println("Malformed: " + response.message());  // HTTP 400
        }

        public void pushedSuccessfully(Response response) {
            println("Transaction successfully posted");  // HTTP 202
        }

        public void otherError(Response response) {
            println("Other error: " + response.message());  // Other codes
        }
    };
}
```

---

### **Phase 7: Wait for Commitment**

**File:** `Transactions.java` → `waitForCommit()`

```java
private static void waitForCommit(BigchainDBJavaDriver driver, Transaction transaction) {
    if (transaction != null) {
        if (driver.COMMIT_TX) {
            // Poll GET /api/v1/transactions/{id} until committed
            TransactionsApi.waitForCommit(transaction.getId());
        }
    }
}
```

**File:** `TransactionsApi.java` → `waitForCommit()`

```java
public static Transaction waitForCommit(String txId) {
    // Poll until transaction is committed
    while (true) {
        try {
            Transaction tx = getTransactionById(txId);
            if (tx != null) {
                return tx;  // Found = committed
            }
            Thread.sleep(1000);  // Wait 1 second before retry
        } catch (Exception e) {
            // Continue polling
        }
    }
}
```

---

## 📡 HTTP Request Details

### **POST Request Structure**

```http
POST /api/v1/transactions HTTP/1.1
Host: 127.0.0.1:9984
Content-Type: application/json

{
  "id": "5ab64ebabc3bf364193c9bbbda30e7b831e3d640a0f97bea8972ccc700db0fa5",
  "version": "2.0",
  "operation": "ADVERTISEMENT",
  "asset": {
    "id": "c89445624eeb03047e8486d75a0be5d250330561335ed02a256693fb7c03b557"
  },
  "inputs": [
    {
      "owners_before": ["4NTScQW5KALVhZLTvNDuVuzBA62Mhkiqy4mANKSNNWUt"],
      "fulfills": null,
      "fulfillment": "pGSAIDISJ8QYzJB0J8Obwx8Lf65cqRqzUVlQO1uEQ2MMg9GdgUA..."
    }
  ],
  "outputs": [
    {
      "public_keys": ["4NTScQW5KALVhZLTvNDuVuzBA62Mhkiqy4mANKSNNWUt"],
      "condition": {
        "details": {
          "type": "ed25519-sha-256",
          "public_key": "4NTScQW5KALVhZLTvNDuVuzBA62Mhkiqy4mANKSNNWUt"
        },
        "uri": "ni:///sha-256;VXOmfDhWop4A1jHHcDXRuHW9LrA9hPDJMprCBqQzYBQ?fpt=ed25519-sha-256&cost=131072"
      },
      "amount": "1"
    }
  ],
  "metadata": {
    "status": "OPEN",
    "advertiser_public_key": "4NTScQW5KALVhZLTvNDuVuzBA62Mhkiqy4mANKSNNWUt",
    "price": "5000.00",
    "description": "Industrial asset for sale",
    "category": "Manufacturing",
    "condition": "Used",
    "expiry_date": "2025-11-04T06:25:18.412",
    "contact_info": "seller@example.com",
    "location": "Factory District",
    "kafkaInTimestamp": "2025-10-05T02:25:18.412",
    "requestCreationTimestamp": "2025-10-05T06:25:18.412000"
  }
}
```

### **Response Codes**

| Code | Meaning | Callback Method |
|------|---------|-----------------|
| **202** | Accepted - Transaction queued for processing | `pushedSuccessfully()` |
| **400** | Bad Request - Invalid transaction schema | `transactionMalformed()` |
| **404** | Not Found - Transaction doesn't exist (GET requests) | `otherError()` |
| **500** | Internal Server Error | `otherError()` |

---

## 📚 Complete Call Stack

### **For ADVERTISEMENT Transaction**

```
1. BigchainDBJavaDriver.main()
   ↓
2. executeAndProcess(createAdvertisementsTasks(...))
   ↓
3. createAdvertisementsTasks(sellerCreateIds, driver, sellerKeyPair)
   ↓
4. Simulation.createAdv(driver, keys, createId, "Open")
   ↓
5. Transactions.doAdvertisement(driver, assetId, metaData, keys)
   ↓
6. BigchainDbTransactionBuilder.init()
       .addAssets(assetId, String.class)
       .addMetaData(metaData)
       .operation(Operations.ADVERTISEMENT)
       .buildAndSign(publicKey, privateKey)
   ↓
7. builder.sendTransaction(callback)
   ↓
8. TransactionsApi.sendTransaction(transaction, callback)
   ↓
9. NetworkUtils.sendPostRequest(url, body, callback)
   ↓
10. OkHttpClient.newCall(request).enqueue(callback)
    ↓
    [HTTP POST to http://127.0.0.1:9984/api/v1/transactions]
    ↓
11. Server receives request
    ↓
12. Server validates with SHACL
    ↓
13. Server responds (202, 400, etc.)
    ↓
14. callback.pushedSuccessfully() or callback.transactionMalformed()
    ↓
15. waitForCommit(driver, transaction)
    ↓
16. Poll GET /api/v1/transactions/{id} until found
```

---

## 🧭 What Changed in This Version

- Standardized operation names and support in the builder:
  - `ADVERTISEMENT` (replaces legacy `ADV`)
  - `BUY_OFFER` (replaces legacy `BUYOFFER`)
  - `SELL`
- Commit-aware sequencing between dependent phases:
  - Waits for `ADVERTISEMENT` before posting `BUY_OFFER`
  - Waits for `BUY_OFFER` before posting `SELL`
- Short, consistent inter-phase delays (~10s) instead of long sleeps.
- Asset handling aligned with SHACL expectations:
  - `ADVERTISEMENT`: `asset.id` points to the seller's CREATE tx id
  - `BUY_OFFER`: `asset.id` points to buyer payment asset; nested `asset.data.advertisement_id`
  - `SELL`: spends seller's asset input; `asset.id` = original asset id, nested `asset.data.buy_offer_id`
- Strong validation of required metadata per operation with clear failures (400) when missing.
- Negative paths added: invalid advertisements, invalid buy offers (against closed ads), invalid sells (using invalid buy offers).
- Return flow added (inverse + accept-return) after sells to test reversals.

---

## ✅ Required Metadata per Operation

These are validated in `Transactions.java` and must be present at send-time.

- ADVERTISEMENT
  - `status` (OPEN | LOCKED | CLOSED)
  - `advertiser_public_key`
  - `price`
  - `description`
  - `category`
  - `condition`
  - `expiry_date`
  - `contact_info`
  - `location`

- BUY_OFFER
  - `buyer_public_key`
  - `offer_amount`
  - `offer_expiry`
  - `escrow_public_key`
  - Also pass: buyer `payment_asset_id` and the `advertisement_id` (via asset fields)

- SELL
  - `seller_public_key`
  - `buyer_public_key`
  - `sale_amount`
  - `sale_currency`
  - Also references: `buy_offer_id` (via asset fields) and spends the seller's asset input

---

## 🚫 Invalid Scenarios and Expected Behavior

- Invalid Advertisement
  - Example: posting multiple ads against policy, or otherwise malformed
  - Expected: server rejects with 400; no commit

- Invalid Buy Offer
  - Close an advertisement first, then attempt a BUY_OFFER against the closed ad
  - Expected: server rejects with 400; no commit

- Invalid Sell
  - Attempt SELL using an invalid buy offer id
  - Expected: server rejects with 400; no commit

Logging
- On rejection, you will see the callback print “Malformed …” or “Other error …” and no “Transaction … is committed.” line.

## 🔑 Key Configuration Points

### **Connection Setup**

**File:** `BigchainDBJavaDriver.java`

```java
public static void setConfig() {
    BigchainDbConfigBuilder.baseUrl("http://127.0.0.1:9984/").setup();
}
```

### **HTTP Client**

**File:** `BigChainDBGlobals.java` (generated during setup)

```java
private static OkHttpClient httpClient = new OkHttpClient.Builder()
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build();
```

### **API Endpoints**

**File:** `BigchainDbApi.java`

```java
public class BigchainDbApi {
    public static final String TRANSACTIONS = "/api/v1/transactions";
    public static final String OUTPUTS = "/api/v1/outputs";
    public static final String ASSETS = "/api/v1/assets";
    public static final String BLOCKS = "/api/v1/blocks";
    public static final String METADATA = "/api/v1/metadata";
    public static final String VALIDATORS = "/api/v1/validators";
}
```

---

## 🎯 Summary

**Transaction Flow in 10 Steps:**

1. ✅ **Configure** connection to `http://127.0.0.1:9984/`
2. ✅ **Generate** EdDSA keypairs for signing
3. ✅ **Create** metadata with all required SHACL fields
4. ✅ **Build** transaction structure (asset, metadata, operation)
5. ✅ **Sign** transaction with private key
6. ✅ **Serialize** transaction to JSON
7. ✅ **POST** JSON to `/api/v1/transactions`
8. ✅ **Receive** response (202 = success, 400 = validation error)
9. ✅ **Poll** GET `/api/v1/transactions/{id}` until committed
10. ✅ **Return** transaction ID to caller

**Libraries Used:**
- **OkHttp 3.9.0** - HTTP client for network requests
- **EdDSA 0.2.0** - Cryptographic signatures
- **Gson 2.8.2** - JSON serialization
- **Cryptoconditions 1.0** - Transaction conditions

**Key Classes:**
- `BigchainDBJavaDriver` - Main orchestrator
- `Simulation` - Helper methods for transactions
- `Transactions` - Transaction builders
- `BigchainDbTransactionBuilder` - Low-level transaction construction
- `TransactionsApi` - HTTP API wrapper
- `NetworkUtils` - HTTP request sender

---

*This complete flow ensures SHACL-compatible transactions are sent to the BigchainDB server with proper validation!* 🚀

