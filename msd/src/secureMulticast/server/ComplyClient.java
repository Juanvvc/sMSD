package secureMulticast.server;

import secureMulticast.keyDistribution.algorithm.*;
import secureMulticast.keyDistribution.symKeyGen.*;
import secureMulticast.keyDistribution.net.*;
import secureMulticast.binaryTree.*;
import secureMulticast.util.Conversion;

import javax.crypto.*;

/**
 * <p> This class is the one to which the LKH server delegates the task of serving the clients' requests. It is
 * responsible of the joining and leaving processes. In the joining process it takes advantage of methods of the
 * TCPconnection class to give a secure communication between the new member and the server because of the critical
 * information that flows through the channel such as the unique identifier, the member's own symmetric key (own KEK),
 * etc. The method start must be called in order to begin the process specified in the constructor.
 *
 * @author  Oscar Burgos & Daniel Jarne
 * @version 1.1, 01/10/04
 */
public class ComplyClient extends Thread{
    ////////////////////////////////////////////////////////////////////////////
    //////// Static and basic ComplyClient fields //////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Defines an int value for the joining process.
     */
    public static final int JOINING=0;

    /**
     * Defines an int value for the leaving process.
     */
    public static final int LEAVING=1;

    /**
     * Specifies the algortithm object used to manage the the tree that structures the secure multcast group.
     */
    private LKH algorithm;

    /**
     * Specifies a MemberID object to
     */
    private MemberID memberID;

    /**
     * Specifies the object used to take care of the TCP communications.
     */
    private MSDUconnection client;

    /**
     * Specifies the service given to the key server (joining or leaving service).
     */
    private int Service;

    ////////////////////////////////////////////////////////////////////////////
    //////// Class constructors ////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructs a ComplyClient object capable of processing joining and leaving requests from
     * members.
     *
     * @param algorithm the LKH algorithm that manages the multicast group in the server.
     * @param client the object that manages the TCP communications with the client (the member).
     * @param Service the service which is going to give this class (joining or leaving service).
     */
    public ComplyClient(LKH algorithm,MSDUconnection client,int Service){
        this.algorithm=algorithm;
        this.client=client;
        memberID=new MemberID(Integer.valueOf(client.getID()).intValue());
        this.Service=Service;
    }

    ////////////////////////////////////////////////////////////////////////////
    //////// Class methods /////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Makes possible the joining and leaving processes. In the joining process establishes a Diffie-Hellman
     * protocol to assure a ciphered channel to transmit the critical information at the beginning of the session.
     * In the leaving protocol, establishes a protocol to know which of the members is the one which wants to
     * leave the secure multicast group.
     */
    public void run(){
        try{
            switch(Service){
            case JOINING:
                System.out.println("Starting D-H algorithm...");
                SecretKey sessionKey=client.genDHsessionKey();
                System.out.println("D-H session key created (algorithm - "+
                                   sessionKey.getAlgorithm()+", value - "+
                                   Conversion.asHex(sessionKey)+")");

                Node newMember=new Node(memberID);
                int id=newMember.getMemberID().identifier;
                newMember.setKEK(algorithm.genNewKEK(id));
                KEK kek=newMember.getKEK();
                System.out.println("Sending member's ID ("+id+
                                   ") and node's associated KEK (algorithm - "+
                                   kek.keyData.getAlgorithm()+", value - "+
                                   Conversion.asHex(kek.keyData)+")");

                int LKH_version=algorithm.LKH_version();
                client.write(MSDUconnection.buildTCPpacket(LKH_version,
                        memberID.identifier,kek,sessionKey));

                try{
                    // close the connection after END message
                    
                    client.read();
                    client.close();
                    System.out.println(
                    ">>>>--->>>--->>--->---END REQUEST---<---<<---<<<---<<<<");
                } catch(Exception e){
                    System.err.print("Error while closing connection: "+e);
                }

                algorithm.memberJoining(newMember);

                break;
            case LEAVING:
                client.write("ID".getBytes());
                int identifier=KEK.BytesToInt(client.read());
                System.out.println("Receiving member's ID "+identifier);
                System.out.println("Excluding member with ID "+identifier);
                client.write("Out of the multicast group".getBytes());
                algorithm.memberLeaving(identifier);
                try{
                    client.close();
                }catch(Exception e){
                    System.err.println("Error while closing connection: "+e);
                }
                System.out.println(
                ">>>>--->>>--->>--->---END REQUEST---<---<<---<<<---<<<<");
                break;

            }
        } catch(IllegalStateException e){
            e.printStackTrace();
        }
       }
}
