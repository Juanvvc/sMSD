package org.msd.comm;

import java.net.*;
import org.msd.comm.NetworkManager;
import org.apache.log4j.Logger; //@@l
import java.io.*;
import javax.microedition.io.StreamConnectionNotifier;
import javax.bluetooth.DiscoveryAgent;
import javax.microedition.io.Connector;
import javax.bluetooth.LocalDevice;
import javax.microedition.io.StreamConnection;

/** Server in a network.
 * A listening server waits for remote connections (at the network level, i.e.,
 * RFCOMM or TCP connections, or UDP messages) and inform to a network manager
 * of the streams received.
 * @version $Revision: 1.22 $ */
interface Server{
    /** Starts the server in url and port.
     * @param url The meaning of this parameter depends on the implementers
     * @param port The meaning of this parameter depends on the implementers
     * @throws java.lang.Exception If the server can not start */
    public void start(String url,int port) throws Exception;

    /** Stops the server. The server will be no longer used.
     * Calling again to start is not guaranteed to work.
     * If the server is yet stopped, a new calling do nothing */
    public void stop();

    /** @param m The network manager to receive incoming connections. */
    public void setManager(NetworkManager m);

    /** Opens a connection from the sender of the last message to this MSD,
     * for connecting MSD 'to' and 'from' with messages 'type'
     * @param com The CommManager to use with the connection
     * @param type The type of connection
     * @param idfrom The identifier of the remote MSD
     * @param idto The identifier of the receiving MSD
     * @throws java.lang.Exception If the connection can not be opened
     * @return A new connection */
    public Connection openConnection(CommManager com,int type,String idfrom,
                                     String idto) throws Exception;

    /** @return The url to connect to this server */
    public String getURL();

    /* @return The port to connect to this server */
    public int getPort();
}


/** Server in a ethernet network. If makes UDP sockets, so it would be useful
 * for multicasting messages */
class InternetMServer implements Server,Runnable{
    /** Maximum size of messages for a UDP network */
    public final static int SIZE=1024;
    private Logger logger=Logger.getLogger(InternetMServer.class); //@@l
    DatagramSocket server=null;
    private String url=null;
    private int port=-1;
    private NetworkManager manager=null;
    private Thread thread;

    /** @param url URL of the connection. If it is a multicast group,
     * join to them. Otherwise, ignored.
     * @param port Port where listen to connections
     * @throws java.lang.Exception If the server can not be started */
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
        thread=new Thread(this,"MulticastListener");
        thread.start();
    }

    public void setManager(NetworkManager m){
        manager=m;
    }

    public void run(){
        boolean interrupted=false;
        while(server!=null&&!interrupted){
            interrupted=false;
            try{
                logger.info("Listening to multicast port "+server.getLocalPort()); //@@l
                // wait for a message
                DatagramPacket p=new DatagramPacket(new byte[SIZE],SIZE);
                server.receive(p);
                try{
                    manager.receive(new ByteArrayInputStream(p.getData()),this);
                } catch(Exception e){
                    logger.warn("Error in multicast comm: "+e.toString()); //@@l
                    e.printStackTrace();
                }
            } catch(Exception e){
                if(!Thread.interrupted()){
                    interrupted=false;
                    logger.warn("Error in multicast comm: "+e.toString()); //@@l
                } else{
                    interrupted=true;
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
        logger.debug("Closing InternetMServer"); //@@l
        try{
            if(server!=null){
                thread.interrupt();
                DatagramSocket s=server;
                server=null;
                if(s!=null){
                    s.close();
                }
            }
            logger.info("InternetMServer closed"); //@@l
        } catch(Exception e){
            logger.warn("InternetMServer not closed: "+e); //@@l
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
    private Logger logger=Logger.getLogger(InternetUServer.class); //@@l
    ServerSocket server=null;
    private int port=-1;
    private String url=null;
    private NetworkManager manager=null;
    private Socket s=null;
    private Thread thread=null;

    /** @param url This server claims to be in this url
     * @param port Port where listen to connections
     * @throws java.lang.Exception If the server can nor be started */
    public void start(String url,int port) throws Exception{
        if(manager==null){
            throw new Exception("Manager not defined");
        }
        server=new ServerSocket(port);
        this.url=url;
        this.port=port;
        thread=new Thread(this,"UnicastListener");
        thread.start();
    }

    public void setManager(NetworkManager m){
        manager=m;
    }

    public void run(){
        boolean interrupted=false;
        while(server!=null&&!interrupted){
            interrupted=false;
            s=null;
            try{
                logger.info("Listening to unicast port "+server.getLocalPort()); //@@l
                s=server.accept();
                manager.receive(s.getInputStream(),this);
                logger.debug("Unicast connection from "+s.getInetAddress()); //@@l
            } catch(Exception e){
                if(!Thread.interrupted()){
                    logger.warn("Error in unicast conn: "+e.toString()); //@@l
                } else{
                    interrupted=true;
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
        logger.debug("Closing InternetUServer"); //@@l
        try{
            if(server!=null){
                thread.interrupt();
                ServerSocket s=server;
                server=null;
                if(s!=null){
                    s.close();
                }
            }
            logger.info("InternetUServer closed"); //@@l
        } catch(Exception e){
            logger.warn("InternetUServer not closed: "+e); //@@l
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


/** Bluetooth server */
class BluetoothServer implements Server,Runnable{
    private StreamConnectionNotifier server=null;
    private String url=null;
    private NetworkManager manager=null;
    private static final Logger logger=Logger.getLogger(BluetoothServer.class); //@@l
    private InputStream in;
    private OutputStream out;
    private StreamConnection con=null;
    private Thread thread=null;

    /** Constructor.
     * @param uuid UUID of this server
     * @param mode Ignored at this moment
     * @throws java.lang.Exception If the server can not be started */
    public void start(String uuid,int mode) throws Exception{
        if(manager==null){
            throw new Exception("Manager now known");
        }
        try{
            LocalDevice local=LocalDevice.getLocalDevice();
            local.setDiscoverable(DiscoveryAgent.GIAC);
            server=(StreamConnectionNotifier)Connector.open(
                    "btspp://localhost:"+uuid.toString()+";name=MSDBTServer");
            // mode==unauthorized, unencrypted. We are in charge of
            // authorization and encryption...
            url=local.getRecord(server).getConnectionURL(javax.bluetooth.
                    ServiceRecord.NOAUTHENTICATE_NOENCRYPT,false);
            thread=new Thread(this,"TCPListener");
            thread.start();
        } catch(Exception e){
            if(server!=null){
                server.close();
            }
            //e.printStackTrace(); //@@l
            throw e;
        }
    }

    public void run(){
        boolean interrupted=false;
        while(server!=null&&!interrupted){
            interrupted=false;
            con=null;
            try{
                logger.info("Listening"); //@@l
                con=server.acceptAndOpen();
                logger.debug("Connected"); //@@l
                in=con.openInputStream();
                out=con.openOutputStream();
                manager.receive(in,this);
            } catch(Exception e){
                if(!Thread.interrupted()){
                    logger.warn("Error in communication: "+e.toString()); //@@l
                } else{
                    interrupted=true;
                }
                if(con!=null){
                    try{
                        out.close();
                        in.close();
                        con.close();
                    } catch(Exception ex){}
                }
            }
        }
    }

    public Connection openConnection(CommManager comm,int type,String idfrom,
                                     String idto) throws Exception{
        if(con!=null){
            return new Connection(comm,type,idfrom,idto,manager,
                                  new BTConnectionStreams(con));
        } else{
            throw new Exception("Connection is null");
        }
    }

    /** @return The URL to connect with this server. */
    public String getURL(){
        return url;
    }

    /** @return The port to connect to this server */
    public int getPort(){
        return -1;
    }

    /** @param net The manager listening to remote connections */
    public void setManager(NetworkManager net){
        manager=net;
    }

    /** Finalize this class, closing the server */
    public void stop(){
        /* If you use Impronto's libraries, comment every line
                 in this method. If you use Avetana's, uncomment them.
                 Read the description of this problem in the README.
         */
        /*if(server==null){
            return;
                 }
                 logger.debug("Closing BTMServer"); //@@l
                 try{
            thread.interrupt();
            StreamConnectionNotifier s=server;
            server=null;
            if(s!=null){
                s.close();
            }
            logger.info("BTMServer finalized"); //@@l
                 } catch(Exception e){
            logger.warn("BTMServer not finalized: "+e); //@@l
                 }*/
    }

    public void finalize() throws Throwable{
        stop();
        super.finalize();
    }
}
