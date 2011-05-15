package de.lmu.ifi.dbs.elki.distance.distancefunction;

import de.lmu.ifi.dbs.elki.distance.distancevalue.NumberDistance;

/**
 * Interface for distance functions that can provide a raw double value.
 * 
 * This is for use in performance-critical situations that need to avoid the
 * boxing/unboxing cost of regular distance API.
 * 
 * @author Erich Schubert
 * 
 * @param <O> Object type
 * @param <D> the distance value
 */
public interface PrimitiveNumberDistanceFunction<O, D extends NumberDistance<D, ?>> extends PrimitiveDistanceFunction<O, D> {
  /**
   * Computes the distance between two given DatabaseObjects according to this
   * distance function.
   * 
   * @param o1 first DatabaseObject
   * @param o2 second DatabaseObject
   * @return the distance between two given DatabaseObjects according to this
   *         distance function
   */
  double doubleDistance(O o1, O o2);
}