package de.lmu.ifi.dbs.elki.math.statistics.distribution;

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
import java.util.Random;

/**
 * Kappa distribution, by Hosking.
 * 
 * TODO: add references.
 * 
 * @author Erich Schubert
 */
public class KappaDistribution implements DistributionWithRandom {
  /**
   * Parameters: location and scale
   */
  double location, scale;

  /**
   * Shape parameters.
   */
  double shape1, shape2;

  /**
   * Random number generator
   */
  Random random;

  /**
   * Constructor.
   * 
   * @param location Location
   * @param scale Scale
   * @param shape1 Shape parameter
   * @param shape2 Shape parameter
   */
  public KappaDistribution(double location, double scale, double shape1, double shape2) {
    this(location, scale, shape1, shape2, null);
  }

  /**
   * Constructor.
   * 
   * @param location Location
   * @param scale Scale
   * @param shape1 Shape parameter
   * @param shape2 Shape parameter
   * @param random Random number generator
   */
  public KappaDistribution(double location, double scale, double shape1, double shape2, Random random) {
    super();
    this.location = location;
    this.scale = scale;
    this.shape1 = shape1;
    this.shape2 = shape2;
    this.random = random;
    if(shape2 >= 0.) {
      if(shape1 < -1.) {
        throw new ArithmeticException("Invalid shape1 parameter - must be greater than -1 if shape2 >= 0.!");
      }
    }
    else {
      if(shape1 < 1. || shape1 > 1. / shape2) {
        throw new ArithmeticException("Invalid shape1 parameter - must be -1 to +1/shape2 if shape2 < 0.!");
      }
    }
  }

  /**
   * Probability density function.
   * 
   * @param val Value
   * @param loc Location
   * @param scale Scale
   * @param shape1 Shape parameter
   * @param shape2 Shape parameter
   * @return PDF
   */
  public static double pdf(double val, double loc, double scale, double shape1, double shape2) {
    final double c = cdf(val, loc, scale, shape1, shape2);
    val = (val - loc) / scale;
    if(shape1 != 0.) {
      val = 1 - shape1 * val;
      if(val < 1e-15) {
        return 0.;
      }
      val = (1. - 1. / shape1) * Math.log(val);
    }
    val = Math.exp(-val);
    return val / scale * Math.pow(c, 1. - shape2);
  }

  @Override
  public double pdf(double val) {
    return pdf(val, location, scale, shape1, shape2);
  }

  /**
   * Cumulative density function.
   * 
   * @param val Value
   * @param loc Location
   * @param scale Scale
   * @param shape1 Shape parameter
   * @param shape2 Shape parameter
   * @return CDF
   */
  public static double cdf(double val, double loc, double scale, double shape1, double shape2) {
    val = (val - loc) / scale;
    if(shape1 != 0.) {
      double tmp = 1. - shape1 * val;
      if(tmp < 1e-15) {
        return (shape1 < 0.) ? 0. : 1.;
      }
      val = Math.exp(Math.log(tmp) / shape1);
    }
    else {
      val = Math.exp(-val);
    }
    if(shape2 != 0.) {
      double tmp = 1. - shape2 * val;
      if(tmp < 1e-15) {
        return 0.;
      }
      val = Math.exp(Math.log(tmp) / shape2);
    }
    else {
      val = Math.exp(-val);
    }
    return val;
  }

  @Override
  public double cdf(double val) {
    return cdf(val, location, scale, shape1, shape2);
  }

  /**
   * Quantile function.
   * 
   * @param val Value
   * @param loc Location
   * @param scale Scale
   * @param shape1 Shape parameter
   * @param shape2 Shape parameter
   * @return Quantile
   */
  public static double quantile(double val, double loc, double scale, double shape1, double shape2) {
    if(!(val >= 0.) || !(val <= 1.)) {
      return Double.NaN;
    }
    if(val == 0.) {
      if(shape2 <= 0.) {
        if(shape1 < 0.) {
          return loc + scale / shape1;
        }
        else {
          return Double.NEGATIVE_INFINITY;
        }
      }
      else {
        if(shape1 != 0.) {
          return loc + scale / shape1 * (1. - Math.pow(shape2, -shape1));
        }
        else {
          return loc + scale * Math.log(shape2);
        }
      }
    }
    if(val == 1.) {
      if(shape1 <= 0.) {
        return Double.NEGATIVE_INFINITY;
      }
      return loc + scale / shape1;
    }
    val = -Math.log(val);
    if(shape2 != 0.) {
      val = (1 - Math.exp(-shape2 * val)) / shape2;
    }
    val = -Math.log(val);
    if(shape1 != 0.) {
      val = (1 - Math.exp(-shape1 * val)) / shape1;
    }
    return loc + scale * val;
  }

  @Override
  public double quantile(double val) {
    return quantile(val, location, scale, shape1, shape2);
  }

  @Override
  public double nextRandom() {
    double u = random.nextDouble();
    return quantile(u, location, scale, shape1, shape2);
  }

  @Override
  public String toString() {
    return "KappaDistribution(location=" + location + ", scale=" + scale + ", shape1=" + shape1 + ", shape2=" + shape2 + ")";
  }
}
