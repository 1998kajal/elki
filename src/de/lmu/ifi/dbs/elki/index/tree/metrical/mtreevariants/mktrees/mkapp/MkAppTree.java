package de.lmu.ifi.dbs.elki.index.tree.metrical.mtreevariants.mktrees.mkapp;

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
import java.util.Map;

import de.lmu.ifi.dbs.elki.database.ids.ArrayModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDRef;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.ids.ModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.query.distance.DistanceQuery;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distanceresultlist.DistanceDBIDResult;
import de.lmu.ifi.dbs.elki.distance.distanceresultlist.DistanceDBIDResultIter;
import de.lmu.ifi.dbs.elki.distance.distanceresultlist.GenericDistanceDBIDList;
import de.lmu.ifi.dbs.elki.distance.distanceresultlist.KNNResult;
import de.lmu.ifi.dbs.elki.distance.distancevalue.NumberDistance;
import de.lmu.ifi.dbs.elki.index.tree.LeafEntry;
import de.lmu.ifi.dbs.elki.index.tree.metrical.mtreevariants.mktrees.AbstractMkTree;
import de.lmu.ifi.dbs.elki.index.tree.query.GenericMTreeDistanceSearchCandidate;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.math.statistics.PolynomialRegression;
import de.lmu.ifi.dbs.elki.persistent.PageFile;
import de.lmu.ifi.dbs.elki.utilities.datastructures.heap.Heap;
import de.lmu.ifi.dbs.elki.utilities.datastructures.heap.UpdatableHeap;

/**
 * MkAppTree is a metrical index structure based on the concepts of the M-Tree
 * supporting efficient processing of reverse k nearest neighbor queries for
 * parameter k < kmax.
 * 
 * @author Elke Achtert
 * 
 * @apiviz.has MkAppTreeNode oneway - - contains
 * 
 * @param <O> the type of DatabaseObject to be stored in the metrical index
 * @param <D> the type of NumberDistance used in the metrical index
 */
public class MkAppTree<O, D extends NumberDistance<D, ?>> extends AbstractMkTree<O, D, MkAppTreeNode<O, D>, MkAppEntry<D>> {
  /**
   * The logger for this class.
   */
  private static final Logging LOG = Logging.getLogger(MkAppTree.class);

  /**
   * Parameter k.
   */
  private int k_max;

  /**
   * Parameter p.
   */
  private int p;

  /**
   * Flag log.
   */
  private boolean log;

  /**
   * Constructor.
   * 
   * @param pageFile Page file
   * @param distanceQuery Distance query
   * @param distanceFunction Distance function
   * @param k_max Maximum value of k supported
   * @param p Parameter p
   * @param log Logspace flag
   */
  public MkAppTree(PageFile<MkAppTreeNode<O, D>> pageFile, DistanceQuery<O, D> distanceQuery, DistanceFunction<O, D> distanceFunction, int k_max, int p, boolean log) {
    super(pageFile, distanceQuery, distanceFunction);
    this.k_max = k_max;
    this.p = p;
    this.log = log;
  }

  /**
   * @throws UnsupportedOperationException since this operation is not supported
   */
  @Override
  public void insert(MkAppEntry<D> id, boolean withPreInsert) {
    throw new UnsupportedOperationException("Insertion of single objects is not supported!");
  }

  /**
   * @throws UnsupportedOperationException since this operation is not supported
   */
  @Override
  protected void preInsert(MkAppEntry<D> entry) {
    throw new UnsupportedOperationException("Insertion of single objects is not supported!");
  }

  /**
   * Inserts the specified objects into this MkApp-Tree.
   * 
   * @param entries the entries to be inserted
   */
  @Override
  public void insertAll(List<MkAppEntry<D>> entries) {
    if(entries.isEmpty()) {
      return;
    }

    if(LOG.isDebugging()) {
      LOG.debugFine("insert " + entries + "\n");
    }

    if(!initialized) {
      initialize(entries.get(0));
    }

    ModifiableDBIDs ids = DBIDUtil.newArray(entries.size());

    // insert
    for(MkAppEntry<D> entry : entries) {
      ids.add(entry.getRoutingObjectID());
      // insert the object
      super.insert(entry, false);
    }

    // do batch nn
    Map<DBID, KNNResult<D>> knnLists = batchNN(getRoot(), ids, k_max + 1);
    
    // adjust the knn distances
    adjustApproximatedKNNDistances(getRootEntry(), knnLists);

    if(EXTRA_INTEGRITY_CHECKS) {
      getRoot().integrityCheck(this, getRootEntry());
    }
  }

  /**
   * Performs a reverse k-nearest neighbor query for the given object ID. The
   * query result is in ascending order to the distance to the query object.
   * 
   * @param id the query object id
   * @param k the number of nearest neighbors to be returned
   * @return a List of the query results
   */
  @Override
  public DistanceDBIDResult<D> reverseKNNQuery(DBIDRef id, int k) {
    GenericDistanceDBIDList<D> result = new GenericDistanceDBIDList<>();
    final Heap<GenericMTreeDistanceSearchCandidate<D>> pq = new UpdatableHeap<>();

    // push root
    pq.add(new GenericMTreeDistanceSearchCandidate<D>(getDistanceQuery().nullDistance(), getRootID(), null, null));

    // search in tree
    while(!pq.isEmpty()) {
      GenericMTreeDistanceSearchCandidate<D> pqNode = pq.poll();
      // FIXME: cache the distance to the routing object in the queue node!

      MkAppTreeNode<O, D> node = getNode(pqNode.nodeID);

      // directory node
      if(!node.isLeaf()) {
        for(int i = 0; i < node.getNumEntries(); i++) {
          MkAppEntry<D> entry = node.getEntry(i);
          D distance = getDistanceQuery().distance(entry.getRoutingObjectID(), id);
          D minDist = entry.getCoveringRadius().compareTo(distance) > 0 ? getDistanceQuery().nullDistance() : distance.minus(entry.getCoveringRadius());

          double approxValue = log ? Math.exp(entry.approximatedValueAt(k)) : entry.approximatedValueAt(k);
          if(approxValue < 0) {
            approxValue = 0;
          }
          D approximatedKnnDist = getDistanceQuery().getDistanceFactory().fromDouble(approxValue);

          if(minDist.compareTo(approximatedKnnDist) <= 0) {
            pq.add(new GenericMTreeDistanceSearchCandidate<D>(minDist, getPageID(entry), entry.getRoutingObjectID(), null));
          }
        }
      }
      // data node
      else {
        for(int i = 0; i < node.getNumEntries(); i++) {
          MkAppLeafEntry<D> entry = (MkAppLeafEntry<D>) node.getEntry(i);
          D distance = getDistanceQuery().distance(entry.getRoutingObjectID(), id);
          double approxValue = log ? StrictMath.exp(entry.approximatedValueAt(k)) : entry.approximatedValueAt(k);
          if(approxValue < 0) {
            approxValue = 0;
          }
          D approximatedKnnDist = getDistanceQuery().getDistanceFactory().fromDouble(approxValue);

          if(distance.compareTo(approximatedKnnDist) <= 0) {
            result.add(distance, entry.getRoutingObjectID());
          }
        }
      }
    }
    return result;
  }

  /**
   * Returns the value of the k_max parameter.
   * 
   * @return the value of the k_max parameter
   */
  public int getK_max() {
    return k_max;
  }

  /**
   * Determines the maximum and minimum number of entries in a node.
   */
  @Override
  protected void initializeCapacities(MkAppEntry<D> exampleLeaf) {
    int distanceSize = exampleLeaf.getParentDistance().externalizableSize();

    // overhead = index(4), numEntries(4), id(4), isLeaf(0.125)
    double overhead = 12.125;
    if(getPageSize() - overhead < 0) {
      throw new RuntimeException("Node size of " + getPageSize() + " Bytes is chosen too small!");
    }

    // dirCapacity = (file.getPageSize() - overhead) / (nodeID + objectID +
    // coveringRadius + parentDistance + approx) + 1
    dirCapacity = (int) (getPageSize() - overhead) / (4 + 4 + distanceSize + distanceSize + (p + 1) * 4 + 2) + 1;

    if(dirCapacity <= 1) {
      throw new RuntimeException("Node size of " + getPageSize() + " Bytes is chosen too small!");
    }

    if(dirCapacity < 10) {
      LOG.warning("Page size is choosen too small! Maximum number of entries " + "in a directory node = " + (dirCapacity - 1));
    }

    // leafCapacity = (file.getPageSize() - overhead) / (objectID +
    // parentDistance +
    // approx) + 1
    leafCapacity = (int) (getPageSize() - overhead) / (4 + distanceSize + (p + 1) * 4 + 2) + 1;

    if(leafCapacity <= 1) {
      throw new RuntimeException("Node size of " + getPageSize() + " Bytes is chosen too small!");
    }

    if(leafCapacity < 10) {
      LOG.warning("Page size is choosen too small! Maximum number of entries " + "in a leaf node = " + (leafCapacity - 1));
    }

    initialized = true;

    if(LOG.isVerbose()) {
      LOG.verbose("Directory Capacity: " + (dirCapacity - 1) + "\nLeaf Capacity:    " + (leafCapacity - 1));
    }
  }

  private List<D> getMeanKNNList(DBIDs ids, Map<DBID, KNNResult<D>> knnLists) {
    double[] means = new double[k_max];
    for(DBIDIter iter = ids.iter(); iter.valid(); iter.advance()) {
      DBID id = DBIDUtil.deref(iter);
      KNNResult<D> knns = knnLists.get(id);
      int k = 0;
      for(DistanceDBIDResultIter<D> it = knns.iter(); k < k_max && it.valid(); it.advance(), k++) {
        means[k] += it.getDistance().doubleValue();
      }
    }

    List<D> result = new ArrayList<>();
    for(int k = 0; k < k_max; k++) {
      means[k] /= ids.size();
      result.add(getDistanceQuery().getDistanceFactory().fromDouble(means[k]));
    }

    return result;
  }

  /**
   * Adjusts the knn distance in the subtree of the specified root entry.
   * 
   * @param entry the root entry of the current subtree
   * @param knnLists a map of knn lists for each leaf entry
   */
  private void adjustApproximatedKNNDistances(MkAppEntry<D> entry, Map<DBID, KNNResult<D>> knnLists) {
    MkAppTreeNode<O, D> node = getNode(entry);

    if(node.isLeaf()) {
      for(int i = 0; i < node.getNumEntries(); i++) {
        MkAppLeafEntry<D> leafEntry = (MkAppLeafEntry<D>) node.getEntry(i);
        // approximateKnnDistances(leafEntry,
        // getKNNList(leafEntry.getRoutingObjectID(), knnLists));
        PolynomialApproximation approx = approximateKnnDistances(getMeanKNNList(leafEntry.getDBID(), knnLists));
        leafEntry.setKnnDistanceApproximation(approx);
      }
    }
    else {
      for(int i = 0; i < node.getNumEntries(); i++) {
        MkAppEntry<D> dirEntry = node.getEntry(i);
        adjustApproximatedKNNDistances(dirEntry, knnLists);
      }
    }

    // PolynomialApproximation approx1 = node.knnDistanceApproximation();
    ArrayModifiableDBIDs ids = DBIDUtil.newArray();
    leafEntryIDs(node, ids);
    PolynomialApproximation approx = approximateKnnDistances(getMeanKNNList(ids, knnLists));
    entry.setKnnDistanceApproximation(approx);
  }

  /**
   * Determines the ids of the leaf entries stored in the specified subtree.
   * 
   * @param node the root of the subtree
   * @param result the result list containing the ids of the leaf entries stored
   *        in the specified subtree
   */
  private void leafEntryIDs(MkAppTreeNode<O, D> node, ModifiableDBIDs result) {
    if(node.isLeaf()) {
      for(int i = 0; i < node.getNumEntries(); i++) {
        MkAppEntry<D> entry = node.getEntry(i);
        result.add(((LeafEntry) entry).getDBID());
      }
    }
    else {
      for(int i = 0; i < node.getNumEntries(); i++) {
        MkAppTreeNode<O, D> childNode = getNode(node.getEntry(i));
        leafEntryIDs(childNode, result);
      }
    }
  }

  /**
   * Computes the polynomial approximation of the specified knn-distances.
   * 
   * @param knnDistances the knn-distances of the leaf entry
   * @return the polynomial approximation of the specified knn-distances.
   */
  private PolynomialApproximation approximateKnnDistances(List<D> knnDistances) {
    StringBuilder msg = new StringBuilder();

    // count the zero distances (necessary of log-log space is used)
    int k_0 = 0;
    if(log) {
      for(int i = 0; i < k_max; i++) {
        double dist = knnDistances.get(i).doubleValue();
        if(dist == 0) {
          k_0++;
        }
        else {
          break;
        }
      }
    }

    de.lmu.ifi.dbs.elki.math.linearalgebra.Vector x = new de.lmu.ifi.dbs.elki.math.linearalgebra.Vector(k_max - k_0);
    de.lmu.ifi.dbs.elki.math.linearalgebra.Vector y = new de.lmu.ifi.dbs.elki.math.linearalgebra.Vector(k_max - k_0);

    for(int k = 0; k < k_max - k_0; k++) {
      if(log) {
        x.set(k, Math.log(k + k_0));
        y.set(k, Math.log(knnDistances.get(k + k_0).doubleValue()));
      }
      else {
        x.set(k, k + k_0);
        y.set(k, knnDistances.get(k + k_0).doubleValue());
      }
    }

    PolynomialRegression regression = new PolynomialRegression(y, x, p);
    PolynomialApproximation approximation = new PolynomialApproximation(regression.getEstimatedCoefficients().getArrayCopy());

    if(LOG.isDebugging()) {
      msg.append("approximation ").append(approximation);
      LOG.debugFine(msg.toString());
    }
    return approximation;

  }

  /**
   * Creates a new leaf node with the specified capacity.
   * 
   * @return a new leaf node
   */
  @Override
  protected MkAppTreeNode<O, D> createNewLeafNode() {
    return new MkAppTreeNode<O, D>(leafCapacity, true);
  }

  /**
   * Creates a new directory node with the specified capacity.
   * 
   * @return a new directory node
   */
  @Override
  protected MkAppTreeNode<O, D> createNewDirectoryNode() {
    return new MkAppTreeNode<O, D>(dirCapacity, false);
  }

  /**
   * Creates a new directory entry representing the specified node.
   * 
   * @param node the node to be represented by the new entry
   * @param routingObjectID the id of the routing object of the node
   * @param parentDistance the distance from the routing object of the node to
   *        the routing object of the parent node
   */
  @Override
  protected MkAppEntry<D> createNewDirectoryEntry(MkAppTreeNode<O, D> node, DBID routingObjectID, D parentDistance) {
    return new MkAppDirectoryEntry<D>(routingObjectID, parentDistance, node.getPageID(), node.coveringRadius(routingObjectID, this), null);
  }

  /**
   * Creates an entry representing the root node.
   * 
   * @return an entry representing the root node
   */
  @Override
  protected MkAppEntry<D> createRootEntry() {
    return new MkAppDirectoryEntry<D>(null, null, 0, null, null);
  }

  @Override
  protected Logging getLogger() {
    return LOG;
  }
}