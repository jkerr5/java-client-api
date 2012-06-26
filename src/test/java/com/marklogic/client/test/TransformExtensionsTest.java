/*
 * Copyright 2012 MarkLogic Corporation
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
package com.marklogic.client.test;

import static org.custommonkey.xmlunit.XMLAssert.assertXpathExists;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.custommonkey.xmlunit.SimpleNamespaceContext;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.exceptions.XpathException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;

import com.marklogic.client.ExtensionMetadata;
import com.marklogic.client.FailedRequestException;
import com.marklogic.client.Format;
import com.marklogic.client.TransformExtensionsManager;
import com.marklogic.client.io.DOMHandle;
import com.marklogic.client.io.StringHandle;

public class TransformExtensionsTest {
	final static String XQUERY_NAME = "testxqy";
	final static String XSLT_NAME   = "testxsl";
	final static String XQUERY_FILE = XQUERY_NAME + ".xqy"; 
	final static String XSLT_FILE   = XSLT_NAME + ".xsl"; 

	static private String xqueryTransform;
	static private String xslTransform;

	@BeforeClass
	public static void beforeClass() throws IOException {
		Common.connectAdmin();

		HashMap<String,String> namespaces = new HashMap<String, String>();
		namespaces.put("xsl",  "http://www.w3.org/1999/XSL/Transform");
		namespaces.put("rapi", "http://marklogic.com/rest-api");

        SimpleNamespaceContext namespaceContext = new SimpleNamespaceContext(namespaces);

        XMLUnit.setIgnoreAttributeOrder(true);
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setNormalize(true);
        XMLUnit.setNormalizeWhitespace(true);
        XMLUnit.setIgnoreDiffBetweenTextAndCDATA(true);

        XMLUnit.setXpathNamespaceContext(namespaceContext);
		xqueryTransform = Common.testFileToString(XQUERY_FILE);
		xslTransform    = Common.testFileToString(XSLT_FILE);
	}
	@AfterClass
	public static void afterClass() {
		Common.release();
		xqueryTransform = null;
		xslTransform    = null;
	}

	static ExtensionMetadata makeXQueryMetadata() {
		ExtensionMetadata metadata = new ExtensionMetadata();
		metadata.setTitle("Document XQuery Transform");
		metadata.setDescription("This plugin adds an attribute to the root element");
		metadata.setProvider("MarkLogic");
		metadata.setVersion("0.1");
		return metadata;
	}

	static ExtensionMetadata makeXSLTMetadata() {
		ExtensionMetadata metadata = new ExtensionMetadata();
		metadata.setTitle("Document XSLT Transform");
		metadata.setDescription("This plugin adds an attribute to the root element");
		metadata.setProvider("MarkLogic");
		metadata.setVersion("0.1");
		return metadata;
	}

	static Map<String,String> makeParameters() {
		Map<String,String> params = new HashMap<String,String>();
		params.put("value", "true");
		return params;
	}

	@Test
	public void testTransformExtensions() throws XpathException {
		TransformExtensionsManager extensionMgr =
			Common.client.newServerConfigManager().newTransformExtensionsManager();

		StringHandle handle = new StringHandle();
		handle.setFormat(Format.TEXT);

		writeXQueryTransform(extensionMgr);

		writeXSLTransform(extensionMgr);

		extensionMgr.readXQueryTransform(XQUERY_NAME, handle);
		assertEquals("Failed to retrieve XQuery transform", xqueryTransform, handle.get());

		Document result = extensionMgr.readXSLTransform(XSLT_NAME, new DOMHandle()).get();
		assertNotNull("Failed to retrieve XSLT transform", result);
		assertXpathExists("/xsl:stylesheet", result);

		result = extensionMgr.listTransforms(new DOMHandle()).get();
		assertNotNull("Failed to retrieve transforms list", result);
		assertXpathExists("/rapi:transforms/rapi:transform/rapi:name[string(.) = 'testxqy']", result);
		assertXpathExists("/rapi:transforms/rapi:transform/rapi:name[string(.) = 'testxsl']", result);

        extensionMgr.deleteTransform(XQUERY_NAME);
        boolean transformDeleted = true;
		try {
			handle = new StringHandle();
			extensionMgr.readXQueryTransform(XQUERY_NAME, handle);
			transformDeleted = (handle.get() == null);
		} catch(FailedRequestException ex) {
		}
		assertTrue("Failed to delete XQuery transform", transformDeleted);

		extensionMgr.deleteTransform(XSLT_NAME);
		try {
			handle = new StringHandle();
			extensionMgr.readXSLTransform(XSLT_NAME, handle);
			transformDeleted = (handle.get() == null);
		} catch(FailedRequestException ex) {
		}
		assertTrue("Failed to delete XSLT transform", transformDeleted);
	}
	public void writeXQueryTransform(TransformExtensionsManager extensionMgr) {
		extensionMgr.writeXQueryTransform(
				XQUERY_NAME,
				new StringHandle().withFormat(Format.TEXT).with(xqueryTransform),
				makeXQueryMetadata(),
				makeParameters()
				);		
	}
	public void writeXSLTransform(TransformExtensionsManager extensionMgr) {
		extensionMgr.writeXSLTransform(
				XSLT_NAME,
				new StringHandle().with(xslTransform),
				makeXSLTMetadata(),
				makeParameters()
				);
	}
}
