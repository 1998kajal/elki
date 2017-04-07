package experimentalcode.shared.index.xtree;
/*
This file is part of ELKI:
Environment for Developing KDD-Applications Supported by Index-Structures

Copyright (C) 2011
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

import de.lmu.ifi.dbs.elki.index.tree.spatial.SpatialEntry;

/**
 * Represents a node in an X-Tree.
 * 
 * @author Marisa Thoma
 */
public class XTreeNode extends XNode<SpatialEntry, XTreeNode> {
  /**
   * Empty constructor for Externalizable interface.
   */
  public XTreeNode() {
    // empty constructor
  }

  /**
   * Creates a new XTreeNode with the specified parameters.
   * 
   * @param capacity the capacity (maximum number of entries plus 1 for
   *        overflow) of this node
   * @param isLeaf indicates whether this node is a leaf node
   */
  public XTreeNode(int capacity, boolean isLeaf, Class<? extends SpatialEntry> eclass) {
    super(capacity, isLeaf, eclass);
  }
}