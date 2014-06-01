package de.lmu.ifi.dbs.elki.datasource.filter.normalization;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2014
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

import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.type.SimpleTypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.data.type.VectorFieldTypeInformation;
import de.lmu.ifi.dbs.elki.datasource.bundle.MultipleObjectsBundle;
import de.lmu.ifi.dbs.elki.datasource.filter.FilterUtil;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.progress.FiniteProgress;
import de.lmu.ifi.dbs.elki.math.linearalgebra.LinearEquationSystem;
import de.lmu.ifi.dbs.elki.math.statistics.distribution.NormalDistribution;
import de.lmu.ifi.dbs.elki.utilities.FormatUtil;
import de.lmu.ifi.dbs.elki.utilities.datastructures.QuickSelect;
import de.lmu.ifi.dbs.elki.utilities.exceptions.ExceptionMessages;

/**
 * Median Absolute Deviation is used for scaling the data set as follows:
 * 
 * First, the median, and median absolute deviation are computed in each axis.
 * Then, each value is projected to (x - median(X)) / MAD(X).
 * 
 * This is similar to z-standardization of data sets, except that it is more
 * robust towards outliers, and only slightly more expensive to compute.
 * 
 * @author Erich Schubert
 * @param <V> vector type
 * 
 * @apiviz.uses NumberVector
 */
// TODO: extract superclass AbstractAttributeWiseNormalization
public class AttributeWiseMADNormalization<V extends NumberVector> implements Normalization<V> {
  /**
   * Class logger.
   */
  private static final Logging LOG = Logging.getLogger(AttributeWiseMADNormalization.class);

  /**
   * Number vector factory.
   */
  protected NumberVector.Factory<V> factory;

  /**
   * Stores the median in each dimension.
   */
  private double[] median = new double[0];

  /**
   * Stores the median absolute deviation in each dimension.
   */
  private double[] madsigma = new double[0];

  /**
   * Constructor.
   */
  public AttributeWiseMADNormalization() {
    super();
  }

  @Override
  public MultipleObjectsBundle filter(MultipleObjectsBundle objects) {
    if(objects.dataLength() == 0) {
      return objects;
    }
    for(int r = 0; r < objects.metaLength(); r++) {
      SimpleTypeInformation<?> type = (SimpleTypeInformation<?>) objects.meta(r);
      final List<?> column = (List<?>) objects.getColumn(r);
      if(!TypeUtil.NUMBER_VECTOR_FIELD.isAssignableFromType(type)) {
        continue;
      }
      @SuppressWarnings("unchecked")
      final List<V> castColumn = (List<V>) column;
      // Get the replacement type information
      @SuppressWarnings("unchecked")
      final VectorFieldTypeInformation<V> castType = (VectorFieldTypeInformation<V>) type;
      factory = FilterUtil.guessFactory(castType);

      // Scan to find the best
      final int dim = castType.getDimensionality();
      median = new double[dim];
      madsigma = new double[dim];
      // Scratch space for testing:
      double[] test = new double[castColumn.size()];

      FiniteProgress dprog = LOG.isVerbose() ? new FiniteProgress("Analyzing data.", dim, LOG) : null;
      // We iterate over dimensions, this kind of filter needs fast random
      // access.
      for(int d = 0; d < dim; d++) {
        for(int i = 0; i < test.length; i++) {
          test[i] = castColumn.get(i).doubleValue(d);
        }
        final double med = QuickSelect.median(test);
        median[d] = med;
        for(int i = 0; i < test.length; i++) {
          test[i] = Math.abs(test[i] - med);
        }
        // Rescale the true MAD for the best standard deviation estimate:
        madsigma[d] = QuickSelect.median(test) * NormalDistribution.ONEBYPHIINV075;
        if(!(madsigma[d] > 0)) {
          LOG.warning("Attribute " + d + " had a MAD of " + madsigma[d] + ". This normalization is not reliable on data sets with mostly duplicate values.");
          // Use smallest non-zero value instead.
          double min = Double.POSITIVE_INFINITY;
          for(double v : test) {
            min = (v > 0 && v < min) ? v : min;
          }
          // Second fallback: if all values were constant, it does not matter:
          madsigma[d] = (min < Double.POSITIVE_INFINITY) ? min * NormalDistribution.ONEBYPHIINV075 : 1.;
        }
        LOG.incrementProcessed(dprog);
      }
      LOG.ensureCompleted(dprog);

      FiniteProgress nprog = LOG.isVerbose() ? new FiniteProgress("Data normalization.", objects.dataLength(), LOG) : null;
      // Normalization scan
      double[] buf = new double[dim];
      for(int i = 0; i < objects.dataLength(); i++) {
        final V obj = castColumn.get(i);
        for(int d = 0; d < dim; d++) {
          buf[d] = normalize(d, obj.doubleValue(d));
        }
        castColumn.set(i, factory.newNumberVector(buf));
        LOG.incrementProcessed(nprog);
      }
      LOG.ensureCompleted(nprog);
    }
    return objects;
  }

  @Override
  public V restore(V featureVector) throws NonNumericFeaturesException {
    if(featureVector.getDimensionality() == median.length) {
      double[] values = new double[featureVector.getDimensionality()];
      for(int d = 0; d < featureVector.getDimensionality(); d++) {
        values[d] = restore(d, featureVector.doubleValue(d));
      }
      return factory.newNumberVector(values);
    }
    else {
      throw new NonNumericFeaturesException("Attributes cannot be resized: current dimensionality: " + featureVector.getDimensionality() + " former dimensionality: " + median.length);
    }
  }

  @Override
  public LinearEquationSystem transform(LinearEquationSystem linearEquationSystem) throws NonNumericFeaturesException {
    throw new UnsupportedOperationException(ExceptionMessages.UNSUPPORTED_NOT_YET);
  }

  /**
   * Normalize a single dimension.
   * 
   * @param d Dimension
   * @param val Value
   * @return Normalized value
   */
  private double normalize(int d, double val) {
    return (val - median[d]) / madsigma[d];
  }

  /**
   * Restore a single dimension.
   * 
   * @param d Dimension
   * @param val Value
   * @return Normalized value
   */
  private double restore(int d, double val) {
    return (val * madsigma[d]) + median[d];
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    result.append("normalization class: ").append(getClass().getName());
    result.append('\n');
    result.append("normalization median: ").append(FormatUtil.format(median));
    result.append('\n');
    result.append("normalization MAD sigma: ").append(FormatUtil.format(madsigma));
    return result.toString();
  }
}
