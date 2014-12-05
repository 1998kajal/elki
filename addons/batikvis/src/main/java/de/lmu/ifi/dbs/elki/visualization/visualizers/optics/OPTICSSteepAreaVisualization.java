package de.lmu.ifi.dbs.elki.visualization.visualizers.optics;

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

import java.awt.Color;
import java.util.Collection;
import java.util.List;

import org.apache.batik.util.SVGConstants;
import org.w3c.dom.Element;

import de.lmu.ifi.dbs.elki.algorithm.clustering.optics.ClusterOrderEntry;
import de.lmu.ifi.dbs.elki.algorithm.clustering.optics.ClusterOrderResult;
import de.lmu.ifi.dbs.elki.algorithm.clustering.optics.DoubleDistanceClusterOrderEntry;
import de.lmu.ifi.dbs.elki.algorithm.clustering.optics.OPTICSXi;
import de.lmu.ifi.dbs.elki.algorithm.clustering.optics.OPTICSXi.SteepAreaResult;
import de.lmu.ifi.dbs.elki.result.HierarchicalResult;
import de.lmu.ifi.dbs.elki.result.Result;
import de.lmu.ifi.dbs.elki.result.ResultUtil;
import de.lmu.ifi.dbs.elki.result.SelectionResult;
import de.lmu.ifi.dbs.elki.utilities.datastructures.hierarchy.Hierarchy;
import de.lmu.ifi.dbs.elki.visualization.VisualizationTask;
import de.lmu.ifi.dbs.elki.visualization.css.CSSClass;
import de.lmu.ifi.dbs.elki.visualization.opticsplot.OPTICSDistanceAdapter;
import de.lmu.ifi.dbs.elki.visualization.opticsplot.OPTICSPlot;
import de.lmu.ifi.dbs.elki.visualization.projector.OPTICSProjector;
import de.lmu.ifi.dbs.elki.visualization.style.StyleLibrary;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGUtil;
import de.lmu.ifi.dbs.elki.visualization.visualizers.AbstractVisFactory;
import de.lmu.ifi.dbs.elki.visualization.visualizers.Visualization;

/**
 * Visualize the steep areas found in an OPTICS plot
 * 
 * @author Erich Schubert
 * 
 * @apiviz.stereotype factory
 * @apiviz.uses Instance oneway - - «create»
 */
public class OPTICSSteepAreaVisualization extends AbstractVisFactory {
  /**
   * A short name characterizing this Visualizer.
   */
  private static final String NAME = "OPTICS Steep Areas";

  /**
   * Constructor.
   */
  public OPTICSSteepAreaVisualization() {
    super();
  }

  @Override
  public void processNewResult(HierarchicalResult baseResult, Result result) {
    Collection<OPTICSProjector<?>> ops = ResultUtil.filterResults(result, OPTICSProjector.class);
    for(OPTICSProjector<?> p : ops) {
      final SteepAreaResult steep = findSteepAreaResult(p.getResult());
      if(steep != null) {
        final VisualizationTask task = new VisualizationTask(NAME, p, null, this);
        task.level = VisualizationTask.LEVEL_DATA + 1;
        baseResult.getHierarchy().add(p, task);
        baseResult.getHierarchy().add(steep, task);
      }
    }
  }

  @Override
  public Visualization makeVisualization(VisualizationTask task) {
    return new Instance<DoubleDistanceClusterOrderEntry>(task);
  }

  @Override
  public boolean allowThumbnails(VisualizationTask task) {
    // Don't use thumbnails
    return false;
  }

  /**
   * Find the OPTICS clustering child of a cluster order.
   * 
   * @param co Cluster order
   * @return OPTICS clustering
   */
  protected static OPTICSXi.SteepAreaResult findSteepAreaResult(ClusterOrderResult<?> co) {
    for(Hierarchy.Iter<Result> r = co.getHierarchy().iterChildren(co); r.valid(); r.advance()) {
      if(OPTICSXi.SteepAreaResult.class.isInstance(r.get())) {
        return (OPTICSXi.SteepAreaResult) r.get();
      }
    }
    return null;
  }

  /**
   * Instance
   * 
   * @author Erich Schubert
   * 
   * @apiviz.uses 
   *              de.lmu.ifi.dbs.elki.algorithm.clustering.optics.OPTICSXi.SteepAreaResult
   */
  public class Instance<E extends ClusterOrderEntry<?>> extends AbstractOPTICSVisualization<E> {
    /**
     * CSS class for markers
     */
    protected static final String CSS_STEEP_UP = "opticsSteepUp";

    /**
     * CSS class for markers
     */
    protected static final String CSS_STEEP_DOWN = "opticsSteepDown";

    /**
     * Our clustering
     */
    OPTICSXi.SteepAreaResult areas;

    /**
     * Constructor.
     * 
     * @param task Visualization task
     */
    public Instance(VisualizationTask task) {
      super(task);
      this.areas = findSteepAreaResult(this.optics.getResult());
      context.addResultListener(this);
      incrementalRedraw();
    }

    @Override
    protected void redraw() {
      makeLayerElement();
      addCSSClasses();

      final OPTICSPlot<E> opticsplot = optics.getOPTICSPlot(context);
      final List<E> co = getClusterOrder();
      final OPTICSDistanceAdapter<E> adapter = opticsplot.getDistanceAdapter();
      final int oheight = opticsplot.getHeight();
      final double xscale = plotwidth / (double) co.size();
      final double yscale = plotheight / (double) oheight;

      for(OPTICSXi.SteepArea area : areas) {
        final int st = area.getStartIndex();
        final int en = area.getEndIndex();
        // Note: make sure we are using doubles!
        final double x1 = (st + .25);
        final double x2 = (en + .75);
        final double y1 = opticsplot.scaleToPixel(adapter.getDoubleForEntry(co.get(st)));
        final double y2 = opticsplot.scaleToPixel(adapter.getDoubleForEntry(co.get(en)));
        Element e = svgp.svgLine(x1 * xscale, y1 * yscale, x2 * xscale, y2 * yscale);
        if(area instanceof OPTICSXi.SteepDownArea) {
          SVGUtil.addCSSClass(e, CSS_STEEP_DOWN);
        }
        else {
          SVGUtil.addCSSClass(e, CSS_STEEP_UP);
        }
        layer.appendChild(e);
      }
    }

    /**
     * Adds the required CSS-Classes
     */
    private void addCSSClasses() {
      // Class for the markers
      final StyleLibrary style = context.getStyleResult().getStyleLibrary();
      if(!svgp.getCSSClassManager().contains(CSS_STEEP_DOWN)) {
        final CSSClass cls = new CSSClass(this, CSS_STEEP_DOWN);
        Color color = SVGUtil.stringToColor(style.getColor(StyleLibrary.PLOT));
        if(color == null) {
          color = Color.BLACK;
        }
        color = new Color((int) (color.getRed() * 0.6), (int) (color.getGreen() * 0.6 + 0.4 * 256.), (int) (color.getBlue() * 0.6));
        cls.setStatement(SVGConstants.CSS_STROKE_PROPERTY, SVGUtil.colorToString(color));
        cls.setStatement(SVGConstants.CSS_STROKE_WIDTH_PROPERTY, style.getLineWidth(StyleLibrary.PLOT) * .5);
        svgp.addCSSClassOrLogError(cls);
      }
      if(!svgp.getCSSClassManager().contains(CSS_STEEP_UP)) {
        final CSSClass cls = new CSSClass(this, CSS_STEEP_UP);
        Color color = SVGUtil.stringToColor(style.getColor(StyleLibrary.PLOT));
        if(color == null) {
          color = Color.BLACK;
        }
        color = new Color((int) (color.getRed() * 0.6 + 0.4 * 256.), (int) (color.getGreen() * 0.6), (int) (color.getBlue() * 0.6));
        cls.setStatement(SVGConstants.CSS_STROKE_PROPERTY, SVGUtil.colorToString(color));
        cls.setStatement(SVGConstants.CSS_STROKE_WIDTH_PROPERTY, style.getLineWidth(StyleLibrary.PLOT) * .5);
        svgp.addCSSClassOrLogError(cls);
      }
    }

    @Override
    public void resultChanged(Result current) {
      if(current instanceof SelectionResult) {
        synchronizedRedraw();
        return;
      }
      super.resultChanged(current);
    }
  }
}
