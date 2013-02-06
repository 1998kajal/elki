package de.lmu.ifi.dbs.elki.data.synthetic.bymodel;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import de.lmu.ifi.dbs.elki.data.ClassLabel;
import de.lmu.ifi.dbs.elki.data.DoubleVector;
import de.lmu.ifi.dbs.elki.data.SimpleClassLabel;
import de.lmu.ifi.dbs.elki.data.model.Model;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.data.type.VectorFieldTypeInformation;
import de.lmu.ifi.dbs.elki.datasource.bundle.MultipleObjectsBundle;
import de.lmu.ifi.dbs.elki.math.linearalgebra.Vector;
import de.lmu.ifi.dbs.elki.utilities.exceptions.UnableToComplyException;

/**
 * Generate a data set according to a given model.
 * 
 * Key idea of this generator is to re-generate points if they are more likely
 * to belong to a different cluster than the one they were generated for. The
 * benefit is that we should end up with a data set that follows closely the
 * model that we specified.
 * 
 * The drawbacks are that on one hand, specifications might be unsatisfiable.
 * For this a retry count is kept and an {@link UnableToComplyException} is
 * thrown when the maximum number of retries is exceeded.
 * 
 * On the other hand, the model might not be exactly as specified. When the
 * generator reports an "Density correction factor estimation" that differs from
 * 1.0 this is an indication that the result is not exact.
 * 
 * On the third hand, rejecting points introduces effects where one generator
 * can influence others, so random generator results will not be stable with
 * respect to the addition of new dimensions and similar if there are any
 * rejects involved. So this generator is not entirely optimal for generating
 * data sets for scalability tests on the number of dimensions, although if
 * clusters overlap little enough (so that no rejects happen) the results should
 * be as expected.
 * 
 * @author Erich Schubert
 * 
 * @apiviz.has GeneratorInterface
 */
public class GeneratorMain {
  /**
   * List of clusters to generate.
   */
  protected LinkedList<GeneratorInterface> generators = new LinkedList<>();

  /**
   * Controls whether points are tested against the model during generation.
   */
  protected boolean testAgainstModel = true;

  /**
   * Add a cluster to the cluster list.
   * 
   * @param c cluster to add
   */
  public void addCluster(GeneratorInterface c) {
    generators.add(c);
  }

  /**
   * Main loop to generate data set.
   * 
   * @return Generated data set
   * @throws UnableToComplyException when model not satisfiable or no clusters
   *         specified.
   */
  public MultipleObjectsBundle generate() throws UnableToComplyException {
    // we actually need some clusters.
    if(generators.size() < 1) {
      throw new UnableToComplyException("No clusters specified.");
    }
    // Assert that cluster dimensions agree.
    final int dim = generators.get(0).getDim();
    {
      for(GeneratorInterface c : generators) {
        if(c.getDim() != dim) {
          throw new UnableToComplyException("Cluster dimensions do not agree.");
        }
      }
    }
    // Prepare result bundle
    MultipleObjectsBundle bundle = new MultipleObjectsBundle();
    VectorFieldTypeInformation<DoubleVector> type = new VectorFieldTypeInformation<>(DoubleVector.FACTORY, dim);
    bundle.appendColumn(type, new ArrayList<>());
    bundle.appendColumn(TypeUtil.CLASSLABEL, new ArrayList<>());
    bundle.appendColumn(TypeUtil.MODEL, new ArrayList<Model>());

    // generate clusters
    for(GeneratorInterface curclus : generators) {
      ClassLabel l = new SimpleClassLabel(curclus.getName());
      Model model = curclus.makeModel();
      int kept = 0;
      while(kept < curclus.getSize()) {
        // generate the "missing" number of points
        List<Vector> newp = curclus.generate(curclus.getSize() - kept);
        if(curclus instanceof GeneratorInterfaceDynamic) {
          GeneratorInterfaceDynamic cursclus = (GeneratorInterfaceDynamic) curclus;
          for(Vector p : newp) {
            boolean keep = true;
            if(testAgainstModel) {
              double max = 0.0;
              double is = 0.0;
              for(GeneratorInterface other : generators) {
                double d = other.getDensity(p) * other.getSize();
                if(other == curclus) {
                  is = d;
                }
                else if(d > max) {
                  max = d;
                }
              }
              // Only keep the point if the largest density was the cluster it
              // was generated for
              if(is < max) {
                keep = false;
              }
            }
            if(keep) {
              DoubleVector dv = new DoubleVector(p);
              bundle.appendSimple(dv, l, model);
              ++kept;
            }
            else {
              cursclus.incrementDiscarded();
            }
          }
        } else {
          // Keep all.
          for (Vector p : newp) {
            DoubleVector dv = new DoubleVector(p);
            bundle.appendSimple(dv, l, model);
            ++kept;
          }
        }
      }
    }
    return bundle;
  }

  /**
   * Return value of the {@link #testAgainstModel} flag.
   * 
   * @return value of testAgainstModel
   */
  public boolean isTestAgainstModel() {
    return testAgainstModel;
  }

  /**
   * Set the value of the {@link #testAgainstModel} flag.
   * 
   * @param testAgainstModel New value
   */
  public void setTestAgainstModel(boolean testAgainstModel) {
    this.testAgainstModel = testAgainstModel;
  }

  /**
   * Access the generators.
   * 
   * @return generators
   */
  public List<GeneratorInterface> getGenerators() {
    return Collections.unmodifiableList(generators);
  }
}