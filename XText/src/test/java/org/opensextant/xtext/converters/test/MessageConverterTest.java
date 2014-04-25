/*
 * Copyright 2013-2014 OpenSextant.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opensextant.xtext.converters.test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import javax.activation.MimeType;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opensextant.xtext.Content;
import org.opensextant.xtext.ConvertedDocument;
import org.opensextant.xtext.converters.MessageConverter;

/**
 *
 * @author jgibson
 */
public class MessageConverterTest {

    @ClassRule
    public static final TemporaryFolder TEMP_DIR = new TemporaryFolder();

    private static File TEST_FILE;

    private static final String CONTENT_ID = MessageConverter.MAIL_KEY_PREFIX + "content-id";
    private static final String CONTENT_DISPOSITION = MessageConverter.MAIL_KEY_PREFIX + "disposition";

    @BeforeClass
    public static void setupTemporaryFolder() throws IOException {
        TEST_FILE = TEMP_DIR.newFile("mimeEmailWithAttachmentsTest.eml");
        FileUtils.copyInputStreamToFile(MessageConverterTest.class.getResourceAsStream("mimeEmailWithAttachmentsTest.eml"), TEST_FILE);
    }

    @Test
    public void complexEmailTest() throws IOException, Exception {
        MessageConverter conv = new MessageConverter();
        ConvertedDocument doc = conv.convert(TEST_FILE);
        Assert.assertEquals((MESSAGE_BODY + MESSAGE_BOUNDARY).trim(), doc.getText());

        Assert.assertEquals(5, doc.getRawChildren().size());
        final HashMap<String, Content> children = new HashMap<String, Content>();
        for (final Content child : doc.getRawChildren()) {
            children.put(child.id, child);
        }

        Content text_attach = children.get("xtext-embedded-attached-text.txt");
        Assert.assertNotNull("text attachment was not found, available attachments are: " + children.keySet(), text_attach);

        String orig_text_attach = IOUtils.toString(getClass().getResourceAsStream("xtext-embedded-attached-text.txt"), "UTF-8");
        String sep = System.getProperty("line.separator");
        if (!"\r\n".equals(sep)) {
            orig_text_attach = orig_text_attach.replaceAll(sep, "\r\n");
        }
        Assert.assertEquals("text/plain", new MimeType(text_attach.mimeType).getBaseType());
        Assert.assertEquals(orig_text_attach, new String(text_attach.content, text_attach.encoding));
        Assert.assertEquals("A686FA7D9F4FB64E99601455209639C5@imc.mitre.org", text_attach.meta.getProperty(CONTENT_ID));
        Assert.assertEquals("attachment", text_attach.meta.getProperty(CONTENT_DISPOSITION));

        Content html_attach = children.get("word_doc_as_html.htm");
        Assert.assertNotNull("Embedded HTML was not found.", html_attach);
        Assert.assertEquals("text/html", new MimeType(html_attach.mimeType).getBaseType());
        Assert.assertEquals("64B706D14F6CAF4598A5A756E2E763A0@imc.mitre.org", html_attach.meta.getProperty(CONTENT_ID));
        Assert.assertEquals("attachment", html_attach.meta.getProperty(CONTENT_DISPOSITION));
        Content word_attach = children.get("doc_with_embedded_geocoded_image2.docx");
        Assert.assertNotNull("Doc with geocoded image was not found.", word_attach);
        Assert.assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", new MimeType(word_attach.mimeType).getBaseType());
        Assert.assertEquals("3ED3B89ABF3D1840B551B527B4DA054D@imc.mitre.org", word_attach.meta.getProperty(CONTENT_ID));
        Assert.assertEquals("attachment", word_attach.meta.getProperty(CONTENT_DISPOSITION));
        Content jpeg_attach = children.get("android_photo_with_gps1.jpeg");
        Assert.assertNotNull("Photo with attached image was not found.", jpeg_attach);
        Assert.assertEquals("image/jpeg", new MimeType(jpeg_attach.mimeType).getBaseType());
        Assert.assertEquals("485710da-7b60-461a-a566-0ad2e0a14b82@imc.mitre.org", jpeg_attach.meta.getProperty(CONTENT_ID));
        Assert.assertEquals("inline", jpeg_attach.meta.getProperty(CONTENT_DISPOSITION));

        Content htmlbody = null;
        for (final Content child : doc.getRawChildren()) {
            if ("true".equals(child.meta.getProperty(MessageConverter.MAIL_KEY_PREFIX + "html-body"))) {
                Assert.assertNull("multiple html bodies found", htmlbody);
                Assert.assertEquals("text/html", new MimeType(child.mimeType).getBaseType());
                Assert.assertEquals("BEA4D58835C6A342B10D665B40F9D105@imc.mitre.org", child.meta.getProperty(CONTENT_ID));
                htmlbody = child;
            }
        }

        Assert.assertNotNull("html body was not found", htmlbody);
    }

    private static final String MESSAGE_BODY = "This is a test of a mime message with several different parsing options.\n" +
            "\n" +
            "This is bold.\n" +
            "Here is a geotagged image.\n" +
            "[cid:485710da-7b60-461a-a566-0ad2e0a14b82@imc.mitre.org]\n" +
            "\n" +
            "It should be inline. Afterwards there will be some additional attachments.\n" +
            "\n" +
            "John\n\n";
    private static final String MESSAGE_BOUNDARY = "\n*******************\n";
}
