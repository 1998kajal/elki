package experimentalcode.shared.algorithm.classifier;

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
import java.util.Collections;

import de.lmu.ifi.dbs.elki.data.ClassLabel;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.ids.DoubleDBIDListIter;
import de.lmu.ifi.dbs.elki.database.ids.KNNList;
import de.lmu.ifi.dbs.elki.database.query.knn.KNNQuery;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.result.Result;
import de.lmu.ifi.dbs.elki.utilities.documentation.Description;
import de.lmu.ifi.dbs.elki.utilities.documentation.Title;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.CommonConstraints;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.IntParameter;
import experimentalcode.arthur.AssociationID;

/**
 * KNNClassifier classifies instances based on the class distribution among the
 * k nearest neighbors in a database.
 * 
 * @author Arthur Zimek
 * @param <O> the type of DatabaseObjects handled by this Algorithm
 * @param <D> the type of Distance used by this Algorithm
 * @param <L> the type of the ClassLabel the Classifier is assigning
 */
@Title("kNN-classifier")
@Description("Lazy classifier classifies a given instance to the majority class of the k-nearest neighbors.")
public class KNNClassifier<O, L extends ClassLabel> extends DistanceBasedClassifier<O, L, Result> {
  /**
   * The logger for this class.
   */
  private static final Logging LOG = Logging.getLogger(KNNClassifier.class);

  /**
   * OptionID for
   * {@link experimentalcode.shared.algorithm.classifier.KNNClassifier#K_PARAM}
   */
  public static final OptionID K_ID = new OptionID("knnclassifier.k", "The number of neighbors to take into account for classification.");

  /**
   * Parameter to specify the number of neighbors to take into account for
   * classification, must be an integer greater than 0.
   * <p>
   * Default value: {@code 1}
   * </p>
   * <p>
   * Key: {@code -knnclassifier.k}
   * </p>
   */
  private final IntParameter K_PARAM = new IntParameter(K_ID, 1).addConstraint(CommonConstraints.GREATER_EQUAL_ONE_INT);

  /**
   * Holds the value of @link #K_PARAM}.
   */
  protected int k;

  /**
   * Holds the database where the classification is to base on.
   */
  protected Database database;

  /**
   * Provides a KNNClassifier, adding parameter {@link #K_PARAM} to the option
   * handler additionally to parameters of super class.
   */
  public KNNClassifier(Parameterization config) {
    super(config);
    config = config.descend(this);
    // parameter k
    if(config.grab(K_PARAM)) {
      k = K_PARAM.getValue();
    }
  }

  /**
   * Checks whether the database has the class labels set. Collects the class
   * labels available n the database. Holds the database to lazily classify new
   * instances later on.
   */
  @Override
  public void buildClassifier(Database database, ArrayList<L> labels) throws IllegalStateException {
    this.setLabels(labels);
    this.database = database;
  }

  /**
   * Provides a class distribution for the given instance. The distribution is
   * the relative value for each possible class among the k nearest neighbors of
   * the given instance in the previously specified database.
   */
  @Override
  public double[] classDistribution(O instance) throws IllegalStateException {
    try {
      double[] distribution = new double[getLabels().size()];
      int[] occurences = new int[getLabels().size()];

      KNNQuery<O> knnq = database.getKNNQuery(getDistanceQuery(), k);
      KNNList query = knnq.getKNNForObject(instance, k);
      Relation<ClassLabel> crep = database.getRelation(TypeUtil.CLASSLABEL);
      for(DoubleDBIDListIter neighbor = query.iter(); neighbor.valid(); neighbor.advance()) {
        int index = Collections.binarySearch(getLabels(), (AssociationID.CLASS.getType().cast(crep.get(neighbor))));
        if(index >= 0) {
          occurences[index]++;
        }
      }
      for(int i = 0; i < distribution.length; i++) {
        distribution[i] = ((double) occurences[i]) / (double) query.size();
      }
      return distribution;
    }
    catch(NullPointerException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Override
  public String model() {
    return "lazy learner - provides no model";
  }

  @Override
  public Result run(Database database) throws IllegalStateException {
    // TODO Implement sensible default behavior.
    return null;
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return TypeUtil.array(TypeUtil.NUMBER_VECTOR_FIELD);
  }

  @Override
  protected Logging getLogger() {
    return LOG;
  }
}