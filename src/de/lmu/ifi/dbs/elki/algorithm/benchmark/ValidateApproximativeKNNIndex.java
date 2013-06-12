package de.lmu.ifi.dbs.elki.algorithm.benchmark;

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
import de.lmu.ifi.dbs.elki.database.QueryUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDRange;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.ids.distance.KNNList;
import de.lmu.ifi.dbs.elki.database.query.DatabaseQuery;
import de.lmu.ifi.dbs.elki.database.query.distance.DistanceQuery;
import de.lmu.ifi.dbs.elki.database.query.knn.KNNQuery;
import de.lmu.ifi.dbs.elki.database.query.knn.LinearScanKNNQuery;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.datasource.DatabaseConnection;
import de.lmu.ifi.dbs.elki.datasource.bundle.MultipleObjectsBundle;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.Distance;
import de.lmu.ifi.dbs.elki.distance.distancevalue.NumberDistance;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.progress.FiniteProgress;
import de.lmu.ifi.dbs.elki.math.MeanVariance;
import de.lmu.ifi.dbs.elki.result.Result;
import de.lmu.ifi.dbs.elki.utilities.RandomFactory;
import de.lmu.ifi.dbs.elki.utilities.exceptions.AbortException;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.DoubleParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.Flag;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.IntParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.ObjectParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.RandomParameter;

/**
 * Algorithm to validate the quality of an approximative kNN index, by
 * performing a number of queries and comparing them to the results obtained by
 * exact indexing (e.g. linear scanning).
 * 
 * @author Erich Schubert
 * 
 * @param <O> Object type
 * 
 * @apiviz.uses KNNQuery
 */
public class ValidateApproximativeKNNIndex<O, D extends Distance<D>> extends AbstractDistanceBasedAlgorithm<O, D, Result> {
  /**
   * The logger for this class.
   */
  private static final Logging LOG = Logging.getLogger(ValidateApproximativeKNNIndex.class);

  /**
   * Number of neighbors to retrieve.
   */
  protected int k = 10;

  /**
   * The alternate query point source. Optional.
   */
  protected DatabaseConnection queries = null;

  /**
   * Sampling size.
   */
  protected double sampling = -1;

  /**
   * Force linear scanning.
   */
  protected boolean forcelinear = false;

  /**
   * Random generator factory
   */
  protected RandomFactory random;

  /**
   * Constructor.
   * 
   * @param distanceFunction Distance function to use
   * @param k K parameter
   * @param queries Query data set (may be null!)
   * @param sampling Sampling rate
   * @param random Random factory
   * @param forcelinear Force the use of linear scanning.
   */
  public ValidateApproximativeKNNIndex(DistanceFunction<? super O, D> distanceFunction, int k, DatabaseConnection queries, double sampling, boolean forcelinear, RandomFactory random) {
    super(distanceFunction);
    this.k = k;
    this.queries = queries;
    this.sampling = sampling;
    this.forcelinear = forcelinear;
    this.random = random;
  }

  /**
   * Run the algorithm.
   * 
   * @param database Database
   * @param relation Relation
   * @return Null result
   */
  public Result run(Database database, Relation<O> relation) {
    // Get a distance and kNN query instance.
    DistanceQuery<O, D> distQuery = database.getDistanceQuery(relation, getDistanceFunction());
    // Approximate query:
    KNNQuery<O, D> knnQuery = database.getKNNQuery(distQuery, k, DatabaseQuery.HINT_OPTIMIZED_ONLY);
    if (knnQuery == null || knnQuery instanceof LinearScanKNNQuery) {
      throw new AbortException("Expected an accelerated query, but got a linear scan -- index is not used.");
    }
    // Exact query:
    KNNQuery<O, D> truekNNQuery;
    if (forcelinear) {
      truekNNQuery = QueryUtil.getLinearScanKNNQuery(distQuery);
    } else {
      truekNNQuery = database.getKNNQuery(distQuery, k, DatabaseQuery.HINT_EXACT);
    }
    if (knnQuery.getClass().equals(truekNNQuery.getClass())) {
      LOG.warning("Query classes are the same. This experiment may be invalid!");
    }

    // No query set - use original database.
    if (queries == null) {
      final DBIDs sample;
      if (sampling <= 0) {
        sample = relation.getDBIDs();
      } else if (sampling < 1.1) {
        int size = (int) Math.min(sampling * relation.size(), relation.size());
        sample = DBIDUtil.randomSample(relation.getDBIDs(), size, random);
      } else {
        int size = (int) Math.min(sampling, relation.size());
        sample = DBIDUtil.randomSample(relation.getDBIDs(), size, random);
      }
      FiniteProgress prog = LOG.isVeryVerbose() ? new FiniteProgress("kNN queries", sample.size(), LOG) : null;
      MeanVariance mv = new MeanVariance(), mvrec = new MeanVariance(), mvdist = new MeanVariance(), mvderr = new MeanVariance();
      int misses = 0;
      for (DBIDIter iditer = sample.iter(); iditer.valid(); iditer.advance()) {
        // Query index:
        KNNList<D> knns = knnQuery.getKNNForDBID(iditer, k);
        // Query reference:
        KNNList<D> trueknns = truekNNQuery.getKNNForDBID(iditer, k);

        // Put adjusted knn size:
        mv.put(knns.size() * k / (double) trueknns.size());

        // Put recall:
        mvrec.put(DBIDUtil.intersectionSize(knns, trueknns) / trueknns.size());

        if (knns.size() >= k) {
          D kdist = knns.getKNNDistance();
          if (kdist instanceof NumberDistance) {
            final double dist = ((NumberDistance<?, ?>) kdist).doubleValue();
            final double tdist = ((NumberDistance<?, ?>) trueknns.getKNNDistance()).doubleValue();
            if (tdist > 0.0) {
              mvdist.put(dist);
              mvderr.put(dist / tdist);
            }
          }
        } else {
          // Less than k objects.
          misses++;
        }
        if (prog != null) {
          prog.incrementProcessed(LOG);
        }
      }
      if (prog != null) {
        prog.ensureCompleted(LOG);
      }
      if (LOG.isStatistics()) {
        LOG.statistics("Mean number of results: " + mv.getMean() + " +- " + mv.getNaiveStddev());
        LOG.statistics("Recall of true results: " + mvrec.getMean() + " +- " + mvrec.getNaiveStddev());
        if (mvdist.getCount() > 0) {
          LOG.statistics("Mean k-distance: " + mvdist.getMean() + " +- " + mvdist.getNaiveStddev());
          LOG.statistics("Mean relative k-distance: " + mvderr.getMean() + " +- " + mvderr.getNaiveStddev());
        }
        if (misses > 0) {
          LOG.statistics(String.format("Number of queries that returned less than k=%d objects: %d (%.2f%%)", k, misses, misses * 100. / sample.size()));
        }
      }
    } else {
      // Separate query set.
      TypeInformation res = getDistanceFunction().getInputTypeRestriction();
      MultipleObjectsBundle bundle = queries.loadData();
      int col = -1;
      for (int i = 0; i < bundle.metaLength(); i++) {
        if (res.isAssignableFromType(bundle.meta(i))) {
          col = i;
          break;
        }
      }
      if (col < 0) {
        throw new AbortException("No compatible data type in query input was found. Expected: " + res.toString());
      }
      // Random sampling is a bit of hack, sorry.
      // But currently, we don't (yet) have an "integer random sample" function.
      DBIDRange sids = DBIDUtil.generateStaticDBIDRange(bundle.dataLength());

      final DBIDs sample;
      if (sampling <= 0) {
        sample = sids;
      } else if (sampling < 1.1) {
        int size = (int) Math.min(sampling * relation.size(), relation.size());
        sample = DBIDUtil.randomSample(sids, size, random);
      } else {
        int size = (int) Math.min(sampling, sids.size());
        sample = DBIDUtil.randomSample(sids, size, random);
      }
      FiniteProgress prog = LOG.isVeryVerbose() ? new FiniteProgress("kNN queries", sample.size(), LOG) : null;
      MeanVariance mv = new MeanVariance(), mvrec = new MeanVariance(), mvdist = new MeanVariance(), mvderr = new MeanVariance();
      int misses = 0;
      for (DBIDIter iditer = sample.iter(); iditer.valid(); iditer.advance()) {
        int off = sids.binarySearch(iditer);
        assert (off >= 0);
        @SuppressWarnings("unchecked")
        O o = (O) bundle.data(off, col);

        // Query index:
        KNNList<D> knns = knnQuery.getKNNForObject(o, k);
        // Query reference:
        KNNList<D> trueknns = truekNNQuery.getKNNForObject(o, k);

        // Put adjusted knn size:
        mv.put(knns.size() * k / (double) trueknns.size());

        // Put recall:
        mvrec.put(DBIDUtil.intersectionSize(knns, trueknns) / trueknns.size());

        if (knns.size() >= k) {
          D kdist = knns.getKNNDistance();
          if (kdist instanceof NumberDistance) {
            final double dist = ((NumberDistance<?, ?>) kdist).doubleValue();
            final double tdist = ((NumberDistance<?, ?>) trueknns.getKNNDistance()).doubleValue();
            if (tdist > 0.0) {
              mvdist.put(dist);
              mvderr.put(dist / tdist);
            }
          }
        } else {
          // Less than k objects.
          misses++;
        }
        if (prog != null) {
          prog.incrementProcessed(LOG);
        }
      }
      if (prog != null) {
        prog.ensureCompleted(LOG);
      }
      if (LOG.isStatistics()) {
        LOG.statistics("Mean number of results: " + mv.getMean() + " +- " + mv.getNaiveStddev());
        LOG.statistics("Recall of true results: " + mvrec.getMean() + " +- " + mvrec.getNaiveStddev());
        if (mvdist.getCount() > 0) {
          LOG.statistics("Mean k-distance: " + mvdist.getMean() + " +- " + mvdist.getNaiveStddev());
          LOG.statistics("Mean relative k-distance: " + mvderr.getMean() + " +- " + mvderr.getNaiveStddev());
        }
        if (misses > 0) {
          LOG.statistics(String.format("Number of queries that returned less than k=%d objects: %d (%.2f%%)", k, misses, misses * 100. / sample.size()));
        }
      }
    }
    return null;
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return TypeUtil.array(getDistanceFunction().getInputTypeRestriction());
  }

  @Override
  protected Logging getLogger() {
    return LOG;
  }

  /**
   * Parameterization class
   * 
   * @apiviz.exclude
   * 
   * @author Erich Schubert
   * 
   * @param <O> Object type
   * @param <D> Distance type
   */
  public static class Parameterizer<O, D extends Distance<D>> extends AbstractDistanceBasedAlgorithm.Parameterizer<O, D> {
    /**
     * Parameter for the number of neighbors.
     */
    public static final OptionID K_ID = new OptionID("validateknn.k", "Number of neighbors to retreive for kNN benchmarking.");

    /**
     * Parameter for the query dataset.
     */
    public static final OptionID QUERY_ID = new OptionID("validateknn.query", "Data source for the queries. If not set, the queries are taken from the database.");

    /**
     * Parameter for the sampling size.
     */
    public static final OptionID SAMPLING_ID = new OptionID("validateknn.sampling", "Sampling size parameter. If the value is less or equal 1, it is assumed to be the relative share. Larger values will be interpreted as integer sizes. By default, all data will be used.");

    /**
     * Force linear scanning.
     */
    public static final OptionID FORCE_ID = new OptionID("validateknn.force-linear", "Force the use of linear scanning as reference.");

    /**
     * Parameter for the random generator.
     */
    public static final OptionID RANDOM_ID = new OptionID("validateknn.random", "Random generator for sampling.");

    /**
     * K parameter
     */
    protected int k = 10;

    /**
     * The alternate query point source. Optional.
     */
    protected DatabaseConnection queries = null;

    /**
     * Sampling size.
     */
    protected double sampling = -1;

    /**
     * Force linear scanning.
     */
    protected boolean forcelinear = false;

    /**
     * Random generator factory
     */
    protected RandomFactory random;

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      IntParameter kP = new IntParameter(K_ID);
      if (config.grab(kP)) {
        k = kP.intValue();
      }
      ObjectParameter<DatabaseConnection> queryP = new ObjectParameter<>(QUERY_ID, DatabaseConnection.class);
      queryP.setOptional(true);
      if (config.grab(queryP)) {
        queries = queryP.instantiateClass(config);
      }
      DoubleParameter samplingP = new DoubleParameter(SAMPLING_ID);
      samplingP.setOptional(true);
      if (config.grab(samplingP)) {
        sampling = samplingP.doubleValue();
      }
      Flag forceP = new Flag(FORCE_ID);
      if (config.grab(forceP)) {
        forcelinear = forceP.isTrue();
      }
      RandomParameter randomP = new RandomParameter(RANDOM_ID, RandomFactory.DEFAULT);
      if (config.grab(randomP)) {
        random = randomP.getValue();
      }
    }

    @Override
    protected ValidateApproximativeKNNIndex<O, D> makeInstance() {
      return new ValidateApproximativeKNNIndex<>(distanceFunction, k, queries, sampling, forcelinear, random);
    }
  }
}
