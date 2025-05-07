/*
 * Copyright BigchainDB GmbH and BigchainDB contributors
 * SPDX-License-Identifier: (Apache-2.0 AND CC-BY-4.0)
 * Code is Apache-2.0 and docs are CC-BY-4.0
 */
package com.bigchaindb.api;

import com.bigchaindb.constants.BigchainDbApi;
import com.bigchaindb.constants.Operations;
import com.bigchaindb.exceptions.TransactionNotFoundException;
import com.bigchaindb.model.BigChainDBGlobals;
import com.bigchaindb.model.GenericCallback;
import com.bigchaindb.model.Transaction;
import com.bigchaindb.model.Transactions;
import com.bigchaindb.util.JsonUtils;
import com.bigchaindb.util.NetworkUtils;

import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;



import java.io.IOException;

/**
 * The Class TransactionsApi.
 */
public class TransactionsApi extends AbstractApi {

	private static final Logger log = LoggerFactory.getLogger( TransactionsApi.class );
	
	/**
	 * Send transaction.
	 *
	 * @param transaction
	 *            the transaction
	 * @param callback
	 *            the callback
	 */
	public static void sendTransaction(Transaction transaction, final GenericCallback callback) {
		log.debug( "sendTransaction Call :" + transaction );
		RequestBody body = RequestBody.create(JSON, transaction.toString());
		NetworkUtils.sendPostRequest(BigChainDBGlobals.getBaseUrl() + BigchainDbApi.TRANSACTIONS, body, callback);
	}

	/**
	 * Sends the transaction.
	 *
	 * @param transaction
	 *            the transaction
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public static void sendTransaction(Transaction transaction) throws IOException {
		log.debug( "sendTransaction Call :" + transaction );
		RequestBody body = RequestBody.create(JSON, JsonUtils.toJson(transaction));
		Response response = NetworkUtils.sendPostRequest(BigChainDBGlobals.getBaseUrl() + BigchainDbApi.TRANSACTIONS, body);
		response.close();
	}

	/**
	 * Validates the transaction.
	 *
	 * @param transaction
	 *            the transaction
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public static Response validateTransaction(Transaction transaction) throws IOException {
		log.debug( "validateTransaction Call :" + transaction );
		RequestBody body = RequestBody.create(JSON, JsonUtils.toJson(transaction));
		Response response = NetworkUtils.sendPostRequest(BigChainDBGlobals.getBaseUrl() + BigchainDbApi.TRANSACTIONS + "/validate", body);
		response.close();
		return response;
	}

	/**
	 * Gets the transaction by id.
	 *
	 * @param id
	 *            the id
	 * @return the transaction by id
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public static Transaction getTransactionById(String id)
			throws IOException, TransactionNotFoundException {
		log.debug( "getTransactionById Call :" + id );
		Response response = NetworkUtils.sendGetRequest(BigChainDBGlobals.getBaseUrl() + BigchainDbApi.TRANSACTIONS + "/" + id);
		if(!response.isSuccessful()){
			if(response.code() == HttpStatus.SC_NOT_FOUND)
				throw new TransactionNotFoundException("Transaction with id " + id + " not present");
		}
		String body = response.body().string();
		response.close();
		return JsonUtils.fromJson(body, Transaction.class);
	}


	public static List<String> getBidsForRFQ(String id)
			throws IOException, TransactionNotFoundException {
		log.debug( "getBidsForRFQ Call :" + id );
		Response response = NetworkUtils.sendGetRequest(BigChainDBGlobals.getBaseUrl() + BigchainDbApi.TRANSACTIONS + "/rfq/" + id);
		if(!response.isSuccessful()){
			if(response.code() == HttpStatus.SC_NOT_FOUND)
				throw new TransactionNotFoundException("Transaction with id " + id + " not present");
		}
		String body = response.body().string();
		response.close();
		return new Gson().fromJson(body, new TypeToken<ArrayList<String>>(){}.getType());
	}

	/**
	 * Gets the transactions by asset id.
	 *
	 * @param assetId
	 *            the asset id
	 * @param operation
	 *            the operation
	 * @return the transactions by asset id
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public static Transactions getTransactionsByAssetId(String assetId, Operations operation)
			throws IOException {
		log.debug( "getTransactionsByAssetId Call :" + assetId + " operation " + operation );
		Response response = NetworkUtils.sendGetRequest(
				BigChainDBGlobals.getBaseUrl() + BigchainDbApi.TRANSACTIONS + "?asset_id=" + assetId + "&operation=" + operation);
		String body = response.body().string();
		response.close();
		return JsonUtils.fromJson(body, Transactions.class);
	}

	public static List<String> getTransferTransactionIdsByAssetId(String assetId) throws IOException {
		log.debug("getTransferTransactionIdsByAssetId Call: " + assetId);
		
		// Construct the URL to get transfer transactions for the given asset ID
		String url = BigChainDBGlobals.getBaseUrl() + BigchainDbApi.TRANSACTIONS + "?asset_id=" + assetId + "&operation=TRANSFER";
		
		// Send the GET request to the server
		Response response = NetworkUtils.sendGetRequest(url);
		String body = response.body().string();
		response.close();
		
		// Convert the response to a Transactions object
		Transactions transferTxns = JsonUtils.fromJson(body, Transactions.class);
		
		// Extract and return the transaction IDs without quotes
		List<String> transferIds = new ArrayList<>();
		if (transferTxns != null && transferTxns.getTransactions() != null) {
			for (Transaction txn : transferTxns.getTransactions()) {
				// Use trim or replace to remove quotes if present
				String txnId = txn.getId().replace("\"", "").trim();
				transferIds.add(txnId);
			}
		}
		
		return transferIds;
	}
	
	public static Transaction waitForCommit(String txId) {
		int maxRetries = 60; //60; // Maximum number of retries
		int sleepMillis = 5000;//5000; // Time to wait between retries (in milliseconds)
	
		for (int i = 0; i < maxRetries; i++) {
			//System.out.println("--> Querying Transaction " + txId + ". Please wait..." + i);
			try {
				Transaction transaction = TransactionsApi.getTransactionById(txId);

				if (transaction != null) {
					System.out.println("Transaction " + txId + " is committed.");
					return transaction;
				}
			} catch (Exception ex) {
				//System.out.println("Transaction " + txId + " not found. Retrying..." + i+1);
			} 
			
			try {
				Thread.sleep(sleepMillis);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new RuntimeException("Interrupted while waiting for transaction commit.", ex);
			}
		}
	
		return null;
	}

	public static Transaction waitForCommitWorkflow(String txId) {
		int maxRetries = 60;//60; // Maximum number of retries
		int sleepMillis = 5000;//5000; // Time to wait between retries (in milliseconds)
	
		for (int i = 0; i < maxRetries; i++) {
			//System.out.println("--> Querying Transaction " + txId + ". Please wait..." + i);
			try {
				Transaction transaction = TransactionsApi.getTransactionById(txId);

				if (transaction != null) {
					System.out.println("Transaction " + txId + " is committed.");
					return transaction;
				}
			} catch (Exception ex) {
				//System.out.println("Transaction " + txId + " not found. Retrying..." + i+1);
			} 
			
			try {
				Thread.sleep(sleepMillis);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new RuntimeException("Interrupted while waiting for transaction commit.", ex);
			}
		}
	
		return null;
	}
}
