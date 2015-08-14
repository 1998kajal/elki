package de.lmu.ifi.dbs.elki.visualization.visualizers;

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

import org.w3c.dom.Element;

import de.lmu.ifi.dbs.elki.database.datastore.DataStoreEvent;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreListener;
import de.lmu.ifi.dbs.elki.result.Result;
import de.lmu.ifi.dbs.elki.result.ResultListener;
import de.lmu.ifi.dbs.elki.visualization.VisualizationTask;
import de.lmu.ifi.dbs.elki.visualization.VisualizerContext;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGPlot;

/**
 * Abstract base class for visualizations.
 *
 * @author Erich Schubert
 * @apiviz.excludeSubtypes
 */
public abstract class AbstractVisualization implements Visualization, ResultListener {
  /**
   * The visualization task we do.
   */
  protected final VisualizationTask task;

  /**
   * Our context
   */
  protected final VisualizerContext context;

  /**
   * The plot we are attached to
   */
  protected final SVGPlot svgp;

  /**
   * Pending redraw
   */
  protected Runnable pendingRedraw = null;

  /**
   * Layer storage
   */
  protected Element layer;

  /**
   * Width
   */
  private double width;

  /**
   * Height
   */
  private double height;

  /**
   * Constructor.
   *
   * @param task Visualization task
   */
  public AbstractVisualization(VisualizationTask task, SVGPlot plot, double width, double height) {
    super();
    this.task = task;
    this.context = task.getContext();
    this.svgp = plot;
    this.width = width;
    this.height = height;
    this.layer = null;
    // Note: we do not auto-add listeners, as we don't know what kind of
    // listeners a visualizer needs, and the visualizer might need to do some initialization first
  }

  @Override
  public void destroy() {
    // Always unregister listeners, as this is easy to forget otherwise
    // TODO: remove destroy() overrides that are redundant?
    context.removeResultListener(this);
    if (this instanceof DataStoreListener) {
      context.removeDataStoreListener((DataStoreListener) this);
    }
  }

  @Override
  public Element getLayer() {
    if(layer == null) {
      incrementalRedraw();
    }
    return layer;
  }

  /**
   * Get the width
   *
   * @return the width
   */
  protected double getWidth() {
    return width;
  }

  /**
   * Get the height
   *
   * @return the height
   */
  protected double getHeight() {
    return height;
  }

  /**
   * Trigger a redraw, but avoid excessive redraws.
   */
  protected final void synchronizedRedraw() {
    Runnable pr = new Runnable() {
      @Override
      public void run() {
        if(AbstractVisualization.this.pendingRedraw == this) {
          AbstractVisualization.this.pendingRedraw = null;
          AbstractVisualization.this.incrementalRedraw();
        }
      }
    };
    pendingRedraw = pr;
    svgp.scheduleUpdate(pr);
  }

  /**
   * Redraw the visualization (maybe incremental).
   *
   * Optional - by default, it will do a full redraw, which often is faster!
   */
  protected void incrementalRedraw() {
    Element oldcontainer = null;
    if(layer != null && layer.hasChildNodes()) {
      oldcontainer = layer;
      layer = (Element) layer.cloneNode(false);
    }
    redraw();
    if(oldcontainer != null && oldcontainer.getParentNode() != null) {
      oldcontainer.getParentNode().replaceChild(layer, oldcontainer);
    }
  }

  /**
   * Perform a full redraw.
   */
  protected abstract void redraw();

  @Override
  public void resultAdded(Result child, Result parent) {
    // Ignore by default
  }

  @Override
  public void resultChanged(Result current) {
    // Default is to redraw when the result we are attached to changed.
    if(task.getResult() == current) {
      synchronizedRedraw();
    }
  }

  @Override
  public void resultRemoved(Result child, Result parent) {
    // Ignore by default.
  }

  /**
   * Default implementation for
   * {@link de.lmu.ifi.dbs.elki.database.datastore.DataStoreListener#contentChanged}
   *
   * Not enabled or used by default, but saves redundant code.
   *
   * @param e Data store event
   */
  public void contentChanged(DataStoreEvent e) {
    synchronizedRedraw();
  }
}