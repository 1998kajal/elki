package de.lmu.ifi.dbs.elki.index.tree.metrical.mtreevariants;

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
import java.util.List;

import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.ids.DBIDRef;
import de.lmu.ifi.dbs.elki.database.query.distance.DistanceQuery;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.Distance;
import de.lmu.ifi.dbs.elki.index.tree.BreadthFirstEnumeration;
import de.lmu.ifi.dbs.elki.index.tree.DistanceEntry;
import de.lmu.ifi.dbs.elki.index.tree.IndexTreePath;
import de.lmu.ifi.dbs.elki.index.tree.TreeIndexPathComponent;
import de.lmu.ifi.dbs.elki.index.tree.metrical.MetricalIndexTree;
import de.lmu.ifi.dbs.elki.index.tree.metrical.mtreevariants.strategies.insert.MTreeInsert;
import de.lmu.ifi.dbs.elki.index.tree.metrical.mtreevariants.strategies.split.Assignments;
import de.lmu.ifi.dbs.elki.index.tree.metrical.mtreevariants.strategies.split.MTreeSplit;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.statistics.Counter;
import de.lmu.ifi.dbs.elki.logging.statistics.LongStatistic;
import de.lmu.ifi.dbs.elki.persistent.PageFile;

/**
 * Abstract super class for all M-Tree variants.
 * 
 * @author Elke Achtert
 * 
 * @apiviz.has SplitResult oneway - - computes
 * @apiviz.has AbstractMTreeNode oneway - - contains
 * 
 * @param <O> the type of DatabaseObject to be stored in the metrical index
 * @param <D> the type of Distance used in the metrical index
 * @param <N> the type of MetricalNode used in the metrical index
 * @param <E> the type of MetricalEntry used in the metrical index
 */
public abstract class AbstractMTree<O, D extends Distance<D>, N extends AbstractMTreeNode<O, D, N, E>, E extends MTreeEntry<D>> extends MetricalIndexTree<O, D, N, E> {
  /**
   * Debugging flag: do extra integrity checks.
   */
  protected static final boolean EXTRA_INTEGRITY_CHECKS = false;

  /**
   * Holds the instance of the trees distance function.
   */
  protected DistanceFunction<? super O, D> distanceFunction;

  /**
   * The distance query.
   */
  protected DistanceQuery<O, D> distanceQuery;

  /**
   * Splitting strategy.
   */
  protected MTreeSplit<O, D, N, E> splitStrategy;

  /**
   * Insertion strategy.
   */
  protected MTreeInsert<O, D, N, E> insertStrategy;

  /**
   * For counting the number of distance computations.
   */
  public Statistics statistics = new Statistics();

  /**
   * Constructor.
   * 
   * @param pagefile Page file
   * @param distanceQuery Distance query
   * @param distanceFunction Distance function
   * @param splitStrategy Split strategy
   * @param insertStrategy Insertion strategy
   */
  public AbstractMTree(PageFile<N> pagefile, DistanceQuery<O, D> distanceQuery, MTreeSplit<O, D, N, E> splitStrategy, MTreeInsert<O, D, N, E> insertStrategy) {
    super(pagefile);
    this.distanceQuery = distanceQuery;
    this.distanceFunction = distanceQuery.getDistanceFunction();
    this.splitStrategy = splitStrategy;
    this.insertStrategy = insertStrategy;
  }

  @Override
  public final DistanceFunction<? super O, D> getDistanceFunction() {
    return distanceFunction;
  }

  @Override
  public final DistanceQuery<O, D> getDistanceQuery() {
    return distanceQuery;
  }

  /**
   * Get the distance factory.
   * 
   * @return the distance factory used
   */
  public final D getDistanceFactory() {
    return distanceFunction.getDistanceFactory();
  }

  /**
   * Returns a string representation of this M-Tree by performing a breadth
   * first enumeration on the tree and adding the string representation of the
   * visited nodes and their entries to the result.
   * 
   * @return a string representation of this M-Tree
   */
  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    int dirNodes = 0;
    int leafNodes = 0;
    int objects = 0;
    int levels = 0;

    N node = getRoot();

    while (!node.isLeaf()) {
      if (node.getNumEntries() > 0) {
        E entry = node.getEntry(0);
        node = getNode(entry);
        levels++;
      }
    }

    BreadthFirstEnumeration<N, E> enumeration = new BreadthFirstEnumeration<>(this, getRootPath());
    while (enumeration.hasMoreElements()) {
      IndexTreePath<E> path = enumeration.nextElement();
      E entry = path.getLastPathComponent().getEntry();
      if (entry.isLeafEntry()) {
        objects++;
        result.append("\n    ").append(entry.toString());
      } else {
        node = getNode(entry);
        result.append("\n\n").append(node).append(", numEntries = ").append(node.getNumEntries());
        result.append("\n").append(entry.toString());

        if (node.isLeaf()) {
          leafNodes++;
        } else {
          dirNodes++;
        }
      }
    }

    result.append(getClass().getName()).append(" hat ").append((levels + 1)).append(" Ebenen \n");
    result.append("DirCapacity = ").append(dirCapacity).append("\n");
    result.append("LeafCapacity = ").append(leafCapacity).append("\n");
    result.append(dirNodes).append(" Directory Nodes \n");
    result.append(leafNodes).append(" Leaf Nodes \n");
    result.append(objects).append(" Objects \n");

    // PageFileUtil.appendPageFileStatistics(result, getPageFileStatistics());
    return result.toString();
  }

  /**
   * Inserts the specified object into this M-Tree.
   * 
   * @param entry the entry to be inserted
   * @param withPreInsert if this flag is true, the preInsert method will be
   *        called before inserting the object
   */
  // todo: implement a bulk load for M-Tree and remove this method
  public void insert(E entry, boolean withPreInsert) {
    if (getLogger().isDebugging()) {
      getLogger().debugFine("insert " + entry.getRoutingObjectID() + "\n");
    }

    if (!initialized) {
      initialize(entry);
    }

    // choose subtree for insertion
    IndexTreePath<E> subtree = insertStrategy.choosePath(this, entry);
    if (getLogger().isDebugging()) {
      getLogger().debugFine("insertion-subtree " + subtree + "\n");
    }

    // determine parent distance
    E parentEntry = subtree.getLastPathComponent().getEntry();
    D parentDistance = distance(parentEntry.getRoutingObjectID(), entry.getRoutingObjectID());
    entry.setParentDistance(parentDistance);

    // create leaf entry and do pre insert
    if (withPreInsert) {
      preInsert(entry);
    }

    // get parent node
    N parent = getNode(parentEntry);
    parent.addLeafEntry(entry);
    writeNode(parent);

    // adjust the tree from subtree to root
    adjustTree(subtree);

    // test
    if (EXTRA_INTEGRITY_CHECKS) {
      if (withPreInsert) {
        getRoot().integrityCheck(this, getRootEntry());
      }
    }
  }

  /**
   * Bulk insert.
   * 
   * @param entries Entries to insert
   */
  public void insertAll(List<E> entries) {
    if (!initialized && entries.size() > 0) {
      initialize(entries.get(0));
    }
    for (E entry : entries) {
      insert(entry, false);
    }
  }

  @Override
  protected final void createEmptyRoot(E exampleLeaf) {
    N root = createNewLeafNode();
    writeNode(root);
  }

  /**
   * Sorts the entries of the specified node according to their minimum distance
   * to the specified object.
   * 
   * @param node the node
   * @param q the id of the object
   * @return a list of the sorted entries
   */
  protected final List<DistanceEntry<D, E>> getSortedEntries(N node, DBID q) {
    List<DistanceEntry<D, E>> result = new ArrayList<>();

    for (int i = 0; i < node.getNumEntries(); i++) {
      E entry = node.getEntry(i);
      D distance = distance(entry.getRoutingObjectID(), q);
      D radius = entry.getCoveringRadius();
      D minDist = radius.compareTo(distance) > 0 ? getDistanceFactory().nullDistance() : distance.minus(radius);

      result.add(new DistanceEntry<>(entry, minDist, i));
    }

    Collections.sort(result);
    return result;
  }

  /**
   * Returns the distance between the two specified ids.
   * 
   * @param id1 the first id
   * @param id2 the second id
   * @return the distance between the two specified ids
   */
  public final D distance(DBIDRef id1, DBIDRef id2) {
    if (id1 == null || id2 == null) {
      return getDistanceFactory().undefinedDistance();
    }
    statistics.countDistanceCalculation();
    return distanceQuery.distance(id1, id2);
  }

  /**
   * Creates a new directory entry representing the specified node.
   * 
   * @param node the node to be represented by the new entry
   * @param routingObjectID the id of the routing object of the node
   * @param parentDistance the distance from the routing object of the node to
   *        the routing object of the parent node
   * @return the newly created directory entry
   */
  protected abstract E createNewDirectoryEntry(N node, DBID routingObjectID, D parentDistance);

  /**
   * Adjusts the tree after insertion of some nodes.
   * 
   * @param subtree the subtree to be adjusted
   */
  private void adjustTree(IndexTreePath<E> subtree) {
    if (getLogger().isDebugging()) {
      getLogger().debugFine("Adjust tree " + subtree + "\n");
    }

    // get the root of the subtree
    Integer nodeIndex = subtree.getLastPathComponent().getIndex();
    N node = getNode(subtree.getLastPathComponent().getEntry());

    // overflow in node; split the node
    if (hasOverflow(node)) {
      // do the split
      Assignments<D, E> assignments = splitStrategy.split(this, node);
      final N newNode = node.isLeaf() ? createNewLeafNode() : createNewDirectoryNode();

      List<E> entries1 = new ArrayList<>(assignments.getFirstAssignments().size());
      List<E> entries2 = new ArrayList<>(assignments.getSecondAssignments().size());
      // Store final parent distances:
      for (DistanceEntry<D, E> ent : assignments.getFirstAssignments()) {
        final E e = ent.getEntry();
        e.setParentDistance(ent.getDistance());
        entries1.add(e);
      }
      for (DistanceEntry<D, E> ent : assignments.getSecondAssignments()) {
        final E e = ent.getEntry();
        e.setParentDistance(ent.getDistance());
        entries2.add(e);
      }
      node.splitTo(newNode, entries1, entries2);

      // write changes to file
      writeNode(node);
      writeNode(newNode);

      if (getLogger().isDebugging()) {
        String msg = "Split Node " + node.getPageID() + " (" + this.getClass() + ")\n" + "      newNode " + newNode.getPageID() + "\n" + "      firstPromoted " + assignments.getFirstRoutingObject() + "\n" + "      firstAssignments(" + node.getPageID() + ") " + assignments.getFirstAssignments() + "\n" + "      firstCR " + assignments.getFirstCoveringRadius() + "\n" + "      secondPromoted " + assignments.getSecondRoutingObject() + "\n" + "      secondAssignments(" + newNode.getPageID() + ") " + assignments.getSecondAssignments() + "\n" + "      secondCR " + assignments.getSecondCoveringRadius() + "\n";
        getLogger().debugFine(msg);
      }

      // if root was split: create a new root that points the two split
      // nodes
      if (isRoot(node)) {
        // FIXME: stimmen die parentDistance der Kinder in node & splitNode?
        IndexTreePath<E> newRootPath = createNewRoot(node, newNode, assignments.getFirstRoutingObject(), assignments.getSecondRoutingObject());
        adjustTree(newRootPath);
      }
      // node is not root
      else {
        // get the parent and add the new split node
        E parentEntry = subtree.getParentPath().getLastPathComponent().getEntry();
        N parent = getNode(parentEntry);
        if (getLogger().isDebugging()) {
          getLogger().debugFine("parent " + parent);
        }
        D parentDistance2 = distance(parentEntry.getRoutingObjectID(), assignments.getSecondRoutingObject());
        // logger.warning("parent: "+parent.toString()+" split: " +
        // splitNode.toString()+ " dist:"+parentDistance2);
        parent.addDirectoryEntry(createNewDirectoryEntry(newNode, assignments.getSecondRoutingObject(), parentDistance2));

        // adjust the entry representing the (old) node, that has been split
        D parentDistance1 = distance(parentEntry.getRoutingObjectID(), assignments.getFirstRoutingObject());
        // logger.warning("parent: "+parent.toString()+" node: " +
        // node.toString()+ " dist:"+parentDistance1);
        node.adjustEntry(parent.getEntry(nodeIndex), assignments.getFirstRoutingObject(), parentDistance1, this);

        // write changes in parent to file
        writeNode(parent);
        adjustTree(subtree.getParentPath());
      }
    }
    // no overflow, only adjust parameters of the entry representing the
    // node
    else {
      if (!isRoot(node)) {
        E parentEntry = subtree.getParentPath().getLastPathComponent().getEntry();
        N parent = getNode(parentEntry);
        int index = subtree.getLastPathComponent().getIndex();
        E entry = parent.getEntry(index);
        node.adjustEntry(entry, entry.getRoutingObjectID(), entry.getParentDistance(), this);
        // write changes in parent to file
        writeNode(parent);
        adjustTree(subtree.getParentPath());
      }
      // root level is reached
      else {
        E rootEntry = getRootEntry();
        node.adjustEntry(rootEntry, rootEntry.getRoutingObjectID(), rootEntry.getParentDistance(), this);
      }
    }
  }

  /**
   * Returns true if in the specified node an overflow has occurred, false
   * otherwise.
   * 
   * @param node the node to be tested for overflow
   * @return true if in the specified node an overflow has occurred, false
   *         otherwise
   */
  private boolean hasOverflow(N node) {
    if (node.isLeaf()) {
      return node.getNumEntries() == leafCapacity;
    }

    return node.getNumEntries() == dirCapacity;
  }

  /**
   * Creates a new root node that points to the two specified child nodes and
   * return the path to the new root.
   * 
   * @param oldRoot the old root of this RTree
   * @param newNode the new split node
   * @param firstRoutingObjectID the id of the routing objects of the first
   *        child node
   * @param secondRoutingObjectID the id of the routing objects of the second
   *        child node
   * @return the path to the new root node that points to the two specified
   *         child nodes
   */
  private IndexTreePath<E> createNewRoot(final N oldRoot, final N newNode, DBID firstRoutingObjectID, DBID secondRoutingObjectID) {
    N root = createNewDirectoryNode();
    writeNode(root);

    // switch the ids
    oldRoot.setPageID(root.getPageID());
    if (!oldRoot.isLeaf()) {
      // FIXME: what is happening here?
      for (int i = 0; i < oldRoot.getNumEntries(); i++) {
        N node = getNode(oldRoot.getEntry(i));
        writeNode(node);
      }
    }

    root.setPageID(getRootID());
    // FIXME: doesn't the root by definition not have a routing object?
    // D parentDistance1 = distance(getRootEntry().getRoutingObjectID(),
    // firstRoutingObjectID);
    // D parentDistance2 = distance(getRootEntry().getRoutingObjectID(),
    // secondRoutingObjectID);
    E oldRootEntry = createNewDirectoryEntry(oldRoot, firstRoutingObjectID, null);
    E newRootEntry = createNewDirectoryEntry(newNode, secondRoutingObjectID, null);
    root.addDirectoryEntry(oldRootEntry);
    root.addDirectoryEntry(newRootEntry);

    // logger.warning("new root: " + getRootEntry().toString() + " childs: " +
    // oldRootEntry.toString() + "," + newRootEntry.toString() + " dists: " +
    // parentDistance1 + ", " + parentDistance2);

    writeNode(root);
    writeNode(oldRoot);
    writeNode(newNode);
    if (getLogger().isDebugging()) {
      String msg = "Create new Root: ID=" + root.getPageID();
      msg += "\nchild1 " + oldRoot;
      msg += "\nchild2 " + newNode;
      getLogger().debugFine(msg);
    }

    return new IndexTreePath<>(new TreeIndexPathComponent<>(getRootEntry(), null));
  }

  @Override
  public List<E> getLeaves() {
    List<E> result = new ArrayList<>();
    BreadthFirstEnumeration<N, E> enumeration = new BreadthFirstEnumeration<>(this, getRootPath());
    while (enumeration.hasMoreElements()) {
      IndexTreePath<E> path = enumeration.nextElement();
      E entry = path.getLastPathComponent().getEntry();
      if (!entry.isLeafEntry()) {
        // TODO: any way to skip unnecessary reads?
        N node = getNode(entry);
        if (node.isLeaf()) {
          result.add(entry);
        }
      }
    }
    return result;
  }

  /**
   * FIXME: expensive depth computation by following a path.
   * 
   * @return depth
   */
  public int getHeight() {
    int levels = 0;
    N node = getRoot();

    while (!node.isLeaf()) {
      if (node.getNumEntries() > 0) {
        E entry = node.getEntry(0);
        node = getNode(entry);
        levels++;
      }
    }
    return levels;
  }

  @Override
  public void logStatistics() {
    super.logStatistics();
    Logging log = getLogger();
    if (log.isStatistics()) {
      log.statistics(new LongStatistic(this.getClass().getName() + ".height", getHeight()));
      statistics.logStatistics();
    }
  }

  /**
   * Class for tracking some statistics.
   * 
   * @author Erich Schubert
   */
  public class Statistics {
    /**
     * For counting the number of distance computations.
     */
    protected final Counter distanceCalcs;

    /**
     * For counting the number of knn queries answered.
     */
    protected final Counter knnQueries;

    /**
     * For counting the number of range queries answered.
     */
    protected final Counter rangeQueries;

    /**
     * Constructor.
     */
    public Statistics() {
      super();
      Logging log = getLogger();
      distanceCalcs = log.isStatistics() ? log.newCounter(this.getClass().getName() + ".distancecalcs") : null;
      knnQueries = log.isStatistics() ? log.newCounter(this.getClass().getName() + ".knnqueries") : null;
      rangeQueries = log.isStatistics() ? log.newCounter(this.getClass().getName() + ".rangequeries") : null;
    }

    /**
     * Count a distance computation.
     */
    public void countDistanceCalculation() {
      if (distanceCalcs != null) {
        distanceCalcs.increment();
      }
    }

    /**
     * Count a knn query invocation.
     */
    public void countKNNQuery() {
      if (knnQueries != null) {
        knnQueries.increment();
      }
    }

    /**
     * Count a range query invocation.
     */
    public void countRangeQuery() {
      if (rangeQueries != null) {
        rangeQueries.increment();
      }
    }

    /**
     * Log the statistics.
     */
    public void logStatistics() {
      Logging log = getLogger();
      if (statistics.distanceCalcs != null) {
        log.statistics(statistics.distanceCalcs);
      }
      if (statistics.knnQueries != null) {
        log.statistics(statistics.knnQueries);
      }
      if (statistics.rangeQueries != null) {
        log.statistics(statistics.rangeQueries);
      }
    }
  }

}
