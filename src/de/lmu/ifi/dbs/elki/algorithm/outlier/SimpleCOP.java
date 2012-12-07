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

import de.lmu.ifi.dbs.elki.algorithm.AbstractDistanceBasedAlgorithm;
import de.lmu.ifi.dbs.elki.algorithm.DependencyDerivator;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.model.CorrelationAnalysisSolution;
import de.lmu.ifi.dbs.elki.data.type.SimpleTypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.QueryUtil;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreFactory;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreUtil;
import de.lmu.ifi.dbs.elki.database.datastore.WritableDataStore;
import de.lmu.ifi.dbs.elki.database.datastore.WritableDoubleDataStore;
import de.lmu.ifi.dbs.elki.database.datastore.WritableIntegerDataStore;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.ids.ModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.query.knn.KNNQuery;
import de.lmu.ifi.dbs.elki.database.relation.MaterializedRelation;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distanceresultlist.KNNResult;
import de.lmu.ifi.dbs.elki.distance.distancevalue.NumberDistance;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.progress.FiniteProgress;
import de.lmu.ifi.dbs.elki.math.linearalgebra.Matrix;
import de.lmu.ifi.dbs.elki.math.linearalgebra.Vector;
import de.lmu.ifi.dbs.elki.math.linearalgebra.pca.PCAFilteredRunner;
import de.lmu.ifi.dbs.elki.math.statistics.distribution.NormalDistribution;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierResult;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierScoreMeta;
import de.lmu.ifi.dbs.elki.result.outlier.ProbabilisticOutlierScore;
import de.lmu.ifi.dbs.elki.utilities.FormatUtil;
import de.lmu.ifi.dbs.elki.utilities.documentation.Reference;
import de.lmu.ifi.dbs.elki.utilities.documentation.Title;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.GreaterConstraint;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.IntParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.ObjectParameter;

/**
 * Algorithm to compute local correlation outlier probability.
 * 
 * This is the simpler, original version of COP, as published in
 * <p>
 * Arthur Zimek<br />
 * Correlation Clustering.<br />
 * PhD thesis, Chapter 18
 * </p>
 * which has then been refined to the method published as {@link COP}
 * 
 * @author Erich Schubert
 * @param <V> the type of NumberVector handled by this Algorithm
 */
@Title("Simple COP: Correlation Outlier Probability")
@Reference(authors = "Arthur Zimek", title = "Correlation Clustering. PhD thesis, Chapter 18", booktitle = "")
public class SimpleCOP<V extends NumberVector<?>, D extends NumberDistance<D, ?>> extends AbstractDistanceBasedAlgorithm<V, D, OutlierResult> implements OutlierAlgorithm {
  /**
   * The logger for this class.
   */
  private static final Logging LOG = Logging.getLogger(SimpleCOP.class);

  /**
   * Number of neighbors to be considered.
   */
  int k;

  /**
   * Holds the object performing the dependency derivation
   */
  private DependencyDerivator<V, D> dependencyDerivator;

  /**
   * Constructor.
   * 
   * @param distanceFunction Distance function
   * @param k k Parameter
   * @param pca PCA runner-
   */
  public SimpleCOP(DistanceFunction<? super V, D> distanceFunction, int k, PCAFilteredRunner<V> pca) {
    super(distanceFunction);
    this.k = k;
    this.dependencyDerivator = new DependencyDerivator<V, D>(null, FormatUtil.NF8, pca, 0, false);
  }

  public OutlierResult run(Database database, Relation<V> data) throws IllegalStateException {
    KNNQuery<V, D> knnQuery = QueryUtil.getKNNQuery(data, getDistanceFunction(), k + 1);

    DBIDs ids = data.getDBIDs();

    WritableDoubleDataStore cop_score = DataStoreUtil.makeDoubleStorage(ids, DataStoreFactory.HINT_HOT | DataStoreFactory.HINT_STATIC);
    WritableDataStore<Vector> cop_err_v = DataStoreUtil.makeStorage(ids, DataStoreFactory.HINT_HOT | DataStoreFactory.HINT_STATIC, Vector.class);
    WritableDataStore<Matrix> cop_datav = DataStoreUtil.makeStorage(ids, DataStoreFactory.HINT_HOT | DataStoreFactory.HINT_STATIC, Matrix.class);
    WritableIntegerDataStore cop_dim = DataStoreUtil.makeIntegerStorage(ids, DataStoreFactory.HINT_HOT | DataStoreFactory.HINT_STATIC, -1);
    WritableDataStore<CorrelationAnalysisSolution<?>> cop_sol = DataStoreUtil.makeStorage(ids, DataStoreFactory.HINT_HOT | DataStoreFactory.HINT_STATIC, CorrelationAnalysisSolution.class);
    {// compute neighbors of each db object
      FiniteProgress progressLocalPCA = LOG.isVerbose() ? new FiniteProgress("Correlation Outlier Probabilities", data.size(), LOG) : null;
      double sqrt2 = Math.sqrt(2.0);
      for (DBIDIter id = data.iterDBIDs(); id.valid(); id.advance()) {
        KNNResult<D> neighbors = knnQuery.getKNNForDBID(id, k + 1);
        ModifiableDBIDs nids = DBIDUtil.newArray(neighbors);
        nids.remove(id);

        // TODO: do we want to use the query point as centroid?
        CorrelationAnalysisSolution<V> depsol = dependencyDerivator.generateModel(data, nids);

        double stddev = depsol.getStandardDeviation();
        double distance = depsol.distance(data.get(id));
        double prob = NormalDistribution.erf(distance / (stddev * sqrt2));

        cop_score.putDouble(id, prob);

        Vector errv = depsol.errorVector(data.get(id)).timesEquals(-1);
        cop_err_v.put(id, errv);

        Matrix datav = depsol.dataProjections(data.get(id));
        cop_datav.put(id, datav);

        cop_dim.putInt(id, depsol.getCorrelationDimensionality());

        cop_sol.put(id, depsol);

        if (progressLocalPCA != null) {
          progressLocalPCA.incrementProcessed(LOG);
        }
      }
      if (progressLocalPCA != null) {
        progressLocalPCA.ensureCompleted(LOG);
      }
    }
    // combine results.
    Relation<Double> scoreResult = new MaterializedRelation<Double>("Original Correlation Outlier Probabilities", "origcop-outlier", TypeUtil.DOUBLE, cop_score, ids);
    OutlierScoreMeta scoreMeta = new ProbabilisticOutlierScore();
    OutlierResult result = new OutlierResult(scoreMeta, scoreResult);
    // extra results
    result.addChildResult(new MaterializedRelation<Integer>("Local Dimensionality", COP.COP_DIM, TypeUtil.INTEGER, cop_dim, ids));
    result.addChildResult(new MaterializedRelation<Vector>("Error vectors", COP.COP_ERRORVEC, TypeUtil.VECTOR, cop_err_v, ids));
    result.addChildResult(new MaterializedRelation<Matrix>("Data vectors", "cop-datavec", TypeUtil.MATRIX, cop_datav, ids));
    result.addChildResult(new MaterializedRelation<CorrelationAnalysisSolution<?>>("Correlation analysis", "cop-sol", new SimpleTypeInformation<CorrelationAnalysisSolution<?>>(CorrelationAnalysisSolution.class), cop_sol, ids));
    return result;
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
  public static class Parameterizer<V extends NumberVector<?>, D extends NumberDistance<D, ?>> extends AbstractDistanceBasedAlgorithm.Parameterizer<V, D> {
    /**
     * Parameter to specify the number of nearest neighbors of an object to be
     * considered for computing its COP_SCORE, must be an integer greater than
     * 0.
     * <p/>
     * Key: {@code -cop.k}
     * </p>
     */
    public static final OptionID K_ID = new OptionID("cop.k", "The number of nearest neighbors of an object to be considered for computing its COP_SCORE.");

    /**
     * Parameter for the PCA runner class.
     * 
     * <p>
     * Key: {@code -cop.pcarunner}
     * </p>
     */
    public static final OptionID PCARUNNER_ID = new OptionID("cop.pcarunner", "The class to compute (filtered) PCA.");

    /**
     * Number of neighbors to be considered.
     */
    int k;

    /**
     * Holds the object performing the dependency derivation
     */
    protected PCAFilteredRunner<V> pca;

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      IntParameter kP = new IntParameter(K_ID);
      kP.addConstraint(new GreaterConstraint(0));
      if (config.grab(kP)) {
        k = kP.intValue();
      }
      ObjectParameter<PCAFilteredRunner<V>> pcaP = new ObjectParameter<PCAFilteredRunner<V>>(PCARUNNER_ID, PCAFilteredRunner.class, PCAFilteredRunner.class);
      if (config.grab(pcaP)) {
        pca = pcaP.instantiateClass(config);
      }
    }

    @Override
    protected SimpleCOP<V, D> makeInstance() {
      return new SimpleCOP<V, D>(distanceFunction, k, pca);
    }
  }
}
