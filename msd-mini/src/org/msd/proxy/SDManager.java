/** This is the mini version */
package org.msd.proxy;

import java.util.Vector;
import org.msd.cache.*;
import java.util.Enumeration;

/** Generic manager. It performs a lot of common issues to all managers.
 * @version $Revision: 1.5 $ */
public abstract class SDManager implements TimeListener{
    /** Class' logger */
    /** listeners collection */
    private Vector listeners;
    /** Name of this network. Used just for logging */

    // PROPERTIES ACCESSIBLES FROM THE CLASSES EXTENDING THIS
    /** if the manager working? was normally started? */
    boolean working;
    /** cache to save the found services */
    Cache cache;
    /** Elements created by this manager */
    Vector elements;
    /** flag saying if the manager is busy performing a search */
    boolean busy;
    /** Object used to be notified when a searching is over. */
    Object searching=new Object();
    /** Netowrk to join to the serices discovered by this manager */
    Network network;

    /** Empty Constructor.
     * This class needs an empty constructor if it is loaded from
     * Class.newInstace (for example, throught a ClassLoader). Anyway, you
     * must avoid using any method before configure the manager with init(). */
    public SDManager(){}

    /*Initializes the manager. Called inside the constructor, you must call
     * this method yourself only if you creates the class other way that
     * the 'new' method (i.e. ClassLoader).
     * @param name Human readable name of the manager.
     * @param cache Cache used by the manager. */
    public void init(Network net, Cache cache) throws Exception{
        network=net;
        this.cache=cache;
        // construct the proxy
        listeners=new Vector();
        elements=new Vector();
    }

    /** Init again the SDManager for the given network.
     * By default, this method does nothing.
     * @param net A new network to perform searching of services.
     * @throws java.lang.Exception If any error occurs
     */
    public void reinit(Network net) throws Exception{

    }

    /** Performs a search in the manager protocol.
     *
     * The classes extending this one must call searchStarted() at the
     * very begining and searchCompleted() at the end.
     *
     * This method should be synchronized and returns inmediately. In order to
     * know when a search has been completed, call to isBusy() or use a
     * listener. */
    public abstract void search() throws Exception;

    /** Say if a searching is taking place. */
    public boolean isBusy(){ return busy; }

    /** Say if the manager started normally after init() method*/
    public boolean isWorking(){ return working; }

    /** Returns the cache used by this proxy */
    public Cache getCache() { return cache; }

    /** Get the name of the proxy */
    public String getName(){ return network.getName(); }

    /** Cleans the cache removing the services this SDManager found. */
    public void clean() throws Exception{
        // note we can not call to this.deleteElement with iterators
        for(Enumeration e=elements.elements(); e.hasMoreElements();)
            cache.deleteElement((Element)e.nextElement());
        elements.removeAllElements();
    }

    /** Delete an element from the manager and cache */
    public void deleteElement(Element e){
        cache.deleteElement(e);
        elements.removeElement(e);
    }

    /** Returns an integer unique for each protocol. each class extending
     * this one should have a different type (one type for protocol) */
    public abstract int getType();

    /** Method to being called at the very first moment of the searching.
     * Informs to the listeners about a new search. */
    void searchStarted() throws Exception{
        if(busy) throw new Exception("We are busy by now");
        System.out.println("Search started for "+getName());
        clean();
        busy=true;
        // avisamos a los escuchadores
        for (Enumeration e=listeners.elements(); e.hasMoreElements();){
            SDListener l=(SDListener)e.nextElement();
            l.searchStarted(new SDEvent(this,SDListener.STARTED,""));
        }
    }

    /** Method to being called at the very end of the searching.
     * Informs to the listeners about a search completed.
     * @param code Search return code. See SDEvent.
     * @param msg Message to send to the listeners. Use it in case of error, it is
     * undeterminated in case of search completed.*/
    void searchCompleted(int code, String msg){
        System.out.println("Search completed for "+getName());
        for (Enumeration e=listeners.elements();e.hasMoreElements();){
            SDListener l=(SDListener)e.nextElement();
            l.searchCompleted(new SDEvent(this,code,msg));
        }

        // and we are not busy
        busy=false;
        synchronized(searching){searching.notify();}
    }

    /** As searchCompleted, but sending no messages to the listeners. */
    void searchCompleted(int code){ searchCompleted(code,""); }

    /** Add a listener to the manager */
    public void addListener(SDListener listener){
        listeners.addElement(listener);
    }

    /** Remove a listener from the list. */
    public void removeListener(SDListener listener){
        listeners.removeElement(listener);
    }

    /** Search for a template in the msd manager.
     *
     * This method waits for the searching to finish. It could be a rather long time
     * if the cached=false flag is used.
     * @param template Service to search. This proxy is appended to the
     * template.
     * @param cached Look up the cache or start a new searching. */
    public Vector searchService(Service template,boolean cached){
        if(cached){
            Service t=(Service)template.clone(cache);
            return cache.getElements(t,cache.getChilds());
        } else{
            try{
                search();
                synchronized(searching){
                    searching.wait();
                }
                return searchService(template,true);
            } catch(Exception e){
                return new Vector();
            }
        }
    }

    /** Return a description to the network this managers is searching */
    public Network getNetwork(){ return network; }

    /** Finish the manager.
     * Other callings after this method are unexpected.
     * Recalling finish is harmless. */
    public abstract void finish();

    /** Finish the manager for a given network.
     * By default, this method just call finish
     * @see #finish
     */
    public void finish(Network net){
        finish();
    }

    /** When a signal from a TimeManager is received, do a searching.
     * @param type Ignored
     * @param data Ignored
     * @return True, except if the manager is not working.
     */
    public boolean signal(int type,Object data){
        if(!working){
            return false;
        }
        try{
            this.search();
        }catch(Exception e){
        }
        return true;
    }
}
