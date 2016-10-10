package org.eclipse.scanning.test.event.queues;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;

import org.eclipse.scanning.api.event.EventException;
import org.eclipse.scanning.api.event.queues.IQueueNew;
import org.eclipse.scanning.api.event.queues.QueueStatus;
import org.eclipse.scanning.api.event.queues.beans.Queueable;
import org.eclipse.scanning.event.queues.QueueNew;
import org.eclipse.scanning.event.queues.QueueServicesHolder;
import org.eclipse.scanning.test.event.queues.dummy.DummyBean;
import org.eclipse.scanning.test.event.queues.mocks.MockConsumer;
import org.eclipse.scanning.test.event.queues.mocks.MockEventService;
import org.junit.Before;
import org.junit.Test;

public class QueueNewTest {
	
	private MockConsumer<DummyBean> mockCons;
	private MockEventService mockEvServ = new MockEventService();
	private String qRoot = "test-queue";
	private URI uri;
	
	@Before
	public void setUp() throws Exception {
		mockCons = new MockConsumer<>();
		mockEvServ.setMockConsumer(mockCons);
		
		QueueServicesHolder.setEventService(mockEvServ);
		uri = new URI("file:///foo/bar");
	}
	
	/**
	 * Test the correct instantiation of the Queue object.
	 * @throws EventException 
	 */
	@Test
	public void testQueueConfig() throws EventException {
		IQueueNew<Queueable> testQueue = new QueueNew<>(qRoot, uri);
		assertEquals("Queue has wrong status", QueueStatus.INITIALISED, testQueue.getStatus());
		
		//Test we're setting destination names correctly on construction
		assertEquals("Configured & expected submit queue names differ", qRoot+IQueueNew.SUBMISSION_QUEUE_SUFFIX, testQueue.getSubmissionQueueName());
		assertEquals("Configured & expected status set names differ", qRoot+IQueueNew.STATUS_SET_SUFFIX, testQueue.getStatusSetName());
		assertEquals("Configured & expected status topic names differ", qRoot+IQueueNew.STATUS_TOPIC_SUFFIX, testQueue.getStatusTopicName());
		assertEquals("Configured & expected heartbeat topic names differ", qRoot+IQueueNew.HEARTBEAT_TOPIC_SUFFIX, testQueue.getHeartbeatTopicName());
		assertEquals("Configured & expected command set names differ", qRoot+IQueueNew.COMMAND_SET_SUFFIX, testQueue.getCommandSetName());
		assertEquals("Configured & expected command topic names differ", qRoot+IQueueNew.COMMAND_TOPIC_SUFFIX, testQueue.getCommandTopicName());
		
		//Test the consumer ID is being set on construction
		assertEquals(mockCons.getConsumerId(), testQueue.getConsumerID());
	}
	
	/**
	 * Test that queues can be cleared
	 * @throws EventException 
	 */
	@Test
	public void testClearQueues() throws EventException {
		IQueueNew<Queueable> testQueue = new QueueNew<>(qRoot, uri);
		
		boolean cleared = testQueue.clearQueues();
		assertTrue("Status queue not cleared", mockCons.isClearStatQueue());
		assertTrue("Submit queue not cleared", mockCons.isClearSubmitQueue());
		assertTrue("clearQueues failed to clear", cleared);
	}
	
	/**
	 * Tests starting & stopping the consumer through the Queue
	 * @throws EventException 
	 */
	@Test
	public void testStartStop() throws EventException {
		IQueueNew<Queueable> testQueue = new QueueNew<>(qRoot, uri);
		
		testQueue.start();
		assertTrue("Consumer start has not been called", mockCons.isStarted());
		assertEquals("Queue has wrong status", QueueStatus.STARTED, testQueue.getStatus());
		
		testQueue.stop();
		assertTrue("Consumer stop has not been called", mockCons.isStopped());
		assertEquals("Queue has wrong status", QueueStatus.STOPPED, testQueue.getStatus());
	}
	
	/**
	 * Tests disconnecting the consumer (and heartbeat monitor) through the Queue
	 * @throws EventException 
	 */
	@Test
	public void testDisconnect() throws EventException {
		IQueueNew<Queueable> testQueue = new QueueNew<>(qRoot, uri);
		
		testQueue.disconnect();
		assertTrue("Consumer disconnect has not been called", mockCons.isDisconnected());
		assertEquals("Queue has wrong status", QueueStatus.DISPOSED, testQueue.getStatus());
	}
}
