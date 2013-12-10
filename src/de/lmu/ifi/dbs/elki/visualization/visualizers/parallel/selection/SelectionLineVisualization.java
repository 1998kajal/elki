package de.lmu.ifi.dbs.elki.visualization.visualizers.parallel.selection;

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

import java.util.Collection;

import org.apache.batik.util.SVGConstants;
import org.w3c.dom.Element;

import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreListener;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDRef;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.result.DBIDSelection;
import de.lmu.ifi.dbs.elki.result.HierarchicalResult;
import de.lmu.ifi.dbs.elki.result.Result;
import de.lmu.ifi.dbs.elki.result.ResultUtil;
import de.lmu.ifi.dbs.elki.result.SelectionResult;
import de.lmu.ifi.dbs.elki.visualization.VisualizationTask;
import de.lmu.ifi.dbs.elki.visualization.css.CSSClass;
import de.lmu.ifi.dbs.elki.visualization.projector.ParallelPlotProjector;
import de.lmu.ifi.dbs.elki.visualization.style.StyleLibrary;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGPath;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGPlot;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGUtil;
import de.lmu.ifi.dbs.elki.visualization.visualizers.AbstractVisFactory;
import de.lmu.ifi.dbs.elki.visualization.visualizers.Visualization;
import de.lmu.ifi.dbs.elki.visualization.visualizers.parallel.AbstractParallelVisualization;
import de.lmu.ifi.dbs.elki.visualization.visualizers.thumbs.ThumbnailVisualization;

/**
 * Visualizer for generating SVG-Elements representing the selected objects
 * 
 * @author Robert Rödler
 * 
 * @apiviz.stereotype factory
 * @apiviz.uses Instance oneway - - «create»
 */
public class SelectionLineVisualization extends AbstractVisFactory {
  /**
   * A short name characterizing this Visualizer.
   */
  public static final String NAME = "Selection Line";

  /**
   * Constructor.
   */
  public SelectionLineVisualization() {
    super();
    thumbmask |= ThumbnailVisualization.ON_DATA | ThumbnailVisualization.ON_SELECTION;
  }

  @Override
  public Visualization makeVisualization(VisualizationTask task) {
    return new Instance(task);
  }

  @Override
  public void processNewResult(HierarchicalResult baseResult, Result result) {
    Collection<SelectionResult> selectionResults = ResultUtil.filterResults(result, SelectionResult.class);
    for(SelectionResult selres : selectionResults) {
      Collection<ParallelPlotProjector<?>> ps = ResultUtil.filterResults(baseResult, ParallelPlotProjector.class);
      for(ParallelPlotProjector<?> p : ps) {
        final VisualizationTask task = new VisualizationTask(NAME, selres, p.getRelation(), this);
        task.level = VisualizationTask.LEVEL_DATA - 1;
        baseResult.getHierarchy().add(selres, task);
        baseResult.getHierarchy().add(p, task);
      }
    }
  }

  /**
   * Instance
   * 
   * @author Robert Rödler
   * 
   * @apiviz.has SelectionResult oneway - - visualizes
   */
  public class Instance extends AbstractParallelVisualization<NumberVector<?>> implements DataStoreListener {
    /**
     * CSS Class for the range marker
     */
    public static final String MARKER = "SelectionLine";

    /**
     * Constructor.
     * 
     * @param task Visualization task
     */
    public Instance(VisualizationTask task) {
      super(task);
      addCSSClasses(svgp);
      context.addDataStoreListener(this);
      context.addResultListener(this);
      incrementalRedraw();
    }

    @Override
    public void destroy() {
      context.removeDataStoreListener(this);
      context.removeResultListener(this);
      super.destroy();
    }

    @Override
    protected void redraw() {
      DBIDSelection selContext = context.getSelection();
      if(selContext != null) {
        DBIDs selection = selContext.getSelectedIds();

        for(DBIDIter iter = selection.iter(); iter.valid(); iter.advance()) {
          Element marker = drawLine(iter);
          if(marker == null) {
            continue;
          }
          SVGUtil.addCSSClass(marker, MARKER);
          layer.appendChild(marker);
        }
      }
    }

    /**
     * Draw a single line.
     * 
     * @param iter Object reference
     * @return
     */
    private Element drawLine(DBIDRef iter) {
      SVGPath path = new SVGPath();
      double[] yPos = proj.fastProjectDataToRenderSpace(relation.get(iter));
      boolean draw = false, drawprev = false, drawn = false;
      for(int i = 0; i < yPos.length; i++) {
        // NaN handling:
        if(yPos[i] != yPos[i]) {
          draw = false;
          drawprev = false;
          continue;
        }
        if(draw) {
          if(drawprev) {
            path.moveTo(getVisibleAxisX(i - 1), yPos[i - 1]);
            drawprev = false;
          }
          path.lineTo(getVisibleAxisX(i), yPos[i]);
          drawn = true;
        }
        else {
          drawprev = true;
        }
        draw = true;
      }
      if(!drawn) {
        return null; // Not enough data.
      }
      return path.makeElement(svgp);
    }

    /**
     * Adds the required CSS-Classes
     * 
     * @param svgp SVG-Plot
     */
    private void addCSSClasses(SVGPlot svgp) {
      final StyleLibrary style = context.getStyleResult().getStyleLibrary();
      // Class for the cube
      if(!svgp.getCSSClassManager().contains(MARKER)) {
        CSSClass cls = new CSSClass(this, MARKER);
        cls.setStatement(SVGConstants.CSS_STROKE_VALUE, style.getColor(StyleLibrary.SELECTION));
        cls.setStatement(SVGConstants.CSS_STROKE_OPACITY_PROPERTY, style.getOpacity(StyleLibrary.SELECTION));
        cls.setStatement(SVGConstants.CSS_STROKE_WIDTH_PROPERTY, style.getLineWidth(StyleLibrary.PLOT) * 2.);
        cls.setStatement(SVGConstants.CSS_STROKE_LINECAP_PROPERTY, SVGConstants.CSS_ROUND_VALUE);
        cls.setStatement(SVGConstants.CSS_STROKE_LINEJOIN_PROPERTY, SVGConstants.CSS_ROUND_VALUE);
        cls.setStatement(SVGConstants.CSS_FILL_PROPERTY, SVGConstants.CSS_NONE_VALUE);
        svgp.addCSSClassOrLogError(cls);
      }
    }
  }
}