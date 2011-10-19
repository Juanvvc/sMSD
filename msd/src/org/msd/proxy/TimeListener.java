package org.msd.proxy;

/** Listeners for a time event. */
public interface TimeListener{
    /** Get a signal from the timer.
     * This method must be brief and return inmediatelly.
     * @param type The type of the signal.
     * @param data The data of the signal.
     * @return If the listener has to be signaled again after the registered
     * time (for loops)
     */
    public boolean signal(int type,Object data);
}
