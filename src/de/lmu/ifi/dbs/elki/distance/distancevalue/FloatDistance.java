package de.lmu.ifi.dbs.elki.distance.distancevalue;

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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.regex.Pattern;

import de.lmu.ifi.dbs.elki.utilities.FormatUtil;

/**
 * Provides a Distance for a float-valued distance.
 * 
 * @author Elke Achtert
 */
public class FloatDistance extends NumberDistance<FloatDistance, Float> {
  /**
   * The static factory instance
   */
  public static final FloatDistance FACTORY = new FloatDistance();

  /**
   * The distance value.
   */
  private float value;

  /**
   * Generated serialVersionUID.
   */
  private static final long serialVersionUID = -5702250266358369075L;

  /**
   * Infinite distance.
   */
  public static final FloatDistance INFINITE_DISTANCE = new FloatDistance(Float.POSITIVE_INFINITY);

  /**
   * Zero distance.
   */
  public static final FloatDistance ZERO_DISTANCE = new FloatDistance(0.0F);

  /**
   * Undefined distance.
   */
  public static final FloatDistance UNDEFINED_DISTANCE = new FloatDistance(Float.NaN);

  /**
   * Empty constructor for serialization purposes.
   */
  public FloatDistance() {
    super();
  }

  /**
   * Constructs a new FloatDistance object that represents the float argument.
   * 
   * @param value the value to be represented by the FloatDistance.
   */
  public FloatDistance(float value) {
    super();
    this.value = value;
  }

  @Override
  public FloatDistance fromDouble(double val) {
    return new FloatDistance((float) val);
  }

  /**
   * Writes the float value of this FloatDistance to the specified stream.
   */
  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
    out.writeFloat(value);
  }

  /**
   * Reads the float value of this FloatDistance from the specified stream.
   */
  @Override
  public void readExternal(ObjectInput in) throws IOException {
    value = in.readFloat();
  }

  /**
   * Returns the number of Bytes this distance uses if it is written to an
   * external file.
   * 
   * @return 4 (4 Byte for a float value)
   */
  @Override
  public int externalizableSize() {
    return 4;
  }

  @Override
  public double doubleValue() {
    return value;
  }

  @Override
  public float floatValue() {
    return value;
  }

  @Override
  public long longValue() {
    return (long) value;
  }

  @Override
  public int compareTo(FloatDistance other) {
    return Float.compare(this.value, other.value);
  }

  /**
   * An infinite FloatDistance is based on {@link Float#POSITIVE_INFINITY
   * Float.POSITIVE_INFINITY}.
   */
  @Override
  public FloatDistance infiniteDistance() {
    return INFINITE_DISTANCE;
  }

  /**
   * A null FloatDistance is based on 0.
   */
  @Override
  public FloatDistance nullDistance() {
    return ZERO_DISTANCE;
  }

  /**
   * An undefined FloatDistance is based on {@link Float#NaN Float.NaN}.
   */
  @Override
  public FloatDistance undefinedDistance() {
    return UNDEFINED_DISTANCE;
  }

  /**
   * As pattern is required a String defining a Float.
   */
  @Override
  public FloatDistance parseString(String val) throws IllegalArgumentException {
    if (val.equals(INFINITY_PATTERN)) {
      return infiniteDistance();
    }

    if (DoubleDistance.DOUBLE_PATTERN.matcher(val).matches()) {
      return new FloatDistance(Float.parseFloat(val));
    } else {
      throw new IllegalArgumentException("Given pattern \"" + val + "\" does not match required pattern \"" + requiredInputPattern() + "\"");
    }
  }

  @Override
  public boolean isInfiniteDistance() {
    return Float.isInfinite(value);
  }

  @Override
  public boolean isNullDistance() {
    return (value <= 0.0);
  }

  @Override
  public boolean isUndefinedDistance() {
    return Float.isNaN(value);
  }

  @Override
  public Pattern getPattern() {
    return DOUBLE_PATTERN;
  }

  @Override
  public String toString() {
    return FormatUtil.NF.format(value);
  }

  @Override
  public int hashCode() {
    return Float.floatToIntBits(value);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    FloatDistance other = (FloatDistance) obj;
    if (Float.floatToIntBits(value) != Float.floatToIntBits(other.value)) {
      return false;
    }
    return true;
  }
}
