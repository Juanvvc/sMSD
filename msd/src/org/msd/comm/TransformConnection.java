/* This file contains classes to manage connections.
 They all are used through methods of TransformConnection: do not use them
 individually if there is no a good reason.
 This classes are protected: only usable inside the package */

package org.msd.comm;

import java.io.InputStream;
import java.io.IOException;

import org.apache.log4j.Logger; //@@l
import java.io.OutputStream;

/** Special class for management of connections.
 * Transform connections from and to streams and connect them.
 * @version $Revision: 1.16 $
 */
public class TransformConnection{
    /** Transform a connection into a couple of streams.
     * @param con An actual connection not closed.
     * @return The trasnformed streams
     * @htrows NulPointerException If con is null
     */
    public static ConnectionStreams connectionToStreams(Connection con){
        if(con==null){
            throw new NullPointerException();
        }
        return new ConnectionStreams(new MInputStream(con),
                                     new MOutputStream(con));
    }

    /** Connect a connection and a couple of streams.
     * @param con An actual connection not closed.
     * @param cs A couple of streams connected.
     * @htrows NulPointerException If con or cs are null
     */
    public static void connect(Connection con,ConnectionStreams cs){
        if(con==null||cs==null){
            throw new NullPointerException();
        }
        new StreamsToConnection(con,cs);
    }

    /** Connect two connections.
     * Send every message received from one to the other.
     * @param from The original connection
     * @param to Connect 'from' to this one.
     * Usually the order is not important.
     * @htrows NulPointerException If from or to are null */
    public static void connect(Connection from,Connection to){
        if(from==null||to==null){
            throw new NullPointerException();
        }
        ConnectionThread ct=new ConnectionThread();
        ct.from=from;
        ct.to=to;
        Thread t=new Thread(ct,"ConnectionToConnection");
        t.start();
    }
}


/** This class converts from two Streams to a connection.
 * Read data from InputStream and send them through the connection. Write
 * the data arrived through the connection to an OutputStream.
 */
class StreamsToConnection implements ConnectionListener,Runnable{
    private static final Logger logger=Logger.getLogger(StreamsToConnection.class); //@@l
    /** The converted connection */
    private Connection con;
    /** The ConnectionStreams to be converted into a Connection */
    ConnectionStreams cs;
    /** @param con An existing connection. The startThread will be called, so
     * the receive method can not be used on this connection.
     * @param cs An existing couple of streams.
     */
    public StreamsToConnection(Connection con,ConnectionStreams cs){
        this.con=con;
        this.cs=cs;
        cs.setConnection(con);
        con.startThread();
        con.addListener(this);
        Thread t=new Thread(this,"StreamsToConnection");
        t.start();
    }

    /**  @param e The event received from the connection.   */
    public void event(ConnectionEvent e){
        // if the message is an error, just warn
        Message m=e.getMessage();
        switch(e.getType()){
        case ConnectionEvent.ERROR:
            logger.warn("An error from the connection: "+new String(m.getData())); //@@l
            break;
            // if the connection get closed, close the streams as well.
        case ConnectionEvent.CLOSED:
            try{
                con.removeListener(this);
                cs.close();
            } catch(IOException ex){
                logger.error("Error while closing streams: "+ex); //@@l
            }
            break
                    ;
        case ConnectionEvent.RECEIVED:

            // if we got a new message, write in the OuputStream.
            try{

                OutputStream out=cs.getOutputStream();
                out.write(m.getData());
                out.flush();
            } catch(IOException ex){
                logger.error("Error while writing: "+ex); //@@l
            }
            break
                    ;
        }
    }

    /** Read data from the InputStream and send as messages */
    public void run(){
        // buffer to read data
        byte[] buffer=new byte[1024];
        while(!con.closed()){
            Message m=new Message(null,con.getIDTo(),con.getIDTo(),con.getType());
            try{
                int r=cs.getInputStream().read(buffer);
                // if e-o-s, close the connection
                if(r==-1){
                    con.close();
                } else{
                    // else send data through the connection
                    byte[] r2=new byte[r];
                    System.arraycopy(buffer,0,r2,0,r);
                    m.setData(r2);
                    con.send(m);
                }
            } catch(Exception e){
                logger.error("Error while reading: "+e); //@@l
                try{
                    m.setData(e.toString().getBytes());
                    m.setType(Message.ERROR);
                    con.send(m);
                } catch(Exception e2){
                    logger.error("Error while informing about error: "+e2); //@@l
                    con.removeListener(this);
                    con.closeConnection();
                }
            }
        }
    }
}


/** Convert a connection into an InputStream.
 *
 * This class does not overwrite reset, mark and markSupported, so the
 * first one throws always an IOException, the second does nothing and the
 * third one returns false. skip method is not overwriten, neither. */
class MInputStream extends InputStream implements ConnectionListener{
    private static final Logger logger=Logger.getLogger(MInputStream.class); //@@l
    /** The buffer we will use */
    private byte[] buffer;
    /** The number of bytes in the buffer */
    private int now=0;
    /** If the stream has arrived to the end-of-stream */
    private boolean eos=false;
    /** The actual connection */
    private Connection con;
    /** Construct a stream with a buffer size of 4096.
     * @param c The actual connection.
     */
    public MInputStream(Connection c){
        this(c,4096);
    }

    /** Construct an stream over a connection.
     * @param c The actual connection. startThread method will be called, so
     * this connection can not be shared with other object reading messages.
     * @param size Buffer size of the connection. If the buffer is not
     * enought to save incoming bytes, the new bytes will be descarded silently.
     */
    public MInputStream(Connection c,int size){
        con=c;
        con.startThread();
        con.addListener(this);
        buffer=new byte[size];
        now=0;
    }

    /** @param e An event from the connection. */
    public void event(ConnectionEvent e){
        Message m=e.getMessage();

        switch(e.getType()){
        case ConnectionEvent.ERROR:
            logger.warn("An error from the connection: "+new String(m.getData())); //@@l
            break;
        case ConnectionEvent.CLOSED:
            con.removeListener(this);
            eos=true;
            break;
        case ConnectionEvent.RECEIVED:
            byte[] r=e.getMessage().getData();
            synchronized(buffer){
                int w=Math.min(r.length,buffer.length-now);
                if(w>buffer.length-now){
                    logger.warn("Discarding "+(buffer.length-now-w)+ //@@l
                                " bytes!"); //@@l
                }
                // copy the message to the buffer... discarding
                // bytes if needed
                System.arraycopy(r,0,buffer,now,w);
                now+=w;
                buffer.notify();
            }
        }
    }

    public int available() throws IOException{
        return now;
    }

    public int read() throws IOException{
        if(eos){
            return-1;
        }
        byte b[]=new byte[1];
        read(b,0,1);
        return b[0];
    }

    public int read(byte[] b) throws IOException{
        return read(b,0,b.length);
    }

    public int read(byte[] b,int off,int len) throws IOException{
        if(b==null){
            throw new NullPointerException();
        }
        if(len==0){
            return 0;
        }
        if(eos){
            return-1;
        }
        if(now==0){
            synchronized(buffer){
                try{
                    buffer.wait();
                } catch(Exception e){
                    throw new IOException("Error while waiting: "+e);
                }
            }
        }
        int r=0;
        synchronized(buffer){
            // return only as much bytes as read
            if(now<=len){
                System.arraycopy(buffer,0,b,off,now);
                r=now;
                now=0;
            } else{
                // return just len bytes and move the others to the begining
                // of the buffer
                System.arraycopy(buffer,0,b,off,len);
                r=len;
                for(int i=len;i<now;i++){
                    buffer[i-len]=buffer[i];
                }
                now=now-len;
            }
        }
        return r;
    }

    public void close() throws IOException{
        try{
            con.close();
        } catch(Exception e){
            con.closeConnection();
            throw new IOException("Error while closing: "+e);
        }
    }
}


/** This class convert an OutputStream into a Connection */
class MOutputStream extends OutputStream{
    private Connection con;
    private byte[] buffer;
    /** length of the buffer currently used */
    private int now;
    /** Creates an MOutputStream with a buffer of 4096 bytes.
     * @param con The original connection.
     */
    public MOutputStream(Connection con){
        this(con,4096);
    }

    /** Creates an MOutputStream,
     * @param con The connection this stream will actually use.
     * @param size The size of the internal buffer.
     * @throws ArrayIndexOutOfBoundsException if size<2
     */
    public MOutputStream(Connection con,int size){
        if(size<2){
            throw new ArrayIndexOutOfBoundsException();
        }
        this.con=con;
        buffer=new byte[size];
        now=0;
    }

    /** Close the stream and the connection.
     * Before closing, do a flushing.
     * @throws IOException If any error. */
    public void close() throws IOException{
        if(con.closed()){
            return;
        }
        try{
            flush();
            con.close();
        } catch(Exception e){
            // if any error, try unconditional closing
            con.closeConnection();
            // and throw exception
            throw new IOException("Error while closing: "+e);
        }
    }

    /** Send the data stored in the buffer.
     * @throws IOException If any errors
     */
    public void flush() throws IOException{
        if(now>0){
            try{
                synchronized(buffer){
                    byte[] b=new byte[now];
                    System.arraycopy(buffer,0,b,0,now);
                    Message m=new Message(b,con.getIDTo(),con.getIDFrom(),
                                          con.getType());
                    con.send(m);
                    now=0;
                }
            } catch(Exception e){
                throw new IOException("Error while sending: "+e);
            }
        }
    }

    /** Equivalent to write(b,0,b.length());
     * @param b The byte array to send
     * @throws IOException If any error
     */
    public void write(byte[] b) throws IOException{
        write(b,0,b.length);
    }

    /** Store the byte array in the buffer.
     * If the buffer gets full, or the array b is greater that the
     * remaining size, do flushings.
     * @param b The byte array containing the data to send.
     * @param off The offset of the data  to send.
     * @param len The length of the data to send.
     * @throws IOException If any error
     */
    public void write(byte[] b,int off,int len) throws IOException{
        // the exceptions from the parent class description
        if(b==null){
            throw new NullPointerException();
        }
        if(off<0||len<0||off+len>b.length){
            throw new IndexOutOfBoundsException();
        }

        // if the size is grather than the buffer size, cut b in pieces.
        if(len>buffer.length){
            byte b2[]=new byte[buffer.length];
            int w=0;
            while(w<len){
                int wn=Math.min(len-w,buffer.length);
                System.arraycopy(b,off+w,b2,0,wn);
                write(b2,0,wn);
                w+=wn;
            }
            return;
        }
        // from here down len<=buffer.length

        // if the remainig size is lesser than length, do a flushing
        if(buffer.length-now<len){
            flush();
        }
        // store array in buffer
        synchronized(buffer){
            System.arraycopy(b,off,buffer,now,len);
            now+=len;
        }
    }

    /** Write a single byte to the buffer.
     * @param b The single byte to write. Only use the less significant bits.
     * @throws An exception if any error occurs.
     */
    public void write(int b) throws IOException{
        byte[] ba=new byte[]{(byte)(b&0xff)};
        write(ba,0,1);
    }
}


/** Connect two connections: send the messages from 'from' to 'to'.
 * Assure the connection is full-duplex */
class ConnectionThread implements Runnable,ConnectionListener{
    Connection from;
    Connection to;
    public void run(){
        from.addListener(this);
        to.addListener(this);
        from.startThread();
        to.startThread();
    }

    public void event(ConnectionEvent e){
        // take in c1 the source of the event and in c2 the other
        Connection c1,c2;
        c1=from;
        c2=to;
        if(e.getSource()==to){
            c1=to;
            c2=from;
        }
        try{
            Message m=e.getMessage();
            // other message (ERROR messages included):
            switch(e.getType()){
            case ConnectionEvent.CLOSED:
                c1.removeListener(this);
                c2.removeListener(this);
                if(!c2.closed()){
                    c2.close();
                }
                from=null;
                to=null;
                break;
            case ConnectionEvent.RECEIVED:
                c2.send(m);
                break;
            }
        } catch(Exception ex){
            // after any error, try to inform to the other party and close
            // connections.
            from.removeListener(this);
            to.removeListener(this);
            Message m=new Message(ex.toString().getBytes(),c2.getIDFrom(),
                                  c2.getIDTo(),Message.ERROR);
            try{
                c2.send(m);
                c2.close();
            } catch(Exception ex2){
            }
            try{
                c1.close();
            } catch(Exception ex2){
            }
            from.closeConnection();
            to.closeConnection();
            from=null;
            to=null;
        }
    }
}
