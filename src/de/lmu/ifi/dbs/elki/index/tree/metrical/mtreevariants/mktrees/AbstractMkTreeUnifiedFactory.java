package de.lmu.ifi.dbs.elki.index.tree.metrical.mtreevariants.mktrees;

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

import de.lmu.ifi.dbs.elki.distance.distancevalue.Distance;
import de.lmu.ifi.dbs.elki.index.Index;
import de.lmu.ifi.dbs.elki.index.tree.metrical.mtreevariants.AbstractMTreeFactory;
import de.lmu.ifi.dbs.elki.index.tree.metrical.mtreevariants.AbstractMTreeNode;
import de.lmu.ifi.dbs.elki.index.tree.metrical.mtreevariants.MTreeEntry;
import de.lmu.ifi.dbs.elki.persistent.PageFileFactory;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.GreaterConstraint;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.IntParameter;

/**
 * Abstract factory for various Mk-Trees
 * 
 * @author Erich Schubert
 * 
 * @apiviz.stereotype factory
 * @apiviz.uses AbstractMkTreeUnified oneway - - «create»
 * 
 * @param <O> Object type
 * @param <D> Distance type
 * @param <N> Node type
 * @param <E> Entry type
 * @param <I> Index type
 */
public abstract class AbstractMkTreeUnifiedFactory<O, D extends Distance<D>, N extends AbstractMTreeNode<O, D, N, E>, E extends MTreeEntry<D>, I extends AbstractMkTree<O, D, N, E, S> & Index, S extends MkTreeSettings<O, D, N, E>> extends AbstractMTreeFactory<O, D, N, E, I, S> {
  /**
   * Constructor.
   * 
   * @param pageFileFactory Data storage
   * @param settings Tree settings
   */
  public AbstractMkTreeUnifiedFactory(PageFileFactory<?> pageFileFactory, S settings) {
    super(pageFileFactory, settings);
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public abstract static class Parameterizer<O, D extends Distance<D>, N extends AbstractMTreeNode<O, D, N, E>, E extends MTreeEntry<D>, S extends MkTreeSettings<O, D, N, E>> extends AbstractMTreeFactory.Parameterizer<O, D, N, E, S> {
    /**
     * Parameter specifying the maximal number k of reverse k nearest neighbors
     * to be supported, must be an integer greater than 0.
     * <p>
     * Key: {@code -mktree.kmax}
     * </p>
     */
    public static final OptionID K_MAX_ID = new OptionID("mktree.kmax", "Specifies the maximal number k of reverse k nearest neighbors to be supported.");

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      IntParameter k_maxP = new IntParameter(K_MAX_ID);
      k_maxP.addConstraint(new GreaterConstraint(0));

      if (config.grab(k_maxP)) {
        settings.k_max = k_maxP.getValue();
      }
    }

    @Override
    protected abstract AbstractMkTreeUnifiedFactory<O, D, N, E, ?, S> makeInstance();
  }
}
