package experimentalcode.heidi.optics;

import java.util.ArrayList;
import java.util.List;

import org.apache.batik.util.SVGConstants;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventTarget;
import org.w3c.dom.svg.SVGPoint;

import de.lmu.ifi.dbs.elki.data.Clustering;
import de.lmu.ifi.dbs.elki.data.DatabaseObject;
import de.lmu.ifi.dbs.elki.data.cluster.Cluster;
import de.lmu.ifi.dbs.elki.data.model.ClusterModel;
import de.lmu.ifi.dbs.elki.data.model.Model;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.ModifiableDBIDs;
import de.lmu.ifi.dbs.elki.distance.distancevalue.Distance;
import de.lmu.ifi.dbs.elki.distance.distancevalue.DoubleDistance;
import de.lmu.ifi.dbs.elki.result.ClusterOrderEntry;
import de.lmu.ifi.dbs.elki.result.ClusterOrderResult;
import de.lmu.ifi.dbs.elki.visualization.opticsplot.OPTICSPlot;
import de.lmu.ifi.dbs.elki.visualization.scales.LinearScale;
import de.lmu.ifi.dbs.elki.visualization.style.StyleLibrary;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGPlot;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGUtil;
import de.lmu.ifi.dbs.elki.visualization.visualizers.AbstractVisualizer;
import de.lmu.ifi.dbs.elki.visualization.visualizers.VisualizerContext;

/**
 * Visualize a line in a OPTICS Plot to select an Epsilon value and generates a
 * new result with the new epsilon
 * 
 * @author
 * 
 * @param <D> distance type
 */
public class OPTICSPlotLineVis<D extends Distance<D>> extends AbstractVisualizer<DatabaseObject> {
  /**
   * Name for this visualizer.
   */
  private static final String NAME = "Heidi OPTICSPlotLineVis";

  /**
   * Curves to visualize
   */
  ArrayList<ClusterOrderResult<D>> cos;

  /**
   * OpticsPlotVis
   */
  private OPTICSPlotVisualizer<D> opvis;

  /**
   * flag if mouseDown (to move line)
   */
  protected boolean mouseDown;

  /**
   * The SVGPlot
   */
  private SVGPlot svgp;

  /**
   * List of the ClusterOrderEntries
   */
  List<ClusterOrderEntry<D>> order;

  /**
   * Index of the actual plot
   */
  private int plotInd;

  /**
   * The actual plot
   */
  private OPTICSPlot<D> opticsplot;

  /**
   * SVG-Element for the line
   */
  private Element ltag;

  /**
   * The y-Value of the actual Plot
   */
  private double yValueLayer;

  /**
   * The Scale of the Opticsplot
   */
  private LinearScale linscale;

  /**
   * The height of the plot
   */
  private Double heightPlot;

  /**
   * Space around plots
   */
  private double space;

  /**
   * Constructor, adhering to
   * {@link de.lmu.ifi.dbs.elki.utilities.optionhandling.Parameterizable}
   */
  public OPTICSPlotLineVis() {
    super(NAME);
  }

  /**
   * @param opvis
   * @param svgp
   * @param context
   * @param order
   * @param plotInd
   */
  public void init(OPTICSPlotVisualizer<D> opvis, SVGPlot svgp, VisualizerContext<? extends DatabaseObject> context, List<ClusterOrderEntry<D>> order, int plotInd) {
    super.init(context);
    this.opvis = opvis;
    this.order = order;
    this.svgp = svgp;
    ltag = svgp.svgElement(SVGConstants.SVG_G_TAG);
    this.plotInd = plotInd;
    opticsplot = opvis.opvisualizer.getOpticsplots().get(plotInd);
    final double imgratio = 1. / opticsplot.getRatio();
    linscale = opticsplot.getScale();
    yValueLayer = opvis.getYValueOfPlot(plotInd);
    space = StyleLibrary.SCALE * OPTICSPlotVisualizer.SPACEFACTOR;
    heightPlot = StyleLibrary.SCALE * imgratio;
  }

  /**
   * Creates an SVG-Element for the Line to select Epsilon-value
   * 
   * @param epsilon
   * @param plInd Index of the actual Plot
   * @return SVG-Element
   */
  protected Element visualize(Double epsilon, double plInd) {
    // absolute y-value
    Element ltagText;
    double yAct;
    if(plotInd == plInd && epsilon != 0.) {
      yAct = yValueLayer + space / 2 + heightPlot - getYFromEpsilon(epsilon);
      ltagText = svgp.svgText(StyleLibrary.SCALE * 1.10, yAct, SVGUtil.fmt(epsilon));
    }
    else {
      yAct = yValueLayer + space / 2 + heightPlot;
      ltagText = svgp.svgText(StyleLibrary.SCALE * 1.10, yAct, " ");
    }
    final Element ltagLine = svgp.svgRect(0, yAct, StyleLibrary.SCALE * 1.08, StyleLibrary.SCALE * 0.0004);
    SVGUtil.addCSSClass(ltagLine, OPTICSPlotVisualizerFactory.CSS_LINE);
    final Element ltagPoint = svgp.svgCircle(StyleLibrary.SCALE * 1.08, yAct, StyleLibrary.SCALE * 0.004);
    SVGUtil.addCSSClass(ltagPoint, OPTICSPlotVisualizerFactory.CSS_LINE);

    SVGUtil.setAtt(ltagText, SVGConstants.SVG_CLASS_ATTRIBUTE, OPTICSPlotVisualizerFactory.CSS_EPSILON);
    final Element ltagEventRect = svgp.svgRect(StyleLibrary.SCALE * 1.03, yValueLayer, StyleLibrary.SCALE * 0.2, heightPlot + space);
    SVGUtil.addCSSClass(ltagEventRect, OPTICSPlotVisualizerFactory.CSS_EVENTRECT);

    if(ltag.hasChildNodes()) {
      NodeList nodes = ltag.getChildNodes();
      ltag.replaceChild(ltagLine, nodes.item(0));
      ltag.replaceChild(ltagPoint, nodes.item(1));
      ltag.replaceChild(ltagText, nodes.item(2));
    }
    else {
      ltag.appendChild(ltagLine);
      ltag.appendChild(ltagPoint);
      ltag.appendChild(ltagText);
      ltag.appendChild(ltagEventRect);
    }
    addEventTagLine(opvis, svgp, ltagEventRect);
    return ltag;
  }

  /**
   * @param y
   * @return
   */
  protected double getEpsilonFromY(double y) {
    if(y < 0) {
      y = 0;
    }
    if(y > heightPlot) {
      y = heightPlot;
    }
    double epsilon = linscale.getUnscaled(y / heightPlot);
    return epsilon;
  }

  /**
   * @param epsilon
   * @return
   */
  protected double getYFromEpsilon(double epsilon) {
    double y = linscale.getScaled(epsilon) * heightPlot;
    if(y < 0) {
      y = 0;
    }
    if(y > heightPlot) {
      y = heightPlot;
    }
    return y;
  }

  /**
   * @param opvisualizer
   * @param svgp
   * @param ltagEventRect
   */
  private void addEventTagLine(OPTICSPlotVisualizer<D> opvisualizer, SVGPlot svgp, Element ltagEventRect) {
    EventTarget targ = (EventTarget) ltagEventRect;
    OPTICSPlotLineHandler<D> oplhandler = new OPTICSPlotLineHandler<D>(this, svgp, ltag, plotInd);
    targ.addEventListener(SVGConstants.SVG_EVENT_MOUSEDOWN, oplhandler, false);
    targ.addEventListener(SVGConstants.SVG_EVENT_MOUSEMOVE, oplhandler, false);
    targ.addEventListener(SVGConstants.SVG_EVENT_MOUSEUP, oplhandler, false);
  }

  /**
   * @param evt
   * @param cPt
   * @param plotInd
   */
  protected void handleLineMousedown(Event evt, SVGPoint cPt, int plotInd) {
    double epsilon = getEpsilonFromY(yValueLayer + heightPlot + space / 2 - cPt.getY());
    opvis.updateLines(epsilon, plotInd);
    mouseDown = true;
  }

  /**
   * @param evt
   * @param cPt
   * @param plotInd
   */
  protected void handleLineMousemove(Event evt, SVGPoint cPt, int plotInd) {
    if(mouseDown) {
      double epsilon = getEpsilonFromY(yValueLayer + heightPlot + space / 2 - cPt.getY());
      opvis.updateLines(epsilon, plotInd);
    }
  }

  /**
   * @param evt
   * @param cPt
   * @param plotInd
   */
  protected void handleLineMouseup(Event evt, SVGPoint cPt, int plotInd) {
    if(mouseDown) {
      double epsilon = getEpsilonFromY(yValueLayer + heightPlot + space / 2 - cPt.getY());

      opvis.opvisualizer.setEpsilon(epsilon);
      opvis.opvisualizer.setEpsilonPlotInd(plotInd);
      opvis.updateLines(epsilon, plotInd);
      mouseDown = false;

      // Holds a list of clusters found.
      List<ModifiableDBIDs> resultList = new ArrayList<ModifiableDBIDs>();

      // Holds a set of noise.
      ModifiableDBIDs noise = DBIDUtil.newHashSet();

      DoubleDistance lastDist = new DoubleDistance();
      DoubleDistance actDist = new DoubleDistance();
      actDist.infiniteDistance();
      ModifiableDBIDs res = DBIDUtil.newHashSet();

      for(int j = 0; j < order.size(); j++) {
        lastDist = actDist;
        actDist = (DoubleDistance) order.get(j).getReachability();

        if(actDist.getValue() > epsilon) {
          if(!res.isEmpty()) {
            resultList.add(res);
            res = DBIDUtil.newHashSet();
          }
          noise.add(order.get(j).getID());
        }
        else {
          if(lastDist.getValue() > epsilon) {
            res.add(order.get(j - 1).getID());
            noise.remove(order.get(j - 1).getID());
          }
          res.add(order.get(j).getID());
        }
      }
      if(!res.isEmpty()) {
        resultList.add(res);
      }
      // logger.warning("resultList: " + resultList.toString());
      // logger.warning("noise: " + noise.toString());
      Clustering<Model> cl = newResult(resultList, noise);
      // TODO: funktioniert das noch?
      opvis.opvisualizer.onClusteringUpdated(cl);
    }
  }

  /**
   * @param resultList
   * @param noise
   * @return
   */
  private Clustering<Model> newResult(List<ModifiableDBIDs> resultList, ModifiableDBIDs noise) {
    Clustering<Model> result = new Clustering<Model>();
    for(ModifiableDBIDs res : resultList) {
      Cluster<Model> c = new Cluster<Model>(res, ClusterModel.CLUSTER);
      result.addCluster(c);
    }
    Cluster<Model> n = new Cluster<Model>(noise, true, ClusterModel.CLUSTER);
    result.addCluster(n);
    return result;
  }
}
