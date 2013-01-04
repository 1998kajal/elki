package de.lmu.ifi.dbs.elki.algorithm.outlier;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2012
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

import java.util.HashMap;

import de.lmu.ifi.dbs.elki.algorithm.AbstractDistanceBasedAlgorithm;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.QueryUtil;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreFactory;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreUtil;
import de.lmu.ifi.dbs.elki.database.datastore.WritableDoubleDataStore;
import de.lmu.ifi.dbs.elki.database.ids.ArrayModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDRef;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.ids.DoubleDBIDPair;
import de.lmu.ifi.dbs.elki.database.ids.ModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.query.knn.KNNQuery;
import de.lmu.ifi.dbs.elki.database.relation.MaterializedRelation;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distanceresultlist.KNNResult;
import de.lmu.ifi.dbs.elki.distance.distancevalue.DoubleDistance;
import de.lmu.ifi.dbs.elki.distance.similarityfunction.PrimitiveSimilarityFunction;
import de.lmu.ifi.dbs.elki.distance.similarityfunction.kernel.KernelMatrix;
import de.lmu.ifi.dbs.elki.distance.similarityfunction.kernel.PolynomialKernelFunction;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.math.DoubleMinMax;
import de.lmu.ifi.dbs.elki.math.MeanVariance;
import de.lmu.ifi.dbs.elki.math.linearalgebra.Vector;
import de.lmu.ifi.dbs.elki.result.outlier.InvertedOutlierScoreMeta;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierResult;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierScoreMeta;
import de.lmu.ifi.dbs.elki.utilities.datastructures.heap.ComparableMaxHeap;
import de.lmu.ifi.dbs.elki.utilities.datastructures.heap.ComparableMinHeap;
import de.lmu.ifi.dbs.elki.utilities.documentation.Description;
import de.lmu.ifi.dbs.elki.utilities.documentation.Reference;
import de.lmu.ifi.dbs.elki.utilities.documentation.Title;
import de.lmu.ifi.dbs.elki.utilities.exceptions.AbortException;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.GreaterEqualConstraint;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.IntParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.ObjectParameter;

/**
 * Angle-Based Outlier Detection
 * 
 * Outlier detection using variance analysis on angles, especially for high
 * dimensional data sets.
 * 
 * H.-P. Kriegel, M. Schubert, and A. Zimek: Angle-Based Outlier Detection in
 * High-dimensional Data. In: Proc. 14th ACM SIGKDD Int. Conf. on Knowledge
 * Discovery and Data Mining (KDD '08), Las Vegas, NV, 2008.
 * 
 * @author Matthias Schubert (Original Code)
 * @author Erich Schubert (ELKIfication)
 * 
 * @apiviz.has KNNQuery
 * 
 * @param <V> Vector type
 */
@Title("ABOD: Angle-Based Outlier Detection")
@Description("Outlier detection using variance analysis on angles, especially for high dimensional data sets.")
@Reference(authors = "H.-P. Kriegel, M. Schubert, and A. Zimek", title = "Angle-Based Outlier Detection in High-dimensional Data", booktitle = "Proc. 14th ACM SIGKDD Int. Conf. on Knowledge Discovery and Data Mining (KDD '08), Las Vegas, NV, 2008", url = "http://dx.doi.org/10.1145/1401890.1401946")
public class ABOD<V extends NumberVector<?>> extends AbstractDistanceBasedAlgorithm<V, DoubleDistance, OutlierResult> implements OutlierAlgorithm {
  /**
   * The logger for this class.
   */
  private static final Logging LOG = Logging.getLogger(ABOD.class);

  /**
   * Parameter for k, the number of neighbors used in kNN queries.
   */
  public static final OptionID K_ID = new OptionID("abod.k", "Parameter k for kNN queries.");

  /**
   * Parameter for sample size to be used in fast mode.
   */
  public static final OptionID FAST_SAMPLE_ID = new OptionID("abod.samplesize", "Sample size to enable fast mode.");

  /**
   * Parameter for the kernel function.
   */
  public static final OptionID KERNEL_FUNCTION_ID = new OptionID("abod.kernelfunction", "Kernel function to use.");

  /**
   * The preprocessor used to materialize the kNN neighborhoods.
   */
  public static final OptionID PREPROCESSOR_ID = new OptionID("abod.knnquery", "Processor to compute the kNN neighborhoods.");

  /**
   * use alternate code below.
   */
  private static final boolean USE_RND_SAMPLE = false;

  /**
   * k parameter.
   */
  private int k;

  /**
   * Variable to store fast mode sampling value.
   */
  int sampleSize = 0;

  /**
   * Store the configured Kernel version.
   */
  private PrimitiveSimilarityFunction<? super V, DoubleDistance> primitiveKernelFunction;

  /**
   * Static DBID map.
   */
  private ArrayModifiableDBIDs staticids = null;

  /**
   * Actual constructor, with parameters. Fast mode (sampling).
   * 
   * @param k k parameter
   * @param sampleSize sample size
   * @param primitiveKernelFunction Kernel function to use
   * @param distanceFunction Distance function
   */
  public ABOD(int k, int sampleSize, PrimitiveSimilarityFunction<? super V, DoubleDistance> primitiveKernelFunction, DistanceFunction<V, DoubleDistance> distanceFunction) {
    super(distanceFunction);
    this.k = k;
    this.sampleSize = sampleSize;
    this.primitiveKernelFunction = primitiveKernelFunction;
  }

  /**
   * Actual constructor, with parameters. Slow mode (exact).
   * 
   * @param k k parameter
   * @param primitiveKernelFunction kernel function to use
   * @param distanceFunction Distance function
   */
  public ABOD(int k, PrimitiveSimilarityFunction<? super V, DoubleDistance> primitiveKernelFunction, DistanceFunction<V, DoubleDistance> distanceFunction) {
    super(distanceFunction);
    this.k = k;
    this.sampleSize = 0;
    this.primitiveKernelFunction = primitiveKernelFunction;
  }

  /**
   * Main part of the algorithm. Exact version.
   * 
   * @param relation Relation to query
   * @return result
   */
  public OutlierResult getRanking(Relation<V> relation) {
    // Fix a static set of IDs
    staticids = DBIDUtil.newArray(relation.getDBIDs());
    staticids.sort();

    KernelMatrix kernelMatrix = new KernelMatrix(primitiveKernelFunction, relation, staticids);
    ComparableMaxHeap<DoubleDBIDPair> pq = new ComparableMaxHeap<>(relation.size());

    // preprocess kNN neighborhoods
    KNNQuery<V, DoubleDistance> knnQuery = QueryUtil.getKNNQuery(relation, getDistanceFunction(), k);

    MeanVariance s = new MeanVariance();
    for (DBIDIter objKey = relation.iterDBIDs(); objKey.valid(); objKey.advance()) {
      s.reset();

      KNNResult<DoubleDistance> neighbors = knnQuery.getKNNForDBID(objKey, k);
      for (DBIDIter key1 = neighbors.iter(); key1.valid(); key1.advance()) {
        for (DBIDIter key2 = neighbors.iter(); key2.valid(); key2.advance()) {
          if (DBIDUtil.equal(key2, key1) || DBIDUtil.equal(key1, objKey) || DBIDUtil.equal(key2, objKey)) {
            continue;
          }
          double nenner = calcDenominator(kernelMatrix, objKey, key1, key2);

          if (nenner != 0) {
            double sqrtnenner = Math.sqrt(nenner);
            double tmp = calcNumerator(kernelMatrix, objKey, key1, key2) / nenner;
            s.put(tmp, 1 / sqrtnenner);
          }

        }
      }
      // Sample variance probably would be correct, however the numerical
      // instabilities can actually break ABOD here.
      pq.add(DBIDUtil.newPair(s.getNaiveVariance(), objKey));
    }

    DoubleMinMax minmaxabod = new DoubleMinMax();
    WritableDoubleDataStore abodvalues = DataStoreUtil.makeDoubleStorage(relation.getDBIDs(), DataStoreFactory.HINT_STATIC);
    while(!pq.isEmpty()) {
      DoubleDBIDPair pair = pq.poll();
      abodvalues.putDouble(pair, pair.doubleValue());
      minmaxabod.put(pair.doubleValue());
    }
    // Build result representation.
    Relation<Double> scoreResult = new MaterializedRelation<>("Angle-based Outlier Degree", "abod-outlier", TypeUtil.DOUBLE, abodvalues, relation.getDBIDs());
    OutlierScoreMeta scoreMeta = new InvertedOutlierScoreMeta(minmaxabod.getMin(), minmaxabod.getMax(), 0.0, Double.POSITIVE_INFINITY);
    return new OutlierResult(scoreMeta, scoreResult);
  }

  /**
   * Main part of the algorithm. Fast version.
   * 
   * @param relation Relation to use
   * @return result
   */
  public OutlierResult getFastRanking(Relation<V> relation) {
    final DBIDs ids = relation.getDBIDs();
    // Fix a static set of IDs
    // TODO: add a DBIDUtil.ensureSorted?
    staticids = DBIDUtil.newArray(ids);
    staticids.sort();

    KernelMatrix kernelMatrix = new KernelMatrix(primitiveKernelFunction, relation, staticids);

    ComparableMaxHeap<DoubleDBIDPair> pq = new ComparableMaxHeap<>(relation.size());
    // get Candidate Ranking
    for (DBIDIter aKey = relation.iterDBIDs(); aKey.valid(); aKey.advance()) {
      WritableDoubleDataStore dists = DataStoreUtil.makeDoubleStorage(ids, DataStoreFactory.HINT_TEMP | DataStoreFactory.HINT_HOT);
      // determine kNearestNeighbors and pairwise distances
      ComparableMinHeap<DoubleDBIDPair> nn;
      if (!USE_RND_SAMPLE) {
        nn = calcDistsandNN(relation, kernelMatrix, sampleSize, aKey, dists);
      } else {
        // alternative:
        nn = calcDistsandRNDSample(relation, kernelMatrix, sampleSize, aKey, dists);
      }

      // get normalization
      double[] counter = calcFastNormalization(aKey, dists, staticids);
      // umsetzen von Pq zu list
      ModifiableDBIDs neighbors = DBIDUtil.newArray(nn.size());
      while (!nn.isEmpty()) {
        neighbors.add(nn.poll());
      }
      // getFilter
      double var = getAbofFilter(kernelMatrix, aKey, dists, counter[1], counter[0], neighbors);
      pq.add(DBIDUtil.newPair(var, aKey));
    }
    // refine Candidates
    ComparableMinHeap<DoubleDBIDPair> resqueue = new ComparableMinHeap<>(k);
    MeanVariance s = new MeanVariance();
    while (!pq.isEmpty()) {
      if (resqueue.size() == k && pq.peek().doubleValue() > resqueue.peek().doubleValue()) {
        break;
      }
      // double approx = pq.peek().getFirst();
      DBIDRef aKey = pq.poll();
      s.reset();
      for (DBIDIter bKey = relation.iterDBIDs(); bKey.valid(); bKey.advance()) {
        if (DBIDUtil.equal(bKey, aKey)) {
          continue;
        }
        for (DBIDIter cKey = relation.iterDBIDs(); cKey.valid(); cKey.advance()) {
          if (DBIDUtil.equal(cKey, aKey)) {
            continue;
          }
          // double nenner = dists[y]*dists[z];
          double nenner = calcDenominator(kernelMatrix, aKey, bKey, cKey);
          if (nenner != 0) {
            double tmp = calcNumerator(kernelMatrix, aKey, bKey, cKey) / nenner;
            double sqrtNenner = Math.sqrt(nenner);
            s.put(tmp, 1 / sqrtNenner);
          }
        }
      }
      double var = s.getSampleVariance();
      if (resqueue.size() < k) {
        resqueue.add(DBIDUtil.newPair(var, aKey));
      } else {
        if (resqueue.peek().doubleValue() > var) {
          resqueue.replaceTopElement(DBIDUtil.newPair(var, aKey));
        }
      }

    }
    DoubleMinMax minmaxabod = new DoubleMinMax();
    WritableDoubleDataStore abodvalues = DataStoreUtil.makeDoubleStorage(ids, DataStoreFactory.HINT_STATIC);
    while(!pq.isEmpty()) {
      DoubleDBIDPair pair = pq.poll();
      abodvalues.putDouble(pair, pair.doubleValue());
      minmaxabod.put(pair.doubleValue());
    }
    // Build result representation.
    Relation<Double> scoreResult = new MaterializedRelation<>("Angle-based Outlier Detection", "abod-outlier", TypeUtil.DOUBLE, abodvalues, ids);
    OutlierScoreMeta scoreMeta = new InvertedOutlierScoreMeta(minmaxabod.getMin(), minmaxabod.getMax(), 0.0, Double.POSITIVE_INFINITY);
    return new OutlierResult(scoreMeta, scoreResult);
  }

  private double[] calcFastNormalization(DBIDRef x, WritableDoubleDataStore dists, DBIDs ids) {
    double[] result = new double[2];

    double sum = 0;
    double sumF = 0;
    for (DBIDIter yKey = ids.iter(); yKey.valid(); yKey.advance()) {
      if (dists.doubleValue(yKey) != 0) {
        double tmp = 1 / Math.sqrt(dists.doubleValue(yKey));
        sum += tmp;
        sumF += (1 / dists.doubleValue(yKey)) * tmp;
      }
    }
    double sofar = 0;
    double sofarF = 0;
    for (DBIDIter zKey = ids.iter(); zKey.valid(); zKey.advance()) {
      if (dists.doubleValue(zKey) != 0) {
        double tmp = 1 / Math.sqrt(dists.doubleValue(zKey));
        sofar += tmp;
        double rest = sum - sofar;
        result[0] += tmp * rest;

        sofarF += (1 / dists.doubleValue(zKey)) * tmp;
        double restF = sumF - sofarF;
        result[1] += (1 / dists.doubleValue(zKey)) * tmp * restF;
      }
    }
    return result;
  }

  private double getAbofFilter(KernelMatrix kernelMatrix, DBIDRef aKey, WritableDoubleDataStore dists, double fulCounter, double counter, DBIDs neighbors) {
    double sum = 0.0;
    double sqrSum = 0.0;
    double partCounter = 0;
    for (DBIDIter bKey = neighbors.iter(); bKey.valid(); bKey.advance()) {
      if (DBIDUtil.equal(bKey, aKey)) {
        continue;
      }
      for (DBIDIter cKey = neighbors.iter(); cKey.valid(); cKey.advance()) {
        if (DBIDUtil.equal(cKey, aKey)) {
          continue;
        }
        if (DBIDUtil.compare(bKey, cKey) > 0) {
          double nenner = dists.doubleValue(bKey) * dists.doubleValue(cKey);
          if (nenner != 0) {
            double tmp = calcNumerator(kernelMatrix, aKey, bKey, cKey) / nenner;
            double sqrtNenner = Math.sqrt(nenner);
            sum += tmp * (1 / sqrtNenner);
            sqrSum += tmp * tmp * (1 / sqrtNenner);
            partCounter += (1 / (sqrtNenner * nenner));
          }
        }
      }
    }
    // TODO: Document the meaning / use of fulCounter, partCounter.
    double mu = (sum + (fulCounter - partCounter)) / counter;
    return (sqrSum / counter) - (mu * mu);
  }

  /**
   * Compute the cosinus value between vectors aKey and bKey.
   * 
   * @param kernelMatrix
   * @param aKey
   * @param bKey
   * @return cosinus value
   */
  private double calcCos(KernelMatrix kernelMatrix, DBIDRef aKey, DBIDRef bKey) {
    final int ai = mapDBID(aKey);
    final int bi = mapDBID(bKey);
    return kernelMatrix.getDistance(ai, ai) + kernelMatrix.getDistance(bi, bi) - 2 * kernelMatrix.getDistance(ai, bi);
  }

  private int mapDBID(DBIDRef aKey) {
    // TODO: this is not the most efficient...
    int off = staticids.binarySearch(aKey);
    if (off < 0) {
      throw new AbortException("Did not find id " + aKey.toString() + " in staticids. " + staticids.contains(aKey));
    }
    return off + 1;
  }

  private double calcDenominator(KernelMatrix kernelMatrix, DBIDRef aKey, DBIDRef bKey, DBIDRef cKey) {
    return calcCos(kernelMatrix, aKey, bKey) * calcCos(kernelMatrix, aKey, cKey);
  }

  private double calcNumerator(KernelMatrix kernelMatrix, DBIDRef aKey, DBIDRef bKey, DBIDRef cKey) {
    final int ai = mapDBID(aKey);
    final int bi = mapDBID(bKey);
    final int ci = mapDBID(cKey);
    return (kernelMatrix.getDistance(ai, ai) + kernelMatrix.getDistance(bi, ci) - kernelMatrix.getDistance(ai, ci) - kernelMatrix.getDistance(ai, bi));
  }

  private ComparableMinHeap<DoubleDBIDPair> calcDistsandNN(Relation<V> data, KernelMatrix kernelMatrix, int sampleSize, DBIDRef aKey, WritableDoubleDataStore dists) {
    ComparableMinHeap<DoubleDBIDPair> nn = new ComparableMinHeap<>(sampleSize);
    for (DBIDIter bKey = data.iterDBIDs(); bKey.valid(); bKey.advance()) {
      double val = calcCos(kernelMatrix, aKey, bKey);
      dists.putDouble(bKey, val);
      if (nn.size() < sampleSize) {
        nn.add(DBIDUtil.newPair(val, bKey));
      } else {
        if (val < nn.peek().doubleValue()) {
          nn.replaceTopElement(DBIDUtil.newPair(val, bKey));
        }
      }
    }
    return nn;
  }

  private ComparableMinHeap<DoubleDBIDPair> calcDistsandRNDSample(Relation<V> data, KernelMatrix kernelMatrix, int sampleSize, DBIDRef aKey, WritableDoubleDataStore dists) {
    ComparableMinHeap<DoubleDBIDPair> nn = new ComparableMinHeap<>(sampleSize);
    int step = (int) ((double) data.size() / (double) sampleSize);
    int counter = 0;
    for (DBIDIter bKey = data.iterDBIDs(); bKey.valid(); bKey.advance()) {
      double val = calcCos(kernelMatrix, aKey, bKey);
      dists.putDouble(bKey, val);
      if (counter % step == 0) {
        nn.add(DBIDUtil.newPair(val, bKey));
      }
      counter++;
    }
    return nn;
  }

  /**
   * Get explanations for points in the database.
   * 
   * @param data to get explanations for
   * @return String explanation
   */
  // TODO: this should be done by the result classes.
  public String getExplanations(Relation<V> data) {
    KernelMatrix kernelMatrix = new KernelMatrix(primitiveKernelFunction, data, staticids);
    // PQ for Outlier Ranking
    ComparableMaxHeap<DoubleDBIDPair> pq = new ComparableMaxHeap<>(data.size());
    HashMap<DBID, DBIDs> explaintab = new HashMap<>();
    // test all objects
    MeanVariance s = new MeanVariance(), s2 = new MeanVariance();
    for (DBIDIter objKey = data.iterDBIDs(); objKey.valid(); objKey.advance()) {
      s.reset();
      // Queue for the best explanation
      ComparableMinHeap<DoubleDBIDPair> explain = new ComparableMinHeap<>();
      // determine Object
      // for each pair of other objects
      for (DBIDIter key1 = data.iterDBIDs(); key1.valid(); key1.advance()) {
        // Collect Explanation Vectors
        s2.reset();
        if (DBIDUtil.equal(objKey, key1)) {
          continue;
        }
        for (DBIDIter key2 = data.iterDBIDs(); key2.valid(); key2.advance()) {
          if (DBIDUtil.equal(key2, key1) || DBIDUtil.equal(objKey, key2)) {
            continue;
          }
          double nenner = calcDenominator(kernelMatrix, objKey, key1, key2);
          if (nenner != 0) {
            double tmp = calcNumerator(kernelMatrix, objKey, key1, key2) / nenner;
            double sqr = Math.sqrt(nenner);
            s2.put(tmp, 1 / sqr);
          }
        }
        explain.add(DBIDUtil.newPair(s2.getSampleVariance(), key1));
        s.put(s2);
      }
      // build variance of the observed vectors
      pq.add(DBIDUtil.newPair(s.getSampleVariance(), objKey));
      //
      ModifiableDBIDs expList = DBIDUtil.newArray();
      expList.add(explain.poll());
      while (!explain.isEmpty()) {
        DBIDRef nextKey = explain.poll();
        if (DBIDUtil.equal(nextKey, objKey)) {
          continue;
        }
        double max = Double.MIN_VALUE;
        for (DBIDIter exp = expList.iter(); exp.valid(); exp.advance()) {
          if (DBIDUtil.equal(exp, objKey) || DBIDUtil.equal(nextKey, exp)) {
            continue;
          }
          double nenner = Math.sqrt(calcCos(kernelMatrix, objKey, nextKey)) * Math.sqrt(calcCos(kernelMatrix, objKey, exp));
          double angle = calcNumerator(kernelMatrix, objKey, nextKey, exp) / nenner;
          max = Math.max(angle, max);
        }
        if (max < 0.5) {
          expList.add(nextKey);
        }
      }
      explaintab.put(DBIDUtil.deref(objKey), expList);
    }
    StringBuilder buf = new StringBuilder();
    buf.append("Result: ABOD\n");
    int count = 0;
    while (!pq.isEmpty()) {
      if (count > 10) {
        break;
      }
      double factor = pq.peek().doubleValue();
      DBIDRef key = pq.poll();
      buf.append(data.get(key)).append(' ');
      buf.append(count).append(" Factor=").append(factor).append(' ').append(key).append('\n');
      DBIDs expList = explaintab.get(key);
      generateExplanation(buf, data, key, expList);
      count++;
    }
    return buf.toString();
  }

  private void generateExplanation(StringBuilder buf, Relation<V> data, DBIDRef key, DBIDs expList) {
    Vector vect1 = data.get(key).getColumnVector();
    for (DBIDIter iter = expList.iter(); iter.valid(); iter.advance()) {
      buf.append("Outlier: ").append(vect1).append('\n');
      Vector exp = data.get(iter).getColumnVector();
      buf.append("Most common neighbor: ").append(exp).append('\n');
      // determine difference Vector
      Vector vals = exp.minus(vect1);
      buf.append(vals).append('\n');
    }
  }

  /**
   * Run ABOD on the data set.
   * 
   * @param relation Relation to process
   * @return Outlier detection result
   */
  public OutlierResult run(Relation<V> relation) {
    if (sampleSize > 0) {
      return getFastRanking(relation);
    } else {
      return getRanking(relation);
    }
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return TypeUtil.array(TypeUtil.NUMBER_VECTOR_FIELD);
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
  public static class Parameterizer<V extends NumberVector<?>> extends AbstractDistanceBasedAlgorithm.Parameterizer<V, DoubleDistance> {
    /**
     * k Parameter.
     */
    protected int k = 0;

    /**
     * Sample size.
     */
    protected int sampleSize = 0;

    /**
     * Distance function.
     */
    protected PrimitiveSimilarityFunction<V, DoubleDistance> primitiveKernelFunction = null;

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      final IntParameter kP = new IntParameter(K_ID, 30);
      kP.addConstraint(new GreaterEqualConstraint(1));
      if (config.grab(kP)) {
        k = kP.getValue();
      }
      final IntParameter sampleSizeP = new IntParameter(FAST_SAMPLE_ID);
      sampleSizeP.addConstraint(new GreaterEqualConstraint(1));
      sampleSizeP.setOptional(true);
      if (config.grab(sampleSizeP)) {
        sampleSize = sampleSizeP.getValue();
      }
      final ObjectParameter<PrimitiveSimilarityFunction<V, DoubleDistance>> param = new ObjectParameter<>(KERNEL_FUNCTION_ID, PrimitiveSimilarityFunction.class, PolynomialKernelFunction.class);
      if (config.grab(param)) {
        primitiveKernelFunction = param.instantiateClass(config);
      }
    }

    @Override
    protected ABOD<V> makeInstance() {
      return new ABOD<>(k, sampleSize, primitiveKernelFunction, distanceFunction);
    }
  }
}
