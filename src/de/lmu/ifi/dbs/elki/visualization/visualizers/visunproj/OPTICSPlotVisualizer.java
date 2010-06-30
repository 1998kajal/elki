package de.lmu.ifi.dbs.elki.visualization.visualizers.visunproj;

import java.io.File;
import java.io.IOException;

import org.apache.batik.util.SVGConstants;
import org.w3c.dom.Element;

import de.lmu.ifi.dbs.elki.data.Clustering;
import de.lmu.ifi.dbs.elki.data.DatabaseObject;
import de.lmu.ifi.dbs.elki.distance.distancevalue.CorrelationDistance;
import de.lmu.ifi.dbs.elki.distance.distancevalue.Distance;
import de.lmu.ifi.dbs.elki.distance.distancevalue.NumberDistance;
import de.lmu.ifi.dbs.elki.result.ClusterOrderResult;
import de.lmu.ifi.dbs.elki.visualization.colors.ColorLibrary;
import de.lmu.ifi.dbs.elki.visualization.css.CSSClassManager.CSSNamingConflict;
import de.lmu.ifi.dbs.elki.visualization.opticsplot.OPTICSColorAdapter;
import de.lmu.ifi.dbs.elki.visualization.opticsplot.OPTICSColorFromClustering;
import de.lmu.ifi.dbs.elki.visualization.opticsplot.OPTICSCorrelationDimensionalityDistance;
import de.lmu.ifi.dbs.elki.visualization.opticsplot.OPTICSDistanceAdapter;
import de.lmu.ifi.dbs.elki.visualization.opticsplot.OPTICSNumberDistance;
import de.lmu.ifi.dbs.elki.visualization.opticsplot.OPTICSPlot;
import de.lmu.ifi.dbs.elki.visualization.style.StyleLibrary;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGPlot;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGSimpleLinearAxis;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGUtil;
import de.lmu.ifi.dbs.elki.visualization.visualizers.StaticVisualization;
import de.lmu.ifi.dbs.elki.visualization.visualizers.Visualization;
import de.lmu.ifi.dbs.elki.visualization.visualizers.Visualizer;
import de.lmu.ifi.dbs.elki.visualization.visualizers.VisualizerContext;

/**
 * Visualize an OPTICS result by constructing an OPTICS plot for it.
 * 
 * @author Erich Schubert
 * 
 * @param <D> Distance type
 */
public class OPTICSPlotVisualizer<D extends Distance<D>> extends AbstractUnprojectedVisualizer<DatabaseObject> {
  /**
   * Name for this visualizer.
   */
  private static final String NAME = "OPTICS Plot";

  /**
   * Curve to visualize
   */
  ClusterOrderResult<D> co = null;

  /**
   * The actual plot object.
   */
  private OPTICSPlot<D> opticsplot;

  /**
   * The image we generated.
   */
  private File imgfile;

  /**
   * Constructor, adhering to
   * {@link de.lmu.ifi.dbs.elki.utilities.optionhandling.Parameterizable}
   */
  public OPTICSPlotVisualizer() {
    super(NAME);
  }

  /**
   * Initialization.
   * 
   * @param context context.
   * @param co Cluster order to visualize
   */
  public void init(VisualizerContext<? extends DatabaseObject> context, ClusterOrderResult<D> co) {
    super.init(context);
    this.co = co;
  }

  /**
   * Make the optics plot
   * 
   * @throws IOException
   */
  protected void makePlot() throws IOException {
    final ColorLibrary colors = context.getStyleLibrary().getColorSet(StyleLibrary.PLOT);
    final Clustering<?> refc = context.getOrCreateDefaultClustering();

    final OPTICSColorAdapter opcolor = new OPTICSColorFromClustering(colors, refc);
    final OPTICSDistanceAdapter<D> opdist = getAdapterForDistance(co);

    opticsplot = new OPTICSPlot<D>(co, opcolor, opdist);
    imgfile = opticsplot.getAsTempFile();
    opticsplot.forgetRenderedImage();
  }

  /**
   * Try to find a distance adapter.
   * 
   * @return distance adapter
   */
  @SuppressWarnings("unchecked")
  private static <D extends Distance<D>> OPTICSDistanceAdapter<D> getAdapterForDistance(ClusterOrderResult<D> co) {
    Class<?> dcls = co.getDistanceClass();
    if(dcls != null && NumberDistance.class.isAssignableFrom(dcls)) {
      return new OPTICSNumberDistance();
    }
    else if(dcls != null && CorrelationDistance.class.isAssignableFrom(dcls)) {
      return new OPTICSCorrelationDimensionalityDistance();
    }
    else if(dcls == null) {
      throw new UnsupportedOperationException("No distance in cluster order?!?");
    }
    else {
      throw new UnsupportedOperationException("No distance adapter found for distance class: " + dcls);
    }
  }

  /**
   * Test whether we have an adapter for this cluster orders distance.
   * 
   * @param <D> distance type
   * @param co Cluster order
   * @return true when we do find a matching adapter.
   */
  public static <D extends Distance<D>> boolean canPlot(ClusterOrderResult<D> co) {
    try {
      OPTICSDistanceAdapter<D> adapt = getAdapterForDistance(co);
      return (adapt != null);
    }
    catch(UnsupportedOperationException e) {
      return false;
    }
  }

  @Override
  public Visualization visualize(SVGPlot svgp, double width, double height) {
    // TODO: Use width, height, imgratio, number of OPTICS plots!
    double scale = StyleLibrary.SCALE;
    final double sizex = scale;
    final double sizey = scale * height / width;
    final double margin = context.getStyleLibrary().getSize(StyleLibrary.MARGIN);
    Element layer = SVGUtil.svgElement(svgp.getDocument(), SVGConstants.SVG_G_TAG);
    final String transform = SVGUtil.makeMarginTransform(width, height, sizex, sizey, margin);
    SVGUtil.setAtt(layer, SVGConstants.SVG_TRANSFORM_ATTRIBUTE, transform);

    if(imgfile == null) {
      try {
        makePlot();
      }
      catch(IOException e) {
        logger.exception("Could not generate OPTICS plot.", e);
      }
    }

    Element itag = svgp.svgElement(SVGConstants.SVG_IMAGE_TAG);
    SVGUtil.setAtt(itag, SVGConstants.SVG_IMAGE_RENDERING_ATTRIBUTE, SVGConstants.SVG_OPTIMIZE_SPEED_VALUE);
    SVGUtil.setAtt(itag, SVGConstants.SVG_X_ATTRIBUTE, 0);
    SVGUtil.setAtt(itag, SVGConstants.SVG_Y_ATTRIBUTE, 0);
    SVGUtil.setAtt(itag, SVGConstants.SVG_WIDTH_ATTRIBUTE, scale);
    SVGUtil.setAtt(itag, SVGConstants.SVG_HEIGHT_ATTRIBUTE, scale / opticsplot.getRatio());
    itag.setAttributeNS(SVGConstants.XLINK_NAMESPACE_URI, SVGConstants.XLINK_HREF_QNAME, imgfile.toURI().toString());

    layer.appendChild(itag);

    try {
      SVGSimpleLinearAxis.drawAxis(svgp, layer, opticsplot.getScale(), 0, scale / opticsplot.getRatio(), 0, 0, true, false, context.getStyleLibrary());
      SVGSimpleLinearAxis.drawAxis(svgp, layer, opticsplot.getScale(), scale, scale / opticsplot.getRatio(), scale, 0, true, true, context.getStyleLibrary());
    }
    catch(CSSNamingConflict e) {
      logger.exception("CSS naming conflict for axes on OPTICS plot", e);
    }
    Integer level = this.getMetadata().getGenerics(Visualizer.META_LEVEL, Integer.class);
    return new StaticVisualization(context, svgp, level, layer, width, height);
  }
}