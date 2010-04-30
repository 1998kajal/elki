package experimentalcode.elke.algorithm.lof;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import de.lmu.ifi.dbs.elki.JUnit4Test;
import de.lmu.ifi.dbs.elki.algorithm.outlier.LOF;
import de.lmu.ifi.dbs.elki.algorithm.outlier.OnlineLOF;
import de.lmu.ifi.dbs.elki.data.DoubleVector;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.DatabaseObjectMetadata;
import de.lmu.ifi.dbs.elki.database.connection.FileBasedDatabaseConnection;
import de.lmu.ifi.dbs.elki.distance.distancevalue.DoubleDistance;
import de.lmu.ifi.dbs.elki.result.AnnotationResult;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierResult;
import de.lmu.ifi.dbs.elki.utilities.UnableToComplyException;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.ListParameterization;
import de.lmu.ifi.dbs.elki.utilities.pairs.Pair;

/**
 * 
 * @author Elke Achtert
 * 
 */
public class TestOnlineLOF implements JUnit4Test {
  // the following values depend on the data set used!
  String dataset = "data/testdata/unittests/elki.csv";

  // size of the data set
  int shoulds = 203;

  int k = 5;

  @Test
  public void testLOF() throws UnableToComplyException {
    ListParameterization params1 = new ListParameterization();
    params1.addParameter(FileBasedDatabaseConnection.INPUT_ID, dataset);
    params1.addParameter(LOF.K_ID, k);
    FileBasedDatabaseConnection<DoubleVector> dbconn = new FileBasedDatabaseConnection<DoubleVector>(params1);

    // get database
    Database<DoubleVector> db = dbconn.getDatabase(null);
    db = getDatabase();

    Integer[] insertion_ids = new Integer[] { 1,16,7};
    Integer[] insertion_ids2 = new Integer[] {97,67,56 };
    // 16,7,67,56};
    List<Pair<DoubleVector, DatabaseObjectMetadata>> insertions = new ArrayList<Pair<DoubleVector, DatabaseObjectMetadata>>();
    for(Integer id : insertion_ids) {
      insertions.add(new Pair<DoubleVector, DatabaseObjectMetadata>(db.get(id), new DatabaseObjectMetadata(db, id)));
      db.delete(id);
    }
    List<Pair<DoubleVector, DatabaseObjectMetadata>> insertions2 = new ArrayList<Pair<DoubleVector, DatabaseObjectMetadata>>();
    for(Integer id : insertion_ids2) {
      insertions2.add(new Pair<DoubleVector, DatabaseObjectMetadata>(db.get(id), new DatabaseObjectMetadata(db, id)));
      db.delete(id);
    }

    // setup algorithm
    OnlineLOF<DoubleVector, DoubleDistance> lof = new OnlineLOF<DoubleVector, DoubleDistance>(params1);
    params1.failOnErrors();
    if(params1.hasUnusedParameters()) {
      fail("Unused parameters: " + params1.getRemainingParameters());
    }

    // run LOF on database
    // lof.setVerbose(true);
    OutlierResult result1 = lof.run(db);

    lof.setVerbose(false);
    db.insert(insertions);
    db.insert(insertions2);

    OutlierResult result2 = runLOF();

    List<Integer> ids = db.getIDs();
    AnnotationResult<Double> scores1 = result1.getScores();
    AnnotationResult<Double> scores2 = result2.getScores();
    for(Integer id : ids) {
      Double lof1 = scores1.getValueFor(id);
      Double lof2 = scores2.getValueFor(id);

      if(lof1 == null || lof2 == null) {
        System.out.println("lof(" + id + ") != lof(" + id + "): " + lof1 + " != " + lof2);
      }

      else if(!lof1.equals(lof2)) {
        System.out.println("lof(" + id + ") != lof(" + id + "): " + lof1 + " != " + lof2);
      }
      // assertTrue("lof(" + id + ") != lof(" + id + "): " + lof1 + " != " +
      // lof2, lof1 == lof2);
    }

  }

  private OutlierResult runLOF() {
    Database<DoubleVector> db = getDatabase();
    ListParameterization params = new ListParameterization();
    params.addParameter(LOF.K_ID, k);

    // setup algorithm
    LOF<DoubleVector, DoubleDistance> lof = new LOF<DoubleVector, DoubleDistance>(params);

    params.failOnErrors();
    if(params.hasUnusedParameters()) {
      fail("Unused parameters: " + params.getRemainingParameters());
    }
    // run LOF on database
    lof.setVerbose(false);
    return lof.run(db);
  }

  private Database<DoubleVector> getDatabase() {
    ListParameterization params = new ListParameterization();
    params.addParameter(FileBasedDatabaseConnection.INPUT_ID, dataset);
    //params.addParameter(AbstractDatabaseConnection.DATABASE_ID, SpatialIndexDatabase.class);
    //params.addParameter(SpatialIndexDatabase.INDEX_ID, RdKNNTree.class);
    //params.addParameter(RdKNNTree.K_ID, k+1);
    
    FileBasedDatabaseConnection<DoubleVector> dbconn = new FileBasedDatabaseConnection<DoubleVector>(params);
    params.failOnErrors();
    if(params.hasUnusedParameters()) {
      fail("Unused parameters: " + params.getRemainingParameters());
    }

    // get database
    Database<DoubleVector> db = dbconn.getDatabase(null);
    return db;
  }

}
