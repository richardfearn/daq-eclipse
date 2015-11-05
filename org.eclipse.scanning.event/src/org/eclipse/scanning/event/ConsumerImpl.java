package org.eclipse.scanning.event;

import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.QueueConnectionFactory;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.eclipse.scanning.api.event.EventException;
import org.eclipse.scanning.api.event.IEventConnectorService;
import org.eclipse.scanning.api.event.IEventService;
import org.eclipse.scanning.api.event.alive.HeartbeatBean;
import org.eclipse.scanning.api.event.alive.KillBean;
import org.eclipse.scanning.api.event.bean.BeanEvent;
import org.eclipse.scanning.api.event.bean.IBeanListener;
import org.eclipse.scanning.api.event.core.IConsumer;
import org.eclipse.scanning.api.event.core.IConsumerProcess;
import org.eclipse.scanning.api.event.core.IProcessCreator;
import org.eclipse.scanning.api.event.core.IPublisher;
import org.eclipse.scanning.api.event.core.ISubmitter;
import org.eclipse.scanning.api.event.core.ISubscriber;
import org.eclipse.scanning.api.event.status.Status;
import org.eclipse.scanning.api.event.status.StatusBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsumerImpl<U extends StatusBean> extends QueueAbstractConnection<U> implements IConsumer<U> {
	
	private static final Logger logger = LoggerFactory.getLogger(ConsumerImpl.class);
	private static final long   ADAY   = 24*60*60*1000; // ms

	private String                        name;
	private UUID                          consumerId;
	private IPublisher<U>                 status;
	private IPublisher<HeartbeatBean>     alive;
	private ISubscriber<IBeanListener<StatusBean>> terminator;
	private ISubscriber<IBeanListener<KillBean>> killer;
	private ISubmitter<U>                 mover;

	private IProcessCreator<U>            runner;
	private boolean                       durable;
	private MessageConsumer               consumer;
	
	private volatile boolean              active;
	private volatile Map<String, WeakReference<IConsumerProcess<U>>>  processes;

	@SuppressWarnings("unchecked")
	ConsumerImpl(URI uri, String submitQName, 
			              String statusQName,
			              String statusTName, 
			              String heartbeatTName,
			              String killTName,
			              IEventConnectorService service,
			              IEventService          eservice) throws EventException {
		
		super(uri, submitQName, statusQName, statusTName, killTName, service);
		
		durable    = true;
		consumerId = UUID.randomUUID();
		name       = "Consumer "+consumerId; // This will hopefully be changed to something meaningful...
		this.processes = new Hashtable<>(7);
		
		mover  = eservice.createSubmitter(uri, statusQName, service);
		status = eservice.createPublisher(uri, statusTName, service);
		status.setQueueName(statusQName); // We also update values in a queue.
		
		if (heartbeatTName!=null) { 
			alive  = eservice.createPublisher(uri, heartbeatTName,  service);
			alive.setConsumerId(consumerId);
			alive.setConsumerName(getName());
		}
		
		terminator = eservice.createSubscriber(uri, statusTName, service);
		terminator.addListener(new IBeanListener<StatusBean>() {
			@Override
			public Class<StatusBean> getBeanClass() {
				return StatusBean.class;
			}

			@Override
			public void beanChangePerformed(BeanEvent<StatusBean> evt) {
				StatusBean bean = evt.getBean();
				if (bean.getStatus()!=Status.REQUEST_TERMINATE) return;
				
				WeakReference<IConsumerProcess<U>> ref = processes.remove(bean.getUniqueId());
				if (ref==null) return;
				IConsumerProcess<U> process = ref.get();
				if (process!=null) {
					try {
						process.terminate();
					} catch (EventException e) {
						logger.error("Cannot terminate process "+process.getBean().getUniqueId(), e);
					}
				}
			}
		});
		
		if (killTName!=null) {
			killer = eservice.createSubscriber(uri, killTName, service);
			killer.addListener(new IBeanListener<KillBean>() {
				@Override
				public Class<KillBean> getBeanClass() {
					return KillBean.class;
				}
	
				@Override
				public void beanChangePerformed(BeanEvent<KillBean> evt) {
					KillBean kbean = evt.getBean();
					if (kbean.getConsumerId().equals(getConsumerId())) {
						try {
							stop();
							if (kbean.isDisconnect()) disconnect();
						} catch (EventException e) {
							logger.error("An internal error occurred trying to terminate the consumer "+getName()+" "+getConsumerId());
						}
						if (kbean.isExitProcess()) {
							try {
								Thread.sleep(2500);
							} catch (InterruptedException e) {
								logger.error("Unable to pause before exit", e);
							}
							System.exit(0); // Normal orderly exit
						}
					}
				}
			});
		}
	}

	@Override
	public void disconnect() throws EventException {
		setActive(false);
		mover.disconnect();
		status.disconnect();
		alive.disconnect();
		killer.disconnect();
		try {
			if (connection!=null) connection.close();
		} catch (JMSException e) {
			throw new EventException("Cannot close consumer connection!", e);
		}
	}


	@Override
	public List<U> getSubmissionQueue() throws EventException {
		return getQueue(getSubmitQueueName());
	}

	@Override
	public List<U> getStatusQueue() throws EventException {
		return getQueue(getStatusQueueName());
	}

	@Override
	public void setRunner(IProcessCreator<U> runner) throws EventException {
		this.runner = runner;
		this.active = runner!=null;
	}
	
	@Override
	public void start() {
		
		final Thread consumerThread = new Thread("Consumer Thread "+getName()) {
			public void run() {
				try {
					ConsumerImpl.this.run();
				} catch (Exception ne) {
					logger.error("Internal error running consumer "+getName(), ne);
					ne.printStackTrace();
					try {
						ConsumerImpl.this.stop();
					} catch (EventException e) {
						ne.printStackTrace();
					}
				}
			}
		};
		consumerThread.setDaemon(true);
		consumerThread.setPriority(Thread.NORM_PRIORITY-1);
		consumerThread.start();
	}
	
	@Override
	public void stop() throws EventException {
        setActive(false);
        alive.setAlive(false);
        final WeakReference<IConsumerProcess<U>>[] wra = processes.values().toArray(new WeakReference[processes.size()]);
        for (WeakReference<IConsumerProcess<U>> wr : wra) {
			if (wr.get()!=null) wr.get().terminate();
		}
        processes.clear();
	}
	
	@Override
	public void run() throws EventException {
		
		if (runner!=null) {
			alive.setAlive(true);
		} else {
			throw new EventException("Cannot start a consumer without a runner to run things!");
		}
		
		long waitTime = 0;
		 
		while(isActive()){
        	try {
        		
        		// Consumes messages from the queue.
	        	Message m = getMessage(uri, getSubmitQueueName());
	            if (m!=null) {
		        	waitTime = 0; // We got a message
	            	
	            	// TODO FIXME Check if we have the max number of processes
	            	// exceeded and wait until we don't...
	            	
	            	TextMessage t = (TextMessage)m;
	            	
	            	final String     str  = t.getText();
                    final U bean   = service.unmarshal(str, getBeanClass());
	            	executeBean(bean);
	            	
	            }
	            
        	} catch (EventException ne) {
        		throw ne;
         		
        	} catch (Throwable ne) {
        		
        		if (ne.getClass().getSimpleName().endsWith("JsonMappingException")) {
            		logger.error("Fatal except deserializing object!", ne);
            		continue;
        		}
        		if (ne.getClass().getSimpleName().endsWith("UnrecognizedPropertyException")) {
        			logger.error("Cannot deserialize bean!", ne);
            		continue;
        		}
        		
        		if (!isDurable()) break;
        		        		
       			try {
					Thread.sleep(Constants.getNotificationFrequency());
				} catch (InterruptedException e) {
					throw new EventException("The consumer was unable to wait!", e);
				}
       			
       			waitTime+=Constants.getNotificationFrequency();
    			checkTime(waitTime); 
    			
        		logger.warn(getName()+" ActiveMQ connection to "+uri+" lost.");
        		logger.warn("We will check every 2 seconds for 24 hours, until it comes back.");

        		continue;
        	}

		}
	}
	
	private void executeBean(U bean) throws EventException {
		
		// We record the bean in the status queue
		mover.submit(bean);
		
		// Run the process
		if (runner == null) {
			bean.setStatus(Status.FAILED);
			bean.setMessage("No runner set for consumer "+getName()+". Nothing run");
			status.broadcast(bean);
			throw new EventException("You must set the runner before executing beans from the queue!");
		}
		
		if (processes.containsKey(bean.getUniqueId())) {
			throw new EventException("The bean with unique id '"+bean.getUniqueId()+"' has already been used. Cannot run the same uuid twice!");
		}
		
		IConsumerProcess<U> process = runner.createProcess(bean, status);
		processes.put(bean.getUniqueId(), new WeakReference<IConsumerProcess<U>>(process));
		process.execute(); // Depending on the process this may or may not run in a separate thread.
	}

	protected void checkTime(long waitTime) {
		
		if (waitTime>ADAY) {
			setActive(false);
			logger.warn("ActiveMQ permanently lost. "+getName()+" will now shutdown!");
			System.exit(0);
		}
	}

	private Message getMessage(URI uri, String submitQName) throws InterruptedException, JMSException {
		
		try {
			if (this.consumer == null) {
				this.consumer = createConsumer(uri, submitQName);
			}
			
			return consumer.receive(1000);
			
		} catch (Exception ne) {
			consumer = null;
			try {
				connection.close();
			} catch (Exception expected) {
				logger.info("Cannot close old connection", ne);
			}
			throw ne;
		}
	}

	private MessageConsumer createConsumer(URI uri, String submitQName) throws JMSException {
		
		QueueConnectionFactory connectionFactory = (QueueConnectionFactory)service.createConnectionFactory(uri);
		this.connection = connectionFactory.createQueueConnection();
		Session   session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		Queue queue = session.createQueue(submitQName);

		final MessageConsumer consumer = session.createConsumer(queue);
		connection.start();
		
		logger.warn(getName()+" Submission ActiveMQ connection to "+uri+" made.");
		
		return consumer;
	}


	@Override
	public IProcessCreator<U> getRunner() {
		return runner;
	}

	@Override
	public UUID getConsumerId() {
		return consumerId;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
		if (alive!=null) alive.setConsumerName(getName());
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public boolean isDurable() {
		return durable;
	}

	public void setDurable(boolean durable) {
		this.durable = durable;
	}
}
