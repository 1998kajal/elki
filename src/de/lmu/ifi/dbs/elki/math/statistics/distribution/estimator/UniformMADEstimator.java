package de.lmu.ifi.dbs.elki.math.statistics.distribution.estimator;

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
import de.lmu.ifi.dbs.elki.math.statistics.distribution.UniformDistribution;
import de.lmu.ifi.dbs.elki.utilities.documentation.Reference;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;

/**
 * Estimate Uniform distribution parameters using Median and MAD.
 * 
 * Reference:
 * <p>
 * Robust Estimators for Transformed Location Scale Families<br />
 * D. J. Olive
 * </p>
 * 
 * @author Erich Schubert
 * 
 * @apiviz.has UniformDistribution
 */
@Reference(title = "Robust Estimators for Transformed Location Scale Families", authors = "D. J. Olive", booktitle = "")
public class UniformMADEstimator extends AbstractMADEstimator<UniformDistribution> {
  /**
   * Static instance.
   */
  public static final UniformMADEstimator STATIC = new UniformMADEstimator();

  /**
   * Private constructor, use static instance!
   */
  private UniformMADEstimator() {
    // Do not instantiate
  }

  @Override
  public UniformDistribution estimateFromMedianMAD(double median, double mad) {
    return new UniformDistribution(median - 2 * mad, median + 2 * mad);
  }

  @Override
  public Class<? super UniformDistribution> getDistributionClass() {
    return UniformDistribution.class;
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static class Parameterizer extends AbstractParameterizer {
    @Override
    protected UniformMADEstimator makeInstance() {
      return STATIC;
    }
  }
}
