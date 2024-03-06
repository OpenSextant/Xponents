/****************************************************************************************
 *  XmlInputStream.java
 *
 *  Created: Jul 21, 2010
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2010
 *
 *  The program is provided "as is" without any warranty express or implied, including
 *  the warranty of non-infringement and the implied warranties of merchantibility and
 *  fitness for a particular purpose.  The Copyright owner will not be liable for any
 *  damages suffered by you as a result of using the Program.  In no event will the
 *  Copyright owner be liable for any special, indirect or consequential damages or
 *  lost profits even if the Copyright owner has been advised of the possibility of
 *  their occurrence.
 *
 ***************************************************************************************/
package org.opensextant.giscore.input;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.Namespace;
import org.opensextant.giscore.events.DocumentStart;
import org.opensextant.giscore.events.Element;
import org.opensextant.giscore.events.IGISObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common superclass for Xml based input stream implementations
 * 
 * @author DRAND
 */
public abstract class XmlInputStream extends GISInputStreamBase {

	private static final Logger log = LoggerFactory.getLogger(XmlInputStream.class);
    
	protected static final XMLInputFactory ms_fact;
	static {
		ms_fact = XMLInputFactory.newInstance();
	}
	protected InputStream is;
	protected XMLEventReader stream;

    @NotNull
	private String encoding = "UTF-8";

	/**
	 * Ctor
	 * 
	 * @param inputStream
	 * @throws IOException if an I/O error occurs
	 */
	public XmlInputStream(InputStream inputStream) throws IOException {
		if (inputStream == null) {
			throw new IllegalArgumentException(
					"inputStream should never be null");
		}
		is = inputStream;
		try {
			this.stream = ms_fact.createXMLEventReader(is);
		} catch (XMLStreamException e) {
			throw new IOException(e);
		}
	}

	/**
	 * Ctor
	 * 
	 * @param inputStream
	 * @param type
	 * @throws IOException if an I/O error occurs
	 */
	public XmlInputStream(InputStream inputStream, DocumentType type)
			throws IOException {
		this(inputStream);
		DocumentStart ds = new DocumentStart(type);
		addLast(ds);
	}

	/**
	 * Closes this input stream and releases any system resources associated
	 * with the stream. Once the stream has been closed, further read()
	 * invocations may throw an IOException. Closing a previously closed stream
	 * has no effect.
	 */
	public void close() {
		if (stream != null)
			try {
				stream.close();
			} catch (XMLStreamException e) {
				log.warn("Failed to close reader", e);
			}
		if (is != null) {
			IOUtils.closeQuietly(is);
			is = null;
		}
	}

   /**
    * Returns the encoding style of the XML data.
    * @return the character encoding, defaults to "UTF-8". Never null.
    */
    @NotNull
    public String getEncoding() {
        return encoding;
    }

	protected void setEncoding(String encoding) {
		if (StringUtils.isNotBlank(encoding)) {
			log.debug("set encoding={}", encoding);
			this.encoding = encoding;
		} else {
			// otherwise assume UTF-8
			log.debug("null/empty encoding. assuming UTF-8");
		}
	}

	/**
	 * Is this event a matching end tag?
	 * 
	 * @param event
	 *            the event
	 * @param tag
	 *            the tag
	 * @return {@code true} if this is an end element event for the
	 *         matching tag
	 */
	protected boolean foundEndTag(XMLEvent event, String tag) {
		if (event == null) {
			return true;
		}
		if (event.getEventType() == XMLEvent.END_ELEMENT) {
			return event.asEndElement().getName().getLocalPart().equals(tag);
		}
		return false;
	}

	/**
	 * Is this event a matching end tag?
	 * 
	 * @param event
	 *            the event
	 * @param name
	 *            the qualified name of this event
	 * 
	 * @return {@code true} if this is an end element event for the
	 *         matching tag
	 */
	protected boolean foundEndTag(XMLEvent event, QName name) {
		if (event == null) {
			return true;
		}
		if (event.getEventType() == XMLEvent.END_ELEMENT) {
			return event.asEndElement().getName().equals(name);
		}
		return false;
	}

	/**
	 * Found a specific start tag
	 * 
	 * @param se
	 * @param tag
	 * @return
	 */
	protected boolean foundStartTag(StartElement se, String tag) {
		return se.getName().getLocalPart().equals(tag);
	}

	/**
	 * Unrecognized elements are packaged and returned as {@link Element}
	 * objects which hold their text and attribute data. These can be processed
	 * by consumers and may be output by the output side of XML based
	 * processors.
	 * 
	 * @param se
	 *            the start tag, never {@code null}
	 * @return the element, never {@code null}
	 *
	 * @throws XMLStreamException
	 *             if there is an error with the underlying XML.
	 */
	@NotNull
	protected IGISObject getForeignElement(StartElement se)
			throws XMLStreamException {
		Element el = new Element();
        QName qName = se.getName();
        el.setName(qName.getLocalPart());
        String nsURI = qName.getNamespaceURI();
        if (StringUtils.isNotBlank(nsURI)) {
            try {
                el.setNamespace(Namespace.getNamespace(qName.getPrefix(), nsURI));
            } catch (IllegalArgumentException e) {
                log.error("Failed to assign namespace " + qName);
            }
        }
		@SuppressWarnings("unchecked")
		Iterator<javax.xml.stream.events.Namespace> nsiter = se.getNamespaces();
		while (nsiter.hasNext()) {
			// add prefixed namespaces to the list if not defined
			javax.xml.stream.events.Namespace ns = nsiter.next();
			final String prefix = ns.getPrefix();
			if (StringUtils.isNotBlank(prefix)) {
				el.addNamespace(Namespace.getNamespace(prefix, ns.getNamespaceURI()));
			}
		}
		@SuppressWarnings("unchecked")
		Iterator<Attribute> aiter = se.getAttributes();
		while (aiter.hasNext()) {
			Attribute attr = aiter.next();
			String aname;
			final String prefix = attr.getName().getPrefix();
			if (StringUtils.isBlank(prefix)) {
				aname = attr.getName().getLocalPart();
			} else {
				aname = prefix + ":"
						+ attr.getName().getLocalPart();
			}
			el.getAttributes().put(aname, attr.getValue());
		}
		XMLEvent nextel = stream.nextEvent();
		while (true) {
			if (nextel instanceof Characters) {
				Characters text = (Characters) nextel;
				final String oldText = el.getText();
				if (oldText != null) {
					// append to existing text
					el.setText(oldText + text.getData());
				} else
					el.setText(text.getData());
			} else if (nextel.isStartElement()) {
				el.getChildren().add(
						(Element) getForeignElement(nextel.asStartElement()));
			} else if (nextel.isEndElement()) {
				break;
			}
			nextel = stream.nextEvent();
		}
		return el;
	}

	/**
	 * Read the element and then serialize the read element(s) into text
	 * 
	 * @param start
	 *            the start element
	 * @return a serialized string
	 * @throws XMLStreamException
	 *             if there is an error with the underlying XML.
	 */
	@NotNull
	protected String getSerializedElement(StartElement start)
			throws XMLStreamException {
		Element el = (Element) getForeignElement(start);
		StringBuilder sb = new StringBuilder(100);
		for (Element child : el.getChildren()) {
			serialize(child, sb);
		}
		sb.append(el.getText());
		return sb.toString();
	}

	private void serialize(Element el, StringBuilder sb) {
		String name = StringUtils.isNotBlank(el.getPrefix()) ? el.getPrefix()
				+ ":" + el.getName() : el.getName();
		sb.append('<');
		sb.append(name);
		for (Map.Entry<String, String> entry : el.getAttributes().entrySet()) {
			sb.append(' ');
			sb.append(entry.getKey());
			sb.append('=');
			sb.append('"');
			sb.append(entry.getValue());
			sb.append('"');
		}
		sb.append('>');
		for (Element child : el.getChildren()) {
			serialize(child, sb);
		}
		sb.append(el.getText());
		sb.append("</");
		sb.append(name);
		sb.append('>');
	}

	/**
	 * Returns non-empty trimmed elementText from stream otherwise null
	 * 
	 * @return non-empty trimmed string, otherwise null
	 * @throws XMLStreamException
	 *             if the current event is not a START_ELEMENT or if a non text
	 *             element is encountered
	 */
	@Nullable
	protected String getNonEmptyElementText() throws XMLStreamException {
		String elementText = stream.getElementText();
		if (elementText == null || elementText.isEmpty())
			return null;
		elementText = elementText.trim();
		return elementText.isEmpty() ? null : elementText;
	}

	/**
	 * Returns Integer from stream if valid otherwise {@code null}
	 *
	 * @return Integer value, otherwise null
	 * @throws XMLStreamException
	 *             if the current event is not a START_ELEMENT
	 */
	@Nullable
	protected Integer getIntegerElementValue(String localName)
			throws XMLStreamException {
		String elementText = getNonEmptyElementText();
		if (elementText != null) {
			try {
				return Integer.parseInt(elementText);
			} catch (NumberFormatException nfe) {
				log.warn("Ignoring bad value for " + localName + ": " + nfe);
			}
		}
		return null;
	}

	@Nullable
	protected Double getDoubleElementValue(String localName)
			throws XMLStreamException {
		String elementText = stream.getElementText();
		if (StringUtils.isNotBlank(elementText))
			try {
				return Double.parseDouble(elementText);
			} catch (NumberFormatException nfe) {
				log.warn("Ignoring bad value for " + localName + ": " + nfe);
			}
		return null;
	}

	/**
	 * Get non-empty element text.
	 * 
	 * @param name
	 *            the qualified name of this event
	 * @return non-empty element text or null if text content is missing, empty
	 *         or null.
	 * @throws XMLStreamException
	 *             if there is an error with the underlying XML.
	 */
	@Nullable
	protected String getElementText(QName name) throws XMLStreamException {
		/*
		 * some elements such as description may have HTML elements as child
		 * elements rather than within required CDATA block.
		 */
		try {
			return getNonEmptyElementText();
		} catch (XMLStreamException e) {
			log.warn("Unable to parse " + name.getLocalPart()
					+ " as text element: " + e);
			skipNextElement(stream, name);
			return null;
		}
	}

	/**
	 * Skip to end of target element given its fully qualified
	 * {@code QName}
	 * 
	 * @param element
	 * @param name
	 *            the qualified name of this event
	 * @throws XMLStreamException
	 *             if there is an error with the underlying XML.
	 */
	protected void skipNextElement(XMLEventReader element, QName name)
			throws XMLStreamException {
		while (true) {
			XMLEvent next = element.nextEvent();
			if (next == null || foundEndTag(next, name)) {
				break;
			}
		}
	}

}