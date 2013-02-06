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
import de.lmu.ifi.dbs.elki.data.model.MeanModel;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.ModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.database.relation.RelationUtil;
import de.lmu.ifi.dbs.elki.distance.distancefunction.PrimitiveDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.Distance;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.utilities.documentation.Reference;
import de.lmu.ifi.dbs.elki.utilities.documentation.Title;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.GreaterConstraint;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.GreaterEqualConstraint;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.IntParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.ObjectParameter;

/**
 * Provides the k-medians clustering algorithm, using Lloyd-style bulk
 * iterations.
 * 
 * Reference:
 * <p>
 * Clustering via Concave Minimization<br />
 * P. S. Bradley, O. L. Mangasarian, W. N. Street<br />
 * in: Advances in neural information processing systems
 * </p>
 * 
 * @author Erich Schubert
 * 
 * @param <V> vector datatype
 * @param <D> distance value type
 */
@Title("K-Medians")
@Reference(title = "Clustering via Concave Minimization", authors = "P. S. Bradley, O. L. Mangasarian, W. N. Street", booktitle = "Advances in neural information processing systems", url = "http://nips.djvuzone.org/djvu/nips09/0368.djvu")
public class KMediansLloyd<V extends NumberVector<?>, D extends Distance<D>> extends AbstractKMeans<V, D, MeanModel<V>> {
  /**
   * The logger for this class.
   */
  private static final Logging LOG = Logging.getLogger(KMediansLloyd.class);

  /**
   * Constructor.
   * 
   * @param distanceFunction distance function
   * @param k k parameter
   * @param maxiter Maxiter parameter
   * @param initializer Initialization method
   */
  public KMediansLloyd(PrimitiveDistanceFunction<NumberVector<?>, D> distanceFunction, int k, int maxiter, KMeansInitialization<V> initializer) {
    super(distanceFunction, k, maxiter, initializer);
  }

  /**
   * Run k-medians.
   * 
   * @param database Database
   * @param relation relation to use
   * @return result
   */
  public Clustering<MeanModel<V>> run(Database database, Relation<V> relation) {
    if (relation.size() <= 0) {
      return new Clustering<>("k-Medians Clustering", "kmedians-clustering");
    }
    // Choose initial medians
    List<? extends NumberVector<?>> medians = initializer.chooseInitialMeans(relation, k, getDistanceFunction());
    // Setup cluster assignment store
    List<ModifiableDBIDs> clusters = new ArrayList<>();
    for (int i = 0; i < k; i++) {
      clusters.add(DBIDUtil.newHashSet(relation.size() / k));
    }

    for (int iteration = 0; maxiter <= 0 || iteration < maxiter; iteration++) {
      if (LOG.isVerbose()) {
        LOG.verbose("K-Medians iteration " + (iteration + 1));
      }
      boolean changed = assignToNearestCluster(relation, medians, clusters);
      // Stop if no cluster assignment changed.
      if (!changed) {
        break;
      }
      // Recompute medians.
      medians = medians(clusters, medians, relation);
    }
    // Wrap result
    final NumberVector.Factory<V, ?> factory = RelationUtil.getNumberVectorFactory(relation);
    Clustering<MeanModel<V>> result = new Clustering<>("k-Medians Clustering", "kmedians-clustering");
    for (int i = 0; i < clusters.size(); i++) {
      MeanModel<V> model = new MeanModel<>(factory.newNumberVector(medians.get(i).getColumnVector().getArrayRef()));
      result.addCluster(new Cluster<>(clusters.get(i), model));
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
      super.makeOptions(config);
      IntParameter kP = new IntParameter(K_ID);
      kP.addConstraint(new GreaterConstraint(0));
      if (config.grab(kP)) {
        k = kP.intValue();
      }

      ObjectParameter<KMeansInitialization<V>> initialP = new ObjectParameter<>(INIT_ID, KMeansInitialization.class, RandomlyGeneratedInitialMeans.class);
      if (config.grab(initialP)) {
        initializer = initialP.instantiateClass(config);
      }

      IntParameter maxiterP = new IntParameter(MAXITER_ID, 0);
      maxiterP.addConstraint(new GreaterEqualConstraint(0));
      if (config.grab(maxiterP)) {
        maxiter = maxiterP.intValue();
      }
    }

    @Override
    protected KMediansLloyd<V, D> makeInstance() {
      return new KMediansLloyd<>(distanceFunction, k, maxiter, initializer);
    }
  }
}
