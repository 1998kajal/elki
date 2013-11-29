package de.lmu.ifi.dbs.elki.database.ids.integer;

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
import java.util.Arrays;

import de.lmu.ifi.dbs.elki.database.ids.DBIDRef;
import de.lmu.ifi.dbs.elki.database.ids.distance.DoubleDistanceDBIDListIter;
import de.lmu.ifi.dbs.elki.database.ids.distance.DoubleDistanceDBIDPair;
import de.lmu.ifi.dbs.elki.database.ids.distance.ModifiableDoubleDistanceDBIDList;
import de.lmu.ifi.dbs.elki.distance.distancevalue.DoubleDistance;

/**
 * Class to store double distance, integer DBID results.
 * 
 * @author Erich Schubert
 * 
 * @apiviz.uses DoubleIntegerArrayQuickSort
 */
public class DoubleDistanceIntegerDBIDList implements ModifiableDoubleDistanceDBIDList, IntegerDBIDs {
  /**
   * Initial size allocation.
   */
  private static final int INITIAL_SIZE = 21;

  /**
   * The size
   */
  int size;

  /**
   * Distance values
   */
  double[] dists;

  /**
   * DBIDs
   */
  int[] ids;

  /**
   * Constructor.
   */
  public DoubleDistanceIntegerDBIDList() {
    this(INITIAL_SIZE);
  }

  /**
   * Constructor.
   * 
   * @param size Initial size
   */
  public DoubleDistanceIntegerDBIDList(int size) {
    super();
    if (size <= 1) {
      size = INITIAL_SIZE;
    }
    this.dists = new double[size];
    this.ids = new int[size];
    this.size = 0; // not filled yet.
  }

  @Override
  public DoubleDistanceIntegerDBIDListIter iter() {
    return new Itr();
  }

  @Override
  public boolean contains(DBIDRef o) {
    final int q = o.internalGetIndex();
    for (int i = 0; i < size; i++) {
      if (q == ids[i]) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isEmpty() {
    return size == 0;
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public DoubleDistanceIntegerDBIDPair get(int index) {
    return new DoubleDistanceIntegerDBIDPair(dists[index], ids[index]);
  }

  /**
   * Add an entry, consisting of distance and internal index.
   * 
   * @param dist Distance
   * @param id Internal index
   */
  protected void addInternal(double dist, int id) {
    final int len = dists.length;
    if (size == len) {
      final int newlength = len + (len >>> 1);
      dists = Arrays.copyOf(dists, newlength);
      ids = Arrays.copyOf(ids, newlength);
    }
    dists[size] = dist;
    ids[size] = id;
    ++size;
  }

  @Override
  @Deprecated
  public void add(DoubleDistance dist, DBIDRef id) {
    addInternal(dist.doubleValue(), id.internalGetIndex());
  }

  @Override
  public void add(double dist, DBIDRef id) {
    addInternal(dist, id.internalGetIndex());
  }

  @Override
  public void add(DoubleDistanceDBIDPair pair) {
    addInternal(pair.doubleDistance(), pair.internalGetIndex());
  }

  @Override
  public void clear() {
    size = 0;
    Arrays.fill(dists, Double.NaN);
    Arrays.fill(ids, -1);
  }

  @Override
  public void sort() {
    DoubleIntegerArrayQuickSort.sort(dists, ids, 0, size);
  }

  /**
   * Reverse the list.
   */
  protected void reverse() {
    for (int i = 0, j = size - 1; i < j; i++, j--) {
      double tmpd = dists[j];
      dists[j] = dists[i];
      dists[i] = tmpd;
      int tmpi = ids[j];
      ids[j] = ids[i];
      ids[i] = tmpi;
    }
  }

  /**
   * Truncate the list to the given size.
   * 
   * @param newsize New size
   */
  public void truncate(int newsize) {
    if (newsize < size) {
      ids = Arrays.copyOf(ids, newsize);
      dists = Arrays.copyOf(dists, newsize);
      size = newsize;
    }
  }

  /**
   * Get the distance of the object at position pos.
   * 
   * Usually, you should be using an iterator instead. This part of the API is
   * not stable.
   * 
   * @param pos Position
   * @return Double distance.
   */
  public double getDoubleDistance(int pos) {
    return dists[pos];
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append("DistanceDBIDList[");
    for (DoubleDistanceDBIDListIter iter = this.iter(); iter.valid();) {
      buf.append(iter.doubleDistance()).append(':').append(iter.internalGetIndex());
      iter.advance();
      if (iter.valid()) {
        buf.append(',');
      }
    }
    buf.append(']');
    return buf.toString();
  }

  /**
   * List iterator.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  private class Itr implements DoubleDistanceIntegerDBIDListIter {
    int offset = 0;

    @Override
    public boolean valid() {
      return offset < size;
    }

    @Override
    public void advance() {
      ++offset;
    }

    @Override
    public int getOffset() {
      return offset;
    }

    @Override
    public void advance(int count) {
      offset += count;
    }

    @Override
    public void retract() {
      --offset;
    }

    @Override
    public void seek(int off) {
      offset = off;
    }

    @Override
    public int internalGetIndex() {
      return ids[offset];
    }

    @Override
    public double doubleDistance() {
      return dists[offset];
    }

    @Override
    public DoubleDistanceDBIDPair getDistancePair() {
      return new DoubleDistanceIntegerDBIDPair(dists[offset], ids[offset]);
    }

    @Override
    @Deprecated
    public DoubleDistance getDistance() {
      return new DoubleDistance(dists[offset]);
    }
  }
}
