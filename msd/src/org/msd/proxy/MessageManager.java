package org.msd.proxy;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Hashtable;

import org.apache.log4j.Logger; //@@l
import org.msd.cache.*;
import org.msd.comm.*;
import org.msd.election.MSDMasterElection;
import secureMulticast.keyDistribution.net.*;

/** Manages the messages that arrives to an MSDManager.
 * This message manager implements the Shared algorithm.
 * @version $Revision: 1.65 $
 * @todo We should remove 'tmp' elements at a given moment... */
public class MessageManager{
    // Identifier of the level
    public final static int UNDEF=-1;
    public final static int DISCOVER_MAIN=0;
    public final static int JOINING=1;
    public final static int INITIAL_UPDATE=2;
    public final static int WAIT_EVENT=3;
    public final static int UPDATE=4;
    public final static int USE=5;
    public final static int ELECTION=6;
    public final static int BROWSE=7;
    public final static int FINAL_UPDATE=8;
    public final static int LEAVING=9;
    public final static int LEFT=10;

    private static final Logger logger=Logger.getLogger(MessageManager.class); //@@l

    /** A hashtable with the identifiers of the updates.
     * The key is the generic name of a network, and the
     * object a String with the identifier of the last UPDATE message sent.
     */
    private Hashtable updateIDs=new Hashtable();


    /** A hashtable with levels of execution.
     * Key: the generic name of a network
     * Object: An Integer with the level of execution for this network
     */
    private Hashtable levels=null;

    /** A hashtable with the objects for manager election.
     * The keys are the generic name of a network and the object
     * an MSDMasterElection.
     */
    private Hashtable elections=null;


    /** Gets the current level of execution for a network.
     * @param net The network.
     * @return The level of execution.*/
    public int getLevel(NetworkManager net){
        if(net==null){
            return UNDEF;
        }
        Integer i=(Integer)levels.get(net.getGenericName());
        if(i==null){
            return UNDEF;
        } else{
            return i.intValue();
        }
    }

    /** Sets the current level of execution for a network.
     * @param l Level of execution. See constants.
     * @param net The network
     */
    public void setLevel(int l,NetworkManager net){
        levels.put(net.getGenericName(),new Integer(l));
        msd.levelChanged(net);
    }

    MSDManager msd=null;
    Cache cache=null;
    /** @param msdManager The MSDManager owner of this object */
    public MessageManager(MSDManager msdManager){
        msd=msdManager;
        cache=msd.getCache();
        levels=new Hashtable();
        elections=new Hashtable();
    }

    /** Manages an MAIN_REQUEST message. Multicasted by a client asking
     * for the main of the MSD net. Every MSD answer the identifier of the
     * main MSD.
     * @param m The received message.
     * @param net The network this message comes from.
     * @throws java.lang.Exception If something goes wrong. */
    protected void manageMainRequest(Message m,NetworkManager net) throws
            Exception{
        // ignore the message we are ot main of the network
        if(!net.getMSDMain().equals(cache.getID())){
            return;
        }

        logger.info("MAIN? received from "+m.getIDFrom()); //@@l

        int level=getLevel(net);
        if(level!=WAIT_EVENT){
            logger.warn("MAIN?: We are not ready: level="+level); //@@l
            return;
        }

        // Get the other party information
        String url=null;
        int port=0;
        try{
            Network clientNet=((Service)cache.createElementFromXML(new
                    String(m.getData()))).getNetwork(net.getGenericName());
            url=clientNet.getURL();
            port=clientNet.getPort();

            // if the network is Bluetooth, save the service by hand.
            // Actually, we know the Bluetooth network has been changed,
            // so we should mark the SDPManager for a new searching. If the
            // searching is at the same time that the joining, errors
            // appear from time to time. So we join this MSDBTServer
            // by hand, and mark it as temporal, until we find a better way
            if(net.isMain()&&net.getGenericName().equals("bluetooth")){
                Service s=new Service(cache,false);
                s.setIDCache("tmp");
                s.setName("MSDBTServer");
                Network n=new Network(cache,false);
                n.setName("bluetooth");
                n.setURL(url);
                s.appendChild(n);
                // remove the MSDBTServer from the cache, if it was there
                Collection c=cache.getElements(s);
                if(c.size()!=0){
                    cache.deleteElement((Service)c.toArray()[0]);
                }
                // add the MSDBTServer to the cache
                cache.addElement(s);
            }
        } catch(Exception e){
            throw new Exception("Couldn't read client properties: "+e);
        }

        // Create a new cache with the relevant information
        String idmain=net.getMSDMain();
        Cache clientCache=new Cache(idmain);
        if(idmain!=null){
            clientCache.addElement((Element)msd.getMSD(idmain).clone(
                    clientCache));
        }
        transformOutCache(clientCache,net,m.getIDFrom());
        // send the information
        m=new Message(clientCache.toString().getBytes(),msd.getID(),
                      m.getIDFrom(),Message.MAIN_REPLY);
        m.setEncode(false);
        net.sendU(m,new Address(url,port,net.getGenericName()));
        logger.debug("Message MAIN= sent to "+url+":"+port); //@@l
    }

    /** Manages a MAIN_REPLY message.
     * @param m The received message.
     * @param net The network this message comes from.
     * @throws java.lang.Exception If something goes wrong. */
    protected synchronized void manageMainReply(Message m,NetworkManager net) throws
            Exception{
        // if we are not in the proper level
        int level=getLevel(net);
        if(level!=DISCOVER_MAIN&&level!=ELECTION){
            logger.warn("MAIN= and we are not in DISCOVER_MAIN level"); //@@l
            return;
        }

        // if we are in an election, stop the process
        // This means an MSD has been elected BEFORE we finish the election:
        // maybe it is no a goot election, but enough for the moment.
        if(level==ELECTION){
            MSDMasterElection election=(MSDMasterElection)elections.get(net.
                    getGenericName());
            if(election!=null){
                election.interruptElection();
            }
        }

        // Get the information... if any
        Cache c=validateCache(m);
        if(c.getChilds().size()==0){
            return;
        }
        // get the main MSD identifier.
        String idmain=c.getID();

        // ignore the MAIN= if we are main of the network
        if(net.getMSDMain()!=null&&net.getMSDMain().equals(msd.getID())){
            if(msd.getID().equals(idmain)){
                return;
            } else{
                // identify a conflict: maybe relect the main MSD?
                throw new Exception("We are main, as well as "+idmain);
            }
        }

        // if the message is from the main MSD...
        if(idmain.equals(m.getIDFrom())){
            // set the main of the network as the one sending this message
            net.setMSDMain(idmain);
            // and join the information to the local cache
            cache.join(c.getDocument());

            //send a JOIN message to the known main
            setLevel(JOINING,net);
            doJoining(net);
            // and get its cache
            doInitialUpdate(net);

            setLevel(WAIT_EVENT,net);
            logger.debug("JOIN process finished"); //@@l
        }
    }


    /** Manages KEY message. Passes the message to the LKH
     * @param m The received message.
     * @param net The network this message comes from.
     * @throws java.lang.Exception If something goes wrong. */
    protected void manageKey(Message m,NetworkManager net) throws Exception{
        logger.debug("Message KEY received"); //@@l
        int level=getLevel(net);
        if(level==WAIT_EVENT||level==JOINING){
            MSDMconnection.setMessage(m,net.getGenericName());
        } else{
            logger.warn("KEY: We are not ready: level="+level); //@@l
        }
    }

    /** Manages GET connection.
     * A message is read from the connection with the template to search in
     * the internal cache. If the message is empty, a default behaviour is
     * triggered depending on the role of the current MSD in the network:
     * a client send every service ofered by itself to the main, a main
     * sends every service to the client but the ones the client is offering,
     * and so on.
     * @param con The received message.
     * @param net The network this connection comes from.
     * @throws java.lang.Exception If something goes wrong.
     * @todo The filtering of the default behaviuor can be performed with
     * XSLT. */
    protected void manageGet(Connection con,NetworkManager net) throws
            Exception{
        int level=getLevel(net);
        if(level!=WAIT_EVENT&&level!=INITIAL_UPDATE&&level!=UPDATE){
            logger.warn("GET: We are not ready: level="+level); //@@l
            return;
        }

        Message m=con.receive();
        logger.info("GET message from "+m.getIDFrom()); //@@l
        Cache c=new Cache(msd.getID());
        if(m.getData()==null||m.getData().length==0){
            // the message was empty: default behaviour
            logger.debug("Empty content"); //@@l
            // do a work-around:
            if(net.isMain()){
                logger.debug("Remote host is a client"); //@@l
                // case we are a main cache: the client wants every service but
                // the ones it is offering, so...
                Service s=new Service(cache,false);
                s.setIDCache("");
                s.setGateway(m.getIDFrom());
                c.setElements(cache.getElementsNot(s,cache.getChilds()));
            } else if(net.getMSDMain().equals(m.getIDFrom())){
                logger.debug("Remote host is a main MSD"); //@@l
                // we are not a main cache but the inquirier is: it wants to
                // know every service we (and only we) are offering
                // remove every service being 'main' the gateway
                Service s=new Service(cache,false);
                s.setIDCache("");
                s.setGateway(m.getIDFrom());
                Collection c1=cache.getElementsNot(s,cache.getChilds());
                // remove every service discovered by 'main'
                s.setGateway("");
                s.setIDCache(m.getIDFrom());
                c1=cache.getElementsNot(s,c1);
                // remove every service in the same network as main
                // specifics msd's from this network
                Network n=new Network(cache,false);
                n.setIDCache("");
                n.setName(net.getGenericName());
                s.setIDCache("");
                s.appendChild(n);
                c.setElements(cache.getElementsNot(s,c1));
                // add its own msd
                c.addElement(msd.getMSD());
            } else{
                logger.debug("Remote host is unknown"); //@@l
                // other case: get every service in the cache
                Service s=new Service(cache,false);
                s.setIDCache("");
                c.setElements(cache.getElements(s,cache.getChilds()));
            }
            transformOutCache(c,net,con.getIDFrom());
        } else{
            // the message was not empty: use it as template
            String pattern=new String(m.getData());
            logger.debug("Pattern: "+pattern); //@@l
            c.setElements(cache.getElements(c.createElementFromXML(pattern)));
        }

        // construct the response
        con.sendBytes(c.toString().getBytes());
        con.close();

        logger.debug("GET finished"); //@@l
    }

    /** Manages a left message. Removes the services from the MSD in the
     * content from the local cache.
     * @param m The received message.
     * @param net The network this message comes from.
     * @throws java.lang.Exception If something goes wrong. */
    protected void manageLeft(Message m,NetworkManager net) throws Exception{
        int l=getLevel(net);
        if(l!=WAIT_EVENT){
            logger.warn("LEFT message and we are not ready: "+l); //@@l
            return;
        }
        String id=new String(m.getData());
        logger.debug("LEFT message: "+id+" has left the network"); //@@l
        left(id,net);
    }

    /** Manages a leave connection: pass to the LKH server.
     * @param conn The received connection.
     * @param net The network this connection comes from.
     * @throws java.lang.Exception If something goes wrong. */
    protected void manageLeave(Connection conn,NetworkManager net) throws
            Exception{
        String idmain=net.getMSDMain();
        if(idmain!=null&&idmain.equals(msd.getID())){
            logger.info("LEAVE connection from "+conn.getIDFrom()); //@@l
            int level=getLevel(net);
            if(level!=WAIT_EVENT){
                logger.warn("LEAVE: We are not ready: level="+level); //@@l
                return;
            }
            MSDUconnection.setConnection(conn,net.getGenericName());

            left(conn.getIDFrom(),net);

            // send a LEFT message to the clients
            Message m=new Message(conn.getIDFrom().getBytes(),msd.getID(),null,
                                  Message.LEFT);
            net.sendM(m);
        } else{
            logger.warn("Leave connection and I am not main."); //@@l
        }
        // the connection is kept open for the LKH library
    }

    /** Manages a join connection: pass to the LKH server.
     * @param conn The recived connection.
     * @param net The network this connection comes from.
     * @throws java.lang.Exception If something goes wrong. */
    protected void manageJoin(Connection conn,NetworkManager net) throws
            Exception{
        if(net.getMSDMain().equals(msd.getID())){
            logger.info("JOIN connection from "+conn.getIDFrom()); //@@l
            int level=getLevel(net);
            if(level!=WAIT_EVENT){
                logger.warn("JOIN: We are not ready: level="+level); //@@l
                return;
            }
            MSDUconnection.setConnection(conn,net.getGenericName());
        } else{
            logger.warn("Leave connection and we I am not main"); //@@l
        }
        // the connection is kept open for the LKH library
    }

    /** Manages a USE connection.
     * @param con The received connection.
     * @param net The network this connection comes from.
     * @throws java.lang.Exception If something goes wrong. */
    protected void manageUse(Connection con,NetworkManager net) throws
            Exception{
        logger.debug("USE connection from "+con.getIDFrom()); //@@l
        int level=getLevel(net);
        Message me=new Message(null,con.getIDTo(),con.getIDFrom(),Message.ERROR);
        if(level!=WAIT_EVENT){
            me.setData("Not ready".getBytes());
            con.send(me);
            throw new Exception("USE: We are not ready: level="+level);
        }
        // get the USE message
        Message m=con.receive();

        // try to connect to the service
        try{
            // assures the service is mine
            Service serv=(Service)cache.createElementFromXML(new String(m.
                    getData()));
            if(serv==null||!serv.getIDCache().equals(cache.getID())){
                throw new Exception("Service not mine");
            }
            Service s=(Service)cache.getElement(serv.getIDCache(),serv.getID());
            if(s==null){
                throw new Exception("Service to use not found");
            }

            // look if it is a local service
            MSDLocalServiceListener local=msd.localServiceListener(s);
            if(local!=null){
                if(!local.canConnect(con)){
                    throw new Exception("The connection is not accepted");
                }
                // send a confirmation message to the other party (i.e. same USE)
                con.send(m);
                // and use the service
                local.use(con);
            } else{
                // Not local: get a connection to the service in the network
                Object o[]=s.getNetworks().toArray();
                // try to connect to the service using our networks.
                // Maybe the MSD must be aware os the netweork to use, but
                // this doesn't seem a easy issue.
                for(int i=0;i<o.length;i++){
                    Network n=(Network)o[i];
                    NetworkManager net2=(NetworkManager)msd.getNetworks().get(n.
                            getName());
                    if(net2==null){
                        continue;
                    }
                    try{
                        ConnectionStreams cs=net2.getConnection(n.getAddress());
                        TransformConnection.connect(con,cs);
                    } catch(Exception e){
                        e.printStackTrace();
                        logger.warn("Couldn't connect to "+n.getAddress()); //@@l
                        throw new Exception("I couldn't connect");
                    }
                }
                // send a confirmation message to the other party (i.e. same USE)
                con.send(m);
            }

            logger.debug("USE finished: service and client connected"); //@@l
        } catch(Exception e){
            logger.error("Error using service: "+e); //@@l
            // error: send error message
            me.setData(e.toString().getBytes());
            con.send(m);
        }
    }

    /** Manages a credential message.
     * The credentials are stored always.
     * @param m The received message.
     * @param net The network this message comes from.
     * @throws java.lang.Exception If something goes wrong. */
    protected void manageCredential(Message m,NetworkManager net){
        // ignore ouer own credential
        if(m.getIDFrom().equals(msd.getID())){
            return;
        }
        logger.debug("New CREDENTIAL from "+m.getIDFrom()+", Size (bytes): "+
                     m.getData().length); //@@l
        // add the credential to the MSDMasterElection object
        MSDMasterElection election=(MSDMasterElection)elections.get(net.
                getGenericName());
        if(election==null){
            election=new MSDMasterElection(msd,net,msd.getCertificate());
            elections.put(net.getGenericName(),election);
        }
        election.newCertificate(m.getData());
    }

    /** Manages an I_AM_HERE message. This method does nothing.
     * @param m The received message.
     * @param net The network this message comes from.
     * @throws java.lang.Exception If something goes wrong. */
    protected void manageIamHere(Message m,NetworkManager net) throws
            Exception{
        // if we are the one to send this message, ignore it.
        if(net==null){
            return;
        }

        logger.debug("I_AM_HERE from: "+m.getIDFrom()); //@@l
    }

    /** Manages an UPDATE message. When an MSD receives this message must send a
     *  GET message, because the transmitter's cache has been changed.
     * @param m The received message.
     * @param net The network this message comes from.
     * @throws java.lang.Exception If something goes wrong.
     * @todo Test of the safest way of managing an exception in this method
     * is removing every service offered by the remote MSD sending the UPDATE */
    protected void manageUpdate(Message m,NetworkManager net) throws Exception{
        int level=getLevel(net);
        if(level!=WAIT_EVENT&&level!=INITIAL_UPDATE){
            logger.warn("UPDATE: We are not ready: level="+level); //@@l
            return;
        }
        // ignore our UPDATES
        if(net==null||m.getIDFrom().equals(cache.getID())){
            return;
        }

        // ignore ulticasted UPDATE from clients if we are clients
        if(!net.isMain()&&!net.getMSDMain().equals(m.getIDFrom())){
            return;
        }

        // Get the identifier of the UPDATE
        String idUpdate=new String(m.getData());
        // if we have this identifier, remove and exit
        String idUpdateRegistered=(String)updateIDs.get(net.getGenericName());
        if(idUpdateRegistered!=null&&idUpdateRegistered.equals((idUpdate))){
            logger.debug("Ignoring my UPDATE in network "+net.getGenericName()); //@@l
            return;
        }
        // else, manage the UPDATE

        logger.info("UPDATE from "+m.getIDFrom()+" id "+idUpdate); //@@l

        if(level==WAIT_EVENT){
            setLevel(UPDATE,net);
        }
        try{
            // Create a GET connection to the other MSD
            Connection con=net.getConnection(Connection.GET,msd.getID(),
                                             m.getIDFrom(),m.getIDFrom());
            // Send an empty message
            con.sendBytes(null);
            // Get the remote cache
            Message mess=con.receive();
            // close the connection
            con.close();
            // create the cache and validate it from the message
            Cache cache2=validateCache(mess);
            // filter the incoming cache
            transformInCache(cache2,net);
            // join the incoming cache to the local one
            if(cache2.getChilds().size()>0){
                cache.join(cache2);
            }
            // update the networks
            msd.triggerCacheUpdated(net,idUpdate);
        } catch(Exception e){
            // after a fail getting the cache, the safest behaviour is
            // assuming the remote MSD has left the network.
            logger.error("Error whil updating: "+e);
            e.printStackTrace();
            cache.deleteElementsFromCache(m.getIDFrom());
            msd.triggerEvent(MSDEvent.UPDATED,net.getGenericName());

            throw e;
        } finally{
            if(getLevel(net)==UPDATE){
                setLevel(WAIT_EVENT,net);
            }
        }

        logger.debug("UPDATE message finished"); //@@l
    }

    /** Manages an UPDATE connection.
     * @param con The connection
     * @param net The network this connection comes from
     */
    protected void manageUpdate(Connection con,NetworkManager net){
        int level=getLevel(net);
        if(level!=WAIT_EVENT&&level!=INITIAL_UPDATE){
            logger.warn("UPDATE: We are not ready: level="+level); //@@l
            return;
        }
        logger.info("UPDATE connection from "+con.getIDFrom()); //@@l

        if(level==WAIT_EVENT){
            setLevel(UPDATE,net);
        }

        String updateID=null;
        try{
            // Send a message with the identifier of the UPDATE
            updateID=msd.getUniqueID(net.getGenericName());
            con.sendBytes(updateID.getBytes());
            // Get the remote cache
            Message mess=con.receive();
            // close the connection
            con.close();
            // create the cache and validate it from the message
            Cache cache2=validateCache(mess);
            // filter the incoming cache
            transformInCache(cache2,net);
            // join the incoming cache to the local one
            if(cache2.getChilds().size()>0){
                cache.join(cache2);
            }
        } catch(Exception e){
            // after a fail getting the cache, the safest behaviour is
            // assuming the remote MSD has left the network.
            cache.deleteElementsFromCache(con.getIDFrom());
            msd.triggerEvent(MSDEvent.UPDATED,net.getGenericName());
        }

        if(getLevel(net)==UPDATE){
            setLevel(WAIT_EVENT,net);
        }

        // update the networks
        msd.triggerCacheUpdated(net,updateID);

        logger.debug("UPDATE connection finished"); //@@l
    }

    /** Transforms the input cache.
     * For each service, if our internal cache has the same service through other
     * gateway with a lesser number of hops, do not join the service to
     * our cache.
     * @param c The incoming cache.
     * @param net The incomng network.
     * @todo This transformation can be performed more easily with XSLT */
    protected void transformInCache(Cache c,NetworkManager net){
        Object o[]=c.getChilds().toArray();
        for(int i=0;i<o.length;i++){
            Element e=(Element)o[i];
            if(e.getType()!=Element.SERVICE){
                continue;
            }
            Service s=(Service)e;
            Service s2=(Service)cache.getElement(e.getIDCache(),e.getID());
            if(s2!=null){
                String gw1=s.getGateway();
                String gw2=s2.getGateway();
                if(gw1!=null&&gw2!=null&&!gw1.equals(gw2)){
                    if(s.getHops()>=s2.getHops()){
                        c.deleteElement(s);
                    }
                }
            }
        }
    }

    /** Transforms the cache to be sent throught a network.
     * This method removes every NetworkManager not being net
     * and if a service remains without networks, set this MSD
     * as the gateway. Method useful in bridges.
     * @param c Cache to transform
     * @param net Network to send the cache through
     * @param to Identifier from the receiver
     * @todo This transformation can be performed more easily with XSLT */
    protected void transformOutCache(Cache c,NetworkManager net,String to){
        Object[] o=c.getChilds().toArray();
        for(int i=0;i<o.length;i++){
            Element e=(Element)o[i];
            // if it is a temporal element, remove
            if(e.getIDCache()==null||e.getIDCache().equals("tmp")){
                c.deleteElement(e);
                continue;
            }
            if(e.getType()==Element.SERVICE){
                // remove the receptor of the message
                if(e.getName().equals("MSD")&&e.getIDCache().equals(to)){
                    c.deleteElement(e);
                }
                // remove special MSDBTServer services
                if(e.getName().equals("MSDBTServer")){
                    c.deleteElement(e);
                    o[i]=null;
                    continue;
                }
                // remove not used networks from service
                Object[] o2=((Service)e).getNetworks().toArray();
                boolean hasMoreNets=false;
                for(int j=0;j<o2.length;j++){
                    Element e2=(Element)o2[j];
                    if(e2.getName().equals(net.getGenericName())){
                        hasMoreNets=true;
                    } else{
                        try{
                            o2[j]=null;
                            e.deleteChild(e2);
                        } catch(Exception ex){
                            logger.error("Error while deleting child: "+ex); //@@l
                        }
                    }
                }
                // if the service has not more networks, set this MSD as the
                // gateway
                if(!hasMoreNets){
                    Service s=(Service)e;
                    s.setGateway(msd.getID());
                    // and add a new hop to use the service
                    s.setHops(s.getHops()+1);
                }
            }
        }
    }

    /** An MSD has left a network. Removes its services.
     * @param id Identifier of the leaving MSD
     * @param net NetworkManager the MSD has left.
     * @todo This code removes every service offered by the
     * eaving MSD... but since an MSD can leave just a network
     * interface, it can be offering services from other interfaces.
     * The situation will be solved in the next service searching, but
     * the services will be unavailable for a period of time.
     */
    public void left(String id,NetworkManager net){
        cache.deleteElementsFromCache(id);

        // UPDATE to networks bridged
        Object[] n=msd.getNetworksBridged(net.getGenericName()).toArray();
        for(int i=0;i<n.length;i++){
            msd.triggerCacheUpdated((NetworkManager)n[i],null);
        }
        // if no networks bridged, throw UPDATE event (do not send any message!)
        if(n.length==0){
            msd.triggerEvent(MSDEvent.UPDATED,net.getGenericName());
        }
    }

    /** Does an update.
     * @param cache The cache to send use in the update.
     * @param net The network to update
     * @param updateID The identifier of the UPDATE to use. If null, creates one.
     */
    public void doUpdate(Cache cache,NetworkManager net,String updateID){
        if(getLevel(net)!=WAIT_EVENT){
            logger.warn("DO_UPDATE: We are not ready for an UPDATE: level="+ //@@l
                        getLevel(net)); //@@l
            return;
        }
        if(updateID==null){
            updateID=msd.getUniqueID(net.getGenericName());
        }

        setLevel(UPDATE,net);
        logger.info("UPDATE level"); //@@l
        try{
            // if the MSD is leader of the network, create an UPDATE message
            // with the given identifier
            if(net.isMain()){
                Message m=new Message(updateID.getBytes(),msd.getID(),null,
                                      Message.UPDATE);
                net.sendM(m);
            } else{
                // else, create an UPDATE connection to the leader
                Connection con=net.getConnection(Connection.UPDATE,msd.getID(),
                                                 net.getMSDMain(),
                                                 net.getMSDMain());
                // save the ID of this UPDATE
                updateIDs.put(net.getGenericName(),new String(con.receiveBytes()));
                // send the cache
                Cache cache2=(Cache)cache.clone();
                transformOutCache(cache2,net,net.getMSDMain());
                con.sendBytes(cache2.toString().getBytes());
                con.closeConnection();
            }
        } catch(Exception e){
            logger.error("Error while updating: "+e); //@@l
            e.printStackTrace(); //@@l
        }
        setLevel(WAIT_EVENT,net);
    }

    /** Does a browsing.
     * If this MSD is main of the network, searches inside the local cache.
     * If it is client, does a GET connection to the main MSD of the network.
     * @param template Template to browse.
     * @param net Network to browse to for services.
     * @return A collection with the elements matching template,
     * or null if an error ocurred.
     */
    public Collection doBrowse(Element template,NetworkManager net){
        if(getLevel(net)!=WAIT_EVENT){
            logger.warn("DO_BROWSE: We are not ready: level="+getLevel(net)); //@@l
            return null;
        }

        setLevel(BROWSE,net);
        logger.info("BROWSE level"); //@@l
        Collection c=null;
        if(net.isMain()){
            try{
                c=cache.getElements(template);
            } catch(Exception e){
                logger.error("Error while browsing: "+e); //@@l
                e.printStackTrace();
            }
        } else{
            try{
                Connection con=msd.getConnection(Connection.GET,net.getMSDMain());
                Message m=new Message(template.toString().getBytes(),msd.getID(),
                                      net.getMSDMain(),Connection.GET);
                con.send(m);
                m=con.receive();
                con.close();
                Cache cc=new Cache();
                cc.load(new ByteArrayInputStream(m.getData()));
                c=cc.getChilds();
            } catch(Exception e){
                logger.error("Error while browsing: "+e); //@@l
                e.printStackTrace();
            }
        }
        setLevel(WAIT_EVENT,net);
        return c;
    }

    /** Does a Main Request in a network.
     * This method returns inmediately.
     * @param net Network we are looking main to. */
    public void doMainRequest(NetworkManager net){

        if(getLevel(net)!=UNDEF){
            logger.warn("DO_DISCOVER_MAIN: We are not ready: level="+ //@@l
                        getLevel(net)); //@@l
        }
        setLevel(DISCOVER_MAIN,net);
        logger.info("DISCOVER_MAIN level"); //@@l
        try{
            Message mess=new Message(msd.getMSD().toString().getBytes(),
                                     msd.getID(),null,Message.MAIN_REQUEST);
            mess.setEncode(false); // do not encode this message
            net.sendM(mess);
        } catch(Exception e){
            logger.error("Error while looking for main: "+e); //@@l
            e.printStackTrace(); //@@l
        }
    }

    /** Does an initial updating.
     * @param net The network to do the initial updating. */
    public void doInitialUpdate(NetworkManager net){
        if(getLevel(net)!=INITIAL_UPDATE){
            logger.warn("DO_INITIAL_UPDATE: We are not ready: level="+ //@@l
                        getLevel(net)); //@@l
            return;
        }
        // Get the whole cache from the main
        try{
            logger.info("INITIAL_UPDATE level"); //@@l
            String idmsd=msd.getID();

            Connection c=net.getConnection(Connection.GET,idmsd,net.getMSDMain(),
                                           net.getMSDMain());
            Message m=new Message(null,idmsd,net.getMSDMain(),
                                  Connection.GET);
            c.send(m);
            m=c.receive();
            c.close();

            Cache cache2=validateCache(m);
            cache.join(cache2.getDocument());

            // sends an UPDATE connection to the main of the network
            c=net.getConnection(Connection.UPDATE,idmsd,net.getMSDMain(),
                                net.getMSDMain());
            // save the UPDATe identifier
            String updateID=new String(c.receiveBytes());
            updateIDs.put(net.getGenericName(),updateID);
            cache2=(Cache)cache.clone();
            this.transformOutCache(cache2,net,net.getMSDMain());
            // send the local cache
            c.sendBytes(cache2.toString().getBytes());

            msd.triggerCacheUpdated(net,updateID);
        } catch(Exception e){
            logger.error("Error while retreiving cache from main: "+e); //@@l
        }
        setLevel(WAIT_EVENT,net);
    }

    /** Does a Final Updating. This method is empty.
     * @param net The network to do the final updating.
     * @throws java.lang.Exception If something goes wrong. */
    public void doFinalUpdate(NetworkManager net) throws Exception{
    }

    /** Does a joining.
     * @param net The network to do a joining. The main MSD of this network
     * must be known and registered in the cache. Does nothing if we are
     * the managers of the network.
     * @throws java.lang.Exception If something goes wrong. */
    public void doJoining(NetworkManager net) throws Exception{
        if(getLevel(net)!=JOINING){
            logger.warn("DO_JOINING: We are not ready: level="+getLevel(net)); //@@l
            throw new Exception("Not ready");
        }
        if(cache.getID().equals(net.getMSDMain())){
            return;
        }
        logger.info("JOINING level"); //@@l
        logger.debug("Using cache: "+cache); //@@l
        msd.joining(net);
        logger.debug("Joined to the network "+net.getGenericName()); //@@l
        setLevel(INITIAL_UPDATE,net);
    }

    /** Does a leaving.
     * @param net The network to do a joining. The main MSD of this network
     * must be known and registered in the cache. Does nothing if we are
     * the managers of the network.
     * @throws java.lang.Exception If something goes wrong. */
    public void doLeaving(NetworkManager net) throws Exception{
        if(getLevel(net)!=WAIT_EVENT){
            logger.warn("DO_LEAVING: We are not ready: level="+getLevel(net)); //@@l
            throw new Exception("Not ready");
        }
        if(cache.getID().equals(net.getMSDMain())){
            return;
        }
        logger.info("LEAVING level"); //@@l
        msd.leaving(net);
        setLevel(LEFT,net);
    }

    /** Starts an election.
     * @param net The network to do tehe election.
     * @throws java.lang.Exception If something goes wrong. */
    public void doElection(NetworkManager net) throws Exception{
        int level=getLevel(net);
        if(level!=WAIT_EVENT&&level!=DISCOVER_MAIN){
            logger.warn("DO_ELECTION: We are not ready: level="+getLevel(net)); //@@l
            return;
        }
        logger.info("ELECTION level"); //@@l
        setLevel(ELECTION,net);
        // multicast the credentials.
        Message m=new Message(msd.getCertificate(),msd.getID(),null,
                              Message.CREDENTIAL);
        net.sendM(m);

        // start the election
        MSDMasterElection election=(MSDMasterElection)elections.get(net.
                getGenericName());
        if(election==null){
            election=new MSDMasterElection(msd,net,msd.getCertificate());
            elections.put(net.getGenericName(),election);
        }
        election.startElection();
    }

    /** Reads a message and returns a Cache object with the
     * contents of the message.
     * @param m The Message
     * @return The cache contained in the message, with the services marked
     * as validated or not validated in this elementary network.
     * @throws java.lang.Exception If the message can not be converted to a
     * cache.*/
    protected Cache validateCache(Message m) throws Exception{
        // ask the user if the message is valid
        // In a complete system, the validation can be done automatically
        // with CommManager.validate() method. We want to test the security
        // here letting the user validate himself the messages
        AskValid a=new AskValid(m,msd.getCommManager());
        boolean valid=a.valid();
        //boolean valid=msd.getCommManager().validate(m);

        Cache cache2=new Cache();
        cache2.load(new ByteArrayInputStream(m.getData()));
        Object o[]=cache2.getChilds().toArray();
        for(int i=0;i<o.length;i++){
            try{
                Service s=(Service)o[i];
                if(valid){
                    s.setConfidence(s.getConfidence()+2);
                } else{
                    s.setConfidence(0);
                }
            } catch(Exception e){
                logger.warn("I can not set confidence level: "+e); //@@l
            }
        }
        return cache2;
    }
}
