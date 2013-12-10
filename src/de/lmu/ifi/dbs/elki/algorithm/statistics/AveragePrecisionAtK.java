package de.lmu.ifi.dbs.elki.algorithm.statistics;

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
import java.util.Collection;

import de.lmu.ifi.dbs.elki.algorithm.AbstractDistanceBasedAlgorithm;
import de.lmu.ifi.dbs.elki.data.ClassLabel;
import de.lmu.ifi.dbs.elki.data.DoubleVector;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.ids.distance.KNNList;
import de.lmu.ifi.dbs.elki.database.query.distance.DistanceQuery;
import de.lmu.ifi.dbs.elki.database.query.knn.KNNQuery;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.NumberDistance;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.progress.FiniteProgress;
import de.lmu.ifi.dbs.elki.math.MeanVariance;
import de.lmu.ifi.dbs.elki.result.CollectionResult;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.CommonConstraints;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.DoubleParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.Flag;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.IntParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.LongParameter;

/**
 * Evaluate a distance functions performance by computing the average precision
 * at k, when ranking the objects by distance.
 * 
 * @author Erich Schubert
 * 
 * @param <V> Vector type
 * @param <D> Distance type
 */
public class AveragePrecisionAtK<V extends Object, D extends NumberDistance<D, ?>> extends AbstractDistanceBasedAlgorithm<V, D, CollectionResult<DoubleVector>> {
  /**
   * The logger for this class.
   */
  private static final Logging LOG = Logging.getLogger(AveragePrecisionAtK.class);

  /**
   * The parameter k - the number of neighbors to retrieve.
   */
  private int k;

  /**
   * Relative number of object to use in sampling.
   */
  private double sampling = 1.0;

  /**
   * Random sampling seed.
   */
  private Long seed = null;

  /**
   * Include query object in evaluation.
   */
  private boolean includeSelf;

  /**
   * Constructor.
   * 
   * @param distanceFunction Distance function
   * @param k K parameter
   * @param sampling Sampling rate
   * @param seed Random sampling seed (may be null)
   * @param includeSelf Include query object in evaluation
   */
  public AveragePrecisionAtK(DistanceFunction<? super V, D> distanceFunction, int k, double sampling, Long seed, boolean includeSelf) {
    super(distanceFunction);
    this.k = k;
    this.sampling = sampling;
    this.seed = seed;
    this.includeSelf = includeSelf;
  }

  /**
   * Run the algorithm
   * 
   * @param database Database to run on (for kNN queries)
   * @param relation Relation for distance computations
   * @param lrelation Relation for class label comparison
   * @return Vectors containing mean and standard deviation.
   */
  public CollectionResult<DoubleVector> run(Database database, Relation<V> relation, Relation<ClassLabel> lrelation) {
    final DistanceQuery<V, D> distQuery = database.getDistanceQuery(relation, getDistanceFunction());
    final int qk = k + (includeSelf ? 0 : 1);
    final KNNQuery<V, D> knnQuery = database.getKNNQuery(distQuery, qk);

    MeanVariance[] mvs = MeanVariance.newArray(k);

    final DBIDs ids;
    if(sampling < 1.0) {
      int size = Math.max(1, (int) (sampling * relation.size()));
      ids = DBIDUtil.randomSample(relation.getDBIDs(), size, seed);
    }
    else {
      ids = relation.getDBIDs();
    }

    if(LOG.isVerbose()) {
      LOG.verbose("Processing points...");
    }
    FiniteProgress objloop = LOG.isVerbose() ? new FiniteProgress("Computing nearest neighbors", ids.size(), LOG) : null;
    // sort neighbors
    for(DBIDIter iter = ids.iter(); iter.valid(); iter.advance()) {
      KNNList<D> knn = knnQuery.getKNNForDBID(iter, qk);
      Object label = lrelation.get(iter);

      int positive = 0, i = 0;
      for(DBIDIter ri = knn.iter(); i < k && ri.valid(); ri.advance()) {
        if(!includeSelf && DBIDUtil.equal(iter, ri)) {
          continue;
        }
        Object olabel = lrelation.get(ri);
        if(label == null) {
          if(olabel == null) {
            positive += 1;
          }
        }
        else {
          if(label.equals(olabel)) {
            positive += 1;
          }
        }
        final double precision = positive / (double) (i + 1);
        mvs[i].put(precision);
        i++;
      }
      if(objloop != null) {
        objloop.incrementProcessed(LOG);
      }
    }
    if(objloop != null) {
      objloop.ensureCompleted(LOG);
    }
    // Collections.sort(results);

    // Transform Histogram into a Double Vector array.
    Collection<DoubleVector> res = new ArrayList<>(k);
    for(int i = 0; i < k; i++) {
      DoubleVector row = new DoubleVector(new double[] { mvs[i].getMean(), mvs[i].getSampleStddev() });
      res.add(row);
    }
    return new CollectionResult<>("Average Precision", "average-precision", res);
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return TypeUtil.array(getDistanceFunction().getInputTypeRestriction(), TypeUtil.CLASSLABEL);
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
  public static class Parameterizer<V extends NumberVector<?>, D extends NumberDistance<D, ?>> extends AbstractDistanceBasedAlgorithm.Parameterizer<V, D> {
    /**
     * Parameter k to compute the average precision at.
     */
    private static final OptionID K_ID = new OptionID("avep.k", "K to compute the average precision at.");

    /**
     * Parameter to enable sampling.
     */
    public static final OptionID SAMPLING_ID = new OptionID("avep.sampling", "Relative amount of object to sample.");

    /**
     * Parameter to control the sampling random seed.
     */
    public static final OptionID SEED_ID = new OptionID("avep.sampling-seed", "Random seed for deterministic sampling.");

    /**
     * Parameter to include the query object.
     */
    public static final OptionID INCLUDESELF_ID = new OptionID("avep.includeself", "Include the query object in the evaluation.");

    /**
     * Neighborhood size.
     */
    protected int k = 20;

    /**
     * Relative amount of data to sample.
     */
    protected double sampling = 1.0;

    /**
     * Random sampling seed.
     */
    protected Long seed = null;

    /**
     * Include query object in evaluation.
     */
    protected boolean includeSelf;

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      final IntParameter kP = new IntParameter(K_ID);
      kP.addConstraint(CommonConstraints.GREATER_THAN_ONE_INT);
      if(config.grab(kP)) {
        k = kP.getValue();
      }
      final DoubleParameter samplingP = new DoubleParameter(SAMPLING_ID);
      samplingP.addConstraint(CommonConstraints.GREATER_THAN_ZERO_DOUBLE);
      samplingP.addConstraint(CommonConstraints.LESS_EQUAL_ONE_DOUBLE);
      samplingP.setOptional(true);
      if(config.grab(samplingP)) {
        sampling = samplingP.getValue();
      }
      final LongParameter rndP = new LongParameter(SEED_ID);
      rndP.setOptional(true);
      if(config.grab(rndP)) {
        seed = rndP.getValue();
      }
      final Flag includeP = new Flag(INCLUDESELF_ID);
      if(config.grab(includeP)) {
        includeSelf = includeP.isTrue();
      }
    }

    @Override
    protected AveragePrecisionAtK<V, D> makeInstance() {
      return new AveragePrecisionAtK<>(distanceFunction, k, sampling, seed, includeSelf);
    }
  }
}
