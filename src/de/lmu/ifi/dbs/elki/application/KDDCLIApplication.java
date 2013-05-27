package de.lmu.ifi.dbs.elki.application;

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

import de.lmu.ifi.dbs.elki.KDDTask;
import de.lmu.ifi.dbs.elki.algorithm.Algorithm;
import de.lmu.ifi.dbs.elki.utilities.Alias;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.workflow.OutputStep;

/**
 * Provides a KDDCLIApplication that can be used to perform any algorithm
 * implementing {@link Algorithm Algorithm} using any DatabaseConnection
 * implementing {@link de.lmu.ifi.dbs.elki.datasource.DatabaseConnection
 * DatabaseConnection}.
 * 
 * @author Arthur Zimek
 * 
 * @apiviz.composedOf KDDTask
 */
@Alias({ "cli", "kddtask" })
public class KDDCLIApplication extends AbstractApplication {
  /**
   * The KDD Task to perform.
   */
  KDDTask task;

  /**
   * Constructor.
   * 
   * @param task Task to run
   */
  public KDDCLIApplication(KDDTask task) {
    super();
    this.task = task;
  }

  @Override
  public void run() {
    task.run();
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static class Parameterizer extends AbstractApplication.Parameterizer {
    /**
     * The KDD Task to perform.
     */
    protected KDDTask task;

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      task = config.tryInstantiate(KDDTask.class);
    }

    @Override
    protected KDDCLIApplication makeInstance() {
      return new KDDCLIApplication(task);
    }
  }

  /**
   * Runs a KDD task accordingly to the specified parameters.
   * 
   * @param args parameter list according to description
   */
  public static void main(String[] args) {
    OutputStep.setDefaultHandlerWriter();
    runCLIApplication(KDDCLIApplication.class, args);
  }
}
