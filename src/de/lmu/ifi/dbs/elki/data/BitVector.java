package de.lmu.ifi.dbs.elki.data;

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
import java.nio.ByteBuffer;
import java.util.BitSet;

import de.lmu.ifi.dbs.elki.math.linearalgebra.Vector;
import de.lmu.ifi.dbs.elki.persistent.ByteArrayUtil;
import de.lmu.ifi.dbs.elki.persistent.ByteBufferSerializer;
import de.lmu.ifi.dbs.elki.utilities.datastructures.arraylike.ArrayAdapter;
import de.lmu.ifi.dbs.elki.utilities.datastructures.arraylike.NumberArrayAdapter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;

/**
 * Provides a BitVector wrapping a BitSet.
 * 
 * @author Arthur Zimek
 */
public class BitVector extends AbstractNumberVector<Bit> {
  /**
   * Static instance.
   */
  public static final BitVector.Factory FACTORY = new BitVector.Factory();

  /**
   * Serializer for up to 2^15-1 dimensions.
   */
  public static final ByteBufferSerializer<BitVector> SHORT_SERIALIZER = new ShortSerializer();

  /**
   * Storing the bits.
   */
  private final BitSet bits;

  /**
   * Dimensionality of this bit vector.
   */
  private final int dimensionality;

  /**
   * Provides a new BitVector corresponding to the specified bits and of the
   * specified dimensionality.
   * 
   * @param bits the bits to be set in this BitVector
   * @param dimensionality the dimensionality of this BitVector
   * @throws IllegalArgumentException if the specified dimensionality is to
   *         small to match the given BitSet
   */
  public BitVector(BitSet bits, int dimensionality) throws IllegalArgumentException {
    if (dimensionality < bits.length()) {
      throw new IllegalArgumentException("Specified dimensionality " + dimensionality + " is to low for specified BitSet of length " + bits.length());
    }
    this.bits = bits;
    this.dimensionality = dimensionality;
  }

  /**
   * Provides a new BitVector corresponding to the bits in the given array.
   * 
   * @param bits an array of bits specifying the bits in this bit vector
   */
  public BitVector(Bit[] bits) {
    this.bits = new BitSet(bits.length);
    for (int i = 0; i < bits.length; i++) {
      this.bits.set(i, bits[i].bitValue());
    }
    this.dimensionality = bits.length;
  }

  @Override
  public int getDimensionality() {
    return dimensionality;
  }

  @Override
  @Deprecated
  public Bit getValue(int dimension) {
    if (dimension < 1 || dimension > dimensionality) {
      throw new IllegalArgumentException("illegal dimension: " + dimension);
    }
    return new Bit(bits.get(dimension - 1));
  }

  @Override
  public double doubleValue(int dimension) {
    if (dimension < 0 || dimension >= dimensionality) {
      throw new IllegalArgumentException("illegal dimension: " + dimension);
    }
    return bits.get(dimension) ? 1.0 : 0.0;
  }

  @Override
  public long longValue(int dimension) {
    if (dimension < 0 || dimension >= dimensionality) {
      throw new IllegalArgumentException("illegal dimension: " + dimension);
    }
    return bits.get(dimension) ? 1 : 0;
  }

  /**
   * Returns a Vector representing in one column and
   * <code>getDimensionality()</code> rows the values of this BitVector as
   * double values.
   * 
   * @return a Matrix representing in one column and
   *         <code>getDimensionality()</code> rows the values of this BitVector
   *         as double values
   * 
   * @see de.lmu.ifi.dbs.elki.data.NumberVector#getColumnVector()
   */
  @Override
  public Vector getColumnVector() {
    double[] values = new double[dimensionality];
    for (int i = 0; i < dimensionality; i++) {
      values[i] = bits.get(i) ? 1 : 0;
    }
    return new Vector(values);
  }

  /**
   * Returns whether this BitVector contains all bits that are set to true in
   * the specified BitSet.
   * 
   * @param bitset the bits to inspect in this BitVector
   * @return true if this BitVector contains all bits that are set to true in
   *         the specified BitSet, false otherwise
   */
  public boolean contains(BitSet bitset) {
    boolean contains = true;
    for (int i = bitset.nextSetBit(0); i >= 0 && contains; i = bitset.nextSetBit(i + 1)) {
      // noinspection ConstantConditions
      contains &= bits.get(i);
    }
    return contains;
  }

  /**
   * Returns a copy of the bits currently set in this BitVector.
   * 
   * @return a copy of the bits currently set in this BitVector
   */
  public BitSet getBits() {
    return (BitSet) bits.clone();
  }

  /**
   * Returns a String representation of this BitVector. The representation is
   * suitable to be parsed by
   * {@link de.lmu.ifi.dbs.elki.datasource.parser.BitVectorLabelParser
   * BitVectorLabelParser}.
   * 
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    Bit[] bitArray = new Bit[dimensionality];
    for (int i = 0; i < dimensionality; i++) {
      bitArray[i] = bits.get(i) ? Bit.TRUE : Bit.FALSE;
    }
    StringBuilder representation = new StringBuilder();
    for (Bit bit : bitArray) {
      if (representation.length() > 0) {
        representation.append(ATTRIBUTE_SEPARATOR);
      }
      representation.append(bit.toString());
    }
    return representation.toString();
  }

  /**
   * Indicates whether some other object is "equal to" this BitVector. This
   * BitVector is equal to the given object, if the object is a BitVector of
   * same dimensionality and with identical bits set.
   * 
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object obj) {
    if (obj instanceof BitVector) {
      BitVector bv = (BitVector) obj;
      return this.getDimensionality() == bv.getDimensionality() && this.bits.equals(bv.bits);

    } else {
      return false;
    }
  }

  /**
   * Factory for bit vectors.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.has BitVector
   */
  public static class Factory extends AbstractNumberVector.Factory<BitVector, Bit> {
    @Override
    public <A> BitVector newFeatureVector(A array, ArrayAdapter<Bit, A> adapter) {
      int dim = adapter.size(array);
      BitSet bits = new BitSet(dim);
      for (int i = 0; i < dim; i++) {
        bits.set(i, adapter.get(array, i).bitValue());
        i++;
      }
      return new BitVector(bits, dim);
    }

    @Override
    public <A> BitVector newNumberVector(A array, NumberArrayAdapter<?, ? super A> adapter) {
      int dim = adapter.size(array);
      BitSet bits = new BitSet(dim);
      for (int i = 0; i < dim; i++) {
        if (adapter.getDouble(array, i) >= 0.5) {
          bits.set(i);
        }
      }
      return new BitVector(bits, dim);
    }

    @Override
    public ByteBufferSerializer<BitVector> getDefaultSerializer() {
      return SHORT_SERIALIZER;
    }
    
    @Override
    public Class<? super BitVector> getRestrictionClass() {
      return BitVector.class;
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
      protected BitVector.Factory makeInstance() {
        return FACTORY;
      }
    }
  }

  /**
   * Serialization class for dense integer vectors with up to
   * {@link Short#MAX_VALUE} dimensions, by using a short for storing the
   * dimensionality.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.uses BitVector - - «serializes»
   */
  public static class ShortSerializer implements ByteBufferSerializer<BitVector> {
    @Override
    public BitVector fromByteBuffer(ByteBuffer buffer) throws IOException {
      short dimensionality = buffer.getShort();
      final int len = ByteArrayUtil.SIZE_SHORT + (dimensionality + 7) / 8;
      if (buffer.remaining() < len) {
        throw new IOException("Not enough data for a bit vector!");
      }
      // read values
      BitSet values = new BitSet(dimensionality);
      byte b = 0;
      for (int i = 0; i < dimensionality; i++) {
        // read the next byte when needed.
        if ((i & 7) == 0) {
          b = buffer.get();
        }
        final byte bit = (byte) (1 << (i & 7));
        if ((b & bit) != 0) {
          values.set(i + 1);
        }
      }
      return new BitVector(values, dimensionality);
    }

    @Override
    public void toByteBuffer(ByteBuffer buffer, BitVector vec) throws IOException {
      final int len = getByteSize(vec);
      assert (vec.getDimensionality() <= Short.MAX_VALUE);
      final short dim = (short) vec.getDimensionality();
      if (buffer.remaining() < len) {
        throw new IOException("Not enough space for the bit vector!");
      }
      // write size
      buffer.putShort(dim);
      // write values
      // Next byte to write:
      byte b = 0;
      for (int i = 0; i < dim; i++) {
        final byte mask = (byte) (1 << (i & 7));
        if (vec.bits.get(i)) {
          b |= mask;
        } else {
          b &= ~mask;
        }
        // Write when appropriate
        if ((i & 7) == 7 || i == dim - 1) {
          buffer.put(b);
          b = 0;
        }
      }
    }

    @Override
    public int getByteSize(BitVector vec) {
      return ByteArrayUtil.SIZE_SHORT + (vec.getDimensionality() + 7) / 8;
    }
  }
}
