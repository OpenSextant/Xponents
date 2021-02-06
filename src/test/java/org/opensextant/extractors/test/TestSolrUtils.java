package org.opensextant.extractors.test;

import static org.junit.Assert.*;

import java.text.ParseException;
import java.util.Date;

import org.apache.solr.common.SolrDocument;
import org.junit.Test;
import org.opensextant.util.SolrUtil;

public class TestSolrUtils {

    @Test
    public void test() throws ParseException {
        SolrDocument doc = new SolrDocument();

        // Not much used. No dates in Solr, ... but just wanted to have this for
        // completeness.
        doc.put("someDay", "2017-01-02T04:05:06Z");
        Date d = SolrUtil.getDate(doc, "someDay");
        System.out.println("Date " + doc.get("someDay") + " Parses as " + d + " in current timezone");
        assertNotNull(d);
    }

}
