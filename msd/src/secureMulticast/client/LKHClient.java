package secureMulticast.client;

import org.msd.proxy.MSDManager;
import secureMulticast.keyDistribution.symKeyGen.KEK;
import secureMulticast.keyDistribution.symKeyGen.KeyOFT;
import secureMulticast.keyDistribution.symKeyGen.KeySingleMessage;
import secureMulticast.keyDistribution.algorithm.LKH;
import secureMulticast.keyDistribution.net.*;
import secureMulticast.util.Conversion;
import secureMulticast.event.LKHChangesSupport;
import secureMulticast.event.LKHEvent;
import secureMulticast.event.LKHListener;

import javax.crypto.SecretKey;
import java.util.Vector;

import org.msd.comm.*;

/**
 * <p> This class implements all the logical processes needed by a client that wants to be part of a secure multicast
 * group. It takes care of the KEK and SEK updating through deciphering the packets whose destination is the client
 * or the subgroup where the client belongs to.
 * As this class extends the thread class, the start method must be called in order to begin the process, but it is
 * not recommended, because the start method is called when the joining method has been successfully run.
 *
 * <p>Actually, this class is just a modification of the original from LKH packet,
 * adapting it to the communications in a UDP network</p>
 *
 * @version $Revision: 1.9 $
 */
public class LKHClient extends Thread{
    ////////////////////////////////////////////////////////////////////////////
    //////// LKHclient fields //////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Specifies the object which contains all the KEKs needed by the member to decipher rekeying messages.
     */
    public StoreKeys StoredKEKs;

    /**
     * Specifies the unique member identifier.
     */
    private int ID;

    /**
     * Specifies the object used to take care of the MSD-unicast communications.
     */
    private MSDUconnection tcp;

    /**
     * Specifies the object used to take care of the MSD-multicast communications.
     */
    private MSDMconnection udp;

    /** MSDManager controlling this client */
    private MSDManager msdmanager;
    private NetworkManager net;

    /**
     * Specifies whether the client must listen on UDP port or not.
     */
    private boolean endListening;

    /**
     * Specifies if the rekeying is the first realized for this client.
     */
    private boolean initialProcess;

    /**
     * Specifies the object responsible for firing events such as new rekeying packets received, new SEK, etc.
     */
    private LKHChangesSupport giveAdvice;

    ////////////////////////////////////////////////////////////////////////////
    //////// Class constructors ////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructs a LKH client capable of updating the KEKs needed and giving the SEK
     * to the object which implements it. The method joining must be called in order to begin
     * the process.
     *
     * @param msdmanager controlling this client. The key server will be the emperor */
    public LKHClient(MSDManager msdmanager,int type,NetworkManager net){
        this.msdmanager=msdmanager;
        this.net=net;
        StoredKEKs=new StoreKeys();
        giveAdvice=new LKHChangesSupport(this,net);

        try{
            tcp=new MSDUconnection(msdmanager,type,net);
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //////// Class methods /////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Adds the specified listener component to this client.
     *
     * @param listener the component which is going to catch events fired by this client.
     */
    public void addLKHListener(LKHListener listener){
        giveAdvice.addLKHListener(listener);
    }

    /**
     * Removes the specified listener component to this client.
     *
     * @param listener the listener component to be removed.
     */
    public void removeLKHListener(LKHListener listener){
        giveAdvice.removeLKHListener(listener);
    }

    /**
     * Executes the joining process to the multicast group establishing a tcp connection to the key server
     * getting the unique's memeber ID and its own KEK. After that the client calls the start method and starts to
     * listen to the specified UDP port for multicast messages.
     *
     * @return an int value containing the ID if the joining process ended correctly, a zero
     *          value otherwise.
     */
    public int joining(){
        try{
            initialProcess=true;
            tcp.write("joining".getBytes());
            System.out.println("Joining Multicast group");
            System.out.println("Starting D-H algorithm...");
            SecretKey sessionKey=tcp.genDHsessionKey();
            System.out.println("D-H session key created (algorithm - "+
                               sessionKey.getAlgorithm()+", value - "+
                               Conversion.asHex(sessionKey)+")");

            int[] MCASTCLIENT=new int[2];
            KEK key=MSDUconnection.unbuildTCPpacket(tcp.read(),MCASTCLIENT,
                    sessionKey);

            ID=MCASTCLIENT[1];
            System.out.println("ID received: "+ID);
            System.out.println("Associated node's key received: (algorithm - "+
                               key.keyData.getAlgorithm()+", value - "+
                               Conversion.asHex(key.keyData)+")");

            Vector Keys=new Vector(0);
            Keys.addElement(key);

            StoredKEKs.storeKeys(MCASTCLIENT[0],Keys);
            giveAdvice.triggerLKHChange(LKHEvent.DUMMY_STATE,null);

            endListening=false;
            start();

            return ID;
        } catch(Exception e){
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Executes the leaving process of the multicast group according to the protocol established by the LKH key
     * server. It is also responsible of free the port where the client is listening to group messages.
     */
    public void leaving(NetworkManager net){
        System.out.println("Leaving Multicast group");
        tcp=new MSDUconnection(msdmanager,Connection.LEAVE,net);
        tcp.write("leaving".getBytes());
        System.out.println("Asking server for leaving");
        tcp.read();
        System.out.println("ID requested from server");
        tcp.write(KEK.intTo4Bytes(ID));
        System.out.println("Sending own ID "+ID);
        tcp.read();
        System.out.println("Out of the multicast group");
        try{
            tcp.close();
        }catch(Exception e){
            System.err.println("Error wile closing connection: "+e);
        }
        tcp=null;
        try{
            msdmanager.send(udp.buildLeavingPacket(StoredKEKs.getLKHversion(),
                    this.ID));

        } catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Returns a vector containing the whole KEKs stored in the client.
     *
     * @return a vector containing all the stored KEKs.
     */
    public Vector getStoredKEKs(){
        return StoredKEKs.getStoredKEKs();
    }

    /**
     * Method called by the method start. It does all the thread processing. Waits for packets
     * of any kind (rekeying, SEKs, etc.) and updates the vector of KEKs and fires events to give
     * advice in case of new received SEK .
     */
    public void run(){
        int[] MCASTCLIENT=new int[5];
        int[] header=new int[2];
        int numKEKs=0;
        Vector identificators=new Vector();
        KEK key;
        Vector receivedKeys;

        udp=new MSDMconnection(msdmanager);
        udp.buildReceivingSocket();
        udp.joinMULTICASTgroup();
        System.out.println(
                "Joined Multicast group, listening on UDP port for rekeying");

        try{
            tcp.write("END".getBytes());
            tcp.close();
        }catch(Exception e){
            System.err.println("Error while closing connecting: "+e);
        }

        while(!endListening){
            identificators.removeAllElements();
            Message m=udp.UDPReceive(net.getGenericName());
            byte[] packetInfo=MSDMconnection.unbuildHeader(m.getData(),
                    header,numKEKs,identificators);
            byte[] packet=null;

            key=StoredKEKs.getKeyToDecipher(identificators);

            if(key!=null||header[0]==MSDMconnection.SIMPLE_MESSAGE_REMOVE){
                int ID_removed=0;
                Vector StoredBlindKEKs=KeyOFT.calcualteBlinds(StoredKEKs.
                        getStoredKEKs());
                switch(header[0]){
                case MSDMconnection.SEK_DISTRIBUTION:
                    packet=MSDMconnection.unbuildCompletePacket(packetInfo,
                            MCASTCLIENT);
                    System.out.println("New SEK packet received");
                    giveAdvice.triggerLKHChange(LKHEvent.NEW_SEK,
                                                MSDMconnection.
                                                decipherSEK(packet,
                            MCASTCLIENT[2],key));
                    break;
                case MSDMconnection.MULTICAST_LEAVING:
                    endListening=true;
                    giveAdvice.triggerLKHChange(LKHEvent.OFF_STATE,null);
                    break;
                case MSDMconnection.SIMPLE_MESSAGE_REMOVE:
                    key=null;
                    Vector v=MSDMconnection.unbuildBalckList(packetInfo);
                    StoredKEKs.removeKEKs(v);
                    break;
                case MSDMconnection.KEK_OFT:
                    packet=MSDMconnection.unbuildCompletePacket(packetInfo,
                            MCASTCLIENT);
                    System.out.println("New KEK packet received ("+
                                       MCASTCLIENT[0]+" new KEKs stored)");
                    ID_removed=MSDMconnection.unbuilRemovedNode(packet);
                    packet=MSDMconnection.unbuildCompletePacket2(packet);

                    receivedKeys=MSDMconnection.unbuildKEKsPacket(MCASTCLIENT[0],
                            MCASTCLIENT[2],packet,key);
                    for(int i=0;i<StoredBlindKEKs.size();i++){
                        if(((KEK)StoredBlindKEKs.get(i)).LKH_ID==ID_removed){
                            StoredBlindKEKs.remove(i);
                        }
                    }
                    if(((KEK)StoredKEKs.get(0)).LKH_ID==
                       ((KEK)receivedKeys.get(0)).LKH_ID){
                        StoredKEKs.storeKeysOFT(header[1],receivedKeys,4,
                                                StoredBlindKEKs);
                    } else{
                        StoredKEKs.storeKeysOFT(header[1],receivedKeys,1,
                                                StoredBlindKEKs);
                    }
                    break;
                case MSDMconnection.KEK_SINGLEMESSAGE:
                    packet=MSDMconnection.unbuildCompletePacketSingleMessage(
                            packetInfo,MCASTCLIENT,identificators);
                    SecretKey P=KeySingleMessage.obtainP(packet,key);
                    System.out.println("New parameter P: "+Conversion.asHex(P));
                    for(int i=0;i<StoredKEKs.size();i++){
                        StoredKEKs.set(i,
                                       new KEK(KeySingleMessage.XOR(((KEK)StoredKEKs.
                                get(i)).keyData,P),
                                               ((KEK)StoredKEKs.get(i)).LKH_ID));
                    }
                    break;
                case MSDMconnection.KEK_SINGLEMESSAGE_ADD:
                    byte[] packet2;
                    packet=MSDMconnection.unbuildAddNodePacketSingleMessage(
                            packetInfo,MCASTCLIENT);
                    packet2=MSDMconnection.unbuildAddNodePPacketSingleMessage(
                            packetInfo,MCASTCLIENT);
                    KEK newKey=MSDMconnection.unbuildKEKsPacket(packet2,key,
                            MCASTCLIENT[2]);
                    P=MSDMconnection.decipheParameterrP(MCASTCLIENT[3],packet,
                            key);
                    System.out.println("New KEK packet received ("+
                                       MCASTCLIENT[0]+" new KEK stored)");
                    System.out.println("New Parameter P: "+Conversion.asHex(P));
                    for(int i=0;i<StoredKEKs.size();i++){
                        StoredKEKs.set(i,
                                       new KEK(KeySingleMessage.XOR(((KEK)StoredKEKs.
                                get(i)).keyData,P),
                                               ((KEK)StoredKEKs.get(i)).LKH_ID));
                    }
                    StoredKEKs.add(1,newKey);
                    break;
                default:
                    packet=MSDMconnection.unbuildCompletePacket(packetInfo,
                            MCASTCLIENT);
                    if(header[1]==LKH.LKHSIMPLE||header[1]==LKH.LKHBATCH||
                       header[1]==LKH.LKHBALANCEDBATCH){
                        StoredKEKs.removeKeys(MCASTCLIENT[1]);
                    }
                    System.out.println("New KEK packet received ("+
                                       MCASTCLIENT[0]+" new KEKs stored)");
                    receivedKeys=MSDMconnection.unbuildKEKsPacket(MCASTCLIENT[0],
                            MCASTCLIENT[2],packet,key);
                    if(header[1]==LKH.OFTSIMPLE){
                        if(initialProcess){
                            initialProcess=false;
                            StoredKEKs.storeKeysOFT(header[1],receivedKeys,0,
                                    StoredBlindKEKs);
                        }
                        if(MCASTCLIENT[0]==2&&
                           ((KEK)(receivedKeys.get(0))).LKH_ID==ID){
                            StoredKEKs.storeKeysOFT(header[1],receivedKeys,3,
                                    StoredBlindKEKs);

                        } else if(MCASTCLIENT[0]==1&&
                                  ((KEK)(receivedKeys.get(0))).LKH_ID==ID){
                            StoredKEKs.storeKeysOFT(header[1],receivedKeys,2,
                                    StoredBlindKEKs);
                        } else if(MCASTCLIENT[0]==1){
                            StoredKEKs.storeKeysOFT(header[1],receivedKeys,1,
                                    StoredBlindKEKs);
                        }
                    } else if(header[1]==LKH.LKHSINGLEMESSAGE||
                              header[1]==LKH.LKHSINGLEMESSAGEBATCH){
                        StoredKEKs.clear();
                        StoredKEKs.storeKeysSM(header[1],receivedKeys);
                    } else{
                        StoredKEKs.storeKeys(header[1],receivedKeys);
                    }
                }
            }
        }
        StoredKEKs.clear();
        udp.leaveMULTICASTgroup();
        udp.close();
    }

    ////////////////////////////////////////////////////////////////////////////
    //////// Class graphic methods /////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Returns an int value containing the index of the KEK vector where the new KEKs start.
     *
     * @return the first new KEK index.
     */
    public int getNewKEKIndex(){
        return StoredKEKs.getNewKEKIndex();
    }
}
