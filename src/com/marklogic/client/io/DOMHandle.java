package com.marklogic.client.io;

import org.w3c.dom.Document;

import com.marklogic.client.abstractio.XMLReadWriteHandle;

public interface DOMHandle extends XMLReadWriteHandle<Document> {
    public DOMHandle on(Document content);
}