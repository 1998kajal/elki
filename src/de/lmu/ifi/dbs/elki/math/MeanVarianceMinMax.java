package de.lmu.ifi.dbs.elki.math;

import de.lmu.ifi.dbs.elki.utilities.exceptions.AbortException;

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
/**
 * Class collecting mean, variance, minimum and maximum statistics.
 * 
 * @author Erich Schubert
 */
public class MeanVarianceMinMax extends MeanVariance {
  /**
   * Minimum value
   */
  double min = Double.POSITIVE_INFINITY;

  /**
   * Maximum value
   */
  double max = Double.NEGATIVE_INFINITY;

  /**
   * Constructor.
   */
  public MeanVarianceMinMax() {
    super();
  }

  /**
   * Constructor cloning existing statistics.
   * 
   * @param other Existing statistics
   */
  public MeanVarianceMinMax(MeanVarianceMinMax other) {
    super(other);
    this.min = other.min;
    this.max = other.max;
  }

  @Override
  public void put(double val) {
    super.put(val);
    min = Math.min(min, val);
    max = Math.max(max, val);
  }

  @Override
  public void put(double val, double weight) {
    super.put(val, weight);
    min = Math.min(min, val);
    max = Math.max(max, val);
  }

  @Override
  public void put(Mean other) {
    if(other instanceof MeanVarianceMinMax) {
      super.put(other);
      min = Math.min(min, ((MeanVarianceMinMax) other).min);
      max = Math.max(max, ((MeanVarianceMinMax) other).max);
    }
    else {
      throw new AbortException("Cannot aggregate into a minmax statistic: " + other.getClass());
    }
  }

  /**
   * Get the current minimum.
   * 
   * @return current minimum.
   */
  public double getMin() {
    return this.min;
  }

  /**
   * Get the current maximum.
   * 
   * @return current maximum.
   */
  public double getMax() {
    return this.max;
  }
  
  /**
   * Get the current minimum and maximum.
   * 
   * @return current minimum and maximum
   */
  public DoubleMinMax getDoubleMinMax(){
    return new DoubleMinMax(this.min,this.max);
  }

  /**
   * Return the difference between minimum and maximum.
   * 
   * @return Difference of current Minimum and Maximum.
   */
  public double getDiff() {
    return this.getMax() - this.getMin();
  }
  
  /**
   * Create and initialize a new array of MeanVarianceMinMax
   * 
   * @param dimensionality Dimensionality
   * @return New and initialized Array
   */
  public static MeanVarianceMinMax[] newArray(int dimensionality) {
    MeanVarianceMinMax[] arr = new MeanVarianceMinMax[dimensionality];
    for(int i = 0; i < dimensionality; i++) {
      arr[i] = new MeanVarianceMinMax();
    }
    return arr;
  }

  @Override
  public String toString() {
    return "MeanVarianceMinMax(mean=" + getMean() + ",var=" + getSampleVariance() + ",min=" + getMin() + ",max=" + getMax() + ")";
  }

  @Override
  public void reset() {
    super.reset();
    min = Double.POSITIVE_INFINITY;
    max = Double.NEGATIVE_INFINITY;
  }
}