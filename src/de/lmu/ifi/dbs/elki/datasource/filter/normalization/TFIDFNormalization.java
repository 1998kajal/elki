package de.lmu.ifi.dbs.elki.datasource.filter.normalization;

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

import gnu.trove.map.hash.TIntDoubleHashMap;

import java.util.BitSet;

import de.lmu.ifi.dbs.elki.data.SparseNumberVector;

/**
 * Perform full TF-IDF Normalization as commonly used in text mining.
 * 
 * Each record is first normalized using "term frequencies" to sum up to 1. Then
 * it is globally normalized using the Inverse Document Frequency, so rare terms
 * are weighted stronger than common terms.
 * 
 * Restore will only undo the IDF part of the normalization!
 * 
 * @author Erich Schubert
 * 
 * @param <V> Vector type
 */
public class TFIDFNormalization<V extends SparseNumberVector<?>> extends InverseDocumentFrequencyNormalization<V> {
  /**
   * Constructor.
   */
  public TFIDFNormalization() {
    super();
  }

  @Override
  protected V filterSingleObject(V featureVector) {
    BitSet b = featureVector.getNotNullMask();
    double sum = 0.0;
    for(int i = b.nextSetBit(0); i >= 0; i = b.nextSetBit(i + 1)) {
      sum += featureVector.doubleValue(i);
    }
    if(sum <= 0) {
      sum = 1.0;
    }
    TIntDoubleHashMap vals = new TIntDoubleHashMap();
    for(int i = b.nextSetBit(0); i >= 0; i = b.nextSetBit(i + 1)) {
      vals.put(i, (float) (featureVector.doubleValue(i) / sum * idf.get(i)));
    }
    return ((SparseNumberVector.Factory<V, ?>) factory).newNumberVector(vals, featureVector.getDimensionality());
  }
}