package com.bigchaindb.smartchaindb.driver;

import com.bigchaindb.api.TransactionsApi;
import com.bigchaindb.builders.BigchainDbConfigBuilder;
import com.bigchaindb.model.Connection;
import com.bigchaindb.model.GenericCallback;
import com.bigchaindb.model.MetaData;
import com.bigchaindb.model.Transaction;
import okhttp3.Response;

import java.security.KeyPair;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.function.Function;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.IOException;


public class BigchainDBJavaDriver {
    public boolean COMMIT_TX = true;
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static final AtomicLong transactionCount = new AtomicLong(0); // Define transactionCount here

    public static void main(String[] args) {
        clearMetricsLog();
        println("Running Process: " + getProcessId());
        int processId = getProcessId();
        
        setConfig();
        long startTime = System.nanoTime();
        KeyPair sellerKeyPair = getKeys();
        KeyPair buyerKeyPair = getKeys();
        
        BigchainDBJavaDriver driver = new BigchainDBJavaDriver();
        //AtomicLong transactionCount = new AtomicLong(0);
        int validAssetCount = 150;
        int invalidAssetCount = 64;

        // Transaction ID lists
        List<String> sellerCreateIds = new CopyOnWriteArrayList<>();
        List<String> buyerCreateIds = new CopyOnWriteArrayList<>();
        List<String> advIds = new CopyOnWriteArrayList<>();
        List<String> buyOfferIds = new CopyOnWriteArrayList<>();
        List<String> sellIds = new CopyOnWriteArrayList<>();
        List<String> invalidAdvIds = new CopyOnWriteArrayList<>();
        List<String> invalidBuyOfferIds = new CopyOnWriteArrayList<>();
        List<String> invalidSellIds = new CopyOnWriteArrayList<>();
        List<String> invalidTransferIds = new CopyOnWriteArrayList<>();
        // Execute each step in sequence:
        executeAndProcess(
            createSellerAssetsTasks(validAssetCount, driver, sellerKeyPair),
            "Seller Asset",
            sellerCreateIds
        )
        .thenCompose(v -> executeAndProcess(
            createBuyerAssetsTasks(validAssetCount, driver, buyerKeyPair),
            "Buyer Asset",
            buyerCreateIds
        ))
        .thenCompose(v -> {
            CompletableFuture<Void> delay = new CompletableFuture<>();
            scheduler.schedule(() -> delay.complete(null), 100, TimeUnit.SECONDS); //100// Introduce a 10-second delay
            return delay;
        })
        .thenCompose(v -> executeAndProcess(
            createAdvertisementsTasks(sellerCreateIds, driver, sellerKeyPair),
            "Advertisement",
            advIds
        ))
        .thenCompose(v -> {
            CompletableFuture<Void> delay = new CompletableFuture<>();
            scheduler.schedule(() -> delay.complete(null), 200, TimeUnit.SECONDS); // 200 Introduce a 10-second delay
            return delay;
        })
        .thenCompose(v -> executeAndProcess(
            createBuyOfferTasks(advIds, buyerCreateIds, driver, buyerKeyPair),
            "Buy Offer",
            buyOfferIds
        ))
        .thenCompose(v -> {
            CompletableFuture<Void> delay = new CompletableFuture<>();
            scheduler.schedule(() -> delay.complete(null), 300, TimeUnit.SECONDS); // 300Introduce a 10-second delay
            return delay;
        })
        .thenCompose(v -> executeAndProcess(
            createSellTasks(sellerCreateIds, advIds, buyOfferIds, driver, sellerKeyPair),
            "Sell Transaction",
            sellIds
        ))
        .thenCompose(v -> {
            CompletableFuture<Void> delay = new CompletableFuture<>();
            scheduler.schedule(() -> delay.complete(null), 300, TimeUnit.SECONDS); //300 Introduce a 10-second delay
            return delay;
        })
        .thenCompose(v -> executeAndProcess(
            createInvalidAdvertisementsTasks(sellerCreateIds, driver, sellerKeyPair, (int) (invalidAssetCount )),
            "Invalid Advertisement",
            invalidAdvIds
        ))
        .thenCompose(v -> executeAndProcess(
            createInvalidBuyOfferTasks(advIds, buyerCreateIds, driver, buyerKeyPair ,sellerKeyPair,sellerCreateIds, (int) (invalidAssetCount )),
            "invalid Buy Offer",
            invalidBuyOfferIds
        ))
        .thenCompose(v -> executeAndProcess(
            createInvalidSellTasks(sellerCreateIds, advIds, invalidBuyOfferIds, driver, sellerKeyPair, (int) (invalidAssetCount )),
            "invalid Sell Transaction",
            invalidSellIds
        ))
        .thenCompose(v -> executeAndProcess(
            createReturnTasks(sellIds, buyOfferIds, driver, sellerKeyPair, buyerKeyPair, (int) (validAssetCount )),
            "Return Handling",
            null // Not collecting IDs here
        ))
        
        // .thenCompose(v -> executeAndProcess(
        //     createInvalidTransferTasks(sellerCreateIds, driver, sellerKeyPair, buyerKeyPair),
        //     "Invalid Transfer",
        //     invalidTransferIds
        // ))
        .thenAccept(v -> {
            long endTime = System.nanoTime();

            logProcessorMetrics(processId, transactionCount.get(), startTime, endTime);
            System.out.println("Process " + processId + " completed with " + transactionCount.get() + " transactions.");
                
            // Shutdown the shared executor if implemented in Promise (if needed):
            Promise.shutdown();
        })
        
        .exceptionally(ex -> {
            printerr("An error occurred: " + ex.getMessage());
            Promise.shutdown();
            return null;
        }).join();

        scheduler.shutdown();
    }

    /**
     * Configures connection URL and credentials
     */
    public static void setConfig() {
        //BigchainDbConfigBuilder.baseUrl("http://127.0.0.1:9984/").setup();
        List<Connection> connections = new ArrayList<>();
        for (String url : DriverConstants.VALIDATOR_NODES) {
            Map<String, Object> attributes = new TreeMap<>();
            attributes.put("baseUrl", url);
            connections.add(new Connection(attributes));
        }

        BigchainDbConfigBuilder
            .addConnections(connections)
            .setTimeout(60000)
            .setup();
    }

    /**
     * Generates EdDSA keypair to sign and verify transactions
     *
     * @return KeyPair
     */
    public static KeyPair getKeys() {
        net.i2p.crypto.eddsa.KeyPairGenerator edDsaKpg = new net.i2p.crypto.eddsa.KeyPairGenerator();
        return edDsaKpg.generateKeyPair();
    }

    /**
     * Creates tasks to create seller assets.
     */
    private static List<Supplier<String>> createSellerAssetsTasks(int count, BigchainDBJavaDriver driver, KeyPair sellerKeyPair) {
        return createTasks(count, "Seller Asset", i -> {
            try {
                String createId = Simulation.createCreate(driver, sellerKeyPair);
                if (createId != null) {
                    println("Seller Asset " + (i + 1) + " Created: " + createId);
                    transactionCount.incrementAndGet(); // Increment the transaction count for this processor
                    return createId;
                } else {
                    printerr("Failed to create Seller Asset " + (i + 1));
                    return null;
                }
            } catch (Exception e) {
                printerr("Exception creating Seller Asset " + (i + 1) + ": " + e.getMessage());
                return null;
            }
        });
    }

    /**
     * Creates tasks to create buyer assets.
     */
    private static List<Supplier<String>> createBuyerAssetsTasks(int count, BigchainDBJavaDriver driver, KeyPair buyerKeyPair) {
        return createTasks(count, "Buyer Asset", i -> {
            try {
                String createId = Simulation.createCreate(driver, buyerKeyPair);
                if (createId != null) {
                    println("Buyer Asset " + (i + 1) + " Created: " + createId);
                    transactionCount.incrementAndGet(); // Increment the transaction count for this processor
                    return createId;
                } else {
                    printerr("Failed to create Buyer Asset " + (i + 1));
                    return null;
                }
            } catch (Exception e) {
                printerr("Exception creating Buyer Asset " + (i + 1) + ": " + e.getMessage());
                return null;
            }
        });
    }

    /**
     * Creates tasks to create advertisements for each seller asset.
     */
    private static List<Supplier<String>> createAdvertisementsTasks(List<String> sellerCreateIds, BigchainDBJavaDriver driver, KeyPair sellerKeyPair) {
        return createTasks(sellerCreateIds.size(), "Advertisement", i -> {
            try {
                if (sellerCreateIds.size() > i) {
                    String createId = sellerCreateIds.get(i);
                    Transaction advTransaction = Simulation.createAdv(driver, sellerKeyPair, createId, "Open");
                    
                    if (advTransaction != null && advTransaction.getId() != null) {
                        println("Advertisement " + (i + 1) + " Created: " + advTransaction.getId());
                        transactionCount.incrementAndGet();
                        return advTransaction.getId();
                    } else {
                        printerr("Failed to create Advertisement for Seller Asset " + (i + 1));
                        return null;
                    }
                } else {
                    printerr("Seller Asset not available for Advertisement at index: " + i);
                    return null;
                }
            } catch (Exception e) {
                printerr("Exception creating Advertisement " + (i + 1) + ": " + e.getMessage());
                return null;
            }
        });
    }
    /**
     * Creates tasks to create advertisements for each seller asset.
     */
    private static List<Supplier<String>> createInvalidTransferTasks(List<String> sellerCreateIds, BigchainDBJavaDriver driver, KeyPair sellerKeyPair, KeyPair buyerKeyPair) {
        return createTasks(sellerCreateIds.size(), "Invalid Transfer", i -> {
            try {
                if (sellerCreateIds.size() > i) {
                    String createId = sellerCreateIds.get(i);
                    
                    Transaction transfer = Simulation.createTransfer(driver, sellerKeyPair, createId, buyerKeyPair);
                    if (transfer != null && transfer.getId() != null) {
                        println("Transfer Asset " + (i + 1) + " Created: " + transfer.getId());
                        transactionCount.incrementAndGet();
                        return transfer.getId();
                    } else {
                        printerr("Failed to create Transfer for Seller Asset " + (i + 1));
                        return null;
                    }
                } else {
                    printerr("Seller Asset not available for Transfer at index: " + i);
                    return null;
                }
            } catch (Exception e) {
                printerr("Exception creating Transfer " + (i + 1) + ": " + e.getMessage());
                return null;
            }
        });
    }
    /**
     * Creates tasks to create buy offers for each valid advertisement.
     */
    private static List<Supplier<String>> createBuyOfferTasks(List<String> advIds, List<String> buyerCreateIds, BigchainDBJavaDriver driver, KeyPair buyerKeyPair) {
        int count = Math.min(advIds.size(), buyerCreateIds.size());
        return createTasks(count, "Buy Offer", i -> {
            try {
                String advId = advIds.get(i);
                String buyerCreateId = buyerCreateIds.get(i);
                Transaction committedAdvTx = TransactionsApi.waitForCommitWorkflow(advId);

                //if (committedAdvTx != null) {
                    // Proceed with the buy offer creation after the advertisement is committed
                    Transaction buyOfferTransaction = Simulation.createBuyOffer(driver, buyerKeyPair, advId, buyerCreateId);
                    if (buyOfferTransaction != null && buyOfferTransaction.getId() != null) {
                        println("Buy Offer " + (i + 1) + " Created: " + buyOfferTransaction.getId());
                        transactionCount.incrementAndGet();
                        return buyOfferTransaction.getId();
                    } else {
                        printerr("Failed to create Buy Offer " + (i + 1));
                        return null;
                    }
                // } else {
                //     printerr("Advertisement " + advId + " was not committed. Skipping Buy Offer creation.");
                //     return null;
                // }
                
            } catch (Exception e) {
                printerr("Exception creating Buy Offer " + (i + 1) + ": " + e.getMessage());
                return null;
            }
        });
    }

    /**
     * Creates tasks to create sell transactions for each valid advertisement and corresponding buy offer.
     */
    private static List<Supplier<String>> createSellTasks(List<String> sellerCreateIds, List<String> advIds, List<String> buyOfferIds, BigchainDBJavaDriver driver, KeyPair sellerKeyPair) {
        int count = Math.min(Math.min(sellerCreateIds.size(), advIds.size()), buyOfferIds.size());
        return createTasks(count, "Sell Transaction", i -> {
            try {
                String createId = sellerCreateIds.get(i);
                String advId = advIds.get(i);
                String buyOfferId = buyOfferIds.get(i);
                Transaction committedAdvTx = TransactionsApi.waitForCommitWorkflow(buyOfferId);

                //if (committedAdvTx != null) {
                    Transaction sellTransaction = Simulation.createSell(driver, sellerKeyPair, createId, advId, buyOfferId);
                    if (sellTransaction != null && sellTransaction.getId() != null) {
                        println("Sell Transaction " + (i + 1) + " Created: " + sellTransaction.getId());
                        transactionCount.incrementAndGet();
                        return sellTransaction.getId();
                    } else {
                        printerr("Failed to create Sell Transaction " + (i + 1));
                        return null;
                    }
                // } else {
                //     printerr("Advertisement " + advId + " was not committed. Skipping Buy Offer creation.");
                //     return null;
                // }
            } catch (Exception e) {
                printerr("Exception creating Sell Transaction " + (i + 1) + ": " + e.getMessage());
                return null;
            }
        });
    }

    /**
     * Creates tasks to create invalid sell transactions for each valid advertisement and corresponding buy offer.
     */
    private static List<Supplier<String>> createInvalidSellTasks(List<String> sellerCreateIds, List<String> advIds, List<String> invalidBuyOfferIds, BigchainDBJavaDriver driver, KeyPair sellerKeyPair,int count) {
        //int count = Math.min(Math.min(sellerCreateIds.size(), advIds.size()), invalidBuyOfferIds.size());
        return createTasks(count, "Invalid Sell Transaction", i -> {
            try {
                
                
                if (sellerCreateIds.size() > i && advIds.size() > i && invalidBuyOfferIds.size() > i) {
                    String createId = sellerCreateIds.get(i);
                    String advId = advIds.get(i);
                    String buyOfferId = invalidBuyOfferIds.get(i);
                    Transaction invalidSellTransaction = Simulation.createSell(driver, sellerKeyPair, createId, advId, buyOfferId);
                    if (invalidSellTransaction != null && invalidSellTransaction.getId() != null) {
                        //invalidSellIds.add(invalidSellTransaction.getId());
                        System.out.println("Invalid Sell Transaction " + (i + 1) + " Created: " + invalidSellTransaction.getId());
                        transactionCount.incrementAndGet();
                        return invalidSellTransaction.getId();
                    } else {
                        System.err.println("Failed to create Invalid Sell Transaction for Advertisement " + (i + 1));
                        return null;
                    }
                } else {
                    System.err.println("Seller Asset, Advertisement, or Invalid Buy Offer not available for Invalid Sell Transaction creation at index: " + i);
                    return null;
                }                      
            
            } catch (Exception e) {
                printerr("Exception creating Sell Transaction " + (i + 1) + ": " + e.getMessage());
                return null;
            }
        });
    }

    /**
     * Creates tasks to handle returns for a subset of sell transactions.
     */
    private static List<Supplier<Void>> createReturnTasks(List<String> sellIds, List<String> buyOfferIds, BigchainDBJavaDriver driver, KeyPair sellerKeyPair, KeyPair buyerKeyPair, int count) {
        count = Math.min(count, sellIds.size());
        return createTasks(count, "Return Handling", i -> {
            try {
                String sellId = sellIds.get(i);
                Transaction committedAdvTx = TransactionsApi.waitForCommitWorkflow(sellId);

                if (committedAdvTx != null) {
                    List<String> transferTxns = TransactionsApi.getTransferTransactionIdsByAssetId(sellId);
                
                if (!transferTxns.isEmpty()) {
                    Transaction inverse = Simulation.createReturnSell(driver, buyerKeyPair, sellId, transferTxns.get(0));
                    transactionCount.incrementAndGet();
                    if (inverse != null && inverse.getId() != null) {
                        String buyOfferId = buyOfferIds.get(i);
                        List<String> returnTxns = TransactionsApi.getTransferTransactionIdsByAssetId(buyOfferId);
                        if (!returnTxns.isEmpty()) {
                            Transaction acceptReturn = Simulation.createAcceptReturn(driver, sellerKeyPair, buyOfferId, returnTxns.get(0), inverse.getId());
                            if (acceptReturn != null && acceptReturn.getId() != null) {
                                println("Return and Accept Return for Sell Transaction " + (i + 1) + " Completed");
                                transactionCount.incrementAndGet();
                            } else {
                                printerr("Failed to create Accept Return for Sell Transaction " + (i + 1));
                            }
                        } else {
                            printerr("No return transactions available for Buy Offer ID: " + buyOfferId);
                        }
                    } else {
                        printerr("Failed to create Return Sell for Sell Transaction " + (i + 1));
                    }
                } else {
                    printerr("No transfer transactions available for Sell ID: " + sellId);
                }
            } else {
                printerr("Advertisement " + sellId + " was not committed. Skipping Buy Offer creation.");
                return null;
            }
            } catch (Exception e) {
                printerr("Exception in Return Handling for Sell Transaction " + (i + 1) + ": " + e.getMessage());
            }
            return null;
        });
    }
    /**
     * Creates tasks invalid advertisements transactions for each valid advertisement and corresponding buy offer.
     */
    private static List<Supplier<String>> createInvalidAdvertisementsTasks(List<String> sellerCreateIds, BigchainDBJavaDriver driver, KeyPair sellerKeyPair ,int count) {
        return createTasks(sellerCreateIds.size(), "Invalid Advertisement", i -> {
            try {
                
                    if (sellerCreateIds.size() > i) {
                        String createId = sellerCreateIds.get(i);
                        Transaction invalidAdvTransaction = Simulation.createAdv(driver, sellerKeyPair, createId, "Open");
                        if (invalidAdvTransaction != null && invalidAdvTransaction.getId() != null) {
                           // invalidAdvIds.add(invalidAdvTransaction.getId());
                            System.out.println("Invalid Advertisement " + (i + 1) + " Created: " + invalidAdvTransaction.getId());
                            transactionCount.incrementAndGet();
                            return invalidAdvTransaction.getId();
                        } else {
                            System.err.println("Failed to create Invalid Advertisement for Seller Asset " + (i + 1));
                            return null;
                        }
                    } else {
                        System.err.println("Seller Asset not available for Invalid Advertisement creation at index: " + i);
                        return null;
                    }    
                
            
            } catch (Exception e) {
                printerr("Exception creating Invalid Advertisement " + (i + 1) + ": " + e.getMessage());
                return null;
            }
        });
    }
    /**
     * Creates tasks to create invalid buy offers for each valid advertisement.
     */
    private static List<Supplier<String>> createInvalidBuyOfferTasks(List<String> advIds, List<String> buyerCreateIds, BigchainDBJavaDriver driver, KeyPair buyerKeyPair, KeyPair sellerKeyPair, List<String> sellerCreateIds, int count) {
        //int count = Math.min(advIds.size(), buyerCreateIds.size());
        return createTasks(count, "Invalid Buy Offer", i -> {
            try {
                  
                        if (advIds.size() > i && buyerCreateIds.size() > i) {
                            String advId = advIds.get(i);
                            Simulation.updateAdv(driver, sellerKeyPair, sellerCreateIds.get(i), advId); // Close the advertisement
                            Transaction invalidBuyOfferTransaction = Simulation.createBuyOffer(driver, buyerKeyPair, advId, buyerCreateIds.get(i));
                            if (invalidBuyOfferTransaction != null && invalidBuyOfferTransaction.getId() != null) {
                                //invalidBuyOfferIds.add(invalidBuyOfferTransaction.getId());
                                System.out.println("Invalid Buy Offer " + (i + 1) + " Created: " + invalidBuyOfferTransaction.getId());
                                transactionCount.incrementAndGet();
                                return invalidBuyOfferTransaction.getId();
                            } else {
                                System.err.println("Failed to create Invalid Buy Offer for Advertisement " + (i + 1));
                                return null;
                            }
                        } else {
                            System.err.println("Advertisement or Buyer Asset not available for Invalid Buy Offer creation at index: " + i);
                            return null;
                        }                 
                           
            } catch (Exception e) {
                printerr("Exception creating Invalid Buy Offer " + (i + 1) + ": " + e.getMessage());
                return null;
            }
        });
    }

    private static void logProcessorMetrics(int processId, long transactionCount, long startTime, long endTime) {
        try (PrintWriter writer = new PrintWriter(new FileWriter("processor_metrics.log", true))) {
            writer.printf("Process=%d, Transactions=%d, Start=%d, End=%d%n", processId, transactionCount, startTime, endTime);
        } catch (IOException e) {
            System.err.println("Error logging metrics: " + e.getMessage());
        }
    }
    private static void clearMetricsLog() {
        try (PrintWriter writer = new PrintWriter(new FileWriter("processor_metrics.log"))) {
            writer.print(""); // Clear the content
        } catch (IOException e) {
            System.err.println("Error clearing metrics log: " + e.getMessage());
        }
    }
    
    private static int getProcessId() {
        String jvmName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
        try {
            return Integer.parseInt(jvmName.split("@")[0]);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * A generic method to create a list of Supplier tasks given a count, a descriptive name, and a task creator function.
     */
    private static <T> List<Supplier<T>> createTasks(int count, String taskType, Function<Integer, T> taskCreator) {
        List<Supplier<T>> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final int index = i;
            tasks.add(() -> {
                T result = taskCreator.apply(index);
                // Optional short delay: Remove or adjust as needed
                return result;
            });
        }
        return tasks;
    }

    /**
     * Executes a list of tasks using Promise.allSettled and processes the results.
     * Adds fulfilled values to the provided results list (if not null) and logs errors.
     */
    private static <T> java.util.concurrent.CompletableFuture<Void> executeAndProcess(List<Supplier<T>> tasks, String taskType, List<T> results) {
        return Promise.allSettled(tasks).thenAccept(settlements -> processSettlements(settlements, results, taskType));
    }

    /**
     * Processes a list of settlements by collecting successful results and logging failures.
     */
    private static <T> void processSettlements(List<Settlement<T>> settlements, List<T> results, String taskType) {
        for (int i = 0; i < settlements.size(); i++) {
            Settlement<T> settlement = settlements.get(i);
            int taskNumber = i + 1;
            if (settlement.isFulfilled() && settlement.getValue() != null) {
                if (results != null) {
                    results.add(settlement.getValue());
                }
            } else {
                String errorMessage = (settlement.getReason() != null)
                        ? settlement.getReason().getMessage() : "Unknown Error";
                printerr(taskType + " " + taskNumber + " failed: " + errorMessage);
            }
        }
    }

    public GenericCallback handleServerResponse(String operation, MetaData metaData, String txId) {
        return new GenericCallback() {
            public void transactionMalformed(Response response) {
                println("Malformed: " + response.message());
            }

            public void pushedSuccessfully(Response response) {
                println("Transaction successfully posted");
            }

            public void otherError(Response response) {
                println("Other error: " + response.message());
            }
        };
    }

    

    private static void println(String out) {
        System.out.println(getProcessId() + ": " + out);
    }

    private static void printerr(String out) {
        System.err.println(getProcessId() + ": " + out);
    }
}
