package de.lmu.ifi.dbs.elki.algorithm;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2014
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

import java.util.ArrayList;
import java.util.Collection;

import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.query.distance.DistanceQuery;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.result.CollectionResult;
import de.lmu.ifi.dbs.elki.utilities.documentation.Description;
import de.lmu.ifi.dbs.elki.utilities.documentation.Title;
import de.lmu.ifi.dbs.elki.utilities.pairs.CTriple;

/**
 * Algorithm to materialize all the distances in a data set.
 * 
 * The result can then be used with the DoubleDistanceParser and
 * MultipleFileInput to use cached distances.
 * 
 * Symmetry is assumed.
 * 
 * @param <O> Object type
 */
// TODO: use DBIDPair -> D map?
@Title("MaterializeDistances")
@Description("Materialize all distances in the data set to use as cached/precalculated data.")
public class MaterializeDistances<O> extends AbstractDistanceBasedAlgorithm<O, CollectionResult<CTriple<DBID, DBID, Double>>> {
  /**
   * The logger for this class.
   */
  private static final Logging LOG = Logging.getLogger(MaterializeDistances.class);

  /**
   * Constructor.
   * 
   * @param distanceFunction Parameterization
   */
  public MaterializeDistances(DistanceFunction<? super O> distanceFunction) {
    super(distanceFunction);
  }

  /**
   * Iterates over all points in the database.
   * 
   * @param database Database to process
   * @param relation Relation to process
   * @return Distance matrix
   */
  public CollectionResult<CTriple<DBID, DBID, Double>> run(Database database, Relation<O> relation) {
    DistanceQuery<O> distFunc = database.getDistanceQuery(relation, getDistanceFunction());
    final int size = relation.size();

    Collection<CTriple<DBID, DBID, Double>> r = new ArrayList<>(size * (size + 1) >> 1);

    for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
      for(DBIDIter iditer2 = relation.iterDBIDs(); iditer2.valid(); iditer2.advance()) {
        // skip inverted pairs
        if(DBIDUtil.compare(iditer2, iditer) > 0) {
          continue;
        }
        double d = distFunc.distance(iditer, iditer2);
        r.add(new CTriple<>(DBIDUtil.deref(iditer), DBIDUtil.deref(iditer2), d));
      }
    }
    return new CollectionResult<>("Distance Matrix", "distance-matrix", r);
  }

  @Override
  protected Logging getLogger() {
    return LOG;
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return TypeUtil.array(getDistanceFunction().getInputTypeRestriction());
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static class Parameterizer<O> extends AbstractDistanceBasedAlgorithm.Parameterizer<O> {
    @Override
    protected MaterializeDistances<O> makeInstance() {
      return new MaterializeDistances<>(distanceFunction);
    }
  }
}