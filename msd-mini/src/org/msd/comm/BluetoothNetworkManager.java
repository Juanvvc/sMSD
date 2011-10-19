/*
 * BluetoothNetworkManager.java
 *
 * Created on 14 de febrero de 2005, 17:26
 */

package org.msd.comm;

import org.msd.cache.*;
import org.msd.proxy.*;
import java.util.Vector;
import java.util.Hashtable;
import java.util.Enumeration;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.Connector;
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
        // set the URL of the network as a unique string
        // This is fair? Well, we know the other BluetoothNetworkManager will be
        // using special/virtual connections, and if it wants to connect to us
        // it will first use our URL to look inside its "connections" collection
        // for one yet placed (this collection is removed after a timeout).
        // For receiving the MAIN= message the timeout is enough, so the only
        // important thing about our URL is being unique, not actual.
        local=new Address(msd.getID(),-1);
        multicast=new Address(uuid,mode);
        super.init(name,local,multicast,msd,comm);

        // Bluetooth special connections and the thread to control them
        connections=new Hashtable();
        // send an event every 60 seconds
        TimeManager.getTimeManager().register(this,60);
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

                    switch(m.getType()){
                    case Message.CLOSE:
                        if(m.getVirtual()==null){
                            continue;
                        } else{
                            // try to close the virtual connection
                            vc=(VirtualConnection)connections.get(m.getVirtual());
                            if(vc==null){
                            } else{
                                vc.closeConnection();
                            }
                        }
                        break;
                    case Message.CONN:

                        // make a new virtual connection
                        ByteArrayInputStream in=new ByteArrayInputStream(m.
                                getData());
                        int type=Integer.valueOf(CommManager.readLine(in,16)).
                                 intValue();
                        if(type==Connection.CONN_BT){
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
                                break;
                            }
                            vc.newMessage(m);
                        } else{
                            // else, send the single message to the router
                            msdManager.getRouter().receive(m,net);
                        }
                    }
                } catch(Exception e){
                    e.printStackTrace();
                    c.closeConnection();
                    next=false;
                }
            }
        }
    }

    public void sendU(Message m,String gw) throws Exception{
        Network net=getMSDNetwork(getMSD(gw));
        if(net==null){
            throw new Exception("MSD is unknown: "+m.getIDTo());
        }
        sendU(m,net.getAddress());
    }

    public void sendU(Message m,Address address) throws Exception{
        String url=address.getURL();
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
        Vector c=getMSDs();

        // if we have no MSD defined, try to look for one
        // if we are main, an SDPManager is running, so don't bother.
        if(c.size()==0&&!isMain()){
            lookForMSDs();
            c=getMSDs();
            System.out.println(c.size()+" MSDs found");
        }

        for(Enumeration e=c.elements(); e.hasMoreElements();){
            Service msd=(Service)e.nextElement();
            Network net=getMSDNetwork(msd);

            try{
                sendU(m,net.getAddress());
            } catch(Exception ex){
                ex.printStackTrace();
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
            sdp.init((Network)network.clone(cache),cache,uuid);
            // look for MSDServers
            Service msdServer=new Service(cache,false);
            msdServer.setName("MSDBTServer");
            Vector coll=sdp.searchService(msdServer,false);
            
            // Manual hack: use this address if you found none in the
            // surroundings. Just for testing!
            if(coll.size()==0){
                msdServer.setIDCache("msd");
                Network n=new Network(cache,false);
                n.setName(this.name);
                n.setURL("btspp://000EA1012986:1");
                msdServer.appendChild(n);
                coll.addElement(msdServer);
            }

            for(Enumeration e=coll.elements();e.hasMoreElements();){
                found=true;
                cacheOriginal.addElement((Element)e.nextElement());
            }
        } catch(Exception e){
            e.printStackTrace();
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
        Connection conn=(Connection)connections.get(leaveUrlAlone(url));
        if(conn==null||conn.closed()){
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
        Connection conn=(Connection)connections.get(leaveUrlAlone(url));
        if(conn!=null){
            if(!conn.closed()){
                return conn;
            }
        }
        ConnectionStreams c=getConnection(new Address(url,-1));
        conn=new Connection(commManager,Connection.CONN_BT,from,to,this,c);
        connections.put(leaveUrlAlone(url),conn);
        new BTConnectionThread(conn,this);

        String msg=Connection.CONN_BT+CommManager.EOL+msdManager.getID();
        Message m=new Message(msg.getBytes(),from,to,Message.CONN);
        m.setEncode(false);
        conn.send(m);
        return conn;
    }

    /** Get the MSD defined with the identifier in the network, or null
     * if not found.
     * @param id Identifier of the MSD
     * @return A service describing the MSD, or null.
     * @throws java.lang.Exception If an error occurs.
     * @todo When this method will throw an exception? */
    public Service getMSD(String id) throws Exception{
        for(Enumeration en=super.getMSDs().elements(); en.hasMoreElements();){
            Service e=(Service)en.nextElement();
            if(e.getIDCache().equals(id)){
                return e;
            }
        }
        return null;
    }

    /** @return MSDBTServer neighbors of this MSD. */
    public Vector getMSDs(){
        Cache cache=msdManager.getCache();
        Service s=new Service(cache,false);
        s.setName("MSDBTServer");
        s.setIDCache("");
        Vector c=cache.getElements(s,cache.getChilds());
        return c;
    }

    /** @param url url
     * @return The same url without parameters (just protoco, bluetooth
     * address and channel). So btspp://1234:5;manager=false will return
     * btspp://1234:5 */
    private String leaveUrlAlone(String url){
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
        synchronized(connections){
            // close all connections
            for(Enumeration en=connections.elements();en.hasMoreElements();){
                try{
                    Connection c=(Connection)en.nextElement();
                    if(!c.closed()){
                        c.close();
                    }
                } catch(Exception e){
                }
            }

            // clear the connections table
            for(Enumeration e=connections.keys(); e.hasMoreElements();){
                connections.remove((String)e.nextElement());
            }
        }
        super.finish();
    }

    /** Every 60 seconds look up the connections and close the ones
     * not used. Also clean up closed connections from hashtable. */
    public boolean signal(int type,Object data){
        synchronized(connections){
            for(java.util.Enumeration e=connections.keys();e.hasMoreElements();){
                String k=(String)e.nextElement();
                Connection c=(Connection)connections.get(k);
                try{
                    if(c.closed()){
                        connections.remove(k);
                    } else if(c.lastUsed()>60){
                        c.close();
                        connections.remove(k);
                    }
                } catch(Exception ex){
                }
            }
        }
        return true;
    }
}
