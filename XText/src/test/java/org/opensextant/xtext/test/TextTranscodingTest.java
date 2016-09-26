package org.opensextant.xtext.test;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;

import org.junit.Test;
import org.opensextant.xtext.ConvertedDocument;
import org.opensextant.xtext.converters.TextTranscodingConverter;

public class TextTranscodingTest {

    @Test
    public void testConversionImplementationInputStreamFile() throws IOException {
        URL obj = getClass().getResource("/plaintext.txt");
        TextTranscodingConverter ttc = new TextTranscodingConverter();
        ConvertedDocument doc = ttc.convert(obj.openStream());
        assertTrue(doc.getEncoding().equals("UTF-8"));
    }

}
