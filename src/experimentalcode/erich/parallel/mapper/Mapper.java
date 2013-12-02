package experimentalcode.erich.parallel.mapper;

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

import de.lmu.ifi.dbs.elki.database.ids.DBIDRef;
import experimentalcode.erich.parallel.MapExecutor;

/**
 * Class to represent a mapper factory.
 * 
 * @author Erich Schubert
 */
public interface Mapper {
  /**
   * Create an instance. May be called multiple times, for example for multiple
   * threads.
   * 
   * @param executor Map executor
   * @return Instance
   */
  public Instance instantiate(MapExecutor exectutor);

  /**
   * Invoke cleanup.
   * 
   * @param inst Instance to cleanup.
   */
  public void cleanup(Instance inst);

  /**
   * Mapper instance.
   * 
   * @author Erich Schubert
   */
  public interface Instance {
    /**
     * Map a single object
     * 
     * @param id Object to map.
     * 
     * @return Mapping result
     */
    public void map(DBIDRef id);
  }
}