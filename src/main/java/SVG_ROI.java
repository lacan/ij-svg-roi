import java.awt.Color;
import java.awt.geom.PathIterator;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;

import org.apache.batik.dom.svg.SAXSVGDocumentFactory;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import fiji.util.gui.GenericDialogPlus;
import ij.ImageJ;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;

/**
 * Responsible for converting all SVG path elements into MetaPost curves.
 */
public class SVG_ROI implements PlugIn{
	private static final String PATH_ELEMENT_NAME = "path";

	@Override
	public void run(String arg0) {
		RoiManager rm = RoiManager.getInstance();
		if (rm == null) {
			rm = new RoiManager();
		}
		
		GenericDialogPlus gd = new GenericDialogPlus("SVGs To ROI Conversion");
		gd.addDirectoryField("SVG Folder", "");
		
		gd.showDialog();
		
		if (gd.wasCanceled())
			return;
		
		//Navigate folder
		File folder = new File(gd.getNextString());		
		
		File[] filelist = folder.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".svg");
			}
		});
		
		File save_folder = new File(folder.getAbsolutePath()+File.separator+"ROI Sets");
		save_folder.mkdirs();
		for (File file : filelist) {
			rm.reset();
			convertSVGToRois(file);
			// Save the ROIs to a subfolder
			rm.runCommand("Save", save_folder.getAbsolutePath()+File.separator+file.getName().substring(0, file.getName().length()-4)+".zip");
			
		}
		
		}
	public static void convertSVGToRois(File file) {
		RoiManager rm = RoiManager.getInstance2();
		if (rm == null) {
			rm = new RoiManager();
		}		
		
		URI uri = file.toURI();
		Document svg = null;
		//Prepare SVG file
		try {			
			svg = createDocument( uri.toString() );
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Iterate through the paths
		NodeList pathNodes = svg.getDocumentElement().getElementsByTagName( PATH_ELEMENT_NAME );
		int pathNodeCount = pathNodes.getLength();
		for( int iPathNode = 0; iPathNode < pathNodeCount; iPathNode++ ) {
			rm.addRoi(convertToShapeRoi(pathNodes.item(iPathNode).getAttributes()));
		}
	}
	
	public static ArrayList<Roi> convertSVGFileToRois(File file) {
		RoiManager rm = RoiManager.getInstance2();
		if (rm == null) {
			rm = new RoiManager();
		}		
		
		URI uri = file.toURI();
		Document svg = null;
		//Prepare SVG file
		try {			
			svg = createDocument( uri.toString() );
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		ArrayList<Roi> rois = new ArrayList<Roi>();
		
		// Iterate through the paths
		NodeList pathNodes = svg.getDocumentElement().getElementsByTagName( PATH_ELEMENT_NAME );
		int pathNodeCount = pathNodes.getLength();
		for( int iPathNode = 0; iPathNode < pathNodeCount; iPathNode++ ) {
			rois.add(convertToShapeRoi(pathNodes.item(iPathNode).getAttributes()));
		}
		return rois;
	}

	
	private static Roi convertToShapeRoi(NamedNodeMap pathAttributes) {
		//Get the paths
		String paths = pathAttributes.getNamedItem("d").getTextContent();
		
		// Remove the Z key if there
		int is_closed = 0;
		if (paths.endsWith("Z")) {
			paths = paths.substring(0, paths.length()-1);
			is_closed = 1;
		}
				
		// Get the fill or Stroke, at least
		String stroke = pathAttributes.getNamedItem("stroke").getTextContent();
		
		// Work on a shapeRoi, as this allows for multiple ROIs to make a shape...
		// ShapeRois can be built similarily to paths in SVG
	
		paths = paths.trim();
		String[] commands = paths.split("(?=L)|(?=M)|(?=C)");
		
		ArrayList<Float> instructions = new ArrayList<Float>();
		
		for (String command : commands ) {
			char c = command.charAt(0);
			command = command.trim();

			command = command.substring(1);

			switch (c) {
			case 'M': // This also means that we need to start a new path... 
				instructions.add((float)PathIterator.SEG_MOVETO);
				// Add the two coordinates
				instructions.addAll(parseCoordinateAsArray(command," "));
				
				break;

			case 'L':
				instructions.add((float)PathIterator.SEG_LINETO);
				instructions.addAll(parseCoordinateAsArray(command,","));
				break;
			case 'C':
				
				instructions.addAll(parseCurveAsArray(command));
				break;
			
			default:
				System.out.println("WHAT IS: "+c +": "+command+"?");
				break;
			}
		}
		
		float[] floatArray = new float[instructions.size()+is_closed];
		int i = 0;

		for (Float f : instructions) {
		    floatArray[i++] = (f != null ? f : Float.NaN); // Or whatever default you want.
		}
		if( is_closed == 1)
			floatArray[i] = PathIterator.SEG_CLOSE;
		
		ShapeRoi the_roi = new ShapeRoi(floatArray);
		the_roi.setStrokeColor(Color.decode(stroke));
		return the_roi;
	}

	private static ArrayList<Float> parseCurveAsArray(String command) {
		// Split at spaces first
		ArrayList<Float> curve_points = new  ArrayList<Float>();

		String[] curve = command.split(" ");
		// should be multiples of 3
		if (curve.length%3 == 0) {

			for (int i=0; i<curve.length/3; i++) {

				curve_points.add((float)PathIterator.SEG_CUBICTO);
				curve_points.addAll(parseCoordinateAsArray(curve[i*3],","));
				curve_points.addAll(parseCoordinateAsArray(curve[i*3+1],","));
				curve_points.addAll(parseCoordinateAsArray(curve[i*3+2],","));
			}
			return curve_points;
		}
		return curve_points;
	}
	
	private static ArrayList<Float> parseCoordinateAsArray(String cs, String delimiter) {
		String[] xy = cs.split(delimiter);
		ArrayList<Float> aPoint = new ArrayList<Float>();
		aPoint.add(Float.parseFloat(xy[0]));
		aPoint.add(Float.parseFloat(xy[1]));
		return aPoint;
	}

	/**
	 * Use the SAXSVGDocumentFactory to parse the given URI into a DOM.
	 * 
	 * @param uri The path to the SVG file to read.
	 * @return A Document instance that represents the SVG file.
	 * @throws Exception The file could not be read.
	 */
	private static Document createDocument( String uri ) throws IOException {
		String parser = XMLResourceDescriptor.getXMLParserClassName();
		SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory( parser );
		return factory.createDocument( uri );
	}

/**
 * Main method for debugging.
 * @param args unused
 */
public static void main(String[] args) {
	// set the plugins.dir property to make the plugin appear in the Plugins menu
	Class<?> clazz = SVG_ROI.class;
	String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
	String pluginsDir = url.substring(5, url.length() - clazz.getName().length() - 6);
	System.setProperty("plugins.dir", pluginsDir);

	// start ImageJ
	new ImageJ();
}
}