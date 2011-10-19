package org.msd.comm;

import java.io.*;
import org.msd.comm.Message;
import org.apache.log4j.Logger; //@@l

import javax.crypto.*;
import java.security.*;

import java.util.Hashtable;

/** This class implements the CommManager layer of the protocol stack.
 *
 * Converts a message to a byte array, wrapping it into an HTTP like
 * protocol and encrypting the content. While receiving, unwraps HTTP
 * data and decrypts the content.
 *
 * @version $Revision: 1.20 $
 * @todo Use HMAC and not MessageDiggest in a similiar way to Ciphers
 * since the Mac object must be joined to a network. To create an object
 * Mac mac=Mac.getInstance("HmacSHA1");
 * mac.init();
 * byte[] macbytes=mac.doFinal(message);
 */
public class CommManager{
    private static final Logger logger=Logger.getLogger(CommManager.class); //@@l
    /** End-of-line string. For HTTP protocol, carriage-return+new-line */
    public final static String EOL="\r\n";

    /** Hashtable of ciphers.
     * The key is the generic name of the network the message will be sent through.
     * The object is a Cipher */
    private java.util.Hashtable ciphers=null;
    /** Hashtable of ciphers.
     * The key is the generic name of the network the message will be received through.
     * The object is a Cipher */
    private java.util.Hashtable deciphers=null;
    /** The object to calculate HMACs */
    private MessageDigest hasher=null;

    public CommManager(){
        ciphers=new Hashtable();
        deciphers=new Hashtable();
        try{
            hasher=MessageDigest.getInstance("MD5");
        } catch(Exception e){
            logger.error(e.toString()); //@@l
        }
    }

    /** Receives a message.
     * Read the input stream and returns the message.
     * @param ins The stream containing the message to receive
     * @param net NetworkManager the message was received through
     * @return The message or null if the input was not properly formed.
     * @throws Exception Any internal exception (particularly, IOException)
     * @todo Add a flag in message to not decode the message in this method,
     * but with a explicit call to decode() not to decode the message after
     * being neccesary */
    public Message receive(InputStream ins,NetworkManager net) throws Exception{
        Message m=new Message();
        // get the first line and prove wether it is well formed
        String firstline=readLine(ins,128);
        // if it's not a petition, return null
        if(!firstline.equals("MSD")){
            throw new Exception("HTTP protocol bad defined: "+firstline);
        }
        boolean enc=false;
        // read until empty line
        int contentlength=0;
        while(true){
            String line=readLine(ins,128);
            if(line.length()==0){
                break;
            }
            java.util.StringTokenizer st=new java.util.StringTokenizer(
                    line,":",false);
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

        m.setData(enc?decode(b,net):b);
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
        hash(m);

        // get the content length
        int contentlength;
        byte b[]=m.getData();
        if(m.getEncode()){
            b=this.encode(b,net);
        }
        if(b!=null){
            contentlength=b.length;
        } else{
            contentlength=0;
        }

        // Creates the header
        StringBuffer send=new StringBuffer("MSD").append(EOL);
        Hashtable h=m.getAttributes();
        Object[] v=h.keySet().toArray();
        for(int i=0;i<v.length;i++){
            send.append((String)v[i]).append(":").append((String)h.get(v[i])).
                    append(EOL);
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

    /** Set the key to encode the data.
     * @param key Key to use to encrypt/decrypt the data
     * @param algorithm Name of the algorythm to use. It must be a name
     * known by javax.crypto.Cipher.getInstance()
     * @param net The NetworkManager used for this key
     * @throws Exception Any exception from javax.crypto.Cipher.getInstance
     */
    public void setKey(Key key,String algorithm,NetworkManager net) throws
            Exception{
        Cipher cipher=Cipher.getInstance(algorithm);
        cipher.init(Cipher.ENCRYPT_MODE,key);
        Cipher decipher=Cipher.getInstance(algorithm);
        decipher.init(Cipher.DECRYPT_MODE,key);
        ciphers.put(net.getGenericName(),cipher);
        deciphers.put(net.getGenericName(),decipher);
        logger.debug("Key set for algorithm "+algorithm); //@@l
    }

    /** Encrypts an array of bytes.
     * @param b Byte array to encode
     * @param net NetworkManager we are encoding for
     * @return Encoded byte array
     * @throws java.lang.Exception */
    private byte[] encode(byte[] b,NetworkManager net) throws Exception{
        Cipher cipher=(Cipher)ciphers.get(net.getGenericName());
        if(cipher==null){
            throw new Exception("Cipher not defined for network "+
                                net.getGenericName());
        }
        if(b==null){
            return null;
        }
        ByteArrayOutputStream out=new ByteArrayOutputStream();

        ByteArrayInputStream in=new ByteArrayInputStream(b);
        CipherInputStream cin=new CipherInputStream(in,cipher);
        int read=0;
        byte[] bo=new byte[1024];
        read=cin.read(bo);
        while(read>-1){
            out.write(bo,0,read);
            read=cin.read(bo);
        }
        return out.toByteArray();
    }

    /** Decrypts an array of bytes.
     * @param b The array to decode
     * @param net NetworkManager we are decoding for
     * @return The decoded array
     * @throws java.lang.Exception */
    private byte[] decode(byte[] b,NetworkManager net) throws Exception{
        Cipher decipher=(Cipher)deciphers.get(net.getGenericName());
        if(decipher==null){
            throw new Exception("Cipher not defined");
        }
        if(b==null){
            return null;
        }
        ByteArrayOutputStream out=new ByteArrayOutputStream();

        ByteArrayInputStream in=new ByteArrayInputStream(b);
        CipherInputStream cin=new CipherInputStream(in,decipher);
        int read=0;
        byte[] bo=new byte[1024];
        read=cin.read(bo);
        while(read>-1){
            out.write(bo,0,read);
            read=cin.read(bo);
        }
        return out.toByteArray();
    }

    /** Add a printable hash code to the message.
     * In this version, the hash code is created with an MD5 algorithm.
     * @param m The message this method will add the hash code to */
    private void hash(Message m){
        if(hasher==null){
            return;
        }
        hasher.reset();
        m.setHashCode(encodePrintable(hasher.digest(m.getData())));
    }

    /** Compares the hash code provided by the message and the
     * hash code calculated for the content.
     * @param m The message to validate
     * @return If both hash codes are the same */
    public boolean validate(Message m){
        // if the message has not hashcode, it can not be verified.
        if(m.getHashCode()==null){
            return false;
        }

        try{
            // hash the message content
            byte[] h=decodePrintable(m.getHashCode());
            hasher.reset();
            byte[] ho=hasher.digest(m.getData());
            logger.debug("Original hash:   "+new String(m.getHashCode())); //@@l
            logger.debug("Calculated hash: "+new String(encodePrintable(ho))); //@@l
            // compare the hashing with the one provided in the message
            if(h.length!=ho.length){
                return false;
            }
            for(int i=0;i<h.length;i++){
                if(h[i]!=ho[i]){
                    return false;
                }
            }
            return true;
        } catch(Exception e){
            logger.warn("Exception while validating: "+e); //@@l
            return false;
        }
    }

    /** Encodes an array to a printable array of bytes.
     *  @param b The array to encode
     *  @return A printable array with the bytes encoded. */
    public static byte[] encodePrintable(byte[] b){
        byte[] e=new byte[b.length*2];
        for(int i=0;i<b.length;i++){
            e[2*i]=(byte)(0x40|(b[i]&0x0f));
            e[2*i+1]=(byte)(0x40|((b[i]&0xf0)>>4));
        }
        return e;
    }

    /** Decodes a byte array encoded with encodePrintable.
     * @see encodePrintable
     * @param b The array to decode
     * @return The original array of data */
    public static byte[] decodePrintable(byte[] b){
        byte[] e=new byte[b.length/2];
        for(int i=0;i<e.length;i++){
            e[i]=(byte)((b[2*i]&0x0f)|((b[2*i+1]&0x0f)<<4));
        }
        return e;
    }
}
