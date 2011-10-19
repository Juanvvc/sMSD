/*
 * MSDEvent.java
 *
 * Created on 25 de enero de 2005, 18:10
 */

package org.msd.proxy;

/** The description of an event in the MSDManager layer of the system.
 *
 * @version $Revision: 1.3 $
 */
public class MSDEvent{
    /** Message type */
    private int type=UNKNOWN;
    /** Network: internet or bluetooth*/
    private String network=null;
    /** A level changed */
    public static final int LEVEL=0;
    /** Unknown event */
    public static final int UNKNOWN=1;
    /** A cache has been updated */
    public static final int UPDATED=2;
    /** The MSDManager that throws this event */
    private MSDManager manager;

    /** @param source Source of the event.
     * @param type Type of the event
     * @param network Generic name of the network responsible of this event */
    public MSDEvent(MSDManager source,int type,String network){
	this.manager=source;
        this.type=type;
        this.network=network;
    }

    /** @return The MSDManager that throwed this event */
    public MSDManager getManager(){
	    return manager;
    }

    /** @return The network that triggers this event */
    public String getNetwork(){ return network; }
    /** @return The type of this event */
    public int getType(){ return type; }
}

