package org.msd.proxy;

import secureMulticast.client.*;
import secureMulticast.server.*;
import secureMulticast.event.*;
import javax.crypto.SecretKey;
import secureMulticast.keyDistribution.algorithm.LKH;
import org.msd.cache.*;
import org.msd.comm.*;
import org.apache.log4j.Logger; //@@l
import java.util.*;
import java.lang.Class;

/** This class implements the MSDManager layer in the protocol stack of the MSD.
 * Applications using the MSD as a library will probably use only this class
 * and its events.
 *
 * @date $Date: 2005-09-27 17:00:34 $
 * @version $Revision: 1.75 $
 */
public class MSDManager extends SDManager implements LKHListener,SDListener{
    private static Logger logger=Logger.getLogger(MSDManager.class); //@@l
    public static final String DEFAULT="org.msd.proxy.DefaultResourceBundle";
    /** MSD event listeners */
    private Collection msdlisteners;
    /** MSDLocalServiceListeners hashtable.
     * The key is the identifier of the service in the cache and the object
     * an instance of the MSDLocalServices interface. */
    private Hashtable msdlocalservices;
    /** NetworkManagers Hashtable.
     * The key is the generic name of the network.
     * The value is the NetworkManager started with this network */
    private Hashtable nets;
    /** Router of the system */
    private RouterManager router=null;
    /** This manager descriptor */
    private Service msd=null;
    /** Relates the name of the managers with proxies.
     * The key is the name of the proxy, and the object the SDManager. */
    private Hashtable proxies;
    /** ResurceBundle to read the configuration from. */
    private ResourceBundle res;
    /** CommManager to use */
    private CommManager comm;
    /** MessageManager to use */
    private MessageManager mess;
    /** Hastable with the LKH entities:
     * key: the name of the network
     * object: An LKHEntity (internal class of this class)
     */
    private Hashtable lkhs=null;

    /** Empty Constructor.
     * This class needs an empty constructor if it is loaded from
     * Class.newInstace (for example, throught a ClassLoader). Anyway, you
     * must avoid using any method before configure the manager with init(). */
    public MSDManager(){}

    /** @return An integer that identify this as an MSD Proxy*/
    public int getType(){
        return 3;
    }

    /** Initialices the manager using a cache readed from a file.
     * @param resource The resource name to get the configuration as
     * expected by java.util.ResourceBundle.getBundle(); The property to
     * read is CacheXML.
     * @throws java.lang.Exception If the MSD can not be started.
     */
    public void init(String resource) throws Exception{
        Cache c=new Cache();
        ResourceBundle res=ResourceBundle.getBundle(resource);
        c.load(new java.io.ByteArrayInputStream(res.getString("CacheXML").
                                                getBytes()));
        init("MSD",c,resource);
    }

    /** Initializes this manager.
     * Take the first MSD in the cache as the one this MSD is running, and
     * try to start every of its registered networks. A network not started
     * is removed from the description.
     * @param name The name of the msd, just for debugging ("MSD" is fine)
     * @param cache The cache to read the initial services. The identifier of
     * this cache will be the unique identifier of this MSD.
     * @param resource The configuration of the MSD.
     * @throws java.lang.Exception If the MSD can not be started. If no
     * network starts, the Exception is thrown. Remember calling finish()
     * method after an exception in init. */
    public void init(String name,Cache cache,String resource) throws Exception{
        Network netMSD=new Network(cache,false);
        netMSD.setName(name);
        super.init(netMSD,cache,resource);

        logger.info("Starting MSD"); //@@l

        // start objects
        msdlisteners=new Vector();
        msdlocalservices=new Hashtable();
        nets=new Hashtable();
        router=new RouterManager(this);
        proxies=new Hashtable();
        comm=new CommManager();
        lkhs=new Hashtable();

        // look in the cache and take the first MSD
        // FIXME: Maybe the "first MSD" varies with the implementation
        // of th XML parser. Test this with several MSDs services and
        // several XML parsers.
        Service emp=new Service(cache,false);
        emp.setIDCache("");
        emp.setName("MSD");
        msd=(Service)cache.getElements(emp,cache.getChilds()).iterator().next();

        // Read some properties from the conf file.
        res=ResourceBundle.getBundle(resource);

        String msdAlgorithm=res.getString("MSD.Algorithm");
        if(msdAlgorithm.equals("shared")){
            mess=new MessageManager(this);
        } else if(msdAlgorithm.equals("hierarchical")){
            mess=new HierarchicalMessageManager(this);
        } else{
            throw new Exception("Algorithm unknown: "+msdAlgorithm);
        }

        // browse childs and start the networks of the MSD
        Object o[]=msd.getNetworks().toArray();
        for(int i=0;i<o.length;i++){
            Network net=(Network)o[i];
            try{
                init(net,cache,resource);
            } catch(Exception e){
                e.printStackTrace();
                logger.info(net.getName()+" not started"); //@@l
                finish(net);
                msd.deleteChild(net);
            }
        }

        // if we do not have an available network, exit
        if(nets.size()==0){
            throw new Exception("No network available");
        }

        logger.info("MSD Started"); //@@l
        working=true;
    }

    /** Starts a network from its description.
     * To start a bridge, the networks bridged has to be previously started.
     * @param net The description of the network to be started.
     * @param cache The cache to look for information. If null, use
     * the one this MSD has been init with.
     * @param resource The resource file to look for configuration. If null,
     * use the one this MSD has been init with.
     * @throws Exception If the network can not be started.
     */
    public void init(Network net,Cache cache,String resource) throws
            Exception{
        if(cache==null){
            cache=this.cache;
        }
        ResourceBundle res;
        if(resource==null){
            res=this.res;
        } else{
            res=ResourceBundle.getBundle(resource);
        }
        logger.info("Starting network: "+net.getName()); //@@l
        String network=net.getName();

        // we use ClassLoader to load the network manager because some will
        // need libraries not present in every system. For example, the
        // BluetoothNetworkManager needs a JSR82 library and Bluetooth device,
        // and a static definition NetworkManaget n=new BluetoothNetworkManager
        // will crash the program if the library or device are not presents.
        ClassLoader cl=getClass().getClassLoader();
        if(network.equals("bluetooth")){
            try{
                String commClass="org.msd.comm.BluetoothNetworkManager";
                Class cloaded=cl.loadClass(commClass);
                NetworkManager nm=(NetworkManager)cloaded.newInstance();
                String uuid=res.getString("MSD.UUID");
                Address local=new Address(uuid,-1,network);
                nm.init(network,local,null,this,comm);
                nets.put(network,nm);
                if(net.isMain()){
                    nm.setMain(true);
                }
                logger.info("BluetoothNetworkManager started"); //@@l
            } catch(Throwable e){
                throw new Exception("BluetoothNetworkManager not started: "+e);
            }
        } else if(network.equals("ethernet")||
                  network.equals("wifi")){
            String url=net.getURL();
            int port=Integer.valueOf(net.getAttrStr("port")).intValue();

            try{
                String commClass="org.msd.comm.InternetNetworkManager";
                Class cloaded=cl.loadClass(commClass);
                InternetNetworkManager nm=(InternetNetworkManager)
                                          cloaded.newInstance();
                String urlM=res.getString("MSD."+network+
                                          ".MulticastURL");
                int portM=Integer.valueOf(res.getString("MSD."+network+
                        ".MulticastPort")).intValue();
                Address local=new Address(url,port,network);
                Address multicast=new Address(urlM,portM,network);
                nm.init(network,local,multicast,this,comm);
                nm.setMain(net.isMain());
                nets.put(network,nm);
                logger.info(network+" started"); //@@l
            } catch(Throwable e){
                throw new Exception(network+" not started: "+e);
            }
        } else if(network.equals("bridge")){
            // remove the bridge if any network bridged was not started
            // if every network has started, do nothing.
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
                throw new Exception("A network bridged not started.");
            } else{
                logger.info("Bridge registered"); //@@l
            }
        } else{
            throw new Exception("Unknown net: "+network);
        }

        // join to the network or start the manager
        NetworkManager net2=(NetworkManager)nets.get(net.getName());
        // if there is no NetworkManager associated to the network,
        // exit (this is the case of the bridges)
        if(net2==null){
            return;
        }
        if(net2.isMain()){
            iAmTheLeader(net2);
        } else{
            lookForLeaderOf(net2);
        }

        // start sending I_AM_HERE messages
        int time,factor;
        try{
            time=Integer.valueOf(res.getString("MSD.IAmHereTime")).intValue();
            factor=Integer.valueOf(res.getString("MSD.IAmHereFactor")).intValue();
        } catch(Exception e){
            logger.warn("IAmHere conf error (using default value): "+e.toString()); //@@l
            time=60;
            factor=3;
        }
        new IAmHere(time,factor,net2);
    }

    /** Looks for the leader of a given netork and joins to it.
     * If the leader is defined in the local cache, just join to it.
     * If not defined, start a leader searching. Do nothing if we are the
     * leaders of the network.
     * @param net We are looking for the manager of this network.
     * @throws java.lang.Exception If the leader can not be found.
     */
    public void lookForLeaderOf(NetworkManager net) throws Exception{
        if(net.isMain()){
            return;
        }
        // look for a defined MSD being manager of the network in the cache
        Service msdMain=new Service(cache,false);
        msdMain.setName("MSD");
        Network n=new Network(cache,false);
        n.setName(net.getGenericName());
        n.setMain(true);
        msdMain.appendChild(n);
        Collection c=cache.getElements(msdMain,cache.getChilds());
        if(c.size()>0){
            logger.debug("The local cache has a main MSD for "+ //@@l
                         net.getGenericName()+"("+msdMain+")"); //@@l
            // if the cache has defined a main MSD, join to it...
            // ..simulating a MAIN= message from the main MSD.
            mess.setLevel(MessageManager.DISCOVER_MAIN,net);

            Cache cc=new Cache(msdMain.getIDCache());
            cc.addElement(msdMain);
            System.out.println(cc.toString());

            Message m=new Message(cc.toString().getBytes(),
                                  msdMain.getID(),getID(),
                                  Message.MAIN_REPLY);
            mess.manageMainReply(m,net);
        } else{
            logger.debug("Looking for main of "+net.getGenericName()); //@@l
            // else, discover the main MSD
            mess.setLevel(MessageManager.UNDEF,net);
            mess.doMainRequest(net);
            TimeManager.getTimeManager().register(this,10,
                                                  MessageManager.DISCOVER_MAIN,
                                                  net);
        }
    }

    /** Reinit a network.
     * The network is initialized with the configuration given in the global
     * init, not the init of the network.
     * @param net The description of the network to be reinit.
     * @throws java.lang.Exception If the network can not be restarted
     */
    public void reinit(Network net) throws Exception{
        finish(net);
        init(net,cache,null);
    }

    /** This thread sends an I_AM_HERE message periodically multicasted
     * in a network. Besides, tests if any neighbor has left the network.
     */
    class IAmHere implements TimeListener{
        private NetworkManager net;
        private int time,factor;
        /** @param time Time in seconds between I_AM_HERE messages.
         * @param factor If any neighbor does not send messages in factor*time seconds,
         * inform MSDManager it has left the network. If factor<1, never inform.
         * @param net The network to send the IAMHere messeges.
         */
        public IAmHere(int time,int factor,NetworkManager net){
            this.factor=factor;
            this.net=net;
            this.time=time;
            TimeManager.getTimeManager().register(this,time);
            logger.debug("I_AM_HERE every "+time+" seconds for "+ //@@l
                         net.getGenericName()); //@@l
        }

        public boolean signal(int type,Object data){
            try{
                Message m=new Message(null,getID(),null,Message.I_AM_HERE);
                m.setEncode(false);
                // multicast a message
                net.sendM(m);

                if(factor<1){
                    return true;
                }
                // take the neighbors in the network and test of they are still alive
                Hashtable n=net.getNeighbors();
                Object o[]=n.keySet().toArray();
                for(int i=0;i<o.length;i++){
                    String id=(String)o[i];
                    Date d=(java.util.Date)n.get(id);
                    Date now=new Date();
                    if((now.getTime()-d.getTime())>(factor*time*1000)){
                        left(id,net);
                        n.remove(id);
                    }
                }
            } catch(Exception e){
                logger.warn("Error in I_AM_HERE thread: "+e); //@@l
                return false;
            }
            return true;
        }
    }


    /** Starts the service discovery managers for a given network.
     * Use this method when a network has been made main: its associated
     * managers will start.
     * Anyway, the main attribute of the network is not tested.
     * @param network Generic name of the new main network.
     * @todo If the SDP searching starts before internet being joined, it seems
     * there is a mutual blocking someplace. To avoid it this method does not
     * performs a searching in the manager started: it must be performed
     * by hand, or at least in ten seconds. We do not know how to by-pass
     * this situation. */
    public void startManagers(String network){
        logger.info("Starting SDManagers for "+network); //@@l
        String managers=res.getString("MSD."+network+".proxies");
        StringTokenizer st=new StringTokenizer(managers,",",false);
        // we use ClassLoader because the plugins can be distributed or
        // installed separately.
        ClassLoader cl=getClass().getClassLoader();
        while(st.hasMoreTokens()){
            String managername=st.nextToken();

            SDManager sd=(SDManager)proxies.get(managername);
            if(sd==null){

                String managerclass=res.getString("MSD.proxy."+managername);
                String managerres=res.getString("MSD.proxy."+managername+".res");
                int time=Integer.valueOf(res.getString("MSD.proxy."+managername+
                        ".time")).intValue();
                try{
                    Class c=cl.loadClass(managerclass);
                    sd=(SDManager)c.newInstance();
                    sd.init(((NetworkManager)getNetworks().get(network)).
                            getNetwork(),cache,managerres);
                    sd.addListener(this);
                    if(time>0){
                        TimeManager.getTimeManager().register(sd,time);
                    }
                    proxies.put(managername,sd);
                    logger.info("SDManager "+managername+" started"); //@@l

                    // start a searching inmediatly
                    try{
                        //sd.search();
                    } catch(Exception e){
                        logger.warn("Initial searching not started: "+e); //@@l
                    }
                } catch(Exception e){
                    logger.warn("SDManager couldn't start: "+e); //@@l
                }
            } else{
                // if the manager was yet started, try to restart with the new network
                try{
                    sd.reinit(((NetworkManager)getNetworks().get(network)).
                              getNetwork());
                    //sd.search();
                    logger.info("SDManager "+managername+" restarted"); //@@l
                } catch(Exception e){
                    logger.warn("SDmanager couldn't restart: "+e); //@@l
                }
            }
        }
    }

    /** Stops the managers for a network.
     * Useful when a network get the not main parameter after being main.
     * Do nothing if the network was not started
     * @param network The network we are starting the managers for. */
    public void stopManagers(String network){
        logger.info("Stopping SDManagers for "+network); //@@l
        // read managers from conf file
        String managers=res.getString("MSD."+network+".proxies");
        StringTokenizer st=new StringTokenizer(managers,",",false);
        while(st.hasMoreTokens()){
            String managername=st.nextToken();
            SDManager sd=(SDManager)proxies.get(managername);
            if(sd!=null){
                sd.finish(((NetworkManager)getNetworks().get(network)).
                          getNetwork());
                if(!sd.isWorking()){
                    proxies.remove(managername);
                }
            }
        }
    }

    /** Gets informed when an MSD leaves a network.
     * If the leaving MSD was main, starts an election.
     * @param id Identifier of the MSd away.
     * @param net The network without messages from this MSD. If null,
     * the MSD has left every network.
     * @todo If we are main MSD of a network, do a rekeying. This should
     * be covered by the rekeing algorithm in periodic distribution -> test */
    public void left(String id,NetworkManager net){
        logger.debug("Removing "+id+(net==null?".":" from "+net.getGenericName())); //@@l
        Service msd=getMSD(id);
        if(msd==null){
            logger.warn("Unknown leaving MSD: "+id); //@@l
            return;
        }
        // if net==null, remove the MSD from every NetworkManager shared.
        if(net==null){
            Object o[]=msd.getNetworks().toArray();
            for(int i=0;i<o.length;i++){
                Network n=(Network)o[i];
                NetworkManager net2=(NetworkManager)nets.get(n.getName());
                if(net2!=null){
                    left(id,net2);
                }
            }
            return;
        } else{
            // remove MSD from network
            mess.left(id,net);
        }

        if(id.equals(net.getMSDMain())){
            logger.info("Leaving MSD was main"); //@@l
            try{
                mess.doElection(net);
            } catch(Exception e){
                logger.error("Exception while starting an election: "+e); //@@l
            }
        }
    }

    /** @return Wether or not a network manager has been initialized.
     *  @param net The generic name of the network to test. */
    public boolean started(String net){
        return nets.get(net)!=null;
    }

    /** Looks for services in an MSD way.
     * This method force an UPDATE level in every networks started, if the
     * network is ready.
     * @throws java.lang.Exception If the search can not take place */
    public void search() throws Exception{
        logger.info("An MSD searching is started"); //@@l
        if(!working){
            throw new Exception("The MSDManager is not working!");
        }
        for(Enumeration e=nets.keys();e.hasMoreElements();){
            try{
                search((Network)msd.getNetwork((String)e.nextElement()));
            } catch(Exception ex){
                logger.warn("Error while searching network: "+ex);
            }
        }
    }

    /** Searches just in a network. Does nothing if the network is not started.
     * If this MSD is main of the network, performs a searching in every
     * SDManager registered. If not, performs a MessageManager.doBrowse().
     * @param net The description of the network to be searched.
     * @throws java.lang.Exception If the searching can not be done.
     */
    public void search(Network net) throws Exception{
        Service s=new Service(cache,false);
        s.setIDCache("");
        if(net.isMain()){
            for(Enumeration e=proxies.elements();e.hasMoreElements();){
                try{
                    ((SDManager)e.nextElement()).search(net);
                } catch(Exception ex){
                    logger.warn("Search couldn't be started: "+ex); //@@l
                }
            }
        } else{
            mess.doBrowse(s,(NetworkManager)nets.get(net.getName()));
        }
    }

    /** Use a service.
     * @param serv Description of the service to use. The ID and CacheID of
     * the service are mandatory. The other attributes are just ignored. Get
     * the identifiers using a template with the searchService() method.
     * @return A connection to the service
     * @throws java.lang.Exception If an error occurs.
     */
    public Connection useConnected(Service serv) throws Exception{
        logger.info("Trying to use service: "+serv); //@@l
        // get the closer MSD
        String idcacheC=serv.getIDCache();
        String idC=serv.getID();
        if(idcacheC==null||idC==null){
            throw new Exception("No MSD defined");
        }
        Service msd=getMSD(idcacheC);
        if(msd==null){
            throw new Exception("MSD unknown: "+idcacheC);
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

        logger.info("Connected to service"); //@@l

        // return the connection
        return c;
    }

    /** Use a service.
     * @param serv Description of the service to use
     * @return A couple of streams to use the service.
     * @throws Exception If an error occurs
     */
    public ConnectionStreams useService(Service serv) throws Exception{
        return TransformConnection.connectionToStreams(useConnected(serv));
    }

    /** @return The service describing this msd */
    public Service getMSD(){
        return msd;
    }

    /** @return The identifier of this MSD */
    public String getID(){
        return msd.getIDCache();
    }

    /** Send a message to other MSD.
     * @param m The message to send
     * @throws java.lang.Exception If the message can not be sent */
    public void send(Message m) throws Exception{
        router.route(m,null);
    }

    /** Receives a connection from other MSDs.
     * @param c The connection received.
     * @param net The network this connections comes from. If null, the
     * connection comes from this MSD (loopback).
     * @throws java.lang.Exception If the connection can not be received */
    public void receive(Connection c,NetworkManager net) throws Exception{
        try{
            switch(c.getType()){
            case Connection.JOIN:
                mess.manageJoin(c,net);
                break;
            case Connection.LEAVE:
                mess.manageLeave(c,net);
                break;
            case Connection.GET:
                mess.manageGet(c,net);
                break;
            case Connection.USE:
                mess.manageUse(c,net);
                break;
            case Connection.UPDATE:
                mess.manageUpdate(c,net);
                break;
            default:
                logger.debug("Unknow connection type: "+c.getType());
            }
        } catch(Exception e){
            logger.warn("The connection caused an exception: "+e); //@@l
            e.printStackTrace();
            // try to close the connection after an error
            try{
                c.close();
            } catch(Exception e2){
            }
        }
        // the connection can remain opened in another thread (USE, JOIN...)
    }

    /** Receive a message from a network manager.
     * @param m The message to be received.
     * @param net The network this message comes from. If null, the messages
     * comes from this MSD (loopback)
     * @throws java.lang.Exception If the message can not be received. */
    public void receive(Message m,NetworkManager net) throws Exception{
        /** End of line */
        /** Message keeping the response to the received message. Message is
         * null if not response is needed */
        switch(m.getType()){
        case Message.MAIN_REQUEST:
            mess.manageMainRequest(m,net);
            break;
        case Message.MAIN_REPLY:
            mess.manageMainReply(m,net);
            break;
        case Message.KEY:
            mess.manageKey(m,net);
            break;
        case Message.I_AM_HERE:
            mess.manageIamHere(m,net);
            break;
        case Message.UPDATE:
            mess.manageUpdate(m,net);
            break;
        case Message.LEFT:
            mess.manageLeft(m,net);
            break;
        case Message.CREDENTIAL:
            mess.manageCredential(m,net);
            break;
        default:
            logger.warn("Ignoring undefined message identifier: "+m.getType()); //@@l
        }
    }

    /** Joins to the MSD network using the LKH library.
     * Waits for a joining and rekeing.
     * @param net The network to join
     * @throws java.lang.Exception If the joining does not take place.
     * @todo This method close the system after any error. It is ok? */
    protected void joining(NetworkManager net) throws Exception{
        if(net.getMSDMain().equals(getID())){
            throw new Exception("Trying to join to ourselves!");
        }

        LKHEntity l;
        try{
            l=new LKHEntity();
            l.client=new LKHClient(this,Connection.JOIN,net);
            l.client.addLKHListener(this);
            lkhs.put(net.getGenericName(),l);
            int id=l.client.joining();
            // set the identifier of the cache as the one received
            if(id==0){
                throw new Exception("Not joined!");
            }

            // wait for rekeying
            synchronized(l.rekeyed){
                l.rekeyed.wait();
            }
        } catch(Exception e){
            logger.fatal("Not joined to the MSD network: "+e); //@@l
            System.exit(1);
        }
    }


    /** Leaves the network. Do nothing if not joined to the network.
     * @param net The network to leave */
    public void leaving(NetworkManager net){
        try{
            LKHEntity l=(LKHEntity)lkhs.get(net.getGenericName());
            if(l==null){
                return;
            }
            l.client.leaving(net);
        } catch(Exception e){
            logger.error("Not left the network: "+e); //@@l
        }
    }

    /** Adds a new listener for this class.
     * Keep in mind SDListeners and MSDListeners are different.
     * This class uses both.
     * @param l The new listener */
    public void addMSDListener(MSDListener l){
        msdlisteners.add(l);
    }

    /** Removes a listener for events from the MSDManager
     * @param  l The listener to be removed. */
    public void removeMSDListener(MSDListener l){
        msdlisteners.remove(l);
    }

    /** Gets an MSD from its identifier.
     * @param id The identifier of the msd. "0" means "this MSD"
     * @return A service describing the MSD or null. */
    public Service getMSD(String id){
        if(id.equals("0")){
            return msd;
        }
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

    /** @return A hashtable of NetworkManagers being the key the generic name
     * of the network. */
    public Hashtable getNetworks(){
        return nets;
    }

    /** @param name The name of a service protocol (slp, sdp)
     * @return The SDManager managing this protocol. */
    public SDManager getProxy(String name){
        return(SDManager)proxies.get(name);
    }

    /** @return The router of this system */
    public RouterManager getRouter(){
        return router;
    }

    /** @return The comm manager object */
    public CommManager getCommManager(){
        return comm;
    }

    /** Gets a connection to a remote manager
     * @param type The type of the connection.
     * @param to The identifier of the remote MSD
     * @throws java.lang.Exception If the connection can not be established
     * @return The connection to the remote MSD
     * @see org.msd.comm.Connection */
    public Connection getConnection(int type,String to) throws Exception{
        return router.openConnection(type,cache.getID(),to);
    }

    /** Removes the network from the list.
     * This method does not stop managers not remove the network from
     * the MSD description: it just remove the network from the internal
     * list of networks used.
     * @param net The network to be removed. */
    public void removeNetwork(NetworkManager net){
        nets.remove(net);
    }

    /** Replies to a detected LKHEvent.
     * @param e The LKH even*/
    public void LKHEventPerformed(LKHEvent e){
        logger.info("LKHEvent "+e.getEvent()); //@@l
        LKHEntity l=(LKHEntity)lkhs.get(e.getNetwork().getGenericName());
        if(l==null){
            logger.warn("LKH event in network "+e.getNetwork().getGenericName()+ //@@l
                        " ignored"); //@@l
        }
        switch(e.getEvent()){
        case LKHEvent.JOINING:
            if(l.server!=null){
                l.algorithm.process();
            } else{
                logger.warn("JOINING event and not LKHserver!"); //@@l
            }
            break;
        case LKHEvent.LEAVING:
            if(l.server!=null){
                l.algorithm.process();
            } else{
                logger.warn("LEAVING event and not LKHserver!"); //@@l
            }
            break;
        case LKHEvent.NEW_SEK:
            logger.info("A new key has arrived"); //@@l
            try{
                l.key=e.getNewSEK();
                comm.setKey(l.key,"DES",e.getNetwork());
                synchronized(l.rekeyed){
                    l.rekeyed.notifyAll();
                }
                break;
            } catch(Exception ex){
                logger.error("Error while rekeing: "+e); //@@l
            }

        }
    }

    /** @param type Trigger this event to the listeners ... (except UPDATE)
     * @param net ... occured at this network generic name
     */
    void triggerEvent(int type,String net){
        // Always inform to the listeners
        synchronized(msdlisteners){
            MSDEvent e=new MSDEvent(this,type,net);
            for(Iterator i=msdlisteners.iterator();i.hasNext();){
                MSDListener l=(MSDListener)i.next();
                l.event(e);
            }
        }
    }

    /** Triggers a cache UPDATE event in every network and calls to the listeners
     * @param net If this MSD is main of the network, UPDATE every networks. If
     * it is not, update the neteworks bridged by this one. If null, update
     * every network of the MSD.
     * @param updateID Use this identifier as the UPDATE identifier for the
     * MessageManager.doUpdate() method. */
    protected void triggerCacheUpdated(NetworkManager net,String updateID){
        Object[] updateNetworks=null; //networks to update
        if(net==null||net.isMain()){
            // if main, update every network of the MSD
            updateNetworks=nets.values().toArray();
        } else{
            // if not, update every network bridged, if any
            updateNetworks=getNetworksBridged(net.getGenericName()).toArray();
        }
        // actually update the networks with the updateID
        for(int i=0;i<updateNetworks.length;i++){
            try{
                mess.doUpdate(cache,(NetworkManager)updateNetworks[i],updateID);
            }catch(Exception e){
                logger.warn("Error while updating network: "+e); //@@l
            }
        }
        // trigger the event
        triggerEvent(MSDEvent.UPDATED,null);
    }

    /** Gets informed of a changing on level.
     * @param net The NetworkManager that changed its level. */
    void levelChanged(NetworkManager net){
        triggerEvent(MSDEvent.LEVEL,net.getGenericName());
    }

    /** @param net A generic name for a network.
     * @return The level of execution for this network or UNDEF if not started. */
    public int getLevel(String net){
        if(net==null){
            return MessageManager.UNDEF;
        }
        NetworkManager net2=(NetworkManager)nets.get(net);
        if(net2==null){
            return MessageManager.UNDEF;
        }
        return mess.getLevel(net2);
    }

    /** Finishes this manager and liberates every resoure used.
     * Finishes the NetworkManagers started by the MSD and
     * every SDManager started. Use this method before closing the MSD. */
    public void finish(){
        logger.debug("Finishing MSDManager"); //@@l
        if(!working){
            logger.warn("MSDManager finishing although not working"); //@@l
        }
        for(Enumeration e=nets.keys();e.hasMoreElements();){
            String net=(String)e.nextElement();
            try{
                finish(msd.getNetwork(net));
            } catch(Exception ex){
                logger.warn("Network "+net+" couldn't be stoped: "+ex); //@@l
            }
        }
        for(Enumeration e=proxies.elements();e.hasMoreElements();){
            ((SDManager)e.nextElement()).finish();
        }

        working=false;
        logger.info("MSDManager finished"); //@@l
    }

    /** Stops a network from its name.
     * Does nothing if the network is not started.
     * @param net The description of the network to be finished.
     */
    public void finish(Network net){
        NetworkManager network=(NetworkManager)nets.get(net.getName());
        if(network==null){
            return;
        }
        try{
            mess.doFinalUpdate(network);
            mess.doLeaving(network);
        } catch(Exception e){
            // ignore the exceptions
        }
        stopManagers(net.getName());
        nets.remove(net.getName());
        network.finish();
        // Note we are not closing the LKHserver. Is there any method to?
    }

    /** During finalize event, does a finish.
     * Do not call manually to this method: the garbage collector is up to.
     * @throws java.lang.Throwable If the class can not be finalized.
     */
    public void finalize() throws Throwable{
        finish();
        super.finalize();
    }

    /** @param e An SDManager starts its searching */
    public void searchStarted(SDEvent e){
        // do nothing
    }

    /** @param e An SDmanager completes its searching */
    public void searchCompleted(SDEvent e){
        try{
            triggerCacheUpdated(null,null);
        } catch(Exception ex){
            logger.error("Error while triggering event: "+ex); //@@l
            ex.printStackTrace();
        }
    }

    /** Returns a collection of NetworkManagers with the networks bridged.
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

    /** Searches for a template in the manager.
     *
     * This method does a browse or searches directly inside the cache.
     * It could be a rather long tim if the cached=false flag is used.
     * @param template Service to search.
     * @param cached Look up the local cache or start a new searching.
     * @return A collection of services matching the template. */
    public Collection searchService(Service template,boolean cached){
        if(cached){
            return cache.getElements(template,cache.getChilds());
        } else{
            return mess.doBrowse(template,
                                 (NetworkManager)nets.values().iterator().next());
        }
    }

    /** Registers a service in the MSD using its own language
     * @param s The service to register.
     * @param l An implementation of MSDLocalServiceListener receiving the
     * remote connections from this service.
     * @throws java.lang.Exception If the serice can not be registered.
     * @see #deregister */
    public void registerService(Service s,MSDLocalServiceListener l) throws
            Exception{
        if(s.getCache()!=cache){
            throw new Exception(
                    "The service has not been created with the local cache");
        } else{
            if(s.getIDCache()!=null&&s.getIDCache().length()>0&&
               !s.getIDCache().equals(cache.getID())){
                throw new Exception(
                        "The service has not been created with the local cache");
            }
        }
        s.appendChild(proxy);
        cache.addElement(s);
        elements.add(s);
        msdlocalservices.put(s.getID(),l);
        this.triggerCacheUpdated(null,null);
    }

    /** Deregisters a service registered with register() method.
     * @param id The identifier of the service to deregister.
     * @throws java.lang.Exception If the service can not be deregistered.
     * @see #register */
    public void deregisterService(String id) throws Exception{
        Element s=cache.getElement(getID(),id);
        if(s==null){
            return;
        }
        if(!elements.contains(s)){
            throw new Exception("The service was not created with register()");
        }
        deleteElement(s);
        msdlocalservices.remove(id);
        this.triggerCacheUpdated(null,null);
    }

    /** @return An MSDLocalServiceListener to a service registered with the
     * register() method.
     * @param s The local service to look for.
     * @see #registerService */
    public MSDLocalServiceListener localServiceListener(Service s){
        return(MSDLocalServiceListener)msdlocalservices.get(s.getID());
    }

    /** Sets this MSDManager as the leader of a network.
     * Starts the service proxies.
     * @param net The network this MSD is the new leader.
     */
    public void iAmTheLeader(NetworkManager net){
        logger.info("Setting myself as leader of "+net.getGenericName()); //@@l
        net.setMain(true);

        lkhs=new Hashtable();
        try{
            // start the lkhserver if not started
            LKHEntity l=new LKHEntity();
            l.server=new LKHserver(LKHserver.DES,this,net);
            l.server.addLKHListener(this);
            l.key=l.server.start();
            comm.setKey(l.key,"DES",net);
            l.algorithm=l.server.getAlgorithm();
            logger.info("LKHserver started in network "+net.getGenericName()); //@@l
            lkhs.put(net.getGenericName(),l);

            startManagers(net.getGenericName());
            mess.setLevel(MessageManager.WAIT_EVENT,net);

            // multicast a MAIN= message
            // Create a new cache with the relevant information
            Cache clientCache=new Cache(cache.getID());
            String idmain=net.getMSDMain();
            clientCache.addElement((Element)getMSD(idmain).clone(
                    clientCache));
            clientCache.setID(idmain);
            // send the information
            Message m=new Message(clientCache.toString().getBytes(),getID(),
                                  null,Message.MAIN_REPLY);
            m.setEncode(false);
            net.sendM(m);

            logger.info("Leader initialized"); //@@l
        } catch(Exception e){
            logger.error("Error while setting myself as leader: "+e); //@@l
            working=false;
        }
    }

    /** This method recives a signal from a TimeManager.
     * The only signal managed is the timeout of the main discovering while
     * initializing a network. The other signals are passed to the superclass.
     * @param type The type of the signal. If type==MessageManager.DISCOVER_MAIN
     * and the MSD is not joined to an MSD main, starts an election.
     * @param data The data of the signal.
     * @return If the signal is periodical. */
    public boolean signal(int type,Object data){
        if(type==MessageManager.DISCOVER_MAIN&&data instanceof NetworkManager){
            NetworkManager net=(NetworkManager)data;
            if(mess.getLevel(net)==MessageManager.DISCOVER_MAIN){
                logger.info("A MAIN MSD can not be found: starting election"); //@@l
                try{
                    mess.doElection(net);
                } catch(Exception e){
                    logger.error("Exception while starting an election: "+e); //@@l
                }
            }
            return false;
        } else{
            return super.signal(type,data);
        }
    }

    /** @return An array of bytes with the certificate of this MSD, or null
     * if no certificate is available.
     * @todo Let a configurable file for this method, and just read the file
     * onece. */
    public byte[] getCertificate(){
        try{
            return org.msd.cache.XMLTools.toByteArray(new java.io.
                    FileInputStream("certificate"));
        } catch(Exception e){
            e.printStackTrace();
            return null;
        }
    }

    /** Class to store information useful for the LKH algorthim for a given
     * network. In a network, an MSD has a client or a server started, never both.
     */
    private class LKHEntity{
        LKH algorithm;
        LKHserver server;
        LKHClient client;
        SecretKey key;
        // Use this object to wait for a new rekying from main
        Object rekeyed=new Object();
    }


    /** A seed for unique identifiers */
    private int idseed=0;
    /** @param net The Generic name of a network.
     * @return An unique ID for each call for communications through the network,
     * or "0" if this MSD is not the main MSD of the network. */
    public String getUniqueID(String net){
        if(((NetworkManager)nets.get(net)).isMain()){
            return ""+idseed++;
        } else{
            return "0";
        }
    }
}
