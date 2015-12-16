package org.eclipse.scanning.test.scan;

import org.eclipse.scanning.api.scan.ScanningException;
import org.eclipse.scanning.event.EventServiceImpl;
import org.eclipse.scanning.points.GeneratorServiceImpl;
import org.eclipse.scanning.sequencer.ScannerServiceImpl;
import org.eclipse.scanning.test.scan.mock.MockScannableConnector;
import org.junit.Before;

public class ScanTest extends AbstractScanTest {
	
	
	@Before
	public void setup() throws ScanningException {
		// We wire things together without OSGi here 
		// DO NOT COPY THIS IN NON-TEST CODE!
		sservice  = new ScannerServiceImpl();
		connector = new MockScannableConnector();
		gservice  = new GeneratorServiceImpl();
		eservice  = new EventServiceImpl();
	}
}
