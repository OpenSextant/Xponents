/****************************************************************************************
 *  WKTInputStream.java
 *
 *  Created: Jan 12, 2012
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2012
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
package org.opensextant.giscore.input.wkt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.opensextant.geodesy.Angle;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.Geodetic3DPoint;
import org.opensextant.geodesy.Latitude;
import org.opensextant.geodesy.Longitude;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.geometry.*;
import org.opensextant.giscore.input.IGISInputStream;

/**
 * Reads geometries from an OGC Well-known text (WKT) formatted file. Geometries are formatted
 * in a straightforward fashion in WKT. Each geometry is introduced by a 
 * word and followed by a parenthesized list of coordinates.
 * 
 * Coordinates can be supplied as two, three or four values depending on whether
 * the coordinates specify x + y, x + y + z or x + y + z + m. The number of 
 * coordinates can be determined by whether the word is followed by another
 * specifier, either "M", "Z" or "ZM".
 * 
 * This library can represent Z but not M.
 * 
 * @author DRAND
 */
public class WKTInputStream implements IGISInputStream {

	protected Reader reader;
	private final WKTLexer lexer;
	//private WKTToken currentop;
	private boolean isM = false;
	private boolean isZ = false;
	
	/**
	 * Ctor
	 * @param stream   An InputStream
	 * @throws IllegalArgumentException if stream is null
         * @throws  IllegalStateException
         *             If UTF-8 encoding is not supported
	 */
	public WKTInputStream(InputStream stream) {
		if (stream == null) {
			throw new IllegalArgumentException("stream should never be null");
		}
		reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
		reader = new BufferedReader(reader);
		lexer = new WKTLexer(reader);
	}
	
	/**
	 * Ctor
	 * @param stream   An InputStream
	 * @param arguments
	 * @throws IllegalArgumentException if stream is null
	 */
	public WKTInputStream(InputStream stream, Object[] arguments) {
		this(stream);
	}

	/**
	 * Reads the next {@code IGISObject} from the InputStream.
	 *
	 * @return next {@code IGISObject},
	 *         or {@code null} if the end of the stream is reached.
	 * @throws IOException if an I/O error occurs or if there
	 * @throws IllegalStateException if a fatal error with the underlying data structure
	 */
	@Override
	public IGISObject read() throws IOException {
		WKTToken currentop = lexer.nextToken();
		if(currentop == null) {
			return null;
		} else if (! currentop.getType().equals(WKTToken.TokenType.ID)) {
			throw new IllegalStateException("Expected an identifier but found the token type " + currentop.getType() + " instead");
		}
		
		// Find out if we have extra geometry and remove the M and/or Z from
		// the type of the geometry to ease matching
		String id = currentop.getIdentifier();
		id = id.toUpperCase();
		isM = id.endsWith("ZM") || id.endsWith("M") || id.endsWith("MZ");
		isZ = id.endsWith("ZM") || id.endsWith("Z") || id.endsWith("MZ");
		if (isZ && isM) {
			id = id.substring(0, id.length() - 2);
		} else if (isZ || isM) {
			id = id.substring(0, id.length() - 1);
		}
		
		if ("POINT".equals(id)) {
			return readPoint();
		} else if ("LINESTRING".equals(id)) {
			return readLine();
		} else if ("POLYGON".equals(id)) {
			return readPolygon();
		} else if ("MULTIPOINT".equals(id)) {
			return readMultiPoint();
		} else if ("MULTILINESTRING".equals(id)) {
			return readMultiLineString();
		} else if ("MULTIPOLYGON".equals(id)) {
			return readMultiPolygon();
		} else if ("GEOMETRYCOLLECTION".equals(id)) {
			return readGeometryCollection();
		}
		
		return null;
	}

	private IGISObject readPoint() throws IOException {
		expectParen();
		Geodetic2DPoint pnt = readCoordinate();
		expectThesis();
		return new Point(pnt);
	}
	
	private IGISObject readLine() throws IOException {
		return new Line(readPointList()); 
	}
	
	private IGISObject readPolygon() throws IOException {
		expectParen();
		WKTToken token = lexer.nextToken();
		LinearRing outerRing = null;
		List<LinearRing> innerRings = new ArrayList<>();
		while(true) {
			if (token.getType().equals(WKTToken.TokenType.CHAR)) {
				int ch = token.getChar();
				if (')' == ch) {
					// Done
					return new Polygon(outerRing, innerRings);
				} else if ('(' == ch) {
					lexer.push(token);
					// A ring
					List<Point> pnts = readPointList();
					LinearRing ring = new LinearRing(pnts);
					if (outerRing == null) {
						outerRing = ring;
					} else {
						innerRings.add(ring);
					}
				} else if (',' == ch) {
					// Ignore, it's a separator
				} else {
					// Error, something that doesn't belong
					throw new IOException("Found an unexpected character in POLYGON: " + token);
				}
			}
			token = lexer.nextToken();
		}
	}

	private IGISObject readMultiPoint() throws IOException {
		return new MultiPoint(readPointList());
	}

	private IGISObject readMultiLineString() throws IOException {
		expectParen();
		List<Line> lines = new ArrayList<>();
		WKTToken token = lexer.nextToken();
		while(true) {
			if (token.getType().equals(WKTToken.TokenType.CHAR)) {
				int ch = token.getChar();
				if (')' == ch) {
					// Done
					return new MultiLine(lines);
				} else if ('(' == ch) {
					lexer.push(token);
					// A line
					List<Point> pnts = readPointList();
					lines.add(new Line(pnts));
				} else if (',' == ch) {
					// Ignore, it's a separator
				} else {
					// Error, something that doesn't belong
					throw new IOException("Found an unexpected character in POLYGON: " + token);
				}				
			}
			token = lexer.nextToken();
		}
	}
	
	private IGISObject readMultiPolygon() throws IOException {
		expectParen();
		List<Polygon> polys = new ArrayList<>();
		WKTToken token = lexer.nextToken();
		while(true) {
			if (token.getType().equals(WKTToken.TokenType.CHAR)) {
				int ch = token.getChar();
				if (')' == ch) {
					// Done
					return new MultiPolygons(polys);
				} else if ('(' == ch) {
					lexer.push(token);
					// A polygon
					Polygon poly = (Polygon) readPolygon();
					polys.add(poly);
				} else if (',' == ch) {
					// Ignore, it's a separator
				} else {
					// Error, something that doesn't belong
					throw new IOException("Found an unexpected character in POLYGON: " + token);
				}				
			}
			token = lexer.nextToken();
		}			
	}
	
	private IGISObject readGeometryCollection() throws IOException {
		expectParen();
		List<Geometry> geometries = new ArrayList<>();
		WKTToken token = lexer.nextToken();
		while(true) {
			if (token.getType().equals(WKTToken.TokenType.ID)) {
				lexer.push(token);
				// Some geometry
				Geometry geo = (Geometry) read();
				geometries.add(geo);
			} else if (token.getType().equals(WKTToken.TokenType.CHAR)) {
				int ch = token.getChar();
				if (')' == ch) {
					// Done
					return new GeometryBag(geometries);
				} else if (',' == ch) {
					// Ignore, it's a separator
				} else {
					// Error, something that doesn't belong
					throw new IOException("Found an unexpected character in POLYGON: " + token);
				}				
			}
			token = lexer.nextToken();
		}		
	}
		
	private List<Point> readPointList() throws IOException {
		expectParen();
		// Read a token and see if we're at the thesis, a comma or the next
		// coordinate
		WKTToken token = lexer.nextToken();
		List<Point> pnts = new ArrayList<>();
		while(true) {
			if (token.getType().equals(WKTToken.TokenType.CHAR)) {
				int ch = token.getChar();
				if (')' == ch) {
					return pnts;
				} else if (',' == ch) {
					// Ignore, it's a separator
				} else {
					// Error, something that doesn't belong
					throw new IOException("Found an unexpected character in LINESTRING: " + token);
				}				
			} else if (token.getType().equals(WKTToken.TokenType.NUMBER)) {
				lexer.push(token);
				pnts.add(new Point(readCoordinate()));
			} else {
				throw new IOException("Found an unexpected identifier in LINESTRING: " + token);
			}
			token = lexer.nextToken();
		}		
	}
	
	/**
	 * Read 2, 3 or 4 numbers depending on the values of isM and isZ. Then 
	 * use the appropriate constructor to make a point.
	 * @return the point constructed
	 * @throws IOException if an error occurs
	 */
	private Geodetic2DPoint readCoordinate() throws IOException {
		WKTToken x, y, z = null;
		
		x = lexer.nextToken();
		y = lexer.nextToken();
		if (isZ) {
			z = lexer.nextToken();
		}
		if (isM) {
			lexer.nextToken(); // Just to consumer, we don't use it
		}
		
		if (isZ) {
			if (x.getType().equals(WKTToken.TokenType.NUMBER) && 
					y.getType().equals(WKTToken.TokenType.NUMBER) &&
					z.getType().equals(WKTToken.TokenType.NUMBER)) {
				Longitude lon = new Longitude(x.getDouble(), Angle.DEGREES);
				Latitude lat = new Latitude(y.getDouble(), Angle.DEGREES);
				return new Geodetic3DPoint(lon, lat, z.getDouble());
			} else {
				throw new IllegalStateException("One of these tokens was not a number: " + x + ", " + y + ", " + z);
			}
		} else {
			if (x.getType().equals(WKTToken.TokenType.NUMBER) && 
					y.getType().equals(WKTToken.TokenType.NUMBER)) {
				Longitude lon = new Longitude(x.getDouble(), Angle.DEGREES);
				Latitude lat = new Latitude(y.getDouble(), Angle.DEGREES);
				return new Geodetic2DPoint(lon, lat);
			} else {
				throw new IllegalStateException("One of these tokens was not a number: " + x + ", " + y);
			}
		}
	}

	/**
	 * Expect a ')' to close a parenthetical expression
	 * @throws IOException  If an I/O error occurs
	 */
	private void expectThesis() throws IOException {
		WKTToken token = lexer.nextToken();
		if (token.getType().equals(WKTToken.TokenType.CHAR)) {
			if (')' == token.getChar()) {
				return;
			}
		}
		throw new IOException("Found the wrong token when looking for a close parenthesis: " + token);
	}

	private void expectParen() throws IOException {
		WKTToken token = lexer.nextToken();
		if (token.getType().equals(WKTToken.TokenType.CHAR)) {
			if ('(' == token.getChar()) {
				return;
			}
		}
		throw new IOException("Found the wrong token when looking for a open parenthesis: " + token);
	}

	@Override
	public void close() {
		IOUtils.closeQuietly(reader);
	}

	@NotNull
	public Iterator<Schema> enumerateSchemata() throws IOException {
		throw new UnsupportedOperationException();
	}
}
