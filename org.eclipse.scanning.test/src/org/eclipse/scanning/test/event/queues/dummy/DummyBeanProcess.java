package org.eclipse.scanning.test.event.queues.dummy;

import org.eclipse.scanning.api.event.EventException;
import org.eclipse.scanning.api.event.core.IPublisher;
import org.eclipse.scanning.api.event.queues.beans.Queueable;

public class DummyBeanProcess<T extends Queueable> extends DummyProcess<DummyBean, T> {
	
	public DummyBeanProcess(T bean, IPublisher<T> publisher) throws EventException {
		super(bean, publisher, false);
	}

	@Override
	public Class<DummyBean> getBeanClass() {
		return DummyBean.class;
	}

}
