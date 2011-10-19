package secureMulticast.keyDistribution.net;

import org.msd.proxy.MSDManager;
import org.msd.comm.*;

import secureMulticast.binaryTree.Node;
import secureMulticast.binaryTree.MemberID;
import secureMulticast.keyDistribution.symKeyGen.KEK;
import secureMulticast.keyDistribution.symKeyGen.KeySingleMessage;
import secureMulticast.keyDistribution.cipher.SymEncrypter;
import secureMulticast.util.Conversion;

import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.util.Vector;
import java.util.Random;
import java.math.BigInteger;

import java.util.Hashtable;

/**
 *
 * <p>  This class is the one that with its constructor and methods allows doing any of the UDP
 * processes needed in any of the other classes that set up the package. It is responsible of
 * construct the sockets, build the packets, sending and receiving them, etc. All in an UDP
 * scenario
 *
 * @author  Oscar Burgos & Daniel Jarne
 * @version 1.1, 01/10/04
 */
public class MSDMconnection{
    ////////////////////////////////////////////////////////////////////////////
    //////// Static and basic MSDMconnection fields /////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Defines the value for the packet header that specifies the kind of packet sent.
     */
    public static final int KEK_LKH=2;

    /**
     * Defines the value for the packet header that specifies the kind of packet sent.
     */
    public static final int KEK_OFT=3;

    /**
     * Defines the value for the packet header that specifies the kind of packet sent.
     */
    public static final int KEK_SINGLEMESSAGE=4;

    /**
     * Defines the value for the packet header that specifies the kind of packet sent.
     */
    public static final int KEK_SINGLEMESSAGE_ADD=5;

    /**
     * Defines the value for the packet header that specifies the kind of packet sent.
     */
    public static final int SIMPLE_MESSAGE_REMOVE=6;

    /**
     * Defines the value for the packet header that specifies the kind of packet sent.
     */
    public static final int SEK_DISTRIBUTION=0;

    /**
     * Defines the value for the packet header that specifies the kind of packet sent.
     */
    public static final int MULTICAST_LEAVING=1;

    private MSDManager msdmanager;
    /** A hashtable of arrived messages. The key is the name of the
     * network and the content a vector of messages not received for this
     * network. The vector is a FIFO list.
     */
    private static Hashtable messages=new Hashtable();

    ////////////////////////////////////////////////////////////////////////////
    //////// Class constructors ////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructs a UDP communications' responsible object. The only thing done by the constructor is giving it
     * the environment; it dos not build any socket because there is a method to do this.
     *
     * @param groupAddress the IP address of the multicast group.
     * @param UDPport the port used by the multicast group for listen to messages.
     */
    public MSDMconnection(MSDManager msdmanager){
        this.msdmanager=msdmanager;
    }

    ////////////////////////////////////////////////////////////////////////////
    //////// Class methods /////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Builds the socket which is going to send the messages (Used by the key server to send messages to members).
     */
    public void buildSendingSocket(){
        // do nothing
    }

    /**
     * Builds the socket which is going to listen to messages sent to the port specified in the class constructor
     * (Used in the clients' side for listening to rekeying or SEK messages).
     */
    public void buildReceivingSocket(){
        // do nothing
    }

    /**
     * Closes the multicast socket.
     */
    public void close(){
        // do nothing
    }

    /**
     * Makes the socket listening to messages sent to the group address besides listening for the members' own
     * address.
     */
    public void joinMULTICASTgroup(){
        // do nothing
    }

    /**
     * Makes the socket stop listening to messages sent to the group address.
     */
    public void leaveMULTICASTgroup(){
        // do nothing
    }

    /**
     * Receives a datagram packet from the multicast socket.
     * Blocks until the packet has been received.
     *
     * @param network The generic name of the network to listen to messages.
     * @return the datagram packet received.
     */
    public Message UDPReceive(String network){
        // synchronized to messages not to create different Vectors here and
        // in setMessage method
        Vector v=null;
        synchronized(messages){
            v=(Vector)messages.get(network);
            if(v==null){
                v=new Vector();
                messages.put(network,v);
            }
        }
        // wits until a message is in the vector.
        Message m=null;
        synchronized(v){
            while(v.size()==0){
                try{
                    v.wait();
                } catch(Exception e){
                    e.printStackTrace();
                    return null;
                }
            }
            m=(Message)v.firstElement();
            v.remove(0);
        }
        return m;
    }

    /**
     * Sends a datagram packet to the specified UDP port in the constructor.
     *
     * @param packet the packet to be sent.
     */
    public void UDPsend(Message m){
        try{
            m.setEncode(false);
            msdmanager.send(m);
            System.out.println(" sent to "+m.getIDTo()+" (packet size - "+
                               m.getData().length+" bytes)");
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Prepares a packet to be send.
     *
     * @param packet the packet to be sent.
     * @param signer destination node.
     * @return the packet prepared for be send.
     */
    private Message send(ByteArrayOutputStream packet,Node signer){
        MemberID signerID=signer.getMemberID();

        if(signerID!=null){
            System.out.println("UNICAST KEK packet ciphered with node's "+
                               signer.getNameToString()+" key (ID - "+
                               signer.getKEK().LKH_ID+", algorithm - "+
                               signer.getKEK().keyData.getAlgorithm()+
                               ", value - "+
                               Conversion.asHex(signer.getKEK().keyData)+")");
            return new Message(packet.toByteArray(),msdmanager.getCache().getID(),
                               null,Message.KEY);
        } else{
            System.out.println("MULTICAST KEK packet ciphered with node's "+
                               signer.getNameToString()+" key (ID - "+
                               signer.getKEK().LKH_ID+", algorithm - "+
                               signer.getKEK().keyData.getAlgorithm()+
                               ", value - "+
                               Conversion.asHex(signer.getKEK().keyData)+")");
            return new Message(packet.toByteArray(),msdmanager.getCache().getID(),null,
                               Message.KEY);
        }
    }


    /**
     * Builds a SEK packet to update the SEK used by the members to decipher the session data.
     *
     * @param LKH_version value specifying the LKH version that manages the rekeying processes in the key server.
     * @param signer the node object whose contained key will cipher the KEK data of the packet.
     * @param SEK the new symmetric key to use to decipher session data.
     * @return a datagram packet ready to be sent.
     */
    public Message buildSEKPacket(int LKH_version,Node signer,SecretKey SEK){
        ByteArrayOutputStream packet=buildSEKBytePacket(LKH_version,signer,SEK);

        System.out.println("SEK packet ciphered with node's "+
                           signer.getNameToString()+" key (ID - "+
                           signer.getKEK().LKH_ID+", algorithm - "+
                           signer.getKEK().keyData.getAlgorithm()+", value - "+
                           Conversion.asHex(signer.getKEK().keyData)+")");
        return new Message(packet.toByteArray(),msdmanager.getCache().getID(),null,
                           Message.KEY);
    }

    /**
     * Builds a SEK packet to update the SEK used by the members to decipher the session data. Packet structure:
     *
     * <table border="1" cellspacing="1">
     * <tr>
     * <td><div align="center"><strong>Packet kind (SEK)</strong></div></td>
     * <td><div align="center"><strong>LKH version</strong></div></td>
     * <td><div align="center"><strong># of SEKs (1)</strong></div></td>
     * <td><div align="center"><strong>signer KEY ID</strong></div></td>
     * <td><div align="center"><strong>SEK data</strong></div></td>
     * </tr>
     * <tr>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">X bytes (depending on cipher key used)</div></td>
     * </tr>
     * </table>
     *
     * @param LKH_version value specifying the LKH version that manages the rekeying processes in the key server.
     * @param signer the node object whose contained key will cipher the KEK data of the packet.
     * @param SEK the new symmetric key to use to decipher session data.
     * @return the packet of bytes ready to be sent.
     */
    public static ByteArrayOutputStream buildSEKBytePacket(int LKH_version,
            Node signer,SecretKey SEK){
        ByteArrayOutputStream packet=new ByteArrayOutputStream();
        packet.write(KEK.intTo4Bytes(SEK_DISTRIBUTION),0,4);
        packet.write(KEK.intTo4Bytes(LKH_version),0,4);
        packet.write(KEK.intTo4Bytes(1),0,4);
        packet.write(KEK.intTo4Bytes(signer.getKEK().LKH_ID),0,4);
        byte[] cipheredKey=cipherSEK(SEK,signer.getKEK());
        packet.write(KEK.intTo4Bytes(cipheredKey.length),0,4);
        packet.write(cipheredKey,0,cipheredKey.length);
        return packet;
    }

    /**
     * Builds a packet to be sent by the own member towards him in order to unblock the socket listening to
     * multicast messages after the member has left the group.
     *
     * @param LKH_version value specifying the LKH version that manages the rekeying processes in the key server.
     * @param LKH_ID identificator of the node.
     * @param localAddress direction IP of the node.
     * @return a datagram packet ready to be sent.
     */
    public Message buildLeavingPacket(int LKH_version,int LKH_ID){
        ByteArrayOutputStream packet=new ByteArrayOutputStream();

        packet.write(KEK.intTo4Bytes(MULTICAST_LEAVING),0,4);
        packet.write(KEK.intTo4Bytes(LKH_version),0,4);
        packet.write(KEK.intTo4Bytes(0),0,4);
        packet.write(KEK.intTo4Bytes(LKH_ID),0,4);
        packet.write(KEK.intTo4Bytes(0),0,4);

        return new Message(packet.toByteArray(),msdmanager.getCache().getID(),
                           msdmanager.getCache().getID(),Message.KEY);
    }

    /**
     * Builds a complete KEK rekeying packet with the needed header.
     *
     * @param LKH_version int value specifying the LKH version that manages the rekeying processes in the key server.
     * @param PackKind the kind of the packet.
     * @param signer the node object whose contained key will cipher the KEK data of the packet.
     * @return a datagram packet ready to be sent.
     */
    public Message buildCompleteKEKPacket(int LKH_version,int PackKind,
                                          Node signer){
        ByteArrayOutputStream packet=buildCompleteKEKBytePacket(LKH_version,
                PackKind,signer);
        return send(packet,signer);
    }

    /**
     * Builds a KEK rekeying packet with the needed header. Packet structure:
     *
     * <table border="1" cellspacing="1">
     * <tr>
     * <td><div align="center"><strong>Packet kind</strong></div></td>
     * <td><div align="center"><strong>LKH version</strong></div></td>
     * <td><div align="center"><strong># of KEKs</strong></div></td>
     * <td><div align="center"><strong>signer KEY ID</strong></div></td>
     * <td><div align="center"><strong>KEK size</strong></div></td>
     * <td><div align="center"><strong>KEKs info</strong></div></td>
     * </tr>
     * <tr>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">X bytes (depending on cipher key used)</div></td>
     * </tr>
     * </table>
     *
     * @param LKH_version int value specifying the LKH version that manages the rekeying processes in the key server.
     * @param PackKind the kind of the packet.
     * @param signer the node object whose contained key will cipher the KEK data of the packet.
     * @return the packet of bytes ready to be sent.
     */
    public static ByteArrayOutputStream buildCompleteKEKBytePacket(int
            LKH_version,int PackKind,Node signer){
        ByteArrayOutputStream packet=new ByteArrayOutputStream();

        packet.write(KEK.intTo4Bytes(PackKind),0,4);
        packet.write(KEK.intTo4Bytes(LKH_version),0,4);
        int numKEKs=signer.getName()[0]-1;
        packet.write(KEK.intTo4Bytes(numKEKs),0,4);
        packet.write(KEK.intTo4Bytes(signer.getKEK().LKH_ID),0,4);
        byte[] cipheredKeys=buildKEKsPacket(signer);
        if(numKEKs!=0){
            packet.write(KEK.intTo4Bytes(cipheredKeys.length/numKEKs),0,4);
        } else{
            packet.write(KEK.intTo4Bytes(0),0,4);
        }
        packet.write(cipheredKeys,0,cipheredKeys.length);

        return packet;
    }

    /**
     * Builds a complete KEK rekeying packet with the needed headerfor OFT algorithm.
     *
     * @param LKH_version int value specifying the LKH version that manages the rekeying processes in the key server.
     * @param PackKind  the kind of the packet.
     * @param signer the node object whose contained key will cipher the KEK data of the packet.
     * @param flag indicates the operation to realize.
     * @return a datagram packet ready to be sent.
     */
    public Message buildCompleteKEKPacketOFT(int LKH_version,
                                             int PackKind,Node signer,int flag){
        return buildCompleteKEKPacketOFT(LKH_version,PackKind,signer,flag,null);
    }

    /**
     * Builds a complete KEK rekeying packet with the needed header for OFT algorithm.
     *
     * @param LKH_version int value specifying the LKH version that manages the rekeying processes in the key server.
     * @param PackKind the kind of the packet.
     * @param signer the node object whose contained key will cipher the KEK data of the packet.
     * @param flag indicates the operation to realize.
     * @param removed removed node.
     * @return a datagram packet ready to be sent.
     */
    public Message buildCompleteKEKPacketOFT(int LKH_version,int PackKind,
                                             Node signer,int flag,Node removed){
        ByteArrayOutputStream packet=buildCompleteKEKBytePacketOFT(LKH_version,
                PackKind,signer,flag,removed);
        return send(packet,signer);
    }

    /**
     * Builds a complete KEK rekeying packet with the needed header for OFT algorithm.
     *
     * @param LKH_version int value specifying the LKH version that manages the rekeying processes in the key server.
     * @param PackKind the kind of the packet.
     * @param signer the node object whose contained key will cipher the KEK data of the packet.
     * @param flag indicates the operation to realize.
     * 0 - a node joins in the group (welcome).
     * 1 - rekey (normal rekeying).
     * 2 - changes the key (sibling leaves).
     * 3 - changes the node key and sends sibling blind KEK (sibling joins).
     * 4 - changes the node key and removes a KEK (sibling root subtree leaves).
     * 5 - rekeys and removes a KEK (sibling root subtree leaves).
     * @return the packet of bytes ready to be sent.
     */
    public static ByteArrayOutputStream buildCompleteKEKBytePacketOFT(int
            LKH_version,int PackKind,Node signer,int flag){
        return buildCompleteKEKBytePacketOFT(LKH_version,PackKind,signer,flag,null);
    }

    /**
     * Builds a KEK rekeying packet with the needed header. Packet structure:
     * <table border="1" cellspacing="1">
     * <tr>
     * <td><div align="center"><strong>Packet kind</strong></div></td>
     * <td><div align="center"><strong>LKH version</strong></div></td>
     * <td><div align="center"><strong># of KEKs</strong></div></td>
     * <td><div align="center"><strong>signer KEY ID</strong></div></td>
     * <td><div align="center"><strong>KEK size</strong></div></td>
     * <td><div align="center"><strong>KEKs info</strong></div></td>
     * </tr>
     * <tr>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">X bytes (depending on cipher key used)</div></td>
     * </tr>
     * </table>
     * or
     * <table border="1" cellspacing="1">
     * <tr>
     * <td><div align="center"><strong>Packet kind</strong></div></td>
     * <td><div align="center"><strong>LKH version</strong></div></td>
     * <td><div align="center"><strong># of KEKs</strong></div></td>
     * <td><div align="center"><strong>signer KEY ID</strong></div></td>
     * <td><div align="center"><strong>KEK size</strong></div></td>
     * <td><div align="center"><strong>ID KEY node removed</strong></div></td>
     * <td><div align="center"><strong>KEKs info</strong></div></td>
     * </tr>
     * <tr>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">X bytes (depending on cipher key used)</div></td>
     * </tr>
     * </table>
     *
     * @param LKH_version
     * @param PackKind the kind of the packet.
     * @param signer the node object whose contained key will cipher the KEK data of the packet.
     * @param flag indicates the operation to realize.
     * 0 - a node joins in the group (welcome).
     * 1 - rekey (normal rekeying).
     * 2 - changes the key (sibling leaves).
     * 3 - changes the node key and sends sibling blind KEK (sibling joins).
     * 4 - changes the node key and removes a KEK (sibling root subtree leaves).
     * 5 - rekeys and removes a KEK (sibling root subtree leaves).
     * @param removed removed node.
     * @return the packet of bytes ready to be sent.
     */
    public static ByteArrayOutputStream buildCompleteKEKBytePacketOFT(int
            LKH_version,int PackKind,Node signer,int flag,Node removed){
        ByteArrayOutputStream packet=new ByteArrayOutputStream();

        packet.write(KEK.intTo4Bytes(PackKind),0,4);
        packet.write(KEK.intTo4Bytes(LKH_version),0,4);

        int numKEKs;
        byte[] cipheredKeys;

        switch(flag){
        case 0:
            numKEKs=signer.getName()[0]-1;
            cipheredKeys=buildKEKOFT(signer,0);
            break;
        case 1:
            numKEKs=1;
            cipheredKeys=buildKEKOFT(signer,1);
            break;
        case 2:
            numKEKs=1;
            cipheredKeys=buildKEKOFT(signer,2);
            break;
        case 3:
            numKEKs=2;
            cipheredKeys=buildKEKOFT(signer,3);
            break;
        case 4:
            numKEKs=1;
            cipheredKeys=buildKEKOFT(signer,4);
            break;
        case 5:
            numKEKs=1;
            cipheredKeys=buildKEKOFT(signer,5);
            break;
        default:
            numKEKs=0;
            cipheredKeys=buildKEKOFT(signer,1);
        }

        packet.write(KEK.intTo4Bytes(numKEKs),0,4);
        packet.write(KEK.intTo4Bytes(signer.getKEK().LKH_ID),0,4);
        if(numKEKs!=0){
            packet.write(KEK.intTo4Bytes(cipheredKeys.length/numKEKs),0,4);
        } else{
            packet.write(KEK.intTo4Bytes(0),0,4);
        }

        if(flag==4||flag==5){
            packet.write(KEK.intTo4Bytes(removed.getParent().getKEK().LKH_ID),0,
                         4);
        }

        packet.write(cipheredKeys,0,cipheredKeys.length);

        return packet;
    }

    /**
     * Builds a complete KEK rekeying packet with the needed header for Single Message.
     *
     * @param LKH_version int value specifying the LKH version that manages the rekeying processes in the key server.
     * @param PackKind the kind of the packet.
     * @param nodes implicated nodes in the rekeying.
     * @param P parameter P
     * @return a datagram packet ready to be sent.
     */
    public Message buildCompleteKEKPacketSingleMessageAddingNodes(int
            LKH_version,int PackKind,Vector nodes,SecretKey P){
        ByteArrayOutputStream packet=buildCompleteKEKBytePacketSingleMessage(
                LKH_version,PackKind,nodes,P);
        return sendSingleMessage(packet,nodes);
    }

    /**
     * Prepares a packet to be send for Single Message algorithm.
     *
     * @param packet the packet to be sent.
     * @param nodes Vector which contains the destination nodes.
     * @return the packet prepared for be send.
     */
    private Message sendSingleMessage(ByteArrayOutputStream packet,
                                      Vector nodes){
        System.out.print(
                "MULTICAST KEK packet (Encryption Single Message). ID keys:");
        System.out.print(" "+((Node)nodes.get(0)).getKEK().LKH_ID);
        for(int i=1;i<nodes.size();i++){
            System.out.print(", "+((Node)nodes.get(i)).getKEK().LKH_ID);
        }
        System.out.print(".\n");
        return new Message(packet.toByteArray(),msdmanager.getCache().getID(),
                           null,Message.KEY);
    }


    /**
     * Builds a KEK rekeying packet with the needed header for Single Message algorithm. Packet structure:
     *
     * <table border="1" cellspacing="1">
     * <tr>
     * <td><div align="center"><strong>Packet kind</strong></div></td>
     * <td><div align="center"><strong>LKH version</strong></div></td>
     * <td><div align="center"><strong># of KEKs</strong></div></td>
     * <td><div align="center"><strong>ciphered data size</strong></div></td>
     * <td><div align="center"><strong>ciphered data</strong></div></td>
     * </tr>
     * <tr>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">X bytes (depending on cipher key used)</div></td>
     * </tr>
     * </table>
     *
     * @param LKH_version int value specifying the LKH version that manages the rekeying processes in the key server.
     * @param PackKind the kind of the packet.
     * @param nodes implicated nodes in the rekeying.
     * @param P parameter P
     * @return the packet of bytes ready to be sent.
     */
    public static ByteArrayOutputStream buildCompleteKEKBytePacketSingleMessage(int
            LKH_version,int PackKind,Vector nodes,SecretKey P){
        ByteArrayOutputStream packet=new ByteArrayOutputStream();

        packet.write(KEK.intTo4Bytes(PackKind),0,4);
        packet.write(KEK.intTo4Bytes(LKH_version),0,4);

        int numKEKs=nodes.size();
        packet.write(KEK.intTo4Bytes(numKEKs),0,4);

        BigInteger cipheredKeys=new BigInteger(((Node)nodes.get(0)).getKEK().
                                               keyData.getEncoded().length*8,
                                               100,new Random());

        for(int i=0;i<nodes.size();i++){
            packet.write(KEK.intTo4Bytes(((Node)nodes.get(i)).getKEK().LKH_ID),
                         0,4);
            cipheredKeys=KeySingleMessage.multiply(cipheredKeys,
                    ((Node)nodes.get(i)).getKEK().keyData.getEncoded());
        }
        cipheredKeys=KeySingleMessage.add(cipheredKeys,P);
        packet.write(KEK.intTo4Bytes(cipheredKeys.toByteArray().length),0,4);
        packet.write(cipheredKeys.toByteArray(),0,
                     cipheredKeys.toByteArray().length);
        return packet;
    }

    /**
     * Builds a KEK rekeying packet with the needed header for Single Message algorithm when a node join in a group.
     * Packet structure:
     *
     * @param LKH_version int value specifying the LKH version that manages the rekeying processes in the key server.
     * @param PackKind the kind of the packet.
     * @param signer the node object whose contained key will cipher the KEK data of the packet.
     * @param P parameter P for update the keys.
     * @param oldkey previous key to rekeying.
     * @return a datagram packet ready to be sent.
     */
    public Message buildWelcomePacketSingleMessage(int LKH_version,int PackKind,
            Node signer,SecretKey P,SecretKey oldkey){
        ByteArrayOutputStream packet=buildWelcomeBytePacketSingleMessage(
                LKH_version,PackKind,signer,P,oldkey);
        return send(packet,signer);
    }

    /**
     * Builds a KEK rekeying packet with the needed header for Single Message algorithm when a node join in a group.
     * Packet structure:
     *
     * <table border="1" cellspacing="1">
     * <tr>
     * <td><div align="center"><strong>Packet kind</strong></div></td>
     * <td><div align="center"><strong>LKH version</strong></div></td>
     * <td><div align="center"><strong># of KEKs</strong></div></td>
     * <td><div align="center"><strong>signer KEY ID</strong></div></td>
     * <td><div align="center"><strong>KEK size</strong></div></td>
     * <td><div align="center"><strong>KEKs info</strong></div></td>
     * </tr>
     * <tr>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">X bytes (depending on cipher key used)</div></td>
     * </tr>
     * </table>
     *
     * @param LKH_version int value specifying the LKH version that manages the rekeying processes in the key server.
     * @param PackKind the kind of the packet.
     * @param signer the node object whose contained key will cipher the KEK data of the packet.
     * @param P parameter P for update the keys.
     * @param oldkey previous key to rekeying.
     * @return the packet of bytes ready to be sent.
     */
    public static ByteArrayOutputStream buildWelcomeBytePacketSingleMessage(int
            LKH_version,int PackKind,Node signer,SecretKey P,SecretKey oldkey){
        ByteArrayOutputStream packet=new ByteArrayOutputStream();

        packet.write(KEK.intTo4Bytes(PackKind),0,4);
        packet.write(KEK.intTo4Bytes(LKH_version),0,4);
        int numKEKs=signer.getName()[0];
        packet.write(KEK.intTo4Bytes(numKEKs),0,4);
        packet.write(KEK.intTo4Bytes(signer.getKEK().LKH_ID),0,4);
        byte[] cipheredKeys;
        cipheredKeys=buildKEKsPacketSM(signer,P,oldkey);
        packet.write(KEK.intTo4Bytes(cipheredKeys.length/numKEKs),0,4);
        packet.write(cipheredKeys,0,cipheredKeys.length);
        return packet;
    }

    /**
     * Builds a KEK rekeying packet with the needed header for Single Message algorithm when adds nodes in the path
     * to root.
     *
     * @param LKH_version int value specifying the LKH version that manages the rekeying processes in the key server.
     * @param PackKind the kind of the packet.
     * @param signer the node object whose contained key will cipher the KEK data of the packet.
     * @param P parameter P
     * @return a datagram packet ready to be sent.
     */
    public Message buildAddNodePacketSingleMessage(int LKH_version,int PackKind,
            Node signer,SecretKey P){
        ByteArrayOutputStream packet=buildAddNodeBytePacketSingleMessage(
                LKH_version,PackKind,signer,P);
        return send(packet,signer);
    }

    /**
     * Builds a KEK rekeying packet with the needed header for Single Message algorithm when adds nodes in the path
     * to root. Packet structure:
     *
     * <table border="1" cellspacing="1">
     * <tr>
     * <td><div align="center"><strong>Packet kind</strong></div></td>
     * <td><div align="center"><strong>LKH version</strong></div></td>
     * <td><div align="center"><strong>KEY ID</strong></div></td>
     * <td><div align="center"><strong>KEK size</strong></div></td>
     * <td><div align="center"><strong>KEKs info</strong></div></td>
     * <td><div align="center"><strong>P size</strong></div></td>
     * <td><div align="center"><strong>parameter P</strong></div></td>
     * </tr>
     * <tr>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">X bytes (depending on cipher key used)</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">X bytes (depending on cipher key used)</div></td>
     * </tr>
     * </table>
     *
     * @param LKH_version int value specifying the LKH version that manages the rekeying processes in the key server.
     * @param PackKind the kind of the packet.
     * @param signer the node object whose contained key will cipher the KEK data of the packet.
     * @param P parameter P
     * @return the packet of bytes ready to be sent.
     */
    public static ByteArrayOutputStream buildAddNodeBytePacketSingleMessage(int
            LKH_version,int PackKind,Node signer,SecretKey P){
        ByteArrayOutputStream packet=new ByteArrayOutputStream();

        packet.write(KEK.intTo4Bytes(PackKind),0,4);
        packet.write(KEK.intTo4Bytes(LKH_version),0,4);
        packet.write(KEK.intTo4Bytes(1),0,4);
        packet.write(KEK.intTo4Bytes(signer.getKEK().LKH_ID),0,4);

        byte[] cipheredKeys;
        cipheredKeys=buildKEKPacketSM(signer,P);

        packet.write(KEK.intTo4Bytes(cipheredKeys.length),0,4);
        packet.write(cipheredKeys,0,cipheredKeys.length);

        cipheredKeys=cipherParameterP(signer,P);
        packet.write(KEK.intTo4Bytes(cipheredKeys.length),0,4);
        packet.write(cipheredKeys,0,cipheredKeys.length);
        return packet;
    }

    /**
     * Build the black list for Single Message algorithm.
     *
     * @param LKH_version  int value specifying the LKH version that manages the rekeying processes in the key server.
     * @param PackKind the kind of the packet.
     * @param removedList the identificators for the black list.
     * @return a datagram packet ready to be sent.
     */
    public Message buildBlackListPacket(int LKH_version,int PackKind,
                                        Vector removedList){
        ByteArrayOutputStream packet=buildBlackListBytePacket(LKH_version,
                PackKind,removedList);
        return new Message(packet.toByteArray(),msdmanager.getCache().getID(),null,
                           Message.KEY);
    }

    /**
     * Build the black list for Single Message algorithm. Packet structure:
     *
     * <table border="1" cellspacing="1">
     * <tr>
     * <td><div align="center"><strong>Packet kind</strong></div></td>
     * <td><div align="center"><strong>LKH version</strong></div></td>
     * <td><div align="center"><strong>KEK size</strong></div></td>
     * <td><div align="center"><strong>Black List</strong></div></td>
     * </tr>
     * <tr>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">X bytes (depending on cipher key used)</div></td>
     * </tr>
     * </table>
     *
     * @param LKH_version  int value specifying the LKH version that manages the rekeying processes in the key server.
     * @param PackKind the kind of the packet.
     * @param removedList the identificators for the black list.
     * @return a datagram packet ready to be sent.
     */
    public static ByteArrayOutputStream buildBlackListBytePacket(int
            LKH_version,int PackKind,Vector removedList){
        ByteArrayOutputStream packet=new ByteArrayOutputStream();

        packet.write(KEK.intTo4Bytes(PackKind),0,4);
        packet.write(KEK.intTo4Bytes(LKH_version),0,4);
        packet.write(KEK.intTo4Bytes(removedList.size()),0,4);
        for(int i=0;i<removedList.size();i++){
            packet.write(KEK.intTo4Bytes(((KEK)removedList.get(i)).LKH_ID),0,4);
        }
        return packet;
    }

    /**
     * Unpacks the packet and returns the ciphered bytes contained in the packet.
     *
     * @param packet the bytes (data) received.
     * @param ClientData an int array where the needed values for decipher will be stored.
     * @return a byte array containing the ciphered bytes of the packet.
     */
    public static byte[] unbuildCompletePacket(byte[] packet,int[] ClientData){
        ByteArrayOutputStream bytetmp=new ByteArrayOutputStream();

        bytetmp.write(packet,0,4);
        ClientData[0]=KEK.BytesToInt(bytetmp.toByteArray());
        bytetmp.reset();
        bytetmp.write(packet,4,4);
        ClientData[1]=KEK.BytesToInt(bytetmp.toByteArray());
        bytetmp.reset();
        bytetmp.write(packet,8,4);
        ClientData[2]=KEK.BytesToInt(bytetmp.toByteArray());
        bytetmp.reset();
        bytetmp.write(packet,12,packet.length-12);

        return bytetmp.toByteArray();
    }

    /**
     * Unpacks the packet and returns the ciphered bytes contained in the packet for OFT algorithm.
     *
     * @param packet the bytes (data) received.
     * @return a byte array containing the ciphered bytes of the packet.
     */
    public static byte[] unbuildCompletePacket2(byte[] packet){
        ByteArrayOutputStream bytetmp=new ByteArrayOutputStream();

        bytetmp.write(packet,0,4);
        bytetmp.reset();
        bytetmp.write(packet,4,packet.length-4);

        return bytetmp.toByteArray();
    }

    /**
     * Unpacks the packet and returns the identificator of the removed node contained in the packet.
     *
     * @param packet the bytes (data) received.
     * @return the identificator of the removed node.
     */
    public static int unbuilRemovedNode(byte[] packet){
        ByteArrayOutputStream bytetmp=new ByteArrayOutputStream();

        bytetmp.write(packet,0,4);
        return KEK.BytesToInt(bytetmp.toByteArray());
    }

    /**
     * Builds a byte array containing all the KEKs to be sent in a rekeying packet.
     *
     * @param signer the node whose key will be the one which ciphers the keys data.
     * @return a byte array with the KEKs data to be sent.
     */
    private static byte[] buildKEKsPacket(Node signer){
        ByteArrayOutputStream keys=new ByteArrayOutputStream();

        SecretKey key=signer.getKEK().keyData;
        SymEncrypter cipher=new SymEncrypter(key.getAlgorithm(),key);

        for(Node node=signer.getParent();node!=null;node=node.getParent()){
            byte[] KEKpacket=node.getKEK().buildKEKpacket(cipher);
            keys.write(KEKpacket,0,KEKpacket.length);
        }
        return keys.toByteArray();
    }

    /**
     * Builds a byte array containing all the KEKs to be sent in a rekeying packet for OFT algorithm.
     *
     * @param signer the node whose key will be the one which ciphers the keys data.
     * @param flag indicates the operation to realize.
     * @return a byte array with the KEKs data to be sent.
     */
    private static byte[] buildKEKOFT(Node signer,int flag){
        ByteArrayOutputStream keys=new ByteArrayOutputStream();
        SecretKey key;
        if(flag==0||flag==1||flag==5){
            key=signer.getKEK().keyData;
        } else{
            key=signer.getOldKEK().keyData;
        }
        SymEncrypter cipher=new SymEncrypter(key.getAlgorithm(),key);

        switch(flag){
        case 0:
            Node node=signer;
            for(Node nodePath=signer;nodePath.getParent()!=null;
                              nodePath=nodePath.getParent()){
                node=nodePath.getSibling();
                packet(cipher,node.getBlindKEK().keyData,
                       node.getParent().getKEK().LKH_ID,keys);
            }
            break;

        case 1:
            packet(cipher,signer.getSibling().getBlindKEK().keyData,
                   signer.getSibling().getParent().getKEK().LKH_ID,keys);
            break;

        case 2:
            packet(cipher,signer.getKEK().keyData,signer.getKEK().LKH_ID,keys);
            break;

        case 3:
            packet(cipher,signer.getKEK().keyData,signer.getKEK().LKH_ID,keys);
            packet(cipher,signer.getSibling().getBlindKEK().keyData,
                   signer.getParent().getKEK().LKH_ID,keys);
            break;

        case 4:
            packet(cipher,signer.getKEK().keyData,signer.getKEK().LKH_ID,keys);
            break;

        case 5:
            packet(cipher,signer.getSibling().getBlindKEK().keyData,
                   signer.getSibling().getParent().getKEK().LKH_ID,keys);
            break;

        default:
            System.out.println("Error in rekeying.");;
        }
        return keys.toByteArray();
    }

    /**
     * Ciphers the data for OFT algorithm.
     *
     * @param cipher
     * @param keyData key of the node.
     * @param LKH_ID identificator of the node.
     * @param keys packet where to store the ciphered data.
     */
    private static void packet(SymEncrypter cipher,SecretKey keyData,int LKH_ID,
                               ByteArrayOutputStream keys){
        byte[] KEKpacket=(new KEK(keyData,LKH_ID)).buildKEKpacket(cipher);
        keys.write(KEKpacket,0,KEKpacket.length);
    }

    /**
     * Unpacks the ciphered KEKs contained in the byte array, deciphers the symmetric key data of the KEK and
     * returns all of them in a vector.
     *
     * @param numKEKs the number of KEKs that must be reconstructed from the bytes.
     * @param KEKsize the size in bytes of each KEK.
     * @param keys the bytes containing the KEKs.
     * @param key the key data needed to decipher the KEK data.
     * @return a vector containing all of the received KEKs.
     */
    public static Vector unbuildKEKsPacket(int numKEKs,int KEKsize,byte[] keys,
                                           KEK key){
        ByteArrayOutputStream cipheredKey=new ByteArrayOutputStream();
        Vector Keys=new Vector(0);
        SecretKey skey=key.keyData;
        SymEncrypter cipher=new SymEncrypter(skey.getAlgorithm(),skey);

        for(int i=0;i<numKEKs;i++){
            cipheredKey.reset();
            cipheredKey.write(keys,KEKsize*i,KEKsize);
            KEK newKey=KEK.unbuildKEKpacket(cipher,cipheredKey.toByteArray());
            Keys.addElement(newKey);

        }
        return Keys;
    }

    /**
     * Unpacks the ciphered KEK contained in the byte array, deciphers the symmetric key data of the KEK and
     * returns the key.
     *
     * @param keys the bytes containing the KEK.
     * @param key the key data needed to decipher the KEK data.
     * @param KEKsize the size in bytes of each KEK.
     * @return a vector containing all of the received KEKs.
     */
    public static KEK unbuildKEKsPacket(byte[] keys,KEK key,int KEKsize){
        ByteArrayOutputStream cipheredKey=new ByteArrayOutputStream();
        SecretKey skey=key.keyData;
        SymEncrypter cipher=new SymEncrypter(skey.getAlgorithm(),skey);

        cipheredKey.reset();
        cipheredKey.write(keys,0,KEKsize);
        KEK newKey=KEK.unbuildKEKpacket(cipher,cipheredKey.toByteArray());

        return newKey;
    }

    /**
     * Ciphers a Session Encryption Key to be sent to the multicast group.
     *
     * @param SEK the SEK to be ciphered.
     * @param rootKEK the KEK used to cipher.
     * @return a byte array containing the ciphered SEK.
     */
    private static byte[] cipherSEK(SecretKey SEK,KEK rootKEK){
        String keyAlgorithm=rootKEK.keyData.getAlgorithm();
        SymEncrypter cipher=new SymEncrypter(keyAlgorithm,rootKEK.keyData);
        return cipher.encrypt(SEK);
    }

    /**
     * Deciphers the SEK given bytes and returns the SEK in its SecretKey format.
     *
     * @param cipheredSEK the bytes containing the ciphered SEK data.
     * @param SEKsize the size in bytes of the SEK.
     * @param rootKEK the KEK used to decipher.
     */
    public static SecretKey decipherSEK(byte[] cipheredSEK,int SEKsize,
                                        KEK rootKEK){
        ByteArrayOutputStream cipheredKey=new ByteArrayOutputStream();
        String keyAlgorithm=rootKEK.keyData.getAlgorithm();
        SymEncrypter cipher=new SymEncrypter(keyAlgorithm,rootKEK.keyData);
        cipheredKey.write(cipheredSEK,0,SEKsize);
        return cipher.decrypt(cipheredKey.toByteArray(),keyAlgorithm);
    }

    /**
     *
     * @param signer the node object whose contained key will cipher the KEK data of the packet.
     * @param P parameter P for update the keys.
     * @param key key to cipher the data.
     * @return the ciphered keys.
     */
    private static byte[] buildKEKsPacketSM(Node signer,SecretKey P,
                                            SecretKey key){
        ByteArrayOutputStream keys=new ByteArrayOutputStream();

        SymEncrypter cipher=new SymEncrypter(key.getAlgorithm(),key);

        for(Node node=signer;node!=null;node=node.getParent()){
            byte[] KEKpacket=(new KEK(KeySingleMessage.XOR(node.getKEK().
                    keyData,P),node.getKEK().LKH_ID)).buildKEKpacket(cipher);
            keys.write(KEKpacket,0,KEKpacket.length);
        }
        return keys.toByteArray();
    }

    /**
     * Unpacks the packet and returns the ciphered bytes contained in the packet for Single Message algorithm.
     *
     * @param packet  packet the bytes (data) received.
     * @param ClientData an int array where the needed values for decipher will be stored.
     * @param identificators identificators of keys to decipher the packet.
     * @return a byte array containing the ciphered bytes of the packet.
     */
    public static byte[] unbuildCompletePacketSingleMessage(byte[] packet,
            int[] ClientData,Vector identificators){
        ByteArrayOutputStream bytetmp=new ByteArrayOutputStream();

        bytetmp.write(packet,0,4);
        ClientData[0]=KEK.BytesToInt(bytetmp.toByteArray());
        bytetmp.reset();

        int i;
        for(i=0;i<ClientData[0];i++){
            bytetmp.write(packet,4+(i*4),4);
            identificators.add(new Integer(KEK.BytesToInt(bytetmp.toByteArray())));
            bytetmp.reset();
        }
        bytetmp.write(packet,4+(i*4),4);
        ClientData[1]=KEK.BytesToInt(bytetmp.toByteArray());
        bytetmp.reset();
        bytetmp.write(packet,8+(i*4),ClientData[1]);
        return bytetmp.toByteArray();
    }

    /**
     * Unpacks the packet and returns the ciphered bytes contained in the packet for Single Message algorithm.
     * The packet contains the ciphered parameter P with symmetric cipher.
     *
     * @param packet  packet the bytes (data) received.
     * @param ClientData an int array where the needed values for decipher will be stored.
     * @return a byte array containing the ciphered bytes of the packet.
     */
    public static byte[] unbuildAddNodePacketSingleMessage(byte[] packet,
            int[] ClientData){
        ByteArrayOutputStream bytetmp=new ByteArrayOutputStream();

        bytetmp.write(packet,0,4);
        ClientData[0]=KEK.BytesToInt(bytetmp.toByteArray());
        bytetmp.reset();
        bytetmp.write(packet,4,4);
        ClientData[1]=KEK.BytesToInt(bytetmp.toByteArray());
        bytetmp.reset();
        bytetmp.write(packet,8,4);
        ClientData[2]=KEK.BytesToInt(bytetmp.toByteArray());
        bytetmp.reset();
        bytetmp.write(packet,12,ClientData[2]);
        bytetmp.reset();
        bytetmp.write(packet,12+ClientData[2],4);
        ClientData[3]=KEK.BytesToInt(bytetmp.toByteArray());
        bytetmp.reset();
        bytetmp.write(packet,16+ClientData[2],ClientData[3]);
        return bytetmp.toByteArray();
    }

    /**
     * Unpacks the packet and returns the ciphered bytes contained in the packet for Single Message algorithm.
     * The packet contains new KEKs for the path to root.
     *
     * @param packet  packet the bytes (data) received.
     * @param ClientData an int array where the needed values for decipher will be stored.
     * @return a byte array containing the ciphered bytes of the packet.
     */
    public static byte[] unbuildAddNodePPacketSingleMessage(byte[] packet,
            int[] ClientData){
        ByteArrayOutputStream bytetmp=new ByteArrayOutputStream();

        bytetmp.write(packet,0,4);
        ClientData[0]=KEK.BytesToInt(bytetmp.toByteArray());
        bytetmp.reset();
        bytetmp.write(packet,4,4);
        ClientData[1]=KEK.BytesToInt(bytetmp.toByteArray());
        bytetmp.reset();
        bytetmp.write(packet,8,4);
        ClientData[2]=KEK.BytesToInt(bytetmp.toByteArray());
        bytetmp.reset();
        bytetmp.write(packet,12,ClientData[2]);
        return bytetmp.toByteArray();
    }

    /**
     * Cipher the parent KEK for Single Message algorithm.
     *
     * @param signer the node object whose contained key will cipher the KEK data of the packet.
     * @param P parameter P
     * @return ciphered packet with the KEKs.
     */
    private static byte[] buildKEKPacketSM(Node signer,SecretKey P){
        ByteArrayOutputStream keys=new ByteArrayOutputStream();

        SecretKey key=signer.getKEK().keyData;
        SymEncrypter cipher=new SymEncrypter(key.getAlgorithm(),key);

        byte[] KEKpacket=(new KEK(KeySingleMessage.XOR(signer.getParent().
                getKEK().keyData,P),signer.getParent().getKEK().LKH_ID)).
                         buildKEKpacket(cipher);
        keys.write(KEKpacket,0,KEKpacket.length);

        return keys.toByteArray();
    }

    /**
     * Cipher parameter P using symmetric cipher.
     *
     * @param signer the node object whose contained key will cipher the data of the packet.
     * @param P parameter to cipher.
     * @return ciphered parameter.
     */
    private static byte[] cipherParameterP(Node signer,SecretKey P){
        ByteArrayOutputStream keys=new ByteArrayOutputStream();

        SecretKey key=signer.getKEK().keyData;
        SymEncrypter cipher=new SymEncrypter(key.getAlgorithm(),key);

        byte[] cipheredData=cipher.encrypt(P);
        keys.write(cipheredData,0,cipheredData.length);

        return keys.toByteArray();
    }

    /**
     * Decipher parameter P for Single Message algorith using symmetric cipher.
     *
     * @param KEKsize size of the ciphered data.
     * @param keys ciphered data.
     * @param key key to decipher the ciphered packet.
     * @return deciphered data.
     */
    public static SecretKey decipheParameterrP(int KEKsize,byte[] keys,KEK key){
        ByteArrayOutputStream cipheredKey=new ByteArrayOutputStream();
        SecretKey skey=key.keyData;
        SymEncrypter cipher=new SymEncrypter(skey.getAlgorithm(),skey);
        cipheredKey.reset();
        cipheredKey.write(keys,0,KEKsize);
        SecretKey keyData=cipher.decrypt(keys,key.keyData.getAlgorithm());
        return keyData;
    }

    /**
     * Unpacks the packet and returns the identificators of the black list.
     *
     * @param packet the bytes (data) received.
     * @return Vector which contains the identificators of the black list.
     */
    public static Vector unbuildBalckList(byte[] packet){
        ByteArrayOutputStream bytetmp=new ByteArrayOutputStream();

        bytetmp.write(packet,0,4);
        int numKeys=KEK.BytesToInt(bytetmp.toByteArray());
        Vector v=new Vector();
        for(int i=0;i<numKeys;i++){
            bytetmp.reset();
            bytetmp.write(packet,4+(i*4),4);
            v.add(new Integer(KEK.BytesToInt(bytetmp.toByteArray())));
        }
        return v;
    }

    /**
     * Unpacks the header packet contained in the byte array.
     *
     * @param packet the bytes containing the packet.
     * @param header returns the two first fields of the header.
     * @param numKEKs returns the number of KEKs contained in the packet.
     * @param identificators returns the identificator of the needed KEK for decipher the packet.
     * @return the data contained in the packet.
     */
    public static byte[] unbuildHeader(byte[] packet,int[] header,int numKEKs,
                                       Vector identificators){
        ByteArrayOutputStream bytetmp=new ByteArrayOutputStream();

        bytetmp.write(packet,0,4);
        header[0]=KEK.BytesToInt(bytetmp.toByteArray());
        bytetmp.reset();
        bytetmp.write(packet,4,4);
        header[1]=KEK.BytesToInt(bytetmp.toByteArray());
        bytetmp.reset();
        bytetmp.write(packet,8,4);
        numKEKs=KEK.BytesToInt(bytetmp.toByteArray());
        bytetmp.reset();

        int indice=0;
        switch(header[0]){
        case KEK_SINGLEMESSAGE:
            for(indice=0;indice<numKEKs;indice++){
                bytetmp.reset();
                bytetmp.write(packet,12+(indice*4),4);
                identificators.add(new Integer(KEK.BytesToInt(bytetmp.
                        toByteArray())));
            }
            break;

        default:
            bytetmp.write(packet,12,4);
            identificators.add(new Integer(KEK.BytesToInt(bytetmp.toByteArray())));
            break;
        }
        bytetmp.reset();
        bytetmp.write(packet,8,packet.length-8);
        return bytetmp.toByteArray();
    }

    /** Set a new message for LKH from an specific network.
     * @param m The message
     * @param network The generic name of the network
     */
    public static void setMessage(Message m,String network){
        try{
            Vector v=null;
            // synchronizing with messages not to create different vectors
            // here and in the UDPReceive method.
            synchronized(messages){
                v=(Vector)messages.get(network);
                if(v==null){
                    v=new Vector();
                    messages.put(network,v);
                }
            }
            // add a message to the vector
            synchronized(v){
                v.add(m);
                v.notifyAll();
            }
        } catch(Exception e){
            e.printStackTrace();
        }
    }
}
