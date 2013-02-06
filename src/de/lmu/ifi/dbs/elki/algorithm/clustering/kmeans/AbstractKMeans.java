package de.lmu.ifi.dbs.elki.algorithm.clustering.kmeans;

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

import java.util.ArrayList;
import java.util.List;

import de.lmu.ifi.dbs.elki.algorithm.AbstractPrimitiveDistanceBasedAlgorithm;
import de.lmu.ifi.dbs.elki.algorithm.clustering.ClusteringAlgorithm;
import de.lmu.ifi.dbs.elki.data.Clustering;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.VectorUtil.SortDBIDsBySingleDimension;
import de.lmu.ifi.dbs.elki.data.model.MeanModel;
import de.lmu.ifi.dbs.elki.data.type.CombinedTypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.ids.ArrayModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.ModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.distance.distancefunction.PrimitiveDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancefunction.PrimitiveDoubleDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.Distance;
import de.lmu.ifi.dbs.elki.math.linearalgebra.Vector;
import de.lmu.ifi.dbs.elki.utilities.datastructures.QuickSelect;

/**
 * Abstract base class for k-means implementations.
 * 
 * @author Erich Schubert
 * 
 * @apiviz.has MeanModel
 * @apiviz.composedOf KMeansInitialization
 * 
 * @param <V> Vector type
 * @param <D> Distance type
 * @param <M> Cluster model type
 */
public abstract class AbstractKMeans<V extends NumberVector<?>, D extends Distance<D>, M extends MeanModel<V>> extends AbstractPrimitiveDistanceBasedAlgorithm<NumberVector<?>, D, Clustering<M>> implements KMeans, ClusteringAlgorithm<Clustering<M>> {
  /**
   * Holds the value of {@link #K_ID}.
   */
  protected int k;

  /**
   * Holds the value of {@link #MAXITER_ID}.
   */
  protected int maxiter;

  /**
   * Method to choose initial means.
   */
  protected KMeansInitialization<V> initializer;

  /**
   * Constructor.
   * 
   * @param distanceFunction distance function
   * @param k k parameter
   * @param maxiter Maxiter parameter
   * @param initializer Function to generate the initial means
   */
  public AbstractKMeans(PrimitiveDistanceFunction<? super NumberVector<?>, D> distanceFunction, int k, int maxiter, KMeansInitialization<V> initializer) {
    super(distanceFunction);
    this.k = k;
    this.maxiter = maxiter;
    this.initializer = initializer;
  }

  /**
   * Returns a list of clusters. The k<sup>th</sup> cluster contains the ids of
   * those FeatureVectors, that are nearest to the k<sup>th</sup> mean.
   * 
   * @param relation the database to cluster
   * @param means a list of k means
   * @param clusters cluster assignment
   * @return true when the object was reassigned
   */
  protected boolean assignToNearestCluster(Relation<V> relation, List<? extends NumberVector<?>> means, List<? extends ModifiableDBIDs> clusters) {
    boolean changed = false;

    if(getDistanceFunction() instanceof PrimitiveDoubleDistanceFunction) {
      @SuppressWarnings("unchecked")
      final PrimitiveDoubleDistanceFunction<? super NumberVector<?>> df = (PrimitiveDoubleDistanceFunction<? super NumberVector<?>>) getDistanceFunction();
      for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
        double mindist = Double.POSITIVE_INFINITY;
        V fv = relation.get(iditer);
        int minIndex = 0;
        for(int i = 0; i < k; i++) {
          double dist = df.doubleDistance(fv, means.get(i));
          if(dist < mindist) {
            minIndex = i;
            mindist = dist;
          }
        }
        if(clusters.get(minIndex).add(iditer)) {
          changed = true;
          // Remove from previous cluster
          // TODO: keep a list of cluster assignments to save this search?
          for(int i = 0; i < k; i++) {
            if(i != minIndex) {
              if(clusters.get(i).remove(iditer)) {
                break;
              }
            }
          }
        }
      }
    }
    else {
      final PrimitiveDistanceFunction<? super NumberVector<?>, D> df = getDistanceFunction();
      for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
        D mindist = df.getDistanceFactory().infiniteDistance();
        V fv = relation.get(iditer);
        int minIndex = 0;
        for(int i = 0; i < k; i++) {
          D dist = df.distance(fv, means.get(i));
          if(dist.compareTo(mindist) < 0) {
            minIndex = i;
            mindist = dist;
          }
        }
        if(clusters.get(minIndex).add(iditer)) {
          changed = true;
          // Remove from previous cluster
          // TODO: keep a list of cluster assignments to save this search?
          for(int i = 0; i < k; i++) {
            if(i != minIndex) {
              if(clusters.get(i).remove(iditer)) {
                break;
              }
            }
          }
        }
      }
    }
    return changed;
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return TypeUtil.array(new CombinedTypeInformation(TypeUtil.NUMBER_VECTOR_FIELD, getDistanceFunction().getInputTypeRestriction()));
  }

  /**
   * Returns the mean vectors of the given clusters in the given database.
   * 
   * @param clusters the clusters to compute the means
   * @param means the recent means
   * @param database the database containing the vectors
   * @return the mean vectors of the given clusters in the given database
   */
  protected List<Vector> means(List<? extends ModifiableDBIDs> clusters, List<? extends NumberVector<?>> means, Relation<V> database) {
    List<Vector> newMeans = new ArrayList<>(k);
    for(int i = 0; i < k; i++) {
      ModifiableDBIDs list = clusters.get(i);
      Vector mean = null;
      if(list.size() > 0) {
        double s = 1.0 / list.size();
        DBIDIter iter = list.iter();
        assert (iter.valid());
        mean = database.get(iter).getColumnVector().timesEquals(s);
        iter.advance();
        for(; iter.valid(); iter.advance()) {
          mean.plusTimesEquals(database.get(iter).getColumnVector(), s);
        }
      }
      else {
        mean = means.get(i).getColumnVector();
      }
      newMeans.add(mean);
    }
    return newMeans;
  }

  /**
   * Returns the median vectors of the given clusters in the given database.
   * 
   * @param clusters the clusters to compute the means
   * @param medians the recent medians
   * @param database the database containing the vectors
   * @return the mean vectors of the given clusters in the given database
   */
  protected List<NumberVector<?>> medians(List<? extends ModifiableDBIDs> clusters, List<? extends NumberVector<?>> medians, Relation<V> database) {
    final int dim = medians.get(0).getDimensionality();
    final SortDBIDsBySingleDimension sorter = new SortDBIDsBySingleDimension(database);
    List<NumberVector<?>> newMedians = new ArrayList<>(k);
    for(int i = 0; i < k; i++) {
      ArrayModifiableDBIDs list = DBIDUtil.newArray(clusters.get(i));
      if(list.size() > 0) {
        Vector mean = new Vector(dim);
        for(int d = 0; d < dim; d++) {
          sorter.setDimension(d);
          DBID id = QuickSelect.median(list, sorter);
          mean.set(d, database.get(id).doubleValue(d));
        }
        newMedians.add(mean);
      }
      else {
        newMedians.add((NumberVector<?>) medians.get(i));
      }
    }
    return newMedians;
  }

  /**
   * Compute an incremental update for the mean.
   * 
   * @param mean Mean to update
   * @param vec Object vector
   * @param newsize (New) size of cluster
   * @param op Cluster size change / Weight change
   */
  protected void incrementalUpdateMean(Vector mean, V vec, int newsize, double op) {
    if(newsize == 0) {
      return; // Keep old mean
    }
    Vector delta = vec.getColumnVector();
    // Compute difference from mean
    delta.minusEquals(mean);
    delta.timesEquals(op / newsize);
    mean.plusEquals(delta);
  }

  /**
   * Perform a MacQueen style iteration.
   * 
   * @param relation Relation
   * @param means Means
   * @param clusters Clusters
   * @return true when the means have changed
   */
  protected boolean macQueenIterate(Relation<V> relation, List<Vector> means, List<ModifiableDBIDs> clusters) {
    boolean changed = false;

    if(getDistanceFunction() instanceof PrimitiveDoubleDistanceFunction) {
      // Raw distance function
      @SuppressWarnings("unchecked")
      final PrimitiveDoubleDistanceFunction<? super NumberVector<?>> df = (PrimitiveDoubleDistanceFunction<? super NumberVector<?>>) getDistanceFunction();

      // Incremental update
      for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
        double mindist = Double.POSITIVE_INFINITY;
        V fv = relation.get(iditer);
        int minIndex = 0;
        for(int i = 0; i < k; i++) {
          double dist = df.doubleDistance(fv, means.get(i));
          if(dist < mindist) {
            minIndex = i;
            mindist = dist;
          }
        }
        // Update the cluster mean incrementally:
        for(int i = 0; i < k; i++) {
          ModifiableDBIDs ci = clusters.get(i);
          if(i == minIndex) {
            if(ci.add(iditer)) {
              incrementalUpdateMean(means.get(i), fv, ci.size(), +1);
              changed = true;
            }
          }
          else if(ci.remove(iditer)) {
            incrementalUpdateMean(means.get(i), fv, ci.size() + 1, -1);
            changed = true;
          }
        }
      }
    }
    else {
      // Raw distance function
      final PrimitiveDistanceFunction<? super NumberVector<?>, D> df = getDistanceFunction();

      // Incremental update
      for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
        D mindist = df.getDistanceFactory().infiniteDistance();
        V fv = relation.get(iditer);
        int minIndex = 0;
        for(int i = 0; i < k; i++) {
          D dist = df.distance(fv, means.get(i));
          if(dist.compareTo(mindist) < 0) {
            minIndex = i;
            mindist = dist;
          }
        }
        // Update the cluster mean incrementally:
        for(int i = 0; i < k; i++) {
          ModifiableDBIDs ci = clusters.get(i);
          if(i == minIndex) {
            if(ci.add(iditer)) {
              incrementalUpdateMean(means.get(i), fv, ci.size(), +1);
              changed = true;
            }
          }
          else if(ci.remove(iditer)) {
            incrementalUpdateMean(means.get(i), fv, ci.size() + 1, -1);
            changed = true;
          }
        }
      }
    }
    return changed;
  }
}
