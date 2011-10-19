/** This is the mini version */
package org.msd.comm;

import org.msd.cache.*;
//import org.apache.log4j.Logger; //@@l
import org.msd.proxy.*;
import java.util.Vector;
import java.io.*;
import java.util.Hashtable;

/** This class implements the NetworkManager layer in the protocol stack of
 * the MSD. Send messages anc connection through a network to the MSDs just
 * in a jump (neighbours).
 *
 *  This class is in charge of making servers, sending and receiveing streams
 *  convertibles to a Message from/to an MSD and getting neighbors to a MSD.
 *  A MSDManager is able to be 'main'in a network. For  each network, just one
 *  MSDManager can be 'main'.
 *
 *  @version $Revision: 1.8 $ */
public abstract class NetworkManager{
    /** manager controlling this MSD */
    protected MSDManager msdManager=null;
    /** Generic name of this network */
    protected String name=null;
    /** Description of this network */
    protected Network network;
    /** Description of the MSD main of this network */
    protected Service msdMain=null;
    /** CommManager to use in this network */
    protected CommManager commManager=null;
    /** List of neighbors of this MSD.
     * key: the identifier of the remote MSD
     * object: the Date of the last message from this MSD */
    protected Hashtable neighbors=null;
    /** Addresses of the MSD */
    protected Address local, multicast;
    /** Wether this network manager is finished */
    protected boolean finished=true;

    /** Inits this manager.
     * Performs several common tasks. Classes extending this one must
     * overwrite this method but call to super.init() on the first line.
     * The URL of the network of the description MSD will be set to the
     * one claimed by this NetworkManager.
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
                     CommManager comm) throws Exception{
        if(!finished){
            throw new Exception("The network is yet started");
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

        this.local=local;
        this.multicast=multicast;
        finished=false;
    }

    /** Adds a new neighbor to the collection.
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
        neighbors.put(id,new java.util.Date());
    }

    /** @return A collection of neighbor identifers. */
    public Hashtable getNeighbors(){
        return this.neighbors;
    }

    /** Receives a message. Read the streams, unwrap them with CommManager and
     * send to MSDManager.
     * If the message is a connection, get or route as well.
     * @param in input to read
     * @param s Server which get the message.
     * @throws Exception If the message can not be sent. */
    public void receive(InputStream in,Server s) throws
            Exception{
        Message m=commManager.receive(in,this);

        // add the sender to the neighbors list
        setNeighbor(m.getIDFrom());
        
        // if the message was asking for a connection, place one
        if(m.getType()==Message.CONN){
            int type=Integer.valueOf(new String(m.getData())).intValue();
            Connection c=s.openConnection(commManager,type,m.getIDFrom(),
                                          m.getIDTo());
            msdManager.getRouter().receive(c,this);
            return;
        }
        
       msdManager.getRouter().receive(m,this);
    }

    /** Sends a message using unicast.
     * @param m Message to send
     * @param gw Identifier of the gateway to use. Setting of this gateway is
     * up to upper layers: this class just send the message to gw MSD. Of
     * course, if the sender and recipient are in the same network,
     * m.getIDTo()==gw.
     * @throws Exception If the message can not be sent. */
    public abstract void sendU(Message m,String gw) throws Exception;

    /** Sends a message to a known address
     * @param m Mesage to send
     * @param address Address of the bridge to the recipient
     * @throws Exception If the message can not be sent.*/
    public abstract void sendU(Message m,Address address) throws
            Exception;

    /** Gets a connection to a remote MSD.
     *
     * <p>The sendX(Message) family sends messages with the internal
     * protocol of CommManager, but in some circunstances (such as
     * imported libraries) is is no useful: we need a connection, and
     * a couple of streams. This method just provided a connection for
     * the network to a single host, to excahnge messages.
     * @param type of mesages to send in the connection.
     * @param from Identifier of the sender
     * @param to Say the connection is to this the sender
     * @param gw Connects throught this gateway
     * @return A connection to a remote MSD. */
    public abstract Connection getConnection(int type,String from,String to,
                                             String gw) throws
            Exception;

    /** @return A connection to a well known host.
     * @param type Type of Connection
     * @param from Identifier of the local host
     * @param to Identifier of the remote host
     * @param address Address of the remote host */
    public abstract Connection getConnection(int type,String from,String to,
                                             Address address) throws
            Exception;

    /** Connects to a host.
     * @param address Address of the remote host.
     * @return A couple of streams to connect to the address
     * @throws IOException If the manager can not connecto to the address.
     */
    public abstract ConnectionStreams getConnection(Address address) throws IOException;

    /** Sends a message using multicast. Returns inmediately (multicast
     * messages never get a response)
     * @throws Exception if the message can not be sent.*/
    public abstract void sendM(Message m) throws Exception;

    /** @return The MSD defined with the identifier in the network, or null
     * if not found. */
    public Service getMSD(String id) throws Exception{
        Vector msds=getMSDs();
        for(int i=0; i<msds.size();i++){
            Service e=(Service)msds.elementAt(i);
            if(e.getIDCache().equals(id)){
                return e;
            }
        }
        return null;
    }

    /** @return This network interface to directly connect with a MSD */
    public Network getMSDNetwork(Service msd) throws Exception{
        if(msd==null){
            return null;
        }
        Vector childs=msd.getChilds();
        for(int i=0; i<childs.size(); i++){
            Element e=(Element)childs.elementAt(i);
            if(e.getType()==Element.NETWORK&&e.getName().equals(name)){
                return(Network)e;
            }
        }
        return null;
    }

    /** @return Every MSD in the cache connected to this network... but me */
    public Vector getMSDs() throws Exception{
        Cache c=msdManager.getCache();
        Service msd=new Service(c,false);
        msd.setName("MSD");
        msd.setIDCache("");
        Network n=new Network(c,false);
        n.setIDCache("");
        n.setName(getGenericName());
        msd.appendChild(n);
        Vector coll=c.getElements(msd,c.getChilds());
        // remove this MSD from collection
        return c.getElementsNot(msdManager.getMSD(),coll);
    }

    /** Gets the generic name of this network */
    public String getGenericName(){
        return name;
    }

    /** Stops this manager because it will be no longer used.
     * Removes the network for the MSD. Likely, the classes extending
     * this one must overwrites this method but calling in the last line. */
    public void finish(){
        try{
            msdManager.removeNetwork(this);
        } catch(Exception e){
        }
        finished=true;
    }

    /** @return The main attribute for this network */
    public void setMain(boolean m){
        network.setMain(m);
        if(m){
            setMSDMain(msdManager.getMSD());
        }
    }

    /** @return The main attribute of this network */
    public boolean isMain(){
        return network.isMain();
    }

    /** @return The description of the MSD main of this network. */
    public Service getMSDMain(){
        return msdMain;
    }

    /** Sets the MSD main of this network.
     * @param msd The description of the MSD main of this network. */
    public void setMSDMain(Service msd){
        msdMain=msd;
    }

    /** @return The network describing this one */
    public Network getNetwork(){
        return network;
    }

    /** @return The local address to connect to this network manager */
    public Address getLocalAddress(){
        return local;
    }
    /** @return The multicast address of this network manager */
    public Address getMulticastAddress(){
        return multicast;
    }    
}
