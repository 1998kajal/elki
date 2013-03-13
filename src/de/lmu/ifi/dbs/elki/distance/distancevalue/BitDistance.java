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

import de.lmu.ifi.dbs.elki.data.Bit;
import de.lmu.ifi.dbs.elki.utilities.exceptions.ExceptionMessages;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.regex.Pattern;

/**
 * Provides a Distance for a bit-valued distance.
 * 
 * @author Arthur Zimek
 */
public class BitDistance extends NumberDistance<BitDistance, Bit> {
  /**
   * The static factory instance
   */
  public static final BitDistance FACTORY = new BitDistance();

  /**
   * The distance value
   */
  private boolean value;

  /**
   * Generated serial version UID
   */
  private static final long serialVersionUID = 6514853467081717551L;

  /**
   * Empty constructor for serialization purposes.
   */
  public BitDistance() {
    super();
  }

  /**
   * Constructs a new BitDistance object that represents the bit argument.
   * 
   * @param bit the value to be represented by the BitDistance.
   */
  public BitDistance(boolean bit) {
    super();
    this.value = bit;
  }

  /**
   * Constructs a new BitDistance object that represents the bit argument.
   * 
   * @param bit the value to be represented by the BitDistance.
   */
  public BitDistance(Bit bit) {
    super();
    this.value = bit.bitValue();
  }

  @Override
  public BitDistance fromDouble(double val) {
    return new BitDistance(val > 0);
  }

  /**
   * Returns the value of this BitDistance as a boolean.
   * 
   * @return the value as a boolean
   */
  public boolean bitValue() {
    return this.value;
  }

  /**
   * Writes the bit value of this BitDistance to the specified stream.
   */
  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
    out.writeBoolean(value);
  }

  /**
   * Reads the bit value of this BitDistance from the specified stream.
   */
  @Override
  public void readExternal(ObjectInput in) throws IOException {
    value = in.readBoolean();
  }

  /**
   * Returns the number of Bytes this distance uses if it is written to an
   * external file.
   * 
   * @return 1 (1 Byte for a boolean value)
   */
  @Override
  public int externalizableSize() {
    return 1;
  }

  @Override
  public double doubleValue() {
    return value ? 1.0 : 0.0;
  }

  @Override
  public long longValue() {
    return value ? 1 : 0;
  }

  @Override
  public int intValue() {
    return value ? 1 : 0;
  }

  @Override
  public int compareTo(BitDistance other) {
    return this.intValue() - other.intValue();
  }

  @Override
  public BitDistance parseString(String val) throws IllegalArgumentException {
    if (testInputPattern(val)) {
      return new BitDistance(Bit.valueOf(val).bitValue());
    } else {
      throw new IllegalArgumentException("Given pattern \"" + val + "\" does not match required pattern \"" + requiredInputPattern() + "\"");
    }
  }

  @Override
  public BitDistance infiniteDistance() {
    return new BitDistance(true);
  }

  @Override
  public BitDistance nullDistance() {
    return new BitDistance(false);
  }

  @Override
  public BitDistance undefinedDistance() {
    throw new UnsupportedOperationException(ExceptionMessages.UNSUPPORTED_UNDEFINED_DISTANCE);
  }

  @Override
  public Pattern getPattern() {
    return Bit.BIT_PATTERN;
  }

  @Override
  public boolean isInfiniteDistance() {
    return false;
  }

  @Override
  public boolean isNullDistance() {
    return !value;
  }

  @Override
  public boolean isUndefinedDistance() {
    return false;
  }

  @Override
  public String toString() {
    return Boolean.toString(value);
  }

  @Override
  public int hashCode() {
    return (value ? 1231 : 1237);
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
    BitDistance other = (BitDistance) obj;
    return (value == other.value);
  }
}
