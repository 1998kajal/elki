package de.lmu.ifi.dbs.elki.math.statistics;

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

import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;

/**
 * Uniform / Rectangular kernel density estimator.
 * 
 * @author Erich Schubert
 */
public final class UniformKernelDensityFunction implements KernelDensityFunction {
  @Override
  public double density(double delta) {
    if(delta < 1) {
      return 0.5;
    }
    return 0;
  }

  /**
   * Private, empty constructor. Use the static instance!
   */
  private UniformKernelDensityFunction() {
    // Nothing to do.
  }

  /**
   * Static instance.
   */
  public static final UniformKernelDensityFunction KERNEL = new UniformKernelDensityFunction();

  /**
   * Parameterization stub.
   * 
   * @author Erich Schubert
   *
   * @apiviz.exclude
   */
  public static class Parameterizer extends AbstractParameterizer {
    @Override
    protected UniformKernelDensityFunction makeInstance() {
      return KERNEL;
    }
  }
}
