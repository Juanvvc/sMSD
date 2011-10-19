package org.msd.proxy;

/** A listener for the MSD events.
 *
 * @date $Date: 2005-04-05 17:50:16 $
 * @version $Revision: 1.5 $
 */
public interface MSDListener {
    /** Inform of an event.
     * @param e The event triggered by the MSD */
    public void event(MSDEvent e);
}
