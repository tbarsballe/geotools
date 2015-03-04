package org.geotools.gml.producer;

import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import org.custommonkey.xmlunit.SimpleNamespaceContext;
import org.custommonkey.xmlunit.XMLUnit;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.util.Converters;
import org.junit.Before;
import org.junit.Test;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.vividsolutions.jts.io.WKTReader;

/**
 * 
 *
 * @source $URL$
 */
public class FeatureTransformerTest {
    
    @Before
    public void setup() {
        Map<String, String> namespaces = new HashMap<String, String>();
        namespaces.put("xlink", "http://www.w3.org/1999/xlink");
        namespaces.put("wfs", "http://www.opengis.net/wfs");
        namespaces.put("gml", "http://www.opengis.net/gml");
        namespaces.put("gt", "http://www.geotools.org");
        XMLUnit.setXpathNamespaceContext(new SimpleNamespaceContext(namespaces));
    }

    @Test
    public void testEncodeEmptyArray() throws Exception {
        FeatureTransformer tx = new FeatureTransformer();
        tx.setIndentation(2);
        tx.setCollectionBounding(true);
        tx.setFeatureBounding(true);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        tx.transform(new FeatureCollection[0], bos);
        String result = bos.toString();
        // System.out.println(result);
        
        
        Document dom = XMLUnit.buildControlDocument(result);
        assertXpathEvaluatesTo("1", "count(//wfs:FeatureCollection)", dom);
        assertXpathEvaluatesTo("unknown", "/wfs:FeatureCollection/gml:boundedBy/gml:null", dom);
        assertXpathEvaluatesTo("0", "count(//gml:featureMember)", dom);
    }

    @Test
    public void testRemoveInvalidXMLChars() throws Exception {
        SimpleFeatureType ft = DataUtilities.createType("invalidChars",
                "the_geom:Point,data:String");
        SimpleFeature feature = SimpleFeatureBuilder.build(ft,
                new Object[] { new WKTReader().read("POINT(0 0)"),
                        "One " + ((char) 0x7) + " test" }, "123");
        SimpleFeatureCollection fc = DataUtilities.collection(feature);

        FeatureTransformer tx = new FeatureTransformer();
        tx.setIndentation(2);
        tx.getFeatureTypeNamespaces().declareNamespace(ft, "gt", "http://www.geotools.org");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        tx.transform(fc, bos);
        String result = bos.toString();

        // System.out.println(result);

        Document dom = XMLUnit.buildControlDocument(result);
        assertXpathEvaluatesTo("1", "count(//wfs:FeatureCollection)", dom);
        assertXpathEvaluatesTo("One  test", "//gt:data", dom);
    }
    
    @Test
    public void testEncodeXML() throws Exception {
        SimpleFeatureType ft = DataUtilities.createType("xml",
                "the_geom:Point,data:Node");
        SimpleFeature feature = SimpleFeatureBuilder.build(ft,
                new Object[] { new WKTReader().read("POINT(0 0)"),
                        (DocumentFragment)Converters.convert("foo<bar>foo</bar>", DocumentFragment.class) }, "123");
        SimpleFeatureCollection fc = DataUtilities.collection(feature);

        FeatureTransformer tx = new FeatureTransformer();
        tx.setIndentation(2);
        tx.getFeatureTypeNamespaces().declareNamespace(ft, "gt", "http://www.geotools.org");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        tx.transform(fc, bos);
        String result = bos.toString();

        // System.out.println(result);

        Document dom = XMLUnit.buildControlDocument(result);
        assertTrue(verifyDom(dom));
        assertXpathEvaluatesTo("1", "count(//wfs:FeatureCollection)", dom);
    }
    
    boolean verifyDom(Node dom) {
        NodeList nodes = dom.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeName().equals("gt:data")) {
                NodeList childNodes = node.getChildNodes();
                assertEquals("#text", childNodes.item(0).getNodeName());
                assertEquals("foo", childNodes.item(0).getTextContent());
                assertEquals("bar", childNodes.item(1).getNodeName());
                assertEquals("foo", childNodes.item(1).getTextContent());
                
                return true;
            }
            if (node.hasChildNodes() && (verifyDom(node))) {
                return true;
            }
        }
        return false;
    }
}
