/*
 *
 * Copyright 2012-2013 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opensextant.extraction;

import java.util.Collection;
import java.util.Collections;

//import java.util.Collection;
//import java.util.Collections;

import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.request.QueryRequest;
//import org.apache.solr.client.solrj.request.RequestWriter;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.ContentStream;
import org.apache.solr.common.util.ContentStreamBase;

/**
 *
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

    /* Not yet fixed in Solr 7.x 
    @Override
    public RequestWriter.ContentWriter getContentWriter(String expectedType) {
        return new RequestWriter.StringPayloadContentWriter(input, "text/plain; charset=UTF8");
    }
    */

    @Override
    public Collection<ContentStream> getContentStreams() {
        ContentStreamBase.StringStream stringStream = new ContentStreamBase.StringStream(input);
        stringStream.setContentType("text/plain; charset=UTF-8");
        return Collections.singleton((ContentStream) stringStream);
    }

}
