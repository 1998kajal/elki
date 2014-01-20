package de.lmu.ifi.dbs.elki.distance.distancefunction.timeseries;

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

import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.data.type.VectorFieldTypeInformation;
import de.lmu.ifi.dbs.elki.distance.distancefunction.AbstractNumberVectorDistanceFunction;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.CommonConstraints;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.DoubleParameter;

/**
 * Provides the Edit Distance for FeatureVectors.
 * 
 * @author Thomas Bernecker
 */
public abstract class AbstractEditDistanceFunction extends AbstractNumberVectorDistanceFunction {
  /**
   * BANDSIZE parameter
   */
  public static final OptionID BANDSIZE_ID = new OptionID("edit.bandSize", "the band size for Edit Distance alignment (positive double value, 0 <= bandSize <= 1)");

  /**
   * Keeps the currently set bandSize.
   */
  protected double bandSize;

  /**
   * Constructor.
   * 
   * @param bandSize Band size
   */
  public AbstractEditDistanceFunction(double bandSize) {
    super();
    this.bandSize = bandSize;
  }

  // TODO: relax this to VectorTypeInformation!
  @Override
  public VectorFieldTypeInformation<? super NumberVector> getInputTypeRestriction() {
    return TypeUtil.NUMBER_VECTOR_FIELD;
  }

  @Override
  public boolean equals(Object obj) {
    if(obj == null) {
      return false;
    }
    if(!this.getClass().equals(obj.getClass())) {
      return false;
    }
    return this.bandSize == ((AbstractEditDistanceFunction) obj).bandSize;
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public abstract static class Parameterizer extends AbstractParameterizer {
    protected double bandSize = 0.0;

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      final DoubleParameter bandSizeP = new DoubleParameter(BANDSIZE_ID, 0.1);
      bandSizeP.addConstraint(CommonConstraints.GREATER_EQUAL_ZERO_DOUBLE);
      bandSizeP.addConstraint(CommonConstraints.LESS_EQUAL_ONE_DOUBLE);
      if(config.grab(bandSizeP)) {
        bandSize = bandSizeP.doubleValue();
      }
    }
  }
}
