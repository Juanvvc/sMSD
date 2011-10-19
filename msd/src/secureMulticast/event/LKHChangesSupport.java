package secureMulticast.event;

import secureMulticast.event.LKHListener;
import secureMulticast.event.LKHEvent;

import java.util.*;

import org.msd.comm.NetworkManager;

/**
 * <p> This class gives support to the LKH listeners and events. It is responsible of registering the listeners
 * and triggering the events in case they happen.
 *
 *
 * @author  Oscar Burgos & Daniel Jarne
 * @version 1.1, 01/10/04
 */
public class LKHChangesSupport
{
	////////////////////////////////////////////////////////////////////////////
	//////// LKHChangesSupport fields //////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Specifies the container vector where the listeners are stored.
	 */
	transient private Vector listeners;

	/**
	 * Specifies the source object that has triggered the event.
	 */
	private Object source;

        private NetworkManager net;

	////////////////////////////////////////////////////////////////////////////
	//////// Class constructors ////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructs a LKH changes support object specifying which will be the source of the triggered events.
	 *
	 * @param source the source class that will trigger the events.
	 * @throws java.lang.NullPointerException a null pointer exception if the source object is a null object.
	 */
	public LKHChangesSupport(Object source,NetworkManager net)
	{
		if (source == null)
		{
			throw new NullPointerException();
		}
		this.source = source;
                this.net=net;
	}

	////////////////////////////////////////////////////////////////////////////
	//////// Class methods /////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Register the specified listener to notification in case of an event happens.
	 *
	 * @param listener the listener object that must be added to the listeners vector.
	 */
	public synchronized void addLKHListener(LKHListener listener)
	{
		if (listeners == null)
		{
			listeners = new Vector();
		}
		listeners.addElement(listener);
	}

	/**
	 * Removes the specified listener from the listeners vector.
	 *
	 * @param listener the listener object to be removed.
	 */
	public synchronized void removeLKHListener(LKHListener listener)
	{
		if (listeners == null)
		{
			return;
		}
		listeners.removeElement(listener);
	}

	/**
	 * Returns an array of the LKH listeners registered in the LKH changes support class.
	 *
	 * @return an array conatining all the registered listeners.
	 */
	public synchronized LKHListener[] getLKHListeners()
	{
		List returnList = new ArrayList();

		if (listeners != null)
			returnList.addAll(listeners);

		return (LKHListener[]) returnList.toArray(new LKHListener[0]);
	}

	/**
	 * Triggers a LKH event and notifies all the registered listeners that an event has happened.
	 *
	 * @param ev an int value specifying the kind of event that has happened.
	 * @param newValue a new value to be used according to the event that has happened.
	 */
	public void triggerLKHChange(int ev, Object newValue)
	{
		if (newValue == null)
			return;

		Vector targets = null;
		synchronized (this)
		{
			if (listeners != null)
				targets = (Vector) listeners.clone();

		}

		LKHEvent event = new LKHEvent(source, ev, newValue,net);

		if (targets != null)
		{
			for (int i = 0; i < targets.size(); i++)
			{
				LKHListener target = (LKHListener) targets.elementAt(i);
				target.LKHEventPerformed(event);
			}
		}
	}
}
