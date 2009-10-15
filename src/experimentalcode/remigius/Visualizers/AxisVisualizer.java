package experimentalcode.remigius.Visualizers;

import org.w3c.dom.Element;

import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.logging.LoggingUtil;
import de.lmu.ifi.dbs.elki.visualization.css.CSSClassManager.CSSNamingConflict;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGPlot;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGSimpleLinearAxis;
import experimentalcode.remigius.ShapeLibrary;

/**
 * Generates a SVG-Element containing axes, including labeling.
 * 
 * TODO: This visualizer could be more useful if it only would draw 1 axis with
 * its position depending on a parameter.
 * 
 * @author Remigius Wojdanowski
 * 
 * @param <NV>
 */
public class AxisVisualizer<NV extends NumberVector<NV, ?>> extends PlanarVisualizer<NV> {
  /**
   * A short name characterizing this Visualizer.
   */
  private static final String NAME = "Axes";

  /**
   * Initializes this Visualizer.
   * 
   * @param database contains all objects to be processed.
   */
  public void init(Database<NV> database) {
    // We don't need the Database. Maybe another superclass / inheritance
    // hierarchy would serve us better.
    super.init(database, 0, NAME);
  }

  @Override
  public Element visualize(SVGPlot svgp) {
    Element layer = super.visualize(svgp);
    try {
      SVGSimpleLinearAxis.drawAxis(svgp, layer, proj.getScale(dimx), -1, 1, 1, 1, true, true);
      SVGSimpleLinearAxis.drawAxis(svgp, layer, proj.getScale(dimy), -1, 1, -1, -1, true, false);
      svgp.updateStyleElement();
    }
    catch(CSSNamingConflict e) {
      LoggingUtil.exception(e);
    }
    return layer;
  }
}
