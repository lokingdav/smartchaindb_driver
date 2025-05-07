package com.bigchaindb.smartchaindb.driver;

import com.bigchaindb.api.TransactionsApi;
import com.bigchaindb.model.MetaData;
import com.bigchaindb.model.Transaction;
import org.apache.kafka.clients.producer.Producer;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.time.format.DateTimeFormatter;

public class Simulation {

//    private static final Producer<String, String> producer = ProducerDriver.createProducer("requestor-"
//            + LocalDateTime.now().toString());

    public static void createPreRequest(BigchainDBJavaDriver driver, KeyPair keys, FileWriter prereqFile) {
        final int MAX_REQUEST_COUNT = 2000, MAX_PRODUCT_COUNT_PER_RFQ = 2;

        for (int l = 0; l < MAX_REQUEST_COUNT; l++) {
            MetaData reqMetaData = getRandomMetadata(MAX_PRODUCT_COUNT_PER_RFQ);
            reqMetaData.setMetaData("requestCreationTimestamp", LocalDateTime.now(Clock.systemUTC()).toString());

            try {
                String txId = Transactions.doPreRequest(driver, reqMetaData, keys, null, null, null);
                prereqFile.write(txId + "\n");
                prereqFile.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Author: Sen Quio
    public static void createPreRequestG(BigchainDBJavaDriver driver, String skey, String pkey, FileWriter prereqFile,
                                         FileWriter personal_prereqFile, int security_parameter_byte, String payload)
            throws IOException, InterruptedException {
        final int MAX_REQUEST_COUNT = 1, MAX_PRODUCT_COUNT_PER_RFQ = 1;

        for (int l = 0; l < MAX_REQUEST_COUNT; l++) {
            MetaData reqMetaData = getRandomMetadata(MAX_PRODUCT_COUNT_PER_RFQ);
            reqMetaData.setMetaData("requestCreationTimestamp", LocalDateTime.now(Clock.systemUTC()).toString());

            SecureRandom random = new SecureRandom();
            byte[] bytes = new byte[security_parameter_byte]; // 128 bits are converted to 16 bytes;
            random.nextBytes(bytes);
            String preimage = String.valueOf(bytes);

            byte[] bytes2 = new byte[security_parameter_byte]; // 128 bits are converted to 16 bytes;
            random.nextBytes(bytes2);
            String rid = String.valueOf(bytes2);

            String test = rid + preimage;
            reqMetaData.setMetaData("hash", test.hashCode());
            reqMetaData.setMetaData("RID", rid);
            try {
                String txId = Transactions.doPreRequest(driver, reqMetaData, null, skey, pkey, payload);
                prereqFile.write(txId + "\n");
                prereqFile.flush();
                personal_prereqFile.write(txId + "|" + rid + "|" + preimage + "\n");
                personal_prereqFile.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void createInterest(BigchainDBJavaDriver driver, KeyPair keys, FileWriter createFile) throws Exception {
        List<String> allPreReqs = readFile("interest.txt");
        HashSet<String> hset = Capabilities.getAllRequestTopics();
        String[] array = new String[hset.size()];
        hset.toArray(array);

        try {
            Map<String, Object> cre_assetData = new TreeMap<String, Object>() {{
                put("capability", array);
                put("machineIdentifier", "X100" + LocalDateTime.now().toString());
            }};

            List<String> createTxs = new ArrayList<>();
            for (int i = 0; i < 2000; i++) {
                try {
                    MetaData cre_metaData = new MetaData();
                    cre_metaData.setMetaData("requestCreationTimestamp", LocalDateTime.now(Clock.systemUTC()).toString());

                    String txId_cre = Transactions.doCreate(driver, cre_assetData, cre_metaData, keys);

                    createTxs.add(txId_cre);
                    createFile.write(txId_cre + "\n");
                    createFile.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            for (int i = 0; i < allPreReqs.size(); i++) {
                try {
                    MetaData metaData = new MetaData();
                    metaData.setMetaData("requestCreationTimestamp", LocalDateTime.now(Clock.systemUTC()).toString());

                    Transactions.doInterest(driver, createTxs.get(i), allPreReqs.get(i), metaData, keys);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Transaction createRFQ(BigchainDBJavaDriver driver, KeyPair keys, FileWriter rfqFile) {
        final int MAX_REQUEST_COUNT = 1;
        final int MAX_PRODUCT_COUNT_PER_RFQ = 4;
        Transaction tx = null;

        for (int l = 0; l < MAX_REQUEST_COUNT; l++) {
            System.out.println("Request#" + l);
            MetaData reqMetaData = getRandomMetadata(MAX_PRODUCT_COUNT_PER_RFQ);
            reqMetaData.setMetaData("kafkaInTimestamp", LocalDateTime.now().toString());
            reqMetaData.setMetaData("requestCreationTimestamp", LocalDateTime.now(Clock.systemUTC()).toString());

            try {
                tx = Transactions.doRequest(driver, null, reqMetaData, keys, null, null, null);
//                String owner = tx.getInputs().get(0).getOwnersBefore().get(0);
//                produceOnKafka(reqMetaData.getMetadata(), tx.getId(), owner);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return tx;
    }

    // Author: Sen Quio
    public static void createRFQG(BigchainDBJavaDriver driver, String skey, String pkey, FileWriter rfqFile,
                                  FileWriter personal_rfqFile, int security_parameter_byte, String payload)
            throws IOException, InterruptedException {

        final int MAX_REQUEST_COUNT = 1, MAX_PRODUCT_COUNT_PER_RFQ = 1;
        // List<String> preReqs = readFile("prereq.txt");
        List<String> personalPreReqs = readFile("personal_prereq.txt");

        for (int j = 0; j < personalPreReqs.size(); j++) {
            MetaData reqMetaData = getRandomMetadata(MAX_PRODUCT_COUNT_PER_RFQ);
            reqMetaData.setMetaData("in-time", LocalDateTime.now().toString());
            reqMetaData.setMetaData("requestCreationTimestamp", LocalDateTime.now(Clock.systemUTC()).toString());

            SecureRandom random = new SecureRandom();
            byte[] bytes = new byte[security_parameter_byte]; // 128 bits are converted to 16 bytes;
            random.nextBytes(bytes);
            String preimage = String.valueOf(bytes);

            String previousID = personalPreReqs.get(j).split("\\|")[0];
            String previous_requestID = personalPreReqs.get(j).split("\\|")[1];
            String preimage_previous = personalPreReqs.get(j).split("\\|")[2];

            reqMetaData.setMetaData("previous_nonce", preimage_previous);
            String test = previous_requestID + preimage;
            reqMetaData.setMetaData("hash", test.hashCode());
            reqMetaData.setMetaData("RID", previous_requestID);
            reqMetaData.setMetaData("previous_transaction_ID", previousID);

            try {
//                String txId = Transactions.doRequest(driver, preReqs.get(l), reqMetaData, keys);
                Transaction tx = Transactions.doRequest(driver, personalPreReqs.get(j).split("\\|")[0],
                        reqMetaData, null, skey, pkey, payload);
                String txId = tx.getId();
                // RFQs.add(txId);
                // produceOnKafka(reqMetaData.getMetadata(), "");
                rfqFile.write(txId + "\n");
                rfqFile.flush();
                personal_rfqFile.write(txId + "|" + previous_requestID + "|" + preimage + "\n");
                personal_rfqFile.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static String createCreate(BigchainDBJavaDriver driver, KeyPair keys) {
        HashSet<String> hset = Capabilities.getAllRequestTopics();
        String[] array = new String[hset.size()];
        hset.toArray(array);
        String createId = null;

        try {
            Map<String, Object> cre_assetData = new TreeMap<String, Object>() {{
                put("capability", array);
                put("machineIdentifier", "X100" + LocalDateTime.now().toString());
            }};

            MetaData creMetaData = new MetaData();
            creMetaData.setMetaData("requestCreationTimestamp", LocalDateTime.now(Clock.systemUTC()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")));
            createId = Transactions.doCreate(driver, cre_assetData, creMetaData, keys);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return createId;
    }

    public static Transaction createBid(BigchainDBJavaDriver driver, KeyPair keys, String rfqId, String createId) {
        HashSet<String> hset = Capabilities.getAllRequestTopics();
        String[] array = new String[hset.size()];
        hset.toArray(array);
        Transaction bid = null;

        try {
            Map<String, Object> cre_assetData = new TreeMap<String, Object>() {{
                put("capability", array);
                put("machineIdentifier", "X100" + LocalDateTime.now().toString());
            }};

            MetaData metaData2 = new MetaData();
            metaData2.setMetaData("requestCreationTimestamp", LocalDateTime.now(Clock.systemUTC()).toString());
            bid = Transactions.doBid(driver, createId, rfqId, metaData2, keys);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return bid;
    }

    public static Transaction createBuyOffer(BigchainDBJavaDriver driver, KeyPair buyerKeys,String advId, String createId) {
        
        Transaction buy = null;

        try {

            MetaData metaData2 = new MetaData();
            metaData2.setMetaData("requestCreationTimestamp", LocalDateTime.now(Clock.systemUTC()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")));
            metaData2.setMetaData("minAmt", "1");
            buy = Transactions.doBuyOffer(driver, createId, advId, metaData2, buyerKeys);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return buy;
    }

    public static Transaction createSell(BigchainDBJavaDriver driver, KeyPair Keys,String txId,String advId, String buyOfferId) {
        
        Transaction sell = null;

        try {

            MetaData metaData2 = new MetaData();
            metaData2.setMetaData("requestCreationTimestamp", LocalDateTime.now(Clock.systemUTC()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")));
            sell = Transactions.doSell(driver, txId, advId, buyOfferId, metaData2, Keys);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return sell;
    }

    
    public static Transaction createReturnSell(BigchainDBJavaDriver driver, KeyPair buyerKeys,String sellId, String createId) {
        
        Transaction buy = null;

        try {

            MetaData metaData2 = new MetaData();
            metaData2.setMetaData("requestCreationTimestamp", LocalDateTime.now(Clock.systemUTC()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")));
            buy = Transactions.doReturnSell(driver, sellId, createId, metaData2, buyerKeys);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return buy;
    }

    public static Transaction createAcceptReturn(BigchainDBJavaDriver driver, KeyPair Keys,String assetdId,String returnId, String sellId) {
        
        Transaction sell = null;

        try {

            MetaData metaData2 = new MetaData();
            metaData2.setMetaData("requestCreationTimestamp", LocalDateTime.now(Clock.systemUTC()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")));
            sell = Transactions.doAcceptReturn(driver, assetdId, returnId, sellId, metaData2, Keys);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return sell;
    }


    public static Transaction createAdv(BigchainDBJavaDriver driver, KeyPair keys, String createId, String status) {
        
        Transaction adv = null;

        try {
            

            MetaData metaData2 = new MetaData();
            metaData2.setMetaData("kafkaInTimestamp", LocalDateTime.now().toString());
            metaData2.setMetaData("requestCreationTimestamp", LocalDateTime.now(Clock.systemUTC()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")));
            metaData2.setMetaData("status", status);
            metaData2.setMetaData("minAmt", "1");
            adv = Transactions.doAdv(driver, createId, metaData2, keys);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return adv;
    }

    public static Transaction createTransfer(BigchainDBJavaDriver driver, KeyPair keys, String createId, KeyPair transferKeys) {
        
        Transaction adv = null;

        try {
            

            MetaData metaData2 = new MetaData();
            metaData2.setMetaData("kafkaInTimestamp", LocalDateTime.now().toString());
            metaData2.setMetaData("requestCreationTimestamp", LocalDateTime.now(Clock.systemUTC()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")));
            adv = Transactions.doTransfer(driver, createId, metaData2, keys, transferKeys);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return adv;
    }

    public static Transaction updateAdv(BigchainDBJavaDriver driver, KeyPair keys, String createId, String advId) {
        
        Transaction adv = null;

        try {
            

            MetaData metaData2 = new MetaData();
            metaData2.setMetaData("kafkaInTimestamp", LocalDateTime.now().toString());
            metaData2.setMetaData("requestCreationTimestamp", LocalDateTime.now(Clock.systemUTC()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")));
            metaData2.setMetaData("status", "Closed");
            
            adv = Transactions.updateAdv(driver, createId, advId, metaData2, keys);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return adv;
    }

    public static Transaction updateAdv(BigchainDBJavaDriver driver, KeyPair keys, String createId,String advId, String status) {
        
        Transaction adv = null;

        try {
            

            MetaData metaData2 = new MetaData();
            metaData2.setMetaData("kafkaInTimestamp", LocalDateTime.now().toString());
            metaData2.setMetaData("requestCreationTimestamp", LocalDateTime.now(Clock.systemUTC()).toString());
            metaData2.setMetaData("status", status);
            adv = Transactions.doAdv(driver, createId, metaData2, keys);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return adv;
    }

    public static void createAccept(BigchainDBJavaDriver driver, KeyPair keys) throws Exception {

        List<String> RFQs = readFile("rfq.txt");
        final int ID = 0, RFQ_COUNT = RFQs.size() / 1;
        Map<String, List<String>> hmap = new HashMap<>();

        for (int i = RFQ_COUNT * ID; i < RFQ_COUNT * (ID + 1); i++) {
            String rfqId = RFQs.get(i);
            List<String> bids = TransactionsApi.getBidsForRFQ(rfqId);

            System.out.println(bids);
            hmap.put(rfqId, bids);
        }

        for (Map.Entry<String, List<String>> entry : hmap.entrySet()) {

            Random random = new Random(System.nanoTime());
            List<String> bids = entry.getValue();
            if (!bids.isEmpty()) {
                int k = random.nextInt(bids.size());
                String winningBid = bids.get(k);

                MetaData metaData = new MetaData();
                metaData.setMetaData("requestCreationTimestamp", LocalDateTime.now(Clock.systemUTC()).toString());

                Transactions.doAccept(driver, winningBid, entry.getKey(), metaData, keys);
            }
        }
    }

    static MetaData getRandomMetadata(int MAX_PRODUCT_COUNT_PER_RFQ) {
        // Random random = new Random(System.nanoTime());
        // Set<String> capset = new HashSet<>();
        // int productCount = random.nextInt(MAX_PRODUCT_COUNT_PER_RFQ) + 1;
        // List<Map<String, String>> productsList = new ArrayList<>();
        List<String> availableCapabilities = new ArrayList<>(Capabilities.getAllRequestTopics());

/*         for (int k = 0; k < productCount; k++) {
            Map<String, String> productMetadata = new TreeMap<>();
            List<String> randomAttributes = StardogTest.getKeys();

            productMetadata.put("Quantity", StardogTest.getQuantity());
            productMetadata.put("Material", StardogTest.getMaterial());

            for (String key : randomAttributes) {
                productMetadata.put(key, StardogTest.getRandomValues(key));
            }

            productsList.add(productMetadata);
        } */

/*         final int NUM_OF_CAPS = random.nextInt(20) + 1;
        for (int _k = 0; _k < NUM_OF_CAPS; _k++) {
            int index = random.nextInt(availableCapabilities.size());
            capset.add(availableCapabilities.get(index));
            availableCapabilities.remove(index);
        } */

        MetaData reqMetaData = new MetaData();
        // reqMetaData.setMetaData("products", productsList);
        reqMetaData.setMetaData("capability", availableCapabilities);

        return reqMetaData;
    }

    private static List<String> readFile(String filename) {
        List<String> preReqs = new ArrayList<>();

        try {
            try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
                String line = br.readLine();

                while (line != null) {
                    preReqs.add(line);
                    line = br.readLine();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return preReqs;
    }

    public static void produceOnKafka(Map<String, Object> metaMap, String txId, String owner) {
        List<String> capabilityList = (List<String>) metaMap.get("capability");
        List<String> inferredCapabilities = (List<String>) metaMap.get("inferredCapabilities");

        JSONObject js = new JSONObject(metaMap);
        js.put("owner", owner);
        js.put("Transaction_id", txId);
        js.put("InferredCapability", inferredCapabilities);
        js.put("CapabilityCount", Integer.toString(capabilityList.size()));
        js.put("kafkaInTimestamp", LocalDateTime.now().toString());

        String data = js.toString();
        String topic = inferredCapabilities.get(new Random(System.nanoTime()).nextInt(inferredCapabilities.size()));
//        ProducerDriver.produce(producer, topic, txId, data);
    }

    public static List<String> mediate(List<String> capabilityList) {
        Set<String> allCapability = new HashSet<>(StardogTest.getCapabilityTopic(capabilityList));
        return new ArrayList<>(allCapability);
    }
}
