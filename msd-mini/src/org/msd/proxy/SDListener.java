package org.msd.proxy;

/** Services managers listeners.
 * @version $Revision: 1.1.1.1 $ */
public interface SDListener {
    /** search started */
    public static final int STARTED=0;
    /** search finished successfully */
    public static final int COMPLETED=1;
    /** search failed (starting or finishing) */
    public static final int ERROR=2;
    /** search canceled (by user) */
    public static final int CANCELED=3;
    /** Method called when a services search have been fisished.
     * Manager remains busy while all its listeners are called,
     * but its services body is already success.
     * @param e Event thrown to the listener. */
    public void searchCompleted(SDEvent e);
    /** Method called when a search is started. Manager is already busy.
     * @param e Event thrown to the listener. */
    public void searchStarted(SDEvent e);
}
