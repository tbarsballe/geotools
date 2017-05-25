package org.geotools.renderer.style;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map.Entry;

/**
 * Utility class for generating SVG icons from SLD marks
 *
 * To convert from SVG icons to MapBox sprites, use spritezero: https://github.com/mapbox/spritezero-cli
 * 
 * Note: Shapes from other MarkFactories (aside from {@link WellKnownMarkFactory} often have a prefix
 * like "shape://". Those prefixes are not included in the filenames written to disk, because they may be invalid.
 * When using the files to generate the sprite index file, they should be renamed to have the correct prefix.
 * 
 */
public class MBStyleSpriteGenerator {

    private static final int SCALE = 32;

    private static Path OUTPUT_FOLDER = Paths.get("src/test/resources/mark-sprite-output");
    
    public static void main(String[] args) throws IOException, URISyntaxException {
       
        // Generate WellKnownMarkFactory marks
        generateSVG("cross.svg", WellKnownMarkFactory.cross);
        generateSVG("star.svg", WellKnownMarkFactory.star, Math.PI);
        generateSVG("triangle.svg", WellKnownMarkFactory.triangle, Math.PI);
        generateSVG("arrow.svg", WellKnownMarkFactory.arrow);
        generateSVG("X.svg", WellKnownMarkFactory.X);
        generateSVG("hatch.svg", WellKnownMarkFactory.hatch, Math.PI);
        generateSVG("square.svg", WellKnownMarkFactory.square);
        generateSVG("circle.svg", WellKnownMarkFactory.circle);

        // Generate ShapeMarkFactory marks
        for (Entry<String, Shape> entry : ShapeMarkFactory.shapes.entrySet()) {
            // Rotate certain shapes so the output matches default GeoTools output.
            if (entry.getKey().trim().equals("slash") || entry.getKey().trim().equals("backslash")) {
                generateSVG("ShapeMarkFactory-" + entry.getKey() + ".svg", entry.getValue(),
                        Math.PI / 2.0);
            } else {

                generateSVG("ShapeMarkFactory-" + entry.getKey() + ".svg", entry.getValue());
            }
        }
    }

    public static void generateSVG(String filename, Shape shape) throws IOException, URISyntaxException {
        generateSVG(filename, shape, null);
    }

    public static void generateSVG(String filename, Shape shape, Double rotation)
            throws IOException, URISyntaxException {
        // Get a DOMImplementation.
        DOMImplementation domImpl = GenericDOMImplementation.getDOMImplementation();

        // Create an instance of org.w3c.dom.Document.
        String svgNS = "http://www.w3.org/2000/svg";
        Document document = domImpl.createDocument(svgNS, "svg", null);

        // Create an instance of the SVG Generator.
        SVGGraphics2D svgGenerator = new SVGGraphics2D(document);
        svgGenerator.setPaint(Color.GRAY);

        AffineTransform at = new AffineTransform();
        at.scale(SCALE, SCALE);
        at.translate(0.5, 0.5);

        if (rotation != null) {
            at.rotate(rotation);
        }

        svgGenerator.setSVGCanvasSize(new Dimension(SCALE, SCALE));

        svgGenerator.fill(at.createTransformedShape(shape));
        svgGenerator.setStroke(new BasicStroke(1));
        svgGenerator.setColor(Color.BLACK);
        svgGenerator.draw(at.createTransformedShape(shape));

        // Write to file
        Writer out = new FileWriter(Paths.get(OUTPUT_FOLDER.toString(), filename).toString());
        svgGenerator.stream(out, true);
        out.flush();
        out.close();
    }

}
