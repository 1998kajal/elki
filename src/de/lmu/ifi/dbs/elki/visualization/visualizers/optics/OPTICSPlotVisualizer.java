package de.lmu.ifi.dbs.elki.visualization.visualizers.optics;

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

import java.util.Collection;

import org.apache.batik.util.SVGConstants;
import org.w3c.dom.Element;

import de.lmu.ifi.dbs.elki.distance.distancevalue.Distance;
import de.lmu.ifi.dbs.elki.distance.distancevalue.DoubleDistance;
import de.lmu.ifi.dbs.elki.logging.LoggingUtil;
import de.lmu.ifi.dbs.elki.result.HierarchicalResult;
import de.lmu.ifi.dbs.elki.result.Result;
import de.lmu.ifi.dbs.elki.result.ResultUtil;
import de.lmu.ifi.dbs.elki.visualization.VisualizationTask;
import de.lmu.ifi.dbs.elki.visualization.css.CSSClassManager.CSSNamingConflict;
import de.lmu.ifi.dbs.elki.visualization.opticsplot.OPTICSPlot;
import de.lmu.ifi.dbs.elki.visualization.projector.OPTICSProjector;
import de.lmu.ifi.dbs.elki.visualization.style.StyleLibrary;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGSimpleLinearAxis;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGUtil;
import de.lmu.ifi.dbs.elki.visualization.visualizers.AbstractVisFactory;
import de.lmu.ifi.dbs.elki.visualization.visualizers.Visualization;

/**
 * Visualize an OPTICS result by constructing an OPTICS plot for it.
 * 
 * @author Erich Schubert
 * 
 * @apiviz.stereotype factory
 * @apiviz.uses Instance oneway - - «create»
 */
public class OPTICSPlotVisualizer extends AbstractVisFactory {
  /**
   * Name for this visualizer.
   */
  private static final String NAME = "OPTICS Plot";

  /**
   * Constructor, adhering to
   * {@link de.lmu.ifi.dbs.elki.utilities.optionhandling.Parameterizable}
   */
  public OPTICSPlotVisualizer() {
    super();
  }

  @Override
  public void processNewResult(HierarchicalResult baseResult, Result result) {
    Collection<OPTICSProjector<?>> ops = ResultUtil.filterResults(result, OPTICSProjector.class);
    for(OPTICSProjector<?> p : ops) {
      // Add plots, attach visualizer
      final VisualizationTask task = new VisualizationTask(NAME, p, null, this);
      task.level = VisualizationTask.LEVEL_DATA;
      baseResult.getHierarchy().add(p, task);
    }
  }

  @Override
  public Visualization makeVisualization(VisualizationTask task) {
    return new Instance<DoubleDistance>(task);
  }

  @Override
  public boolean allowThumbnails(VisualizationTask task) {
    // Don't use thumbnails
    return false;
  }

  /**
   * Instance.
   * 
   * @author Erich Schubert
   * 
   * @param <D> Distance type
   */
  public class Instance<D extends Distance<D>> extends AbstractOPTICSVisualization<D> {
    /**
     * Constructor.
     * 
     * @param task Visualization task
     */
    public Instance(VisualizationTask task) {
      super(task);
    }

    @Override
    protected void redraw() {
      makeLayerElement();
      // addCSSClasses();

      OPTICSPlot<D> opticsplot = optics.getOPTICSPlot(context);
      String ploturi = opticsplot.getSVGPlotURI();

      Element itag = svgp.svgElement(SVGConstants.SVG_IMAGE_TAG);
      SVGUtil.setAtt(itag, SVGConstants.SVG_IMAGE_RENDERING_ATTRIBUTE, SVGConstants.SVG_OPTIMIZE_SPEED_VALUE);
      SVGUtil.setAtt(itag, SVGConstants.SVG_X_ATTRIBUTE, 0);
      SVGUtil.setAtt(itag, SVGConstants.SVG_Y_ATTRIBUTE, 0);
      SVGUtil.setAtt(itag, SVGConstants.SVG_WIDTH_ATTRIBUTE, plotwidth);
      SVGUtil.setAtt(itag, SVGConstants.SVG_HEIGHT_ATTRIBUTE, plotheight);
      itag.setAttributeNS(SVGConstants.XLINK_NAMESPACE_URI, SVGConstants.XLINK_HREF_QNAME, ploturi);

      layer.appendChild(itag);

      try {
        final StyleLibrary style = context.getStyleResult().getStyleLibrary();
        SVGSimpleLinearAxis.drawAxis(svgp, layer, opticsplot.getScale(), 0, plotheight, 0, 0, SVGSimpleLinearAxis.LabelStyle.LEFTHAND, style);
        SVGSimpleLinearAxis.drawAxis(svgp, layer, opticsplot.getScale(), plotwidth, plotheight, plotwidth, 0, SVGSimpleLinearAxis.LabelStyle.RIGHTHAND, style);
      }
      catch(CSSNamingConflict e) {
        LoggingUtil.exception("CSS naming conflict for axes on OPTICS plot", e);
      }
    }
  }
}