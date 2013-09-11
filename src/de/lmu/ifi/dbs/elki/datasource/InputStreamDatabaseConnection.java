package de.lmu.ifi.dbs.elki.datasource;

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

import java.io.InputStream;
import java.util.List;

import de.lmu.ifi.dbs.elki.datasource.bundle.MultipleObjectsBundle;
import de.lmu.ifi.dbs.elki.datasource.filter.ObjectFilter;
import de.lmu.ifi.dbs.elki.datasource.parser.NumberVectorLabelParser;
import de.lmu.ifi.dbs.elki.datasource.parser.Parser;
import de.lmu.ifi.dbs.elki.datasource.parser.StreamingParser;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.statistics.Duration;
import de.lmu.ifi.dbs.elki.utilities.documentation.Description;
import de.lmu.ifi.dbs.elki.utilities.documentation.Title;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;

/**
 * Provides a database connection expecting input from an input stream such as
 * stdin.
 * 
 * @author Arthur Zimek
 * 
 * @apiviz.uses Parser oneway - - runs
 */
@Title("Input-Stream based database connection")
@Description("Parse an input stream such as STDIN into a database.")
public class InputStreamDatabaseConnection extends AbstractDatabaseConnection {
  /**
   * The logger for this class.
   */
  private static final Logging LOG = Logging.getLogger(InputStreamDatabaseConnection.class);

  /**
   * Holds the instance of the parser.
   */
  Parser parser;

  /**
   * The input stream to parse from.
   */
  InputStream in = System.in;

  /**
   * Constructor.
   * 
   * @param filters Filters to use
   * @param parser the parser to provide a database
   */
  public InputStreamDatabaseConnection(List<ObjectFilter> filters, Parser parser) {
    super(filters);
    this.parser = parser;
  }

  @Override
  public MultipleObjectsBundle loadData() {
    // Run parser
    if(LOG.isDebugging()) {
      LOG.debugFine("Invoking parsers.");
    }
    if(parser instanceof StreamingParser) {
      final StreamingParser streamParser = (StreamingParser)parser;
      streamParser.initStream(in);

      // normalize objects and transform labels
      if(LOG.isDebugging()) {
        LOG.debugFine("Invoking filters.");
      }
      Duration duration = LOG.isStatistics() ? LOG.newDuration(this.getClass().getName() + ".load") : null;
      if (duration != null) {
        duration.begin();
      }
      MultipleObjectsBundle objects = MultipleObjectsBundle.fromStream(invokeFilters(streamParser));
      if (duration != null) {
        duration.end();
        LOG.statistics(duration);
      }
      return objects;
    }
    else {
      Duration duration = LOG.isStatistics() ? LOG.newDuration(this.getClass().getName() + ".parse") : null;
      if (duration != null) {
        duration.begin();
      }
      MultipleObjectsBundle parsingResult = parser.parse(in);
      if (duration != null) {
        duration.end();
        LOG.statistics(duration);
      }

      // normalize objects and transform labels
      if(LOG.isDebugging()) {
        LOG.debugFine("Invoking filters.");
      }
      Duration fduration = LOG.isStatistics() ? LOG.newDuration(this.getClass().getName() + ".filter") : null;
      if (fduration != null) {
        fduration.begin();
      }
      MultipleObjectsBundle objects = invokeFilters(parsingResult);
      if (fduration != null) {
        fduration.end();
        LOG.statistics(fduration);
      }
      return objects;
    }
  }

  @Override
  protected Logging getLogger() {
    return LOG;
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static class Parameterizer extends AbstractDatabaseConnection.Parameterizer {
    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      configParser(config, Parser.class, NumberVectorLabelParser.class);
      configFilters(config);
    }

    @Override
    protected InputStreamDatabaseConnection makeInstance() {
      return new InputStreamDatabaseConnection(filters, parser);
    }
  }
}