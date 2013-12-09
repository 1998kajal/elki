package de.lmu.ifi.dbs.elki.datasource.parser;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2013
 Ludwig-Maximilians-Universität München
 Lehr- und Forschungseinheit für Datenbanksysteme
 ELKI Development Team

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.regex.Pattern;

import org.junit.Test;

import de.lmu.ifi.dbs.elki.JUnit4Test;

/**
 * Simple unit test for testing the new tokenizer
 * 
 * TODO: add more test cases, refactor into input, expected-output pattern.
 * 
 * @author Erich Schubert
 */
public class TestTokenizer implements JUnit4Test {
  @Test
  public void testSimple() {
    final String input = "1 -234 3.1415 - banana\n";
    final Object[] expect = { 1L, -234L, 3.1415, "-", "banana" };

    tokenizerTest(new Tokenizer(Pattern.compile("\\s"), "\"'"), input, expect);
  }

  @Test
  public void testQuotes() {
    final String input = "'this is' \"a test\" '123' '123 456' \"bana' na\"\n";
    final Object[] expect = { "this is", "a test", 123L, "123 456", "bana' na" };
    tokenizerTest(new Tokenizer(Pattern.compile("\\s"), "\"'"), input, expect);
  }

  @Test
  public void testSpecials() {
    final String input = "nan inf -∞ NaN infinity NA\n";
    final Object[] expect = { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN, Double.POSITIVE_INFINITY, Double.NaN };
    tokenizerTest(new Tokenizer(Pattern.compile("\\s"), "\"'"), input, expect);
  }

  private void tokenizerTest(Tokenizer t, String input, Object[] expect) {
    t.initialize(input); // Initializer.

    for(int i = 0; i < expect.length; i++, t.advance()) {
      assertTrue("Tokenizer stopped early.", t.valid());
      Object e = expect[i];
      // Negative tests first:
      if(e instanceof String || e instanceof Double) {
        try {
          long val = t.getLongBase10();
          fail("The value " + t.getSubstring() + " was expected to be not parseable as long integer, but returned: " + val);
        }
        catch(Exception ex) {
          // pass. this is expected to fail.
        }
      }
      if(e instanceof String) {
        try {
          double val = t.getDouble();
          fail("The value " + t.getSubstring() + " was expected to be not parseable as double, but returned: " + val);
        }
        catch(Exception ex) {
          // pass. this is expected to fail.
        }
      }
      // Positive tests:
      if(e instanceof Long) {
        assertEquals("Long parsing failed.", (long) e, t.getLongBase10());
      }
      if(e instanceof Double) {
        // Note: this also works for NaNs, they are treated special.
        assertEquals("Double parsing failed.", (double) e, t.getDouble(), Double.MIN_VALUE);
      }
      if(e instanceof String) {
        assertEquals("String parsing failed.", (String) e, t.getSubstring());
      }
    }
    assertTrue("Spurious data after expected end: " + t.getSubstring(), !t.valid());
  }
}
