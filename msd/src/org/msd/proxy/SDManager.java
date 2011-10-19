package org.msd.proxy;

import java.util.Vector;
import org.apache.log4j.Logger; //@@l
import org.apache.log4j.Level; //@@l
import java.util.Iterator;
import java.util.Collection;
import org.msd.cache.*;

/**
 * Generic proxy manager. It performs a lot of common issues to all managers.
 *
 * A proxy manager performs searching of services in a number of initilized
 * networks. Implementations for each protocol must be provided.
 *
 * The main use of this class is for periodically searching of new services,
 * listening to the events (search started or fininshed) with an SDListener.
 * You can always look for a service template inmediatly.
 * @version $Revision: 1.16 $
 */
public abstract class SDManager implements TimeListener{
    /** Class' logger */
    private static Logger logger=Logger.getLogger(SDManager.class); //@@l
    /**
     * The level of logging of the SDManagers.
     * Use constants from org.apache.log4j.Level
     */
    protected static Level LEVEL=Level.WARN; //@@l
    /** listeners collection */
//    private Collection<SDListener> listeners;     //@@1.5
    private Collection listeners; //@@1.4
    /** Name of this network. Used just for logging */
    private String name; //@@l

    // PROPERTIES ACCESSIBLES FROM THE CLASSES EXTENDING THIS
    /** if the manager working? was normally started? */
    protected boolean working;
    /** cache to save the found services */
    protected Cache cache;
    /** references the proxy inside the cache */
    protected Proxy proxy;
    /** Elements created by this manager */
//    Collection<Element> elements;  //@@1.5
    protected Collection elements; //@@1.4
    /** flag saying if the manager is busy performing a search */
    protected boolean busy;
    /** Object used to be notified when a searching is over. */
    protected Object searching=new Object();
    /** Network to join to the services this manager discovers */
    protected Network network;

    /** Empty Constructor.
     * This class needs an empty constructor if it is loaded from
     * Class.newInstace (for example, throught a ClassLoader). Anyway, you
     * must avoid using any method before configure the manager with init(). */
    public SDManager(){}

    /**
     * Initializes the manager for a network.
     *
     * You must initilize the SDManager for each network it has to listen to for
     * service descriptions. For example, in a device with two ethernet interfaces
     * you must initilize the SDManager to listen to SLP descriptions in both
     * interfaces.
     * @param cache Cache used by the manager.
     * @param resource A resource name for reading this manager properties. If
     * the manager has no properties, ignores this parameter.
     * @throws Exception If the SDManager can not be started. The Exception
     * can be thrown at any moment of the init process, so remember calling
     * finish() method to ensure it frees the resources used.
     * @param net Initializes the manager for this network
     */
    public void init(Network net,Cache cache,String resource) throws Exception{
        network=net;
        this.cache=cache;
        // construct the proxy
        proxy=new Proxy(cache,false);
        proxy.setName(getName());
        proxy.setSDType(getType());
//        listeners=new Vector<SDListener>();       //@@1.5
        listeners=new Vector(); //@@1.4
//        elements=new Vector<Element>();           //@@1.5
        elements=new Vector(); //@@1.4
        name=getName(); //@@l
    }

    /** Init again the SDManager for the given network.
     * By default, this method does nothing.
     * @param net A new network to perform searching of services.
     * @throws java.lang.Exception If any error occurs
     */
    public void reinit(Network net) throws Exception{

    }

    /**
     * Performs a searching for services offered in every networok initialized
     * in the manager protocol.
     *
     * This method should returns inmediately. In order to know when a search
     * has been completed, call to isBusy() or use a listener.
     * @remarks The classes extending this one must call searchStarted() at the
     * very begining and searchCompleted() at the end.
     * @throws java.lang.Exception If the searching can not be started
     */
    public abstract void search() throws Exception;

    /** Performs a searching only in a network started.
     *
     * This methos just call to search. Overwrite to do something different.
     * @param net The network to be searched.
     * @throws Exception If the searching can not be started.
     */
    public void search(Network net) throws Exception{
        search();
    }

    /**
     * This methods informs if the SDManager is busy performing a service search,
     * @return If the SDManager is busy
     */
    public boolean isBusy(){
        return busy;
    }

    /**
     * Tells if the SDManager is working (it has been initialized for at least one
     * network)
     * @return If the SDManager os working
     */
    public boolean isWorking(){
        return working;
    }

    /**
     *
     * @return The cache used by this proxy
     */
    public Cache getCache(){
        return cache;
    }

    /**
     *
     * @return The human readable name of this proxy
     */
    public String getName(){
        return network.getName();
    }

    /**
     * Cleans the cache removing the services this SDManager found.
     */
    public void clean() throws Exception{
        // note we can not call to this.deleteElement with iterators
//        for(Element e: elements) cache.deleteElement(e); //@@1.5
        for(Iterator i=elements.iterator();i.hasNext();){ //@@1.4
            cache.deleteElement((Element)i.next()); //@@1.4
        }
        elements.clear();
    }

    /**
     * Deletes an element from the cache and the internal list of elements
     * found by this proxy.
     * @param e The element to be removed
     * @todo Is this method useful for anything?
     */
    public void deleteElement(Element e){
        cache.deleteElement(e);
        elements.remove(e);
    }

    /**
     * Returns an integer unique for each protocol. Each class extending
     * this one should have a different type (one type for protocol)
     * @return The unique identifier of the proxy.
     */
    public abstract int getType();

    /** Method to being called at the very first moment of the searching.
     * Informs to the listeners about a new search. */
    protected void searchStarted() throws Exception{
        if(!working){
            logger.warn("Manager doesn't seem to be working: " //@@l
                        +name); //@@l
        }
        if(busy){
            throw new Exception("We are busy by now");
        }
        logger.info("Search started: "+name); //@@l
        clean();
        busy=true;
        // avisamos a los escuchadores
//        for(SDListener l: listeners)                                   //@@1.5
//            l.searchStarted(new SDEvent(this,SDListener.STARTED,""));  //@@1.5
        for(Iterator i=listeners.iterator();i.hasNext();){ //@@1.4
            SDListener l=(SDListener)i.next(); //@@1.4
            l.searchStarted(new SDEvent(this,SDListener.STARTED,"")); //@@1.4
        } //@@1.4
    }

    /** Method to being called at the very end of the searching.
     * Informs to the listeners about a search completed.
     * @param code Search return code. See SDEvent.
     * @param msg Message to send to the listeners. Use it in case of error, it is
     * undeterminated in case of search completed.*/
    protected void searchCompleted(int code,String msg){
        if(!busy){
            logger.warn("Event completed while no searching registered."); //@@l
        }
//        for(SDListener l: listeners)                              //@@1.5
//            l.searchCompleted(new SDEvent(this,code,msg));        //@@1.5
        for(Iterator i=listeners.iterator();i.hasNext();){ //@@1.4
            SDListener l=(SDListener)i.next(); //@@1.4
            l.searchCompleted(new SDEvent(this,code,msg)); //@@1.4
        } //@@1.4

        // and we are not busy
        busy=false;
        synchronized(searching){
            searching.notify();
        }
        switch(code){ //@@l
        case SDListener.COMPLETED:
            logger.info("Search completed: "+name);
            return; //@@l
        case SDListener.CANCELED:
            logger.info("Search canceled: "+name);
            return; //@@l
        case SDListener.ERROR:
            logger.warn("Search interrupted with errors: "+name);
            return; //@@l
        default:
            logger.warn("Search state unknown ("+code+"): "+name); //@@l
        } //@@l
    }

    /** As searchCompleted, but sending no messages to the listeners. */
    protected void searchCompleted(int code){
        searchCompleted(code,"");
    }

    /**
     * Adds a listener to the manager
     * @param listener A new listener for this manager
     */
    public void addListener(SDListener listener){
        listeners.add(listener);
    }

    /**
     * Removes a listener from the list.
     * @param listener The listener to be removed. Do nothing of the listener was
     * not added or yet removed.
     */
    public void removeListener(SDListener listener){
        listeners.remove(listener);
    }

    /**
     * Searchs for a template in the msd manager.
     *
     * This method blocks until the searching is complete.
     * it can be a rather long time of cached=false.
     * @param template Service to search. This proxy is appended to the
     * template.
     * @param cached Look up the cache or start a new searching.
     * @return A collection with the found services matching the template. It
     * could be empty if no service is found.
     */
    public Collection searchService(Service template,boolean cached){
        if(cached){
            Service t=(Service)template.clone(cache);
            t.appendChild(proxy);
            return cache.getElements(t,cache.getChilds());
        } else{
            try{
                search();
                synchronized(searching){
                    searching.wait();
                }
                return searchService(template,true);
            } catch(Exception e){
                logger.warn("Error in searching: "+e); //@@l
                return new Vector();
            }
        }
    }

    /** Finish the manager.
     * Other callings after this method are unexpected.
     * Recalling finish is harmless. */
    public abstract void finish();

    /**
     * Finish the manager for a given network.
     * By default, this method just call finish
     * @see #finish
     * @param net The network to finish the SDManager
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
        } catch(Exception e){
            logger.warn("Error while searching: "+e); //@@l
        }
        return true;
    }

    /** @return A collection with the services registered with this SDManager */
    public Collection getElements(){
        return elements;
    }
}
