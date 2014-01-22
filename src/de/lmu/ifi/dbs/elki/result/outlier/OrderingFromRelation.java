package de.lmu.ifi.dbs.elki.result.outlier;

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

import java.util.Comparator;

import de.lmu.ifi.dbs.elki.database.ids.ArrayModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.ids.DBIDRef;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.relation.DoubleRelation;
import de.lmu.ifi.dbs.elki.result.OrderingResult;

/**
 * Ordering obtained from an outlier score.
 * 
 * @author Erich Schubert
 * 
 * @apiviz.uses Relation
 */
public class OrderingFromRelation implements OrderingResult {
  /**
   * Outlier scores.
   */
  protected DoubleRelation scores;

  /**
   * Factor for ascending (+1) and descending (-1) ordering.
   */
  protected int ascending = +1;
  
  /**
   * Constructor for outlier orderings
   * 
   * @param scores outlier score result
   * @param ascending Ascending when {@code true}, descending otherwise
   */
  public OrderingFromRelation(DoubleRelation scores, boolean ascending) {
    super();
    this.scores = scores;
    this.ascending = ascending ? +1 : -1;
  }
  
  /**
   * Ascending constructor.
   * 
   * @param scores
   */
  public OrderingFromRelation(DoubleRelation scores) {
    this(scores, true);
  }

  @Override
  public DBIDs getDBIDs() {
    return scores.getDBIDs();
  }

  @Override
  public ArrayModifiableDBIDs iter(DBIDs ids) {
    ArrayModifiableDBIDs sorted = DBIDUtil.newArray(ids);
    sorted.sort(new ImpliedComparator());
    return sorted;
  }

  @Override
  public String getLongName() {
    return scores.getLongName()+" Order";
  }

  @Override
  public String getShortName() {
    return scores.getShortName()+"_order";
  }

  /**
   * Internal comparator, accessing the map to sort objects
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  protected final class ImpliedComparator implements Comparator<DBIDRef> {
    @Override
    public int compare(DBIDRef id1, DBIDRef id2) {
      double k1 = scores.doubleValue(id1);
      double k2 = scores.doubleValue(id2);
      return ascending * Double.compare(k2, k1);
    }
  }
}
