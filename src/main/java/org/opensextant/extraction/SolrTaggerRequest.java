/*
 *
 * Copyright 2012-2018 The MITRE Corporation.
 */
package org.opensextant.extraction;

import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.request.RequestWriter;
import org.apache.solr.common.params.SolrParams;

/**
 * @author dsmiley
 * @author ubaldino
 */
@SuppressWarnings("serial")
public class SolrTaggerRequest extends QueryRequest {

    private final String input;

    public SolrTaggerRequest(SolrParams params, String text) {
        super(params, SolrRequest.METHOD.POST);
        this.input = text;
    }

    /* Fixed in Solr 7.x */
    @Override
    public RequestWriter.ContentWriter getContentWriter(String expectedType) {
        return new RequestWriter.StringPayloadContentWriter(input, "text/plain; charset=UTF-8");
    }
}
