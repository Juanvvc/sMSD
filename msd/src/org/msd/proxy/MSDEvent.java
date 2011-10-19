/*
 * MSDEvent.java
 *
 * Created on 25 de enero de 2005, 18:10
 */

package org.msd.proxy;


/** An event for the events from the MSDManager.
 *
 * @date $Date: 2005-05-17 11:58:54 $
 * @version $Revision: 1.5 $
 */
public class MSDEvent extends java.util.EventObject{
    /** Main MSD URL */
    private String url=null;
    /** Main MSD port */
    private int port=-1;
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

    /** @param source Source of the event.
     * @param type Type of the event
     * @param network Generic name of the network responsible of this event */
    public MSDEvent(MSDManager source,int type,String network){
        super(source);
        this.type=type;
        this.network=network;
    }

    /** @return The network that triggers this event */
    public String getNetwork(){ return network; }
    /** @return The type of this event */
    public int getType(){ return type; }
}

