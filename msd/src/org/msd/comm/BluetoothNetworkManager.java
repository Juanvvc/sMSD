/*
 * BluetoothNetworkManager.java
 *
 * Created on 14 de febrero de 2005, 17:26
 */

package org.msd.comm;

import org.msd.cache.*;
import org.msd.proxy.*;
import java.util.Collection;
import java.util.Iterator;
import java.util.Hashtable;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.Connector;
import java.io.InputStream;
import org.apache.log4j.Logger; //@@l
import java.io.ByteArrayInputStream;
import java.io.IOException;


/** A manager for Bluetooth networks.
 * Bluetooth is special: only handles connections, not single messages.
 * To save time and battery power we store every connection in case they
 * will be used in the future (very certainly). We use the URL as the
 * key of this special Bluetooth connections. Every message between two MSDs
 * are sent through this Bluetooth connections: single mesages are directly passed
 * to the router, but connected messages are pased to a VirtualConnection. */
public class BluetoothNetworkManager extends NetworkManager implements TimeListener{
    private static Logger logger=Logger.getLogger(BluetoothNetworkManager.class); //@@l
    private Server server=null;
    private String uuid=null;
    /** Hashtable of connections. In special connections, the key is the
     * url of the remote device. In virtual connections, the key is the
     * virtual identifier set by this manager or the remote one.
     */
    private Hashtable connections=null;
    /** Next identifier for a virtual connection. In order to ensure
     * unicity of this identifiers the actual virtual identifier is
     * <pre>identifier of the msd +"-" + nextVirtual</pre> */
    private long nextVirtual=0;

    /** Initialize this network manager. The local address will be the
     * address to connect to this MSD, and the multicast includes UUID and
     * mode of the connection for the MSD.
     * @param name Generic name of this network
     * @param address Address for the manager. Set uuid as the URL of
     * this address, and mode as the port of this address.
     * @param ignored This parameter is ignored.
     * @param msd The manager which creates this NetworkManager
     * @param comm Encode the Messages using this CommManager
     * @throws java.lang.Exception If any error during creation (including
     * 'the bluetooth network is not available for this device')
     */
    public void init(String name,Address address,Address ignored,MSDManager msd,
                     CommManager comm) throws Exception{
        uuid=address.getURL();
        int mode=address.getPort();
        server=new BluetoothServer();
        server.setManager(this);
        server.start(uuid,mode);
        local=new Address(server.getURL(),-1,null);
        multicast=new Address(uuid,mode,null);
        super.init(name,local,multicast,msd,comm);

        // Bluetooth special connections and the thread to control them
        connections=new Hashtable();
        // send an event every 60 seconds
        TimeManager.getTimeManager().register(this,60);
    }

    /** Receive a message. Read the streams, unwrap them with CommManager and
     * send to the router.
     * If the message is a connection, get or route as well.
     * @param in Input to read
     * @param s Server which get the message.
     * @throws java.lang.Exception If any error during reception occurs */
    public void receive(InputStream in,Server s) throws
            Exception{
        Message m=commManager.receive(in,this);
        logger.debug("Message from "+m.getIDFrom()+" to "+m.getIDTo()+ //@@l
                     " type "+m.getType()); //@@l

        if(m.getType()==Message.CONN){
            // if the message was asking for a connection, place one special
            ByteArrayInputStream in2=new ByteArrayInputStream(m.getData());
            int type=Integer.valueOf(CommManager.readLine(in2,16)).intValue();
            if(type!=Connection.CONN_BT){
                logger.warn("Connection type "+type+ //@@l
                            " without special connection: ignoring"); //@@l
            }
            String key=CommManager.readLine(in2,128);
            key=leaveUrlAlone(key);
            Connection c=s.openConnection(commManager,Connection.CONN_BT,
                                          m.getIDFrom(),m.getIDTo());
            logger.debug("Bluetooth connection from: "+s.getURL()); //@@l
            connections.put(key,c);
            new BTConnectionThread(c,this);
        } else{
            // if it was a single message...
            logger.warn("Single message through BluetoothNetworkManager."); //@@l
            msdManager.getRouter().receive(m,this);
            // close inmediatly this connection
            try{
                s.openConnection(null,0,null,null).close();
            } catch(Exception e){}
        }
    }

    /** Class used to receive messages from a special Bluetooth
     * connection and send them to the router or virtual connections.
     * @todo This thread is very similar to the one in Connection. Can we
     * convert this one in an event listener?
     */
    private class BTConnectionThread extends Thread{
        private Connection c=null;
        private NetworkManager net;
        public BTConnectionThread(Connection c,NetworkManager n){
            super("Special connection manager");
            this.c=c;
            net=n;
            start();
        }

        public void run(){
            boolean next=true;
            VirtualConnection vc=null;
            while(next&&!c.closed()){
                try{
                    Message m=c.receive();
                    // if the message was null, the connection is closed:
                    // break the loop
                    if(m==null){
                        next=false;
                        continue;
                    }

                    setNeighbor(c.getIDFrom());

                    logger.debug("Message from "+m.getIDFrom()+" virtual="+ //@@l
                                 m.getVirtual()); //@@l
                    switch(m.getType()){
                    case Message.CLOSE:
                        if(m.getVirtual()==null){
                            logger.warn("Unexpected CLOSE message"); //@@l
                            continue;
                        } else{
                            // try to close the virtual connection
                            vc=(VirtualConnection)connections.get(m.getVirtual());
                            if(vc==null){
                                logger.warn( //@@l
                                        "Trying to close an unknown virtual connection: "+ //@@l
                                        m.getVirtual()); //@@l
                            } else{
                                vc.closeConnection();
                            }
                        }
                        break;
                    case Message.CONN:

                        // make a new virtual connection
                        logger.debug("Virtual connection from "+m.getIDFrom()+ //@@l
                                     " to "+m.getIDTo()); //@@l
                        ByteArrayInputStream in=new ByteArrayInputStream(m.
                                getData());
                        int type=Integer.valueOf(CommManager.readLine(in,16)).
                                 intValue();
                        if(type==Connection.CONN_BT){
                            logger.warn( //@@l
                                    "Special connection inside antoher one: ignoring"); //@@l
                            break;
                        }
                        String key=CommManager.readLine(in,128);
                        vc=new VirtualConnection(type,m.getIDFrom(),m.getIDTo(),
                                                 net,c,key);
                        connections.put(key,vc);
                        msdManager.getRouter().receive(vc,net);
                        break;
                    default:

                        // other messages: look if they are from virtual
                        // connections or single
                        if(m.getVirtual()!=null){
                            // virtual: inform to the virtual connection
                            vc=(VirtualConnection)connections.get(m.getVirtual());
                            if(vc==null){
                                logger.warn("Message for virtual="+ //@@l
                                            m.getVirtual()+ //@@l
                                            " but connection not found"); //@@l
                                break;
                            }
                            logger.debug("New message "+m.getType()+" virtual="+ //@@l
                                         m.getVirtual()); //@@l
                            vc.newMessage(m);
                        } else{
                            // else, send the single message to the router
                            logger.debug("New single message for "+m.getIDTo()); //@@l
                            msdManager.getRouter().receive(m,net);
                        }
                    }
                } catch(Exception e){
                    logger.error("Error while receiving message: "+e); //@@l
                    e.printStackTrace(); //@@l
                    c.closeConnection();
                    next=false;
                }
            }
        }
    }


    public void sendU(Message m,String gw) throws Exception{
        logger.debug("Sending "+new String(m.getData())); //@@l
        Network net=getMSDNetwork(getMSD(gw));
        if(net==null){
            throw new Exception("MSD is unknown: "+m.getIDTo());
        }
        sendU(m,net.getAddress());
    }

    public void sendU(Message m,Address address) throws Exception{
        String url=address.getURL();
        logger.debug("Sending "+new String(m.getData())); //@@l
        Connection con;
        // if the connection was not yet stablished, place one
        con=(Connection)connections.get(leaveUrlAlone(url));
        if(con==null||con.closed()){
            con=getSpecialConnection(m.getIDFrom(),m.getIDTo(),
                                     address);
            con.send(m);
        } else{
            // else send the message through the connection
            con.send(m);
        }
    }

    public void sendM(Message m) throws Exception{
        logger.debug("Multicasting "+new String(m.getData())); //@@l
        Collection c=getMSDs();

        // if we have no MSD defined, try to look for one
        // if we are main, an SDPManager is running, so don't bother.
        if(c.size()==0&&!isMain()){
            lookForMSDs();
            c=getMSDs();
            logger.debug(c.size()+" new MSDs in the cache"); //@@l
        }

        for(Iterator i=c.iterator();i.hasNext();){
            Service msd=(Service)i.next();
            Network net=getMSDNetwork(msd);
            String url=net.getURL();

            // skip sending to me. Note we can not compare idcache: the
            // MSDBTServers are found by me, so idcache is mine.
            if(leaveUrlAlone(url).equals(leaveUrlAlone(server.getURL()))){
                continue;
            }

            logger.debug("Sending message to "+url); //@@l
            try{
                sendU(m,net.getAddress());
            } catch(Exception e){
                logger.error("Error in communication with "+url); //@@l
                e.printStackTrace();
            }
        }
    }

    /** Multicast on Bluetooth is a bit special: actually it is unicast
     * to every known MSDServer. So at the very beguinig we need to
     * look for MSDServers at Bluetooth network, although being clients.
     * Call this method to look for MSDs on bluetooth. The cache wil be filled
     * with temporal MSD with idcache="tmp". Remember remove this MSD if they
     * are no longer useful.
     * @returns If any MSD has been found */
    private boolean lookForMSDs(){
        // look for MSDServer services at Bluetooth
        boolean found=false;
        try{
            // create a temporal SDManager to store the MSDBTServers
            // This proxy sdp is only searching for the uuid of the MSDServer
            SDPManager sdp=new SDPManager();
            Cache cache=new Cache("tmp"); // temporal cache, to be removed
            Cache cacheOriginal=msdManager.getCache();
            //look for just MSDs
            sdp.init((Network)network.clone(cache),cache,null,uuid);
            // look for MSDServers
            Service msdServer=new Service(cache,false);
            msdServer.setName("MSDBTServer");
            Collection coll=sdp.searchService(msdServer,false);
            // convert MSDBTServers to MSD in the network
            for(Iterator i=coll.iterator();i.hasNext();){
                found=true;
                cacheOriginal.addElement((Element)((Element)i.next()).clone(
                        cacheOriginal));
            }
        } catch(Exception e){
            logger.error("Error looking for other MSDs: "+e); //@@l
        }
        return found;
    }

    public Connection getConnection(int type,String from,String to,
                                    String gw) throws
            Exception{
        Network net=getMSDNetwork(getMSD(gw));
        if(net==null){
            throw new Exception("Unreachable host");
        }
        if(type==Connection.CONN_BT){
            return getSpecialConnection(from,to,net.getAddress());
        } else{
            return getConnection(type,from,to,net.getAddress());
        }
    }

    public ConnectionStreams getConnection(Address address) throws IOException{
        String url=address.getURL();
        if(url==null){
            throw new NullPointerException();
        }
        logger.debug("Opening stream connection to "+url); //@@l
        StreamConnection con=(StreamConnection)Connector.open(url);
        if(con==null){
            throw new IOException();
        }
        return new BTConnectionStreams(con);
    }

    /** Get a normal connection to a remote device.
     * If a special connection was not place, place one and a virtual
     * connection for this asked connection.
     * @param type Type of the connection
     * @param from Identifier of the remitent
     * @param to Identifier of the recipient
     * @param address The address of the remote MSD.
     * @throws java.lang.Exception If the connection could be placed
     * @return The nee connection to the remote device.
     */
    public Connection getConnection(int type,String from,String to,
                                    Address address) throws Exception{
        String url=address.getURL();
        logger.debug("Opening virtual connection to "+url); //@@l
        Connection conn=(Connection)connections.get(leaveUrlAlone(url));
        if(conn==null||conn.closed()){
            logger.debug("No preexistent normal connection"); //@@l
            // if we haven't got a special connection, place one
            conn=getSpecialConnection(from,to,address);
        }
        // place a virtual connection on this connection
        String virtual=msdManager.getID()+"-"+(nextVirtual++);
        VirtualConnection vc=new VirtualConnection(type,from,to,this,conn,
                virtual);
        String r=type+CommManager.EOL+virtual;
        Message m=new Message(r.getBytes(),from,to,Message.CONN);
        m.setEncode(false);
        vc.send(m);
        connections.put(virtual,vc);
        logger.debug("Virtual connection placed"); //@@l
        return vc;
    }

    /** Place special connections.
     * An special connection in Bluetooth is just an RFCOMM connection to
     * a remote MSD, to share virtual connections and not-connected messages
     * in a efficient way.
     * @param from the identifier of the local MSD.
     * @param to The identifier of the remote MSD.
     * @param address The Address of the remote MSD.
     * @throws java.lang.Exception If the connection is not possible.
     * @return The special connection place. */
    public Connection getSpecialConnection(String from,String to,
                                           Address address) throws Exception{
        String url=address.getURL();
        logger.debug("Opening special connection to "+url); //@@l
        Connection conn=(Connection)connections.get(leaveUrlAlone(url));
        if(conn!=null){
            if(!conn.closed()){
                return conn;
            }
        }
        ConnectionStreams c=getConnection(new Address(url,-1,getGenericName()));
        conn=new Connection(commManager,Connection.CONN_BT,from,to,this,c);
        connections.put(leaveUrlAlone(url),conn);
        new BTConnectionThread(conn,this);

        String msg=Connection.CONN_BT+CommManager.EOL+server.getURL();
        Message m=new Message(msg.getBytes(),from,to,Message.CONN);
        m.setEncode(false);
        conn.send(m);
        logger.debug("Special connection placed"); //@@l
        return conn;
    }

    /** Get the MSD defined with the identifier in the network, or null
     * if not found.
     * @param id Identifier of the MSD
     * @return A service describing the MSD, or null.
     * @throws java.lang.Exception If an error occurs.
     * @todo When this method will throw an exception? */
    public Service getMSD(String id){
        for(Iterator i=super.getMSDs().iterator();i.hasNext();){
            Service e=(Service)i.next();
            logger.debug("MSD: "+e); //@@l
            if(e.getIDCache().equals(id)){
                return e;
            }
        }
        return null;
    }

    /** @return MSDBTServer neighbors of this MSD. */
    public Collection getMSDs(){
        Cache cache=msdManager.getCache();
        Service s=new Service(cache,false);
        s.setName("MSDBTServer");
        s.setIDCache("");
        Collection c=cache.getElements(s,cache.getChilds());
        return c;
    }

    /** @param url url
     * @return The same url without parameters (just protoco, bluetooth
     * address and channel). So btspp://1234:5;manager=false will return
     * btspp://1234:5 */
    private String leaveUrlAlone(String url){
        String type=null;
        int id=url.indexOf(";");
        if(id==-1){
            id=url.length();
        }
        String r=url.substring(0,id);
        return r;
    }

    public void finish(){
        if(finished){
            return;
        }
        logger.debug("Finishing "+getGenericName()); //@@l
        if(server!=null){
            synchronized(server){
                if(server!=null){
                    server.stop();
                }
                server=null;
            }
        }
        synchronized(connections){
            // close all connections
            for(Iterator i=connections.values().iterator();i.hasNext();){
                try{
                    Connection c=(Connection)i.next();
                    if(!c.closed()){
                        c.close();
                    }
                } catch(Exception e){
                    logger.warn("Error while closing connection: "+e); //@@l
                }
            }
            connections.clear();
        }
        super.finish();
    }

    /** Every 60 seconds look up the connections and close the ones
     * not used. Also clean up closed connections from hashtable.
     * @param type The type of the signal
     * @param data The data of the signal
     * @return Wether this signal is periodic or not */
    public boolean signal(int type,Object data){
        synchronized(connections){
            for(java.util.Enumeration e=connections.elements();
                                        e.hasMoreElements();){
                Connection c=(Connection)e.nextElement();
                try{
                    if(c.closed()){
                        connections.remove(c);
                    } else if(c.lastUsed()>60){
                        c.close();
                        connections.remove(c);
                        logger.warn("Connection automatically closed"); //@@l
                    }
                } catch(Exception ex){
                    logger.error("Error while closing connection: "+ex); //@@l
                }
            }
        }
        return true;
    }
}
