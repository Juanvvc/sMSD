package org.msd.proxy;

/** A listener for the MSD events.
 *
 * @version $Revision: 1.2 $
 */
public interface MSDListener {
    /** Inform of an event.
     * @param e The event triggered by the MSD */
    public void event(MSDEvent e);
}
