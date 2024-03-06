/****************************************************************************************
 *  XmlOutputStreamBase.java
 *
 *  Created: Feb 6, 2009
 *
 *  (C) Copyright MITRE Corporation 2009
 *
 *  The program is provided "as is" without any warranty express or implied, including
 *  the warranty of non-infringement and the implied warranties of merchantability and
 *  fitness for a particular purpose.  The Copyright owner will not be liable for any
 *  damages suffered by you as a result of using the Program.  In no event will the
 *  Copyright owner be liable for any special, indirect or consequential damages or
 *  lost profits even if the Copyright owner has been advised of the possibility of
 *  their occurrence.
 *
 ***************************************************************************************/
package org.opensextant.giscore.output;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.opensextant.giscore.Namespace;
import org.opensextant.giscore.events.Comment;
import org.opensextant.giscore.events.Element;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.events.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A base class for those gis output stream implementations that output to XML
 * files.
 *
 * @author DRAND
 *
 */
public class XmlOutputStreamBase extends StreamVisitorBase implements
        IGISOutputStream {

    private static final Logger log = LoggerFactory.getLogger(XmlOutputStreamBase.class);

    private final DecimalFormat formatter = new DecimalFormat("0.##########");

    protected OutputStream stream;

    /**
     * Keep flag if writer is open so don't accidentally try to append closing tags more than once...
     */
    protected boolean writerOpen = true;

    protected XMLStreamWriter writer;
    protected XMLOutputFactory factory;
    protected final Map<String, String> namespaces = new HashMap<>();

    // common encoding to use which allows special characters such as degrees character
    // that is included in Geometry.toString() output but is not part of the UTF-8 character set.
    public static final String ISO_8859_1 = "ISO-8859-1";

    /**
     * Ctor
     */
    protected XmlOutputStreamBase() {
        // Create the stream as needed
    }

    /**
     * Creates a new XML output stream to write data to the specified 
     * underlying output stream with specified encoding.
     * The encoding on {@code writeStartDocument()} call to the writer must
     * match encoding of the {@code XmlOutputStreamBase}.
     *
     * @param stream the underlying input stream
     * @param encoding the encoding to use
     * @throws XMLStreamException if there is an error with the underlying XML
     */
    public XmlOutputStreamBase(OutputStream stream, String encoding) throws XMLStreamException {
        init(stream, encoding);
    }

    /**
     * Create and initialize the xml output stream
     * @param stream
     * @param encoding
     * @throws XMLStreamException
     */
    protected void init(OutputStream stream, String encoding)
            throws XMLStreamException {
        if (stream == null) {
            throw new IllegalArgumentException("stream should never be null");
        }
        this.stream = stream;
        factory = createFactory();
        writer = StringUtils.isBlank(encoding)
                ? factory.createXMLStreamWriter(stream) // use default encoding 'Cp1252'
                : factory.createXMLStreamWriter(stream, encoding);
    }

    /**
     * Creates a new XML output stream to write data to the specified
     * underlying output stream with default encoding 'Cp1252'.
     *
     * @param stream the underlying input stream.
     * @throws XMLStreamException if there is an error with the underlying XML
     */
    public XmlOutputStreamBase(OutputStream stream) throws XMLStreamException {
        this(stream, null);
    }

    /**
     * @return
     */
    protected XMLOutputFactory createFactory() {
        return XMLOutputFactory.newInstance();
    }

    /**
     * Close this writer and free any resources associated with the
     * writer.  This also closes the underlying output stream.
     *
     * @throws IOException if an error occurs
     */
    @Override
    public void close() throws IOException {
        try {
            if (writerOpen) {
                writer.flush();
                writer.close();
            }
        } catch (XMLStreamException e) {
            throw new IOException(e);
        } finally {
            writerOpen = false;
            IOUtils.closeQuietly(stream);
            stream = null;
            writer = null;
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.opensextant.giscore.output.IGISOutputStream#write(org.opensextant.giscore.events.IGISObject)
     */
    @Override
    public void write(IGISObject object) {
        object.accept(this);
    }

    /*
     * Don't bother quoting for these characters
     */
    private static final String ALLOWED_SPECIAL_CHARACTERS = "{}[]\"'=.-,#_!@$*()[]/:";

    /**
     * Writes XML comment to the output stream if text comment value is not null or empty.
     * The comment can contain any unescaped character (e.g. "declarations for head &amp; body")
     * and any occurrences of "--" (double-hyphen) will be hex-escaped to &#x2D;&#x2D;
     *
     * @param comment Comment, never {@code null}
     *
     * @throws IllegalStateException if there is an error with the underlying XML 
     */
    @Override
    public void visit(Comment comment) {
        String text = comment.getText();
        // ignore empty or null comments
        if (StringUtils.isNotBlank(text))
            try {
                // XML 1.0 spec: string "--" (double-hyphen) MUST NOT occur within comments. Any other character may appear inside.
                // e.g. <!-- declarations for <head> & <body> -->
                text = text.replace("--", "&#x2D;&#x2D;");
                StringBuilder buf = new StringBuilder();
                // prepend comment with space if not already whitespace
                if (!Character.isWhitespace(text.charAt(0)))
                    buf.append(' ');
                buf.append(text);
                // append comment text with space if not already whitespace
                if (text.length() == 1 || !Character.isWhitespace(text.charAt(text.length() - 1)))
                    buf.append(' ');
                writer.writeComment(buf.toString());
                writer.writeCharacters("\n");
            } catch (XMLStreamException e) {
                throw new IllegalStateException(e);
            }
    }

    /**
     * Visit a row. Output as an XML Comment.
     * @param row
     * @throws IllegalStateException if there is an error with the underlying XML
     */
    @Override
    public void visit(Row row) {
        log.debug("ignore row");
        visit(new Comment("Row-oriented data " + row));
    }

    /**
     * See if the characters include special characters, and if it does use a
     * CDATA. Special characters here means anything that isn't alphanumeric or
     * writespace - CDATA is always valid, so this keeps it simple.
     *
     * @param outputString
     * @throws XMLStreamException if there is an error with the underlying XML
     */
    protected void handleCharacters(String outputString)
            throws XMLStreamException {
        boolean foundSpecial = false;
        for (int i = 0; i < outputString.length(); i++) {
            char ch = outputString.charAt(i);
            if (!Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch)
                    && ALLOWED_SPECIAL_CHARACTERS.indexOf(ch) == -1) {
                foundSpecial = true;
                // unsafe/special characters include: <>&%
                break;
            }
        }
        if (foundSpecial)
            writer.writeCData(outputString);
        else
            writer.writeCharacters(outputString);
    }

    /**
     * Handle a simple element with non-null text, a common case for KML
     *
     * @param tag local name of the tag, may not be null
     * @param content Content to write as parsed character data, if null then an empty XML element is written
     * @throws XMLStreamException if there is an error with the underlying XML
     */
    protected void handleNonNullSimpleElement(String tag, Object content) throws XMLStreamException {
        if (content != null) handleSimpleElement(tag, content);
    }

    /**
     * Handle a simple element with text, a common case for KML
     *
     * @param tag local name of the tag, may not be null
     * @param content Content to write as parsed character data, if null then an empty XML element is written
     * @throws XMLStreamException if there is an error with the underlying XML
     */
    protected void handleSimpleElement(String tag, Object content)
            throws XMLStreamException {
        if (content == null)
            writer.writeEmptyElement(tag);
        else {
            writer.writeStartElement(tag);
            handleCharacters(content.toString());
            writer.writeEndElement();
        }
        writer.writeCharacters("\n");
    }

    /**
     * Write an element
     *
     * @param el the element, never null
     * @param parentNamespace namespace of parent element, null if root element or unknown
     *
     * @throws XMLStreamException if there is an error with the underlying XML
     */
    protected void handleXmlElement(Element el, Namespace parentNamespace)
            throws XMLStreamException {
        handleXmlElement(el, namespaces, parentNamespace);
    }

    /**
     * Write an element
     *
     * @param el the element
     * @param parentNamespaces declared namespaces from which to resolve local namespace prefixes
     * @param parentNamespace namespace of parent element, null if root element or unknown
     *
     * @throws XMLStreamException if there is an error with the underlying XML
     */
    private void handleXmlElement(Element el, Map<String, String> parentNamespaces, Namespace parentNamespace)
            throws XMLStreamException {
        Map<String, String> namespaces = new HashMap<>(parentNamespaces);
        final Namespace namespace = el.getNamespace();
        final String nsPrefix = namespace.getPrefix();
        boolean writeNamespaces = true;
        if (StringUtils.isNotBlank(nsPrefix)) {
            String nsURI = namespaces.get(nsPrefix);
            if (StringUtils.isNotBlank(nsURI)) {
                writer.writeStartElement(nsURI, el.getName());
            } else {
                nsURI = namespace.getURI();
                if (StringUtils.isNotBlank(nsURI)) {
                    // namespace not defined in parent/root document
                    // add namespace to local namespace scope for children of this element to resolve
                    namespaces.put(nsPrefix, nsURI);
                    writer.writeStartElement(nsPrefix, el.getName(), nsURI);
                    writer.writeNamespace(nsPrefix, nsURI);
                } else {
                    log.warn("Unknown namespace prefix found " + nsPrefix);
                    writeAsComment(el);
                    writeNamespaces = false;
                }
            }
        } else {
            String nsUri = namespace.getURI();
            if (StringUtils.isBlank(nsUri) || (parentNamespace != null && nsUri.equals(parentNamespace.getURI()))) {
                writer.writeStartElement(el.getName());
            } else {
                // otherwise write element with explicit default XML namespace
                // nsUri has non-blank value not equal to parent element's namespace
                writer.writeStartElement(el.getName());
                writer.writeDefaultNamespace(nsUri);
            }
        }
        if (writeNamespaces) {
            for (Namespace ns : el.getNamespaces()) {
                if (!namespace.equals(ns)) {
                    final String nsPrefix1 = ns.getPrefix();
                    writer.writeNamespace(nsPrefix1, ns.getURI());
                    if (!namespaces.containsKey(nsPrefix1))
                        namespaces.put(nsPrefix1, ns.getURI());
                }
            }
        }
        for (Map.Entry<String, String> attr : el.getAttributes().entrySet()) {
            String key = attr.getKey();
            String val = attr.getValue();
            String[] parts = key.split(":");
            if (parts.length == 2) {
                String prefix = parts[0];
                String ns = namespaces.get(prefix);
                if (ns == null) {
                    log.warn("Unknown namespace prefix found " + prefix);
                } else
                    writer.writeAttribute(prefix, ns, parts[1], val);
            } else {
                writer.writeAttribute(key, val);
            }
        }
        for (Element child : el.getChildren()) {
            handleXmlElement(child, namespaces, namespace);
        }
        final String text = el.getText();
        if (StringUtils.isNotBlank(text)) {
            writer.writeCharacters(text);
        }
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    /**
     * Write formatted element inline within XML comment
     * @param el
     * @throws XMLStreamException if there is an error with the underlying XML
     */
    protected void writeAsComment(Element el) throws XMLStreamException {
        StringBuilder sb = new StringBuilder();
        writeAsComment(el, sb, 0);
        // XML Spec 1.0: For compatibility, the string " -- " (double-hyphen) MUST NOT occur within comments.
        // XMLStreamWriter.writeComment() does *NOT* encode/escape the comment text
        String text = sb.toString().replace("--", "&#x2D;&#x2D;");
        writer.writeComment(text);
    }

    private void writeAsComment(Element el, StringBuilder sb, int level) {
        if (level == 0) {
            sb.append('\n');
        } else {
            sb.append("  ".repeat(level)); // indent 2-spaces for each depth level
        }
        sb.append('<');
        if (StringUtils.isNotBlank(el.getPrefix())) {
            sb.append(el.getPrefix()).append(':');
        }
        sb.append(el.getName());
        // if namespace not declared in root element then declare it
        // e.g. xmlns:gx="http://www.google.com/kml/ext/2.2" 
        if (level == 0 && StringUtils.isNotBlank(el.getNamespaceURI())
                && StringUtils.isNotBlank(el.getPrefix())
                && !el.getNamespaceURI().equals(namespaces.get(el.getPrefix()))) {
            escapeAttribute(sb, "xmlns:" + el.getPrefix(), el.getNamespaceURI());
        }
        for (Map.Entry<String, String> attr : el.getAttributes().entrySet()) {
            String key = attr.getKey();
            String val = attr.getValue();
            escapeAttribute(sb, key, val);
        }
        sb.append('>');
        List<Element> children = el.getChildren();
        boolean needNewline = true;
        if (!children.isEmpty()) {
            sb.append('\n');
            for (Element child : children) {
                writeAsComment(child, sb, level + 1);
            }
            needNewline = false;
        }
        if (StringUtils.isNotBlank(el.getText())) {
            sb.append(el.getText());
        } else {
            if (needNewline) {
                sb.append('\n');
            }
            sb.append("  ".repeat(level));// indent 2-spaces for each depth level
        }
        sb.append("</").append(el.getName()).append(">\n");
    }

    protected static void escapeAttribute(StringBuilder sb, String key, String val) {
        if (StringUtils.isBlank(key) || StringUtils.isBlank(val)) {
            return;
        }
        String delim;
        if (key.indexOf('\"') == -1) delim = "\"";
        else if (key.indexOf('\'') == -1) delim = "'";
        else {
            delim = "\"";
            key = key.replace("\"", "\\\""); // escape quotes in string
        }
        sb.append(' ').append(key).append('=');
        sb.append(delim).append(val).append(delim);
    }

    /**
     * Handles simple element with a namespace.
     *
     * @param ns Namespace. If null then delagates to handleSimpleElement with name and value. 
     * @param tag local name of the element, may not be null
     * @param content Content to write as parsed character data, if null then an empty XML element is written
     * @throws XMLStreamException if there is an error with the underlying XML
     */
    protected void handleSimpleElement(Namespace ns, String tag, String content) throws XMLStreamException {
        if (ns == null) {
            handleSimpleElement(tag, content);
        } else {
            writer.writeStartElement(ns.getPrefix(), tag, ns.getURI());
            handleCharacters(content);
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }
    }

    /**
     * Writes a namespace to the output stream.
     * If the prefix argument to this method is the empty string,
     * "xmlns", or null this method will delegate to writeDefaultNamespace
     * @param ns Namespace
     * @throws XMLStreamException if there is an error with the underlying XML
     * @throws IllegalStateException if the current state does not allow Namespace writing
     */
    protected void writeNamespace(Namespace ns) throws XMLStreamException {
        if (ns != null) writer.writeNamespace(ns.getPrefix(), ns.getURI());
    }

    /**
     * Formats double values suitable for XML output using DecimalFormater.
     * Rounds off decimal value at 10-decimal places eliminating most common
     * round-off errors which are typically at 14 decimal places or beyond
     * (e.g. -34.93 may get converted to -34.93000000000001 with conversion
     * from decimal degrees to radians and back to degrees.
     * @param d double value
     * @return formatted decimal value
     */
    protected String formatDouble(double d) {
        // note doubles like -34.93 may get formatted as -34.93000000000001
        // if using Double.toString()
        // string parsed decimal degrees -> radians -> printed out as decimal degrees
        // using java.text.DecimalFormat("0.########") with 10 decimal places to be safe
        // since 8 decimal places .00000001 is 1mm resolution and anything beyond is round-off error
        return formatter.format(d);
    }

}
