package org.eclipse.scanning.api.event.alive;

import java.util.UUID;

import org.eclipse.scanning.api.event.IdBean;

public class HeartbeatBean extends IdBean {

	public static final HeartbeatBean EMPTY = new HeartbeatBean();

	/**
	 * Beamline that the acquisition server is controlling
	 */
	private String  beamline;
	
	/**
	 * Time that the beat happened on the server
	 */
	private long    publishTime;
	
	/**
	 * Time that the heartbeater started or -1 is the checker does not provide this information
	 */
	private long    conceptionTime;

	/**
	 * Time that the beat happened on the server
	 */
	private long    lastAlive;

	/**
	 * Provides the consumer name, may be null.
	 */
	private String consumerName;

	/**
	 * Tells you the unique id of the thing that is alive. May be null.
	 */
	private UUID consumerId;

	private ConsumerStatus consumerStatus;
	
	private String hostName;

	public long getConceptionTime() {
		return conceptionTime;
	}

	public void setConceptionTime(long conceptionTime) {
		this.conceptionTime = conceptionTime;
	}

	public long getPublishTime() {
		return publishTime;
	}

	public void setPublishTime(long publishTime) {
		this.publishTime = publishTime;
	}

	public String getBeamline() {
		return beamline;
	}

	public void setBeamline(String beamline) {
		this.beamline = beamline;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result
				+ ((beamline == null) ? 0 : beamline.hashCode());
		result = prime * result
				+ (int) (conceptionTime ^ (conceptionTime >>> 32));
		result = prime * result
				+ ((consumerId == null) ? 0 : consumerId.hashCode());
		result = prime * result
				+ ((consumerName == null) ? 0 : consumerName.hashCode());
		result = prime * result
				+ ((consumerStatus == null) ? 0 : consumerStatus.hashCode());
		result = prime * result
				+ ((hostName == null) ? 0 : hostName.hashCode());
		result = prime * result + (int) (lastAlive ^ (lastAlive >>> 32));
		result = prime * result + (int) (publishTime ^ (publishTime >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		HeartbeatBean other = (HeartbeatBean) obj;
		if (beamline == null) {
			if (other.beamline != null)
				return false;
		} else if (!beamline.equals(other.beamline))
			return false;
		if (conceptionTime != other.conceptionTime)
			return false;
		if (consumerId == null) {
			if (other.consumerId != null)
				return false;
		} else if (!consumerId.equals(other.consumerId))
			return false;
		if (consumerName == null) {
			if (other.consumerName != null)
				return false;
		} else if (!consumerName.equals(other.consumerName))
			return false;
		if (consumerStatus != other.consumerStatus)
			return false;
		if (hostName == null) {
			if (other.hostName != null)
				return false;
		} else if (!hostName.equals(other.hostName))
			return false;
		if (lastAlive != other.lastAlive)
			return false;
		if (publishTime != other.publishTime)
			return false;
		return true;
	}

	public UUID getConsumerId() {
		return consumerId;
	}

	public void setConsumerId(UUID consumerId) {
		this.consumerId = consumerId;
	}

	@Override
	public String toString() {
		return "HeartbeatBean [beamline=" + beamline + ", publishTime="
				+ publishTime + ", conceptionTime=" + conceptionTime
				+ ", consumerId=" + consumerId + "]";
	}

	public String getConsumerName() {
		return consumerName;
	}

	public void setConsumerName(String consumerName) {
		this.consumerName = consumerName;
	}

	public long getLastAlive() {
		return lastAlive;
	}

	public void setLastAlive(long lastAlive) {
		this.lastAlive = lastAlive;
	}

	public ConsumerStatus getConsumerStatus() {
		return consumerStatus;
	}

	public void setConsumerStatus(ConsumerStatus consumerStatus) {
		this.consumerStatus = consumerStatus;
	}

	public String getHostName() {
		return hostName;
	}

	public void setHostName(String hostName) {
		this.hostName = hostName;
	}

}
