package de.lmu.ifi.dbs.elki.datasource.parser;

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

import gnu.trove.iterator.TIntDoubleIterator;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.util.BitSet;
import java.util.List;
import java.util.regex.Pattern;

import de.lmu.ifi.dbs.elki.data.LabelList;
import de.lmu.ifi.dbs.elki.data.SparseFloatVector;
import de.lmu.ifi.dbs.elki.data.SparseNumberVector;
import de.lmu.ifi.dbs.elki.data.type.SimpleTypeInformation;
import de.lmu.ifi.dbs.elki.data.type.VectorFieldTypeInformation;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.utilities.documentation.Description;
import de.lmu.ifi.dbs.elki.utilities.documentation.Title;
import de.lmu.ifi.dbs.elki.utilities.exceptions.AbortException;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.Flag;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.ObjectParameter;

/**
 * A parser to load term frequency data, which essentially are sparse vectors
 * with text keys.
 * 
 * @author Erich Schubert
 * 
 * @apiviz.has SparseFloatVector
 */
@Title("Term frequency parser")
@Description("Parse a file containing term frequencies. The expected format is 'label term1 <freq> term2 <freq> ...'. Terms must not contain the separator character!")
public class TermFrequencyParser<V extends SparseNumberVector<?>> extends NumberVectorLabelParser<V> {
  /**
   * Class logger.
   */
  private static final Logging LOG = Logging.getLogger(TermFrequencyParser.class);

  /**
   * Maximum dimension used.
   */
  int maxdim;

  /**
   * Map.
   */
  TObjectIntMap<String> keymap;

  /**
   * Normalize.
   */
  boolean normalize;

  /**
   * Same as {@link #factory}, but subtype.
   */
  private SparseNumberVector.Factory<V, ?> sparsefactory;

  /**
   * Constructor.
   * 
   * @param normalize Normalize
   * @param colSep
   * @param quoteChar
   * @param labelIndices
   */
  public TermFrequencyParser(boolean normalize, Pattern colSep, char quoteChar, BitSet labelIndices, SparseNumberVector.Factory<V, ?> factory) {
    super(colSep, quoteChar, labelIndices, factory);
    this.normalize = normalize;
    this.maxdim = 0;
    this.keymap = new TObjectIntHashMap<>();
    this.sparsefactory = factory;
  }

  @Override
  protected void parseLineInternal(String line) {
    List<String> entries = tokenize(line);

    double len = 0;
    TIntDoubleHashMap values = new TIntDoubleHashMap();
    LabelList labels = null;

    String curterm = null;
    for (int i = 0; i < entries.size(); i++) {
      if (curterm == null) {
        curterm = entries.get(i);
      } else {
        try {
          double attribute = Double.parseDouble(entries.get(i));
          Integer curdim = keymap.get(curterm);
          if (curdim == null) {
            curdim = Integer.valueOf(maxdim + 1);
            keymap.put(curterm, curdim);
            maxdim += 1;
          }
          values.put(curdim, attribute);
          len += attribute;
          curterm = null;
        } catch (NumberFormatException e) {
          if (curterm != null) {
            if (labels == null) {
              labels = new LabelList(1);
            }
            labels.add(curterm);
          }
          curterm = entries.get(i);
        }
      }
    }
    if (curterm != null) {
      if (labels == null) {
        labels = new LabelList(1);
      }
      labels.add(curterm);
    }
    if (normalize) {
      if (Math.abs(len - 1.0) > 1E-10 && len > 1E-10) {
        for (TIntDoubleIterator iter = values.iterator(); iter.hasNext();) {
          iter.advance();
          iter.setValue(iter.value() / len);
        }
      }
    }

    curvec = sparsefactory.newNumberVector(values, maxdim);
    curlbl = labels;
  }

  @Override
  protected SimpleTypeInformation<V> getTypeInformation(int dimensionality) {
    if (dimensionality > 0) {
      return new VectorFieldTypeInformation<>(factory, dimensionality);
    }
    if (dimensionality == DIMENSIONALITY_VARIABLE) {
      return new SimpleTypeInformation<>(factory.getRestrictionClass(), factory.getDefaultSerializer());
    }
    throw new AbortException("No vectors were read from the input file - cannot determine vector data type.");
  }

  @Override
  protected Logging getLogger() {
    return LOG;
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static class Parameterizer<V extends SparseNumberVector<?>> extends NumberVectorLabelParser.Parameterizer<V> {
    /**
     * Option ID for normalization.
     */
    public static final OptionID NORMALIZE_FLAG = new OptionID("tf.normalize", "Normalize vectors to manhattan length 1 (convert term counts to term frequencies)");

    /**
     * Normalization flag.
     */
    boolean normalize = false;

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      Flag normF = new Flag(NORMALIZE_FLAG);
      if (config.grab(normF)) {
        normalize = normF.getValue().booleanValue();
      }
    }

    @Override
    protected void getFactory(Parameterization config) {
      ObjectParameter<SparseNumberVector.Factory<V, ?>> factoryP = new ObjectParameter<>(VECTOR_TYPE_ID, SparseNumberVector.Factory.class, SparseFloatVector.Factory.class);
      if (config.grab(factoryP)) {
        factory = factoryP.instantiateClass(config);
      }
    }

    @Override
    protected TermFrequencyParser<V> makeInstance() {
      return new TermFrequencyParser<>(normalize, colSep, quoteChar, labelIndices, (SparseNumberVector.Factory<V, ?>) factory);
    }
  }
}
