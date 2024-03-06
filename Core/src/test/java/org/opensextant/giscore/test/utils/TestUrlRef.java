package org.opensextant.giscore.test.utils;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

import junit.framework.TestCase;
import org.opensextant.giscore.input.kml.UrlRef;

/**
 * @author Jason Mathews, MITRE Corp.
 * Date: Oct 28, 2010 5:19:13 PM
 */
public class TestUrlRef extends TestCase {

    public void testEscapeUri() {
        String uri = "#foo%20";
        assertEquals(uri, UrlRef.escapeUri(uri));

        // these URIs should be escaped and not same as original string        
        String[] uris = {
                "foo?x=<B>{String} to [encode]</B>",
                "http://localhost/foo?x=|^\\"
        };

        for(String id : uris) {
            // assertFalse(id.equals(UrlRef.escapeUri(id)));
            if (id.equals(UrlRef.escapeUri(id)))
                failSame("URI expected to be different but same: URI=" + id + ":");
        }
    }

    public void testEquals() throws URISyntaxException, MalformedURLException {
        final URI uri = new URI("http://localhost/mydata");
        UrlRef val1 = new UrlRef(uri);
        assertEquals(val1, val1);
        UrlRef val2 = new UrlRef(uri);
        assertEquals(val1, val2);
        assertEquals(val1.hashCode(), val2.hashCode());
    }

    public void testNotEquals() throws URISyntaxException, MalformedURLException {
        UrlRef val1 = new UrlRef(new URI("http://localhost1/mydata1"));
        UrlRef val2 = new UrlRef(new URI("http://localhost2/mydata2"));
        assertFalse(val1.equals(val2));
    }

    public void testIsIdentifier() {
        
        String[]ids = { "X509Data", "abc-ABC_12.34", "id%20",
                "_\u00B7\u3005\u30FE",  // XMLExtender
                "_\u309A", // XMLCombiningChar
                "_\uD7A3",  // XMLLetter
                "_\u0ED9"   // XMLDigit
        };
        /*
         * valid identifier follows NCName production in [Namespaces in XML]:
         *  NCName ::=  (Letter | '_') (NCNameChar)*  -- An XML Name, minus the ":"
         *  NCNameChar ::=  Letter | Digit | '.' | '-' | '_' | CombiningChar | Extender
         * 
         * Also allowing the URI escaping mechanism %HH,
         * where HH is the hexadecimal notation of a byte value.  
         */
        for(String id : ids) {
            assertTrue(UrlRef.isIdentifier(id));
        }

        String[] badIds = {
            null, "", " ", "124_Must start with alpha",
            "_\uABFF",
            "bad id", "x<bad>", "bad:id", // contains invalid characters
            "bad%zz", "bad%" // '%' must precede two hexadecimal digits otherwise must be escaped
        };
        for(String id : badIds) {
            assertFalse(UrlRef.isIdentifier(id));
        }
	}

    public void testIsAbsoluteUrl() {
        assertTrue(UrlRef.isAbsoluteUrl("http://kml-samples.googlecode.com/svn/trunk/kml/Style/remote-style.kml"));
        assertTrue(UrlRef.isAbsoluteUrl("http://[1080:0:0:0:8:800:200C:417A]/index.html")); // ipv6 URL
        assertTrue(UrlRef.isAbsoluteUrl("file:///c:/data/test.kml"));
        assertTrue(UrlRef.isAbsoluteUrl("file:C:/path/test.kml"));
        assertTrue(UrlRef.isAbsoluteUrl("file:/C:/data/test.kml"));

        assertFalse(UrlRef.isAbsoluteUrl("remote-style.kml#style"));
        assertFalse(UrlRef.isAbsoluteUrl("file.kml#id"));
        assertFalse(UrlRef.isAbsoluteUrl("file.query?url=http://s.com/foo"));
    }

	public void testIsIdentifierWithWhitespace() {
		assertFalse(UrlRef.isIdentifier("This has whitespace"));
		assertTrue(UrlRef.isIdentifier("This has whitespace", true));
    }
}
