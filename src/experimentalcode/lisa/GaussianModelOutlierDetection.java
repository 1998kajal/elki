package experimentalcode.lisa;

import de.lmu.ifi.dbs.elki.algorithm.AbstractAlgorithm;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.database.AssociationID;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreFactory;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreUtil;
import de.lmu.ifi.dbs.elki.database.datastore.WritableDataStore;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.math.MinMax;
import de.lmu.ifi.dbs.elki.math.linearalgebra.Matrix;
import de.lmu.ifi.dbs.elki.math.linearalgebra.Vector;
import de.lmu.ifi.dbs.elki.result.AnnotationFromDataStore;
import de.lmu.ifi.dbs.elki.result.AnnotationResult;
import de.lmu.ifi.dbs.elki.result.OrderingFromDataStore;
import de.lmu.ifi.dbs.elki.result.OrderingResult;
import de.lmu.ifi.dbs.elki.result.outlier.BasicOutlierScoreMeta;
import de.lmu.ifi.dbs.elki.result.outlier.InvertedOutlierScoreMeta;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierResult;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierScoreMeta;
import de.lmu.ifi.dbs.elki.utilities.DatabaseUtil;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.Flag;

/**
 * Outlier have smallest GMOD_PROB: the outlier scores is the
 * <em>probability density</em> of the assumed distribution.
 * 
 * @author Lisa Reichert
 * 
 * @param <V> Vector type
 */
public class GaussianModelOutlierDetection<V extends NumberVector<V, Double>> extends AbstractAlgorithm<V, OutlierResult> {
  /**
   * OptionID for {@link #INVERT_FLAG}
   */
  public static final OptionID INVERT_ID = OptionID.getOrCreateOptionID("gaussod.invert", "Invert the value range to [0:1], with 1 being outliers instead of 0.");

  /**
   * Parameter to specify a scaling function to use.
   * <p>
   * Key: {@code -gaussod.invert}
   * </p>
   */
  private final Flag INVERT_FLAG = new Flag(INVERT_ID);

  /**
   * Invert the result
   */
  private boolean invert = false;

  OutlierResult result;

  public static final AssociationID<Double> GMOD_PROB = AssociationID.getOrCreateAssociationID("gmod.prob", Double.class);

  public GaussianModelOutlierDetection(Parameterization config) {
    super(config);
    config = config.descend(this);
    if (config.grab(INVERT_FLAG)) {
      invert = INVERT_FLAG.getValue();
    }
  }

  @Override
  protected OutlierResult runInTime(Database<V> database) throws IllegalStateException {
    MinMax<Double> mm = new MinMax<Double>();
    // resulting scores
    WritableDataStore<Double> oscores = DataStoreUtil.makeStorage(database.getIDs(), DataStoreFactory.HINT_HOT | DataStoreFactory.HINT_STATIC, Double.class);

    // Compute mean and covariance Matrix
    V mean = DatabaseUtil.centroid(database);
    // debugFine(mean.toString());
    Matrix covarianceMatrix = DatabaseUtil.covarianceMatrix(database, mean);
    // debugFine(covarianceMatrix.toString());
    Matrix covarianceTransposed = covarianceMatrix.inverse();

    // Normalization factors for Gaussian PDF
    final double fakt = (1.0 / (Math.sqrt(Math.pow(2 * Math.PI, database.dimensionality()) * covarianceMatrix.det())));

    // for each object compute Mahalanobis distance
    for(DBID id : database) {
      V x = database.get(id);
      Vector x_minus_mean = x.minus(mean).getColumnVector();
      // Gaussian PDF
      final double mDist = x_minus_mean.transposeTimes(covarianceTransposed).times(x_minus_mean).get(0, 0);
      final double prob = fakt * Math.exp(-mDist / 2.0);

      mm.put(prob);
      oscores.put(id, prob);
    }

    final OutlierScoreMeta meta;
    if (invert) {
      double max = mm.getMax() != 0 ? mm.getMax() : 1.;
      for (DBID id : database) {
        oscores.put(id, (max - oscores.get(id)) / max);
      }
      meta = new BasicOutlierScoreMeta(0.0, 1.0);
    } else {
      meta = new InvertedOutlierScoreMeta(mm.getMin(), mm.getMax(), 0.0, Double.POSITIVE_INFINITY );
    }
    AnnotationResult<Double> res1 = new AnnotationFromDataStore<Double>(GMOD_PROB, oscores);
    OrderingResult res2 = new OrderingFromDataStore<Double>(oscores);
    result = new OutlierResult(meta, res1, res2);
    return result;
  }
}
