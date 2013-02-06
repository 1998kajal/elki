package de.lmu.ifi.dbs.elki.utilities.scaling.outlier;

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

import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.math.DoubleMinMax;
import de.lmu.ifi.dbs.elki.math.MathUtil;
import de.lmu.ifi.dbs.elki.math.MeanVariance;
import de.lmu.ifi.dbs.elki.math.statistics.distribution.NormalDistribution;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierResult;
import de.lmu.ifi.dbs.elki.utilities.documentation.Reference;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.DoubleParameter;

/**
 * Scaling that can map arbitrary values to a probability in the range of [0:1].
 * 
 * Transformation is done using the formulas
 * 
 * {@code y = sqrt(x - min)}
 * 
 * {@code newscore = max(0, erf(lambda * (y - mean) / (stddev * sqrt(2))))}
 * 
 * Where min and mean can be fixed to a given value, and stddev is then computed
 * against this mean.
 * 
 * @author Erich Schubert
 */
@Reference(authors = "H.-P. Kriegel, P. Kröger, E. Schubert, A. Zimek", title = "Interpreting and Unifying Outlier Scores", booktitle = "Proc. 11th SIAM International Conference on Data Mining (SDM), Mesa, AZ, 2011", url = "http://siam.omnibooksonline.com/2011datamining/data/papers/018.pdf")
public class SqrtStandardDeviationScaling implements OutlierScalingFunction {
  /**
   * Parameter to specify the fixed minimum to use.
   * <p>
   * Key: {@code -sqrtstddevscale.min}
   * </p>
   */
  public static final OptionID MIN_ID = new OptionID("sqrtstddevscale.min", "Fixed minimum to use in sqrt scaling.");

  /**
   * Parameter to specify a fixed mean to use.
   * <p>
   * Key: {@code -sqrtstddevscale.mean}
   * </p>
   */
  public static final OptionID MEAN_ID = new OptionID("sqrtstddevscale.mean", "Fixed mean to use in standard deviation scaling.");

  /**
   * Parameter to specify the lambda value
   * <p>
   * Key: {@code -sqrtstddevscale.lambda}
   * </p>
   */
  public static final OptionID LAMBDA_ID = new OptionID("sqrtstddevscale.lambda", "Significance level to use for error function.");

  /**
   * Field storing the lambda value
   */
  protected Double lambda = null;

  /**
   * Min to use
   */
  Double min = null;

  /**
   * Mean to use
   */
  Double mean = null;

  /**
   * Scaling factor to use (usually: Lambda * Stddev * Sqrt(2))
   */
  double factor;

  /**
   * Constructor.
   * 
   * @param min
   * @param mean
   * @param lambda
   */
  public SqrtStandardDeviationScaling(Double min, Double mean, Double lambda) {
    super();
    this.min = min;
    this.mean = mean;
    this.lambda = lambda;
  }

  @Override
  public double getScaled(double value) {
    assert (factor != 0) : "prepare() was not run prior to using the scaling function.";
    if(value <= min) {
      return 0;
    }
    value = (value <= min) ? 0 : Math.sqrt(value - min);
    if(value <= mean) {
      return 0;
    }
    return Math.max(0, NormalDistribution.erf((value - mean) / factor));
  }

  @Override
  public void prepare(OutlierResult or) {
    if(min == null) {
      DoubleMinMax mm = new DoubleMinMax();
      Relation<Double> scores = or.getScores();
      for(DBIDIter id = scores.iterDBIDs(); id.valid(); id.advance()) {
        double val = scores.get(id);
        if(!Double.isNaN(val) && !Double.isInfinite(val)) {
          mm.put(val);
        }
      }
      min = mm.getMin();
    }
    if(mean == null) {
      MeanVariance mv = new MeanVariance();
      Relation<Double> scores = or.getScores();
      for(DBIDIter id = scores.iterDBIDs(); id.valid(); id.advance()) {
        double val = scores.get(id);
        val = (val <= min) ? 0 : Math.sqrt(val - min);
        mv.put(val);
      }
      mean = mv.getMean();
      factor = lambda * mv.getSampleStddev() * MathUtil.SQRT2;
    }
    else {
      double sqsum = 0;
      int cnt = 0;
      Relation<Double> scores = or.getScores();
      for(DBIDIter id = scores.iterDBIDs(); id.valid(); id.advance()) {
        double val = scores.get(id);
        val = (val <= min) ? 0 : Math.sqrt(val - min);
        sqsum += (val - mean) * (val - mean);
        cnt += 1;
      }
      factor = lambda * Math.sqrt(sqsum / cnt) * MathUtil.SQRT2;
    }
  }

  @Override
  public double getMin() {
    return 0.0;
  }

  @Override
  public double getMax() {
    return 1.0;
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static class Parameterizer extends AbstractParameterizer {
    protected Double min = null;

    protected Double mean = null;

    protected Double lambda = null;

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      DoubleParameter minP = new DoubleParameter(MIN_ID);
      minP.setOptional(true);
      if(config.grab(minP)) {
        min = minP.getValue();
      }

      DoubleParameter meanP = new DoubleParameter(MEAN_ID);
      meanP.setOptional(true);
      if(config.grab(meanP)) {
        mean = meanP.getValue();
      }

      DoubleParameter lambdaP = new DoubleParameter(LAMBDA_ID, 3.0);
      if(config.grab(lambdaP)) {
        lambda = lambdaP.getValue();
      }
    }

    @Override
    protected SqrtStandardDeviationScaling makeInstance() {
      return new SqrtStandardDeviationScaling(min, mean, lambda);
    }
  }
}