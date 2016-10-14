package org.eclipse.scanning.test.event.queues.processors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.eclipse.scanning.api.event.queues.beans.QueueAtom;
import org.eclipse.scanning.api.event.status.Status;
import org.eclipse.scanning.event.queues.QueueServicesHolder;
import org.eclipse.scanning.event.queues.beans.SubTaskAtom;
import org.eclipse.scanning.event.queues.processors.SubTaskAtomProcessor;
import org.eclipse.scanning.test.event.queues.dummy.DummyAtom;
import org.eclipse.scanning.test.event.queues.mocks.MockEventService;
import org.eclipse.scanning.test.event.queues.mocks.MockPublisher;
import org.eclipse.scanning.test.event.queues.mocks.MockQueueService;
import org.eclipse.scanning.test.event.queues.mocks.MockSubmitter;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class SubTaskAtomProcessorTest {
	
	private SubTaskAtom stAt;
	private SubTaskAtomProcessor stAtProcr;
	private ProcessorTestInfrastructure pti;
	
	private static MockQueueService mockQServ;
	private static MockSubmitter<QueueAtom> mockSub;
	private static MockEventService mockEvServ;
	private static MockPublisher<QueueAtom> mockPub;
	
	@BeforeClass
	public static void setUpClass() {
		//Configure the processor Mock queue infrastructure
		mockSub = new MockSubmitter<>();
		mockQServ = new MockQueueService();
		mockQServ.setMockSubmitter(mockSub);
		QueueServicesHolder.setQueueService(mockQServ);
		
		
		mockPub = new MockPublisher<>(null, null);
		mockEvServ = new MockEventService();
		mockEvServ.setMockPublisher(mockPub);
		QueueServicesHolder.setEventService(mockEvServ);
	}
	
	@AfterClass
	public static void tearDownClass() {
		QueueServicesHolder.unsetEventService(mockEvServ);
		mockEvServ = null;
		mockPub = null;
		
		QueueServicesHolder.unsetQueueService(mockQServ);
		mockQServ = null;
		mockSub = null;
	}
	
	@Before
	public void setUp() {
		pti = new ProcessorTestInfrastructure();
		
		//Create processor & test atom
		stAt = new SubTaskAtom("Test queue sub task bean");
		stAt.setBeamline("I15-1(test)");
		stAt.setHostName("afakeserver.diamond.ac.uk");
		stAt.setUserName(System.getProperty("user.name"));
		DummyAtom atomA = new DummyAtom("Hildebrand", 300);
		DummyAtom atomB = new DummyAtom("Yuri", 1534);
		DummyAtom atomC = new DummyAtom("Ingrid", 654);
		stAt.queue().add(atomA);
		stAt.queue().add(atomB);
		stAt.queue().add(atomC);
		
		//Reset queue architecture
		mockSub.resetSubmitter();
		mockPub.resetPublisher();
	}
	
	@After
	public void tearDown() {
		pti = null;
	}
	
	@Test
	public void testExecution() throws Exception {
		stAtProcr = new SubTaskAtomProcessor();
		
		pti.executeProcessor(stAtProcr, stAt);
		
		assertTrue("Execute flag not set true after execution", stAtProcr.isExecuted());
		
		stAtProcr.getQueueBroadcaster().broadcast(Status.RUNNING, 99.5d, "Running finished.");
		stAtProcr.getProcessorLatch().countDown();
		pti.exceptionCheck();
		
		//These are the statuses & percent completes reported by the processor as it sets up the run
		Status[] reportedStatuses = new Status[]{Status.RUNNING, Status.RUNNING,
				Status.RUNNING, Status.RUNNING};
		Double[] reportedPercent = new Double[]{0d, 1d, 
				4d, 5d};
		
		pti.checkFirstBroadcastBeanStatuses(stAt, reportedStatuses, reportedPercent);
		pti.checkLastBroadcastBeanStatuses(stAt, Status.COMPLETE, true);
		
		checkSubmittedBeans(mockSub);
	}
	
	@Test
	public void testTermination() throws Exception {
		stAtProcr = new SubTaskAtomProcessor();
		
		pti.executeProcessor(stAtProcr, stAt);
		//Set some arbitrary percent complete
		stAtProcr.getQueueBroadcaster().broadcast(Status.REQUEST_TERMINATE, 20d);
		
		/*
		 * terminate is usually called as follows:
		 * AbstractPausableProcess.terminate() -> QueueProcess.doTerminate -> stAtProcr.terminate()
		 */
		pti.getQProc().terminate();
		pti.exceptionCheck();
		assertTrue("Terminated flag not set true after termination", stAtProcr.isTerminated());
		pti.checkLastBroadcastBeanStatuses(stAt, Status.TERMINATED, true);
		//TODO Should this be the message or the queue-message?
		assertEquals("Wrong message set after termination.", "Active-queue aborted before completion (requested)", pti.getLastBroadcastBean().getMessage());
		
	}
	
	@Test
	public void testChildFailure() throws Exception {
		stAtProcr = new SubTaskAtomProcessor();
		
		pti.executeProcessor(stAtProcr, stAt);
		//Set some arbitrary percent complete and release the latch
		stAtProcr.getQueueBroadcaster().broadcast(Status.RUNNING, 20d);
		stAtProcr.getProcessorLatch().countDown();
		//Need to give the post-match analysis time to run
		Thread.sleep(10);
		
		/*
		 * FAILED is always going to happen underneath - i.e. process will be 
		 * running & suddenly latch will be counted down.
		 * 
		 * QueueListener sets the message and queueMessage
		 * We just need to set this bean's status to FAILED.
		 */
		pti.checkLastBroadcastBeanStatuses(stAt, Status.FAILED, false);
	}
	
	protected void checkSubmittedBeans(MockSubmitter<QueueAtom> ms) throws Exception {
		List<QueueAtom> submittedBeans = ms.getQueue();
		assertTrue("No beans in the final status set", submittedBeans.size() != 0);
		for (QueueAtom dummy : submittedBeans) {
			//First check beans are in final state
			assertTrue("Final bean "+dummy.getName()+" is not final",dummy.getStatus().isFinal());
			//Check the properties of the ScanAtom have been correctly passed down
			assertFalse("No beamline set", dummy.getBeamline() == null);
			assertEquals("Incorrect beamline", stAt.getBeamline(), dummy.getBeamline());
			assertFalse("No hostname set", dummy.getHostName() == null);
			assertEquals("Incorrect hostname", stAt.getHostName(), dummy.getHostName());
			assertFalse("No username set", dummy.getUserName() == null);
			assertEquals("Incorrect username", stAt.getUserName(), dummy.getUserName());
		}
	}

}