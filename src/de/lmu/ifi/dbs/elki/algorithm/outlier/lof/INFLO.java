package de.lmu.ifi.dbs.elki.algorithm.outlier.lof;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2012
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

import de.lmu.ifi.dbs.elki.algorithm.AbstractDistanceBasedAlgorithm;
import de.lmu.ifi.dbs.elki.algorithm.outlier.OutlierAlgorithm;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreFactory;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreUtil;
import de.lmu.ifi.dbs.elki.database.datastore.WritableDataStore;
import de.lmu.ifi.dbs.elki.database.datastore.WritableDoubleDataStore;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.KNNList;
import de.lmu.ifi.dbs.elki.database.ids.ModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.query.DatabaseQuery;
import de.lmu.ifi.dbs.elki.database.query.distance.DistanceQuery;
import de.lmu.ifi.dbs.elki.database.query.knn.KNNQuery;
import de.lmu.ifi.dbs.elki.database.relation.DoubleRelation;
import de.lmu.ifi.dbs.elki.database.relation.MaterializedDoubleRelation;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.math.DoubleMinMax;
import de.lmu.ifi.dbs.elki.math.Mean;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierResult;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierScoreMeta;
import de.lmu.ifi.dbs.elki.result.outlier.QuotientOutlierScoreMeta;
import de.lmu.ifi.dbs.elki.utilities.documentation.Description;
import de.lmu.ifi.dbs.elki.utilities.documentation.Reference;
import de.lmu.ifi.dbs.elki.utilities.documentation.Title;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.CommonConstraints;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.DoubleParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.IntParameter;

/**
 * INFLO provides the Mining Algorithms (Two-way Search Method) for Influence
 * Outliers using Symmetric Relationship
 * <p>
 * Reference: <br>
 * <p>
 * Jin, W., Tung, A., Han, J., and Wang, W. 2006<br/>
 * Ranking outliers using symmetric neighborhood relationship<br/>
 * In Proc. Pacific-Asia Conf. on Knowledge Discovery and Data Mining (PAKDD),
 * Singapore
 * </p>
 * 
 * @author Ahmed Hettab
 * 
 * @apiviz.has KNNQuery
 * 
 * @param <O> the type of DatabaseObject the algorithm is applied on
 */
@Title("INFLO: Influenced Outlierness Factor")
@Description("Ranking Outliers Using Symmetric Neigborhood Relationship")
@Reference(authors = "Jin, W., Tung, A., Han, J., and Wang, W", title = "Ranking outliers using symmetric neighborhood relationship", booktitle = "Proc. Pacific-Asia Conf. on Knowledge Discovery and Data Mining (PAKDD), Singapore, 2006", url = "http://dx.doi.org/10.1007/11731139_68")
public class INFLO<O> extends AbstractDistanceBasedAlgorithm<O, OutlierResult> implements OutlierAlgorithm {
  /**
   * The logger for this class.
   */
  private static final Logging LOG = Logging.getLogger(INFLO.class);

  /**
   * Parameter to specify if any object is a Core Object must be a double
   * greater than 0.0
   * <p>
   * see paper "Two-way search method" 3.2
   */
  public static final OptionID M_ID = new OptionID("inflo.m", "The threshold");

  /**
   * Holds the value of {@link #M_ID}.
   */
  private double m;

  /**
   * Parameter to specify the number of nearest neighbors of an object to be
   * considered for computing its INFLO_SCORE. must be an integer greater than
   * 1.
   */
  public static final OptionID K_ID = new OptionID("inflo.k", "The number of nearest neighbors of an object to be considered for computing its INFLO_SCORE.");

  /**
   * Holds the value of {@link #K_ID}.
   */
  private int k;

  /**
   * Constructor with parameters.
   * 
   * @param distanceFunction Distance function in use
   * @param m m Parameter
   * @param k k Parameter
   */
  public INFLO(DistanceFunction<? super O> distanceFunction, double m, int k) {
    super(distanceFunction);
    this.m = m;
    this.k = k;
  }

  /**
   * Run the algorithm
   * 
   * @param database Database to process
   * @param relation Relation to process
   * @return Outlier result
   */
  public OutlierResult run(Database database, Relation<O> relation) {
    DistanceQuery<O> distFunc = database.getDistanceQuery(relation, getDistanceFunction());

    ModifiableDBIDs processedIDs = DBIDUtil.newHashSet(relation.size());
    ModifiableDBIDs pruned = DBIDUtil.newHashSet();
    // KNNS
    WritableDataStore<ModifiableDBIDs> knns = DataStoreUtil.makeStorage(relation.getDBIDs(), DataStoreFactory.HINT_TEMP | DataStoreFactory.HINT_HOT, ModifiableDBIDs.class);
    // RNNS
    WritableDataStore<ModifiableDBIDs> rnns = DataStoreUtil.makeStorage(relation.getDBIDs(), DataStoreFactory.HINT_TEMP | DataStoreFactory.HINT_HOT, ModifiableDBIDs.class);
    // density
    WritableDoubleDataStore density = DataStoreUtil.makeDoubleStorage(relation.getDBIDs(), DataStoreFactory.HINT_TEMP | DataStoreFactory.HINT_HOT);
    // init knns and rnns
    for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
      knns.put(iditer, DBIDUtil.newArray());
      rnns.put(iditer, DBIDUtil.newArray());
    }

    // TODO: use kNN preprocessor?
    KNNQuery<O> knnQuery = database.getKNNQuery(distFunc, k, DatabaseQuery.HINT_HEAVY_USE);

    for(DBIDIter id = relation.iterDBIDs(); id.valid(); id.advance()) {
      // if not visited count=0
      int count = rnns.get(id).size();
      if(!processedIDs.contains(id)) {
        // TODO: use exactly k neighbors?
        KNNList list = knnQuery.getKNNForDBID(id, k);
        knns.get(id).addDBIDs(list);
        processedIDs.add(id);
        density.putDouble(id, 1 / list.getKNNDistance());

      }
      ModifiableDBIDs s = knns.get(id);
      for(DBIDIter q = knns.get(id).iter(); q.valid(); q.advance()) {
        if(!processedIDs.contains(q)) {
          // TODO: use exactly k neighbors?
          KNNList listQ = knnQuery.getKNNForDBID(q, k);
          knns.get(q).addDBIDs(listQ);
          density.putDouble(q, 1 / listQ.getKNNDistance());
          processedIDs.add(q);
        }

        if(knns.get(q).contains(id)) {
          rnns.get(q).add(id);
          rnns.get(id).add(q);
          count++;
        }
      }
      if(count >= s.size() * m) {
        pruned.add(id);
      }
    }

    // Calculate INFLO for any Object
    // IF Object is pruned INFLO=1.0
    DoubleMinMax inflominmax = new DoubleMinMax();
    WritableDoubleDataStore inflos = DataStoreUtil.makeDoubleStorage(relation.getDBIDs(), DataStoreFactory.HINT_STATIC);
    for(DBIDIter id = relation.iterDBIDs(); id.valid(); id.advance()) {
      if(!pruned.contains(id)) {
        ModifiableDBIDs knn = knns.get(id);
        ModifiableDBIDs rnn = rnns.get(id);

        double denP = density.doubleValue(id);
        knn.addDBIDs(rnn);
        Mean mean = new Mean();
        for(DBIDIter iter = knn.iter(); iter.valid(); iter.advance()) {
          mean.put(density.doubleValue(iter));
        }
        double den = mean.getMean() / denP;
        inflos.putDouble(id, den);
        // update minimum and maximum
        inflominmax.put(den);

      }
      if(pruned.contains(id)) {
        inflos.putDouble(id, 1.0);
        inflominmax.put(1.0);
      }
    }

    // Build result representation.
    DoubleRelation scoreResult = new MaterializedDoubleRelation("Influence Outlier Score", "inflo-outlier", inflos, relation.getDBIDs());
    OutlierScoreMeta scoreMeta = new QuotientOutlierScoreMeta(inflominmax.getMin(), inflominmax.getMax(), 0.0, Double.POSITIVE_INFINITY, 1.0);
    return new OutlierResult(scoreMeta, scoreResult);
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return TypeUtil.array(getDistanceFunction().getInputTypeRestriction());
  }

  @Override
  protected Logging getLogger() {
    return LOG;
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static class Parameterizer<O> extends AbstractDistanceBasedAlgorithm.Parameterizer<O> {
    protected double m = 1.0;

    protected int k = 0;

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      final DoubleParameter mP = new DoubleParameter(M_ID, 1.0);
      mP.addConstraint(CommonConstraints.GREATER_THAN_ZERO_DOUBLE);
      if(config.grab(mP)) {
        m = mP.doubleValue();
      }

      final IntParameter kP = new IntParameter(K_ID);
      kP.addConstraint(CommonConstraints.GREATER_THAN_ONE_INT);
      if(config.grab(kP)) {
        k = kP.intValue();
      }
    }

    @Override
    protected INFLO<O> makeInstance() {
      return new INFLO<>(distanceFunction, m, k);
    }
  }
}
