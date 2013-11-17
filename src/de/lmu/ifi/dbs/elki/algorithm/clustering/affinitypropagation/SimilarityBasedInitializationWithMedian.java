package de.lmu.ifi.dbs.elki.algorithm.clustering.affinitypropagation;

import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.ids.ArrayDBIDs;
import de.lmu.ifi.dbs.elki.database.ids.DBIDArrayIter;
import de.lmu.ifi.dbs.elki.database.query.similarity.SimilarityQuery;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.distance.distancevalue.NumberDistance;
import de.lmu.ifi.dbs.elki.distance.similarityfunction.SimilarityFunction;
import de.lmu.ifi.dbs.elki.distance.similarityfunction.kernel.LinearKernelFunction;
import de.lmu.ifi.dbs.elki.utilities.datastructures.QuickSelect;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.DoubleParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.ObjectParameter;

/**
 * Similarity based initialization.
 * 
 * @author Erich Schubert
 * 
 * @param <O> Object type
 * @param <D> Distance type
 */
public class SimilarityBasedInitializationWithMedian<O, D extends NumberDistance<D, ?>> implements AffinityPropagationInitialization<O> {
  /**
   * Similarity function.
   */
  SimilarityFunction<? super O, D> similarity;

  /**
   * Quantile to use.
   */
  double quantile;

  /**
   * Constructor.
   * 
   * @param similarity Similarity function
   * @param quantile Quantile
   */
  public SimilarityBasedInitializationWithMedian(SimilarityFunction<? super O, D> similarity, double quantile) {
    super();
    this.similarity = similarity;
    this.quantile = quantile;
  }

  @Override
  public double[][] getSimilarityMatrix(Database db, Relation<O> relation, ArrayDBIDs ids) {
    final int size = ids.size();
    SimilarityQuery<O, D> sq = db.getSimilarityQuery(relation, similarity);
    double[][] mat = new double[size][size];
    double[] flat = new double[(size * (size - 1)) >> 1];
    // TODO: optimize for double valued primitive distances.
    DBIDArrayIter i1 = ids.iter(), i2 = ids.iter();
    // Compute self-similarities first, for centering:
    for(int i = 0; i < size; i++, i1.advance()) {
      mat[i][i] = sq.similarity(i1, i1).doubleValue() * .5;
    }
    i1.seek(0);
    for(int i = 0, j = 0; i < size; i++, i1.advance()) {
      final double[] mati = mat[i]; // Probably faster access.
      i2.seek(i + 1);
      for(int k = i + 1; k < size; k++, i2.advance()) {
        mati[k] = sq.similarity(i1, i2).doubleValue() - mati[i] - mat[k][k];
        mat[k][i] = mati[k]; // symmetry.
        flat[j] = mati[k];
        j++;
      }
    }
    double median = QuickSelect.quantile(flat, quantile);
    // On the diagonal, we place the median
    for(int i = 0; i < size; i++) {
      mat[i][i] = median;
    }
    return mat;
  }

  @Override
  public TypeInformation getInputTypeRestriction() {
    return similarity.getInputTypeRestriction();
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   * 
   * @param <O> Object type
   * @param <D> Distance type
   */
  public static class Parameterizer<O, D extends NumberDistance<D, ?>> extends AbstractParameterizer {
    /**
     * Parameter for the similarity function.
     */
    public static final OptionID SIMILARITY_ID = new OptionID("ap.similarity", "Similarity function to use.");

    /**
     * Similarity function.
     */
    SimilarityFunction<? super O, D> similarity;

    /**
     * Quantile to use.
     */
    double quantile;

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      ObjectParameter<SimilarityFunction<? super O, D>> param = new ObjectParameter<>(SIMILARITY_ID, SimilarityFunction.class, LinearKernelFunction.class);
      if(config.grab(param)) {
        similarity = param.instantiateClass(config);
      }

      DoubleParameter quantileP = new DoubleParameter(QUANTILE_ID, .5);
      if(config.grab(quantileP)) {
        quantile = quantileP.doubleValue();
      }
    }

    @Override
    protected SimilarityBasedInitializationWithMedian<O, D> makeInstance() {
      return new SimilarityBasedInitializationWithMedian<>(similarity, quantile);
    }
  }
}