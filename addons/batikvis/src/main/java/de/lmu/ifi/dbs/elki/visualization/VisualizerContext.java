package de.lmu.ifi.dbs.elki.visualization;

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

import java.util.Collection;
import java.util.List;

import javax.swing.event.EventListenerList;

import de.lmu.ifi.dbs.elki.algorithm.clustering.trivial.ByLabelHierarchicalClustering;
import de.lmu.ifi.dbs.elki.algorithm.clustering.trivial.TrivialAllInOne;
import de.lmu.ifi.dbs.elki.data.Clustering;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.model.Model;
import de.lmu.ifi.dbs.elki.data.type.NoSupportedDataTypeException;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreEvent;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreListener;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.result.DBIDSelection;
import de.lmu.ifi.dbs.elki.result.HierarchicalResult;
import de.lmu.ifi.dbs.elki.result.Result;
import de.lmu.ifi.dbs.elki.result.ResultHierarchy;
import de.lmu.ifi.dbs.elki.result.ResultListener;
import de.lmu.ifi.dbs.elki.result.ResultUtil;
import de.lmu.ifi.dbs.elki.result.SelectionResult;
import de.lmu.ifi.dbs.elki.visualization.projector.ProjectorFactory;
import de.lmu.ifi.dbs.elki.visualization.style.ClusterStylingPolicy;
import de.lmu.ifi.dbs.elki.visualization.style.StyleLibrary;
import de.lmu.ifi.dbs.elki.visualization.style.StyleResult;
import de.lmu.ifi.dbs.elki.visualization.visualizers.VisFactory;

/**
 * Map to store context information for the visualizer. This can be any data
 * that should to be shared among plots, such as line colors, styles etc.
 *
 * @author Erich Schubert
 *
 *         TODO: remove this class
 *
 * @apiviz.landmark
 * @apiviz.composedOf StyleLibrary
 * @apiviz.composedOf SelectionResult
 * @apiviz.composedOf ResultHierarchy
 * @apiviz.composedOf EventListenerList
 * @apiviz.composedOf StyleResult
 * @apiviz.composedOf ProjectorFactory
 * @apiviz.composedOf VisFactory
 */
public class VisualizerContext implements DataStoreListener, Result {
  /**
   * Logger.
   */
  private static final Logging LOG = Logging.getLogger(VisualizerContext.class);

  /**
   * The full result object
   */
  private HierarchicalResult result;

  /**
   * The event listeners for this context.
   */
  private EventListenerList listenerList = new EventListenerList();

  /**
   * Projectors to use
   */
  private Collection<ProjectorFactory> projectors;

  /**
   * Factories to use
   */
  private Collection<VisFactory> factories;

  /**
   * Selection result
   */
  private SelectionResult selection;

  /**
   * Styling result
   */
  private StyleResult styleresult;

  /**
   * Constructor. We currently require a Database and a Result.
   *
   * @param result Result
   * @param projectors Projectors to use
   * @param factories Visualizer Factories to use
   */
  public VisualizerContext(HierarchicalResult result, StyleLibrary stylelib, Collection<ProjectorFactory> projectors, Collection<VisFactory> factories) {
    super();
    this.result = result;
    this.projectors = projectors;
    this.factories = factories;

    // Ensure that various common results needed by visualizers are
    // automatically created
    final Database db = ResultUtil.findDatabase(result);
    if(db == null) {
      LOG.warning("No database reachable from " + result);
      return;
    }
    ResultUtil.ensureClusteringResult(db, result);
    this.selection = ResultUtil.ensureSelectionResult(db);
    for(Relation<?> rel : ResultUtil.getRelations(result)) {
      ResultUtil.getSamplingResult(rel);
      // FIXME: this is a really ugly workaround. :-(
      if(TypeUtil.NUMBER_VECTOR_FIELD.isAssignableFromType(rel.getDataTypeInformation())) {
        @SuppressWarnings("unchecked")
        Relation<? extends NumberVector> vrel = (Relation<? extends NumberVector>) rel;
        ResultUtil.getScalesResult(vrel);
      }
    }
    makeStyleResult(stylelib);

    // result.getHierarchy().add(result, this);

    // Add visualizers.
    processNewResult(result, result);

    // For proxying events.
    db.addDataStoreListener(this);
    // Add a result listener. Don't expose these methods to avoid inappropriate
    // use.
    addResultListener(new ResultListener() {
      @Override
      public void resultAdded(Result child, Result parent) {
        processNewResult(getResult(), child);
      }

      @Override
      public void resultChanged(Result current) {
        // FIXME: need to do anything?
      }

      @Override
      public void resultRemoved(Result child, Result parent) {
        // FIXME: implement
      }
    });
  }

  /**
   * Generate a new style result for the given style library.
   *
   * @param stylelib Style library
   */
  protected void makeStyleResult(StyleLibrary stylelib) {
    styleresult = new StyleResult();
    styleresult.setStyleLibrary(stylelib);
    List<Clustering<? extends Model>> clusterings = ResultUtil.getClusteringResults(result);
    if(clusterings.size() > 0) {
      styleresult.setStylingPolicy(new ClusterStylingPolicy(clusterings.get(0), stylelib));
    }
    else {
      Clustering<Model> c = generateDefaultClustering();
      styleresult.setStylingPolicy(new ClusterStylingPolicy(c, stylelib));
    }
    result.getHierarchy().add(result, styleresult);
  }

  /**
   * Get the full result object
   *
   * @return result object
   */
  public HierarchicalResult getResult() {
    return result;
  }

  /**
   * Get the hierarchy object
   *
   * @return hierarchy object
   */
  public ResultHierarchy getHierarchy() {
    return result.getHierarchy();
  }

  /**
   * Get the style result.
   *
   * @return Style result
   */
  public StyleResult getStyleResult() {
    return styleresult;
  }

  /**
   * Generate a default (fallback) clustering.
   *
   * @return generated clustering
   */
  private Clustering<Model> generateDefaultClustering() {
    final Database db = ResultUtil.findDatabase(getResult());
    Clustering<Model> c = null;
    try {
      // Try to cluster by labels
      ByLabelHierarchicalClustering split = new ByLabelHierarchicalClustering();
      c = split.run(db);
    }
    catch(NoSupportedDataTypeException e) {
      // Put everything into one
      c = new TrivialAllInOne().run(db);
    }
    return c;
  }

  // TODO: add ShowVisualizer,HideVisualizer with tool semantics.

  /**
   * Get the current selection.
   *
   * @return selection
   */
  public DBIDSelection getSelection() {
    return selection.getSelection();
  }

  /**
   * Set a new selection.
   *
   * @param sel Selection
   */
  public void setSelection(DBIDSelection sel) {
    selection.setSelection(sel);
    getHierarchy().resultChanged(selection);
  }

  /**
   * Adds a listener for the <code>DataStoreEvent</code> posted after the
   * content changes.
   *
   * @param l the listener to add
   * @see #removeDataStoreListener
   */
  public void addDataStoreListener(DataStoreListener l) {
    listenerList.add(DataStoreListener.class, l);
  }

  /**
   * Removes a listener previously added with <code>addDataStoreListener</code>.
   *
   * @param l the listener to remove
   * @see #addDataStoreListener
   */
  public void removeDataStoreListener(DataStoreListener l) {
    listenerList.remove(DataStoreListener.class, l);
  }

  /**
   * Proxy datastore event to child listeners.
   */
  @Override
  public void contentChanged(DataStoreEvent e) {
    for(DataStoreListener listener : listenerList.getListeners(DataStoreListener.class)) {
      listener.contentChanged(e);
    }
  }

  /**
   * Process a particular result.
   *
   * @param baseResult Base Result
   * @param newResult Newly added Result
   */
  private void processNewResult(HierarchicalResult baseResult, Result newResult) {
    for(ProjectorFactory p : projectors) {
      try {
        p.processNewResult(baseResult, newResult);
      }
      catch(Throwable e) {
        LOG.warning("ProjectorFactory " + p.getClass().getCanonicalName() + " failed:", e);
      }
    }
    // Collect all visualizers.
    for(VisFactory f : factories) {
      try {
        f.processNewResult(baseResult, newResult);
      }
      catch(Throwable e) {
        LOG.warning("VisFactory " + f.getClass().getCanonicalName() + " failed:", e);
      }
    }
  }

  /**
   * Register a result listener.
   *
   * @param listener Result listener.
   */
  public void addResultListener(ResultListener listener) {
    getHierarchy().addResultListener(listener);
  }

  /**
   * Remove a result listener.
   *
   * @param listener Result listener.
   */
  public void removeResultListener(ResultListener listener) {
    getHierarchy().removeResultListener(listener);
  }

  @Override
  public String getLongName() {
    return "Visualizer context";
  }

  @Override
  public String getShortName() {
    return "vis-context";
  }
}
