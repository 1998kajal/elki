package de.lmu.ifi.dbs.elki.visualization.gui.detail;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2015
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.batik.util.SVGConstants;
import org.w3c.dom.Element;

import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.LoggingUtil;
import de.lmu.ifi.dbs.elki.utilities.datastructures.hierarchy.Hierarchy;
import de.lmu.ifi.dbs.elki.visualization.VisualizationItem;
import de.lmu.ifi.dbs.elki.visualization.VisualizationListener;
import de.lmu.ifi.dbs.elki.visualization.VisualizationTask;
import de.lmu.ifi.dbs.elki.visualization.VisualizerContext;
import de.lmu.ifi.dbs.elki.visualization.batikutil.AttributeModifier;
import de.lmu.ifi.dbs.elki.visualization.css.CSSClass;
import de.lmu.ifi.dbs.elki.visualization.gui.overview.PlotItem;
import de.lmu.ifi.dbs.elki.visualization.style.StyleLibrary;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGEffects;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGPlot;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGUtil;
import de.lmu.ifi.dbs.elki.visualization.visualizers.Visualization;

/**
 * Manages a detail view.
 *
 * @author Erich Schubert
 *
 * @apiviz.has Visualization
 * @apiviz.has PlotItem
 * @apiviz.uses VisualizerContext
 * @apiviz.uses VisualizationTask
 */
public class DetailView extends SVGPlot implements VisualizationListener {
  /**
   * Meta information on the visualizers contained.
   */
  PlotItem visi;

  /**
   * Ratio of this view.
   */
  double ratio = 1.0;

  /**
   * The visualizer context
   */
  VisualizerContext context;

  /**
   * Map from visualizers to layers
   */
  Map<VisualizationTask, Visualization> layermap = new HashMap<>();

  /**
   * The created width
   */
  private double width;

  /**
   * The created height
   */
  private double height;

  /**
   * Constructor.
   *
   * @param vis Visualizations to use
   * @param ratio Plot ratio
   */
  public DetailView(VisualizerContext context, PlotItem vis, double ratio) {
    super();
    this.context = context;
    this.visi = vis;
    this.ratio = ratio;

    this.visi.sort();

    // TODO: only do this when there is an interactive visualizer?
    setDisableInteractions(true);
    addBackground(context);
    SVGEffects.addShadowFilter(this);
    SVGEffects.addLightGradient(this);

    redraw();
    context.addVisualizationListener(this);
  }

  /**
   * Create a background node. Note: don't call this at arbitrary times - the
   * background may cover already drawn parts of the image!
   *
   * @param context
   */
  private void addBackground(VisualizerContext context) {
    // Make a background
    CSSClass cls = new CSSClass(this, "background");
    cls.setStatement(SVGConstants.CSS_FILL_PROPERTY, context.getStyleLibrary().getBackgroundColor(StyleLibrary.PAGE));
    Element bg = this.svgElement(SVGConstants.SVG_RECT_TAG);
    SVGUtil.setAtt(bg, SVGConstants.SVG_X_ATTRIBUTE, "0");
    SVGUtil.setAtt(bg, SVGConstants.SVG_Y_ATTRIBUTE, "0");
    SVGUtil.setAtt(bg, SVGConstants.SVG_WIDTH_ATTRIBUTE, "100%");
    SVGUtil.setAtt(bg, SVGConstants.SVG_HEIGHT_ATTRIBUTE, "100%");
    addCSSClassOrLogError(cls);
    SVGUtil.setCSSClass(bg, cls.getName());

    // Note that we rely on this being called before any other drawing routines.
    getRoot().appendChild(bg);
  }

  protected void redraw() {
    destroyVisualizations();

    // Try to keep the area approximately 1.0
    width = Math.sqrt(getRatio());
    height = 1.0 / width;

    ArrayList<Visualization> layers = new ArrayList<>();
    // TODO: center/arrange visualizations?
    for(Iterator<VisualizationTask> tit = visi.tasks.iterator(); tit.hasNext();) {
      VisualizationTask task = tit.next();
      if(task.visible) {
        try {
          Visualization v = task.getFactory().makeVisualization(task, this, width, height, visi.proj);
          if(task.noexport) {
            v.getLayer().setAttribute(NO_EXPORT_ATTRIBUTE, NO_EXPORT_ATTRIBUTE);
          }
          layers.add(v);
          layermap.put(task, v);
        }
        catch(Exception e) {
          if(Logging.getLogger(task.getFactory().getClass()).isDebugging()) {
            LoggingUtil.exception("Visualization failed.", e);
          }
          else {
            LoggingUtil.warning("Visualizer " + task.getFactory().getClass().getName() + " failed - enable debugging to see details: " + e.toString());
          }
        }
      }
    }
    // Arrange
    for(Visualization layer : layers) {
      if(layer.getLayer() != null) {
        getRoot().appendChild(layer.getLayer());
      }
      else {
        LoggingUtil.warning("NULL layer seen.");
      }
    }

    double ratio = width / height;
    getRoot().setAttribute(SVGConstants.SVG_WIDTH_ATTRIBUTE, "20cm");
    getRoot().setAttribute(SVGConstants.SVG_HEIGHT_ATTRIBUTE, (20 / ratio) + "cm");
    getRoot().setAttribute(SVGConstants.SVG_VIEW_BOX_ATTRIBUTE, "0 0 " + width + " " + height);

    updateStyleElement();
  }

  /**
   * Cleanup function. To remove listeners.
   */
  public void destroy() {
    context.removeVisualizationListener(this);
    destroyVisualizations();
  }

  private void destroyVisualizations() {
    for(Entry<VisualizationTask, Visualization> v : layermap.entrySet()) {
      v.getValue().destroy();
    }
    layermap.clear();
  }

  @Override
  public void dispose() {
    destroy();
    super.dispose();
  }

  /**
   * Get the plot ratio.
   *
   * @return the current ratio
   */
  public double getRatio() {
    return ratio;
  }

  /**
   * Set the plot ratio
   *
   * @param ratio the new ratio to set
   */
  public void setRatio(double ratio) {
    // TODO: trigger refresh?
    this.ratio = ratio;
  }

  /**
   * @return the layermap
   */
  // TODO: Temporary
  public Map<VisualizationTask, Visualization> getLayermap() {
    return layermap;
  }

  /**
   * Class used to insert a new visualization layer
   *
   * @author Erich Schubert
   *
   * @apiviz.exclude
   */
  protected class InsertVisualization implements Runnable {
    /**
     * The visualization to insert.
     */
    Visualization vis;

    /**
     * Visualization.
     *
     * @param vis
     */
    public InsertVisualization(Visualization vis) {
      super();
      this.vis = vis;
    }

    @Override
    public void run() {
      DetailView.this.getRoot().appendChild(vis.getLayer());
      updateStyleElement();
    }
  }

  @Override
  public void visualizationChanged(VisualizationItem current) {
    // Make sure we are affected:
    if(!(current instanceof VisualizationTask)) {
      return;
    }
    if(visi.proj != null) {
      boolean include = false;
      Hierarchy.Iter<Object> it = context.getVisHierarchy().iterAncestors(current);
      for(; it.valid(); it.advance()) {
        if(visi.proj == it.get()) {
          include = true;
          break;
        }
      }
      if(!include) {
        return; // Attached to different projection.
      }
    }
    // Get the layer
    final VisualizationTask task = (VisualizationTask) current;
    Visualization vis = layermap.get(task);
    if(vis != null) {
      // Ensure visibility is as expected
      boolean isHidden = SVGConstants.CSS_HIDDEN_VALUE.equals(vis.getLayer().getAttribute(SVGConstants.CSS_VISIBILITY_PROPERTY));
      if(task.visible) {
        if(isHidden) {
          this.scheduleUpdate(new AttributeModifier(vis.getLayer(), SVGConstants.CSS_VISIBILITY_PROPERTY, SVGConstants.CSS_VISIBLE_VALUE));
        }
      }
      else {
        if(!isHidden) {
          this.scheduleUpdate(new AttributeModifier(vis.getLayer(), SVGConstants.CSS_VISIBILITY_PROPERTY, SVGConstants.CSS_HIDDEN_VALUE));
        }
      }
    }
    else {
      // Only materialize when becoming visible
      if(task.visible) {
        // LoggingUtil.warning("Need to recreate a missing layer for " + task);
        vis = task.getFactory().makeVisualization(task, this, width, height, visi.proj);
        if(task.noexport) {
          vis.getLayer().setAttribute(NO_EXPORT_ATTRIBUTE, NO_EXPORT_ATTRIBUTE);
        }
        layermap.put(task, vis);
        this.scheduleUpdate(new InsertVisualization(vis));
      }
    }
    // FIXME: ensure layers are ordered correctly!
  }
}
