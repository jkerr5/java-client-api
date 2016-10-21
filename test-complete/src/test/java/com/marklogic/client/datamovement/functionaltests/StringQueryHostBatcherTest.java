/*
 * Copyright 2014-2016 MarkLogic Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.marklogic.client.datamovement.functionaltests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.JsonNode;
import com.marklogic.client.DatabaseClient;
import com.marklogic.client.DatabaseClientFactory;
import com.marklogic.client.DatabaseClientFactory.Authentication;
import com.marklogic.client.admin.ExtensionMetadata;
import com.marklogic.client.admin.ServerConfigurationManager;
import com.marklogic.client.admin.TransformExtensionsManager;
import com.marklogic.client.document.JSONDocumentManager;
import com.marklogic.client.document.ServerTransform;
import com.marklogic.client.io.DOMHandle;
import com.marklogic.client.io.FileHandle;
import com.marklogic.client.io.Format;
import com.marklogic.client.io.InputStreamHandle;
import com.marklogic.client.io.JacksonHandle;
import com.marklogic.client.io.StringHandle;
import com.marklogic.client.query.QueryManager;
import com.marklogic.client.query.RawCombinedQueryDefinition;
import com.marklogic.client.query.RawQueryByExampleDefinition;
import com.marklogic.client.query.StringQueryDefinition;
import com.marklogic.client.query.StructuredQueryBuilder;
import com.marklogic.client.query.StructuredQueryBuilder.Operator;
import com.marklogic.client.query.StructuredQueryDefinition;
import com.marklogic.client.datamovement.DataMovementManager;
import com.marklogic.client.datamovement.Forest;
import com.marklogic.client.datamovement.JobTicket;
import com.marklogic.client.datamovement.QueryHostBatcher;
import com.marklogic.client.datamovement.WriteHostBatcher;
import com.marklogic.client.datamovement.functionaltests.util.DmsdkJavaClientREST;

/**
 * @author ageorge
 * Purpose : Test String Queries 
 * - On multiple documents using Java Client DocumentManager Write method and WriteHostbatcher.
 * - On meta-data.
 * - On non-existent document. Verify error message.
 * - With invalid string query. Verify error message.
 *
 */
public class StringQueryHostBatcherTest extends  DmsdkJavaClientREST {
	private static String dbName = "StringQueryHostBatcherDB";
	private static String [] fNames = {"StringQueryHostBatcherDB-1", "StringQueryHostBatcherDB-2", "StringQueryHostBatcherDB-3"};
	private static DataMovementManager dmManager = null;
	private static DataMovementManager moveMgr = DataMovementManager.newInstance();
	private static String restServerHost = null;
	private static String restServerName = null;
	private static int restServerPort = 0;
	private static DatabaseClient clientQHB = null;
	private static DatabaseClient client = null;
	private static String dataConfigDirPath = null;

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		loadGradleProperties();
		restServerPort = getRestAppServerPort();
		restServerHost = getRestAppServerHostName();
		
		restServerName = getRestAppServerName();
		dmManager = DataMovementManager.newInstance();
		dataConfigDirPath = getDataConfigDirPath();
		
		setupJavaRESTServer(dbName, fNames[0], restServerName, restServerPort);
		setupAppServicesConstraint(dbName);
				
		createUserRolesWithPrevilages("test-eval","xdbc:eval", "xdbc:eval-in","xdmp:eval-in","any-uri","xdbc:invoke");
	    createRESTUser("eval-user", "x", "test-eval","rest-admin","rest-writer","rest-reader","rest-extension-user","manage-user");
	    
	    // For use with Java/REST Client API
	    client = DatabaseClientFactory.newClient(restServerHost, restServerPort, "admin", "admin", Authentication.DIGEST);
	    // For use with QueryHostBatcher
	    clientQHB = DatabaseClientFactory.newClient(restServerHost, restServerPort, "eval-user", "x", Authentication.DIGEST);
	    moveMgr.withClient(clientQHB);
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		System.out.println("In tearDownAfterClass");
		// Release clients
		client.release();
		clientQHB.release();
		associateRESTServerWithDB(restServerName, "Documents" );
		deleteRESTUser("eval-user");
		detachForest(dbName, fNames[0]);
		
		deleteDB(dbName);
		deleteForest(fNames[0]);			
	}

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		System.out.println("In setup");		  
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
		System.out.println("In tearDown");
		clearDB(restServerPort);
	}
	
	/*
	 * To test String query with Document Manager (Java Client API write method) and WriteHostbatcher.
	 * @throws IOException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws XpathException
	 */
	@Test
	public void testAndWordQuery() throws IOException, ParserConfigurationException, SAXException, InterruptedException
	{	
		System.out.println("Running testAndWordQuery");
		
		String[] filenames = {"constraint1.xml", "constraint2.xml", "constraint3.xml", "constraint4.xml", "constraint5.xml"};
		String queryOptionName = "absRangeConstraintWithVariousGrammarAndWordQueryOpt.xml";
		try {

			// write docs using Java Client API
			for (String filename : filenames) {
				writeDocumentUsingInputStreamHandle(client, filename, "/abs-range-constraint/", "XML");
			}

			setQueryOption(client, queryOptionName);

			QueryManager queryMgr = client.newQueryManager();

			// create query def
			StringQueryDefinition querydef = queryMgr.newStringDefinition(queryOptionName);
			querydef.setCriteria("(pop:high OR pop:medium) AND price:medium AND intitle:served");

			// create handle to search using Java Client API.
			JacksonHandle jh = new JacksonHandle();
			JsonNode jsonResults = queryMgr.search(querydef, jh).get();

			// Verify the results. 
			JsonNode searchResult = jsonResults.get("results").get(0);
			assertEquals(1, searchResult.get("index").asInt());
			assertEquals("/abs-range-constraint/constraint4.xml", searchResult.get("uri").asText());
			String contents = searchResult.get("content").asText();
			assertTrue("Expected String not available", contents.contains("Vannevar served"));
			assertTrue("Expected amt not available", contents.contains("12.34"));

			// Clear the database.
			clearDB(8000);

			//Use WriteHostbatcher to write the same files.				
			WriteHostBatcher batcher = moveMgr.newWriteHostBatcher();

			batcher.withBatchSize(2);
			InputStreamHandle contentHandle1 = new InputStreamHandle();
			contentHandle1.set(new FileInputStream(dataConfigDirPath + filenames[0]));
			InputStreamHandle contentHandle2 = new InputStreamHandle();
			contentHandle2.set(new FileInputStream(dataConfigDirPath + filenames[1]));
			InputStreamHandle contentHandle3 = new InputStreamHandle();
			contentHandle3.set(new FileInputStream(dataConfigDirPath + filenames[2]));
			InputStreamHandle contentHandle4 = new InputStreamHandle();
			contentHandle4.set(new FileInputStream(dataConfigDirPath + filenames[3]));
			InputStreamHandle contentHandle5 = new InputStreamHandle();
			contentHandle5.set(new FileInputStream(dataConfigDirPath + filenames[4]));

			batcher.add("/abs-range-constraint/batcher-contraints1.xml", contentHandle1);
			batcher.add("/abs-range-constraint/batcher-contraints2.xml", contentHandle2);
			batcher.add("/abs-range-constraint/batcher-contraints3.xml", contentHandle3);
			batcher.add("/abs-range-constraint/batcher-contraints4.xml", contentHandle4);
			batcher.add("/abs-range-constraint/batcher-contraints5.xml", contentHandle5);

			// Verify if the batch flushes when batch size is reached.
			// Flush
			batcher.flushAndWait();
			// Hold for asserting the callbacks batch contents, since callback are on different threads than the main JUnit thread.
			// JUnit can not assert on different threads; other than the main one. 
			StringBuilder batchResults = new StringBuilder();
			StringBuilder batchFailResults = new StringBuilder();

			// Run a QueryHostBatcher on the new URIs.
			QueryHostBatcher queryBatcher1 = moveMgr.newQueryHostBatcher(querydef);

			queryBatcher1.onUrisReady((client1, batch) -> {
				for (String str : batch.getItems()) {
					batchResults.append(str)
							.append('|');
				}

				batchResults.append(batch.getForest().getForestName())
						.append('|')
						.append(batch.getBytesMoved())
						.append('|')
						.append(batch.getJobBatchNumber())
						.append('|');

			});
			queryBatcher1.onQueryFailure((client1, throwable) -> {
				System.out.println("Exceptions thrown from callback onQueryFailure");
				throwable.printStackTrace();
				batchFailResults.append("Test has Exceptions");
			});

			JobTicket jobTicket = moveMgr.startJob(queryBatcher1);
			boolean bJobFinished = queryBatcher1.awaitTermination(3, TimeUnit.MINUTES);

			if (queryBatcher1.isTerminated()) {

				if (!batchFailResults.toString().isEmpty() && batchFailResults.toString().contains("Exceptions")) {
					fail("Test failed due to exceptions");
				}

				// Verify the batch results now.
				String[] res = batchResults.toString().split("\\|");

				assertTrue("URI returned not correct", res[0].contains("/abs-range-constraint/batcher-contraints4.xml"));
				//Verify Fores Name.
				assertTrue("Forest name not correct", res[1].contains(fNames[0]));
			}
		}
		catch(Exception e) {
			System.out.print(e.getMessage());
		}
		finally {
			
		}
	}
	
	/*
	 * To test String query with multiple forests.
	 * @throws Exception
	 *
	 */
	@Test
	public void testAndWordQueryWithMultipleForests() throws Exception
	{
		String testMultipleDB = "QBMultipleForestDB";
		String[] testMultipleForest = {"QBMultipleForestDB-1", "QBMultipleForestDB-2", "QBMultipleForestDB-3"};
		
		try {
			System.out.println("Running testAndWordQueryWithMultipleForests");

			//Setup a separate database/
			createDB(testMultipleDB);
			createForest(testMultipleForest[0], testMultipleDB);
			createForest(testMultipleForest[1], testMultipleDB);			
			associateRESTServerWithDB(restServerName, testMultipleDB);

			setupAppServicesConstraint(testMultipleDB);

			String[] filenames = {"constraint1.xml", "constraint2.xml", "constraint3.xml", "constraint4.xml", "constraint5.xml"};
			String queryOptionName = "absRangeConstraintWithVariousGrammarAndWordQueryOpt.xml";

			setQueryOption(clientQHB, queryOptionName);

			QueryManager queryMgr = clientQHB.newQueryManager();

			StringQueryDefinition querydef = queryMgr.newStringDefinition();
			querydef.setCriteria("0012");

			//Use WriteHostbatcher to write the same files.				
			WriteHostBatcher batcher = moveMgr.newWriteHostBatcher();

			batcher.withBatchSize(2);
			InputStreamHandle contentHandle1 = new InputStreamHandle();
			contentHandle1.set(new FileInputStream(dataConfigDirPath + filenames[0]));
			InputStreamHandle contentHandle2 = new InputStreamHandle();
			contentHandle2.set(new FileInputStream(dataConfigDirPath + filenames[1]));
			InputStreamHandle contentHandle3 = new InputStreamHandle();
			contentHandle3.set(new FileInputStream(dataConfigDirPath + filenames[2]));
			InputStreamHandle contentHandle4 = new InputStreamHandle();
			contentHandle4.set(new FileInputStream(dataConfigDirPath + filenames[3]));
			InputStreamHandle contentHandle5 = new InputStreamHandle();
			contentHandle5.set(new FileInputStream(dataConfigDirPath + filenames[4]));

			batcher.add("/abs-range-constraint/batcher-contraints1.xml", contentHandle1);
			batcher.add("/abs-range-constraint/batcher-contraints2.xml", contentHandle2);
			batcher.add("/abs-range-constraint/batcher-contraints3.xml", contentHandle3);
			batcher.add("/abs-range-constraint/batcher-contraints4.xml", contentHandle4);
			batcher.add("/abs-range-constraint/batcher-contraints5.xml", contentHandle5);

			// Verify if the batch flushes when batch size is reached.
			// Flush
			batcher.flushAndWait();
			// Hold for asserting the callbacks batch contents, since callback are on different threads than the main JUnit thread.
			// JUnit can not assert on different threads; other than the main one. 
			StringBuilder batchResults = new StringBuilder();
			StringBuilder batchFailResults = new StringBuilder();

			// Run a QueryHostBatcher on the new URIs.
			QueryHostBatcher queryBatcher1 = moveMgr.newQueryHostBatcher(querydef);

			queryBatcher1.onUrisReady((client1, batch)-> {
				for (String str : batch.getItems()) {
					batchResults.append(str)
							.append('|');
				}     
			});
			queryBatcher1.onQueryFailure((client1, throwable)-> {        	
				System.out.println("Exceptions thrown from callback onQueryFailure");        	
				throwable.printStackTrace();
				batchFailResults.append("Test has Exceptions");          	
			} );

			JobTicket jobTicket = moveMgr.startJob(queryBatcher1);
			boolean bJobFinished = queryBatcher1.awaitTermination(3, TimeUnit.MINUTES);		

			if (queryBatcher1.isTerminated()) {

				if (!batchFailResults.toString().isEmpty() && batchFailResults.toString().contains("Exceptions")) {
					fail("Test failed due to exceptions");
				}

				// Verify the batch results now.
				String[] res = batchResults.toString().split("\\|");
				assertEquals("Number of reults returned is incorrect", 1, res.length);
				assertTrue("URI returned not correct", res[0].contains("/abs-range-constraint/batcher-contraints2.xml"));
			}
		}
		catch (Exception e) {
			System.out.println("Exceptions thrown from Test testAndWordQueryWithMultipleForests");
			System.out.println(e.getMessage());
		}
		finally {
			// Associate back the original DB.
			associateRESTServerWithDB(restServerName, dbName);
			detachForest(testMultipleDB, testMultipleForest[0]);
			detachForest(testMultipleDB, testMultipleForest[1]);
			deleteDB(testMultipleDB);
			
			deleteForest(testMultipleForest[0]);
			deleteForest(testMultipleForest[1]);
		}
	}
	
	/*
	 * To test query by example with WriteHostbatcher and QueryHostBatcher.
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@Test
	public void testQueryByExample() throws IOException, InterruptedException
	{	
		System.out.println("Running testQueryByExample");

		String[] filenames = {"constraint1.json", "constraint2.json", "constraint3.json", "constraint4.json", "constraint5.json"};				
		WriteHostBatcher batcher = moveMgr.newWriteHostBatcher();

		batcher.withBatchSize(2);
	
		InputStreamHandle contentHandle1 = new InputStreamHandle();
		contentHandle1.set(new FileInputStream(dataConfigDirPath + filenames[0]));
		InputStreamHandle contentHandle2 = new InputStreamHandle();
		contentHandle2.set(new FileInputStream(dataConfigDirPath + filenames[1]));
		InputStreamHandle contentHandle3 = new InputStreamHandle();
		contentHandle3.set(new FileInputStream(dataConfigDirPath + filenames[2]));
		InputStreamHandle contentHandle4 = new InputStreamHandle();
		contentHandle4.set(new FileInputStream(dataConfigDirPath + filenames[3]));
		InputStreamHandle contentHandle5 = new InputStreamHandle();
		contentHandle5.set(new FileInputStream(dataConfigDirPath + filenames[4]));

		StringBuffer writebatchResults  = new StringBuffer();
		batcher.add("/batcher-contraints1.json", contentHandle1);
		batcher.add("/batcher-contraints2.json", contentHandle2);
		batcher.add("/batcher-contraints3.json", contentHandle3);
		batcher.add("/batcher-contraints4.json", contentHandle4);
		batcher.add("/batcher-contraints5.json", contentHandle5);
		
		// Flush
		batcher.flushAndWait();

		StringBuilder querybatchResults = new StringBuilder();
		StringBuilder querybatchFailResults = new StringBuilder();

		// get the query
		File file = new File(dataConfigDirPath + "qbe1.json");
		FileHandle fileHandle = new FileHandle(file);

		QueryManager queryMgr = client.newQueryManager();
		RawQueryByExampleDefinition qbyexDef = queryMgr.newRawQueryByExampleDefinition(fileHandle.withFormat(Format.JSON));

		// Run a QueryHostBatcher.
		QueryHostBatcher queryBatcher1 = moveMgr.newQueryHostBatcher(qbyexDef);
		queryBatcher1.onUrisReady((client1, batch)-> {

			for (String str : batch.getItems()) {
				querybatchResults.append(str)
						.append('|');
			}

			querybatchResults.append(batch.getBytesMoved())
					.append('|')
					.append(batch.getForest().getForestName())
					.append('|')
					.append(batch.getJobBatchNumber())
					.append('|');

		});
		queryBatcher1.onQueryFailure((client1, throwable)-> {        	
			System.out.println("Exceptions thrown from callback onQueryFailure");        	
			throwable.printStackTrace();
			querybatchFailResults.append("Test has Exceptions");          	
		} );
		JobTicket jobTicket = moveMgr.startJob(queryBatcher1);
		boolean bJobFinished = queryBatcher1.awaitTermination(30, TimeUnit.SECONDS);
		
		if (queryBatcher1.isTerminated()) {

			if (!querybatchFailResults.toString().isEmpty() && querybatchFailResults.toString().contains("Exceptions")) {
				fail("Test failed due to exceptions");
			}

			// Verify the batch results now.
			String[] res = querybatchResults.toString().split("\\|");

			assertTrue("URI returned not correct", res[0].contains("/batcher-contraints1.json"));
			assertEquals("Bytes Moved","0", res[1]);
			assertEquals("Batch Number","0", res[3]);
		}	
	}
		
	/*
	 * To test query by example with WriteHostbatcher and QueryHostBatcher 
	 * with Query Failure (incorrect query syntax).
	 *
	 * @throws IOException
	 * @throws InterruptedException
	 */
	// EA 3 Modify the test for batch failure results. Remove the fail.
	@Ignore
	public void testQueryHostBatcherQueryFailures() throws IOException, InterruptedException
	{	
		System.out.println("Running testQueryHostBatcherQueryFailures");

		String[] filenames = {"constraint1.xml", "constraint2.xml", "constraint3.xml", "constraint4.xml", "constraint5.xml"};				
		WriteHostBatcher batcher = moveMgr.newWriteHostBatcher();

		batcher.withBatchSize(2);
	
		InputStreamHandle contentHandle1 = new InputStreamHandle();
		contentHandle1.set(new FileInputStream(dataConfigDirPath + filenames[0]));
		InputStreamHandle contentHandle2 = new InputStreamHandle();
		contentHandle2.set(new FileInputStream(dataConfigDirPath + filenames[1]));
		InputStreamHandle contentHandle3 = new InputStreamHandle();
		contentHandle3.set(new FileInputStream(dataConfigDirPath + filenames[2]));
		InputStreamHandle contentHandle4 = new InputStreamHandle();
		contentHandle4.set(new FileInputStream(dataConfigDirPath + filenames[3]));
		InputStreamHandle contentHandle5 = new InputStreamHandle();
		contentHandle5.set(new FileInputStream(dataConfigDirPath + filenames[4]));

		batcher.add("/fail-contraints1.xml", contentHandle1);
		batcher.add("/fail-contraints2.xml", contentHandle2);
		batcher.add("/fail-contraints3.xml", contentHandle3);
		batcher.add("/fail-contraints4.xml", contentHandle4);
		batcher.add("/fail-contraints5.xml", contentHandle5);

		// Flush
		batcher.flushAndWait();
		StringBuilder batchResults = new StringBuilder();
		StringBuilder batchFailResults = new StringBuilder();
		// create query def
		String combinedQuery = "{\"search\":" + 
		"{\"query\":{\"value-constraint-query\":{\"constraint-name\":\"id\", \"text\":\"0026\"}}," + 
		"\"options\":{\"return-metrcs\":false, \"return-qtext\":false, \"debug\":true, \"transorm-results\":{\"apply\":\"raw\"}," + 
		"\"constraint\":{\"name\":\"id\", \"value\":{\"element\":{\"ns\":\"\", \"name\":\"id\"}}}}}}";

		System.out.println("QUERY IS : "+ combinedQuery);
		// create a handle for the search criteria
		StringHandle rawHandle = new StringHandle(combinedQuery);
		
		rawHandle.setFormat(Format.JSON);

		QueryManager queryMgr = client.newQueryManager();

		// create a search definition based on the handle
		RawCombinedQueryDefinition querydef = queryMgr.newRawCombinedQueryDefinition(rawHandle);
		
		// Run a QueryHostBatcher.
		QueryHostBatcher queryBatcher1 = moveMgr.newQueryHostBatcher(querydef);
		queryBatcher1.onUrisReady((client1, batch)-> {

			for (String str : batch.getItems()) {
				batchResults.append(str).append('|');
			}

			batchResults.append(batch.getBytesMoved())
					.append('|')
					.append(batch.getForest().getForestName())
					.append('|')
					.append(batch.getJobBatchNumber())
					.append('|');

		});
		queryBatcher1.onQueryFailure((client1, throwable)-> {
			System.out.println("Exceptions thrown from callback onQueryFailure");
			Forest forest = throwable.getSourceForest();
			batchFailResults.append("Test has Exceptions")
					.append('|')
					.append(throwable.getBytesMoved())
					.append('|')
					.append(throwable.getJobRecordNumber())
					.append('|')
					.append(throwable.getBatchRecordNumber())
					.append('|')
					.append(throwable.getSourceUri())
					.append('|')
					.append(throwable.getMimetype())
					.append('|')			
					.append(forest.getForestName())
					.append('|')
					.append(forest.getHost())
					.append('|')
					.append(forest.getDatabaseName())		
					.append('|')
					.append(forest.isDeleteOnly())
					.append('|')
					.append(forest.isUpdateable());
		} );
		JobTicket jobTicket = moveMgr.startJob(queryBatcher1);
		queryBatcher1.awaitTermination(3, TimeUnit.MINUTES);
		
		if (queryBatcher1.isTerminated()) {
			
			if( !batchFailResults.toString().isEmpty() && batchFailResults.toString().contains("Exceptions")) {
				// Write out and assert on query failures.
				System.out.println("Exception Buffer contents on Query Exceptions received from callback onQueryFailure");  
				System.out.println(batchFailResults.toString());
				// Remove this failure once there are no NPEs and doa asserts on various counters in failure scenario.
				fail("Test failed due to exceptions"); 
			}			
		}	
	}
	
	/*
	 * To test QueryHostBatcher's callback support by invoking the client object to do a lookup 
	 * Insert only one document to validate the functionality
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@Test
	public void testQueryHostBatcherCallbackClient() throws IOException, InterruptedException
	{	
		System.out.println("Running testQueryHostBatcherCallbackClient");

		String[] filenames = {"constraint1.json"};				
		WriteHostBatcher batcher = moveMgr.newWriteHostBatcher();

		batcher.withBatchSize(2);
	
		InputStreamHandle contentHandle1 = new InputStreamHandle();
		contentHandle1.set(new FileInputStream(dataConfigDirPath + filenames[0]));
		batcher.add("contraints1.json", contentHandle1);
			
		// Flush
		batcher.flushAndWait();
		StringBuffer batchFailResults  = new StringBuffer();
		String expectedStr = "Vannevar Bush wrote an article for The Atlantic Monthly";
		
		// get the query
		File file = new File(dataConfigDirPath + "qbe1.json");
		FileHandle fileHandle = new FileHandle(file);

		QueryManager queryMgr = client.newQueryManager();
		StringQueryDefinition querydef = queryMgr.newStringDefinition();
		querydef.setCriteria("Vannevar");

		// Run a QueryHostBatcher.
		QueryHostBatcher queryBatcher1 = moveMgr.newQueryHostBatcher(querydef);
				
		queryBatcher1.withBatchSize(1000);
		//Hold for contents read back from callback client.
		StringBuffer ccBuf = new StringBuffer();
		
		queryBatcher1.onUrisReady((client1, batch)-> {			
			// Do a lookup back into the database with the client and batch content.			
			// Want to verify if the client object can be utilized from a Callback.
			JSONDocumentManager docMgr = client1.newJSONDocumentManager();
			JacksonHandle jh = new JacksonHandle();
			docMgr.read(batch.getItems()[0], jh);
			System.out.println("JH Contents is " + jh.get().toString());
			System.out.println("Batch Contents is " + batch.getItems()[0]);
			
			ccBuf.append(jh.get().toString());
		});
		queryBatcher1.onQueryFailure((client1, throwable)-> {        	
			System.out.println("Exceptions thrown from callback onQueryFailure");        	
			
			batchFailResults.append("Test has Exceptions").append('|');
		} );
		JobTicket jobTicket = moveMgr.startJob(queryBatcher1);
		queryBatcher1.awaitTermination(3, TimeUnit.MINUTES);
							
		if (queryBatcher1.isTerminated()) {
			
			if( !batchFailResults.toString().isEmpty() && batchFailResults.toString().contains("Exceptions")) {
				// Write out and assert on query failures.
				System.out.println("Exception Buffer contents on Query Exceptions received from callback onQueryFailure");  
				System.out.println(batchFailResults.toString());				
				fail("Test failed due to exceptions"); 
			}
			System.out.println("Contents from the callback are : " + ccBuf.toString());
			// Verify the Callback contents.
			assertTrue("Lookup for a document from Callback using the client failed", ccBuf.toString().contains(expectedStr));
		}
	}
	
	/*
	 * Test to validate QueryHostBatcher when there is no data.
	 * No search results are returned. 
	 */
	@Test
	public void testQueryHostBatcherWithNoData() throws IOException, InterruptedException
	{	
		System.out.println("Running testQueryHostBatcherWithNoData");

		String jsonDoc = "{" +
				"\"employees\": [" +
				"{ \"firstName\":\"John\" , \"lastName\":\"Doe\" }," +
				"{ \"firstName\":\"Ann\" , \"lastName\":\"Smith\" }," +
				"{ \"firstName\":\"Bob\" , \"lastName\":\"Foo\" }]" +
				"}";
		// create query def
		QueryManager queryMgr = client.newQueryManager();

		StringQueryDefinition querydef = queryMgr.newStringDefinition();
		querydef.setCriteria("John AND Bob");	
		
		// Run a QueryHostBatcher when no results are returned.
		QueryHostBatcher queryBatcherNoResult = moveMgr.newQueryHostBatcher(querydef);

		StringBuilder batchNoResults = new StringBuilder();
		StringBuilder batchNoFailResults = new StringBuilder();

		queryBatcherNoResult.onUrisReady((client1, batch) -> {
			for (String str : batch.getItems()) {
				batchNoResults.append(str).append('|');
			}
		});
		queryBatcherNoResult.onQueryFailure((client1, throwable)-> {        	
			System.out.println("Exceptions thrown from callback onQueryFailure when no results returned");        	
			// Should be empty in a successful run. Else fill the buffer to report error.
			batchNoFailResults.append("Test has Exceptions");
			batchNoFailResults.append("|");
		});
		JobTicket jobTicketNoRes = moveMgr.startJob(queryBatcherNoResult);
		queryBatcherNoResult.awaitTermination(30, TimeUnit.SECONDS);
		
		if (queryBatcherNoResult.isTerminated()) {
			assertTrue("Query returned no results when there is no data" , batchNoResults.toString().isEmpty());			
		}		
	}

	/*
	 * To test query by example with WriteHostbatcher and QueryHostBatcher 
	 * 1) Verify batch size on QueryHostBatcher.
	 * 
	 *
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@Test
	public void testQueryHostBatcherBatchSize() throws IOException, InterruptedException
	{	
		System.out.println("Running testQueryHostBatcherBatchSize");

		String jsonDoc = "{" +
				"\"employees\": [" +
				"{ \"firstName\":\"John\" , \"lastName\":\"Doe\" }," +
				"{ \"firstName\":\"Ann\" , \"lastName\":\"Smith\" }," +
				"{ \"firstName\":\"Bob\" , \"lastName\":\"Foo\" }]" +
				"}";
		// create query def
		QueryManager queryMgr = client.newQueryManager();

		StringQueryDefinition querydef = queryMgr.newStringDefinition();
		querydef.setCriteria("John AND Bob");	
		
		WriteHostBatcher batcher = moveMgr.newWriteHostBatcher();
		batcher.withBatchSize(1000);
		StringHandle handle = new StringHandle();
		handle.set(jsonDoc);
		String uri = null;
		
		// Insert 1K documents
		for (int i = 0; i < 1000; i++) {
			uri = "/firstName" + i + ".json";
			batcher.add(uri, handle);
		}

		// Flush
		batcher.flushAndWait();
		StringBuffer batchResults  = new StringBuffer();
		StringBuffer batchFailResults  = new StringBuffer();
			
		// Run a QueryHostBatcher with a large AwaitTermination.
		QueryHostBatcher queryBatcherbatchSize = moveMgr.newQueryHostBatcher(querydef);
		queryBatcherbatchSize.withBatchSize(20);
		
		Calendar  calBef = Calendar.getInstance();
		long before = calBef.getTimeInMillis();
		
		queryBatcherbatchSize.onUrisReady((client1, batch)-> {
			batchResults.append(batch.getJobBatchNumber()).append('|');
			System.out.println("Batch Numer is " + batch.getJobBatchNumber());

		});
		queryBatcherbatchSize.onQueryFailure((client1, throwable)-> {
			System.out.println("Exceptions thrown from callback onQueryFailure");

			batchFailResults.append("Test has Exceptions")
					.append('|');
		});
		JobTicket jobTicket = moveMgr.startJob(queryBatcherbatchSize);
		// Make sure to modify TimeUnit.TIMEUNIT.Method(duration) below before the assert
		queryBatcherbatchSize.awaitTermination(3, TimeUnit.MINUTES);
			
		Calendar  calAft;
		long after = 0L;
		long duration = 0L;
		long queryJobTimeoutValue = 0L;
		
		while(!queryBatcherbatchSize.isTerminated()) {
			// do nothing.
		}	
		// Check the time of termination
		calAft = Calendar.getInstance();
		after = calAft.getTimeInMillis();
		duration = after - before;
		queryJobTimeoutValue = TimeUnit.MINUTES.toSeconds(duration);
			
		if (queryBatcherbatchSize.isTerminated()) {			
			System.out.println("Duration is ===== " + queryJobTimeoutValue);
	        System.out.println(batchResults.toString());
	        
	        assertEquals("Number of batches should have been 50", batchResults.toString().split("\\|").length, 50);
		}
		//Clear the contents for next query host batcher object results.
		batchResults.delete(0, (batchResults.capacity() -1));
		batchFailResults.delete(0, (batchFailResults.capacity() -1));
		// Run a QueryHostBatcher with a small AwaitTermination.
		QueryHostBatcher queryBatcherSmallTimeout = moveMgr.newQueryHostBatcher(querydef);
		queryBatcherSmallTimeout.withBatchSize(1000);
		
		queryBatcherSmallTimeout.onUrisReady((client1, batch)-> {
			batchResults.append(batch.getJobBatchNumber()).append('|');
			System.out.println("QueryHostBatcher with 1000 batch size - Batch Numer is " + batch.getJobBatchNumber());

		});
		queryBatcherSmallTimeout.onQueryFailure((client1, throwable) -> {
			System.out.println("Exceptions thrown from callback onQueryFailure");

			batchFailResults.append("Test has Exceptions").append('|');
			batchFailResults.append(throwable.getBatchRecordNumber());
		});
		JobTicket jobTicketTimeout = moveMgr.startJob(queryBatcherSmallTimeout);
		queryBatcherSmallTimeout.awaitTermination(5, TimeUnit.MILLISECONDS);
		if (queryBatcherSmallTimeout.isTerminated()) {					
	        System.out.println(batchResults.toString());	        
	        assertNotEquals("Number of batches should not have been 1", batchResults.toString().split("\\|").length, 5);
		}
		if (batchFailResults!= null && !batchFailResults.toString().isEmpty()) {
			assertTrue("Exceptions not found when query time out value reached", batchFailResults.toString().contains("Test has Exceptions"));
		}

	}

	/*
	 * To test query by example with WriteHostbatcher and QueryHostBatcher 
	 * 1) Verify awaitTermination method on QueryHostBatcher.
	 * 
	 *
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@Test
	public void testQueryHostBatcherFailures() throws IOException, InterruptedException
	{	
		System.out.println("Running testQueryHostBatcherFailures");

		String jsonDoc = "{" +
				"\"employees\": [" +
				"{ \"firstName\":\"John\" , \"lastName\":\"Doe\" }," +
				"{ \"firstName\":\"Ann\" , \"lastName\":\"Smith\" }," +
				"{ \"firstName\":\"Bob\" , \"lastName\":\"Foo\" }]" +
				"}";
		// create query def
		QueryManager queryMgr = client.newQueryManager();

		StringQueryDefinition querydef = queryMgr.newStringDefinition();
		querydef.setCriteria("John AND Bob");	
		
		WriteHostBatcher batcher = moveMgr.newWriteHostBatcher();
		batcher.withBatchSize(1000);
		StringHandle handle = new StringHandle();
		handle.set(jsonDoc);
		String uri = null;
		
		// Insert 10 K documents
		for (int i = 0; i < 10000; i++) {
			uri = "/firstName" + i + ".json";
			batcher.add(uri, handle);
		}
		
		// Flush
		batcher.flushAndWait();
		StringBuilder batchResults = new StringBuilder();
		StringBuilder batchFailResults = new StringBuilder();

		// Run a QueryHostBatcher with AwaitTermination.
		QueryHostBatcher queryBatcherAwait = moveMgr.newQueryHostBatcher(querydef);
		
		Calendar  calBef = Calendar.getInstance();
		long before = calBef.getTimeInMillis();
		
		JobTicket jobTicket = moveMgr.startJob(queryBatcherAwait);
		// Make sure to modify TimeUnit.MILLISECONDS.Method(duration) below before the assert
		queryBatcherAwait.awaitTermination(30, TimeUnit.SECONDS);
		
		queryBatcherAwait.onUrisReady((client1, batch)-> {
			for (String str : batch.getItems()) {
				batchResults.append(str)
						.append('|');
			}
		});
		queryBatcherAwait.onQueryFailure((client1, throwable)-> {
			System.out.println("Exceptions thrown from callback onQueryFailure");

			batchFailResults.append("Test has Exceptions")
					.append('|');
		});
			
		Calendar  calAft;
		long after = 0L;
		long duration = 0L;
		long quertJobTimeoutValue = 0L;
		
		while(!queryBatcherAwait.isTerminated()) {
			// do nothing
		}	
		// Check the time of termination
		calAft = Calendar.getInstance();
		after = calAft.getTimeInMillis();
		duration = after - before;
		quertJobTimeoutValue = TimeUnit.MILLISECONDS.toSeconds(duration);
			
		if (queryBatcherAwait.isTerminated()) {
			System.out.println("Duration is " + quertJobTimeoutValue);
			if (quertJobTimeoutValue >= 30 && quertJobTimeoutValue < 35) {
				assertTrue("Job termination with awaitTermination passed within specified time", quertJobTimeoutValue >= 30 && quertJobTimeoutValue < 35);
			} else if (quertJobTimeoutValue > 35) {
				fail("Job termination with awaitTermination failed");
			}
		}
	}
	
	
	@Test	
	public void testServerXQueryTransform() throws IOException, ParserConfigurationException, SAXException, TransformerException, InterruptedException, XPathExpressionException
	{	
		System.out.println("Running testServerXQueryTransform");		
		
		TransformExtensionsManager transMgr = 
				client.newServerConfigManager().newTransformExtensionsManager();
		ExtensionMetadata metadata = new ExtensionMetadata();
		metadata.setTitle("Adding attribute xquery Transform");
		metadata.setDescription("This plugin transforms an XML document by adding attribute to root node");
		metadata.setProvider("MarkLogic");
		metadata.setVersion("0.1");
		// get the transform file from add-attr-xquery-transform.xqy
		File transformFile = new File(dataConfigDirPath +"add-attr-xquery-transform.xqy");
		FileHandle transformHandle = new FileHandle(transformFile);
		transMgr.writeXQueryTransform("add-attr-xquery-transform", transformHandle, metadata);
		ServerTransform transform = new ServerTransform("add-attr-xquery-transform");
		transform.put("name", "Lang");
		transform.put("value", "English");
		
		String xmlStr1 = "<?xml  version=\"1.0\" encoding=\"UTF-8\"?><foo>This is so foo</foo>";
		String xmlStr2 = "<?xml  version=\"1.0\" encoding=\"UTF-8\"?><foo>This is so bar</foo>";
			
		//Use WriteHostbatcher to write the same files.				
		WriteHostBatcher batcher = moveMgr.newWriteHostBatcher();

		batcher.withBatchSize(5);
		batcher.withTransform(transform);
		StringHandle handleFoo = new StringHandle();
		handleFoo.set(xmlStr1);
		
		StringHandle handleBar = new StringHandle();
		handleBar.set(xmlStr2);
		String uri = null;
		
		// Insert 10 documents
		for (int i = 0; i < 10; i++) {
			uri = "foo" + i + ".xml";
			batcher.add(uri, handleFoo);
		}
		
		for (int i = 0; i < 10; i++) {
			uri = "bar" + i + ".xml";
			batcher.add(uri, handleBar);
		}
		// Flush
		batcher.flushAndWait();
		
		StringBuffer batchResults  = new StringBuffer();
		StringBuffer batchFailResults  = new StringBuffer();
		
		// create query def
		QueryManager queryMgr = client.newQueryManager();
		StringQueryDefinition querydef = queryMgr.newStringDefinition();
		querydef.setCriteria("foo OR bar");	
						
		// Run a QueryHostBatcher on the new URIs.
		QueryHostBatcher queryBatcher1 = moveMgr.newQueryHostBatcher(querydef);
		queryBatcher1.withBatchSize(5);
						
		queryBatcher1.onUrisReady((client1, batch)-> {
			for (String str : batch.getItems()) {
				batchResults.append(str);
				batchResults.append("|");
			}
		});
		queryBatcher1.onQueryFailure((client1, throwable)-> {        	
			System.out.println("Exceptions thrown from callback onQueryFailure");        	
			throwable.printStackTrace();
			batchFailResults.append("Test has Exceptions");          	
		} );
		JobTicket jobTicket = moveMgr.startJob(queryBatcher1);
		
		if (queryBatcher1.isTerminated()) {
			// Verify the batch results now.
			String[] res = batchResults.toString().split("\\|");
			assertEquals("Query results URI list length returned after transformation incorrect", res.length, 20);
			
			// Get a random URI, since the URIs returned are not ordered. Get the 3rd URI.			
			assertTrue("URI returned not correct", res[2].contains("foo") || res[2].contains("bar"));
			
			// do a lookup with the first URI using the client to verify transforms are done.					
			DOMHandle readHandle = readDocumentUsingDOMHandle(client, res[0], "XML");
			String contents = readHandle.evaluateXPath("/foo/text()", String.class);
			// Verify that the contents are of xmlStr1 or xmlStr2.
						
			System.out.println("Contents are : " + contents);
			assertTrue("Lookup for a document from Callback using the client failed", xmlStr1.contains(contents) || xmlStr2.contains(contents));
		}
		// release client
		client.release();		
	}

	/*
	 * To test QueryHostBatcher functionality (errors if any) when a Forest is being removed and added during a start job.  
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@Test
	public void testQueryBatcherWithForestRemoveAndAdd() throws IOException, InterruptedException
	{	
		System.out.println("Running testQueryBatcherWithForestRemoveAndAdd");
		String testMultipleDB = "QBMultipleForestDB";
		String[] testMultipleForest = {"QBMultipleForestDB-1", "QBMultipleForestDB-2", "QBMultipleForestDB-3"};
		
		try {
			//Setup a separate database/
			createDB(testMultipleDB);
			createForest(testMultipleForest[0], testMultipleDB);
			setupAppServicesConstraint(testMultipleDB);
			
			associateRESTServerWithDB(restServerName, testMultipleDB);
			
			String jsonDoc = "{" +
					"\"employees\": [" +
					"{ \"firstName\":\"John\" , \"lastName\":\"Doe\" }," +
					"{ \"firstName\":\"Ann\" , \"lastName\":\"Smith\" }," +
					"{ \"firstName\":\"Bob\" , \"lastName\":\"Foo\" }]" +
					"}";
			// create query def
			QueryManager queryMgr = client.newQueryManager();

			StringQueryDefinition querydef = queryMgr.newStringDefinition();
			querydef.setCriteria("John AND Bob");	

			WriteHostBatcher batcher = moveMgr.newWriteHostBatcher();
			batcher.withBatchSize(1000);
			StringHandle handle = new StringHandle();
			handle.set(jsonDoc);
			String uri = null;

			// Insert 20K documents to have a sufficient large query seek time
			for (int i = 0; i < 20000; i++) {
				uri = "/firstName" + i + ".json";
				batcher.add(uri, handle);
			}

			// Flush
			batcher.flushAndWait();
			StringBuffer batchResults  = new StringBuffer();
			StringBuffer batchFailResults  = new StringBuffer();

			// Run a QueryHostBatcher with AwaitTermination.
			QueryHostBatcher queryBatcherAddForest = moveMgr.newQueryHostBatcher(querydef);
			queryBatcherAddForest.withBatchSize(2000);

			queryBatcherAddForest.onUrisReady((client1, batch)-> {
				batchResults.append(batch.getJobBatchNumber())
						.append('|');
				System.out.println("Batch Numer is " + batch.getJobBatchNumber());
			});
			queryBatcherAddForest.onQueryFailure((client1, throwable) -> {
				System.out.println("Exceptions thrown from callback onQueryFailure");

				batchFailResults.append("Test has Exceptions")
						.append('|')
						.append(throwable.getMessage());
			});

			JobTicket jobTicket = moveMgr.startJob(queryBatcherAddForest);
			queryBatcherAddForest.awaitTermination(3, TimeUnit.MINUTES);

			// Now add a Forests to the database.
			createForest(testMultipleForest[1], testMultipleDB);
			createForest(testMultipleForest[2], testMultipleDB);
			while(!queryBatcherAddForest.isTerminated()) {
				// Do nothing. Wait for batcher to complete.
			}	
		
			if (queryBatcherAddForest.isTerminated()) {					
				if (batchResults!= null && !batchResults.toString().isEmpty()) {
					System.out.print("Results from onUrisReady === ");
					System.out.print(batchResults.toString());
					// We should be having 10 batches numbered 1 to 10.
					assertTrue("Batches not complete in results", batchResults.toString().contains("10"));
				}
				if (batchFailResults!= null && !batchFailResults.toString().isEmpty()) {
					System.out.print("Results from onQueryFailure === ");
					System.out.print(batchFailResults.toString());
					assertTrue("Exceptions not found when forest added", batchFailResults.toString().contains("Test has Exceptions"));
				}
			}
			
			// Reomove a forest.
			StringBuffer batchResultsRem  = new StringBuffer();
			StringBuffer batchFailResultsRem  = new StringBuffer();
			
			// Run a QueryHostBatcher with AwaitTermination.
			QueryHostBatcher queryBatcherRemoveForest = moveMgr.newQueryHostBatcher(querydef);
			queryBatcherRemoveForest.withBatchSize(2000);

			queryBatcherRemoveForest.onUrisReady((client1, batch)-> {
				batchResultsRem.append(batch.getJobBatchNumber())
						.append('|');
				System.out.println("Batch Numer is " + batch.getJobBatchNumber());
			});
			queryBatcherRemoveForest.onQueryFailure((client1, throwable)-> {        	
				System.out.println("Exceptions thrown from callback onQueryFailure");        	

				batchFailResultsRem.append("Test has Exceptions");
				batchFailResultsRem.append("|");
				batchFailResultsRem.append(throwable.getMessage());
			});

			JobTicket jobTicketRem = moveMgr.startJob(queryBatcherRemoveForest);
			queryBatcherRemoveForest.awaitTermination(3, TimeUnit.MINUTES);

			// Now remove a Forest from the database.
			detachForest(testMultipleDB, testMultipleForest[2]);
			deleteForest(testMultipleForest[2]);
			while(!queryBatcherRemoveForest.isTerminated()) {
				// Do nothing. Wait for batcher to complete.
			}	

			if (queryBatcherRemoveForest.isTerminated()) {					
				if (batchResultsRem!= null && !batchResultsRem.toString().isEmpty()) {
					System.out.print("Results from onUrisReady === ");
					// We should be having 10 batches numbered 1 to 10.
					//TODO Add rest of the validations when feature complete.
					System.out.print(batchResultsRem.toString());
					assertTrue("Batches not complete in results when forest removed", batchResultsRem.toString().contains("10"));
				}
				if (batchFailResultsRem!= null && !batchFailResultsRem.toString().isEmpty()) {
					System.out.print("Results from onQueryFailure === ");
					System.out.print(batchFailResultsRem.toString());
					assertTrue("Exceptions not found when forest removed", batchFailResultsRem.toString().contains("Test has Exceptions"));
				}
			}
		}
		catch(Exception e) {
			System.out.print(e.getMessage());
		}

		finally {
			// Associate back the original DB.
			try {
				associateRESTServerWithDB(restServerName, dbName);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			detachForest(testMultipleDB, testMultipleForest[0]);
			detachForest(testMultipleDB, testMultipleForest[1]);
			//In case something asserts
			detachForest(testMultipleDB, testMultipleForest[2]);
			deleteDB(testMultipleDB);

			deleteForest(testMultipleForest[0]);
			deleteForest(testMultipleForest[1]);
			deleteForest(testMultipleForest[2]);
		}
	}

	/*
	 * To test QueryHostBatcher's callback support with long lookup timefor the client object to do a lookup 
	 * Insert documents to validate the functionality.
	 * Induce a long pause which exceeds awaitTermination time.
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@Test
	public void testBatchClientLookupTimeout() throws IOException, InterruptedException
	{	
		System.out.println("Running testBatchClientLookupTimeout");
		String testMultipleDB = "QBMultipleForestDB";
		String[] testMultipleForest = {"QBMultipleForestDB-1"};
		
		try {
			//Setup a separate database/
			createDB(testMultipleDB);
			createForest(testMultipleForest[0], testMultipleDB);
			
			associateRESTServerWithDB(restServerName, testMultipleDB);
			setupAppServicesConstraint(testMultipleDB);

			String jsonDoc = "{" +
					"\"employees\": [" +
					"{ \"firstName\":\"John\" , \"lastName\":\"Doe\" }," +
					"{ \"firstName\":\"Ann\" , \"lastName\":\"Smith\" }," +
					"{ \"firstName\":\"Bob\" , \"lastName\":\"Foo\" }]" +
					"}";
			// create query def
			QueryManager queryMgr = client.newQueryManager();

			StringQueryDefinition querydef = queryMgr.newStringDefinition();
			querydef.setCriteria("John AND Bob");	

			WriteHostBatcher batcher = moveMgr.newWriteHostBatcher();
			batcher.withBatchSize(1000);
			StringHandle handle = new StringHandle();
			handle.set(jsonDoc);
			String uri = null;

			// Insert 2K documents to have a sufficient large query seek time
			for (int i = 0; i < 20000; i++) {
				uri = "/firstName" + i + ".json";
				batcher.add(uri, handle);
			}

			// Flush
			batcher.flushAndWait();
			StringBuilder batchResults = new StringBuilder();
			StringBuffer batchFailResults = new StringBuffer();
			StringBuilder ccBuf = new StringBuilder();

			// Run a QueryHostBatcher with AwaitTermination.
			QueryHostBatcher queryBatcherAddForest = moveMgr.newQueryHostBatcher(querydef);
			queryBatcherAddForest.withBatchSize(200);

			queryBatcherAddForest.onUrisReady((client1, batch)-> {
				// Check only once
				if (ccBuf.toString().isEmpty())
				{
					JSONDocumentManager docMgr = client1.newJSONDocumentManager();
					JacksonHandle jh = new JacksonHandle();
					docMgr.read(batch.getItems()[0], jh);
					try {
						// Simulate a large time in reading back the results
						Thread.sleep(40000);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						System.out.println(e.getMessage());
					}			
					ccBuf.append(jh.get().toString().trim());
					// The first read should exhaust the awaitTermination timeout. Buffer contains only one result.
					System.out.println("JH Contents is " + jh.get().toString());
					System.out.println("Batch Contents is " + batch.getItems()[0]);						
				}
				
				batchResults.append(batch.getJobBatchNumber());
				batchResults.append("|");
			System.out.println("Batch Numer is " + batch.getJobBatchNumber());
			});
			queryBatcherAddForest.onQueryFailure((client1, throwable) -> {
				System.out.println("Exceptions thrown from callback onQueryFailure");

				batchFailResults.append("Test has Exceptions");
				batchFailResults.append("|");
				batchFailResults.append(throwable.getMessage());
			});
			// Have a small awaitTerminatio timeout for the batcher.
			JobTicket jobTicket = moveMgr.startJob(queryBatcherAddForest);
			queryBatcherAddForest.awaitTermination(30, TimeUnit.SECONDS);
	
			if (queryBatcherAddForest.isTerminated()) {					
			
				if (batchFailResults!= null && !batchFailResults.toString().isEmpty()) {
					System.out.print("Results from onQueryFailure === ");
					System.out.print(batchFailResults.toString());
					assertTrue("Exceptions not found when forest added", batchFailResults.toString().contains("Test has Exceptions"));
				}
			}
			assertTrue("Batches are available in results when they should not be.", batchResults.toString().isEmpty());
		}
		catch(Exception e) {
			System.out.print(e.getMessage());
		}
		finally {
			// Associate back the original DB.
			try {
				associateRESTServerWithDB(restServerName, dbName);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			detachForest(testMultipleDB, testMultipleForest[0]);
			deleteDB(testMultipleDB);
			deleteForest(testMultipleForest[0]);
		}
	}

	/*
	 * To test QqueryHostBatcher when WriteHostbatcher writes same document. Simulate a deadlock / resource contention.
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@Test
	public void testSimultaneousBothBatcherAccess() throws IOException, InterruptedException
	{	
		System.out.println("Running testSimultaneousBothBatcherAccess");
		clearDB(restServerPort);

		String[] filenames = {"constraint1.json", "constraint2.json", "constraint3.json", "constraint4.json", "constraint5.json"};				
		WriteHostBatcher batcher = moveMgr.newWriteHostBatcher();

		batcher.withBatchSize(2);
	
		InputStreamHandle contentHandle1 = new InputStreamHandle();
		contentHandle1.set(new FileInputStream(dataConfigDirPath + filenames[0]));
		InputStreamHandle contentHandle2 = new InputStreamHandle();
		contentHandle2.set(new FileInputStream(dataConfigDirPath + filenames[1]));
		InputStreamHandle contentHandle3 = new InputStreamHandle();
		contentHandle3.set(new FileInputStream(dataConfigDirPath + filenames[2]));
		InputStreamHandle contentHandle4 = new InputStreamHandle();
		contentHandle4.set(new FileInputStream(dataConfigDirPath + filenames[3]));
		InputStreamHandle contentHandle5 = new InputStreamHandle();
		contentHandle5.set(new FileInputStream(dataConfigDirPath + filenames[4]));

		StringBuilder writebatchResults = new StringBuilder();
		batcher.add("/batcher-contraints1.json", contentHandle1);
		batcher.add("/batcher-contraints2.json", contentHandle2);
		batcher.add("/batcher-contraints3.json", contentHandle3);
		batcher.add("/batcher-contraints4.json", contentHandle4);
		batcher.add("/batcher-contraints5.json", contentHandle5);

		// Flush
		batcher.flushAndWait();

		StringBuffer querybatchResults = new StringBuffer();
		StringBuilder querybatchFailResults = new StringBuilder();

		// get the query
		File file = new File(dataConfigDirPath + "qbe1.json");
		FileHandle fileHandle = new FileHandle(file);

		QueryManager queryMgr = client.newQueryManager();
		RawQueryByExampleDefinition qbyexDef = queryMgr.newRawQueryByExampleDefinition(fileHandle.withFormat(Format.JSON));

		// Run a QueryHostBatcher.
		QueryHostBatcher queryBatcher1 = moveMgr.newQueryHostBatcher(qbyexDef);
		queryBatcher1.onUrisReady((client1, batch)-> {
						
			for (String str : batch.getItems()) {
				querybatchResults.append(str)
						.append('|');
			}

			querybatchResults.append(batch.getBytesMoved())
					.append('|')
					.append(batch.getForest().getForestName())
					.append('|')
					.append(batch.getJobBatchNumber())
					.append('|');

		});
		queryBatcher1.onQueryFailure((client1, throwable)-> {        	
			System.out.println("Exceptions thrown from callback onQueryFailure");        	
			throwable.printStackTrace();
			querybatchFailResults.append("Test has Exceptions");
			querybatchFailResults.append(throwable.getMessage());
		} );
		
		// Trying to use a WriteHostBatcher on the same docId.
		WriteHostBatcher batcherTwo = moveMgr.newWriteHostBatcher();
		String jsonDoc = "{" +
				"\"employees\": [" +
				"{ \"firstName\":\"John\" , \"lastName\":\"Doe\" }," +
				"{ \"firstName\":\"Ann\" , \"lastName\":\"Smith\" }," +
				"{ \"firstName\":\"Bob\" , \"lastName\":\"Foo\" }]" +
				"}";
		StringHandle handle = new StringHandle();
		handle.set(jsonDoc);

		// Update contents to same doc uri.
		batcherTwo.withBatchSize(1);
		batcherTwo.add("/batcher-contraints11.json", handle);
		batcherTwo.flushAndWait();
		
		JobTicket jobTicketWriteTwo = moveMgr.startJob(batcherTwo);
		
		JobTicket jobTicket = moveMgr.startJob(queryBatcher1);
		queryBatcher1.awaitTermination(1, TimeUnit.MINUTES);
			
		if (queryBatcher1.isTerminated()) {
			
			if( !querybatchFailResults.toString().isEmpty() && querybatchFailResults.toString().contains("Exceptions"))
			{
				System.out.println("Query Batch Failed - Buffer Contents are:" + querybatchFailResults.toString());
				fail("Test failed due to exceptions"); 
			}
			if( querybatchResults != null &&  !querybatchResults.toString().isEmpty()) {
			// Verify the batch results now.
			String[] res = querybatchResults.toString().split("\\|");

			assertTrue("URI returned not correct", res[0].contains("/batcher-contraints1.json"));
			assertEquals("Bytes Moved","0", res[1]);
			assertEquals("Batch Number","0", res[3]);
			}
		}	
	}
	
	@Test
	public void testQueryBatcherJobDetails() throws Exception
	{
		String testMultipleDB = "QHBJobDetaitDB";
		String[] testMultipleForest = {"QHBJobDetaitDB-1"};
		
		try {
			System.out.println("Running testQueryBatcherJobDetails");

			//Setup a separate database/
			createDB(testMultipleDB);
			createForest(testMultipleForest[0], testMultipleDB);		
			associateRESTServerWithDB(restServerName, testMultipleDB);

			setupAppServicesConstraint(testMultipleDB);

			String[] filenames = {"constraint1.xml", "constraint2.xml", "constraint3.xml", "constraint4.xml", "constraint5.xml"};
			String queryOptionName = "absRangeConstraintWithVariousGrammarAndWordQueryOpt.xml";

			setQueryOption(clientQHB, queryOptionName);

			QueryManager queryMgr = clientQHB.newQueryManager();

			StringQueryDefinition querydef = queryMgr.newStringDefinition();
			querydef.setCriteria("0012");

			//Use WriteHostbatcher to write the same files.				
			WriteHostBatcher batcher = moveMgr.newWriteHostBatcher();

			batcher.withBatchSize(2);
			InputStreamHandle contentHandle1 = new InputStreamHandle();
			contentHandle1.set(new FileInputStream(dataConfigDirPath + filenames[0]));
			InputStreamHandle contentHandle2 = new InputStreamHandle();
			contentHandle2.set(new FileInputStream(dataConfigDirPath + filenames[1]));
			InputStreamHandle contentHandle3 = new InputStreamHandle();
			contentHandle3.set(new FileInputStream(dataConfigDirPath + filenames[2]));
			InputStreamHandle contentHandle4 = new InputStreamHandle();
			contentHandle4.set(new FileInputStream(dataConfigDirPath + filenames[3]));
			InputStreamHandle contentHandle5 = new InputStreamHandle();
			contentHandle5.set(new FileInputStream(dataConfigDirPath + filenames[4]));

			batcher.add("/abs-range-constraint/batcher-contraints1.xml", contentHandle1);
			batcher.add("/abs-range-constraint/batcher-contraints2.xml", contentHandle2);
			batcher.add("/abs-range-constraint/batcher-contraints3.xml", contentHandle3);
			batcher.add("/abs-range-constraint/batcher-contraints4.xml", contentHandle4);
			batcher.add("/abs-range-constraint/batcher-contraints5.xml", contentHandle5);

			// Flush
			batcher.flushAndWait();

			StringBuilder batchResults = new StringBuilder();
			StringBuilder batchDetails = new StringBuilder();
			StringBuilder forestDetails = new StringBuilder();
			StringBuilder jobDetails = new StringBuilder();
			StringBuilder batchFailResults = new StringBuilder();

			// Run a QueryHostBatcher on the new URIs.
			QueryHostBatcher queryBatcher1 = moveMgr.newQueryHostBatcher(querydef);

			queryBatcher1.onUrisReady((client1, batch)-> {
				for (String str : batch.getItems()) {
					batchResults.append(str)
					.append('|');
					// Batch details
					batchDetails.append(batch.getJobBatchNumber())
					.append('|')
					.append(batch.getJobResultsSoFar())
					.append('|')
					.append(batch.getForestBatchNumber())
					.append('|')
					.append(batch.getBytesMoved());
					
					// Get the Forest details
					Forest forest = batch.getForest();
					forestDetails.append(forest.getDatabaseName())
					.append('|')
					.append(forest.getHost())
					.append('|')
					.append(forest.getForestName());
					
					// Get the Job details					
					/*jobDetails.append(batch.getJobTicket().getJobId())
					.append('|')
					.append(batch.getJobTicket().getJobType().name());*/
				}     
			});
			queryBatcher1.onQueryFailure((client1, throwable)-> {        	
				System.out.println("Exceptions thrown from callback onQueryFailure");        	
				throwable.printStackTrace();
				batchFailResults.append("Test has Exceptions");          	
			} );

			JobTicket jobTicket = moveMgr.startJob(queryBatcher1);
			String jobId = jobTicket.getJobId();
			String jobName = jobTicket.getJobType().name();
						
			boolean bJobFinished = queryBatcher1.awaitTermination(3, TimeUnit.MINUTES);		

			if (queryBatcher1.isTerminated()) {

				if (!batchFailResults.toString().isEmpty() && batchFailResults.toString().contains("Exceptions")) {
					fail("Test failed due to exceptions");
				}

				// Verify the batch results now.
				String[] res = batchResults.toString().split("\\|");
				assertEquals("Number of reults returned is incorrect", 1, res.length);
				assertTrue("URI returned not correct", res[0].contains("/abs-range-constraint/batcher-contraints2.xml"));
				
				// verify the Job and batch get method values.
				String[] batchDetailsArray = batchDetails.toString().split("\\|");
				
				assertTrue("Job Batch Number not correct", Long.parseLong(batchDetailsArray[0]) > 0);
				assertTrue("Job Results So Far Number not correct", Long.parseLong(batchDetailsArray[1]) > 0);
				assertTrue("Forest Batch Number not correct", Long.parseLong(batchDetailsArray[2]) > 0);
				// Git Isue 124. For bytesMoved.
				assertTrue("Job Bytes Moved not correct", Long.parseLong(batchDetailsArray[3]) == 0);
								
				// verify the forest get method values.
				String[] forestDetailsArray = forestDetails.toString().split("\\|");
				assertTrue("Database name returned from batch is not correct", forestDetailsArray[0].equalsIgnoreCase(dbName));
				assertTrue("Forest name returned from batch is not correct", forestDetailsArray[2].equalsIgnoreCase(testMultipleForest[0]) ||
						                                                       forestDetailsArray[2].equalsIgnoreCase(testMultipleForest[1]));
				// verify the job ticket get method values. This needs to be implemented.
				/*String[] jobTicketArray = jobDetails.toString().split("\\|");
				assertTrue("Job Id returned from batch is not correct", jobTicketArray[0].equalsIgnoreCase(jobId));
				assertTrue("Job Type name returned from batch is not correct", forestDetailsArray[2].equalsIgnoreCase(jobName));*/
			}
		}
		catch (Exception e) {
			System.out.println("Exceptions thrown from Test testAndWordQueryWithMultipleForests");
			System.out.println(e.getMessage());
		}
		finally {
			// Associate back the original DB.
			associateRESTServerWithDB(restServerName, dbName);
			detachForest(testMultipleDB, testMultipleForest[0]);
			deleteDB(testMultipleDB);
			
			deleteForest(testMultipleForest[0]);
		}
	}
	
	/*
	 * These are test methods that verify that different query types work.
	 * Testing - 
	 * Word query
	 * range query
	 * value query
	 */
	@Test
	public void testDifferentQueryTypes() throws Exception
	{
		String testMultipleDB = "QBtestDifferentQueryTypesDB";
		String[] testMultipleForest = {"QBtestDifferentQueryTypesDB-1"};
		
		try {
			System.out.println("Running testDifferentQueryTypes");

			//Setup a separate database/
			createDB(testMultipleDB);
			createForest(testMultipleForest[0], testMultipleDB);
			
			associateRESTServerWithDB(restServerName, testMultipleDB);

			setupAppServicesConstraint(testMultipleDB);

			String[] filenames = {"constraint1.xml", "constraint2.xml", "constraint3.xml", "constraint4.xml", "constraint5.xml"};
			ServerConfigurationManager srvMgr = client.newServerConfigManager();
			srvMgr.readConfiguration();
			srvMgr.setQueryOptionValidation(true);
			srvMgr.writeConfiguration();

			QueryManager queryMgr = clientQHB.newQueryManager();

			// create query def
			StructuredQueryBuilder qb = queryMgr.newStructuredQueryBuilder();
			StructuredQueryDefinition queryWorddef = qb.word(qb.element("id"), "0026");

			//Use WriteHostbatcher to write the same files.				
			WriteHostBatcher batcher = moveMgr.newWriteHostBatcher();

			batcher.withBatchSize(2);
			InputStreamHandle contentHandle1 = new InputStreamHandle();
			contentHandle1.set(new FileInputStream(dataConfigDirPath + filenames[0]));
			InputStreamHandle contentHandle2 = new InputStreamHandle();
			contentHandle2.set(new FileInputStream(dataConfigDirPath + filenames[1]));
			InputStreamHandle contentHandle3 = new InputStreamHandle();
			contentHandle3.set(new FileInputStream(dataConfigDirPath + filenames[2]));
			InputStreamHandle contentHandle4 = new InputStreamHandle();
			contentHandle4.set(new FileInputStream(dataConfigDirPath + filenames[3]));
			InputStreamHandle contentHandle5 = new InputStreamHandle();
			contentHandle5.set(new FileInputStream(dataConfigDirPath + filenames[4]));

			batcher.add("/abs-range-constraint/batcher-contraints1.xml", contentHandle1);
			batcher.add("/abs-range-constraint/batcher-contraints2.xml", contentHandle2);
			batcher.add("/abs-range-constraint/batcher-contraints3.xml", contentHandle3);
			batcher.add("/abs-range-constraint/batcher-contraints4.xml", contentHandle4);
			batcher.add("/abs-range-constraint/batcher-contraints5.xml", contentHandle5);

			// Flush
			batcher.flushAndWait();

			StringBuilder batchWordResults = new StringBuilder();
			StringBuilder batchWordFailResults = new StringBuilder();

			// Run a QueryHostBatcher on the new URIs.
			QueryHostBatcher queryBatcher1 = moveMgr.newQueryHostBatcher(queryWorddef);

			queryBatcher1.onUrisReady((client1, batch)-> {
				for (String str : batch.getItems()) {
					batchWordResults.append(str)
							.append('|');
				}     
			});
			queryBatcher1.onQueryFailure((client1, throwable)-> {        	
				System.out.println("Exceptions thrown from callback onQueryFailure in testDifferentQueryTypes");        	
				throwable.printStackTrace();
				batchWordFailResults.append("Test has Exceptions");          	
			} );
			JobTicket jobTicket = moveMgr.startJob(queryBatcher1);
			boolean bJobFinished = queryBatcher1.awaitTermination(3, TimeUnit.MINUTES);
			while(!queryBatcher1.isTerminated()) {
				// do nothing.
			}	

			if (queryBatcher1.isTerminated()) {

				if (!batchWordFailResults.toString().isEmpty() && batchWordFailResults.toString().contains("Exceptions")) {
					fail("Test failed due to exceptions in testDifferentQueryTypes - Word Query");
				}

				// Verify the batch results now.
				String[] res = batchWordResults.toString().split("\\|");
				assertEquals("Number of reults returned is incorrect", 1, res.length);
				assertTrue("URI returned not correct", res[0].contains("/abs-range-constraint/batcher-contraints5.xml"));
			}
			
			// Run a range query.
			
			StructuredQueryDefinition queryRangedef = qb.range(qb.element("popularity"), "xs:integer", Operator.GE, 4);
			QueryHostBatcher queryBatcher2 = moveMgr.newQueryHostBatcher(queryRangedef);
			//StringBuilder batchRangeResults = new StringBuilder();
			List<String> batchRangeResults = new ArrayList<String>();
			StringBuilder batchRangeFailResults = new StringBuilder();

			queryBatcher2.onUrisReady((client1, batch)-> {
				for (String str : batch.getItems()) {
					batchRangeResults.add(str);
					//batchRangeResults.append(str)
					//.append('|');
				}     
			});
			queryBatcher2.onQueryFailure((client1, throwable)-> {        	
				System.out.println("Exceptions thrown from callback onQueryFailure in testDifferentQueryTypes");        	
				throwable.printStackTrace();
				batchRangeFailResults.append("Test has Exceptions");          	
			} );
			jobTicket = moveMgr.startJob(queryBatcher2);
			bJobFinished = queryBatcher2.awaitTermination(3, TimeUnit.MINUTES);
			while(!queryBatcher2.isTerminated()) {
				// do nothing.
			}	

			if (queryBatcher2.isTerminated()) {

				if (!batchRangeFailResults.toString().isEmpty() && batchRangeFailResults.toString().contains("Exceptions")) {
					fail("Test failed due to exceptions in testDifferentQueryTypes - Word Query");
				}

				// Verify the batch results now.
				assertTrue("No of documents returned in range query not correct", batchRangeResults.size() == 4);
				assertTrue("URI returned not correct", batchRangeResults.contains("/abs-range-constraint/batcher-contraints1.xml"));
				assertTrue("URI returned not correct", batchRangeResults.contains("/abs-range-constraint/batcher-contraints2.xml"));
				assertTrue("URI returned not correct", batchRangeResults.contains("/abs-range-constraint/batcher-contraints4.xml"));
				assertTrue("URI returned not correct", batchRangeResults.contains("/abs-range-constraint/batcher-contraints5.xml"));
			}
			
			// Run a ValueQueryOnAttribute query.
			
			StructuredQueryDefinition valuequeyDef = qb.value(qb.elementAttribute(qb.element(new QName("http://cloudbank.com", "price")), qb.attribute("amt")), "0.1");
			QueryHostBatcher queryBatcher3 = moveMgr.newQueryHostBatcher(valuequeyDef);
			//StringBuilder batchRangeResults = new StringBuilder();
			List<String> batchValueResults = new ArrayList<String>();
			StringBuilder batchvalueFailResults = new StringBuilder();

			queryBatcher3.onUrisReady((client1, batch)-> {
				for (String str : batch.getItems()) {
					batchValueResults.add(str);
				}     
			});
			queryBatcher3.onQueryFailure((client1, throwable)-> {        	
				System.out.println("Exceptions thrown from callback onQueryFailure in testDifferentQueryTypes");        	
				throwable.printStackTrace();
				batchvalueFailResults.append("Test has Exceptions");          	
			} );
			jobTicket = moveMgr.startJob(queryBatcher3);
			bJobFinished = queryBatcher3.awaitTermination(3, TimeUnit.MINUTES);
			while(!queryBatcher3.isTerminated()) {
				// do nothing.
			}	

			if (queryBatcher3.isTerminated()) {

				if (!batchvalueFailResults.toString().isEmpty() && batchvalueFailResults.toString().contains("Exceptions")) {
					fail("Test failed due to exceptions in testDifferentQueryTypes - Word Query");
				}

				// Verify the batch results now.
				assertTrue("No of documents returned in range query not correct", batchValueResults.size() == 1);
				assertTrue("URI returned not correct", batchRangeResults.contains("/abs-range-constraint/batcher-contraints1.xml"));
			}
		}
		catch (Exception e) {
			System.out.println("Exceptions thrown from Test testDifferentQueryTypes");
			System.out.println(e.getMessage());
		}
		finally {
			// Associate back the original DB.
			associateRESTServerWithDB(restServerName, dbName);
			detachForest(testMultipleDB, testMultipleForest[0]);
			deleteDB(testMultipleDB);
			deleteForest(testMultipleForest[0]);
		}
	}
}