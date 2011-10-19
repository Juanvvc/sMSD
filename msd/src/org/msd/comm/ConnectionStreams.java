package org.msd.comm;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import javax.microedition.io.StreamConnection;

/** Save a couple of streams in a single class.
 * This class stores a java.io.InputStream and java.io.OutputStream in a
 * single class.
 * @version $Revision: 1.5 $ */
public class ConnectionStreams{
    private InputStream in;
    private OutputStream out;
    private Connection con=null;
    /** @param in The input stream to store
     * @param out The output stream to store
     * @throws NullPointerException If in or out are null */
    public ConnectionStreams(InputStream in,OutputStream out){
        if(in==null||out==null){
            throw new NullPointerException();
        }
        this.in=in;
        this.out=out;
    }

    /** @param con The connection to be associated with this streams:
     * when the streams get be closed, the connection too.
     */
    public void setConnection(Connection con){
        this.con=con;
    }

    public OutputStream getOutputStream(){
        return out;
    }

    public InputStream getInputStream(){
        return in;
    }

    /** Close both streams (and connection, if it is associated).
     * This method is preferred to only close an stream with its inner
     * method.
     * @throws IOException */
    public void close() throws IOException{
        in.close();
        out.close();
        if(con!=null&&!con.closed()){
            try{
                con.close();
            }catch(Exception e){
                con.closeConnection();
            }
        }
    }
}


/** Open and close standard streams from a Bluetooth connection */
class BTConnectionStreams extends ConnectionStreams{
    private StreamConnection s;
    /** @param s The Bluetooth connection to get the input and putput streams.
     * @throws IOException If the streams couldn't be taken.
     */
    public BTConnectionStreams(StreamConnection s) throws IOException{
        super(s.openInputStream(),s.openOutputStream());
        this.s=s;
    }

    public void close() throws IOException{
        super.close();
        s.close();
    }
}


/** Open and close standard streams from an Internet Socket */
class IConnectionStreams extends ConnectionStreams{
    private java.net.Socket s;
    /** @param s The socket connection to get the input and putput streams.
     * @throws IOException If the streams couldn't be taken.
     */
    public IConnectionStreams(java.net.Socket s) throws IOException{
        super(s.getInputStream(),s.getOutputStream());
        this.s=s;
    }

    public void close() throws IOException{
        super.close();
        s.close();
    }
}
