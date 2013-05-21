package de.lmu.ifi.dbs.elki.algorithm.clustering.hierarchical;

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

import de.lmu.ifi.dbs.elki.algorithm.AbstractDistanceBasedAlgorithm;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreFactory;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreUtil;
import de.lmu.ifi.dbs.elki.database.datastore.WritableDBIDDataStore;
import de.lmu.ifi.dbs.elki.database.datastore.WritableDoubleDistanceDataStore;
import de.lmu.ifi.dbs.elki.database.datastore.WritableIntegerDataStore;
import de.lmu.ifi.dbs.elki.database.ids.ArrayDBIDs;
import de.lmu.ifi.dbs.elki.database.ids.DBIDArrayIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.query.distance.DistanceQuery;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancefunction.minkowski.SquaredEuclideanDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.DoubleDistance;
import de.lmu.ifi.dbs.elki.distance.distancevalue.NumberDistance;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.progress.FiniteProgress;
import de.lmu.ifi.dbs.elki.utilities.documentation.Reference;
import de.lmu.ifi.dbs.elki.utilities.exceptions.AbortException;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.ObjectParameter;

/**
 * This tutorial will step you through implementing a well known clustering
 * algorithm, agglomerative hierarchical clustering, in multiple steps.
 * 
 * This is the third step, where we add support for different linkage
 * strategies.
 * 
 * This is the naive O(n^3) algorithm. See {@link SLINK} for a much faster
 * algorithm (however, only for single-linkage).
 * 
 * Reference for the unified concept:
 * <p>
 * G. N. Lance and W. T. Williams<br />
 * A general theory of classificatory sorting strategies 1. Hierarchical systems
 * <br/>
 * The computer journal 9.4 (1967): 373-380.
 * </p>
 * 
 * See also:
 * <p>
 * A Review of Classification<br />
 * R. M. Cormack<br />
 * Journal of the Royal Statistical Society. Series A, Vol. 134, No. 3
 * </p>
 * 
 * @author Erich Schubert
 * 
 * @param <O> Object type
 */
@Reference(authors = "G. N. Lance and W. T. Williams", title = "A general theory of classificatory sorting strategies 1. Hierarchical systems", booktitle = "The computer journal 9.4", url = "http://dx.doi.org/ 10.1093/comjnl/9.4.373")
public class NaiveAgglomerativeHierarchicalClustering<O, D extends NumberDistance<D, ?>> extends AbstractDistanceBasedAlgorithm<O, D, PointerHierarchyRepresentationResult<DoubleDistance>> implements HierarchicalClusteringAlgorithm<DoubleDistance> {
  /**
   * Class logger
   */
  private static final Logging LOG = Logging.getLogger(NaiveAgglomerativeHierarchicalClustering.class);

  /**
   * Current linkage method in use.
   */
  LinkageMethod linkage = WardLinkageMethod.STATIC;

  /**
   * Constructor.
   * 
   * @param distanceFunction Distance function to use
   * @param linkage Linkage method
   */
  public NaiveAgglomerativeHierarchicalClustering(DistanceFunction<? super O, D> distanceFunction, LinkageMethod linkage) {
    super(distanceFunction);
    this.linkage = linkage;
  }

  /**
   * Run the algorithm
   * 
   * @param db Database
   * @param relation Relation
   * @return Clustering hierarchy
   */
  public PointerHierarchyRepresentationResult<DoubleDistance> run(Database db, Relation<O> relation) {
    DistanceQuery<O, D> dq = db.getDistanceQuery(relation, getDistanceFunction());
    ArrayDBIDs ids = DBIDUtil.ensureArray(relation.getDBIDs());
    final int size = ids.size();

    if (size > 0x10000) {
      throw new AbortException("This implementation does not scale to data sets larger than " + 0x10000 + " instances (~17 GB RAM), which results in an integer overflow.");
    }
    if (SingleLinkageMethod.class.isInstance(linkage)) {
      LOG.verbose("Notice: SLINK is a much faster algorithm for single-linkage clustering!");
    }

    // Compute the initial (lower triangular) distance matrix.
    double[] scratch = new double[triangleSize(size)];
    DBIDArrayIter ix = ids.iter(), iy = ids.iter(), ij = ids.iter();
    // Position counter - must agree with computeOffset!
    int pos = 0;
    boolean square = WardLinkageMethod.class.isInstance(linkage) && !(SquaredEuclideanDistanceFunction.class.isInstance(getDistanceFunction()));
    for (int x = 0; ix.valid(); x++, ix.advance()) {
      iy.seek(0);
      for (int y = 0; y < x; y++, iy.advance()) {
        scratch[pos] = dq.distance(ix, iy).doubleValue();
        // Ward uses variances -- i.e. squared values
        if (square) {
          scratch[pos] *= scratch[pos];
        }
        pos++;
      }
    }

    // Initialize space for result:
    WritableDBIDDataStore pi = DataStoreUtil.makeDBIDStorage(ids, DataStoreFactory.HINT_HOT | DataStoreFactory.HINT_STATIC);
    WritableDoubleDistanceDataStore lambda = (WritableDoubleDistanceDataStore) DataStoreUtil.makeStorage(ids, DataStoreFactory.HINT_HOT | DataStoreFactory.HINT_STATIC, DoubleDistance.class);
    WritableIntegerDataStore csize = DataStoreUtil.makeIntegerStorage(ids, DataStoreFactory.HINT_HOT | DataStoreFactory.HINT_TEMP);
    for (DBIDIter it = ids.iter(); it.valid(); it.advance()) {
      pi.put(it, it);
      lambda.put(it, Double.POSITIVE_INFINITY);
      csize.put(it, 1);
    }

    // Repeat until everything merged into 1 cluster
    FiniteProgress prog = LOG.isVerbose() ? new FiniteProgress("Agglomerative clustering", size - 1, LOG) : null;
    for (int i = 1; i < size; i++) {
      double min = Double.POSITIVE_INFINITY;
      int minx = -1, miny = -1;
      ix.seek(0);
      for (int x = 0; x < size; x++, ix.advance()) {
        if (lambda.doubleValue(ix) < Double.POSITIVE_INFINITY) {
          continue;
        }
        final int xbase = triangleSize(x);
        iy.seek(0);
        for (int y = 0; y < x; y++, iy.advance()) {
          if (lambda.doubleValue(iy) < Double.POSITIVE_INFINITY) {
            continue;
          }
          final int idx = xbase + y;
          if (scratch[idx] < min) {
            min = scratch[idx];
            minx = x;
            miny = y;
          }
        }
      }
      assert (minx >= 0 && miny >= 0);
      // Avoid allocating memory, by reusing existing iterators:
      ix.seek(minx);
      iy.seek(miny);
      // Perform merge in data structure: x -> y
      // Since y < x, prefer keeping y, dropping x.
      lambda.put(ix, min);
      pi.put(ix, iy);
      // Merge into cluster
      int sizex = csize.intValue(ix), sizey = csize.intValue(iy);
      csize.put(iy, sizex + sizey);

      // Update distance matrix. Note: miny < minx

      // Implementation note: most will not need sizej, and could save the
      // hashmap lookup.
      final int xbase = triangleSize(minx), ybase = triangleSize(miny);
      // Write to (y, j), with j < y
      ij.seek(0);
      for (int j = 0; j < miny; j++, ij.advance()) {
        if (lambda.doubleValue(ij) < Double.POSITIVE_INFINITY) {
          continue;
        }
        final int sizej = csize.intValue(ij);
        scratch[ybase + j] = linkage.combine(sizex, scratch[xbase + j], sizey, scratch[ybase + j], sizej, min);
      }
      ij.advance(); // Always skip y
      ij.seek(miny + 1);
      // Write to (j, y), with y < j < x
      for (int j = miny + 1; j < minx; j++, ij.advance()) {
        if (lambda.doubleValue(ij) < Double.POSITIVE_INFINITY) {
          continue;
        }
        final int jbase = triangleSize(j);
        final int sizej = csize.intValue(ij);
        scratch[jbase + miny] = linkage.combine(sizex, scratch[xbase + j], sizey, scratch[jbase + miny], sizej, min);
      }
      ij.advance(); // Skip x
      ij.seek(minx + 1);
      // Write to (j, y), with y < x < j
      for (int j = minx + 1; j < size; j++, ij.advance()) {
        if (lambda.doubleValue(ij) < Double.POSITIVE_INFINITY) {
          continue;
        }
        final int sizej = csize.intValue(ij);
        final int jbase = triangleSize(j);
        scratch[jbase + miny] = linkage.combine(sizex, scratch[jbase + minx], sizey, scratch[jbase + miny], sizej, min);
      }
      if (prog != null) {
        prog.incrementProcessed(LOG);
      }
    }
    if (prog != null) {
      prog.ensureCompleted(LOG);
    }

    PointerHierarchyRepresentationResult<DoubleDistance> result = new PointerHierarchyRepresentationResult<>(ids, pi, lambda);
    // TODO: also return an ordering, like slink, for visualization?

    return result;
  }

  /**
   * Compute the size of a complete x by x triangle (minus diagonal)
   * 
   * @param x Offset
   * @return Size of complete triangle
   */
  protected static int triangleSize(int x) {
    return (x * (x - 1)) >>> 1;
  }

  @Override
  public DoubleDistance getDistanceFactory() {
    return DoubleDistance.FACTORY;
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    // The input relation must match our distance function:
    return TypeUtil.array(getDistanceFunction().getInputTypeRestriction());
  }

  @Override
  protected Logging getLogger() {
    return LOG;
  }

  /**
   * Parameterization class
   * 
   * @author Erich Schubert
   * 
   * @param <O> Object type
   * @param <D> Distance type
   */
  public static class Parameterizer<O, D extends NumberDistance<D, ?>> extends AbstractDistanceBasedAlgorithm.Parameterizer<O, D> {
    /**
     * Option ID for linkage parameter.
     */
    public static final OptionID LINKAGE_ID = new OptionID("hierarchical.linkage", "Linkage method to use (e.g. Ward, Single-Link)");

    /**
     * Current linkage in use.
     */
    protected LinkageMethod linkage;

    @Override
    protected void makeOptions(Parameterization config) {
      // We don't call super, because we want a different default distance.
      ObjectParameter<DistanceFunction<O, D>> distanceFunctionP = makeParameterDistanceFunction(SquaredEuclideanDistanceFunction.class, DistanceFunction.class);
      if (config.grab(distanceFunctionP)) {
        distanceFunction = distanceFunctionP.instantiateClass(config);
      }

      ObjectParameter<LinkageMethod> linkageP = new ObjectParameter<>(LINKAGE_ID, LinkageMethod.class);
      linkageP.setDefaultValue(WardLinkageMethod.class);
      if (config.grab(linkageP)) {
        linkage = linkageP.instantiateClass(config);
      }
    }

    @Override
    protected NaiveAgglomerativeHierarchicalClustering<O, D> makeInstance() {
      return new NaiveAgglomerativeHierarchicalClustering<>(distanceFunction, linkage);
    }
  }
}
