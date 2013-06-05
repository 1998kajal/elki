package de.lmu.ifi.dbs.elki.algorithm.clustering.gdbscan;

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

import gnu.trove.list.array.TIntArrayList;

import java.util.ArrayList;

import de.lmu.ifi.dbs.elki.algorithm.AbstractAlgorithm;
import de.lmu.ifi.dbs.elki.algorithm.clustering.ClusteringAlgorithm;
import de.lmu.ifi.dbs.elki.data.Cluster;
import de.lmu.ifi.dbs.elki.data.Clustering;
import de.lmu.ifi.dbs.elki.data.model.ClusterModel;
import de.lmu.ifi.dbs.elki.data.model.Model;
import de.lmu.ifi.dbs.elki.data.type.SimpleTypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreFactory;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreUtil;
import de.lmu.ifi.dbs.elki.database.datastore.WritableIntegerDataStore;
import de.lmu.ifi.dbs.elki.database.ids.ArrayModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.progress.FiniteProgress;
import de.lmu.ifi.dbs.elki.logging.progress.IndefiniteProgress;
import de.lmu.ifi.dbs.elki.utilities.documentation.Reference;
import de.lmu.ifi.dbs.elki.utilities.exceptions.AbortException;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.ObjectParameter;

/**
 * Generalized DBSCAN, density-based clustering with noise.
 * <p>
 * Reference:<br />
 * Jörg Sander, Martin Ester, Hans-Peter Kriegel, Xiaowei Xu:<br />
 * Density-Based Clustering in Spatial Databases: The Algorithm GDBSCAN and Its
 * Applications<br />
 * In: Data Mining and Knowledge Discovery, 1998.
 * </p>
 * 
 * @author Erich Schubert
 * @author Arthur Zimek
 *
 * @apiviz.landmark
 * 
 * @apiviz.has Instance
 * @apiviz.composedOf CorePredicate
 * @apiviz.composedOf NeighborPredicate
 */
@Reference(authors = "Jörg Sander, Martin Ester, Hans-Peter Kriegel, Xiaowei Xu", title = "Density-Based Clustering in Spatial Databases: The Algorithm GDBSCAN and Its Applications", booktitle = "Data Mining and Knowledge Discovery", url = "http://dx.doi.org/10.1023/A:1009745219419")
public class GeneralizedDBSCAN extends AbstractAlgorithm<Clustering<Model>> implements ClusteringAlgorithm<Clustering<Model>> {
  /**
   * Get a logger for this algorithm
   */
  private static final Logging LOG = Logging.getLogger(GeneralizedDBSCAN.class);

  /**
   * The neighborhood predicate factory.
   */
  NeighborPredicate npred;

  /**
   * The core predicate factory.
   */
  CorePredicate corepred;

  /**
   * Constructor for parameterized algorithm.
   * 
   * @param npred Neighbor predicate
   * @param corepred Core point predicate
   */
  public GeneralizedDBSCAN(NeighborPredicate npred, CorePredicate corepred) {
    super();
    this.npred = npred;
    this.corepred = corepred;
  }

  @Override
  public Clustering<Model> run(Database database) {
    for (SimpleTypeInformation<?> t : npred.getOutputType()) {
      if (corepred.acceptsType(t)) {
        return new Instance<>(npred.instantiate(database, t), corepred.instantiate(database, t)).run();
      }
    }
    throw new AbortException("No compatible types found.");
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return TypeUtil.array(npred.getInputTypeRestriction());
  }

  @Override
  protected Logging getLogger() {
    return LOG;
  }

  /**
   * Instance for a particular data set.
   * 
   * @author Erich Schubert
   *
   * @apiviz.composedOf CorePredicate.Instance
   * @apiviz.composedOf NeighborPredicate.Instance
   */
  public class Instance<T> {
    /**
     * Unprocessed IDs
     */
    private static final int UNPROCESSED = -2;

    /**
     * Noise IDs
     */
    private static final int NOISE = -1;

    /**
     * Noise IDs
     */
    private static final int FIRST_CLUSTER = 0;

    /**
     * The neighborhood predicate
     */
    final NeighborPredicate.Instance<T> npred;

    /**
     * The core object property
     */
    final CorePredicate.Instance<T> corepred;

    /**
     * Full Constructor
     * 
     * @param npred Neighborhood predicate
     * @param corepred Core object predicate
     */
    public Instance(NeighborPredicate.Instance<T> npred, CorePredicate.Instance<T> corepred) {
      super();
      this.npred = npred;
      this.corepred = corepred;
    }

    /**
     * Run the actual GDBSCAN algorithm.
     * 
     * @return Clustering result
     */
    public Clustering<Model> run() {
      final DBIDs ids = npred.getIDs();
      // Setup progress logging
      final FiniteProgress progress = LOG.isVerbose() ? new FiniteProgress("Generalized DBSCAN Clustering", ids.size(), LOG) : null;
      final IndefiniteProgress clusprogress = LOG.isVerbose() ? new IndefiniteProgress("Number of clusters found", LOG) : null;
      // (Temporary) store the cluster ID assigned.
      final WritableIntegerDataStore clusterids = DataStoreUtil.makeIntegerStorage(ids, DataStoreFactory.HINT_TEMP, UNPROCESSED);
      // Note: these are not exact!
      final TIntArrayList clustersizes = new TIntArrayList();

      // Implementation Note: using Integer objects should result in
      // reduced memory use in the HashMap!
      int clusterid = FIRST_CLUSTER;
      int clustersize = 0;
      int noisesize = 0;
      // Iterate over all objects in the database.
      for(DBIDIter id = ids.iter(); id.valid(); id.advance()) {
        // Skip already processed ids.
        if(clusterids.intValue(id) != UNPROCESSED) {
          continue;
        }
        // Evaluate Neighborhood predicate
        final T neighbors = npred.getNeighbors(id);
        // Evaluate Core-Point predicate:
        if(corepred.isCorePoint(id, neighbors)) {
          clusterids.putInt(id, clusterid);
          clustersize = 1 + setbasedExpandCluster(clusterid, clusterids, neighbors, progress);
          // start next cluster on next iteration.
          clustersizes.add(clustersize);
          clustersize = 0;
          clusterid += 1;
          if(clusprogress != null) {
            clusprogress.setProcessed(clusterid, LOG);
          }
        }
        else {
          // otherwise, it's a noise point
          clusterids.putInt(id, NOISE);
          noisesize += 1;
        }
        // We've completed this element
        if(progress != null) {
          progress.incrementProcessed(LOG);
        }
      }
      // Finish progress logging.
      if(progress != null) {
        progress.ensureCompleted(LOG);
      }
      if(clusprogress != null) {
        clusprogress.setCompleted(LOG);
      }

      // Transform cluster ID mapping into a clustering result:
      ArrayList<ArrayModifiableDBIDs> clusterlists = new ArrayList<>(clusterid + 1);
      // add noise cluster storage
      clusterlists.add(DBIDUtil.newArray(noisesize));
      // add storage containers for clusters
      for(int i = 0; i < clustersizes.size(); i++) {
        clusterlists.add(DBIDUtil.newArray(clustersizes.get(i)));
      }
      // do the actual inversion
      for(DBIDIter id = ids.iter(); id.valid(); id.advance()) {
        int cluster = clusterids.intValue(id);
        clusterlists.get(cluster + 1).add(id);
      }
      clusterids.destroy();

      Clustering<Model> result = new Clustering<>("GDBSCAN", "gdbscan-clustering");
      int cid = 0;
      for(ArrayModifiableDBIDs res : clusterlists) {
        boolean isNoise = (cid == FIRST_CLUSTER);
        Cluster<Model> c = new Cluster<Model>(res, isNoise, ClusterModel.CLUSTER);
        result.addToplevelCluster(c);
        cid++;
      }
      return result;
    }

    /**
     * Set-based expand cluster implementation.
     * 
     * @param clusterid ID of the current cluster.
     * @param clusterids Current object to cluster mapping.
     * @param neighbors Neighbors acquired by initial getNeighbors call.
     * @param progress Progress logging
     * 
     * @return cluster size
     */
    protected int setbasedExpandCluster(final int clusterid, final WritableIntegerDataStore clusterids, final T neighbors, final FiniteProgress progress) {
      int clustersize = 0;
      final ArrayModifiableDBIDs activeSet = DBIDUtil.newArray();
      npred.addDBIDs(activeSet, neighbors);
      // run expandCluster as long as this set is non-empty (non-recursive
      // implementation)
      while(!activeSet.isEmpty()) {
        final DBID id = activeSet.remove(activeSet.size() - 1);
        clustersize += 1;
        // Assign object to cluster
        final int oldclus = clusterids.putInt(id, clusterid);
        if(oldclus == UNPROCESSED) {
          // expandCluster again:
          // Evaluate Neighborhood predicate
          final T newneighbors = npred.getNeighbors(id);
          // Evaluate Core-Point predicate
          if(corepred.isCorePoint(id, newneighbors)) {
            // Note: the recursion is unrolled into iteration over the active
            // set.
            npred.addDBIDs(activeSet, newneighbors);
          }
          if(progress != null) {
            progress.incrementProcessed(LOG);
          }
        }
      }
      return clustersize;
    }
  }

  /**
   * Parameterization class
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static class Parameterizer extends AbstractParameterizer {
    /**
     * Neighborhood predicate
     */
    NeighborPredicate npred = null;

    /**
     * Core point predicate
     */
    CorePredicate corepred = null;

    /**
     * Parameter for neighborhood predicate
     */
    public static final OptionID NEIGHBORHOODPRED_ID = new OptionID("gdbscan.neighborhood", "Neighborhood predicate for GDBSCAN");

    /**
     * Parameter for core predicate
     */
    public static final OptionID COREPRED_ID = new OptionID("gdbscan.core", "Core point predicate for GDBSCAN");

    @Override
    protected void makeOptions(Parameterization config) {
      // Neighborhood predicate
      ObjectParameter<NeighborPredicate> npredOpt = new ObjectParameter<>(NEIGHBORHOODPRED_ID, NeighborPredicate.class, EpsilonNeighborPredicate.class);
      if(config.grab(npredOpt)) {
        npred = npredOpt.instantiateClass(config);
      }

      // Core point predicate
      ObjectParameter<CorePredicate> corepredOpt = new ObjectParameter<>(COREPRED_ID, CorePredicate.class, MinPtsCorePredicate.class);
      if(config.grab(corepredOpt)) {
        corepred = corepredOpt.instantiateClass(config);
      }
    }

    @Override
    protected GeneralizedDBSCAN makeInstance() {
      return new GeneralizedDBSCAN(npred, corepred);
    }
  }
}
