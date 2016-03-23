package org.eclipse.scanning.api.event.core;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.scanning.api.event.EventException;

public class ResponseConfiguration {
	
	public static final ResponseConfiguration DEFAULT = new ResponseConfiguration(ResponseType.ONE, 1, TimeUnit.SECONDS);
	
	public enum ResponseType {
		/**
		 * One response is required, after that comes in, return.
		 * If the timeout is reached throw an exception.
		 * 
		 * This is more efficient because the timeout is not waited for,
		 * it can return as soon as the first message is encountered.
		 */
		ONE, 
		
		/**
		 * Multiple reponses are required, wait for the
		 * reponse timeout and collate all reponses. If no
		 * response comes in, throw an exception.
		 * 
		 * Less efficient but caters for multiple sources 
		 * of the reponse if there may be some.
		 */
		ONE_OR_MORE;
	}

	private ResponseType responseType;
	private long         timeout;
	private TimeUnit     timeUnit;
	private CountDownLatch latch;
	private boolean somethingFound;
	
	public ResponseConfiguration() {
		this(ResponseType.ONE, 1, TimeUnit.SECONDS);
	}
	
	public ResponseConfiguration(ResponseType responseType, long timeout, TimeUnit timeUnit) {
		super();
		this.responseType = responseType;
		this.timeout  = timeout;
		this.timeUnit = timeUnit;
	}
	
	public void latch() throws EventException, InterruptedException {
		
		if (getResponseType()==ResponseType.ONE) {
			this.latch    = new CountDownLatch(1);
			boolean ok = latch.await(timeout, timeUnit);
			if (!ok) throw new EventException("The timeout of "+timeout+" "+timeUnit+" was reached and no response occurred!");
			
		} else if (getResponseType()==ResponseType.ONE_OR_MORE) {
			somethingFound = false;
			Thread.sleep(timeUnit.toMillis(timeout));
			if (!somethingFound) throw new EventException("The timeout of "+timeout+" "+timeUnit+" was reached and no response occurred!");
		}
	}
	
	public void countDown() {
		somethingFound = true;
		if (latch!=null) latch.countDown();
	}
	
	public ResponseType getResponseType() {
		return responseType;
	}
	public void setResponseType(ResponseType responseType) {
		this.responseType = responseType;
	}
	public long getTimeout() {
		return timeout;
	}
	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}
	public TimeUnit getTimeUnit() {
		return timeUnit;
	}
	public void setTimeUnit(TimeUnit timeUnit) {
		this.timeUnit = timeUnit;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((responseType == null) ? 0 : responseType.hashCode());
		result = prime * result + ((timeUnit == null) ? 0 : timeUnit.hashCode());
		result = prime * result + (int) (timeout ^ (timeout >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ResponseConfiguration other = (ResponseConfiguration) obj;
		if (responseType != other.responseType)
			return false;
		if (timeUnit != other.timeUnit)
			return false;
		if (timeout != other.timeout)
			return false;
		return true;
	}
}
