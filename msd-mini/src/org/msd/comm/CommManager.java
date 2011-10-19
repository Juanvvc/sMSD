package org.msd.comm;

import java.io.*;
import org.msd.comm.Message;
import org.msd.cache.StringTokenizer;

import java.util.Enumeration;
import java.util.Hashtable;

/** This class implements the CommManager layer of the protocol stack.
 *
 * Converts a message to a byte array, wrapping it into an HTTP like
 * protocol and encrypting the content. While receiving, unwraps HTTP
 * data and decrypts the content.
 *
 * @version $Revision: 1.7 $
 */
public class CommManager{
    /** End-of-line string. For HTTP protocol, carriage-return+new-line */
    public final static String EOL="\r\n";

    public CommManager(){
    }


    /** Receive a message.
     * Read the input stream and returns the message.
     * @param ins The stream containing the message to receive
     * @param net NetworkManager the message was received through
     * @return The message or null if the input was not properly formed.
     * @throws Exception Any internal exception (particularly, IOException) */
    public Message receive(InputStream ins,NetworkManager net) throws Exception{
        Message m=new Message();
        // get the first line and prove wether it is well formed
        String firstline=readLine(ins,128);
        // if it's not a petition, return null
        if(!firstline.equals("MSD")){
            throw new Exception("HTTP protocol bad defined: "+firstline);
        }
//        boolean enc=false;
        // read the header until empty line
        int contentlength=0;
        while(true){
            String line=readLine(ins,128);
            if(line.length()==0){
                break;
            }
            StringTokenizer st=new StringTokenizer(line,":");
            String header=st.nextToken();
            header=header.toLowerCase();
            m.setAttribute(header,st.nextToken());
        }
        contentlength=Integer.parseInt(m.getAttribute("length"));

        // read msg of content lengh bytes
        byte[] b=new byte[contentlength];
        int read=0;
        while(read<contentlength){
            read+=ins.read(b,read,contentlength-read);
        }
        if(b.length==0){
            b=null;
        }

        m.setData(b);
        return m;
    }

    /** Reads a line from an InputStream.
     * We are dealing with binary streams, so char streams as BufferedReader
     * are not desiderable: we use our method instead.
     * "End-of-line" is defined in a HTTP-manner: \r\n or end-of-stream.
     * @param in InputStream to read
     * @param max Max number of bytes in the stream for a line.
     * @return The read line.
     * @throws java.io.IOException If there was an exception reading the
     * line or the line is longer then max.
     */
    public static String readLine(InputStream in,int max) throws IOException{
        byte[] b=new byte[max];
        int read=0;
        boolean ok=false;
        for(read=0;read<max;read++){
            int r=in.read();
            if(r==-1){
                break;
            }
            byte rb=(byte)r;
            b[read]=rb;
            if(rb=='\n'&&ok){
                break;
            } else if(rb=='\r'&&!ok){
                ok=true;
            } else{
                ok=false;
            }
        }
        if(read>=max){
            throw new IOException("Line too long: "+read);
        }
        return new String(b,0,(ok?read-1:read));
    }

    /** Send a message in HTTP 1.0-like format to an output stream.
     * The HTTP message just have two headers: content-length and
     * x-msdmessagetyp. The very first line of the output is
     * GET /msd HTTP/1.0, if the message is not a response, and
     * HTTP/1.0 200 OK if it is.
     * @param m Message to send
     * @param out Send the message in this stream
     * @param net NetworkManager the message will be sent through
     * @throws java.lang.Exception */
    public void send(Message m,OutputStream out,NetworkManager net) throws
            Exception{
        // get the content length
        int contentlength;
        byte b[]=m.getData();
        if(m.getEncode()){
            //b=this.encode(b,net);
        }
        if(b!=null){
            contentlength=b.length;
        } else{
            contentlength=0;
        }

        // Creates the header
        StringBuffer send=new StringBuffer("MSD").append(EOL);
        Hashtable h=m.getAttributes();
        for(Enumeration en=h.keys(); en.hasMoreElements();){
            String v=(String)en.nextElement();
            send.append(v).append(":").append(h.get(v)).append(EOL);
        }
        send.append("length:").append(contentlength).append(EOL).append(EOL);
        // writes the header
        out.write(send.toString().getBytes());

        // writes the content
        if(b!=null){
            out.write(b);
        }
        out.flush();
    }
}
