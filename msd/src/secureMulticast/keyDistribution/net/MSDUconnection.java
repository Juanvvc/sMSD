package secureMulticast.keyDistribution.net;

import secureMulticast.keyDistribution.symKeyGen.*;
import secureMulticast.keyDistribution.cipher.*;

import org.msd.proxy.MSDManager;
import org.msd.comm.*;

import javax.crypto.*;
import javax.crypto.spec.*;
import javax.crypto.interfaces.*;
import java.io.*;
import java.security.*;
import java.security.spec.*;

import java.util.Hashtable;

/**
 * <p> This class is the one that with its constructor and methods allows doing any of the TCP processes needed
 * in any of the other classes that set up the package. It is responsible of construct the sockets, build the packets,
 * sending and receiving them, etc. All in a TCP scenario.
 *
 * @author  Oscar Burgos & Daniel Jarne
 * @version 1.1, 01/10/04
 */
public class MSDUconnection{
    ////////////////////////////////////////////////////////////////////////////
    //////// Static and basic TCPconnection fields /////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Defines an int value for internal use of the class that helps to differentiate between a server and a client.
     */
    private static final int SERVER=0;

    /**
     * Defines an int value for internal use of the class that helps to differentiate between a server and a client.
     */
    private static final int CLIENT=1;

    private MSDManager msdmanager;
    private Connection connection;
    private String id=null;

    /**
     * Specifies who is the one that has instantiated the class (a server or a client). That is needed in the process
     * of establishment of a secure channel through the Diffie-Hellman protocol.
     */
    private int whoami;

    ////////////////////////////////////////////////////////////////////////////
    //////// Class constructors ////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructs a TCP communications' responsible object. This constructor is used by the client to connect to the
     * server. It builds the remote socket and the streams.
     *
     * @param remoteAddress the address of the server we want to connect.
     * @param port the port where the server is listening.
     */
    public MSDUconnection(MSDManager msd,int type,NetworkManager net){
        whoami=CLIENT;
        msdmanager=msd;
        try{
            connection=msd.getConnection(type,net.getMSDMain());
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Constructs a TCP communications' responsible object. This constructor is used by the server to receive data
     * froma a client.
     *
     * @param socket the socket connecting to the client.
     */
    public MSDUconnection(MSDManager msd,Connection con){
        whoami=SERVER;
        connection=con;
        msdmanager=msd;
    }

    ////////////////////////////////////////////////////////////////////////////
    //////// Class methods /////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /** Get the identfier of the other extreme of the connection */
    public String getID(){
        return id;
    }

    /**
     * Closes the socket.
     */
    public void close(){
        try{
            connection.close();
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Reads bytes from the socket stream.
     *
     * @return an array of bytes containing the data readen from the stream.
     */
    public byte[] read(){
        byte[] packet=null;

        try{
            Message m=connection.receive();
            id=m.getIDFrom();
            packet=m.getData();
        } catch(Exception e){
            try{
                e.printStackTrace();
            } catch(Throwable t){
                System.err.println("Error while tracing error "+e);
            }
        }

        return packet;
    }

    /**
     * Writes bytes to the socket stream.
     *
     * @param packet an array of bytes containing the data to be sent.
     */
    public void write(byte[] packet){
        try{
            Message m=new Message(packet,msdmanager.getCache().getID(),id,
                                  connection.getType());
            m.setEncode(false);
            connection.send(m);
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Builds a packet containing the initial data that a client needs, such as its unique ID, its own KEK and the
     * LKH algorithm used; ready to be sent. The critical data is ciphered (member ID and KEK data). Packet structure:
     *
     * <table border="1" cellspacing="1">
     * <tr>
     * <td><div align="center"><strong>LKH version</strong></div></td>
     * <td><div align="center"><strong>member ID</strong></div></td>
     * <td><div align="center"><strong>KEK size</strong></div></td>
     * <td><div align="center"><strong>KEK info</strong></div></td>
     * </tr>
     * <tr>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">8 bytes (ciphered)</div></td>
     * <td><div align="center">4 bytes</div></td>
     * <td><div align="center">X bytes (depending on cipher key used)</div></td>
     * </tr>
     * </table>
     *
     * @param LKH_version an int value specifying the LKH algorithm used in the server.
     * @param LKH_ID the unique member identifier.
     * @param key the KEK object containing the KEK data associated to the member.
     * @param skey the symmetric key (obtained via the D-H algorithm) to cipher the critical data.
     * @return the byte array containing the packet to be sent.
     */
    public static byte[] buildTCPpacket(int LKH_version,int LKH_ID,KEK key,
                                        SecretKey skey){
        ByteArrayOutputStream packet=new ByteArrayOutputStream();
        SymEncrypter enc=new SymEncrypter(skey.getAlgorithm(),skey);

        packet.write(KEK.intTo4Bytes(LKH_version),0,4);
        packet.write(enc.encrypt(KEK.intTo4Bytes(LKH_ID)),0,8);
        byte[] cipheredkekpacket=key.buildKEKpacket(enc);
        int KEKsize=cipheredkekpacket.length;
        packet.write(KEK.intTo4Bytes(KEKsize),0,4);
        packet.write(cipheredkekpacket,0,KEKsize);

        return packet.toByteArray();
    }

    /**
     * Unpacks the received packet, deciphers the member ID and the KEK data, and stores the critical values in the
     * ClienData array.
     *
     * @param packet the bytes to unpack.
     * @param ClientData an int array where the header values will be stored.
     * @param skey the symmetric key needed to decipher the ciphered bytes.
     * @return a KEK object, which is the member's own KEK.
     */
    public static KEK unbuildTCPpacket(byte[] packet,int[] ClientData,
                                       SecretKey skey){
        ByteArrayOutputStream bytetmp=new ByteArrayOutputStream();
        SymEncrypter enc=new SymEncrypter(skey.getAlgorithm(),skey);

        bytetmp.write(packet,0,4);
        ClientData[0]=KEK.BytesToInt(bytetmp.toByteArray());
        bytetmp.reset();
        bytetmp.write(packet,4,8);
        ClientData[1]=KEK.BytesToInt(enc.decrypt(bytetmp.toByteArray()));
        bytetmp.reset();
        bytetmp.write(packet,12,4);
        int KEKsize=KEK.BytesToInt(bytetmp.toByteArray());
        bytetmp.reset();
        bytetmp.write(packet,16,KEKsize);
        return KEK.unbuildKEKpacket(enc,bytetmp.toByteArray());
    }

    /**
     * Resizes an array to the specified new size.
     *
     * @param array the byte array to resize.
     * @param newSize the desired new size.
     * @return the byte array resized to the new size.
     */
    private static byte[] resizeArray(byte[] array,int newSize){

        byte[] newArray=new byte[newSize];
        int lastElement=Math.min(newSize,array.length);
        System.arraycopy(array,0,newArray,0,lastElement);

        return newArray;
    }

    /**
     * Generates a symmetic key to establish a secure channel according to the Diffie-Hellman algorithm without the
     * need of sending the key through the channel. This method is called from both the client and the server.
     *
     * @return a symmetric key which will be used to cipher the session data.
     */
    public SecretKey genDHsessionKey(){
        PublicKey publicKey;
        PrivateKey privateKey;
        SecretKey secretKey=null;

        try{
            if(whoami==SERVER){
                // Create the parameter generator for a 512-bit DH key pair
                AlgorithmParameterGenerator paramGen=
                        AlgorithmParameterGenerator.getInstance("DH");
                paramGen.init(512);

                // Generate the parameters
                AlgorithmParameters params=paramGen.generateParameters();
                DHParameterSpec dhSpec=(DHParameterSpec)params.getParameterSpec(
                        DHParameterSpec.class);

                // Use the values to generate a key pair
                KeyPairGenerator keyGen=KeyPairGenerator.getInstance("DH");
                keyGen.initialize(dhSpec);
                KeyPair keypair=keyGen.generateKeyPair();

                // Get the generated public and private keys
                privateKey=keypair.getPrivate();

                // Send the public key bytes to the other party...
                write(keypair.getPublic().getEncoded());
                // Retrieve the public key bytes of the other party
                publicKey=getPartnersPublicKey(read());
            } else{ //if(whoami == CLIENT)
                // Retrieve the public key bytes of the other party
                publicKey=getPartnersPublicKey(read());

                // Use the parameters specified in server's public key.
                DHParameterSpec dhSpec=((DHPublicKey)publicKey).getParams();

                // Use the values to generate a key pair
                KeyPairGenerator keyGen=KeyPairGenerator.getInstance("DH");
                keyGen.initialize(dhSpec);
                KeyPair keypair=keyGen.generateKeyPair();

                // Get the generated private key
                privateKey=keypair.getPrivate();
                write(keypair.getPublic().getEncoded());
            }

            // Prepare to generate the secret key with the private key and public key of the other party
            KeyAgreement ka=KeyAgreement.getInstance("DH");
            ka.init(privateKey);
            ka.doPhase(publicKey,true);

            // Generate the secret key
            secretKey=ka.generateSecret("DESede");
        } catch(InvalidKeyException e){
            e.printStackTrace();
        } catch(InvalidAlgorithmParameterException e){
            e.printStackTrace();
        } catch(NoSuchAlgorithmException e){
            e.printStackTrace();
        } catch(InvalidParameterSpecException e){
            e.printStackTrace();
        }

        return secretKey;
    }

    /**
     * Converts the public key bytes into a PublicKey object.
     *
     * @param publicKeyBytes the bytes containing public key data received from other party.
     * @return a public key object.
     */
    private static PublicKey getPartnersPublicKey(byte[] publicKeyBytes){
        try{
            X509EncodedKeySpec x509KeySpec=new X509EncodedKeySpec(
                    publicKeyBytes);
            KeyFactory keyFact=KeyFactory.getInstance("DH");
            PublicKey publicKey=keyFact.generatePublic(x509KeySpec);
            return publicKey;
        } catch(NoSuchAlgorithmException e){
            e.printStackTrace();
        } catch(InvalidKeySpecException e){
            e.printStackTrace();
        }
        return null;
    }

    private static Hashtable newConnections=new Hashtable();
    /** Block until a new connection involving LKH arrives from an
     * specific network.
     * @param network The generic name of the network to listen.
     * @return A new from connection from this network.
     * @throws java.lang.Exception If anything gos wrong */
    public static Connection accept(String network) throws Exception{
        Connection c=(Connection)newConnections.get(network);
        while(c==null){
            synchronized(newConnections){
                try{
                    System.out.println("Waiting for LKH conn on "+network); //@@l
                    newConnections.wait();
                    c=(Connection)newConnections.get(network);
                } catch(Exception e){
                    e.printStackTrace();
                }
            }
        }
        newConnections.remove(network);
        return c;
    }

    /** Set a new connection related to LKH for an specific network.
     * @param c The connection.
     * @param network The generic name for the network, */
    public static void setConnection(Connection c,String network){
        try{
            if(newConnections.get(network)!=null){
                System.err.println("There is a connection in queue: ignoring");
                return;
            }
            synchronized(newConnections){
                newConnections.put(network,c);
                newConnections.notifyAll();
            }
        } catch(Exception e){
            e.printStackTrace();
        }
    }
}
