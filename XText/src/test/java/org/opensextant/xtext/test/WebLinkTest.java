package org.opensextant.xtext.test;

import static org.junit.Assert.*;

import java.net.MalformedURLException;
import java.net.URL;

import org.junit.Test;
import org.opensextant.xtext.collectors.sharepoint.SPLink;
import org.opensextant.xtext.collectors.web.HyperLink;

public class WebLinkTest {

    public static void err(String msg) {
        System.err.println(msg);
    }

    public static void print(String msg) {
        System.out.println(msg);
    }

    /**
     * Tests to see how URLs are mapped to files on disk.  That is, without special chars
     * and choosing best MIME type and file ext.
     */
    @Test
    public void test() {
        try {
            HyperLink hl = null;
            URL someSite = new URL("http://abc.com/");
            URL pageOnSite = new URL("http://abc.com/abc/xyz.htm");
            hl = new HyperLink("http://abc.com/page.aspx?download=MyHappyFamily.pdf", pageOnSite, someSite);
            print("Found=" + hl.getNormalPath() + " In " + hl.getDirectory());
            hl = new HyperLink("http://abc.com/page.aspx?download=MyHappyFamily.pdf&item=Blue", pageOnSite, someSite);
            print("Found=" + hl.getNormalPath() + " In " + hl.getDirectory());
            hl = new HyperLink("http://abc.com/page.aspx?download=MyHappyFamily.xxz", pageOnSite, someSite);
            print("Found=" + hl.getNormalPath() + " In " + hl.getDirectory());

            hl = new HyperLink("../", pageOnSite, someSite);
            assert (!hl.isCurrentPage());
            hl = new HyperLink("./", pageOnSite, someSite);
            assert (hl.isCurrentPage());
            hl = new HyperLink(".", pageOnSite, someSite);
            assert (hl.isCurrentPage());

            hl = new HyperLink("../", pageOnSite, someSite);
            assert (!hl.isCurrentPage());
            hl = new HyperLink("./a", pageOnSite, someSite);
            assert (hl.isCurrentPage());
            hl = new HyperLink("./a.htm", pageOnSite, someSite);
            assert (hl.isCurrentPage());
            hl = new HyperLink(".", pageOnSite, someSite);
            assert (hl.isCurrentPage());

            // Trailing slash:
            URL folderOnSite = new URL("http://abc.com/abc/");
            hl = new HyperLink("../", folderOnSite, someSite);
            assert (!hl.isCurrentPage());

            try {
                hl = new HyperLink("../", new URL("http://abc.com/abc"), someSite);
                assert (!hl.isCurrentPage());
            } catch (MalformedURLException err) {
                err("Still figuring out what this means. is 'abc' a page or a directory? "
                        + "or does it matter.  Is '..' in relation to that resource correct?");
            }
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

            try {
                hl = new HyperLink("../", new URL(someSite, "./abc/xyz.htm"), someSite);
                hl = new HyperLink("xyz-peer.htm", new URL(someSite, "./abc/xyz.htm"), someSite);
                hl = new HyperLink("../", new URL(someSite, "abc/xyz.htm"), someSite);
                hl = new HyperLink("../", new URL(someSite, "./abc"), someSite);
                hl = new HyperLink("../", new URL("http://abc.com/abc"), someSite);
            } catch (MalformedURLException relErrs) {
                err(relErrs.getMessage());
            }
        } catch (MalformedURLException err1) {
            fail(err1.getMessage());
        } catch (Exception err) {
            fail("Developer error -- bad URL." + err.getMessage());
        }
    }

}
