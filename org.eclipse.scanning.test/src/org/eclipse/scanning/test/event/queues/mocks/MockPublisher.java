package org.eclipse.scanning.test.event.queues.mocks;

import java.io.PrintStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.scanning.api.event.EventException;
import org.eclipse.scanning.api.event.IEventConnectorService;
import org.eclipse.scanning.api.event.core.IConsumer;
import org.eclipse.scanning.api.event.core.IPublisher;
import org.eclipse.scanning.api.event.queues.beans.IAtomWithChildQueue;
import org.eclipse.scanning.api.event.queues.beans.Queueable;
import org.eclipse.scanning.api.event.status.StatusBean;
import org.eclipse.scanning.test.event.queues.dummy.DummyHasQueue;

public class MockPublisher<T> implements IPublisher<T> {
	
	
	private String topicName;
	private final URI uri;
	private String queueName;
	private IConsumer<?> consumer;
	
	private volatile List<Queueable> broadcastBeans = new ArrayList<>();
<<<<<<< HEAD
	
	private boolean disconnected;
=======
>>>>>>> 1752c7276fb469d85630de3784673b5143fb9a7e
	
	private boolean alive;
	
	public MockPublisher(URI uri, String topic) {
		//Removed from sig: IEventConnectorService service
		this.topicName = topic;
		this.uri = uri;
		
		alive = true;
	}
	
	public void resetPublisher() {
		broadcastBeans.clear();
	}

	@Override
	public String getTopicName() {
		return topicName;
	}

	@Override
	public void setTopicName(String topic) throws EventException {
		this.topicName = topic;
		
	}

	@Override
	public void disconnect() throws EventException {
		setDisconnected(true);
	}

	@Override
	public URI getUri() {
		return uri;
	}
	
	@Override
	public void broadcast(T bean) throws EventException {
		final DummyHasQueue broadBean = new DummyHasQueue();
		StatusBean loBean = (StatusBean)bean;
		broadBean.setMessage(loBean.getMessage());
		broadBean.setPreviousStatus(loBean.getPreviousStatus());
		broadBean.setStatus(loBean.getStatus());
		broadBean.setPercentComplete(loBean.getPercentComplete());
		broadBean.setUniqueId(loBean.getUniqueId());
		broadBean.setName(loBean.getName());
		
		if (bean instanceof IAtomWithChildQueue) {
			broadBean.setQueueMessage(((IAtomWithChildQueue)bean).getQueueMessage());
		}
		
		broadcastBeans.add(broadBean);
	}
	
	public List<Queueable> getBroadcastBeans() {
		return broadcastBeans;
	}
	
	public Queueable getLastBean() {
		if (broadcastBeans.size() > 0) {
			return broadcastBeans.get(broadcastBeans.size()-1);
		} else {
			return null;
		}
	}

	@Override
	public void setAlive(boolean alive) throws EventException {
		this.alive = alive;
	}

	@Override
	public boolean isAlive() {
		return alive;
	}

	@Override
	public void setStatusSetName(String queueName) {
		this.queueName = queueName;
		
	}

	@Override
	public String getStatusSetName() {
		return queueName;
	}
	
	@Override
	public void setStatusSetAddRequired(boolean required) {
		throw new RuntimeException("setStatusSetAddRequired is not implemented!");
	}


	@Override
	public void setLoggingStream(PrintStream stream) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public IEventConnectorService getConnectorService() {
		// TODO Auto-generated method stub
		return null;
	}

	public IConsumer<?> getConsumer() {
		return consumer;
	}

	public void setConsumer(IConsumer<?> consumer) {
		this.consumer = consumer;
	}

	public boolean isDisconnected() {
		return disconnected;
	}

	public void setDisconnected(boolean disconnected) {
		this.disconnected = disconnected;
	}

}
