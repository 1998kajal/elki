package de.lmu.ifi.dbs.elki.distance.distancefunction.correlation;

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

import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.database.ids.DBIDRef;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.distance.distancefunction.AbstractIndexBasedDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancefunction.FilteredLocalPCABasedDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.SubspaceDistance;
import de.lmu.ifi.dbs.elki.index.IndexFactory;
import de.lmu.ifi.dbs.elki.index.preprocessed.LocalProjectionIndex;
import de.lmu.ifi.dbs.elki.index.preprocessed.localpca.FilteredLocalPCAIndex;
import de.lmu.ifi.dbs.elki.index.preprocessed.localpca.KNNQueryFilteredPCAIndex;
import de.lmu.ifi.dbs.elki.math.MathUtil;
import de.lmu.ifi.dbs.elki.math.linearalgebra.Matrix;
import de.lmu.ifi.dbs.elki.math.linearalgebra.Vector;
import de.lmu.ifi.dbs.elki.math.linearalgebra.pca.PCAFilteredResult;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;

/**
 * Provides a distance function to determine a kind of correlation distance
 * between two points, which is a pair consisting of the distance between the
 * two subspaces spanned by the strong eigenvectors of the two points and the
 * affine distance between the two subspaces.
 * 
 * @author Elke Achtert
 * 
 * @apiviz.has Instance
 */
public class LocalSubspaceDistanceFunction extends AbstractIndexBasedDistanceFunction<NumberVector<?>, FilteredLocalPCAIndex<NumberVector<?>>, SubspaceDistance> implements FilteredLocalPCABasedDistanceFunction<NumberVector<?>, FilteredLocalPCAIndex<NumberVector<?>>, SubspaceDistance> {
  /**
   * Constructor
   * 
   * @param indexFactory Index factory
   */
  public LocalSubspaceDistanceFunction(IndexFactory<NumberVector<?>, FilteredLocalPCAIndex<NumberVector<?>>> indexFactory) {
    super(indexFactory);
  }

  @Override
  public SubspaceDistance getDistanceFactory() {
    return SubspaceDistance.FACTORY;
  }

  @Override
  public <T extends NumberVector<?>> Instance<T> instantiate(Relation<T> database) {
    // We can't really avoid these warnings, due to a limitation in Java
    // Generics (AFAICT)
    @SuppressWarnings("unchecked")
    FilteredLocalPCAIndex<T> indexinst = (FilteredLocalPCAIndex<T>) indexFactory.instantiate((Relation<NumberVector<?>>) database);
    return new Instance<>(database, indexinst, this);
  }

  /**
   * The actual instance bound to a particular database.
   * 
   * @author Erich Schubert
   */
  public static class Instance<V extends NumberVector<?>> extends AbstractIndexBasedDistanceFunction.Instance<V, FilteredLocalPCAIndex<V>, SubspaceDistance, LocalSubspaceDistanceFunction> implements FilteredLocalPCABasedDistanceFunction.Instance<V, FilteredLocalPCAIndex<V>, SubspaceDistance> {
    /**
     * @param database Database
     * @param index Index
     */
    public Instance(Relation<V> database, FilteredLocalPCAIndex<V> index, LocalSubspaceDistanceFunction distanceFunction) {
      super(database, index, distanceFunction);
    }

    /**
     * Note, that the pca of o1 must have equal ore more strong eigenvectors
     * than the pca of o2.
     * 
     */
    @Override
    public SubspaceDistance distance(DBIDRef id1, DBIDRef id2) {
      PCAFilteredResult pca1 = index.getLocalProjection(id1);
      PCAFilteredResult pca2 = index.getLocalProjection(id2);
      V o1 = relation.get(id1);
      V o2 = relation.get(id2);
      return distance(o1, o2, pca1, pca2);
    }

    /**
     * Computes the distance between two given DatabaseObjects according to this
     * distance function. Note, that the first pca must have an equal number of
     * strong eigenvectors than the second pca.
     * 
     * @param o1 first DatabaseObject
     * @param o2 second DatabaseObject
     * @param pca1 first PCA
     * @param pca2 second PCA
     * @return the distance between two given DatabaseObjects according to this
     *         distance function
     */
    public static <V extends NumberVector<?>> SubspaceDistance distance(V o1, V o2, PCAFilteredResult pca1, PCAFilteredResult pca2) {
      if(pca1.getCorrelationDimension() != pca2.getCorrelationDimension()) {
        throw new IllegalStateException("pca1.getCorrelationDimension() != pca2.getCorrelationDimension()");
      }

      Matrix strong_ev1 = pca1.getStrongEigenvectors();
      Matrix weak_ev2 = pca2.getWeakEigenvectors();
      Matrix m1 = weak_ev2.getColumnDimensionality() == 0 ? strong_ev1.transpose() : strong_ev1.transposeTimes(weak_ev2);
      double d1 = m1.norm2();

      Vector o1_minus_o2 = o1.getColumnVector().minusEquals(o2.getColumnVector());
      double affineDistance = Math.max(MathUtil.mahalanobisDistance(pca1.similarityMatrix(), o1_minus_o2), MathUtil.mahalanobisDistance(pca2.similarityMatrix(), o1_minus_o2));

      return new SubspaceDistance(d1, affineDistance);
    }
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static class Parameterizer extends AbstractIndexBasedDistanceFunction.Parameterizer<LocalProjectionIndex.Factory<NumberVector<?>, FilteredLocalPCAIndex<NumberVector<?>>>> {
    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      configIndexFactory(config, LocalProjectionIndex.Factory.class, KNNQueryFilteredPCAIndex.Factory.class);
    }

    @Override
    protected LocalSubspaceDistanceFunction makeInstance() {
      return new LocalSubspaceDistanceFunction(factory);
    }
  }
}