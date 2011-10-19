package org.msd.comm;

import java.util.EventObject;

/** The event of a connection */
public class ConnectionEvent extends EventObject{
    /** A message has been received */
    public static final int RECEIVED=0;
    /** A message has been sent */
    public static final int SENT=1;
    /** Someone calls to the closeConnection method: it is closed and no longer
     * in use (an exception will be thrown if the user try to call to any
     * method after this event) */
    public static final int CLOSED=4;
    /** An error has arrived from the other extreme of the connection */
    public static final int ERROR=5;

    private Message m;
    private int type;
    /** Create a new EventConnection.
     * @param c The original connection which throws the event
     * @param m The message that caused the event. If the event
     * is closing, the message will be null.
     * @param type The type of the event.
     */
    public ConnectionEvent(Connection c,Message m,int type){
        super(c);
        this.type=type;
        this.m=m;
    }

    /** @return The message that caused the event. If the event is
     * closing, the message is null. */
    public Message getMessage(){
        return m;
    }

    /** @return The type of event */
    public int getType(){
        return type;
    }
}
