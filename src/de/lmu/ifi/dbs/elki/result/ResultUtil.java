package de.lmu.ifi.dbs.elki.result;

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import de.lmu.ifi.dbs.elki.algorithm.clustering.ClusteringAlgorithm;
import de.lmu.ifi.dbs.elki.algorithm.clustering.trivial.ByLabelOrAllInOneClustering;
import de.lmu.ifi.dbs.elki.data.Clustering;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.model.Model;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierResult;
import de.lmu.ifi.dbs.elki.utilities.ClassGenericsUtil;

/**
 * Utilities for handling result objects
 * 
 * @author Erich Schubert
 * 
 * @apiviz.uses Result oneway - - filters
 */
public class ResultUtil {
  /**
   * Collect all Annotation results from a Result
   * 
   * @param r Result
   * @return List of all annotation results
   */
  public static List<Relation<?>> getRelations(Result r) {
    if(r instanceof Relation<?>) {
      List<Relation<?>> anns = new ArrayList<>(1);
      anns.add((Relation<?>) r);
      return anns;
    }
    if(r instanceof HierarchicalResult) {
      return ClassGenericsUtil.castWithGenericsOrNull(List.class, filterResults((HierarchicalResult) r, Relation.class));
    }
    return Collections.emptyList();
  }

  /**
   * Collect all ordering results from a Result
   * 
   * @param r Result
   * @return List of ordering results
   */
  public static List<OrderingResult> getOrderingResults(Result r) {
    if(r instanceof OrderingResult) {
      List<OrderingResult> ors = new ArrayList<>(1);
      ors.add((OrderingResult) r);
      return ors;
    }
    if(r instanceof HierarchicalResult) {
      return filterResults((HierarchicalResult) r, OrderingResult.class);
    }
    return Collections.emptyList();
  }

  /**
   * Collect all clustering results from a Result
   * 
   * @param r Result
   * @return List of clustering results
   */
  public static List<Clustering<? extends Model>> getClusteringResults(Result r) {
    if(r instanceof Clustering<?>) {
      List<Clustering<?>> crs = new ArrayList<>(1);
      crs.add((Clustering<?>) r);
      return crs;
    }
    if(r instanceof HierarchicalResult) {
      return ClassGenericsUtil.castWithGenericsOrNull(List.class, filterResults((HierarchicalResult) r, Clustering.class));
    }
    return Collections.emptyList();
  }

  /**
   * Collect all collection results from a Result
   * 
   * @param r Result
   * @return List of collection results
   */
  public static List<CollectionResult<?>> getCollectionResults(Result r) {
    if(r instanceof CollectionResult<?>) {
      List<CollectionResult<?>> crs = new ArrayList<>(1);
      crs.add((CollectionResult<?>) r);
      return crs;
    }
    if(r instanceof HierarchicalResult) {
      return ClassGenericsUtil.castWithGenericsOrNull(List.class, filterResults((HierarchicalResult) r, CollectionResult.class));
    }
    return Collections.emptyList();
  }

  /**
   * Return all Iterable results
   * 
   * @param r Result
   * @return List of iterable results
   */
  public static List<IterableResult<?>> getIterableResults(Result r) {
    if(r instanceof IterableResult<?>) {
      List<IterableResult<?>> irs = new ArrayList<>(1);
      irs.add((IterableResult<?>) r);
      return irs;
    }
    if(r instanceof HierarchicalResult) {
      return ClassGenericsUtil.castWithGenericsOrNull(List.class, filterResults((HierarchicalResult) r, IterableResult.class));
    }
    return Collections.emptyList();
  }

  /**
   * Collect all outlier results from a Result
   * 
   * @param r Result
   * @return List of outlier results
   */
  public static List<OutlierResult> getOutlierResults(Result r) {
    if(r instanceof OutlierResult) {
      List<OutlierResult> ors = new ArrayList<>(1);
      ors.add((OutlierResult) r);
      return ors;
    }
    if(r instanceof HierarchicalResult) {
      return filterResults((HierarchicalResult) r, OutlierResult.class);
    }
    return Collections.emptyList();
  }

  /**
   * Collect all settings results from a Result
   * 
   * @param r Result
   * @return List of settings results
   */
  public static List<SettingsResult> getSettingsResults(Result r) {
    if(r instanceof SettingsResult) {
      List<SettingsResult> ors = new ArrayList<>(1);
      ors.add((SettingsResult) r);
      return ors;
    }
    if(r instanceof HierarchicalResult) {
      return filterResults((HierarchicalResult) r, SettingsResult.class);
    }
    return Collections.emptyList();
  }

  /**
   * Return only results of the given restriction class
   * 
   * @param <C> Class type
   * @param restrictionClass Class restriction
   * @return filtered results list
   */
  // We can't ensure that restrictionClass matches C.
  @SuppressWarnings("unchecked")
  public static <C> ArrayList<C> filterResults(Result r, Class<?> restrictionClass) {
    ArrayList<C> res = new ArrayList<>();
    if(restrictionClass.isInstance(r)) {
      res.add((C) restrictionClass.cast(r));
    }
    if(r instanceof HierarchicalResult) {
      for(Iterator<Result> iter = ((HierarchicalResult) r).getHierarchy().iterDescendants(r); iter.hasNext();) {
        Result result = iter.next();
        if(restrictionClass.isInstance(result)) {
          res.add((C) restrictionClass.cast(result));
        }
      }
    }
    return res;
  }

  /**
   * Ensure that the result contains at least one Clustering.
   * 
   * @param <O> Database type
   * @param db Database to process
   * @param result result
   */
  public static <O> void ensureClusteringResult(final Database db, final Result result) {
    Collection<Clustering<?>> clusterings = ResultUtil.filterResults(result, Clustering.class);
    if(clusterings.size() == 0) {
      ClusteringAlgorithm<Clustering<Model>> split = new ByLabelOrAllInOneClustering();
      Clustering<Model> c = split.run(db);
      addChildResult(db, c);
    }
  }

  /**
   * Ensure that there also is a selection container object.
   * 
   * @param db Database
   * @return selection result
   */
  public static SelectionResult ensureSelectionResult(final Database db) {
    List<SelectionResult> selections = ResultUtil.filterResults(db, SelectionResult.class);
    if(!selections.isEmpty()) {
      return selections.get(0);
    }
    SelectionResult sel = new SelectionResult();
    addChildResult(db, sel);
    return sel;
  }

  /**
   * Get the sampling result attached to a relation
   * 
   * @param rel Relation
   * @return Sampling result.
   */
  public static SamplingResult getSamplingResult(final Relation<?> rel) {
    Collection<SamplingResult> selections = ResultUtil.filterResults(rel, SamplingResult.class);
    if(selections.size() == 0) {
      final SamplingResult newsam = new SamplingResult(rel);
      addChildResult(rel, newsam);
      return newsam;
    }
    return selections.iterator().next();
  }

  /**
   * Get (or create) a scales result for a relation.
   * 
   * @param rel Relation
   * @return associated scales result
   */
  public static ScalesResult getScalesResult(final Relation<? extends NumberVector<?>> rel) {
    Collection<ScalesResult> scas = ResultUtil.filterResults(rel, ScalesResult.class);
    if(scas.size() == 0) {
      final ScalesResult newsca = new ScalesResult(rel);
      addChildResult(rel, newsca);
      return newsca;
    }
    return scas.iterator().next();
  }

  /**
   * Add a child result.
   * 
   * @param parent Parent
   * @param child Child
   */
  public static void addChildResult(HierarchicalResult parent, Result child) {
    parent.getHierarchy().add(parent, child);
  }

  /**
   * Find the first database result in the tree.
   * 
   * @param baseResult Result tree base.
   * @return Database
   */
  public static Database findDatabase(Result baseResult) {
    final List<Database> dbs = filterResults(baseResult, Database.class);
    if(!dbs.isEmpty()) {
      return dbs.get(0);
    }
    else {
      return null;
    }
  }

  /**
   * Recursively remove a result and its children.
   * 
   * @param hierarchy Result hierarchy
   * @param child Result to remove
   */
  public static void removeRecursive(ResultHierarchy hierarchy, Result child) {
    for(Result parent : hierarchy.getParents(child)) {
      hierarchy.remove(parent, child);
    }
    for(Result sub : hierarchy.getChildren(child)) {
      removeRecursive(hierarchy, sub);
    }
  }
}