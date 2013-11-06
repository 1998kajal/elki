package de.lmu.ifi.dbs.elki.database.ids;

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

/**
 * Interface for array based DBIDs.
 * 
 * @author Erich Schubert
 * 
 * @apiviz.has DBIDArrayIter
 */
public interface ArrayDBIDs extends DBIDs {
  /**
   * Get the i'th entry (starting at 0)
   * 
   * If possible, use an {@link DBIDArrayIter} via {@link #iter()} instead!
   * 
   * @param i Index
   * @return DBID of i'th entry.
   */
  public DBID get(int i);

  /**
   * Assign a DBID variable the value of position {@code index}.
   * 
   * @param index Position
   * @param var Variable to assign the value to.
   */
  public void assignVar(int index, DBIDVar var);

  /**
   * Iterable
   * 
   * @return Iterator
   */
  @Override
  public DBIDArrayIter iter();

  /**
   * Size of the DBID "collection".
   * 
   * @return size
   */
  @Override
  public int size();

  /**
   * Search for the position of the given key, assuming that the data set is
   * sorted.
   * 
   * For keys not found, <code>-(1+insertion position)</code> is returned, as
   * for Java {@link java.util.Collections#binarySearch}
   * 
   * @param key Key to search for
   * @return Offset of key
   */
  public int binarySearch(DBIDRef key);

  /**
   * Slice a subarray (as view, not copy!)
   * 
   * @param begin Begin (inclusive)
   * @param end End (exclusive)
   * @return Array slice.
   */
  public ArrayDBIDs slice(int begin, int end);
}
