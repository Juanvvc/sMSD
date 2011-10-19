package org.msd.proxy;

import org.msd.cache.*;
import org.msd.comm.*;
import java.util.*;

/** This class implements the MSDManager layer in the protocol stack of the MSD.
 * Applications using the MSD as a library will probably use only this class,
 * and its events.
 *
 * @version $Revision: 1.11 $
 */
public abstract class MSDManager extends SDManager{
    /** MSD event listeners */
    Vector msdlisteners=new Vector();
    /** NetworkManagers Hashtable
     * The key is the generic name of the network.
     * The value is the NetworkManager started with this network */
    protected Hashtable nets;
    /** Router of the system */
    protected RouterManager router=null;
    /** This manager descriptor */
    protected Service msd=null;
    /** CommManager to use */
    protected CommManager comm;

    /** @return An integer that identify this as an MSD Proxy*/
    public int getType(){
        return 3;
    }

    /** This methos does nothing: use searchService() instead.
     * @see #searchService */
    public void search(){
    }

    /** Use a service.
     * @param serv Description of the service to use
     * @return A couple of streams to use the service.
     * @throws Exception If an error occurs
     */
    public ConnectionStreams useService(Service serv) throws Exception{
        return TransformConnection.connectionToStreams(useConnected(serv));
    }

    /** Use a service.
     * @param serv Description of the service to use
     * @return A connection to the service
     * @throws java.lang.Exception If an error occurs.
     */
    public Connection useConnected(Service serv) throws Exception{
        System.out.println("Using service "+serv.getID()+" in "+
                           serv.getIDCache());
        // get the closer MSD
        String idcacheC=serv.getIDCache();
        String idC=serv.getID();
        if(idcacheC==null||idC==null){
            throw new Exception("No MSD defined");
        }

        // connect to the closer MSD
        Connection c=router.openConnection(Connection.USE,getID(),idcacheC);
        Message m=new Message(serv.toString().getBytes(),getID(),idcacheC,
                              Message.USE);
        c.send(m);
        // wait for confirmation
        m=c.receive();
        if(m.getType()==Message.ERROR){
            c.close();
            throw new Exception("Error connecting to service: "+
                                new String(m.getData()));
        }
        System.out.println("Service ready to be used");
        // return the connection
        return c;
    }

    /** Get the service describing this msd */
    public Service getMSD(){
        return msd;
    }

    /** Get the identifier of this MSD */
    public String getID(){
        return msd.getIDCache();
    }

    /** Send a message to other MSD */
    public void send(Message m) throws Exception{
        router.route(m,null);
    }

    /** Receive a connection where receive and send messages */
    public abstract void receive(Connection c,NetworkManager net) throws Exception;

    /** Receive a message from a network manager.
     * @return A message as a response, or null if no response
     * is needed */
    public abstract void receive(Message m,NetworkManager net) throws Exception;

    /** Add a new listener for this class.
     * Keep in mind SDListeners and MSDListeners are different.
     * This class uses both. */
    public void addMSDListener(MSDListener l){
        msdlisteners.addElement(l);
    }

    /** Remove a listener to this class events */
    public void removeMSDListener(MSDListener l){
        msdlisteners.removeElement(l);
    }

    /** Get a service MSD from its identifier */
    public Service getMSD(String id){
        try{
            Service msd=new Service(cache,false);
            msd.setName("MSD");
            msd.setIDCache(id);
            return(Service)cache.getElements(msd,cache.getChilds()).elementAt(0);
        } catch(Exception e){
            return null;
        }
    }

    /** Get a hashtable of NetworkManagers being the key the generic name
     * of the network. */
    public Hashtable getNetworks(){
        return nets;
    }

    /** Get the router of this system */
    public RouterManager getRouter(){
        return router;
    }

    /** Get a connection to a remote manager */
    public Connection getConnection(int type,String to) throws Exception{
        return router.openConnection(type,cache.getID(),to);
    }

    /** Remove the network from the list */
    public void removeNetwork(NetworkManager net){
        nets.remove(net.getGenericName());
        try{
            getMSD().deleteChild(net.getNetwork());
        } catch(Exception e){
        }
    }
    
    /** @param type Trigger an event of this type...
     * @param net ... occured at this network generic name
     */
    protected void triggerEvent(int type,String net){
        synchronized(msdlisteners){
            MSDEvent e=new MSDEvent(this,type,net);
            for(Enumeration en=msdlisteners.elements();en.hasMoreElements();){
                MSDListener l=(MSDListener)en.nextElement();
                l.event(e);
            }
        }
    }

    /** Finishes this manager.
     * Finishes the NetworkManagers started by the MSD */
    public void finish(){
        if(!working)
            return;
        System.out.println("Finishing MSDManager");
        Message m=new Message(getID().getBytes(), getID(), null,  Message.LEFT);
        for(Enumeration e=nets.elements();e.hasMoreElements();){
            NetworkManager n=(NetworkManager)e.nextElement();
            try{
                n.sendM(m);
            }catch(Exception ex){
                // after an error, try to send the message just to the
                // main MSD of the network
                try{
                    n.sendU(m,n.getMSDMain().getNetwork(
                        n.getGenericName()).getAddress());
                }catch(Exception ex2){
                    System.err.println("Error while finishing: "+ex2);
                }
            }
            n.finish();
        }
        working=false;
    }

    /** Event: an SDManager starts its searching */
    public void searchStarted(SDEvent e){
    }

    /** Event: an SDmanager completes its searching */
    public void searchCompleted(SDEvent e){
    }

    /** Return a collection with the networks bridged.
     * @param net Generic name of a network. If null, return every network
     * this MSD connects (do not care about bridges!)
     * @return A collection with the other NetworkManager extreme of the
     * bridge, or an empty Vector in the network is not bridged */
    public Vector getNetworksBridged(String net){
        return new Vector();
    }
    
    /** Search for a template in the manager.
     *
     * This method does a browse or search directly inside the cache.
     * It could be a rather long tim if the cached=false flag is used.
     * @todo Performing the searching in every network manager registered.
     * @param template Service to search.
     * @param cached Look up the services cached or start a new searching. */
    public Vector searchService(Service template,boolean cached){
        System.out.println("Searching for service "+template);
        if(cached){
            return cache.getElements(template);
        }else{
            try{
                // do the searching just in the first network manager registered
                NetworkManager net=(NetworkManager)nets.elements().nextElement();
                Service msdMain=net.getMSDMain();
                Connection c=net.getConnection(Connection.GET,getID(),
                        msdMain.getIDCache(),
                        msdMain.getNetwork(net.getGenericName()).getAddress());
                Message m=new Message(template.toString().getBytes(),getID(),
                        msdMain.getIDCache(),Connection.GET);
                c.send(m);
                m=c.receive();
                c.close();
                // create a cache
                Cache cc=new Cache();
                cc.load(new String(m.getData()));
                // set the gateway of services
                for(Enumeration e=cc.getChilds().elements(); e.hasMoreElements();){
                    ((Service)(e.nextElement())).setGateway(cc.getID());
                }
                // set the gw of the cache
                cache.join(cc);
                // return the elements of the cache
                return cc.getChilds();
            } catch(Exception e){
                System.err.println("Error while searching service: "+e); 
                e.printStackTrace();
                return searchService(template,true);
            }
        }
    }
    
    /** Starts a main searching in a network.
     * @param net The network to start the searching. */
    void doMainRequest(NetworkManager net){
        try{
            System.out.println("Looking for main on "+net.getGenericName());
            Message m=new Message(msd.toString().getBytes(),msd.getIDCache(),null,
                                  Message.MAIN_REQUEST);
            send(m);
        } catch(Exception e){
        }
    }
}
