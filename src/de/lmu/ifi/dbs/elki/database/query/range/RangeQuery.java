package de.lmu.ifi.dbs.elki.database.query.range;

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

import de.lmu.ifi.dbs.elki.database.ids.DBIDRef;
import de.lmu.ifi.dbs.elki.database.ids.distance.DistanceDBIDList;
import de.lmu.ifi.dbs.elki.database.query.DatabaseQuery;
import de.lmu.ifi.dbs.elki.distance.distancevalue.Distance;

/**
 * The interface for range queries
 * 
 * @author Erich Schubert
 * 
 * @apiviz.landmark
 * @apiviz.uses DistanceDBIDResult oneway - - «create»
 * 
 * @param <O> Object type
 * @param <D> Distance type
 */
public interface RangeQuery<O, D extends Distance<D>> extends DatabaseQuery {
  /**
   * Get the nearest neighbors for a particular id in a given query range
   * 
   * @param id query object ID
   * @param range Query range
   * @return neighbors
   */
  public DistanceDBIDList<D> getRangeForDBID(DBIDRef id, D range);

  /**
   * Get the nearest neighbors for a particular object in a given query range
   * 
   * @param obj Query object
   * @param range Query range
   * @return neighbors
   */
  public DistanceDBIDList<D> getRangeForObject(O obj, D range);

  /**
   * Get the distance factory for the given distance type.
   * 
   * @return Distance factory.
   */
  public D getDistanceFactory();
}
