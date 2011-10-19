package secureMulticast.keyDistribution.algorithm;

import secureMulticast.event.LKHChangesSupport;
import secureMulticast.event.LKHEvent;
import secureMulticast.util.Conversion;
import secureMulticast.binaryTree.Tree;
import secureMulticast.binaryTree.Node;
import secureMulticast.keyDistribution.net.MSDMconnection;

import org.msd.proxy.MSDManager;

import javax.crypto.SecretKey;
import java.util.Vector;
import java.util.Random;

/**
 * <p> This class is an implementation of the One-way Function Tree Logical Key Hierarchy. It allows member joinings and
 * leavings, and does automatically the rekeying process after a time interval.
 *
 * @author  Oscar Burgos & Daniel Jarne
 * @version 1.1, 01/10/04
 */
public class OFT extends LKH{
    ////////////////////////////////////////////////////////////////////////////
    //////// Class constructors ////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructs a new One-way Function Tree LKH algorithm object. The constructor gives all the parameters needed to
     * start the algorithm execution.
     *
     * @param keyAlgorithm the algorithm used to generate the symmetric keys.
     * @param groupAddress the multicast group IP address.
     * @param port the UDP port used by the group.
     * @param notification the object used to trigger the LKH events.
     */
    public OFT(int keyAlgorithm,MSDManager msd,LKHChangesSupport notification){
        tree=new Tree(keyAlgorithm);
        this.notification=notification;
        welcomed=new Vector(0);
        blackList=new Vector(0);
        UDPsender=new MSDMconnection(msd);
        UDPsender.buildSendingSocket();
    }

    /**
     * Returns an int value describing the One-way Function Tree LKH algorithm used.
     *
     * @return an int value describing the algorithm.
     */
    public int LKH_version(){
        return OFTSIMPLE;
    }

    /**
     * Adds a new node (new member) to the LKH Tree structure. It is a synchronized method because it modifies
     * the welcomed vector.
     *
     * @param newMember the new member's associated node (with its MemberID and KEK).
     */
    public synchronized void memberJoining(Node newMember){
        welcomed.addElement(newMember);
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
                if(performer.isLeaf()){
                    performer.setKEK(tree.generateKEK(performer.hashCode()));

                    if(!performer.isRoot()){
                        tree.recalculateKeyOFT();
                    }

                    UDPsender.UDPsend(UDPsender.buildCompleteKEKPacketOFT(LKH.
                            OFTSIMPLE,MSDMconnection.KEK_LKH,performer,2));

                    for(Node keyPath=performer;keyPath!=tree.getRoot();
                            keyPath=keyPath.getParent()){
                        Node keyPathsibling=keyPath.getSibling();
                        UDPsender.UDPsend(UDPsender.buildCompleteKEKPacketOFT(
                                LKH.OFTSIMPLE,MSDMconnection.KEK_LKH,
                                keyPathsibling,1));
                    }
                } else{
                    Node randomNode=performer;
                    Random rand=new Random();

                    while(!randomNode.isLeaf()){
                        if(randomNode.getLeftSon().getSuccessorsDepth()<
                           randomNode.getRightSon().getSuccessorsDepth()){
                            randomNode=randomNode.getLeftSon();
                        } else if(randomNode.getLeftSon().getSuccessorsDepth()>
                                  randomNode.getRightSon().getSuccessorsDepth()){
                            randomNode=randomNode.getRightSon();
                        } else{
                            if(rand.nextInt(2)==1){
                                randomNode=randomNode.getLeftSon();
                            } else{
                                randomNode=randomNode.getRightSon();
                            }
                        }
                    }
                    randomNode.setKEK(tree.generateKEK(randomNode.hashCode()));
                    tree.recalculateKeyOFT();

                    UDPsender.UDPsend(UDPsender.buildCompleteKEKPacketOFT(LKH.
                            OFTSIMPLE,MSDMconnection.KEK_OFT,randomNode,4,
                            removed));

                    for(Node keyPath=randomNode;keyPath!=performer;
                            keyPath=keyPath.getParent()){
                        Node keyPathsibling=keyPath.getSibling();
                        UDPsender.UDPsend(UDPsender.buildCompleteKEKPacketOFT(
                                LKH.OFTSIMPLE,MSDMconnection.KEK_OFT,
                                keyPathsibling,5,removed));
                    }

                    for(Node keyPath=performer;keyPath!=tree.getRoot();
                            keyPath=keyPath.getParent()){
                        Node keyPathsibling=keyPath.getSibling();
                        UDPsender.UDPsend(UDPsender.buildCompleteKEKPacketOFT(
                                LKH.OFTSIMPLE,MSDMconnection.KEK_LKH,
                                keyPathsibling,1));
                    }
                }
            }
        } else if(!welcomed.isEmpty()){
            performer=tree.insertNode((Node)welcomed.get(0));
            welcomed.clear();

            notification.triggerLKHChange(LKHEvent.TREE_UPDATED,performer);

            if(performer.getSibling()!=null){
                performer.getSibling().setKEK(tree.generateKEK(performer.
                        getSibling().hashCode()));
            }

            System.out.println(
                    "\n>>>>--->>>--->>--->---NEW REKEYING---<---<<---<<<---<<<<");

            if(!performer.isRoot()){
                tree.recalculateKeyOFT();
            }

            UDPsender.UDPsend(UDPsender.buildCompleteKEKPacketOFT(LKH.OFTSIMPLE,
                    MSDMconnection.KEK_LKH,performer,0));

            if(performer.getSibling()!=null){
                UDPsender.UDPsend(UDPsender.buildCompleteKEKPacketOFT(LKH.
                        OFTSIMPLE,MSDMconnection.KEK_LKH,performer.getSibling(),
                        3));
            }

            if(performer.getParent()!=null){
                for(Node keyPath=performer.getParent();keyPath!=tree.getRoot();
                                 keyPath=keyPath.getParent()){
                    Node keyPathsibling=keyPath.getSibling();
                    UDPsender.UDPsend(UDPsender.buildCompleteKEKPacketOFT(LKH.
                            OFTSIMPLE,MSDMconnection.KEK_LKH,keyPathsibling,1));
                }
            }
        } else{
            return;
        }

        SecretKey newSEK=genNewSEK();
        System.out.println("\nNew Session Key value - "+Conversion.asHex(newSEK)+
                           "\n");
        if(!tree.isEmpty()){
            UDPsender.UDPsend(UDPsender.buildSEKPacket(LKH.OFTSIMPLE,
                    tree.getRoot(),newSEK));
        }
        notification.triggerLKHChange(LKHEvent.NEW_SEK,newSEK);

        System.out.println(
                ">>>>--->>>--->>--->---END REKEYING---<---<<---<<<---<<<<");
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
