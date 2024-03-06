/****************************************************************************************
 *  CsvInputStream.java
 *
 *  Created: Apr 10, 2009
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2009
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
package org.opensextant.giscore.input.csv;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.events.Row;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.events.SimpleField;
import org.opensextant.giscore.input.GISInputStreamBase;

/**
 * Reads a CSV file in as a series of rows. It is up to the caller to decide
 * if the first row is or is not a header.
 * 
 * @author DRAND
 */
public class CsvInputStream extends GISInputStreamBase {
	/*
	 * Private markers for end of file and end of line
	 */
	private static final Object EOF = new Object();
	private static final Object EOL = new Object();

	/**
	 * The reader used to read from the stream, never {@code null} after
	 * ctor
	 */
	private Reader reader;
	
	private Character valueDelimiter = ',';
	private String lineDelimiter;
	private Character quote = '"';
	
	/**
	 * The schema, may be provided (in which case we ignore the header) or
	 * derived from the first row. Things will be funky if we don't anticipate
	 * this right.
	 */
	private Schema schema;
	
	/**
	 * @param file
	 * @param arguments
	 * @throws FileNotFoundException
	 * @throws IllegalArgumentException if file argument is null or does not exist
	 * @throws IllegalStateException if encoding or I/O exception occurs
	 */
	public CsvInputStream(File file, Object[] arguments) throws FileNotFoundException {
		if (file == null) {
			throw new IllegalArgumentException("file should never be null");
		}
		if (!file.exists()) {
			throw new IllegalArgumentException("file does not exist: " + file);
		}
		InputStream stream = new FileInputStream(file);
		init(stream, arguments);
	}

	/**
	 * @param stream
	 * @param arguments
	 * @throws IllegalArgumentException if stream is null
	 * @throws IllegalStateException if encoding or I/O exception occurs
	 */
	public CsvInputStream(InputStream stream, Object[] arguments) {
		if (stream == null) {
			throw new IllegalArgumentException(
					"stream should never be null");
		}
		init(stream, arguments);
	}

	/**
	 * @param stream
	 * @param arguments
	 * @throws IllegalStateException if encoding or I/O exception occurs
	 */
	private void init(InputStream stream, Object[] arguments) {
		try {
			if (arguments != null) {
				if (arguments.length > 0 && arguments[0] != null) {
					schema = (Schema) arguments[0];
				}
				if (arguments.length > 1 && arguments[1] != null) {
					lineDelimiter = (String) arguments[1];
				}			
				if (arguments.length > 2 && arguments[2] != null) {
					valueDelimiter = (Character) arguments[2];
				}
				if (arguments.length > 3 && arguments[3] != null) {
					quote = (Character) arguments[3];
				}
			}
			
			reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
			reader = new BufferedReader(reader);
			
			if (lineDelimiter == null) {
				// Try to find out what the delimiter is, find a \r or \n and
				// see if it is followed by another control character
				reader.mark(1000);
				for(int i = 0; i < 1000; i++) {
					int ch = reader.read();
					if (ch == '\r' || ch == '\n') {
						StringBuilder b = new StringBuilder();
						do {
							b.append((char) ch);
							ch = reader.read();
						} while(ch < 0x20);
						lineDelimiter = b.toString();
						break;
					}
				}
				reader.reset();
				if (lineDelimiter == null) {
					lineDelimiter = "\n";
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("Problem in ctor", e);
		}
	}

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.input.IGISInputStream#close()
	 */
	public void close() {
		IOUtils.closeQuietly(reader);
	}

	/**
	 *
	 *
	 * @exception  IOException  If an I/O error occurs
	 * @exception  IllegalStateException  if encountered invalid input when parsing
	 */
	public IGISObject read() throws IOException {
		if (hasSaved()) {
			return super.readSaved();
		} else if (reader.ready()) {
			return readRow();
		} else {
			return null;
		}
	}

	/**
	 * @return row
	 * @exception  IOException  If an I/O error occurs
	 * @exception  IllegalStateException
	 */
	private IGISObject readRow() throws IOException {
		List<String> tokens = new ArrayList<>();
		while(reader.ready()) {
			Object[] token = readNextToken();
			tokens.add((String) token[0]);
			if (token[1] == EOF || token[1] == EOL) break;
		}
		URI suri;
		try {
			 suri = new URI("#csvschema");
		} catch (URISyntaxException e) {
			final IOException e2 = new IOException(e);
			throw e2;
		}
		if (schema == null) {
			schema = new Schema();
			schema.setId(suri);
			for(String header : tokens) {
				schema.put(new SimpleField(header));
			}
			return schema;
		}
		Row row = new Row();
		row.setSchema(suri);
		Iterator<String> iter = tokens.iterator();
		for(String fname : schema.getKeys()) {
			SimpleField field = schema.get(fname);
			if (iter.hasNext()) {
				String value = iter.next();
				row.putData(field, value);
			}
		}
		return row;
	}

	/**
	 * @return
	 * @throws  IOException  If an I/O error occurs
	 * @throws  IllegalStateException  if encountered invalid input when parsing
	 */
	private Object[] readNextToken() throws IOException {
		StringBuilder b = new StringBuilder();
		int in = reader.read();
		char ch = (char)in;
		// Handle case where there's no content directly
		if (in < 0) {
			return new Object[] {b.toString(), EOF};
		} else if (ch == valueDelimiter) {
			return new Object[] {b.toString(), null};
		} else if (checkLineDelimiter(ch)) {
			return new Object[] {b.toString(), EOL};
		}
		if (ch == quote) {
			in = reader.read();
			while(in >= 0) {
				ch = (char)in;
				if (ch == quote) {
					// either done or quoting the next character
					in = reader.read();
					ch = (char)in;
					if (ch != quote) {
						if (in < 0) {
							return new Object[] {b.toString(), EOF};
						} else if (ch == valueDelimiter) {
							return new Object[] {b.toString(), null};
						} else if (checkLineDelimiter(ch)) {
							return new Object[] {b.toString(), EOL};
						} else {
							throw new IllegalStateException("Found unexpected char " + ch);
						}
					}
				}
				b.append(ch);
				in = reader.read();
			}
			throw new IllegalStateException("Quoted string did not end as expected");
		} else {
			while(reader.ready() && in >= 0 && ch != valueDelimiter) {
				b.append(ch);
				in = reader.read();
				ch = (char)in;
				if (checkLineDelimiter(ch)) {
					return new Object[] {b.toString(), EOL};
				} else if (in < 0) {
					return new Object[] {b.toString(), EOF};
				}
			}
			return new Object[] {b.toString(), null};
		}
	}

	/**
	 * Mark the reader and check to see if we're at a line delimiter. If we haven't 
	 * found a delimiter, this method leaves the reader pointing to the next 
	 * character.
	 * @param ch the current character
	 * @return {@code true} if we've found a delimiter
	 * @throws  IOException  If an I/O error occurs
	 */
	private boolean checkLineDelimiter(char ch) throws IOException {
		// if explicitly specify lineDelim as \r\n in constructor and file was translated to UNIX lineDelim
		// then this doesn't recognize end of lines.
		if (ch != lineDelimiter.charAt(0)) 
			return false;
		int count = lineDelimiter.length() - 1;
		if (count > 0) { 
			reader.mark(count);
			for(int i = 0; i < count; i++) {
				int peek = reader.read();
				if (peek != lineDelimiter.charAt(i + 1)) {
					reader.reset();
					return false;
				}
			}
		}
		return true;
	}

}
