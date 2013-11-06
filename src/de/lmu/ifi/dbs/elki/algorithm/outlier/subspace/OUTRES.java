package de.lmu.ifi.dbs.elki.algorithm.outlier.subspace;

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

import java.util.Arrays;
import java.util.BitSet;

import de.lmu.ifi.dbs.elki.algorithm.AbstractAlgorithm;
import de.lmu.ifi.dbs.elki.algorithm.outlier.OutlierAlgorithm;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.QueryUtil;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreFactory;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreUtil;
import de.lmu.ifi.dbs.elki.database.datastore.WritableDoubleDataStore;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDRef;
import de.lmu.ifi.dbs.elki.database.ids.distance.DistanceDBIDList;
import de.lmu.ifi.dbs.elki.database.ids.distance.DistanceDBIDPair;
import de.lmu.ifi.dbs.elki.database.ids.distance.DistanceDBIDListIter;
import de.lmu.ifi.dbs.elki.database.ids.distance.DoubleDistanceDBIDList;
import de.lmu.ifi.dbs.elki.database.ids.distance.DoubleDistanceDBIDPair;
import de.lmu.ifi.dbs.elki.database.ids.distance.DoubleDistanceDBIDPairList;
import de.lmu.ifi.dbs.elki.database.ids.distance.DoubleDistanceDBIDListIter;
import de.lmu.ifi.dbs.elki.database.ids.distance.ModifiableDoubleDistanceDBIDList;
import de.lmu.ifi.dbs.elki.database.query.range.RangeQuery;
import de.lmu.ifi.dbs.elki.database.relation.MaterializedRelation;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.database.relation.RelationUtil;
import de.lmu.ifi.dbs.elki.distance.distancefunction.PrimitiveDoubleDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancefunction.subspace.SubspaceEuclideanDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.DoubleDistance;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.progress.FiniteProgress;
import de.lmu.ifi.dbs.elki.math.DoubleMinMax;
import de.lmu.ifi.dbs.elki.math.MathUtil;
import de.lmu.ifi.dbs.elki.math.MeanVariance;
import de.lmu.ifi.dbs.elki.math.statistics.distribution.GammaDistribution;
import de.lmu.ifi.dbs.elki.math.statistics.kernelfunctions.EpanechnikovKernelDensityFunction;
import de.lmu.ifi.dbs.elki.math.statistics.kernelfunctions.KernelDensityFunction;
import de.lmu.ifi.dbs.elki.result.outlier.InvertedOutlierScoreMeta;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierResult;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierScoreMeta;
import de.lmu.ifi.dbs.elki.utilities.documentation.Reference;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.DoubleParameter;

/**
 * Adaptive outlierness for subspace outlier ranking (OUTRES).
 * 
 * Note: this algorithm seems to have a O(n^3) complexity without appropriate
 * index structures to accelerate range queries: each object in each tested
 * subspace will need to know the mean and standard deviation of the density of
 * the neighbors, which in turn needs another range query.
 * 
 * Reference:
 * <p>
 * E. Müller, M. Schiffer, T. Seidl<br />
 * Adaptive outlierness for subspace outlier ranking<br />
 * in: Proc. 19th ACM International Conference on Information and knowledge
 * management
 * </p>
 * 
 * @author Viktoria Pleintinger
 * @author Erich Schubert
 * 
 * @apiviz.composedOf KernelDensityEstimator
 * 
 * @param <V> vector type
 */
@Reference(authors = "E. Müller, M. Schiffer, T. Seidl", title = "Adaptive outlierness for subspace outlier ranking", booktitle = "Proc. 19th ACM International Conference on Information and knowledge management")
public class OUTRES<V extends NumberVector<?>> extends AbstractAlgorithm<OutlierResult> implements OutlierAlgorithm {
  /**
   * The logger for this class.
   */
  private static final Logging LOG = Logging.getLogger(OUTRES.class);

  /**
   * The epsilon (in 2d) parameter
   */
  private final double eps;

  /**
   * Constant for Kolmogorov-Smirnov at alpha=0.01 (table value)
   */
  private static final double K_S_CRITICAL001 = 1.63;

  /**
   * Constructor.
   * 
   * @param eps Epsilon
   */
  public OUTRES(double eps) {
    super();
    this.eps = eps;
  }

  /**
   * Main loop for OUTRES
   * 
   * @param relation Relation to process
   * @return Outlier detection result
   */
  public OutlierResult run(Relation<V> relation) {
    WritableDoubleDataStore ranks = DataStoreUtil.makeDoubleStorage(relation.getDBIDs(), DataStoreFactory.HINT_STATIC);
    DoubleMinMax minmax = new DoubleMinMax();

    KernelDensityEstimator kernel = new KernelDensityEstimator(relation);
    BitSet subspace = new BitSet(kernel.dim);

    FiniteProgress progress = LOG.isVerbose() ? new FiniteProgress("OUTRES scores", relation.size(), LOG) : null;

    for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
      subspace.clear();
      double score = outresScore(0, subspace, iditer, kernel);
      ranks.putDouble(iditer, score);
      minmax.put(score);
      if(progress != null) {
        progress.incrementProcessed(LOG);
      }
    }
    if(progress != null) {
      progress.ensureCompleted(LOG);
    }

    OutlierScoreMeta meta = new InvertedOutlierScoreMeta(minmax.getMin(), minmax.getMax(), 0., 1., 1.);
    OutlierResult outresResult = new OutlierResult(meta, new MaterializedRelation<>("OUTRES", "outres-score", TypeUtil.DOUBLE, ranks, relation.getDBIDs()));
    return outresResult;
  }

  /**
   * Main loop of OUTRES. Run for each object
   * 
   * @param s start dimension
   * @param subspace Current subspace
   * @param id Current object ID
   * @param kernel Kernel
   * @return Score
   */
  public double outresScore(final int s, BitSet subspace, DBIDRef id, KernelDensityEstimator kernel) {
    double score = 1.0; // Initial score is 1.0
    final SubspaceEuclideanDistanceFunction df = new SubspaceEuclideanDistanceFunction(subspace);
    MeanVariance meanv = new MeanVariance();

    for(int i = s; i < kernel.dim; i++) {
      if(subspace.get(i)) { // TODO: needed? Or should we always start with i=0?
        continue;
      }
      subspace.set(i);
      df.setSelectedDimensions(subspace);
      final double adjustedEps = kernel.adjustedEps(kernel.dim);
      // Query with a larger window, to also get neighbors of neighbors
      // Subspace euclidean is metric!
      final DoubleDistance range = new DoubleDistance(adjustedEps * 2.);
      RangeQuery<V, DoubleDistance> rq = QueryUtil.getRangeQuery(kernel.relation, df, range);

      DistanceDBIDList<DoubleDistance> neighc = rq.getRangeForDBID(id, range);
      DoubleDistanceDBIDList neigh = refineRange(neighc, adjustedEps);
      if(neigh.size() > 2) {
        // Relevance test
        if(relevantSubspace(subspace, neigh, kernel)) {
          final double density = kernel.subspaceDensity(subspace, neigh);
          // Compute mean and standard deviation for densities of neighbors.
          meanv.reset();
          for (DoubleDistanceDBIDListIter neighbor = neigh.iter(); neighbor.valid(); neighbor.advance()) {
            DoubleDistanceDBIDList n2 = subsetNeighborhoodQuery(neighc, neighbor, df, adjustedEps, kernel);
            meanv.put(kernel.subspaceDensity(subspace, n2));
          }
          final double deviation = (meanv.getMean() - density) / (2. * meanv.getSampleStddev());
          // High deviation:
          if(deviation >= 1) {
            score *= (density / deviation);
          }
          // Recursion
          score *= outresScore(i + 1, subspace, id, kernel);
        }
      }
      subspace.clear(i);
    }
    return score;
  }

  /**
   * Refine a range query.
   * 
   * @param neighc Original result
   * @param adjustedEps New epsilon
   * @return refined list
   */
  private DoubleDistanceDBIDList refineRange(DistanceDBIDList<DoubleDistance> neighc, double adjustedEps) {
    ModifiableDoubleDistanceDBIDList n = new DoubleDistanceDBIDPairList(neighc.size());
    // We don't have a guarantee for this list to be sorted
    for (DistanceDBIDListIter<DoubleDistance> neighbor = neighc.iter(); neighbor.valid(); neighbor.advance()) {
      DistanceDBIDPair<DoubleDistance> p = neighbor.getDistancePair();
      if(p instanceof DoubleDistanceDBIDPair) {
        if(((DoubleDistanceDBIDPair) p).doubleDistance() <= adjustedEps) {
          n.add((DoubleDistanceDBIDPair) p);
        }
      }
      else {
        double dist = p.getDistance().doubleValue();
        if(dist <= adjustedEps) {
          n.add(dist, p);
        }
      }
    }
    return n;
  }

  /**
   * Refine neighbors within a subset.
   * 
   * @param neighc Neighbor candidates
   * @param dbid Query object
   * @param df distance function
   * @param adjustedEps Epsilon range
   * @param kernel Kernel
   * @return Neighbors of neighbor object
   */
  private DoubleDistanceDBIDList subsetNeighborhoodQuery(DistanceDBIDList<DoubleDistance> neighc, DBIDRef dbid, PrimitiveDoubleDistanceFunction<? super V> df, double adjustedEps, KernelDensityEstimator kernel) {
    ModifiableDoubleDistanceDBIDList n = new DoubleDistanceDBIDPairList(neighc.size());
    V query = kernel.relation.get(dbid);
    for (DistanceDBIDListIter<DoubleDistance> neighbor = neighc.iter(); neighbor.valid(); neighbor.advance()) {
      DistanceDBIDPair<DoubleDistance> p = neighbor.getDistancePair();
      double dist = df.doubleDistance(query, kernel.relation.get(p));
      if(dist <= adjustedEps) {
        n.add(dist, p);
      }
    }
    return n;
  }

  /**
   * Subspace relevance test.
   * 
   * @param subspace Subspace to test
   * @param neigh Neighbor list
   * @param kernel Kernel density estimator
   * @return relevance test result
   */
  protected boolean relevantSubspace(BitSet subspace, DoubleDistanceDBIDList neigh, KernelDensityEstimator kernel) {
    Relation<V> relation = kernel.relation;
    final double crit = K_S_CRITICAL001 / Math.sqrt(neigh.size());

    for(int dim = subspace.nextSetBit(0); dim > 0; dim = subspace.nextSetBit(dim + 1)) {
      // TODO: can we save this copy somehow?
      double[] data = new double[neigh.size()];
      {
        int count = 0;
        for (DBIDIter neighbor = neigh.iter(); neighbor.valid(); neighbor.advance()) {
          V vector = relation.get(neighbor);
          data[count] = vector.doubleValue(dim);
          count++;
        }
        assert (count == neigh.size());
      }
      Arrays.sort(data);

      final double norm = data[data.length - 1] - data[0];
      final double min = data[0];

      // Kolmogorow-Smirnow-Test against uniform distribution:
      for(int j = 1; j < data.length - 2; j++) {
        double delta = (j / (data.length - 1.)) - ((data[j] - min) / norm);
        if(Math.abs(delta) > crit) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Kernel density estimation and utility class.
   * 
   * @author Erich Schubert
   */
  protected class KernelDensityEstimator {
    /**
     * Actual kernel in use
     */
    final KernelDensityFunction kernel = EpanechnikovKernelDensityFunction.KERNEL;

    /**
     * Relation to retrieve data from
     */
    final Relation<V> relation;

    /**
     * Epsilon values for different subspace dimensionalities
     */
    final double[] epsilons;

    /**
     * Optimal bandwidth for a dimensionality of 2
     */
    final double hopttwo;

    /**
     * Dimensionality of data set
     */
    final int dim;

    /**
     * Constructor.
     * 
     * @param relation Relation to apply to
     */
    public KernelDensityEstimator(Relation<V> relation) {
      super();
      this.relation = relation;
      dim = RelationUtil.dimensionality(relation);
      hopttwo = optimalBandwidth(2);
      epsilons = new double[dim + 1];
      Arrays.fill(epsilons, Double.NEGATIVE_INFINITY);
      epsilons[2] = OUTRES.this.eps;
    }

    /**
     * Compute density in the given subspace.
     * 
     * @param subspace Subspace
     * @param neighbors Neighbor distance list
     * @return Density
     */
    protected double subspaceDensity(BitSet subspace, DoubleDistanceDBIDList neighbors) {
      final double bandwidth = optimalBandwidth(subspace.cardinality());

      double density = 0;
      for (DoubleDistanceDBIDListIter neighbor = neighbors.iter(); neighbor.valid(); neighbor.advance()) {
        double v = neighbor.doubleDistance() / bandwidth;
        if(v < 1) {
          density += 1 - (v * v);
        }
      }

      return density / relation.size();
    }

    /**
     * Compute optimal kernel bandwidth
     * 
     * @param dim Dimensionality of subspace
     * @return optimal bandwidth
     */
    protected double optimalBandwidth(int dim) {
      // Pi in the publication is redundant and cancels out!
      double hopt = 8 * GammaDistribution.gamma(dim / 2.0 + 1) * (dim + 4) * MathUtil.powi(2, dim);
      return hopt * Math.pow(relation.size(), (-1. / (dim + 4)));
    }

    /**
     * Rescale the query radius based on the given dimensionality.
     * 
     * @param dim Dimensionality
     * @return Query radius
     */
    protected double adjustedEps(int dim) {
      // Cached
      double e = epsilons[dim];
      if(e < 0) {
        e = epsilons[2] * optimalBandwidth(dim) / hopttwo;
        epsilons[dim] = e;
      }
      return e;
    }
  }

  @Override
  protected Logging getLogger() {
    return LOG;
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return TypeUtil.array(TypeUtil.NUMBER_VECTOR_FIELD);
  }

  /**
   * Parameterization class.
   * 
   * @author Viktoria Pleintinger
   * 
   * @apiviz.exclude
   */
  public static class Parameterizer<O extends NumberVector<?>> extends AbstractParameterizer {
    /**
     * Option ID for Epsilon parameter
     */
    public static final OptionID D_ID = new OptionID("outres.epsilon", "Range value for OUTRES in 2 dimensions.");

    /**
     * Query radius
     */
    protected double eps;

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      final DoubleParameter param = new DoubleParameter(D_ID);
      if(config.grab(param)) {
        eps = param.getValue();
      }
    }

    @Override
    protected OUTRES<O> makeInstance() {
      return new OUTRES<>(eps);
    }
  }
}