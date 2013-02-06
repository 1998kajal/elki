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
import de.lmu.ifi.dbs.elki.data.Cluster;
import de.lmu.ifi.dbs.elki.data.Clustering;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.model.KMeansModel;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.ids.ModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.database.relation.RelationUtil;
import de.lmu.ifi.dbs.elki.distance.distancefunction.PrimitiveDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancefunction.minkowski.EuclideanDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancefunction.minkowski.SquaredEuclideanDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.Distance;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.math.linearalgebra.Vector;
import de.lmu.ifi.dbs.elki.utilities.documentation.Description;
import de.lmu.ifi.dbs.elki.utilities.documentation.Reference;
import de.lmu.ifi.dbs.elki.utilities.documentation.Title;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.GreaterConstraint;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.GreaterEqualConstraint;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.IntParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.ObjectParameter;

/**
 * Provides the k-means algorithm, using MacQueen style incremental updates.
 * 
 * <p>
 * Reference:<br />
 * J. MacQueen: Some Methods for Classification and Analysis of Multivariate
 * Observations. <br />
 * In 5th Berkeley Symp. Math. Statist. Prob., Vol. 1, 1967, pp 281-297.
 * </p>
 * 
 * @author Erich Schubert
 * @apiviz.has KMeansModel
 * 
 * @param <V> vector type to use
 * @param <D> distance function value type
 */
@Title("K-Means")
@Description("Finds a partitioning into k clusters.")
@Reference(authors = "J. MacQueen", title = "Some Methods for Classification and Analysis of Multivariate Observations", booktitle = "5th Berkeley Symp. Math. Statist. Prob., Vol. 1, 1967, pp 281-297", url = "http://projecteuclid.org/euclid.bsmsp/1200512992")
public class KMeansMacQueen<V extends NumberVector<?>, D extends Distance<D>> extends AbstractKMeans<V, D, KMeansModel<V>> {
  /**
   * The logger for this class.
   */
  private static final Logging LOG = Logging.getLogger(KMeansMacQueen.class);

  /**
   * Constructor.
   * 
   * @param distanceFunction distance function
   * @param k k parameter
   * @param maxiter Maxiter parameter
   * @param initializer Initialization method
   */
  public KMeansMacQueen(PrimitiveDistanceFunction<NumberVector<?>, D> distanceFunction, int k, int maxiter, KMeansInitialization<V> initializer) {
    super(distanceFunction, k, maxiter, initializer);
  }

  /**
   * Run k-means.
   * 
   * @param database Database
   * @param relation relation to use
   * @return Clustering result
   */
  public Clustering<KMeansModel<V>> run(Database database, Relation<V> relation) {
    if (relation.size() <= 0) {
      return new Clustering<>("k-Means Clustering", "kmeans-clustering");
    }
    // Choose initial means
    List<Vector> means = new ArrayList<>(k);
    for (NumberVector<?> nv : initializer.chooseInitialMeans(relation, k, getDistanceFunction())) {
      means.add(nv.getColumnVector());
    }
    // Initialize cluster and assign objects
    List<ModifiableDBIDs> clusters = new ArrayList<>();
    for (int i = 0; i < k; i++) {
      clusters.add(DBIDUtil.newHashSet(relation.size() / k));
    }
    assignToNearestCluster(relation, means, clusters);
    // Initial recomputation of the means.
    means = means(clusters, means, relation);

    // Refine result
    for (int iteration = 0; maxiter <= 0 || iteration < maxiter; iteration++) {
      if (LOG.isVerbose()) {
        LOG.verbose("K-Means iteration " + (iteration + 1));
      }
      boolean changed = macQueenIterate(relation, means, clusters);
      if (!changed) {
        break;
      }
    }
    final NumberVector.Factory<V, ?> factory = RelationUtil.getNumberVectorFactory(relation);
    Clustering<KMeansModel<V>> result = new Clustering<>("k-Means Clustering", "kmeans-clustering");
    for (int i = 0; i < clusters.size(); i++) {
      DBIDs ids = clusters.get(i);
      KMeansModel<V> model = new KMeansModel<>(factory.newNumberVector(means.get(i).getArrayRef()));
      result.addCluster(new Cluster<>(ids, model));
    }
    return result;
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
  public static class Parameterizer<V extends NumberVector<?>, D extends Distance<D>> extends AbstractPrimitiveDistanceBasedAlgorithm.Parameterizer<NumberVector<?>, D> {
    /**
     * k Parameter.
     */
    protected int k;

    /**
     * Maximum number of iterations.
     */
    protected int maxiter;

    /**
     * Initialization method.
     */
    protected KMeansInitialization<V> initializer;

    @Override
    protected void makeOptions(Parameterization config) {
      ObjectParameter<PrimitiveDistanceFunction<NumberVector<?>, D>> distanceFunctionP = makeParameterDistanceFunction(SquaredEuclideanDistanceFunction.class, PrimitiveDistanceFunction.class);
      if (config.grab(distanceFunctionP)) {
        distanceFunction = distanceFunctionP.instantiateClass(config);
        if (!(distanceFunction instanceof EuclideanDistanceFunction) && !(distanceFunction instanceof SquaredEuclideanDistanceFunction)) {
          LOG.warning("k-means optimizes the sum of squares - it should be used with squared euclidean distance and may stop converging otherwise!");
        }
      }

      IntParameter kP = new IntParameter(K_ID);
      kP.addConstraint(new GreaterConstraint(0));
      if (config.grab(kP)) {
        k = kP.getValue();
      }

      ObjectParameter<KMeansInitialization<V>> initialP = new ObjectParameter<>(INIT_ID, KMeansInitialization.class, RandomlyGeneratedInitialMeans.class);
      if (config.grab(initialP)) {
        initializer = initialP.instantiateClass(config);
      }

      IntParameter maxiterP = new IntParameter(MAXITER_ID, 0);
      maxiterP.addConstraint(new GreaterEqualConstraint(0));
      if (config.grab(maxiterP)) {
        maxiter = maxiterP.getValue();
      }
    }

    @Override
    protected KMeansMacQueen<V, D> makeInstance() {
      return new KMeansMacQueen<>(distanceFunction, k, maxiter, initializer);
    }
  }
}
