package org.msd.comm;

import java.io.OutputStream;
import java.util.Date;
import java.util.Vector;
import org.apache.log4j.Logger; //@@l

/** Network connection.
 * If two members of an MSD network want to
 * exchange more than one message, they use connections: they are a stream
 * of messages. In order to convert or connect a connection to/into standard
 * streams (such as the ones in java.io), see TransformConnection classes.
 * A connection is always unicast, never multicast.
 * @see TransformConnection
 * @see Message
 * @version $Revision: 1.36 $ $Date: 2005-09-27 16:55:47 $ */
public class Connection{
    static final Logger logger=Logger.getLogger(Connection.class); //@@l
    /** Unregistered type of connection */
    public static final int UNREGISTERED=0;
    /** Connection to get a remote cache matching a template */
    public static final int GET=4;
    /** Connection to get a remote cache not matching a template */
    public static final int GET_NOT=5;
    /** A special connection to use virtual connections and messages
     * inside it. */
    public static final int CONN_BT=11;
    /** Connection to leave the network */
    public static final int LEAVE=6;
    /** Connection to join to the network */
    public static final int JOIN=7;
    /** Connection to use a service in the network */
    public static final int USE=8;
    /** Connection for an UPDATE */
    public static final int UPDATE=9;

    /** The CommManager to encode/decode messages */
    private CommManager comm;
    /** Streams we use to make the connection */
    protected ConnectionStreams s=null;
    /** Identifier of the receiver of the connection */
    private String idto;
    /** Identifier of the remitent of the connection */
    private String idfrom;
    /** Type of connection */
    private int type;
    /** Date this connection was last used */
    protected Date lastUsed=null;
    /** Wether the connection is closed or not */
    boolean closed=false;
    /** The network manager this connection uses */
    protected NetworkManager net;
    /** The thread to manage the connection through listeners.
     * If null, manual calls to receive are needed */
    protected Thread thread=null;
    /** List of listeners of this connection */
    protected Vector listeners=new Vector();

    /** @param comm CommManager to cypher/decypher
     * @param type Type of connection
     * @param idfrom Identifier of the other party of the connection.
     * @param idto Identifier of the receiver of the connectio. If this connection
     * is for this MSD, idto is the identifier of this MSD.
     * @param net The networkManager this connection comes from.
     * @param s The actual streams of the connection */
    public Connection(CommManager comm,int type,String idfrom,String idto,
                      NetworkManager net,ConnectionStreams s){
        this.comm=comm;
        this.net=net;
        this.s=s;
        setType(type);
        setIDs(idfrom,idto);
        lastUsed=new Date();
    }

    /** @param l Add the listener to the list of listeners */
    public void addListener(ConnectionListener l){
        listeners.add(l);
    }

    /** @param l Remove the listener from the list of listeners */
    public void removeListener(ConnectionListener l){
        listeners.remove(l);
    }

    /** Trigger an event.
     * @param m Message responsible of the event. If the event is of type
     * CLOSED, this paramenter is ignored.
     * @param type Type of event.
     * @see ConnectionEvent */
    public void triggerEvent(Message m,int type){
        if(type==ConnectionEvent.CLOSED){
            m=null;
        }
        new TriggerEvent(this,m,type,listeners);
    }

    /** Trigger events in a new thread not to block execution.
     * @todo This thread seems not useful, but we keep them in case
     * the system down not work without...*/
    private class TriggerEvent extends Thread{
        private Message m;
        private int type;
        private Connection c;
        private Vector listeners;
        public TriggerEvent(Connection c,Message m,int type,Vector l){
            this.m=m;
            this.type=type;
            this.c=c;
            listeners=(Vector)l.clone();
            start();
        }

        public void run(){
            ConnectionEvent e=new ConnectionEvent(c,m,type);
            Object o[]=listeners.toArray();
            for(int i=0;i<o.length;i++){
                ConnectionListener l=(ConnectionListener)o[i];
                l.event(e);
            }
            listeners.clear();
        }
    }


    /** Start a new thread reading messages from connection.
     * Use this method only if you can not call to receive yourself: the
     * messages will be received thorugh events, and none should call
     * to received method. If the thread is already running, do nothing.
     */
    public void startThread(){
        if(thread!=null){
            return;
        }
        thread=new Thread("ConnectionThread"){
            public void run(){
                while(!interrupted()&&!closed()){
                    try{
                        receive();
                    } catch(InterruptedException e){
                        // do nothing: it is normal
                    } catch(Exception e){
                        logger.warn("Error while receiving: "+e); //@@l
                        closeConnection();
                    }
                }
            }
        };
        thread.start();
    }

    /** Set identifiers for the connection.
     * @param from Identifier of the sender
     * @param to Identifier of the recipient
     */
    public void setIDs(String from,String to){
        idfrom=from;
        idto=to;
    }

    /** Send a message through the connection.
     * @param m Message to send
     * @throws java.lang.Exception */
    public void send(Message m) throws Exception{
        if(closed){
            throw new Exception("Connection is closed");
        }
        lastUsed=new Date();
        OutputStream out=s.getOutputStream();
        comm.send(m,out,net);
        out.flush();
        triggerEvent(m,ConnectionEvent.SENT);
    }

    /** Sends a byte array to the other side of the connection.
     * @param b Thearray to send
     * @throws Exception If the array can not be sent */
    public void sendBytes(byte[] b) throws Exception{
        send(new Message(b,idto,idfrom,type));
    }

    /** Receives a message from the connection. Blocks until received.
     *
     * Keep in mind you can get a CLOSE message calling to this method
     * if there are virtual connection inside ths one.
     * @return A new message from the other partner. The identifiers
     * are set accordingly to the sender and receiver identifiers,
     * overwritting whatever original identifier of the message.
     * If the connection is closed, return null. If the message was an
     * error, it is returned as well as informed to the listeners.
     * @throws java.lang.Exception
     */
    public Message receive() throws Exception{
        if(closed){
            return null;
        }
        Message m=comm.receive(s.getInputStream(),net);
        if(m.getType()==Message.CLOSE&&m.getVirtual()==null){
            closeConnection();
            m=null;
        } else if(m.getType()==Message.ERROR){
            triggerEvent(m,ConnectionEvent.ERROR);
        } else{
            triggerEvent(m,ConnectionEvent.RECEIVED);
        }
        return m;
    }

    /** Receives a byte array from the other side of the connection.
     * @return A received byte array, and empty array if an error comes from
     * the other side or a null object if the conection is closed.
     * @throws Exception if any network error occurs. */
    public byte[] receiveBytes() throws Exception{
        Message m=receive();
        if(m==null){
            return null;
        } else if(m.getType()==Message.ERROR){
            return new byte[0];
        } else{
            return m.getData();
        }
    }

    /** Gets the type of connection.
     * The type of the connection is just for discriminating connections
     * during arrival. The messages through the connection can be from
     * any type.
     * @return The type of the connection, from the list of constants
     * of Message. */
    public int getType(){
        return type;
    }

    /** Sets the type of the connection.
     * @param t The type of the connection */
    public void setType(int t){
        type=t;
    }

    /** Get the identifier of the MSD which opened the connection.
     * @return The identifier of the MSD wich opened the connection. */
    public String getIDFrom(){
        return idfrom;
    }

    /** Get the identifier of the MSD receiving the connection.
     * @return The unique identifier of the MSD receiving the connection */
    public String getIDTo(){
        return idto;
    }

    /** Close this connection. Send CLOSE message to the other party. Calls
     * to closeConnection at the end. Also Notify about the closing.
     * If the connection is already closed, do nothig.
     * @throws java.lang.Exception If any error occurs */
    public void close() throws Exception{
        if(closed){
            return;
        }
        // send the CLOSE message to the other party
        Message m=new Message(null,this.getIDFrom(),this.getIDTo(),
                              Message.CLOSE);
        if(thread!=null){
            thread.interrupt();
        }
        m.setEncode(false);
        try{
            send(m);
        } catch(Throwable t){
            t.printStackTrace();
            logger.warn("Error while closing: "+t); //@@l
        }
        closeConnection();
        closed=true;
    }

    /** Close the connection for the network. Call this method to close
     * the connection in case you receive a CLOSE message from the other party,
     * or after any error in connection.
     * If the connection is already closed, do nothig.
     * The method should call to 'triggerEvent' with type=CLOSE  */
    public void closeConnection(){
        if(closed){
            return;
        }
        try{
            logger.debug("Closing ConnectionStream "+net.getGenericName()); //@@l
            s.getOutputStream().flush();
            s.close();
        } catch(java.io.IOException e){
            logger.error("Error while closing ConnectionStream: "+e); //@@l
        }
        if(thread!=null){
            synchronized(thread){
                thread.interrupt();
            }
        }
        closed=true;
        triggerEvent(null,ConnectionEvent.CLOSED);
        // after triggering event, remove listeners
        listeners.clear();
    }

    /** Finalize this class closing
     * @throws Throwable If the class can not be finalized (never thrown) */
    public void finalize() throws Throwable{
        if(!closed){
            try{
                close();
            } catch(Exception e){
                logger.warn("Error while finalizing: "+e); //@@l
                closeConnection();
            }
        }
        super.finalize();
    }

    /** Returns the number of secons the connections has not be used.
     * @return The number of secons this connection has been not used. */
    public int lastUsed(){
        return(int)((new Date()).getTime()-lastUsed.getTime())/1000;
    }

    /** Return wether the connection has been closed.
     * @return True is the connection was closed */
    public boolean closed(){
        return closed;
    }
}


/** This class represents a loop back connection */
class LocalConnection extends Connection{
    /** The queue of the incoming messages of this connection */
    private Message[] messages;
    /** Object to be notified when a new message arrives */
    private Object newMessage;
    /** indexMessage is next index inside the queue of the message to return
     * when someone calls to receive().
     * nextMessage is the index inside the queue where save a message when
     * someone calls to newMessage().
     */
    private int indexMessage,nextMessage;
    /** The other extreme of the local connection */
    private LocalConnection other=null;

    /** Constructs a local connection. The queue of incoming
     * messages is set to 5. The network of the loopback connection is null.
     * @param id The identifier of the sender an receiver.
     * @param type Type of this virtual connection
     */
    public LocalConnection(int type,String id){
        super(null,type,id,id,null,null);
        messages=new Message[5];
        for(int i=0;i<5;i++){
            messages[i]=null;
        }
        newMessage=new Object();
        indexMessage=0;
        nextMessage=0;
    }

    /** @param o The other extreme of the local connection. The local
     * connection is useless until the other extreme has been established. */
    public void setOther(LocalConnection o){
        other=o;
    }

    /** Send the messages through the actual connection.
     * @param m The message to send.
     * @throws java.lang.Exception If the message can not be thrown.
     */
    public void send(Message m) throws Exception{
        if(other==null){
            throw new Exception("This local connection is not configured!");
        }
        other.newMessage(m);
    }

    /** Receives a new message. This method blocks until a new message
     * arrives.
     * @throws java.lang.Exception After any error.
     * @return A received Mesage, or null if the connection is closed.
     */
    public Message receive() throws Exception{
        if(closed){
            return null;
        }
        if(messages[indexMessage]==null){
            synchronized(newMessage){
                newMessage.wait();
            }
        }
        Message m=messages[indexMessage];
        messages[indexMessage]=null;
        indexMessage=(indexMessage+1)%messages.length;
        lastUsed=new Date();

        if(m.getType()==Message.CLOSE){
            closeConnection();
            return null;
        } else if(m.getType()==Message.ERROR){
            triggerEvent(m,ConnectionEvent.ERROR);
        } else{
            triggerEvent(m,ConnectionEvent.RECEIVED);
        }
        return m;
    }

    /** Save a new message in the queue of incoming messages.
     * @param m A new message arrived, will be returned by receive()
     * @throws java.lang.Exception If the incoming queue is full.
     */
    public void newMessage(Message m) throws Exception{
        if(messages[nextMessage]!=null){
            throw new Exception("Messages queue is full");
        }
        messages[nextMessage]=m;
        nextMessage=(nextMessage+1)%messages.length;
        synchronized(newMessage){
            newMessage.notify();
        }
    }

    /** Actually close the connection. */
    public void closeConnection(){
        if(closed){
            return;
        }
        logger.debug("Closing local connection"); //@@l
        if(thread!=null){
            synchronized(thread){
                thread.interrupt();
            }
        }
        closed=true;
        triggerEvent(null,ConnectionEvent.CLOSED);
        listeners.clear();
    }
}


/** This class represent a virtual connection inside another connection.
 * Useful for reusing a connection for other connections. */
class VirtualConnection extends Connection{
    /** Actual connection to send the messages through */
    private Connection con;
    /** Identifier of this virtual connection */
    private String virtual;
    /** The queue of the incoming messages of this connection */
    private Message[] messages;
    /** Object to be notified when a new message arrives */
    private Object newMessage;
    /** indexMessage is next index inside the queue of the message to return
     * when someone calls to receive().
     * nextMessage is the index inside the queue where save a message when
     * someone calls to newMessage().
     */
    private int indexMessage,nextMessage;

    /** Constructs a new virtual connection. The queue of incoming
     * messages is set to 5.
     * @param con Actual connection to send messages (not receiving!)
     * @param idto Identifier of the sender
     * @param idfrom Identifier of the receiver
     * @param type Type of this virtual connection
     * @param id Identifier of this virtual connection
     * @param net The network of this connection.
     */
    public VirtualConnection(int type,String idfrom,String idto,
                             NetworkManager net,Connection con,String id){
        super(null,type,idfrom,idto,net,null);
        virtual=id;
        this.con=con;
        messages=new Message[5];
        for(int i=0;i<5;i++){
            messages[i]=null;
        }
        newMessage=new Object();
        indexMessage=0;
        nextMessage=0;
    }

    /** Sends the messages through the actual connection.
     * Also sets the virtual parameter of the message.
     * @param m The message to send.
     * @throws java.lang.Exception If the message can not be sent.
     */
    public void send(Message m) throws Exception{
        if(closed){
            throw new Exception("Connection is closed");
        }
        lastUsed=new Date();
        m.setVirtual(virtual);
        con.send(m);
        triggerEvent(m,ConnectionEvent.SENT);
    }

    /** Receives a new message. This method blocks until a new message
     * is arrived.
     * @return A received message, or null if the connection is closed.
     * @throws java.lang.Exception After any error.
     */
    public Message receive() throws Exception{
        if(closed){
            return null;
        }
        if(messages[indexMessage]==null){
            synchronized(newMessage){
                newMessage.wait();
            }
        }
        Message m=messages[indexMessage];
        messages[indexMessage]=null;
        indexMessage=(indexMessage+1)%messages.length;
        lastUsed=new Date();

        if(m.getType()==Message.CLOSE){
            closeConnection();
            return null;
        } else if(m.getType()==Message.ERROR){
            triggerEvent(m,ConnectionEvent.ERROR);
        } else{
            triggerEvent(m,ConnectionEvent.RECEIVED);
        }
        return m;
    }

    /** In virtual connections, someone is reading from the actual connection,
     * so starting a new thread to read messages not only is useless, but painful.
     */
    public void newThread(){
        // do nothing
    }

    /** Save a new message in the queue of incoming messages.
     * @param m A new message arrived, will be returned by receive()
     * @throws java.lang.Exception If the incoming queue is full.
     */
    public void newMessage(Message m) throws Exception{
        if(messages[nextMessage]!=null){
            throw new Exception("Messages queue is full");
        }
        messages[nextMessage]=m;
        nextMessage=(nextMessage+1)%messages.length;
        synchronized(newMessage){
            newMessage.notify();
        }
    }

    /** Closes the connection.
     * @throws java.lang.Exception After any error. */
    public void close() throws Exception{
        if(closed){
            return;
        }
        // send the CLOSE message to the other party
        Message m=new Message(null,this.getIDFrom(),this.getIDTo(),
                              Message.CLOSE);
        if(thread!=null){
            thread.interrupt();
        }
        m.setEncode(false);
        try{
            send(m);
        } catch(Exception e){
            logger.warn("Error while closing: "+e); //@@l
        }
        closeConnection();
        closed=true;
    }

    /** Actually close the connection, no matter of the consequences.
     * Use this method only after an error. */
    public void closeConnection(){
        if(closed){
            return;
        }
        logger.debug("Closing virtual connection "+virtual); //@@l
        if(thread!=null){
            synchronized(thread){
                thread.interrupt();
            }
        }
        closed=true;
        triggerEvent(null,ConnectionEvent.CLOSED);
        listeners.clear();
    }
}
