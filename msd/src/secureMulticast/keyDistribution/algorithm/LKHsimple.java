package secureMulticast.keyDistribution.algorithm;

import secureMulticast.binaryTree.*;
import secureMulticast.keyDistribution.net.*;
import secureMulticast.util.*;
import secureMulticast.event.LKHEvent;
import secureMulticast.event.LKHChangesSupport;

import javax.crypto.*;
import java.util.*;

import org.msd.proxy.MSDManager;

/**
 * <p> This class is an implementation of the Lam-Gouda Logical Key Hierarchy. It allows member joinings and leavings,
 * and does automatically the rekeying process like the original algorithm specifies.
 *
 * @author  Oscar Burgos & Daniel Jarne
 * @version 1.1, 01/10/04
 */
public class LKHsimple extends LKH{
    ////////////////////////////////////////////////////////////////////////////
    //////// Class constructors ////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructs a new LKH algorithm object. The constructor gives all the parameters needed to start the algorithm
     * execution.
     *
     * @param keyAlgorithm the algorithm used to generate the symmetric keys.
     * @param groupAddress the multicast group IP address.
     * @param port the UDP port used by the group.
     * @param notification the object used to trigger the LKH events.
     */
    public LKHsimple(int keyAlgorithm,MSDManager msd,
                     LKHChangesSupport notification){
        tree=new Tree(keyAlgorithm);
        this.notification=notification;
        welcomed=new Vector(0);
        blackList=new Vector(0);
        UDPsender=new MSDMconnection(msd);
        UDPsender.buildSendingSocket();
    }

    ////////////////////////////////////////////////////////////////////////////
    //////// Class methods /////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Returns an int value describing the LKH algorithm used.
     *
     * @return an int value describing the algorithm.
     */
    public int LKH_version(){
        return LKHSIMPLE;
    }

    /**
     * Adds a new node (new member) to the LKH Tree structure. It is a synchronized method because it modifies
     * the welcomed vector.
     *
     * @param newMember the new member's associated node (with its MemberID and KEK).
     */
    public synchronized void memberJoining(Node newMember){
        welcomed.addElement(newMember);
        // Graphics
        //process();
        notification.triggerLKHChange(LKHEvent.JOINING,newMember);
    }

    /**
     * Removes the node with the specified ID (member leaving) from the LKH Tree structure. It is a synchronized
     * method because modifies the blackList vector.
     *
     * @param identifier the member's ID that is leaving the group.
     */
    public synchronized void memberLeaving(int identifier){
        Node n=tree.getNodeByMemberIdentifier(identifier);
        blackList.addElement(n);
        // Graphics
        //process();
        notification.triggerLKHChange(LKHEvent.LEAVING,n);
    }

    /**
     * Makes all the rekeying process according to the elements stored in the vectors (the new members, the leaving
     * members, etc). It must be implemented as a synchronized method because in the redefinition it should modify
     * the welcomed, the blackList and the changesList vectors.
     */
    public synchronized void process(){
        Node performer;

        if(!blackList.isEmpty()){
            Node removed=(Node)blackList.get(0);
            notification.triggerLKHChange(LKHEvent.DISCARD_NODE,removed);
            performer=tree.removeNode(removed);
            blackList.clear();

            System.out.println(
                    "\n>>>>--->>>--->>--->---NEW REKEYING---<---<<---<<<---<<<<");
            if(performer!=null){
                updatePathKeys(performer);
                UDPsender.UDPsend(UDPsender.buildCompleteKEKPacket(LKH.
                        LKHSIMPLE,MSDMconnection.KEK_LKH,performer));
            }
        } else if(!welcomed.isEmpty()){
            performer=tree.insertNode((Node)welcomed.get(0));
            welcomed.clear();

            notification.triggerLKHChange(LKHEvent.TREE_UPDATED,performer);

            System.out.println(
                    "\n>>>>--->>>--->>--->---NEW REKEYING---<---<<---<<<---<<<<");

            updatePathKeys(performer);
            if(performer!=tree.getRoot()){
                UDPsender.UDPsend(UDPsender.buildCompleteKEKPacket(LKH.
                        LKHSIMPLE,MSDMconnection.KEK_LKH,performer));
            }
        } else{
            return;
        }

        for(Node keyPath=performer;keyPath!=tree.getRoot();
                keyPath=keyPath.getParent()){
            Node keyPathsibling=keyPath.getSibling();
            UDPsender.UDPsend(UDPsender.buildCompleteKEKPacket(LKH.LKHSIMPLE,
                    MSDMconnection.KEK_LKH,keyPathsibling));
        }

        SecretKey newSEK=genNewSEK();
        System.out.println("\nNew Session Key value - "+Conversion.asHex(newSEK)+
                           "\n");
        if(!tree.isEmpty()){
            UDPsender.UDPsend(UDPsender.buildSEKPacket(LKH.LKHSIMPLE,
                    tree.getRoot(),newSEK));
        }
        notification.triggerLKHChange(LKHEvent.NEW_SEK,newSEK);

        System.out.println(
                ">>>>--->>>--->>--->---END REKEYING---<---<<---<<<---<<<<");
    }

    /**
     * Updates the KEKs in the path from the node passed as parameter to the root of the LKH tree.
     *
     * @param node the node from which the KEKs must be replaced.
     */
    private void updatePathKeys(Node node){
        Vector performerPartners=node.pathToRoot();
        for(int i=0;i<performerPartners.size();i++){
            Node partner=(Node)performerPartners.get(i);
            partner.setKEK(genNewKEK(partner.hashCode()));
        }
    }

    /**
     * Starts the algorithm execution (it only takes effect in case of the batch and balanced batch LKH because they
     * implement the Runnable class).
     * Has no effect in this algorithm implementation.
     */
    public void start(){
    }

    /**
     * Stops the algorithm execution (it only takes effect in case of the batch and balanced batch LKH because they
     * implement the Runnable class).
     * Has no effect in this algorithm implementation.
     */
    public void stop(){
    }
}
