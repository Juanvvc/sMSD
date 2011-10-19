package org.msd.proxy;

/**
 * A listener for SDManagers.
 * 
 * The SDManager informs to its listeners when a new searching is started,
 * finished or canceled.
 * @version $Revision: 1.3 $
 */
public interface SDListener {
    /**
     * A new searching is started.
     */
    public static final int STARTED=0;
    /**
     * The searching finishes without errors.
     */
    public static final int COMPLETED=1;
    /**
     * The searching failed.
     */
    public static final int ERROR=2;
    /**
     * The searching has been canceled by someone.
     */
    public static final int CANCELED=3;
    /**
     * Method called when a services search have been fisished.
     * Manager remains busy while all its listeners are called,
     * but its services are already ready to be browsed.
     * @param e Event thrown to the listener.
     */
    public void searchCompleted(SDEvent e);
    /** Method called when a search is started. Manager is already busy.
     * @param e Event thrown to the listener. */
    public void searchStarted(SDEvent e);
}
