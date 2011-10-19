package es.upc;

import java.net.*;
import java.io.*;
import com.solers.slp.*;
import java.util.Hashtable;
import java.util.Vector;
import java.util.Locale;
import java.util.Enumeration;

/** This class stores a server of images.
 *
 * The protocol is simple: it set a TCP listener on a well known port
 * listening for incoming messages. When a client is connected, it
 * can send a number of messages:
 * <ul>
 * <li>LIST+EOL: The server will send back a list of the form:
 * NUMBER_OF_ITEMS+EOL+ITEM_NAME+EOL+ITEM_NAME+EOL+...</li>
 * <li>BYE+EOL: the server will close the connection.</li>
 * <li>ITEM_NAME+EOL: the server will send back LENGHT+EOL+IMAGE_BYTES
 * <li>PUBLISH+EOL+NAME+EOL+LENGTH+IMAGE_BYTES
 * </ul>
 * Note that EOL means 'end-of-line' and it is the string '\r\n'. After
 * sending 1024 bytes of the image, the server will wait for a single byte
 * from the listener to prevent intermiddle buffer overflows.
 *
 * The configuration file has multiple lines with the form:
 * ITEM_NAME;ITEM_FILE
 * We are not usng properties file in case this server be used with
 * Java Personal Profile.
 *
 * This server uses SLP to register itself with the SLP protocol, if
 * available.
 * @author juanvi
 * @version $Revision: 1.3 $ $Date: 2005-05-24 16:52:56 $ */
public class ImageServer implements Runnable {
    /** The server socket */
    private ServerSocket serverSocket = null;
    /** The thread running this server */
    private Thread thread = null;
    /** The service URL */
    private ServiceURL url = null;
    /** A hashtable of images: the key is the name of the image and
     * the object the filename to look for it.
     */
    private Hashtable images = null;
    /** End Of Line */
    private final static byte[] EOL = {'\r', '\n'};

    /** Creates the server from command line.
     * @param args The arguments of the server. Only two arguments
     * are important: the first one will be the file to read the
     * configuration from and the second the port to listen to
     * connections.
     */
    public static void main(String args[]) {
        int port = 15152;
        String file = "images.txt";
        try {
            if (args.length == 2) {
                file = args[0];
                port = Integer.parseInt(args[1]);
            }
            ImageServer server = new ImageServer(file, port);
        } catch (Exception e) {
            System.err.println("Error while starting system: " + e);
            e.printStackTrace();
            System.exit(1);
        }
    }

    /** Use this class to shutdown the server. */
    private class Shutdown extends Thread {
        ImageServer server = null;
        /** @param s Finish this server when the run method is called */
        public Shutdown(ImageServer s) {
            server = s;
        }

        public void run() {
            server.finish();
        }
    }


    /** Construct the server, ready for incoming calls.
     *
     * @param file The name of the file to read the images.
     * @param port The port to listen for connections.
     * @throws java.lang.Exception If the server can not be set.
     */
    public ImageServer(String file, int port) throws Exception {
        System.out.println("Initializing image server: "+file+", "+port);

        // read configuration from file
        images = new Hashtable();
        BufferedReader in = new BufferedReader(new InputStreamReader(getClass().
                getClassLoader().getSystemResourceAsStream(file)));
        String line = null;
        while ((line = in.readLine()) != null) {
            int p = line.indexOf(";");
            String key = line.substring(0, p);
            String image = line.substring(p + 1);
            byte[] im = toByteArray(getClass().getClassLoader().
                                getSystemResourceAsStream(image));
	    images.put(key,im);
        }
        printList(System.out);

        // set the server in the port
        serverSocket = new ServerSocket(port);

        // set the thread to run when closing
        try {
            Runtime.getRuntime().addShutdownHook(new Shutdown(this));
        } catch (Exception e) {

        }

        // register the service in SLP
        try {
            Advertiser ad = ServiceLocationManager.getAdvertiser(Locale.
                    getDefault());
            String urlStr = "service:imageserver://" +
                            InetAddress.getLocalHost().getCanonicalHostName() +
                            ":" + port;
            url = new ServiceURL(urlStr, ServiceURL.LIFETIME_DEFAULT);
            ad.register(url, new Vector());
        } catch (Exception e) {
            System.err.println("I couln't register the server in SLP: " + e);
            url = null;
        }

        // start the thread
        thread = new Thread(this);
        thread.start();
    }

    /** Run the server waiting for incoming services */
    public void run() {
        while (!thread.interrupted()) {
            Socket s = null;
            try {
                System.out.println("Waiting from clients on port" +
                                   serverSocket.getLocalPort());
                // get a new connection
                s = serverSocket.accept();
                System.out.println("Connection from " +
                                   s.getInetAddress().getHostAddress());
		InputStream in=s.getInputStream();
		OutputStream out=s.getOutputStream();
                while (true) {
                    // read a line from the client
                    String line = readLine(in,128);
                    // if BYTE, break
                    if (line.equals("BYE")) {
                        break;
                    }
		    // let the user publish an image
		    if(line.equals("PUBLISH")){
			String name=readLine(in,128);
			int length=Integer.parseInt(readLine(in,128));
			byte[] b=new byte[length];
			int r=0;
			while(r<length){
			      r+=in.read(b,r,length-r);
			      out.write('0');
			      out.flush();
			}
			images.put(name,b);
			System.out.println("Published: "+name);
			continue;
		    }
                    if (!images.containsKey(line)) {
                        // if unknown (this includes LIST), print the list
                        printList(out);
                    } else {
                        // else, print the image
                        printImage(line, out, in);
                    }
                }
                s.close();
            } catch (Exception e) {
                System.err.println("Error while listening: " + e);
                // after any exception, do nothing
                try {
                    s.close();
                } catch (Exception e2) {

                }
            }
        }
    }

    /** @param out Print the list in this output stream.
     * @throws java.lang.Exception If an error occurs */
    public void printList(OutputStream out) throws Exception {
        System.out.println("Printing list");
	out.write((""+images.size()).getBytes());
	out.write(EOL);
	out.flush();
        for (Enumeration e = images.keys(); e.hasMoreElements(); ) {
            out.write(e.nextElement().toString().getBytes());
            out.write(EOL);
        }
        out.flush();
    }

    /** @param image Print the filename...
     * @param out In this output stream.
     * @throws java.lang.Exception If an error occurs
     */
    public void printImage(String image, OutputStream out, InputStream in) throws Exception {
        System.out.println("Printing image " + image);
        byte[] im =(byte[])images.get(image);
	out.write(("" + im.length).getBytes());
        out.write(EOL);
	out.flush();
	for(int i=0; i<im.length; i+=1024){
        	out.write(im,i,Math.min(1024,im.length-i));
		out.flush();
		in.read();
	}
    }

    /** Stop the server and unregister the service from the SLP */
    public void finish() {
        System.out.println("Finishing server");
        try {
            thread.interrupt();
        } catch (Exception e) {
            System.err.println("Exception while interrumping server: " + e);
        }
        // unregister the service in SLP
        try {
            if (url != null) {
                Advertiser ad = ServiceLocationManager.getAdvertiser(
                        Locale.getDefault());
                ad.deregister(url);
            }
        } catch (Exception e) {
            System.err.println("I couldn't deregister the service: " + e);
        }
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
    
    /** Save a whole stream in a byte array.
     *
     * Code from java.sun.com answering abut how load a resource inside a Jar from
     * an applet. */
    public static byte[] toByteArray(java.io.InputStream input) throws java.io.
            IOException {
        int status = 0;
        final int blockSize = 4096;
        int totalBytesRead = 0;
        int blockCount = 1;
        byte[] dynamicBuffer = new byte[blockSize * blockCount];
        final byte[] buffer = new byte[blockSize];

        boolean endOfStream = false;
        while (!endOfStream) {
            int bytesRead = 0;
            if (input.available() != 0) {
                // data is waiting so read as
                //much as is available
                status = input.read(buffer);
                endOfStream = (status == -1);
                if (!endOfStream) {
                    bytesRead = status;
                }
            } else {
                // no data waiting so use the
                //one character read to block until
                // data is available or the end of the input stream is reached
                status = input.read();
                endOfStream = (status == -1);
                buffer[0] = (byte) status;
                if (!endOfStream) {
                    bytesRead = 1;
                }
            }

            if (!endOfStream) {
                if (totalBytesRead + bytesRead > blockSize * blockCount) {
                    // expand the size of the buffer
                    blockCount++;
                    final byte[] newBuffer = new byte[blockSize * blockCount];
                    System.arraycopy(dynamicBuffer, 0, newBuffer, 0,
                                     totalBytesRead);
                    dynamicBuffer = newBuffer;
                }
                System.arraycopy(buffer, 0, dynamicBuffer, totalBytesRead,
                                 bytesRead);
                totalBytesRead += bytesRead;
            }
        } //end of while(!endOfStream)

        // make a copy of the array of the exact length
        final byte[] result = new byte[totalBytesRead];
        if (totalBytesRead != 0) {
            System.arraycopy(dynamicBuffer, 0, result, 0, totalBytesRead);
        }

        return result;
    }
}
