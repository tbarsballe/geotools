package org.geotools.renderer.style;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * Utility class for generating SVG icons from SLD marks
 *
 * To convert from SVG icons to MapBox sprites, use spritezero: https://github.com/mapbox/spritezero-cli
 */
public class MBStyleSpriteGenerator {

    public static void main(String[] args) throws IOException {

        generateSVG("cross-24.svg", WellKnownMarkFactory.cross);
        generateSVG("star-24.svg", WellKnownMarkFactory.star);
        generateSVG("triangle-24.svg", WellKnownMarkFactory.triangle);
        generateSVG("arrow-24.svg", WellKnownMarkFactory.arrow);
        generateSVG("X-24.svg", WellKnownMarkFactory.X);
        generateSVG("hatch-24.svg", WellKnownMarkFactory.hatch);
        generateSVG("square-24.svg", WellKnownMarkFactory.square);
        generateSVG("circle-24.svg", WellKnownMarkFactory.circle);
    }

    public static void generateSVG(String filename, Shape shape) throws IOException {
        // Get a DOMImplementation.
        DOMImplementation domImpl = GenericDOMImplementation.getDOMImplementation();

        // Create an instance of org.w3c.dom.Document.
        String svgNS = "http://www.w3.org/2000/svg";
        Document document = domImpl.createDocument(svgNS, "svg", null);

        // Create an instance of the SVG Generator.
        SVGGraphics2D svgGenerator = new SVGGraphics2D(document);


        AffineTransform at = new AffineTransform();

        at.scale(24, 24);
        at.translate(0.5, 0.5);

        svgGenerator.setSVGCanvasSize(new Dimension(24, 24));
        svgGenerator.fill(at.createTransformedShape(shape));


        //Write to file
        Writer out = new FileWriter(filename);
        svgGenerator.stream(out, true);
        out.flush();
        out.close();
    }
}
