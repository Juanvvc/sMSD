/*
 * InternetNetworkManager.java
 *
 * Created on 14 de febrero de 2005, 17:24
 */

package org.msd.comm;

import org.msd.cache.*;
import org.msd.proxy.*;
import java.io.*;
import java.net.*;

/** A manager for internet/ethernet networks.
 * @version $Revision: 1.23 $ */
public class InternetNetworkManager extends NetworkManager{
    /** Servers for unicast and multicast communications.
     * If the UDP and TCP servers does not share the port,
     * starts another UDP server (serverU2) in the TCP port*/
    private Server serverM=null,serverU=null,serverU2=null;

    /** Initializer.
     * This method initializes the NetworkManager and starts, at least, 
     * two servers: an InternetMServer for incoming UDP messages in the
     * multicast port and an InternetUServer for incoming TCP connections in
     * the unicast port. If the unicast and multicast ports are not the same,
     * another InternetMServer is started in the unicast port for incoming
     * UDP messages.
     *
     * @param name The generic name of this network.
     * @param multicast Multicast address in Internet.
     * @param local This msd claims to be in this local address.
     * If URL is null, try to guess the local url
     * (automatically guessing wouldn't work at systems with several IP
     * interfaces... including loopback)
     * @param msd The MSDManager to send the incoming messages
     * @param comm The CommManager to filter the incoming messages
     * @throws java.lang.Exception If the manager can not be started */
    public void init(String name,Address local,Address multicast,
                     MSDManager msd,
                     CommManager comm) throws Exception{
        String urlLocal=local.getURL();
        if(urlLocal==null||urlLocal.length()==0){
            urlLocal=InetAddress.getLocalHost().getHostAddress();
            local=new Address(urlLocal,local.getPort(),null);
        }

        super.init(name,local,multicast,msd,comm);

        /** Initialize multicast server: */
        try{
            serverM=new InternetMServer();
            serverM.setManager(this);
            serverM.start(multicast.getURL(),multicast.getPort());
            logger.info("InternetMServer started"); //@@l
        } catch(Exception e){
            serverM=null;
            logger.warn("InternetMServer not started: "+e); //@@l
            throw e;
        }
        /** Initialize unicast server: */
        try{
            serverU=new InternetUServer();
            serverU.setManager(this);
            serverU.start(local.getURL(),local.getPort());
            network.setAttrStr("url",serverU.getURL());
            network.setAttrStr("port",""+serverU.getPort());
            local=network.getAddress();
            logger.info("InternetUServer started"); //@@l
            if(serverM.getPort()!=serverU.getPort()){
                serverU2=new InternetMServer();
                serverU2.setManager(this);
                serverU2.start(serverU.getURL(),serverU.getPort());
                logger.info("Second InternetMServer started"); //@@l
            }
        } catch(Exception e){
            serverU=null;
            logger.warn("InternetUServer not started: "+e); //@@l
            throw e;
        }
    }

    /** Sends a unicast message.
     * Ignores the 'to' identifier of the message and send it to the gateway.
     * @param m The message to send
     * @param gw The identifier of the MSD-Gateway (bridge) to use
     * @throws java.lang.Exception After any error */
    public void sendU(Message m,String gw) throws Exception{
        // wrap the message into HTTP
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        commManager.send(m,out,this);
        byte msg[]=out.toByteArray();

        Network n=getMSDNetwork(getMSD(gw));
        if(n==null){
            throw new Exception("Receiver unkown: "+m.getIDTo());
        }
        sendU(m,n.getAddress());
    }

    /** Sends a message to a well known Address. Ignores the 'to' identifier
     * of the message. Use this method only to send messages to
     * MSDs with a unknown indetifier (but we got the url by other
     * means, such in UPDATE with content messages)
     * @param m The message to send
     * @param address The address of the remote MSD
     * @throws java.lang.Exception After any error */
    public void sendU(Message m,Address address) throws Exception{
        String url=address.getURL();
        int port=address.getPort();
        // wrap the message into HTTP
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        commManager.send(m,out,this);
        byte msg[]=out.toByteArray();

        // send the message
        if(msg.length>InternetMServer.SIZE){
            throw new Exception("Message too big");
        }
        DatagramSocket ds=new DatagramSocket();
        // send to url
        DatagramPacket dp=new DatagramPacket(msg,msg.length,
                                             InetAddress.getByName(url),port);
        ds.send(dp);
    }

    /** Get a TCP connection to send and receive messages inside this network.
     * @param type Type of connection.
     * @param from Identifier of the MSD sending messages.
     * @param to Indentifier of te MSD receiving messages
     * @param gw Gateway. Actually, we connect to the gateway and not to 'to'
     * MSD. If 'to' and 'from' are in the same network, use 'gw'='to'
     * @return A new connection to the remote MSD
     * @throws java.lang.Exception After any error.
     */
    public Connection getConnection(int type,String from,String to,String gw) throws
            Exception{
        Network net=getMSDNetwork(getMSD(gw));
        if(net==null){
            throw new Exception("Host unreachable");
        }
        return getConnection(type,from,to,net.getAddress());
    }

    public ConnectionStreams getConnection(Address address) throws IOException{
        Socket s=new Socket(address.getURL(),address.getPort());
        return new IConnectionStreams(s);
    }

    public Connection getConnection(int type,String from,String to,
                                    Address address) throws Exception{
        ConnectionStreams s=getConnection(address);
        Connection conn=new Connection(commManager,type,from,to,this,s);
        Message m=new Message((""+type).getBytes(),from,to,Message.CONN);
        m.setEncode(false);
        conn.send(m);
        return conn;
    }

    /** Send a multicast message with UDP protocol.
     * @param m The message to multicast
     * @throws java.lang.Exception If the message can not be sent */
    public void sendM(Message m) throws Exception{
        // wrap the message into HTTP
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        commManager.send(m,out,this);
        byte msg[]=out.toByteArray();

        InetAddress url=InetAddress.getByName(multicast.getURL());
        int port=multicast.getPort();

        // send the message
        if(msg.length>InternetMServer.SIZE){
            throw new Exception("Message too big");
        }
        DatagramSocket ds=new DatagramSocket();
        // send to url multicast
        DatagramPacket dp=new DatagramPacket(msg,msg.length,url,port);
        ds.send(dp);
    }

    /** @return This managers local address */
    public Address getAddress(){
        return local;
    }

    /** Finishes this network manager */
    public void finish(){
        if(finished){
            logger.warn("Finishing a network yet finished"); //@@l
            return;
        }
        // try to finish multicast and unicast servers
        if(serverM!=null){
            serverM.stop();
        }
        if(serverU!=null){
            serverU.stop();
        }
        if(serverU2!=null){
            serverU2.stop();
        }
        super.finish();
    }
}
