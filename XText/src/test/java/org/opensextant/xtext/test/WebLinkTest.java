package org.opensextant.xtext.test;

import static org.junit.Assert.*;

import java.net.URL;

import org.junit.Test;
import org.opensextant.xtext.collectors.web.HyperLink;

public class WebLinkTest {

    @Test
    public void test() {
        try {
            HyperLink hl = null;                       
            URL someSite = new URL("http://abc.com/");
            URL pageOnSite = new URL("http://abc.com/abc/xyz.htm");
            
            hl = new HyperLink("../", pageOnSite, someSite);
            assert (!hl.isCurrentPage());
            hl = new HyperLink("./",pageOnSite, someSite);
            assert (hl.isCurrentPage());
            hl = new HyperLink(".",pageOnSite, someSite);
            assert (hl.isCurrentPage());

            hl = new HyperLink("../",pageOnSite, someSite);
            assert (!hl.isCurrentPage());
            hl = new HyperLink("./a",pageOnSite, someSite);
            assert (hl.isCurrentPage());
            hl = new HyperLink("./a.htm",pageOnSite, someSite);
            assert (hl.isCurrentPage());
            hl = new HyperLink(".",pageOnSite, someSite);
            assert (hl.isCurrentPage());
            
            // Trailing slash:
            URL folderOnSite = new URL("http://abc.com/abc/");
            hl = new HyperLink("../", folderOnSite, someSite);
            assert (!hl.isCurrentPage());
            hl = new HyperLink("../", new URL("http://abc.com/abc"), someSite);
            assert (!hl.isCurrentPage());
            hl = new HyperLink("./a", folderOnSite, someSite);
            assert (hl.isCurrentPage());
            hl = new HyperLink("./a/b", folderOnSite, someSite);
            assert (hl.isCurrentPage());
            hl = new HyperLink("../c/b.htm", folderOnSite, someSite);
            assert (!hl.isCurrentPage());
            hl = new HyperLink("./a.htm", folderOnSite, someSite);
            assert (hl.isCurrentPage());
            hl = new HyperLink(".", folderOnSite, someSite);
            assert (hl.isCurrentPage());
            
            
            hl = new HyperLink("../", new URL(someSite, "./abc/xyz.htm"), someSite);
            hl = new HyperLink("xyz-peer.htm", new URL(someSite, "./abc/xyz.htm"), someSite);
            hl = new HyperLink("../", new URL(someSite, "abc/xyz.htm"), someSite);
            hl = new HyperLink("../", new URL(someSite, "./abc"), someSite);
            hl = new HyperLink("../", new URL("http://abc.com/abc"), someSite);
        } catch (Exception err) {
            fail("Developer error -- bad URL.");
        }
    }

}
