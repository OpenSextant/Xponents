/****************************************************************************************
 *  TestWKTLexer.java
 *
 *  Created: Jan 13, 2012
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
package org.opensextant.giscore.test.input;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import org.junit.Test;
import org.opensextant.giscore.input.wkt.WKTLexer;
import org.opensextant.giscore.input.wkt.WKTToken;
import static org.junit.Assert.assertEquals;

public class TestWKTLexer {
	public final static String TestString = " FOO123(-43.23.124)(a)bc de124,42,124";
	
	/* @Test */
	public void testTokenBasics() {
		WKTToken idtoken = new WKTToken("ABC");
		
		assertEquals(WKTToken.TokenType.ID, idtoken.getType());
		assertEquals("ABC", idtoken.getIdentifier());
		assertEquals(new WKTToken("ABC"), idtoken);
		
		WKTToken ntoken = new WKTToken(123.4);
		
		assertEquals(WKTToken.TokenType.NUMBER, ntoken.getType());
		assertEquals(123.4, ntoken.getDouble(), 1e-5);
		assertEquals(new WKTToken(123.4), ntoken);
		
		WKTToken ctoken = new WKTToken('c');
		
		assertEquals(WKTToken.TokenType.CHAR, ctoken.getType());
		assertEquals('c', ctoken.getChar());
		assertEquals(new WKTToken('c'), ctoken);
	}
	
	@Test
	public void testLexerFunctionality() throws IOException {
		StringReader reader = new StringReader(TestString);
		WKTLexer lexer = new WKTLexer(new BufferedReader(reader));
		WKTToken token;
		
		token = lexer.nextToken();
		assertEquals(new WKTToken("FOO"), token);
		
		token = lexer.nextToken();
		assertEquals(new WKTToken(123), token);
		
		token = lexer.nextToken();
		assertEquals(new WKTToken('('), token);
		
		token = lexer.nextToken();
		assertEquals(new WKTToken(-43.23), token);
		
		token = lexer.nextToken();
		assertEquals(new WKTToken(.124), token);
		
		token = lexer.nextToken();
		assertEquals(new WKTToken(')'), token);
		
		token = lexer.nextToken();
		assertEquals(new WKTToken('('), token);
		
		token = lexer.nextToken();
		assertEquals(new WKTToken("a"), token);
		
		token = lexer.nextToken();
		assertEquals(new WKTToken(')'), token);
		
		token = lexer.nextToken();
		assertEquals(new WKTToken("bcde"), token);
		
		// 124,42,124
		
		token = lexer.nextToken();
		assertEquals(new WKTToken(124.), token);
		
		token = lexer.nextToken();
		assertEquals(new WKTToken(','), token);
		
		token = lexer.nextToken();
		assertEquals(new WKTToken(42.), token);
		
		token = lexer.nextToken();
		assertEquals(new WKTToken(','), token);
		
		token = lexer.nextToken();
		assertEquals(new WKTToken(124.), token);
		
		// Test push
		lexer.push(token);
		lexer.push(new WKTToken("A"));
		token = lexer.nextToken();
		assertEquals(new WKTToken("A"), token);
		token = lexer.nextToken();
		assertEquals(new WKTToken(124.), token);
		
	}

}
