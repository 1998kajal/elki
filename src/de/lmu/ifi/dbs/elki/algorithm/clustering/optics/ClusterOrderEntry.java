package de.lmu.ifi.dbs.elki.algorithm.clustering.optics;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2014
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

/**
 * Generic Cluster Order Entry Interface.
 * 
 * @author Erich Schubert
 * 
 * @apiviz.landmark
 * 
 * @apiviz.composedOf DBID
 * 
 * @param <SELF> Type self-reference.
 */
public interface ClusterOrderEntry<SELF> extends Comparable<SELF> {
  /**
   * Returns the object id of this entry.
   * 
   * @return the object id of this entry
   */
  public DBID getID();

  /**
   * Returns the id of the predecessor of this entry if this entry has a
   * predecessor, null otherwise.
   * 
   * @return the id of the predecessor of this entry
   */
  public DBID getPredecessorID();
}