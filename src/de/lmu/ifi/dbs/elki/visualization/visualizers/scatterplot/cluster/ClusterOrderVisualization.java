package de.lmu.ifi.dbs.elki.visualization.visualizers.scatterplot.cluster;

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

import java.util.Collection;

import org.w3c.dom.Element;

import de.lmu.ifi.dbs.elki.database.datastore.DataStoreListener;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.distance.distancevalue.DoubleDistance;
import de.lmu.ifi.dbs.elki.result.HierarchicalResult;
import de.lmu.ifi.dbs.elki.result.Result;
import de.lmu.ifi.dbs.elki.result.ResultUtil;
import de.lmu.ifi.dbs.elki.result.optics.ClusterOrderEntry;
import de.lmu.ifi.dbs.elki.result.optics.ClusterOrderResult;
import de.lmu.ifi.dbs.elki.visualization.VisualizationTask;
import de.lmu.ifi.dbs.elki.visualization.css.CSSClass;
import de.lmu.ifi.dbs.elki.visualization.projector.ScatterPlotProjector;
import de.lmu.ifi.dbs.elki.visualization.style.StyleLibrary;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGUtil;
import de.lmu.ifi.dbs.elki.visualization.visualizers.AbstractVisFactory;
import de.lmu.ifi.dbs.elki.visualization.visualizers.Visualization;
import de.lmu.ifi.dbs.elki.visualization.visualizers.scatterplot.AbstractScatterplotVisualization;

/**
 * Cluster order visualizer: connect objects via the spanning tree the cluster
 * order represents.
 * 
 * @author Erich Schubert
 * 
 * @apiviz.stereotype factory
 * @apiviz.uses Instance oneway - - «create»
 */
public class ClusterOrderVisualization extends AbstractVisFactory {
  /**
   * A short name characterizing this Visualizer.
   */
  private static final String NAME = "Predecessor Graph";

  /**
   * Constructor, adhering to
   * {@link de.lmu.ifi.dbs.elki.utilities.optionhandling.Parameterizable}
   */
  public ClusterOrderVisualization() {
    super();
  }

  @Override
  public Visualization makeVisualization(VisualizationTask task) {
    return new Instance(task);
  }

  @Override
  public void processNewResult(HierarchicalResult baseResult, Result result) {
    Collection<ClusterOrderResult<DoubleDistance>> cos = ResultUtil.filterResults(result, ClusterOrderResult.class);
    for(ClusterOrderResult<DoubleDistance> co : cos) {
      Collection<ScatterPlotProjector<?>> ps = ResultUtil.filterResults(baseResult, ScatterPlotProjector.class);
      for(ScatterPlotProjector<?> p : ps) {
        final VisualizationTask task = new VisualizationTask(NAME, co, p.getRelation(), this);
        task.initDefaultVisibility(false);
        task.level = VisualizationTask.LEVEL_DATA - 1;
        baseResult.getHierarchy().add(co, task);
        baseResult.getHierarchy().add(p, task);
      }
    }
  }

  /**
   * Instance
   * 
   * @author Erich Schubert
   * 
   * @apiviz.has ClusterOrderResult oneway - - visualizes
   */
  // TODO: listen for CLUSTER ORDER changes.
  public class Instance extends AbstractScatterplotVisualization implements DataStoreListener {
    /**
     * CSS class name
     */
    private static final String CSSNAME = "predecessor";

    /**
     * The result we visualize
     */
    protected ClusterOrderResult<?> result;

    public Instance(VisualizationTask task) {
      super(task);
      result = task.getResult();
      context.addDataStoreListener(this);
      incrementalRedraw();
    }

    @Override
    public void destroy() {
      super.destroy();
      context.removeDataStoreListener(this);
    }

    @Override
    public void redraw() {
      final StyleLibrary style = context.getStyleResult().getStyleLibrary();
      CSSClass cls = new CSSClass(this, CSSNAME);
      style.lines().formatCSSClass(cls, 0, style.getLineWidth(StyleLibrary.CLUSTERORDER));

      svgp.addCSSClassOrLogError(cls);

      for(ClusterOrderEntry<?> ce : result) {
        DBID thisId = ce.getID();
        DBID prevId = ce.getPredecessorID();
        if(thisId == null || prevId == null) {
          continue;
        }
        double[] thisVec = proj.fastProjectDataToRenderSpace(rel.get(thisId));
        double[] prevVec = proj.fastProjectDataToRenderSpace(rel.get(prevId));

        // FIXME: DO NOT COMMIT
        thisVec[0] = thisVec[0] * 0.95 + prevVec[0] * 0.05;
        thisVec[1] = thisVec[1] * 0.95 + prevVec[1] * 0.05;

        Element arrow = svgp.svgLine(prevVec[0], prevVec[1], thisVec[0], thisVec[1]);
        SVGUtil.setCSSClass(arrow, cls.getName());

        layer.appendChild(arrow);
      }
    }
  }
}