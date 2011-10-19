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
 * @version $Revision: 1.7 $ */
public class InternetNetworkManager extends NetworkManager{
    /** Servers for unicast and multicast communications.
     * serverU2 is used for UDP messages when unicast and multicast are not
     * using the same port */
    private Server serverM=null,serverU=null, serverU2=null;

    /**
     * @param name The generic name of this network.
     * @param multicast Multicast address in Internet.
     * @param local This msd claims to be in this local address.
     * If URl is null, try to guess the local url
     * (automatically guessing wouldn't work at systems with several IP
     * interfaces... including loopback. Take special care in IPv6 environments)
     * If the port is 0, use a system-provided one.
     * @param msd The MSDManager to send the incoming messages
     * @param comm The CommManager to filter the incoming messages */
    public void init(String name,Address local,Address multicast,
                     MSDManager msd,
                     CommManager comm) throws Exception{
        String urlLocal=local.getURL();
        if(urlLocal==null||urlLocal.length()==0){
            urlLocal=InetAddress.getLocalHost().getHostAddress();
            local=new Address(urlLocal,local.getPort());
            System.out.println("Setting local address: "+local);
        }

        super.init(name,local,multicast,msd,comm);

        /** Initialize multicast server: */
        if(multicast!=null){
            try{
                serverM=new InternetMServer();
                serverM.setManager(this);
                serverM.start(multicast.getURL(),multicast.getPort());
            } catch(Exception e){
                serverM=null;
                // An error in the multicast listener is not important.
                //throw e;
                e.printStackTrace();
            }
        }
        /** Initialize unicast server: */
        try{
            serverU=new InternetUServer();
            serverU.setManager(this);
            serverU.start(local.getURL(),local.getPort());
            network.setURL(serverU.getURL());
            network.setPort(serverU.getPort());
            // if unicast and multicast has different port numbers, start
            // another UDP server in the TCP port
            if(serverM==null||(serverM.getPort()!=serverU.getPort())){
            	serverU2=new InternetMServer();
            	serverU2.setManager(this);
            	serverU2.start(serverU.getURL(),serverU.getPort());
            }
            local=network.getAddress();
        } catch(Exception e){
            serverU=null;
            serverU2=null;
            throw e;
        }
    }

    /** Send a message with a TCP connection. If we have defined a gateway
     * to this recipient, send the message to it. */
    public void sendU(Message m,String gw) throws Exception{
        // wrap the message into HTTP
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        commManager.send(m,out,this);

        Network n=getMSDNetwork(getMSD(gw));
        if(n==null){
            throw new Exception("Receiver unkown: "+m.getIDTo());
        }
        sendU(m,n.getAddress());
    }

    /** Send message to a well known Address. Ignore the 'to' identifier
     * of the message. Use this method only to send messages to
     * MSDs with a unknown indetifier (but we got the url by other
     * means, such in UPDATE with content messages)  */
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
        System.out.println("Connection to "+to);
        ConnectionStreams s=getConnection(address);
        Connection conn=new Connection(commManager,type,from,to,this,s);
        Message m=new Message((""+type).getBytes(),from,to,Message.CONN);
        m.setEncode(false);
        conn.send(m);
        return conn;
    }

    /** Send a multicast message with UDP protocol. */
    public void sendM(Message m) throws Exception{
        if(multicast==null)
            throw new Exception("No multicasting support");
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

    /** Returns this managers local address */
    public Address getAddress(){
        return local;
    }

    /** Finish this network manager */
    public void finish(){
        if(finished){
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

/** Server in a ethernet network. If makes UDP sockets, so it would be useful
 * for multicasting messages */
class InternetMServer implements Server,Runnable{
    /** Maximum size of messages for a UDP network */
    public final static int SIZE=1024;
    DatagramSocket server=null;
    private String url=null;
    private int port=-1;
    private NetworkManager manager=null;
    private Thread thread;

    /** @param url URL of the connection. If it is a multicast group,
     * join to them. Otherwise, ignored.
     * @param port Port where listen to connections */
    public void start(String url,int port) throws Exception{
        if(manager==null){
            throw new Exception("Manager not defined");
        }
        InetAddress inet=InetAddress.getByName(url);
        if(inet.isMulticastAddress()){
            MulticastSocket m=new MulticastSocket(port);
            m.joinGroup(inet);
            server=m;
        } else{
            server=new DatagramSocket(port);
        }
        this.url=url;
        this.port=port;
        thread=new Thread(this);
        thread.start();
    }

    public void setManager(NetworkManager m){
        manager=m;
    }

    public void run(){
    	System.out.println("Listening UDP in "+server.getLocalPort());
        while(server!=null&&!Thread.interrupted()){
            try{
                // wait for a message
                DatagramPacket p=new DatagramPacket(new byte[SIZE],SIZE);
                server.receive(p);
                try{
                    manager.receive(new ByteArrayInputStream(p.getData()),this);
                } catch(Exception e){
                    e.printStackTrace();
                }
            } catch(Exception e){
                if(!Thread.interrupted()){
                }
            }
        }
    }

    public Connection openConnection(CommManager comm,int type,String idfrom,
                                     String idto) throws Exception{
        throw new Exception("This network does not support connections.");
    }

    public void stop(){
        if(server==null){
            return;
        }
        try{
            if(server!=null){
                thread.interrupt();
                DatagramSocket s=server;
                server=null;
                if(s!=null){
                    s.close();
                }
            }
        } catch(Exception e){
        }
    }

    public void finalize() throws Throwable{
        stop();
        super.finalize();
    }

    public String getURL(){
        return url;
    }

    public int getPort(){
        return port;
    }
}


/** Server in a ethernet network. It makes TCP sockets, so it would be useful
 * for unicast communications */
class InternetUServer implements Server,Runnable{
    ServerSocket server=null;
    private int port=-1;
    private String url=null;
    private NetworkManager manager=null;
    private Socket s=null;
    private Thread thread=null;

    /** @param url URL of the connection.
     * @param url This server claims to be in this url
     * @param port Port where listen to connections. If 0, creates the socket
     * in any free port */
    public void start(String url,int port) throws Exception{
        if(manager==null){
            throw new Exception("Manager not defined");
        }
        // keep in mind a port of number 0 creates the socket in any free port.
        server=new ServerSocket(port);
        this.url=url;
        this.port=server.getLocalPort();
        thread=new Thread(this);
        thread.start();
    }

    public void setManager(NetworkManager m){
        manager=m;
    }

    public void run(){
        while(server!=null&&!Thread.interrupted()){
            s=null;
            System.out.println("Listening TCP in "+server.getLocalPort());
            try{
                s=server.accept();
                manager.receive(s.getInputStream(),this);
            } catch(Exception e){
                if(!Thread.interrupted()){
                }
                try{
                    s.close();
                } catch(Exception ex){}
            }
        }
    }

    public void stop(){
        if(server==null){
            return;
        }
        try{
            if(server!=null){
                thread.interrupt();
                ServerSocket s=server;
                server=null;
                if(s!=null){
                    s.close();
                }
            }
        } catch(Exception e){
        }
    }

    public void finalize() throws Throwable{
        stop();
        super.finalize();
    }

    public Connection openConnection(CommManager comm,int type,String idfrom,
                                     String idto) throws Exception{
        if(s!=null){
            return new Connection(comm,type,idfrom,idto,manager,
                                  new IConnectionStreams(s));
        } else{
            throw new Exception("Socket is null");
        }
    }

    public String getURL(){
        return url;
    }

    public int getPort(){
        return port;
    }
}
