package secureMulticast.server;

import secureMulticast.keyDistribution.algorithm.*;
import secureMulticast.keyDistribution.net.*;
import secureMulticast.event.LKHListener;
import secureMulticast.event.LKHChangesSupport;

import javax.crypto.*;

import org.msd.proxy.MSDManager;
import org.msd.comm.NetworkManager;

/**
 * <p> This class implements all the logical processes needed by a server that pretends to be a LKH key server of
 * a secure multicast group. It takes care of the KEK and SEK updating and also of sending them to the clients through
 * the multicast channel. It can manage six LKH rekeying algorithms such as the first designed LKH algorithm, the
 * batch LKH algorithm, the balanced batch LKH algorithm, OFT algorithm, single message LKH algorithm and batch Single
 * Message LKH algorithm. The symmetric keys used, are the ones supported by the SunJCE provider: DES, Blowfish, DESede
 * (Triple DES) and AES. As this class implements the Runnable class, the start method must be called in order to begin
 * the process. To stop the server (and by the way free the binded port) the stop method must be called.
 *
 * @author  Oscar Burgos & Daniel Jarne
 * @version 1.1, 01/10/04
 */
public class LKHserver implements Runnable{
    ////////////////////////////////////////////////////////////////////////////
    //////// Static and basic LKHserver fields /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Defines an int value for the simple LKH algorithm.
     */
    public static final int LKHSIMPLE=0;

    /**
     * Defines an int value for the Batch LKH algorithm.
     */
    public static final int LKHBATCH=1;

    /**
     * Defines an int value for the Balanced Batch LKH algorithm.
     */
    public static final int LKHBALANCEDBATCH=2;

    /**
     * Defines an int value for the OFT algorithm.
     */
    public static final int OFT=3;

    /**
     * Defines an int value for the Single Message LKH algorithm.
     */
    public static final int LKHSINGLEMESSAGE=4;

    /**
     * Defines an int value for the Batch Single Message LKH algorithm.
     */
    public static final int LKHSINGLEMESSAGEBATCH=5;

    /**
     * Defines an int value for the DES algorith.
     */
    public static final int DES=0;

    /**
     * Defines an int value for the Blowfish algorith.
     */
    public static final int Blowfish=1;

    /**
     * Defines an int value for the DESede (TripleDES) algorith.
     */
    public static final int DESede=2;

    /**
     * Defines an int value for the AES symmetric algorith.
     */
    public static final int AES=3;

    /**
     * Specifies a Thread object to manage the server execution.
     */
    private Thread engine;

    /**
     * Specifies the algorithm object used in the server.
     */
    private LKH algorithm;

    /**
     * Specifies the object responsible for triggering events such as new joinings or leavings, new SEK, etc.
     */
    private LKHChangesSupport giveAdvice;

    private MSDManager msdmanager;

    private NetworkManager net;

    ////////////////////////////////////////////////////////////////////////////
    //////// Class constructors ////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructs a LKHserver capable of serve all the member's requests (joinings, leavings, etc.) but also
     * to generate new SEK on every rekeying process and notification to the class that implements the server
     * through triggering events. The server can implement any of the supported LKH algorithms an keys.
     * It is necessary to call the start method in order to begin the server execution.
     *
     * @param LKHalgorithm an int value specifying the LKH algorithm to be implemented in the server.
     *					   {@link #LKHSIMPLE basic LKH algorithm}, {@link #LKHBATCH Batch LKH algorithm},
     *					   {@link #LKHBALANCEDBATCH Balanced Batch LKH algorithm}, {@link #OFT algorithm},
     *                     {@link #LKHSINGLEMESSAGE algorithm}, {@link #LKHSINGLEMESSAGEBATCH}
     * @param sleepTime an int value specifying the time between rekeying in case of using Batch, Balanced Batch
     *					or Batch Single Message LKH algorithms.
     * @param keyAlgorithm an int value specifying the algorithm used to generate the symmetric keys.
     *					   {@link #DES the DES algorithm}, {@link #Blowfish the Blowfish algorithm},
     *					   {@link #DESede the triple DES algorithm}, {@link #AES algorithm}
     * @msd MSDManager to send/receive the connections
     */
    public LKHserver(int LKHalgorithm,int sleepTime,int keyAlgorithm,
                     MSDManager msdmanager, NetworkManager net){
        giveAdvice=new LKHChangesSupport(this,net);
        this.msdmanager=msdmanager;
        this.net=net;
        setupAlgorithm(LKHalgorithm,keyAlgorithm,sleepTime);
    }

    /**
     * Constructs a LKHserver capable of serve all the member's requests (joinings, leavings, etc.) but also
     * to generate new SEK on every rekeying process and notification to the class that implements the server
     * through triggering events. It is necessary to call the start method in order to begin the server execution.
     *
     * @param keyAlgorithm an int value specifying the algorithm used to generate symmetric keys.
     *					   {@link #DES the DES algorithm}, {@link #Blowfish the Blowfish algorithm},
     *					   {@link #DESede the triple DES algorithm}, {@link #AES algorithm}
     * @msd MSDManager to send/receive the connections
     */
    public LKHserver(int keyAlgorithm,MSDManager msd,NetworkManager net){
        giveAdvice=new LKHChangesSupport(this,net);
        this.msdmanager=msd;
        this.net=net;
        setupAlgorithm(LKHSIMPLE,keyAlgorithm,0);
    }

    ////////////////////////////////////////////////////////////////////////////
    //////// Class methods /////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Sets the LKH algorithm to be implemented in the key server.
     *
     * @param LKHalgorithm an int value specifying the LKH algorithm to be implemented in the server.
     * @param keyAlgorithm an int value specifying the algorithm used to generate symmetric keys.
     * @param sleepTime an int value specifying the time between rekeying in case of using Batch, Balanced Batch
     *					or Batch Single Message LKH algorithms.
     * @param groupAddress a string representing the multicast group address.
     * @param UDPport an int specifying the UDP port for multicast messages.
     */
    private void setupAlgorithm(int LKHalgorithm,int keyAlgorithm,int sleepTime){

        switch(LKHalgorithm){
        case LKHSIMPLE:
            algorithm=new LKHsimple(keyAlgorithm,msdmanager,giveAdvice);
            break;
        case LKHBATCH:
            algorithm=new LKHbatch(keyAlgorithm,sleepTime,msdmanager,giveAdvice);
            break;
        case LKHBALANCEDBATCH:
            algorithm=new LKHbalancedBatch(keyAlgorithm,sleepTime,msdmanager,
                                           giveAdvice);
            break;
        case OFT:
            algorithm=new OFT(keyAlgorithm,msdmanager,giveAdvice);
            break;
        case LKHSINGLEMESSAGE:
            algorithm=new LKHSingleMessage(keyAlgorithm,msdmanager,
                                           giveAdvice);
            break;
        case LKHSINGLEMESSAGEBATCH:
            algorithm=new BatchSingleMessageLKH(keyAlgorithm,sleepTime,
                                                msdmanager,giveAdvice);
            break;
        }
        algorithm.start();
    }

    /**
     * Adds the specified listener component to this server.
     *
     * @param listener the component which is going to catch events triggered by this server.
     */
    public void addLKHListener(LKHListener listener){
        giveAdvice.addLKHListener(listener);
    }

    /**
     * Removes the specified listener component to this server.
     *
     * @param listener the listener component to be removed.
     */
    public void removeLKHListener(LKHListener listener){
        giveAdvice.removeLKHListener(listener);
    }

    /**
     * Begins the server execution and generates the first SEK to encipher the session data.
     *
     * @return the first SEK to allow encipher session data.
     */
    public SecretKey start(){
        if(engine==null){
            engine=new Thread(this);
            engine.start();
        }
        return algorithm.genNewSEK();
    }

    /**
     * Initializes the server execution (creates an instance of a TCPconnection object), listens to the specified
     * TCP port for members' requests and delegates the execution of the request to another class to continue
     * listening to other requests.
     */
    public void run(){

        Thread thisThread=Thread.currentThread();
        while(engine==thisThread){
            try{
                MSDUconnection tcp=new MSDUconnection(msdmanager,MSDUconnection.accept(net.getGenericName()));
                String line=new String(tcp.read());

                if(line.equals("joining")){
                    System.out.println(
                            "\n>>>>--->>>--->>--->---NEW REQUEST---<---<<---<<<---<<<<");
                    System.out.println("New joining request received");
                    ComplyClient serveClient=new ComplyClient(algorithm,tcp,
                            ComplyClient.JOINING);
                    tcp=null;
                    serveClient.start();
                }
                if(line.equals("leaving")){
                    System.out.println(
                            "\n>>>>--->>>--->>--->---NEW REQUEST---<---<<---<<<---<<<<");
                    System.out.println("New leaving request received");
                    ComplyClient serveClient=new ComplyClient(algorithm,tcp,
                            ComplyClient.LEAVING);
                    serveClient.start();
                }
                if(line.equals("stop")){
                    // do nothing
                }
            } catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //////// Class graphic methods /////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Returns an LKH object; that is, in fact, the algorithm used.
     *
     * @return the LKH algorithm object.
     */
    public LKH getAlgorithm(){
        return algorithm;
    }
}
