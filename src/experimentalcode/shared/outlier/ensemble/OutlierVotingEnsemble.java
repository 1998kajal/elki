package experimentalcode.shared.outlier.ensemble;
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

import java.util.ArrayList;
import java.util.List;

import de.lmu.ifi.dbs.elki.algorithm.AbstractAlgorithm;
import de.lmu.ifi.dbs.elki.algorithm.Algorithm;
import de.lmu.ifi.dbs.elki.algorithm.outlier.OutlierAlgorithm;
import de.lmu.ifi.dbs.elki.data.type.CombinedTypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreFactory;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreUtil;
import de.lmu.ifi.dbs.elki.database.datastore.WritableDoubleDataStore;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.ModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.relation.MaterializedRelation;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.progress.FiniteProgress;
import de.lmu.ifi.dbs.elki.math.DoubleMinMax;
import de.lmu.ifi.dbs.elki.result.Result;
import de.lmu.ifi.dbs.elki.result.ResultUtil;
import de.lmu.ifi.dbs.elki.result.outlier.BasicOutlierScoreMeta;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierResult;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierScoreMeta;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.ChainedParameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.ListParameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.ObjectListParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.ObjectParameter;
import experimentalcode.shared.outlier.ensemble.voting.EnsembleVoting;

/**
 * Simple outlier ensemble method.
 * 
 * @author Erich Schubert
 * 
 * @apiviz.composedOf EnsembleVoting
 * @apiviz.uses OutlierResult oneway - - reads
 * @apiviz.uses OutlierResult oneway - - «create»
 * 
 * @param <O> object type
 */
public class OutlierVotingEnsemble<O> extends AbstractAlgorithm<OutlierResult> {
  /**
   * The logger for this class.
   */
  private static final Logging LOG = Logging.getLogger(OutlierVotingEnsemble.class);
  
  /**
   * Parameter for the individual algorithms
   */
  private ObjectListParameter<OutlierAlgorithm> ALGORITHMS_PARAM = new ObjectListParameter<OutlierAlgorithm>(OptionID.ALGORITHM, OutlierAlgorithm.class);

  /**
   * The actual algorithms
   */
  private List<OutlierAlgorithm> algorithms;

  /**
   * Voting strategy to use in the ensemble.
   */
  public static final OptionID VOTING_ID = new OptionID("ensemble.voting", "Voting strategy to use in the ensemble.");

  /**
   * Voting strategy parameter
   */
  private ObjectParameter<EnsembleVoting> VOTING_PARAM = new ObjectParameter<EnsembleVoting>(VOTING_ID, EnsembleVoting.class);

  /**
   * The voting in use.
   */
  private EnsembleVoting voting;

  /**
   * Constructor, adhering to
   * {@link de.lmu.ifi.dbs.elki.utilities.optionhandling.Parameterizable}
   * 
   * @param config Parameterization
   */
  public OutlierVotingEnsemble(Parameterization config) {
    super();
    config = config.descend(this);
    if(config.grab(ALGORITHMS_PARAM)) {
      ListParameterization subconfig = new ListParameterization();
      ChainedParameterization chain = new ChainedParameterization(subconfig, config);
      chain.errorsTo(config);
      algorithms = ALGORITHMS_PARAM.instantiateClasses(chain);
      subconfig.logAndClearReportedErrors();
    }
    if(config.grab(VOTING_PARAM)) {
      voting = VOTING_PARAM.instantiateClass(config);
    }
  }

  @Override
  public OutlierResult run(Database database) throws IllegalStateException {
    int num = algorithms.size();
    // Run inner outlier algorithms
    ModifiableDBIDs ids = DBIDUtil.newHashSet();
    ArrayList<OutlierResult> results = new ArrayList<OutlierResult>(num);
    {
      FiniteProgress prog = LOG.isVerbose() ? new FiniteProgress("Inner outlier algorithms", num, LOG) : null;
      for(Algorithm alg : algorithms) {
        Result res = alg.run(database);
        List<OutlierResult> ors = ResultUtil.getOutlierResults(res);
        for(OutlierResult or : ors) {
          results.add(or);
          ids.addDBIDs(or.getScores().getDBIDs());
        }
        if(prog != null) {
          prog.incrementProcessed(LOG);
        }
      }
      if(prog != null) {
        prog.ensureCompleted(LOG);
      }
    }
    // Combine
    WritableDoubleDataStore sumscore = DataStoreUtil.makeDoubleStorage(ids, DataStoreFactory.HINT_STATIC);
    DoubleMinMax minmax = new DoubleMinMax();
    {
      FiniteProgress cprog = LOG.isVerbose() ? new FiniteProgress("Combining results", ids.size(), LOG) : null;
      for(DBIDIter id = ids.iter(); id.valid(); id.advance()) {
        ArrayList<Double> scores = new ArrayList<Double>(num);
        for(OutlierResult r : results) {
          Double score = r.getScores().get(id);
          if(score != null) {
            scores.add(score);
          }
          else {
            LOG.warning("DBID " + id + " was not given a score by result " + r);
          }
        }
        if(scores.size() > 0) {
          double combined = voting.combine(scores);
          sumscore.putDouble(id, combined);
          minmax.put(combined);
        }
        else {
          LOG.warning("DBID " + id + " was not given any score at all.");
        }
        if(cprog != null) {
          cprog.incrementProcessed(LOG);
        }
      }
      if(cprog != null) {
        cprog.ensureCompleted(LOG);
      }
    }
    OutlierScoreMeta meta = new BasicOutlierScoreMeta(minmax.getMin(), minmax.getMax());
    Relation<Double> scores = new MaterializedRelation<Double>("Outlier Ensemble", "ensemble-outlier", TypeUtil.DOUBLE, sumscore, ids);
    return new OutlierResult(meta, scores);
  }

  @Override
  protected Logging getLogger() {
    return LOG;
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    TypeInformation[] trs = new TypeInformation[algorithms.size()];
    for (int i = 0; i < trs.length; i++) {
      // FIXME: only allow single-input algorithms?
      trs[i] = algorithms.get(i).getInputTypeRestriction()[0];
    }
    return TypeUtil.array(new CombinedTypeInformation(trs));
  }
}