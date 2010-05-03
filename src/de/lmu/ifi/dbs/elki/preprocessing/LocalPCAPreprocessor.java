package de.lmu.ifi.dbs.elki.preprocessing;

import java.util.List;

import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.DistanceResultPair;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreFactory;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreUtil;
import de.lmu.ifi.dbs.elki.database.datastore.WritableDataStore;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancefunction.EuclideanDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.DoubleDistance;
import de.lmu.ifi.dbs.elki.logging.AbstractLoggable;
import de.lmu.ifi.dbs.elki.logging.progress.FiniteProgress;
import de.lmu.ifi.dbs.elki.math.linearalgebra.pca.PCAFilteredResult;
import de.lmu.ifi.dbs.elki.math.linearalgebra.pca.PCAFilteredRunner;
import de.lmu.ifi.dbs.elki.utilities.ExceptionMessages;
import de.lmu.ifi.dbs.elki.utilities.documentation.Description;
import de.lmu.ifi.dbs.elki.utilities.documentation.Title;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.ObjectParameter;

/**
 * Abstract superclass for preprocessors performing for each object of a certain
 * database a filtered PCA based on the local neighborhood of the object.
 * 
 * @author Elke Achtert
 * @param <V> the type of NumberVector handled by this Preprocessor
 */
@Title("Local PCA Preprocessor")
@Description("Materializes the local PCA and the locally weighted matrix of objects of a database.")
public abstract class LocalPCAPreprocessor<V extends NumberVector<V, ?>> extends AbstractLoggable implements LocalProjectionPreprocessor<V, PCAFilteredResult> {
  /**
   * The default distance function for the PCA.
   */
  public static final Class<?> DEFAULT_PCA_DISTANCE_FUNCTION = EuclideanDistanceFunction.class;

  /**
   * OptionID for {@link #PCA_DISTANCE_PARAM}
   */
  public static final OptionID PCA_DISTANCE_ID = OptionID.getOrCreateOptionID("localpca.distancefunction", "The distance function used to select objects for running PCA.");

  /**
   * Parameter to specify the distance function used for running PCA.
   * 
   * Key: {@code -localpca.distancefunction}
   */
  protected final ObjectParameter<DistanceFunction<V, DoubleDistance>> PCA_DISTANCE_PARAM = new ObjectParameter<DistanceFunction<V, DoubleDistance>>(PCA_DISTANCE_ID, DistanceFunction.class, DEFAULT_PCA_DISTANCE_FUNCTION);

  /**
   * Holds the instance of the distance function specified by
   * {@link #PCA_DISTANCE_PARAM}.
   */
  protected DistanceFunction<V, DoubleDistance> pcaDistanceFunction;

  /**
   * PCA utility object.
   */
  private PCAFilteredRunner<V, DoubleDistance> pca;

  /**
   * Storage for the precomputed results.
   */
  private WritableDataStore<PCAFilteredResult> pcaStorage = null;

  /**
   * Constructor, adhering to
   * {@link de.lmu.ifi.dbs.elki.utilities.optionhandling.Parameterizable}
   * 
   * @param config Parameterization
   */
  public LocalPCAPreprocessor(Parameterization config) {
    super();

    // parameter pca distance function
    if(config.grab(PCA_DISTANCE_PARAM)) {
      pcaDistanceFunction = PCA_DISTANCE_PARAM.instantiateClass(config);
    }

    pca = new PCAFilteredRunner<V, DoubleDistance>(config);
  }

  /**
   * Performs for each object of the specified database a filtered PCA based on
   * the local neighborhood of the object.
   * 
   * @param database the database for which the preprocessing is performed
   */
  public void run(Database<V> database) {
    if(database == null || database.size() <= 0) {
      throw new IllegalArgumentException(ExceptionMessages.DATABASE_EMPTY);
    }
    
    // Note: this is required for ERiC to work properly, otherwise the data is recomputed for the partitions!
    if (pcaStorage != null) {
      return;
    }

    pcaStorage = DataStoreUtil.makeStorage(database.getIDs(), DataStoreFactory.HINT_HOT | DataStoreFactory.HINT_TEMP, PCAFilteredResult.class);

    long start = System.currentTimeMillis();
    FiniteProgress progress = logger.isVerbose() ? new FiniteProgress("Performing local PCA", database.size(), logger) : null;

    for(DBID id :database) {
      List<DistanceResultPair<DoubleDistance>> objects = objectsForPCA(id, database);

      PCAFilteredResult pcares = pca.processQueryResult(objects, database);

      pcaStorage.put(id, pcares);

      if(progress != null) {
        progress.incrementProcessed(logger);
      }
    }
    if(progress != null) {
      progress.ensureCompleted(logger);
    }

    long end = System.currentTimeMillis();
    if(logger.isVerbose()) {
      long elapsedTime = end - start;
      logger.verbose(this.getClass().getName() + " runtime: " + elapsedTime + " milliseconds.");
    }
  }

  /**
   * Returns the objects to be considered within the PCA for the specified query
   * object.
   * 
   * @param id the id of the query object for which a PCA should be performed
   * @param database the database holding the objects
   * @return the list of the objects (i.e. the ids and the distances to the
   *         query object) to be considered within the PCA
   */
  protected abstract List<DistanceResultPair<DoubleDistance>> objectsForPCA(DBID id, Database<V> database);

  /**
   * Get the precomputed local PCA for a particular object ID.
   * 
   * @param objid Object ID
   * @return Matrix
   */
  public PCAFilteredResult get(DBID objid) {
    return pcaStorage.get(objid);
  }
}