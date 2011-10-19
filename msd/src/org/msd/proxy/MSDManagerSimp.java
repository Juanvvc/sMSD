package org.msd.proxy;

import org.msd.cache.*;
import org.msd.comm.*;
import org.apache.log4j.Logger; //@@l
import java.util.*;
import java.lang.Class;

/** A simplified MSDManager to let easier debugging.
 *
 * @version $Revision: 1.9 $
 */
public class MSDManagerSimp extends org.msd.proxy.MSDManager{
    private static Logger logger=Logger.getLogger(MSDManagerSimp.class); //@@l
    /** NetworkManagers Hashtable
     * The key is the name of the network: bluetooth, internet
     * The value is the NetworkManager started with this network */
    private Hashtable nets;
    /** Router of the system */
    private RouterManager router=null;
    /** This manager descriptor */
    private Service msd=null;
    /** ResurceBundle to read the configuration from */
    private ResourceBundle res;
    private CommManager comm;

    /** Empty Constructor.
     * This class needs an empty constructor if it is loaded from
     * Class.newInstace (for example, throught a ClassLoader). Anyway, you
     * must avoid using any method before configure the manager with init(). */
    public MSDManagerSimp(){}

    /** Returns an integer that identify this as an MSD Proxy*/
    public int getType(){
        return 3;
    }

    /** Initializes this manager */
    public void init(String name,Cache cache,String resource) throws Exception{
        this.cache=cache;
        Network netMSD=new Network(cache,false);
        netMSD.setName("msd");

        nets=new Hashtable();
        router=new RouterManager(this);
        comm=new CommManager();

        // look in the cache and take the first MSD with the identifier
        // of the cache.
        Service emp=new Service(cache,false);
        emp.setName("MSD");
        msd=(Service)cache.getElements(emp,cache.getChilds()).iterator().next();

        // Read some properties from the conf file.
        res=ResourceBundle.getBundle(resource);

        // browse childs and get the networks of the MSD
        Object o[]=msd.getNetworks().toArray();
        for(int i=0;i<o.length;i++){
            Network net=(Network)o[i];
            String network=net.getName();

            ClassLoader cl=getClass().getClassLoader();
            if(network.equals("bluetooth")){
                String url=net.getURL();

                try{
                    BluetoothNetworkManager nm=new BluetoothNetworkManager();
                    String uuid=res.getString("MSD.UUID");
                    Address a=new Address(uuid,-1,network);
                    nm.init(network,a,null,this,comm);
                    nets.put(network,nm);
                    if(net.isMain()){
                        nm.setMain(true);
                    }
                    logger.info("BluetoothNetworkManager started"); //@@l
                } catch(Throwable e){
                    logger.warn("BluetoothNetworkManager not started: "+e); //@@l
                    e.printStackTrace(); //@@l
                    // remove the network from the cache
                    try{
                        cache.deleteElement(net);
                    } catch(Exception ex){
                        logger.error("Error while removing network: "+ex); //@@l
                    }
                }
            } else if(network.equals("ethernet")||
                      network.equals("wifi")){
                String url=net.getURL();
                int port=Integer.valueOf(net.getAttrStr("port")).intValue();

                try{
                    InternetNetworkManager nm=new InternetNetworkManager();
                    String urlMult=res.getString("MSD."+network+
                                                 ".MulticastURL");
                    int portMult=Integer.valueOf(res.getString("MSD."+network+
                            ".MulticastPort")).intValue();
                    Address multicast=new Address(urlMult,portMult,network);
                    nm.init(network,net.getAddress(),multicast,this,comm);
                    nm.setMain(net.isMain());
                    nets.put(network,nm);
                    logger.info("InternetNetworkManager started"); //@@l
                } catch(Throwable e){
                    logger.warn("InternetNetworkManager not started: "+e); //@@l
                    e.printStackTrace(); //@@l
                    // remove the network from the cache
                    try{
                        cache.deleteElement(net);
                    } catch(Exception ex){
                        logger.error("Error while removing network: "+ex); //@@l
                    }
                }
            } else if(network.equals("bridge")){
                // remove the bridge if any network bridged was not started
                Object o2[]=net.getChilds().toArray();
                boolean anyDisconnected=false;
                for(int j=0;j<o2.length;j++){
                    Element e=(Element)o2[j];
                    if(e.getType()==Element.NETWORK){
                        if(nets.get(e.getName())==null){
                            anyDisconnected=true;
                            break;
                        }
                    }
                }
                if(anyDisconnected){
                    msd.deleteChild(net);
                    logger.info("Bridge deregistered"); //@@l
                } else{
                    logger.info("Bridge registered"); //@@l
                }
            } else{
                logger.warn("Unknown net: "+network); //@@l
            }
        }

        // start the network managers
        // if any is main:
        //     - call to startManagers(network)
        //     - send I_AM_HERE messages every certain time
        // if not:
        //     - send EMP_REQUEST
        for(Iterator i=nets.values().iterator();i.hasNext();){
            NetworkManager net=(NetworkManager)i.next();
            if(net.isMain()){
                // Start proxies SDP or SLP
                logger.debug("Starting proxies..."); //@@l
                startManagers(net.getGenericName());
                // Start thread of I_AM_HERE messages
            } else{
            }
        }

        working=true;
    }

    /** Starts the managers for a given network.
     * Use this method when a network has been made main: its associated
     * managers will start.
     * @param network Generic name of the new main network. */
    public void startManagers(String network){
        logger.info("Starting SDManagers for "+network); //@@l
        String managers=res.getString("MSD."+network+".names");
        StringTokenizer st=new StringTokenizer(managers,",",false);
        ClassLoader cl=getClass().getClassLoader();
        Collection managerCol=new Vector();
        while(st.hasMoreTokens()){
            String managername=st.nextToken();
            String managerclass=res.getString("MSD."+network+"."+managername);
            String managerres=res.getString("MSD."+network+"."+managername+
                                            ".res");
            int time=Integer.valueOf(res.getString("MSD."+network+"."+
                    managername+".time")).intValue();
            try{
                Class c=cl.loadClass(managerclass);
                SDManager sd=(SDManager)c.newInstance();
                sd.init(((NetworkManager)getNetworks().get(network)).getNetwork(),
                        cache,managerres);
                sd.addListener(this);
                if(time>0){
                    TimeManager.getTimeManager().register(sd,time);
                }
                managerCol.add(sd);
                logger.info("SDManager "+managername+" started"); //@@l
            } catch(Exception e){
                logger.warn("SDManager couldn't start: "+e); //@@l
            }
        }
    }

    /** Stops the managers for a network.
     * Useful when a network get the not main parameter after being main.
     * Do nothing if the network was not started */
    public void stopManagers(String network){
        // do nothing
    }

    /** Checks wether a network manager has been initialized */
    public boolean started(String net){
        return nets.get(net)!=null;
    }

    /** Look for services in an MSD way. */
    public void search() throws Exception{
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

    private MSDTester tester=null;
    public void setTester(MSDTester tester){
        this.tester=tester;
    }

    /** Receive a connection where receive and send messages */
    public void receive(Connection c,NetworkManager net) throws Exception{
        tester.receive(c);
    }

    /** Receive a message from a network manager.
     * @return A message as a response, or null if no response
     * is needed */
    public void receive(Message m,NetworkManager net) throws Exception{
        tester.receive(m);
    }

    /** Get a service MSD from its identifier */
    public Service getMSD(String id){
        try{
            Service msd=new Service(cache,false);
            msd.setName("MSD");
            msd.setIDCache(id);
            return(Service)cache.getElements(msd,cache.getChilds()).iterator().
                    next();
        } catch(Exception e){
            logger.error("Error while looking for MSD "+id+": "+e); //@@l
            return null;
        }
    }

    /** Get a hashtable of NetworkManagers being the key the generic name
     * of the network. */
    public Hashtable getNetworks(){
        return nets;
    }

    /** Get a SDManager started in this network */
    public SDManager getProxy(String net){
        return null;
    }

    /** Returns a Hashtable of NetworkManagers woth its unique name as the
     * key. Do not store this hashtable: it is created on the fly because the
     * main MSD in each network can change over time.
     */
    public Hashtable getUniqueNetworks(){
        Hashtable r=new Hashtable();
        for(Iterator i=nets.values().iterator();i.hasNext();){
            NetworkManager net=(NetworkManager)i.next();
            r.put(net.getGenericName(),net);
        }
        return r;
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
        try{
            stopManagers(net.getGenericName());
        } catch(Exception e){
            logger.error("I couldn't stop the managers: "+e); //@@l
        }
        nets.remove(net);
        try{
            getMSD().deleteChild(net.getNetwork());
        } catch(Exception e){
            logger.warn("Error while removing network: "+e); //@@l
        }
    }

    /** Finish this manager.
     * Finish the NetworkManagers started by the MSD and
     * every SDManager started. */
    public void finish(){
        logger.debug("Finishing MSDManager"); //@@l
        for(Iterator i=nets.values().iterator();i.hasNext();){
            NetworkManager net=(NetworkManager)i.next();
            net.finish();
            stopManagers(net.getGenericName());
        }

        working=false;
        logger.info("MSDManager finished"); //@@l
    }

    /** Event: an SDManager starts its searching */
    public void searchStarted(SDEvent e){
        // do nothing
    }

    /** Event: an SDmanager completes its searching */
    public void searchCompleted(SDEvent e){
        try{
            this.triggerCacheUpdated(null,null);
        } catch(Exception ex){
        }
    }

    /** Return a collection with the networks bridged.
     * @param net Generic name of a network. If null, return every network
     * this MSD connects (do not care about bridges!)
     * @returns A collection with the other NetworkManager extreme of the
     * bridge, or an empty Collection in the network is not bridged */
    public Collection getNetworksBridged(String net){
        if(net==null){
            return nets.values();
        }
        Vector v=new Vector();
        /** Look for a bridge network with net in it */
        Object o[]=msd.getNetworks().toArray();
        for(int i=0;i<o.length;i++){
            Network e=(Network)o[i];
            if(e.getName().equals("bridge")){
                Vector v2=new Vector();
                boolean bridged=false;
                Object o2[]=e.getChilds().toArray();
                for(int j=0;j<o2.length;j++){
                    Element e2=(Element)o2[j];
                    if(e2.getType()==Element.NETWORK){
                        String name=e2.getName();
                        if(name.equals(net)){
                            bridged=true;
                        } else{
                            v2.add(nets.get(e2.getName()));
                        }
                    }
                    if(bridged){
                        v.addAll(v2);
                    }
                }
            }
        }
        return v;
    }

    public Collection searchService(Element template,boolean cached){
        return null;
    }
}
