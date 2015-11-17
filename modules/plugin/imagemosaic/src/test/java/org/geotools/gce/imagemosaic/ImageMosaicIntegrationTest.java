package org.geotools.gce.imagemosaic;

import static org.junit.Assert.assertEquals;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.media.jai.PlanarImage;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureWriter;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFactorySpi;
import org.geotools.data.Transaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.factory.Hints;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.gce.geotiff.GeoTiffFormat;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.gce.geotiff.GeoTiffWriter;
import org.geotools.gce.imagemosaic.Utils.Prop;
import org.geotools.geometry.DirectPosition2D;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.test.TestData;
import org.junit.Before;
import org.junit.Test;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.filter.FilterFactory;
import org.opengis.geometry.DirectPosition;
import org.opengis.geometry.Envelope;
import org.opengis.parameter.GeneralParameterValue;
import org.opengis.parameter.ParameterValue;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;

public class ImageMosaicIntegrationTest {
    
    private final static PrecisionModel PRECISION_MODEL = new PrecisionModel(PrecisionModel.FLOATING);
    private final static GeometryFactory GEOM_FACTORY = new GeometryFactory(PRECISION_MODEL);
    
    private File testData;
    
    @Before
    public void setUp() throws FileNotFoundException, IOException {
        testData = TestData.file(this,"redblue");
    }
    
    //TODO: Move to test utils
    public static ImageMosaicReader makeImageMosaic(File mosaicDir, Map<File, ReferencedEnvelope> rasterData) throws IOException {
        
        //Delete mosaicDir contents to avoid erroneous files
        if (mosaicDir.exists()) {
            FileUtils.deleteDirectory(mosaicDir);
        }
        mosaicDir.mkdirs();
        
        BufferedImage bufferedImage;
        GeoTiffWriter gtWriter;
        GridCoverage2D coverage;
        GridCoverageFactory gcFactory = new GridCoverageFactory();
        
        for (Entry<File, ReferencedEnvelope> entry : rasterData.entrySet()) {
            File imageFile = entry.getKey();
            ReferencedEnvelope envelope = entry.getValue();
            
            String name = FilenameUtils.removeExtension(imageFile.getName());
            
            bufferedImage = ImageIO.read(imageFile);
            coverage = gcFactory.create(name, bufferedImage, envelope);
            gtWriter = new GeoTiffWriter(new File(mosaicDir, name+".tiff"));
            gtWriter.write(coverage, null);
            
            gtWriter.dispose();
        }
        
        //Initialize the imagemosaic
        ImageMosaicReader reader = (ImageMosaicReader) new ImageMosaicFormatFactory().createFormat()
                .getReader(mosaicDir);
        
        //reader.dispose();
        
        return reader;
    }
    
    public void basicRedBlueMosaic(File mosaicDir) throws IOException {
        Map<File, ReferencedEnvelope> rasters = new HashMap<File, ReferencedEnvelope>();
        CoordinateReferenceSystem crs = DefaultGeographicCRS.WGS84;
        
        rasters.put(new File(testData,"red.tiff"), new ReferencedEnvelope(-30, 10, -30, 10, crs));
        rasters.put(new File(testData,"blue.tiff"), new ReferencedEnvelope(30, -10, 30, -10, crs));
        
        ImageMosaicReader mosaic = makeImageMosaic(mosaicDir, rasters);
        mosaic.dispose();
    }
    
    @Test
    public void testSortBy() throws IOException {
        File mosaicDir = new File(testData, "sortby");
        basicRedBlueMosaic(mosaicDir);
        
        
      //Look at the shapefile index, and try rearranging features
        FileDataStoreFactorySpi shpFactory = new ShapefileDataStoreFactory();
        Map map = Collections.singletonMap( "url", new URL("file://"+mosaicDir.getAbsolutePath()+"/sortby.shp") );
        
        DataStore shapefile = shpFactory.createNewDataStore( map );
        
        String typeName = shapefile.getTypeNames()[0];
        
        SimpleFeatureStore store = (SimpleFeatureStore) shapefile.getFeatureSource(typeName);
        
        SimpleFeatureType featureType = store.getSchema();
        if (featureType.getDescriptor("sort") == null) {
            SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
            
            //Duplicate featureType, adding sort property
            builder.init(featureType);
            builder.add("sort", Integer.class);
            
            featureType = builder.buildFeatureType();
            SimpleFeature[] features = (SimpleFeature[]) store.getFeatures().toArray();
            ListFeatureCollection newFeatures = new ListFeatureCollection(featureType);
            SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(featureType);
            for (int i = 0; i < features.length; i++) {
                //TODO: Copy features + sort to new feature; add to collection
                for (Property property : features[i].getProperties()) {
                    featureBuilder.add(property.getValue());
                }
                featureBuilder.add(i);
                
                newFeatures.add(featureBuilder.buildFeature(features[i].getID()));
                
            }
            
            shapefile.removeSchema(typeName);
            shapefile.createSchema(featureType);
            
            store = (SimpleFeatureStore) shapefile.getFeatureSource(typeName);
            store.addFeatures(newFeatures);
        }
        
        GeneralParameterValue[] params = new GeneralParameterValue[1];
        ParameterValue<String> sortBy = ImageMosaicFormat.SORT_BY
                .createValue();
        sortBy.setValue("sort A");
        params[0] = sortBy;
        
        BufferedImage image;
        File imageFile = new File(testData,"sortby_a.tiff");
        imageMosaicToGeoTiff(mosaicDir, 
                imageFile,
                params);
        
        image = ImageIO.read(imageFile);
        assertEquals(0xFF0000FE, image.getRGB(75, 75));//blue
        
        sortBy = ImageMosaicFormat.SORT_BY
                .createValue();
        sortBy.setValue("sort D");
        params[0] = sortBy;
        
        imageFile = new File(testData, "sortby_d.tiff");
        imageMosaicToGeoTiff(mosaicDir, 
                imageFile,
                params);
        
        image = ImageIO.read(imageFile);
        assertEquals(0xFFFE0000,image.getRGB(75, 75));//red
        
    }
    
    @Test
    public void testFootprintOverlap() throws IOException, ParseException {
        File mosaicDir = new File(testData,"overlap");
        basicRedBlueMosaic(mosaicDir);
        
        BufferedWriter writer = new BufferedWriter(new FileWriter(new File(mosaicDir, "red.wkt")));
        writer.write("POLYGON((-30 -30, -30 10, 10 10, 10 -30, -30 -30),(-20 -20, -20 0, 0 0, 0 -20, -20 -20))");
        writer.newLine();
        writer.close();
        
        writer = new BufferedWriter(new FileWriter(new File(mosaicDir, "blue.wkt")));
        writer.write("POLYGON((-10 -10, -10 30, 30 30, 30 -10, -10 -10),(0 0, 0 20, 20 20, 20 0, 0 0))");
        writer.newLine();
        writer.close();
        
        GeneralParameterValue[] params = new GeneralParameterValue[1];
        ParameterValue<String> footprintManagement = ImageMosaicFormat.FOOTPRINT_BEHAVIOR
                .createValue();
        footprintManagement.setValue(FootprintBehavior.Transparent.name());
        params[0] = footprintManagement;
        
        File imageFile = new File(testData,"overlap.tiff");
        imageFile.delete();
        
        imageMosaicToGeoTiff(mosaicDir, 
                imageFile,
                params);
        
        BufferedImage image = ImageIO.read(imageFile);
        assertEquals(0xFFFE0000, image.getRGB(80, 70));//red
        assertEquals(0xFF0000FE, image.getRGB(70, 80));//blue
    }
    
    @Test
    public void testDifferentResolutions() throws IOException {
        File mosaicDir = new File(testData,"grid");
        File tiff = new File(testData, "grid.tiff");
        
        Map<File, ReferencedEnvelope> rasters = new HashMap<File, ReferencedEnvelope>();
        CoordinateReferenceSystem crs = DefaultGeographicCRS.WGS84;
        
        rasters.put(new File(testData,"grid50.tiff"), new ReferencedEnvelope(-30, 10, -30, 10, crs));
        rasters.put(new File(testData,"grid100.tiff"), new ReferencedEnvelope(30, -10, 30, -10, crs));
        
        ImageMosaicReader mosaic = makeImageMosaic(mosaicDir, rasters);
        mosaic.dispose();
        
        imageMosaicToGeoTiff(mosaicDir, tiff, new GeneralParameterValue[0]);
        
        BufferedImage image = ImageIO.read(tiff);
        assertEquals(0xFFFE0000, image.getRGB(0, 149));//red
        assertEquals(0xFFFE0000, image.getRGB(1, 149));//red
        assertEquals(0xFFFEFEFE, image.getRGB(2, 149));//white
        assertEquals(0xFFFEFEFE, image.getRGB(148, 0));//white
        assertEquals(0xFF0000FE, image.getRGB(149, 0));//blue
        
    }
    
    public void imageMosaicToGeoTiff(File mosaicDir, File tiff) throws IOException {
        imageMosaicToGeoTiff(mosaicDir, tiff, new GeneralParameterValue[0]);
    }
    
    public void imageMosaicToGeoTiff(File mosaicDir, File tiff, GeneralParameterValue[] params) throws IOException {
        ImageMosaicReader reader = (ImageMosaicReader) new ImageMosaicFormatFactory().createFormat()
                .getReader(mosaicDir);
        
        GridCoverage2D coverage = reader.read(params);
        
        GeoTiffWriter writer = new GeoTiffWriter(tiff);
        writer.write(coverage, new GeneralParameterValue[0]);
        
        reader.dispose();
        writer.dispose();
    }
}
