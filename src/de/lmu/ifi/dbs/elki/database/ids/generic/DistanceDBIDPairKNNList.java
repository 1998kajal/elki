package de.lmu.ifi.dbs.elki.database.ids.generic;

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

import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDRef;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.distance.DistanceDBIDPair;
import de.lmu.ifi.dbs.elki.database.ids.distance.DistanceDBIDListIter;
import de.lmu.ifi.dbs.elki.database.ids.distance.KNNHeap;
import de.lmu.ifi.dbs.elki.database.ids.distance.KNNList;
import de.lmu.ifi.dbs.elki.distance.distancevalue.Distance;
import de.lmu.ifi.dbs.elki.utilities.datastructures.heap.Heap;

/**
 * Finalized KNN List.
 * 
 * @author Erich Schubert
 * 
 * @param <D> Distance type
 */
public class DistanceDBIDPairKNNList<D extends Distance<D>> implements KNNList<D> {
  /**
   * The value of k this was materialized for.
   */
  private final int k;

  /**
   * The actual data array.
   */
  private final Object[] data;

  /**
   * Constructor, to be called from KNNHeap only. Use {@link KNNHeap#toKNNList}
   * instead!
   * 
   * @param heap Calling heap
   */
  protected DistanceDBIDPairKNNList(KNNHeap<D> heap) {
    super();
    this.data = new Object[heap.size()];
    this.k = heap.getK();
    // Get sorted data from heap; but in reverse.
    int i = heap.size();
    while (heap.size() > 0) {
      i--;
      assert (i >= 0);
      data[i] = heap.poll();
    }
    assert (data.length == 0 || data[0] != null);
    assert (heap.size() == 0);
  }

  /**
   * Constructor. With a KNNHeap, use {@link KNNHeap#toKNNList} instead!
   * 
   * @param heap Calling heap
   * @param k K value
   */
  public DistanceDBIDPairKNNList(Heap<? extends DistanceDBIDPair<D>> heap, int k) {
    super();
    this.data = new Object[heap.size()];
    this.k = k;
    assert (heap.size() >= this.k) : "Heap doesn't contain enough objects!";
    // Get sorted data from heap; but in reverse.
    int i = heap.size();
    while (!heap.isEmpty()) {
      i--;
      assert (i >= 0);
      data[i] = heap.poll();
    }
    assert (data.length == 0 || data[0] != null);
    assert (heap.size() == 0);
  }

  @Override
  public int getK() {
    return k;
  }

  @Override
  public D getKNNDistance() {
    return get(getK() - 1).getDistance();
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append("kNNList[");
    for (DistanceDBIDListIter<D> iter = this.iter(); iter.valid();) {
      buf.append(iter.getDistance()).append(':').append(DBIDUtil.toString(iter));
      iter.advance();
      if (iter.valid()) {
        buf.append(',');
      }
    }
    buf.append(']');
    return buf.toString();
  }

  @SuppressWarnings("unchecked")
  @Override
  public DistanceDBIDPair<D> get(int index) {
    return (DistanceDBIDPair<D>) data[index];
  }

  @Override
  public DistanceDBIDListIter<D> iter() {
    return new Itr();
  }

  @Override
  public int size() {
    return data.length;
  }

  @Override
  public boolean contains(DBIDRef o) {
    for (DBIDIter iter = iter(); iter.valid(); iter.advance()) {
      if (DBIDUtil.equal(iter, o)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  /**
   * Iterator.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  private class Itr implements DistanceDBIDListIter<D> {
    /**
     * Cursor position.
     */
    private int pos = 0;

    @Override
    public int internalGetIndex() {
      return get(pos).internalGetIndex();
    }

    @Override
    public boolean valid() {
      return pos < data.length;
    }

    @Override
    public void advance() {
      pos++;
    }

    @Override
    public D getDistance() {
      return get(pos).getDistance();
    }

    @Override
    public DistanceDBIDPair<D> getDistancePair() {
      return get(pos);
    }

    @Override
    public int getOffset() {
      return pos;
    }

    @Override
    public void advance(int count) {
      pos += count;
    }

    @Override
    public void retract() {
      --pos;
    }

    @Override
    public void seek(int off) {
      pos += off;
    }
  }
}
