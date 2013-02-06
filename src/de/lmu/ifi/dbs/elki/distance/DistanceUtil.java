package de.lmu.ifi.dbs.elki.distance;

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

import de.lmu.ifi.dbs.elki.database.query.distance.DistanceQuery;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.Distance;
import de.lmu.ifi.dbs.elki.distance.distancevalue.DoubleDistance;

/**
 * Class with distance related utility functions.
 * 
 * @apiviz.uses de.lmu.ifi.dbs.elki.distance.distancevalue.Distance oneway - -
 *              handles
 * 
 * @author Erich Schubert
 */
public final class DistanceUtil {
  /**
   * Returns the maximum of the given Distances or the first, if none is greater
   * than the other one.
   * 
   * @param <D> distance type
   * @param d1 first Distance
   * @param d2 second Distance
   * @return Distance the maximum of the given Distances or the first, if
   *         neither is greater than the other one
   */
  public static <D extends Distance<D>> D max(D d1, D d2) {
    if(d1 == null) {
      return d2;
    }
    if(d2 == null) {
      return d1;
    }
    if(d1.compareTo(d2) > 0) {
      return d1;
    }
    else if(d2.compareTo(d1) > 0) {
      return d2;
    }
    else {
      return d1;
    }
  }

  /**
   * Returns the minimum of the given Distances or the first, if none is less
   * than the other one.
   * 
   * @param <D> distance type
   * @param d1 first Distance
   * @param d2 second Distance
   * @return Distance the minimum of the given Distances or the first, if
   *         neither is less than the other one
   */
  public static <D extends Distance<D>> D min(D d1, D d2) {
    if(d1 == null) {
      return d2;
    }
    if(d2 == null) {
      return d1;
    }
    if(d1.compareTo(d2) < 0) {
      return d1;
    }
    else if(d2.compareTo(d1) < 0) {
      return d2;
    }
    else {
      return d1;
    }
  }

  /**
   * Test whether a distance function is double-valued.
   * 
   * @param df Distance function
   * @return True when the distance function returns double values
   */
  public static boolean isDoubleDistanceFunction(DistanceFunction<?, ?> df) {
    Object factory = df.getDistanceFactory();
    return (factory == DoubleDistance.FACTORY) || (factory instanceof DoubleDistance);
  }

  /**
   * Test whether a distance query is double-valued.
   * 
   * @param df Distance function
   * @return True when the distance function returns double values
   */
  public static boolean isDoubleDistanceFunction(DistanceQuery<?, ?> df) {
    Object factory = df.getDistanceFactory();
    return (factory == DoubleDistance.FACTORY) || (factory instanceof DoubleDistance);
  }
}