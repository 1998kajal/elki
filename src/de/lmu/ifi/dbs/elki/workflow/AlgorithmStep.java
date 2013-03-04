package de.lmu.ifi.dbs.elki.workflow;

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

import java.util.List;

import de.lmu.ifi.dbs.elki.algorithm.Algorithm;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.index.Index;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.LoggingConfiguration;
import de.lmu.ifi.dbs.elki.logging.statistics.Duration;
import de.lmu.ifi.dbs.elki.result.BasicResult;
import de.lmu.ifi.dbs.elki.result.HierarchicalResult;
import de.lmu.ifi.dbs.elki.result.Result;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.Flag;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.ObjectListParameter;

/**
 * The "algorithms" step, where data is analyzed.
 * 
 * @author Erich Schubert
 * 
 * @apiviz.has Algorithm
 * @apiviz.has Result
 * @apiviz.uses Database
 */
public class AlgorithmStep implements WorkflowStep {
  /**
   * Logger
   */
  private static final Logging LOG = Logging.getLogger(AlgorithmStep.class);

  /**
   * Holds the algorithm to run.
   */
  private List<Algorithm> algorithms;

  /**
   * The algorithm output
   */
  private BasicResult result = null;

  /**
   * Constructor.
   * 
   * @param algorithms
   */
  public AlgorithmStep(List<Algorithm> algorithms) {
    super();
    this.algorithms = algorithms;
  }

  /**
   * Run algorithms.
   * 
   * @param database Database
   * @return Algorithm result
   */
  public HierarchicalResult runAlgorithms(Database database) {
    result = new BasicResult("Algorithm Step", "main");
    result.addChildResult(database);
    if (LOG.isStatistics() && database.getIndexes().size() > 0) {
      LOG.statistics("Index statistics before running algorithms:");
      for (Index idx : database.getIndexes()) {
        idx.logStatistics();
      }
    }
    for (Algorithm algorithm : algorithms) {
      Duration duration = LOG.isStatistics() ? LOG.newDuration(algorithm.getClass().getName()+".runtime") : null;
      if (duration != null) {
        duration.begin();
      }
      Result res = algorithm.run(database);
      if (duration != null) {
        duration.end();
        LOG.statistics(duration);
      }
      if (LOG.isStatistics()) {
        LOG.statistics("Index statistics after running algorithms:");
        for (Index idx : database.getIndexes()) {
          idx.logStatistics();
        }
      }
      if (res != null) {
        result.addChildResult(res);
      }
    }
    return result;
  }

  /**
   * Get the algorithm result.
   * 
   * @return Algorithm result.
   */
  public HierarchicalResult getResult() {
    return result;
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static class Parameterizer extends AbstractParameterizer {
    /**
     * Enable logging of performance data
     */
    protected boolean time = false;

    /**
     * Holds the algorithm to run.
     */
    protected List<Algorithm> algorithms;

    /**
     * Flag to allow verbose messages while running the application.
     * <p>
     * Key: {@code -time}
     * </p>
     */
    public static final OptionID TIME_ID = new OptionID("time", "Enable logging of runtime data. Do not combine with more verbose logging, since verbose logging can significantly impact performance.");

    /**
     * Parameter to specify the algorithm to run.
     * <p>
     * Key: {@code -algorithm}
     * </p>
     */
    public static final OptionID ALGORITHM_ID = new OptionID("algorithm", "Algorithm to run.");

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      // Time parameter
      final Flag timeF = new Flag(TIME_ID);
      if (config.grab(timeF)) {
        time = timeF.getValue();
      }
      // parameter algorithm
      final ObjectListParameter<Algorithm> ALGORITHM_PARAM = new ObjectListParameter<>(ALGORITHM_ID, Algorithm.class);
      if (config.grab(ALGORITHM_PARAM)) {
        algorithms = ALGORITHM_PARAM.instantiateClasses(config);
      }
    }

    @Override
    protected AlgorithmStep makeInstance() {
      LoggingConfiguration.setStatistics(time);
      return new AlgorithmStep(algorithms);
    }
  }
}
