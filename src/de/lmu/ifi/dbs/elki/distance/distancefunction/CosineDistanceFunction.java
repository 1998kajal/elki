package de.lmu.ifi.dbs.elki.distance.distancefunction;

import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.distance.distancevalue.DoubleDistance;
import de.lmu.ifi.dbs.elki.math.linearalgebra.Vector;

/**
 * Cosine distance function for feature vectors.
 * 
 * The cosine distance is computed from the cosine similarity by
 * <code>1-(cosine similarity)</code>.
 * 
 * @author Arthur Zimek
 * @param <V> the type of FeatureVector to compute the distances in between
 */
public class CosineDistanceFunction<V extends NumberVector<V, ?>> extends AbstractDistanceFunction<V, DoubleDistance> {
  /**
   * Provides a CosineDistanceFunction.
   */
  public CosineDistanceFunction() {
    super(DoubleDistance.FACTORY);
  }

  /**
   * Computes the cosine distance for two given feature vectors.
   * 
   * The cosine distance is computed from the cosine similarity by
   * <code>1-(cosine similarity)</code>.
   * 
   * @param v1 first feature vector
   * @param v2 second feature vector
   * @return the cosine distance for two given feature vectors v1 and v2
   */
  public DoubleDistance distance(V v1, V v2) {
    Vector m1 = v1.getColumnVector();
    m1.normalize();
    Vector m2 = v2.getColumnVector();
    m2.normalize();

    double d = 1 - m1.transposeTimes(m2);
    if(d < 0) {
      d = 0;
    }
    return new DoubleDistance(d);
  }

  @Override
  public Class<? super V> getInputDatatype() {
    return NumberVector.class;
  }
}