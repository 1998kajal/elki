package de.lmu.ifi.dbs.elki.algorithm.clustering.kmeans;

/*
 This file is part of ELKI: Environment for Developing KDD-Applications Supported by Index-Structures

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

import de.lmu.ifi.dbs.elki.algorithm.AbstractAlgorithm;
import de.lmu.ifi.dbs.elki.algorithm.clustering.kmeans.quality.KMeansQualityMeasure;
import de.lmu.ifi.dbs.elki.data.Clustering;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.model.MeanModel;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancefunction.PrimitiveDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.Distance;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.progress.FiniteProgress;
import de.lmu.ifi.dbs.elki.utilities.exceptions.AbortException;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.GreaterEqualConstraint;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.IntParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.ObjectParameter;

/**
 * Run K-Means multiple times, and keep the best run.
 * 
 * @author Stephan Baier
 * @author Erich Schubert
 * 
 * @param <V> Vector type
 * @param <D> Distance type
 * @param <M> Model type
 */
public class BestOfMultipleKMeans<V extends NumberVector<?>, D extends Distance<?>, M extends MeanModel<V>> extends AbstractAlgorithm<Clustering<M>> implements KMeans<V, D, M> {
  /**
   * The logger for this class.
   */
  private static final Logging LOG = Logging.getLogger(BestOfMultipleKMeans.class);

  /**
   * Number of trials to do.
   */
  private int trials;

  /**
   * Variant of kMeans for the bisecting step.
   */
  private KMeans<V, D, M> innerkMeans;

  /**
   * Quality measure which should be used.
   */
  private KMeansQualityMeasure<? super V, ? super D> qualityMeasure;

  /**
   * Constructor.
   * 
   * @param trials Number of trials to do.
   * @param innerkMeans K-Means variant to actually use.
   * @param qualityMeasure Quality measure
   */
  public BestOfMultipleKMeans(int trials, KMeans<V, D, M> innerkMeans, KMeansQualityMeasure<? super V, ? super D> qualityMeasure) {
    super();
    this.trials = trials;
    this.innerkMeans = innerkMeans;
    this.qualityMeasure = qualityMeasure;
  }

  @Override
  public Clustering<M> run(Database database, Relation<V> relation) {
    if (!(innerkMeans.getDistanceFunction() instanceof PrimitiveDistanceFunction)) {
      throw new AbortException("K-Means results can only be evaluated for primitive distance functions, got: " + innerkMeans.getDistanceFunction().getClass());
    }
    final PrimitiveDistanceFunction<? super V, D> df = (PrimitiveDistanceFunction<? super V, D>) innerkMeans.getDistanceFunction();
    Clustering<M> bestResult = null;
    if (trials > 1) {
      double bestCost = Double.POSITIVE_INFINITY;
      FiniteProgress prog = LOG.isVerbose() ? new FiniteProgress("K-means iterations", trials, LOG) : null;
      for (int i = 0; i < trials; i++) {
        Clustering<M> currentCandidate = innerkMeans.run(database, relation);
        double currentCost = qualityMeasure.calculateCost(currentCandidate, df, relation);

        if (LOG.isVerbose()) {
          LOG.verbose("Cost of candidate " + i + ": " + currentCost);
        }

        if (currentCost < bestCost) {
          bestResult = currentCandidate;
          bestCost = currentCost;
        }
        if (prog != null) {
          prog.incrementProcessed(LOG);
        }
      }
      if (prog != null) {
        prog.ensureCompleted(LOG);
      }
    } else {
      bestResult = innerkMeans.run(database);
    }

    return bestResult;
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return innerkMeans.getInputTypeRestriction();
  }

  @Override
  public DistanceFunction<? super V, D> getDistanceFunction() {
    return innerkMeans.getDistanceFunction();
  }

  @Override
  public void setK(int k) {
    innerkMeans.setK(k);
  }

  @Override
  public void setDistanceFunction(PrimitiveDistanceFunction<? super NumberVector<?>, D> distanceFunction) {
    innerkMeans.setDistanceFunction(distanceFunction);
  }

  @Override
  protected Logging getLogger() {
    return LOG;
  }

  /**
   * Parameterization class.
   * 
   * @author Stephan Baier
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   * 
   * @param <V> Vector type
   * @param <D> Distance type
   * @param <M> Model type
   */
  public static class Parameterizer<V extends NumberVector<?>, D extends Distance<D>, M extends MeanModel<V>> extends AbstractParameterizer {
    /**
     * Parameter to specify the iterations of the bisecting step.
     */
    public static final OptionID TRIALS_ID = new OptionID("kmeans.trials", "The number of trials to run.");

    /**
     * Parameter to specify the kMeans variant.
     */
    public static final OptionID KMEANS_ID = new OptionID("kmeans.algorithm", "KMeans variant to run multiple times.");

    /**
     * Parameter to specify the variant of quality measure.
     */
    public static final OptionID QUALITYMEASURE_ID = new OptionID("kmeans.qualitymeasure", "Quality measure variant for deciding which run to keep.");

    /**
     * Number of trials to perform.
     */
    protected int trials;

    /**
     * Variant of kMeans to use.
     */
    protected KMeans<V, D, M> kMeansVariant;

    /**
     * Quality measure.
     */
    protected KMeansQualityMeasure<? super V, ? super D> qualityMeasure;

    @Override
    protected void makeOptions(Parameterization config) {
      IntParameter trialsP = new IntParameter(TRIALS_ID);
      trialsP.addConstraint(new GreaterEqualConstraint(1));
      if (config.grab(trialsP)) {
        trials = trialsP.intValue();
      }

      ObjectParameter<KMeans<V, D, M>> kMeansVariantP = new ObjectParameter<>(KMEANS_ID, KMeans.class);
      if (config.grab(kMeansVariantP)) {
        kMeansVariant = kMeansVariantP.instantiateClass(config);
      }

      ObjectParameter<KMeansQualityMeasure<V, ? super D>> qualityMeasureP = new ObjectParameter<>(QUALITYMEASURE_ID, KMeansQualityMeasure.class);
      if (config.grab(qualityMeasureP)) {
        qualityMeasure = qualityMeasureP.instantiateClass(config);
      }
    }

    @Override
    protected BestOfMultipleKMeans<V, D, M> makeInstance() {
      return new BestOfMultipleKMeans<>(trials, kMeansVariant, qualityMeasure);
    }
  }
}
