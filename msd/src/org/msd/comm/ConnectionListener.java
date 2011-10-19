package org.msd.comm;

/** Listeners of connections.
 * @see ConnectionEvent.
 * @version $Revision: 1.2 $ */
public interface ConnectionListener{
    /** The connection throws an event.
     * @param e The event.
     */
    public void event(ConnectionEvent e);
}
