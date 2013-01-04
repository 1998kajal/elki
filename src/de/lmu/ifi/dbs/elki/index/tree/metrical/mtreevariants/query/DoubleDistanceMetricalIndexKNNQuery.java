package de.lmu.ifi.dbs.elki.index.tree.metrical.mtreevariants.query;

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

import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.query.distance.DistanceQuery;
import de.lmu.ifi.dbs.elki.database.query.knn.AbstractDistanceKNNQuery;
import de.lmu.ifi.dbs.elki.distance.distancefunction.PrimitiveDoubleDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distanceresultlist.DoubleDistanceKNNHeap;
import de.lmu.ifi.dbs.elki.distance.distanceresultlist.KNNResult;
import de.lmu.ifi.dbs.elki.distance.distancevalue.DoubleDistance;
import de.lmu.ifi.dbs.elki.index.tree.DirectoryEntry;
import de.lmu.ifi.dbs.elki.index.tree.metrical.mtreevariants.AbstractMTree;
import de.lmu.ifi.dbs.elki.index.tree.metrical.mtreevariants.AbstractMTreeNode;
import de.lmu.ifi.dbs.elki.index.tree.metrical.mtreevariants.MTreeEntry;
import de.lmu.ifi.dbs.elki.index.tree.query.DoubleMTreeDistanceSearchCandidate;
import de.lmu.ifi.dbs.elki.utilities.datastructures.heap.ComparableMinHeap;

/**
 * Instance of a KNN query for a particular spatial index.
 * 
 * @author Erich Schubert
 * 
 * @apiviz.uses AbstractMTree
 * 
 * @param <O> Object type
 */
public class DoubleDistanceMetricalIndexKNNQuery<O> extends AbstractDistanceKNNQuery<O, DoubleDistance> {
  /**
   * The index to use
   */
  protected final AbstractMTree<O, DoubleDistance, ?, ?> index;

  /**
   * Distance function
   */
  protected PrimitiveDoubleDistanceFunction<? super O> distf;

  /**
   * Constructor.
   * 
   * @param index Index to use
   * @param distanceQuery Distance query used
   * @param distf Distance function
   */
  public DoubleDistanceMetricalIndexKNNQuery(AbstractMTree<O, DoubleDistance, ?, ?> index, DistanceQuery<O, DoubleDistance> distanceQuery, PrimitiveDoubleDistanceFunction<? super O> distf) {
    super(distanceQuery);
    this.index = index;
    this.distf = distf;
  }

  @Override
  public KNNResult<DoubleDistance> getKNNForObject(O q, int k) {
    if (k < 1) {
      throw new IllegalArgumentException("At least one object has to be requested!");
    }

    DoubleDistanceKNNHeap knnList = new DoubleDistanceKNNHeap(k);
    double d_k = Double.POSITIVE_INFINITY;

    final ComparableMinHeap<DoubleMTreeDistanceSearchCandidate> pq = new ComparableMinHeap<>();

    // Push the root node
    pq.add(new DoubleMTreeDistanceSearchCandidate(0, index.getRootID(), null, 0));

    // search in tree
    while (!pq.isEmpty()) {
      DoubleMTreeDistanceSearchCandidate pqNode = pq.poll();
      DBID id_p = pqNode.routingObjectID;
      double d1 = pqNode.routingDistance;

      if (knnList.size() >= k && pqNode.mindist > d_k) {
        break;
      }

      AbstractMTreeNode<?, DoubleDistance, ?, ?> node = index.getNode(pqNode.nodeID);

      // directory node
      if (!node.isLeaf()) {
        for (int i = 0; i < node.getNumEntries(); i++) {
          final MTreeEntry<DoubleDistance> entry = node.getEntry(i);
          final DBID id_i = entry.getRoutingObjectID();
          double or_i = entry.getCoveringRadius().doubleValue();
          double d2 = id_p != null ? entry.getParentDistance().doubleValue() : 0;
          double diff = Math.abs(d1 - d2);

          if (diff <= d_k + or_i) {
            final O ob_i = relation.get(id_i);
            double d3 = distf.doubleDistance(ob_i, q);
            double d_min = Math.max(d3 - or_i, 0);
            if (d_min <= d_k) {
              pq.add(new DoubleMTreeDistanceSearchCandidate(d_min, ((DirectoryEntry) entry).getPageID(), id_i, d3));
            }
          }
        }
      }
      // data node
      else {
        for (int i = 0; i < node.getNumEntries(); i++) {
          final MTreeEntry<DoubleDistance> entry = node.getEntry(i);
          final DBID id_i = entry.getRoutingObjectID();
          double d2 = id_p != null ? entry.getParentDistance().doubleValue() : 0;
          double diff = Math.abs(d1 - d2);

          if (diff <= d_k) {
            final O o_i = relation.get(id_i);
            double d3 = distf.doubleDistance(o_i, q);
            if (d3 <= d_k) {
              knnList.add(d3, id_i);
              d_k = knnList.doubleKNNDistance();
            }
          }
        }
      }
    }
    return knnList.toKNNList();
  }
}
