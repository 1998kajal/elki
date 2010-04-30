package experimentalcode.shared.algorithm.classifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import de.lmu.ifi.dbs.elki.data.ClassLabel;
import de.lmu.ifi.dbs.elki.data.DatabaseObject;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.result.Result;
import de.lmu.ifi.dbs.elki.utilities.ExceptionMessages;
import de.lmu.ifi.dbs.elki.utilities.Util;
import de.lmu.ifi.dbs.elki.utilities.documentation.Description;
import de.lmu.ifi.dbs.elki.utilities.documentation.Title;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;

/**
 * Classifier to classify instances based on the prior probability of classes in
 * the database.
 * 
 * @author Arthur Zimek
 * @param <O> the type of DatabaseObjects handled by this Algorithm
 * @param <L> the type of the ClassLabel the Classifier is assigning
 */
@Title("Prior Probability Classifier")
@Description("Classifier to predict simply prior probabilities for all classes as defined by their relative abundance in a given database.")
public class PriorProbabilityClassifier<O extends DatabaseObject, L extends ClassLabel> extends AbstractClassifier<O, L, Result> {
  /**
   * Holds the prior probabilities.
   */
  protected double[] distribution;

  /**
   * Index of the most abundant class.
   */
  protected int prediction;

  /**
   * Holds the database the prior probabilities are based on.
   */
  protected Database<O> database;

  /**
   * Provides a classifier always predicting the prior probabilities.
   */
  public PriorProbabilityClassifier(Parameterization config) {
    super(config);
  }

  /**
   * Learns the prior probability for all classes.
   */
  public void buildClassifier(Database<O> database, ArrayList<L> classLabels) throws IllegalStateException {
    this.setLabels(classLabels);
    this.database = database;
    distribution = new double[getLabels().size()];
    int[] occurences = new int[getLabels().size()];
    for(Iterator<DBID> iter = database.iterator(); iter.hasNext();) {
      ClassLabel label = database.getClassLabel(iter.next());
      int index = Collections.binarySearch(getLabels(), label);
      if(index > -1) {
        occurences[index]++;
      }
      else {
        throw new IllegalStateException(ExceptionMessages.INCONSISTENT_STATE_NEW_LABEL + ": " + label);
      }
    }
    double size = database.size();
    for(int i = 0; i < distribution.length; i++) {
      distribution[i] = occurences[i] / size;
    }
    prediction = Util.getIndexOfMaximum(distribution);
  }

  /**
   * Returns the index of the most abundant class. According to the prior class
   * probability distribution, this is the index of the class showing maximum
   * prior probability.
   * 
   * @param instance unused
   */
  @Override
  public int classify(O instance) throws IllegalStateException {
    return prediction;
  }

  /**
   * Returns the distribution of the classes' prior probabilities.
   * 
   * @param instance unused
   */
  public double[] classDistribution(O instance) throws IllegalStateException {
    return distribution;
  }

  public String model() {
    StringBuffer output = new StringBuffer();
    for(int i = 0; i < distribution.length; i++) {
      output.append(getLabels().get(i));
      output.append(" : ");
      output.append(distribution[i]);
      output.append('\n');
    }
    return output.toString();
  }

  @Override
  protected Result runInTime(@SuppressWarnings("unused") Database<O> database) throws IllegalStateException {
    // TODO Implement sensible default behavior.
    return null;
  }
}