package org.msd.comm;

import org.msd.cache.*;
import org.apache.log4j.Logger; //@@l
import org.msd.proxy.*;
import java.util.Collection;
import java.io.*;
import java.util.Hashtable;

/** This class implements the NetworkManager layer in the protocol stack of
 * the MSD. Send messages and connections through a network to the MSDs just
 * in a jump (neighbors).
 *
 *  This class is in charge of making servers, sending and receiveing streams
 *  convertibles to a Message from/to an MSD and getting neighbors to an MSD.
 *  A MSDManager is able to be 'main' in a network. For  each network, just one
 *  MSDManager can be 'main'.
 *
 *  @version $Revision: 1.39 $ */
public abstract class NetworkManager{
    /** The logger object of this class. */
    protected static Logger logger=Logger.getLogger(NetworkManager.class); //@@l
    /** manager controlling this MSD */
    protected MSDManager msdManager=null;
    /** Generic name of this network */
    protected String name=null;
    /** Description of this network */
    protected Network network;
    /** Identifier of the main MSD in the network */
    protected String idmain="";
    /** CommManager to use in this network */
    protected CommManager commManager=null;
    /** List of neighbors of this MSD.
     * key: the identifier of the remote MSD
     * object: the Date of the last message from this MSD */
    protected Hashtable neighbors=null;
    /** The local address of this network manager */
    protected Address local;
    /** Multicast address of this network manager. */
    protected Address multicast;
    /** If the network has been finished */
    protected boolean finished=true;

    /** Init this manager.
     * This method performs several common tasks. Classes extending this one must
     * overwrite this method but call to super.init() on the first line.
     * @param name Generic name of this network
     * @param multicast Remote Address for multicasting in this network. If its
     * name is null, set name to 'name'.
     * @param local Local address of the MSD in this network. If its name
     * is null, set name to 'name'.
     * @param msd Manager to be informed when a message arrives.
     * @param comm Manager to be use to encode/decode the
     * messages.
     * @throws Exception A generic exception if the manager couldn't start */
    public void init(String name,Address local,Address multicast,MSDManager msd,
                     CommManager comm) throws
            Exception{
        if(!finished){
            throw new Exception("The network is yet running");
        }
        neighbors=new java.util.Hashtable();
        this.name=name;
        msdManager=msd;
        commManager=comm;
        // get the network describing this one
        network=getMSDNetwork(msd.getMSD());
        network.setAddress(local);
        if(network==null){
            throw new Exception("I can't find "+name+" in the cache");
        }

        if(local.getName()==null){
            local=new Address(local.getURL(),local.getPort(),name);
        }
        if(multicast.getName()==null){
            multicast=new Address(multicast.getURL(),multicast.getPort(),name);
        }
        this.local=local;
        this.multicast=multicast;
        finished=false;
    }

    /** Add a new neighbor to the collection.
     * A neighbor is defined as an MSD inside a hop radius at
     * NetworkManager level. Tipically, we only discover a neighbor when
     * we receive a message from him.
     * @param id Identifier of the neighbor.
     * @throws NullPointerException if id==null
     */
    public void setNeighbor(String id){
        if(id==null){
            throw new NullPointerException();
        }
        if(id.equals(msdManager.getID())){
            return;
        }
        neighbors.put(id,new java.util.Date());
    }

    /** @return A collection of neighbor identifers. */
    public Hashtable getNeighbors(){
        return this.neighbors;
    }

    /**
     * Receive a message. Read the streams, unwrap them with CommManager and
     * send to MSDManager.
     * If the message is a connection, get or route as well.
     * @param in input to read
     * @param s Server which get the message.
     * @throws java.lang.Exception After any error during receiving the message.
     */
    public void receive(InputStream in,Server s) throws
            Exception{
        Message m=commManager.receive(in,this);
        logger.debug("Message from "+m.getIDFrom()+" to "+m.getIDTo()+ //@@l
                     " type "+m.getType()); //@@l

        // add the sender to the neighbors list
        setNeighbor(m.getIDFrom());

        // if the message was asking for a connection, place one
        if(m.getType()==Message.CONN){
            int type=Integer.valueOf(new String(m.getData())).intValue();
            Connection c=s.openConnection(commManager,type,m.getIDFrom(),
                                          m.getIDTo());
            logger.debug("Get connection from "+m.getIDFrom()+" to "+m.getIDTo()+ //@@l
                         " type "+m.getType()); //@@l
            msdManager.getRouter().receive(c,this);
            return;
        }

        msdManager.getRouter().receive(m,this);
        logger.debug("Message received"); //@@l
    }

    /**
     * Send a message using unicast.
     * @param m Message to send
     * @param gw Identifier of the gateway to use. Setting of this gateway is
     * up to upper layers: this class just send the message to gw MSD. Of
     * course, if the sender and recipient are in the same network,
     * m.getIDTo()==gw.
     * @throws java.lang.Exception After any error sending the message.
     */
    public abstract void sendU(Message m,String gw) throws Exception;

    /**
     * Send a message to a known address
     * @param m Mesage to send
     * @param address Address of the bridge to the recipient
     * @throws java.lang.Exception After any error sending the message
     */
    public abstract void sendU(Message m,Address address) throws
            Exception;

    /**
     * Get a connection for a remote MSD.
     *
     * The sendX(Message) family sends messages with the internal
     * protocol of CommManager, but in some circunstances is is no enough:
     * we need a connection and a couple of streams. This method just
     * provides a connection for the network to a single host, to exchange
     * a message flow.
     *
     * This method can only provide connections to hosts in the same network
     * than this one: no bridging is provided.
     * @see CommManager#getConnection
     * @return The connection to the remote host.
     * @param type of mesages to send in the connection.
     * @param from Identifier of the sender
     * @param to Identifier of the receiver
     * @param gw Use this gateway identifier. This method actually connects to
     * this gateway, acting as bridge of the receiver if they are not
     * the same host.
     * @throws java.lang.Exception If the remote host can ot be connected.
     */
    public abstract Connection getConnection(int type,String from,String to,
                                             String gw) throws
            Exception;

    /**
     * Gets a connection to a well know address.
     *
     * This method can not connect inter-networks.
     * @param type Type of Connection
     * @param from Identifier of the local host
     * @param to Identifier of the remote host
     * @param address Address of the remote gateway to connect to the remote host.
     * If the receiver is in this network, the 'address' is its
     * address.
     * @throws java.lang.Exception If the remote gateway can not be contacted.
     * @return A connection to the gateway
     * @see CommManager#getConnection
     */
    public abstract Connection getConnection(int type,String from,String to,
                                             Address address) throws
            Exception;

    /** Get an StreamConnection to a host.
     * @param address Address of the remote host.
     * @return A couple of streams to connect to the address
     * @throws IOException If the manager can not connecto to the address.
     */
    public abstract ConnectionStreams getConnection(Address address) throws IOException;

    /**
     * Send a message using multicast.
     * @param m The message to be multicasted.
     * @throws java.lang.Exception If the message can not be sent
     */
    public abstract void sendM(Message m) throws Exception;

    /**
     * Get the MSD defined with the identifier in the network, or null
     * if not found.
     * @param id Identifier of the MSD
     * @return The description of the MSD.
     */
    public Service getMSD(String id){
        Object o[]=getMSDs().toArray();
        for(int i=0; i<o.length; i++){
            Service e=(Service)o[i];
            if(e.getIDCache().equals(id)){
                return e;
            }
        }
        return null;
    }

    /**
     * Get the network interface to directly connect with a MSD.
     * @param msd The remote MSD to be contacted, in the same networl.
     * @return The network description to contact to the remote MSD or null if not found.
     */
    public Network getMSDNetwork(Service msd){
        if(msd==null){
            return null;
        }
        Object o[]=msd.getNetworks().toArray();
        for(int i=0; i<o.length; i++){
            Network e=(Network)o[i];
            if(e.getName().equals(name)){
                return e;
            }
        }
        return null;
    }

    /**
     * This method returns every MSD connected to this network... but itself.
     * @return A collection of services.
     */
    public Collection getMSDs(){
        Cache c=msdManager.getCache();
        Service msd=new Service(c,false);
        msd.setName("MSD");
        msd.setIDCache("");
        Network n=new Network(c,false);
        n.setIDCache("");
        n.setName(getGenericName());
        // Look for netid only if we know a main MSD.
        if(idmain!=null&&idmain.length()>0){
            n.setName(getGenericName());
        }
        msd.appendChild(n);
        Collection coll=c.getElements(msd,c.getChilds());
        // remove this MSD from collection
        return c.getElementsNot(msdManager.getMSD(),coll);
    }

    /**
     * Gets the generic name of this network
     * @return The generic name of this network.
     */
    public String getGenericName(){
        return name;
    }

    /** Stop this manager because it will be no longer used.
     * Removes the network for the MSD. Likely, the classes extending
     * this one must overwrites this method but calling in the last line.
     * Does nothing if the network was finished.*/
    public void finish(){
        if(finished){
            return;
        }
        try{
            msdManager.removeNetwork(this);
        } catch(Exception e){
            logger.warn("Error removing network: "+e); //@@l
        }
        finished=true;
        logger.info(getGenericName()+" finished."); //@@l
    }

    /**
     * During finalize event, finish the managers.
     * Do not call manually this method: the garbage collector is up to
     * @throws java.lang.Throwable If the object can not be finalized.
     */
    public void finalize() throws Throwable{
        finish();
        super.finalize();
    }

    /**
     * Sets the main attribute for this network.
     * If this MSD is main of the network, setMSDMain is also called.
     * @param m Wether this MSD is main or not of this NetworkManager.
     */
    public void setMain(boolean m){
        network.setMain(m);
        if(m){
            setMSDMain(msdManager.getCache().getID());
        }
    }

    /**
     * Gets the main attribute of this network
     * @return Wether this MSD is main or not of this NetworkManager
     */
    public boolean isMain(){
        return network.isMain();
    }

    /**
     * Returns the identifier of the main MSD of this network
     * @return The unique identifier of the MSD main of this network.
     */
    public String getMSDMain(){
        return idmain;
    }

    /**
     * Sets the identifier of the main MSD of this network
     * @param id The identifier of the main of this network.
     */
    public void setMSDMain(String id){
        idmain=id;
    }

    /**
     * Returns the network describing this one
     * @return The Network object describing this network.
     */
    public Network getNetwork(){
        return network;
    }

    /**
     * Gets the local address to connect to this network manager
     * @return The local address to contact to this MSD.
     */
    public Address getLocalAddress(){
        return local;
    }
    /**
     * Gets the multicast address of this network manager
     * @return The multicast address of this network.
     */
    public Address getMulticastAddress(){
        return multicast;
    }
}
