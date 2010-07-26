package de.lmu.ifi.dbs.elki.algorithm.outlier;

import java.util.List;

import de.lmu.ifi.dbs.elki.algorithm.AbstractAlgorithm;
import de.lmu.ifi.dbs.elki.data.DatabaseObject;
import de.lmu.ifi.dbs.elki.database.AssociationID;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.DistanceResultPair;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreFactory;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreUtil;
import de.lmu.ifi.dbs.elki.database.datastore.WritableDataStore;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.query.KNNQuery;
import de.lmu.ifi.dbs.elki.database.query.PreprocessorKNNQuery;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancefunction.EuclideanDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.NumberDistance;
import de.lmu.ifi.dbs.elki.logging.progress.FiniteProgress;
import de.lmu.ifi.dbs.elki.logging.progress.StepProgress;
import de.lmu.ifi.dbs.elki.math.ErrorFunctions;
import de.lmu.ifi.dbs.elki.math.MeanVariance;
import de.lmu.ifi.dbs.elki.preprocessing.MaterializeKNNPreprocessor;
import de.lmu.ifi.dbs.elki.result.AnnotationFromDataStore;
import de.lmu.ifi.dbs.elki.result.AnnotationResult;
import de.lmu.ifi.dbs.elki.result.MultiResult;
import de.lmu.ifi.dbs.elki.result.OrderingFromDataStore;
import de.lmu.ifi.dbs.elki.result.OrderingResult;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierResult;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierScoreMeta;
import de.lmu.ifi.dbs.elki.result.outlier.ProbabilisticOutlierScore;
import de.lmu.ifi.dbs.elki.utilities.documentation.Description;
import de.lmu.ifi.dbs.elki.utilities.documentation.Reference;
import de.lmu.ifi.dbs.elki.utilities.documentation.Title;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.GreaterConstraint;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.ChainedParameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.ListParameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.ClassParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.DoubleParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.IntParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.ObjectParameter;

/**
 * LoOP: Local Outlier Probabilities
 * 
 * Distance/density based algorithm similar to LOF to detect outliers, but with
 * statistical methods to achieve better result stability.
 * 
 * @author Erich Schubert
 * @param <O> the type of DatabaseObjects handled by this Algorithm
 */
@Title("LoOP: Local Outlier Probabilities")
@Description("Variant of the LOF algorithm normalized using statistical values.")
@Reference(authors = "H.-P. Kriegel, P. Kröger, E. Schubert, A. Zimek", title = "LoOP: Local Outlier Probabilities", booktitle = "Proceedings of the 18th International Conference on Information and Knowledge Management (CIKM), Hong Kong, China, 2009", url = "http://dx.doi.org/10.1145/1645953.1646195")
public class LoOP<O extends DatabaseObject, D extends NumberDistance<D, ?>> extends AbstractAlgorithm<O, MultiResult> {
  /**
   * OptionID for {@link #REFERENCE_DISTANCE_FUNCTION_PARAM}
   */
  public static final OptionID REFERENCE_DISTANCE_FUNCTION_ID = OptionID.getOrCreateOptionID("loop.referencedistfunction", "Distance function to determine the reference set of an object.");

  /**
   * The distance function to determine the reachability distance between
   * database objects.
   * <p>
   * Default value: {@link EuclideanDistanceFunction}
   * </p>
   * <p>
   * Key: {@code -loop.referencedistfunction}
   * </p>
   */
  private final ObjectParameter<DistanceFunction<O, D>> REFERENCE_DISTANCE_FUNCTION_PARAM = new ObjectParameter<DistanceFunction<O, D>>(REFERENCE_DISTANCE_FUNCTION_ID, DistanceFunction.class, true);

  /**
   * OptionID for {@link #COMPARISON_DISTANCE_FUNCTION_PARAM}
   */
  public static final OptionID COMPARISON_DISTANCE_FUNCTION_ID = OptionID.getOrCreateOptionID("loop.comparedistfunction", "Distance function to determine the reference set of an object.");

  /**
   * The distance function to determine the reachability distance between
   * database objects.
   * <p>
   * Default value: {@link EuclideanDistanceFunction}
   * </p>
   * <p>
   * Key: {@code -loop.comparedistfunction}
   * </p>
   */
  private final ObjectParameter<DistanceFunction<O, D>> COMPARISON_DISTANCE_FUNCTION_PARAM = new ObjectParameter<DistanceFunction<O, D>>(COMPARISON_DISTANCE_FUNCTION_ID, DistanceFunction.class, EuclideanDistanceFunction.class);

  /**
   * OptionID for {@link #KNNQUERY_PARAM}
   */
  public static final OptionID PREPROCESSOR_ID = OptionID.getOrCreateOptionID("loop.knnquery", "kNN query to use");

  /**
   * The preprocessor used to materialize the kNN neighborhoods.
   * 
   * Default value: {@link MaterializeKNNPreprocessor} </p>
   * <p>
   * Key: {@code -loop.knnquery}
   * </p>
   */
  private final ClassParameter<KNNQuery<O, D>> KNNQUERY_PARAM = new ClassParameter<KNNQuery<O, D>>(PREPROCESSOR_ID, KNNQuery.class, PreprocessorKNNQuery.class);

  /**
   * The association id to associate the LOOP_SCORE of an object for the
   * LOOP_SCORE algorithm.
   */
  public static final AssociationID<Double> LOOP_SCORE = AssociationID.getOrCreateAssociationID("loop", Double.class);

  /**
   * OptionID for {@link #KCOMP_PARAM}
   */
  public static final OptionID KCOMP_ID = OptionID.getOrCreateOptionID("loop.kcomp", "The number of nearest neighbors of an object to be considered for computing its LOOP_SCORE.");

  /**
   * Parameter to specify the number of nearest neighbors of an object to be
   * considered for computing its LOOP_SCORE, must be an integer greater than 1.
   * <p>
   * Key: {@code -loop.kcomp}
   * </p>
   */
  private final IntParameter KCOMP_PARAM = new IntParameter(KCOMP_ID, new GreaterConstraint(1));

  /**
   * OptionID for {@link #KCOMP_PARAM}
   */
  public static final OptionID KREF_ID = OptionID.getOrCreateOptionID("loop.kref", "The number of nearest neighbors of an object to be used for the PRD value.");

  /**
   * Parameter to specify the number of nearest neighbors of an object to be
   * considered for computing its LOOP_SCORE, must be an integer greater than 1.
   * <p>
   * Key: {@code -loop.kref}
   * </p>
   */
  private final IntParameter KREF_PARAM = new IntParameter(KREF_ID, new GreaterConstraint(1), true);

  /**
   * OptionID for {@link #LAMBDA_PARAM}
   */
  public static final OptionID LAMBDA_ID = OptionID.getOrCreateOptionID("loop.lambda", "The number of standard deviations to consider for density computation.");

  /**
   * Parameter to specify the number of nearest neighbors of an object to be
   * considered for computing its LOOP_SCORE, must be an integer greater than 1.
   * <p>
   * Key: {@code -loop.lambda}
   * </p>
   */
  private final DoubleParameter LAMBDA_PARAM = new DoubleParameter(LAMBDA_ID, new GreaterConstraint(0.0), 2.0);

  /**
   * Holds the value of {@link #KCOMP_PARAM}.
   */
  int kcomp;

  /**
   * Holds the value of {@link #KREF_PARAM}.
   */
  int kref;

  /**
   * Hold the value of {@link #LAMBDA_PARAM}.
   */
  double lambda;

  /**
   * Preprocessor Step 1
   */
  protected KNNQuery<O, D> knnQueryCompare;

  /**
   * Preprocessor Step 2
   */
  protected KNNQuery<O, D> knnQueryReference;

  /**
   * Include object itself in kNN neighborhood.
   */
  boolean objectIsInKNN = false;

  /**
   * Constructor, adhering to
   * {@link de.lmu.ifi.dbs.elki.utilities.optionhandling.Parameterizable}
   * 
   * @param config Parameterization
   */
  public LoOP(Parameterization config) {
    super(config);
    // Lambda
    if(config.grab(LAMBDA_PARAM)) {
      lambda = LAMBDA_PARAM.getValue();
    }

    // k
    if(config.grab(KCOMP_PARAM)) {
      kcomp = KCOMP_PARAM.getValue();
    }

    // k for reference set
    if(config.grab(KREF_PARAM)) {
      kref = KREF_PARAM.getValue();
    }
    else {
      kref = kcomp;
    }

    int preprock = kcomp;

    DistanceFunction<O, D> comparisonDistanceFunction = null;
    DistanceFunction<O, D> referenceDistanceFunction = null;

    if(config.grab(COMPARISON_DISTANCE_FUNCTION_PARAM)) {
      comparisonDistanceFunction = COMPARISON_DISTANCE_FUNCTION_PARAM.instantiateClass(config);
    }

    // referenceDistanceFunction
    if(config.grab(REFERENCE_DISTANCE_FUNCTION_PARAM)) {
      referenceDistanceFunction = REFERENCE_DISTANCE_FUNCTION_PARAM.instantiateClass(config);
    }
    else {
      referenceDistanceFunction = null;
      // Adjust preprocessor k to accomodate both values
      preprock = Math.max(kcomp, kref);
    }

    // configure first preprocessor
    if(config.grab(KNNQUERY_PARAM) && COMPARISON_DISTANCE_FUNCTION_PARAM.isDefined()) {
      ListParameterization query1Params = new ListParameterization();
      query1Params.addParameter(KNNQuery.K_ID, preprock + (objectIsInKNN ? 0 : 1));
      query1Params.addParameter(KNNQuery.DISTANCE_FUNCTION_ID, comparisonDistanceFunction);
      ChainedParameterization chain = new ChainedParameterization(query1Params, config);
      // chain.errorsTo(config);
      knnQueryCompare = KNNQUERY_PARAM.instantiateClass(chain);
      query1Params.reportInternalParameterizationErrors(config);

      if(referenceDistanceFunction != null) {
        // configure second preprocessor
        ListParameterization query2Params = new ListParameterization();
        query2Params.addParameter(KNNQuery.K_ID, kref + (objectIsInKNN ? 0 : 1));
        query2Params.addParameter(KNNQuery.DISTANCE_FUNCTION_ID, referenceDistanceFunction);
        ChainedParameterization chain2 = new ChainedParameterization(query2Params, config);
        // chain2.errorsTo(config);
        knnQueryReference = KNNQUERY_PARAM.instantiateClass(chain2);
        query2Params.reportInternalParameterizationErrors(config);
      }
    }
  }

  /**
   * Performs the LoOP algorithm on the given database.
   */
  @Override
  protected OutlierResult runInTime(Database<O> database) throws IllegalStateException {
    final double sqrt2 = Math.sqrt(2.0);

    StepProgress stepprog = logger.isVerbose() ? new StepProgress(5) : null;

    // neighborhoods queries
    KNNQuery.Instance<O, D> neighcompare;
    KNNQuery.Instance<O, D> neighref;

    neighcompare = knnQueryCompare.instantiate(database);
    if(stepprog != null) {
      stepprog.beginStep(1, "Materializing neighborhoods with respect to reachability distance.", logger);
    }
    if(REFERENCE_DISTANCE_FUNCTION_PARAM.isDefined()) {
      if(stepprog != null) {
        stepprog.beginStep(2, "Materializing neighborhoods for (separate) reference set function.", logger);
      }
      neighref = knnQueryReference.instantiate(database);
    }
    else {
      if(stepprog != null) {
        stepprog.beginStep(2, "Re-using the neighborhoods.", logger);
      }
      neighref = neighcompare;
    }

    // Probabilistic distances
    WritableDataStore<Double> pdists = DataStoreUtil.makeStorage(database.getIDs(), DataStoreFactory.HINT_HOT | DataStoreFactory.HINT_TEMP, Double.class);
    {// computing PRDs
      if(stepprog != null) {
        stepprog.beginStep(3, "Computing pdists", logger);
      }
      FiniteProgress prdsProgress = logger.isVerbose() ? new FiniteProgress("pdists", database.size(), logger) : null;
      int counter = 0;
      for(DBID id : database) {
        counter++;
        List<DistanceResultPair<D>> neighbors = neighref.get(id);
        double sqsum = 0.0;
        // use first kref neighbors as reference set
        int ks = 0;
        for(DistanceResultPair<D> neighbor : neighbors) {
          if(objectIsInKNN || neighbor.getID() != id) {
            double d = neighbor.getDistance().doubleValue();
            sqsum += d * d;
            ks++;
            if(ks >= kref) {
              break;
            }
          }
        }
        Double pdist = lambda * Math.sqrt(sqsum / ks);
        pdists.put(id, pdist);
        if(prdsProgress != null) {
          prdsProgress.setProcessed(counter, logger);
        }
      }
    }
    // Compute PLOF values.
    WritableDataStore<Double> plofs = DataStoreUtil.makeStorage(database.getIDs(), DataStoreFactory.HINT_HOT | DataStoreFactory.HINT_TEMP, Double.class);
    MeanVariance mvplof = new MeanVariance();
    {// compute LOOP_SCORE of each db object
      if(stepprog != null) {
        stepprog.beginStep(4, "Computing PLOF", logger);
      }

      FiniteProgress progressPLOFs = logger.isVerbose() ? new FiniteProgress("PLOFs for objects", database.size(), logger) : null;
      int counter = 0;
      for(DBID id : database) {
        counter++;
        List<DistanceResultPair<D>> neighbors = neighcompare.get(id);
        MeanVariance mv = new MeanVariance();
        // use first kref neighbors as comparison set.
        int ks = 0;
        for(DistanceResultPair<D> neighbor1 : neighbors) {
          if(objectIsInKNN || neighbor1.getID() != id) {
            mv.put(pdists.get(neighbor1.getSecond()));
            ks++;
            if(ks >= kcomp) {
              break;
            }
          }
        }
        double plof = Math.max(pdists.get(id) / mv.getMean(), 1.0);
        if(Double.isNaN(plof) || Double.isInfinite(plof)) {
          plof = 1.0;
        }
        plofs.put(id, plof);
        mvplof.put((plof - 1.0) * (plof - 1.0));

        if(progressPLOFs != null) {
          progressPLOFs.setProcessed(counter, logger);
        }
      }
    }

    double nplof = lambda * Math.sqrt(mvplof.getMean());
    if(logger.isDebugging()) {
      logger.verbose("nplof normalization factor is " + nplof + " " + mvplof.getMean() + " " + mvplof.getStddev());
    }

    // Compute final LoOP values.
    WritableDataStore<Double> loops = DataStoreUtil.makeStorage(database.getIDs(), DataStoreFactory.HINT_STATIC, Double.class);
    {// compute LOOP_SCORE of each db object
      if(stepprog != null) {
        stepprog.beginStep(5, "Computing LoOP scores", logger);
      }

      FiniteProgress progressLOOPs = logger.isVerbose() ? new FiniteProgress("LoOP for objects", database.size(), logger) : null;
      int counter = 0;
      for(DBID id : database) {
        counter++;
        List<DistanceResultPair<D>> neighbors = neighcompare.get(id);
        MeanVariance mv = new MeanVariance();
        // use first kref neighbors as comparison set.
        int ks = 0;
        for(DistanceResultPair<D> neighbor1 : neighbors) {
          if(objectIsInKNN || neighbor1.getID() != id) {
            mv.put(pdists.get(neighbor1.getSecond()));
            ks++;
            if(ks >= kcomp) {
              break;
            }
          }
        }
        double loop = Math.max(pdists.get(id) / mv.getMean(), 1.0);
        if(Double.isNaN(loop) || Double.isInfinite(loop)) {
          loop = 1.0;
        }
        loops.put(id, ErrorFunctions.erf((loop - 1) / (nplof * sqrt2)));

        if(progressLOOPs != null) {
          progressLOOPs.setProcessed(counter, logger);
        }
      }
    }

    if(stepprog != null) {
      stepprog.setCompleted(logger);
    }

    // Build result representation.
    AnnotationResult<Double> scoreResult = new AnnotationFromDataStore<Double>(LOOP_SCORE, loops);
    OrderingResult orderingResult = new OrderingFromDataStore<Double>(loops, true);
    OutlierScoreMeta scoreMeta = new ProbabilisticOutlierScore();
    return new OutlierResult(scoreMeta, scoreResult, orderingResult);
  }
}