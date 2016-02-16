package org.eclipse.scanning.test.scan.nexus;

import static org.eclipse.dawnsci.nexus.test.util.NexusAssert.assertAxes;
import static org.eclipse.dawnsci.nexus.test.util.NexusAssert.assertIndices;
import static org.eclipse.dawnsci.nexus.test.util.NexusAssert.assertSignal;
import static org.eclipse.dawnsci.nexus.test.util.NexusAssert.assertTarget;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.tree.TreeFile;
import org.eclipse.dawnsci.analysis.dataset.impl.PositionIterator;
import org.eclipse.dawnsci.nexus.INexusFileFactory;
import org.eclipse.dawnsci.nexus.NXdata;
import org.eclipse.dawnsci.nexus.NXdetector;
import org.eclipse.dawnsci.nexus.NXentry;
import org.eclipse.dawnsci.nexus.NXinstrument;
import org.eclipse.dawnsci.nexus.NXpositioner;
import org.eclipse.dawnsci.nexus.NXroot;
import org.eclipse.dawnsci.nexus.NexusException;
import org.eclipse.dawnsci.nexus.NexusFile;
import org.eclipse.dawnsci.nexus.NexusUtils;
import org.eclipse.scanning.api.event.scan.DeviceState;
import org.eclipse.scanning.api.points.GeneratorException;
import org.eclipse.scanning.api.points.IPointGenerator;
import org.eclipse.scanning.api.points.IPointGeneratorService;
import org.eclipse.scanning.api.points.IPosition;
import org.eclipse.scanning.api.points.models.BoundingBox;
import org.eclipse.scanning.api.points.models.GridModel;
import org.eclipse.scanning.api.points.models.StepModel;
import org.eclipse.scanning.api.scan.AbstractRunnableDevice;
import org.eclipse.scanning.api.scan.IDeviceConnectorService;
import org.eclipse.scanning.api.scan.IDeviceService;
import org.eclipse.scanning.api.scan.IRunnableDevice;
import org.eclipse.scanning.api.scan.IRunnableEventDevice;
import org.eclipse.scanning.api.scan.IWritableDetector;
import org.eclipse.scanning.api.scan.ScanningException;
import org.eclipse.scanning.api.scan.event.IRunListener;
import org.eclipse.scanning.api.scan.event.RunEvent;
import org.eclipse.scanning.api.scan.models.ScanModel;
import org.eclipse.scanning.example.detector.DarkImageModel;
import org.eclipse.scanning.example.detector.MandelbrotModel;
import org.eclipse.scanning.points.PointGeneratorFactory;
import org.eclipse.scanning.sequencer.DeviceServiceImpl;
import org.eclipse.scanning.test.scan.mock.MockScannableConnector;
import org.junit.BeforeClass;
import org.junit.Test;

public class DarkPluginTest {

	
	private static INexusFileFactory   fileFactory;
	
	private static IDeviceService        service;
	private static IPointGeneratorService       gservice;
	private static IDeviceConnectorService connector;
	
	private static IWritableDetector<MandelbrotModel> detector;
	private static IWritableDetector<DarkImageModel>  dark;

	@BeforeClass
	public static void before() throws Exception {
		
		connector = new MockScannableConnector();
		service   = new DeviceServiceImpl(connector); // Not testing OSGi so using hard coded service.
		gservice  = new PointGeneratorFactory();
		
		MandelbrotModel model = new MandelbrotModel();
		model.setName("mandelbrot");
		model.setxName("xNex");
		model.setyName("yNex");
		
		detector = (IWritableDetector<MandelbrotModel>)service.createRunnableDevice(model);
		assertNotNull(detector);
		detector.addRunListener(new IRunListener.Stub() {
			@Override
			public void runPerformed(RunEvent evt) throws ScanningException{
                System.out.println("Ran mandelbrot detector @ "+evt.getPosition());
			}
		});

		DarkImageModel dmodel = new DarkImageModel();
		dark =  (IWritableDetector<DarkImageModel>)service.createRunnableDevice(dmodel);
		assertNotNull(dark);
		dark.addRunListener(new IRunListener.Stub() {
			@Override
			public void writePerformed(RunEvent evt) throws ScanningException{
                System.out.println("Wrote dark image @ "+evt.getPosition());
			}
		});
	}
	
	/**
	 * This test fails if the chunking is not done by the detector.
	 *  
	 * @throws Exception
	 */
	@Test
	public void testDarkImage() throws Exception {	

		// Tell configure detector to write 1 image into a 2D scan
		IRunnableDevice<ScanModel> scanner = createGridScan(8, 5);
		scanner.run(null);

		checkNexusFile(scanner, 8, 5);
	}
	
	private void checkNexusFile(IRunnableDevice<ScanModel> scanner, int... sizes) throws NexusException, ScanningException {
		final ScanModel scanModel = ((AbstractRunnableDevice<ScanModel>) scanner).getModel();
		assertEquals(DeviceState.READY, scanner.getDeviceState());
		
		String filePath = ((AbstractRunnableDevice<ScanModel>) scanner).getModel().getFilePath();
		NexusFile nf = fileFactory.newNexusFile(filePath);
		nf.openToRead();
		TreeFile nexusTree = NexusUtils.loadNexusTree(nf);
		NXroot rootNode = (NXroot) nexusTree.getGroupNode();
		NXentry entry = rootNode.getEntry();

		List<String> positionerNames = scanModel.getPositionIterable().iterator().next().getNames();

		// check the data for the dark detector
		checkDark(rootNode, positionerNames, sizes);
		
		// check the images from the mandelbrot detector
		checkImages(rootNode, positionerNames, sizes);
	}
		
	private void checkDark(NXroot rootNode, List<String> positionerNames, int... sizes) throws NexusException, ScanningException {
		String detectorName = dark.getName();
		NXentry entry = rootNode.getEntry();
		NXdetector detector = entry.getInstrument().getDetector(detectorName);
		assertNotNull(detector);
		IDataset ds = detector.getDataNode(NXdetector.NX_DATA).getDataset().getSlice();
		int[] shape = ds.getShape();                                                                        

		double size = 1; 
		for (double i : sizes)size*=i;
		size = size / (((AbstractRunnableDevice<DarkImageModel>)dark).getModel().getFrequency());
		assertEquals(shape[0], (int)size);
		
		checkNXdata(rootNode, detectorName, positionerNames);
	}
	
	private void checkImages(NXroot rootNode, List<String> positionerNames, int... sizes) throws NexusException, ScanningException {
		String detectorName = detector.getName();
		NXentry entry = rootNode.getEntry();
		NXinstrument instrument = entry.getInstrument();
		NXdetector detector = instrument.getDetector(detectorName);
		assertNotNull(detector);
		IDataset ds = detector.getDataNode(NXdetector.NX_DATA).getDataset().getSlice();
		
		int[] shape = ds.getShape();                                                                        
		for (int i = 0; i < sizes.length; i++) assertEquals(sizes[i], shape[i]);                            
		                                                                                                    
		// Make sure none of the numbers are NaNs. The detector                                             
		// is expected to fill this scan with non-nulls.                                                    
        final PositionIterator it = new PositionIterator(shape);                                            
        while(it.hasNext()) {
        	int[] next = it.getPos();
        	assertFalse(Double.isNaN(ds.getDouble(next)));
        }

		// Check axes
        // Demand values should be 1D
        for (int i = 0; i < positionerNames.size(); i++) {
        	String positionerName = positionerNames.get(i);
        	NXpositioner positioner = instrument.getPositioner(positionerName);
        	assertNotNull(positioner);
        	ds = positioner.getDataNode(NXpositioner.NX_VALUE + "_demand").getDataset().getSlice();
    		shape = ds.getShape();
			assertEquals(1, shape.length);
		    assertEquals(sizes[i], shape[0]);
        
    		// Actual values should be scanD
        	ds = positioner.getDataNode(NXpositioner.NX_VALUE).getDataset().getSlice();
    		shape = ds.getShape();
    		assertArrayEquals(sizes, shape);
		}
        
        checkNXdata(rootNode, detectorName, positionerNames);
	}
	
	private void checkNXdata(NXroot rootNode, String detectorName, List<String> scannableNames) {
		NXentry entry = rootNode.getEntry();
		NXdata data = entry.getData(detectorName);
		assertNotNull(data);
		
		// check the default data field for the NXdata group
		assertSignal(data, NXdata.NX_DATA);
		assertSame(data.getDataNode(NXdata.NX_DATA), 
				entry.getInstrument().getDetector(detectorName).getDataNode(NXdetector.NX_DATA));
		assertTarget(data, NXdata.NX_DATA, rootNode, "/entry/instrument/" + detectorName + "/data");

		int rank = entry.getInstrument().getDetector(detectorName).getDataNode(NXdetector.NX_DATA).getRank();
		String[] expectedAxesNames = Stream.concat(scannableNames.stream().map(x -> x + "_value_demand"),
				Collections.nCopies(rank - scannableNames.size(), ".").stream()).toArray(String[]::new);
		assertAxes(data, expectedAxesNames);
		int[] defaultDimensionMappings = IntStream.range(0, scannableNames.size()).toArray();
		
		// check the value_demand and value fields for each scannable
		for (int i = 0; i < scannableNames.size(); i++) {
			String positionerName = scannableNames.get(i);
			NXpositioner positioner = entry.getInstrument().getPositioner(positionerName);
			
			// check value_demand data node
			String demandFieldName = positionerName + "_" + NXpositioner.NX_VALUE + "_demand";
			assertSame(data.getDataNode(demandFieldName), positioner.getDataNode("value_demand"));
			assertIndices(data, demandFieldName, i);
			assertTarget(data, demandFieldName, rootNode,
					"/entry/instrument/" + positionerName + "/value_demand");
			
			// check value data node
			String valueFieldName = positionerName + "_" + NXpositioner.NX_VALUE;
			assertSame(data.getDataNode(valueFieldName), positioner.getDataNode(NXpositioner.NX_VALUE));
			assertIndices(data, valueFieldName, defaultDimensionMappings);
			assertTarget(data, valueFieldName, rootNode,
					"/entry/instrument/" + positionerName + "/" + NXpositioner.NX_VALUE);
		}
	}

	private IRunnableDevice<ScanModel> createGridScan(int... size) throws Exception {
		
		// Create scan points for a grid and make a generator
		GridModel gmodel = new GridModel();
		gmodel.setxName("xNex");
		gmodel.setColumns(size[size.length-2]);
		gmodel.setyName("yNex");
		gmodel.setRows(size[size.length-1]);
		gmodel.setBoundingBox(new BoundingBox(0,0,3,3));
		
		IPointGenerator<?,IPosition> gen = gservice.createGenerator(gmodel);
		
		// We add the outer scans, if any
		if (size.length > 2) { 
			for (int dim = size.length-3; dim>-1; dim--) {
				final StepModel model;
				if (size[dim]-1>0) {
				    model = new StepModel("neXusScannable"+(dim+1), 10,20,9.9d/(size[dim]-1));
				} else {
					model = new StepModel("neXusScannable"+(dim+1), 10,20,30); // Will generate one value at 10
				}
				final IPointGenerator<?,IPosition> step = gservice.createGenerator(model);
				gen = gservice.createCompoundGenerator(step, gen);
			}
		}
	
		// Create the model for a scan.
		final ScanModel  smodel = new ScanModel();
		smodel.setPositionIterable(gen);
		smodel.setDetectors(detector, dark);
		
		// Create a file to scan into.
		File output = File.createTempFile("test_dark_nexus", ".nxs");
		output.deleteOnExit();
		smodel.setFilePath(output.getAbsolutePath());
		System.out.println("File writing to "+smodel.getFilePath());

		// Create a scan and run it without publishing events
		IRunnableDevice<ScanModel> scanner = service.createRunnableDevice(smodel, null);
		
		final IPointGenerator<?,IPosition> fgen = gen;
		((IRunnableEventDevice<ScanModel>)scanner).addRunListener(new IRunListener.Stub() {
			@Override
			public void runWillPerform(RunEvent evt) throws ScanningException{
                try {
					System.out.println("Running acquisition scan of size "+fgen.size());
				} catch (GeneratorException e) {
					throw new ScanningException(e);
				}
			}
		});

		return scanner;
	}

	public static INexusFileFactory getFileFactory() {
		return fileFactory;
	}

	public static void setFileFactory(INexusFileFactory fileFactory) {
		DarkPluginTest.fileFactory = fileFactory;
	}

}
