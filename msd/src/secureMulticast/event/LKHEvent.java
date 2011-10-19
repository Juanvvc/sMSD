package secureMulticast.event;

import javax.crypto.*;
import java.util.*;

import org.msd.comm.NetworkManager;

/**
 * <p> This class makes a prototype of the events that can happen and can be processed in the package.
 *
 * @author  Oscar Burgos & Daniel Jarne
 * @version 1.1, 01/10/04
 */
public class LKHEvent extends EventObject
{
	////////////////////////////////////////////////////////////////////////////
	//////// Static and basic LKHEvent fields //////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Defines an int value for a joining event.
	 */
	public static final int JOINING = 1;

	/**
	 * Defines an int value for a leaving event.
	 */
	public static final int LEAVING = 2;

	/**
	 * Defines an int value for a leaving event.
	 */
	public static final int TREE_UPDATED = 3;

	/**
	 * Defines an int value for a leaving event.
	 */
	public static final int DISCARD_NODE = 4;

	/**
	 * Defines an int value for a newly generated SEK event.
	 */
	public static final int NEW_SEK = 5;

	/**
	 * Defines an int value for a member in a dummy state event (not updating the KEKs and SEK).
	 */
	public static final int DUMMY_STATE = 6;

	/**
	 * Defines an int value for a member out of the secure multicast group.
	 */
	public static final int OFF_STATE = 7;

	/**
	 * Defines an int value for the group address reception event.
	 */
	public static final int GROUP_ADDR = 6;

	/**
	 * Defines an int value for the group port reception event.
	 */
	public static final int GROUP_PRT = 7;

	/**
	 * Specifies the kind of event to be reported.
	 */
	private int event;

	/**
	 * Specifies the value to report associated to the event that has just happened.
	 */
	private Object value;

        private NetworkManager net=null;

	////////////////////////////////////////////////////////////////////////////
	//////// Class constructors ////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructs a LKH event object to be triggered.
	 *
	 * @param source the source object on which the event initially happened.
	 * @param event an int value describing the event.
	 * @param value the new value needed to process the event in the listener.
	 */
	public LKHEvent(Object source, int event, Object value,NetworkManager net)
	{
		super(source);
		this.event = event;
		this.value = value;
                this.net=net;
	}

	////////////////////////////////////////////////////////////////////////////
	//////// Class methods /////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Returns an int value describing the event that has happened.
	 *
	 * @return the int value which describes the event.
	 */
	public int getEvent()
	{
		return event;
	}

	/**
	 * Returns the newly generated SEK.
	 *
	 * @return the new symmetric key needed to encrypt the session data.
	 */
	public SecretKey getNewSEK()
	{
		return (SecretKey) value;
	}

	/**
	 * Returns the newly received value (port or address).
	 *
	 * @return the new received value, port or address.
	 */
	public String getStringValue()
	{
		return (String) value;
	}

	/**
	 * Returns the newly received generic object.
	 *
	 * @return the new received generic object.
	 */
	public Object getValue()
	{
		return value;
	}

        /** Returns the NetworkManager that triggered this event */
        public NetworkManager getNetwork(){
            return net;
        }
}
