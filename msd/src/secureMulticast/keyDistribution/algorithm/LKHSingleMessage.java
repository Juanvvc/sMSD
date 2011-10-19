package secureMulticast.keyDistribution.algorithm;

import secureMulticast.binaryTree.Node;
import secureMulticast.binaryTree.Tree;
import secureMulticast.keyDistribution.net.MSDMconnection;
import secureMulticast.keyDistribution.symKeyGen.KEK;
import secureMulticast.keyDistribution.symKeyGen.KeySingleMessage;
import secureMulticast.util.Conversion;
import secureMulticast.event.LKHChangesSupport;
import secureMulticast.event.LKHEvent;

import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.util.Vector;
import java.util.Random;
import java.math.BigInteger;

import org.msd.proxy.MSDManager;

/**
 * <p> This class is an implementation of the Single Message Logical Key Hierarchy. It allows member joinings and
 * leavings, and does automatically the rekeying process after a time interval.
 *
 * @author  Oscar Burgos & Daniel Jarne
 * @version 1.1, 01/10/04
 */
public class LKHSingleMessage extends LKH{
    ////////////////////////////////////////////////////////////////////////////
    //////// LKHSingleMessage fields ///////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Defines the parameter which will allow to update the client's keys.
     */
    SecretKey P;

    /**
     * Defines a list contains the removed nodes.
     */
    Vector removedList=new Vector();


    ////////////////////////////////////////////////////////////////////////////
    //////// Class constructors ////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructs a new Single Message LKH algorithm object. The constructor gives all the parameters needed to start
     * the algorithm execution.
     *
     * @param keyAlgorithm the algorithm used to generate the symmetric keys.
     * @param groupAddress the multicast group IP address.
     * @param port the UDP port used by the group.
     * @param notification the object used to trigger the LKH events.
     */
    public LKHSingleMessage(int keyAlgorithm,MSDManager msd,
                            LKHChangesSupport notification){
        tree=new Tree(keyAlgorithm);
        this.notification=notification;
        welcomed=new Vector(0);
        blackList=new Vector(0);
        UDPsender=new MSDMconnection(msd);
        UDPsender.buildSendingSocket();
        KeySingleMessage.setSeed((new BigInteger(KeySingleMessage.
                                                 calculateParameterR(tree).
                                                 getEncoded().length*8,
                                                 new Random())).toByteArray(),
                                 KeySingleMessage.calculateParameterR(tree).
                                 getEncoded().length);
    }

    ////////////////////////////////////////////////////////////////////////////
    //////// Class methods /////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Returns an int value describing the Single Message LKH algorithm used.
     *
     * @return an int value describing the algorithm.
     */
    public int LKH_version(){
        return LKHSINGLEMESSAGE;
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
        Node performer=null;

        boolean borrar=false;

        if(!blackList.isEmpty()){
            Node removed=(Node)blackList.get(0);

            notification.triggerLKHChange(LKHEvent.DISCARD_NODE,removed);

            performer=tree.removeNode(removed);
            System.out.println(
                    "\n>>>>--->>>--->>--->---NEW REKEYING---<---<<---<<<---<<<<");

            calculateParameterP();

            if(((Node)blackList.get(0)).getParent()!=null){
                removedList.add(((Node)blackList.get(0)).getParent().getKEK());
            }

            borrar=true;
            blackList.clear();
        } else if(!welcomed.isEmpty()){
            performer=tree.insertNode((Node)welcomed.get(0));
            SecretKey oldkey=buildKey(performer);

            System.out.println(
                    "\n>>>>--->>>--->>--->---NEW REKEYING---<---<<---<<<---<<<<");

            calculateParameterP();
            UDPsender.UDPsend(UDPsender.buildWelcomePacketSingleMessage(LKH.
                    LKHSINGLEMESSAGE,MSDMconnection.KEK_LKH,performer,P,oldkey));

            if(performer.getSibling()!=null){
                UDPsender.UDPsend(UDPsender.buildAddNodePacketSingleMessage(LKH.
                        LKHSINGLEMESSAGE,MSDMconnection.KEK_SINGLEMESSAGE_ADD,
                        performer.getSibling(),P));
            }

            performer=performer.getParent();

            borrar=false;

            welcomed.clear();
            notification.triggerLKHChange(LKHEvent.TREE_UPDATED,performer);
        } else{
            return;
        }

        if(performer!=null&&(performer.getParent()!=null||borrar)){
            Vector v=new Vector();
            if(borrar){
                Vector tmp=performer.pathToRoot();
                tmp.remove(tree.getRoot());
                for(int i=0;i<tmp.size();i++){
                    v.add(((Node)tmp.get(i)).getSibling());
                }
                v.add(0,performer);
                if(performer.getSibling()!=null){
                    v.add(0,performer.getSibling());
                }
            } else{
                Vector tmp=performer.pathToRoot();
                tmp.add(0,performer);
                tmp.remove(tree.getRoot());
                for(int i=0;i<tmp.size();i++){
                    v.add(((Node)tmp.get(i)).getSibling());
                }
            }
            if(v.size()!=0){
                UDPsender.UDPsend(UDPsender.
                        buildCompleteKEKPacketSingleMessageAddingNodes(LKH.
                        LKHSINGLEMESSAGE,MSDMconnection.KEK_SINGLEMESSAGE,v,P));
            }
        }
        if(!tree.isEmpty()){
            updateKeys();
        }

        if(removedList.size()>=10){
            String s="Packet with the list of removed ID nodes: ";
            for(int i=0;i<removedList.size();i++){
                s=s+((KEK)removedList.get(i)).LKH_ID+" ";
            }
            System.out.println(s);
            UDPsender.UDPsend(UDPsender.buildBlackListPacket(LKH.
                    LKHSINGLEMESSAGE,MSDMconnection.SIMPLE_MESSAGE_REMOVE,
                    removedList));

            removedList.clear();
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
     * Updates the KEKs of the tree.
     */
    private void updateKeys(){
        for(Node node=tree.getRoot().firstPoOrder();node!=tree.getRoot();
                node=node.nextPoOrder()){
            node.setKEK(KeySingleMessage.XOR(node.getKEK().keyData,P));
        }
        tree.getRoot().setKEK(KeySingleMessage.XOR(tree.getRoot().getKEK().
                keyData,P));
    }

    /**
     * Updates the node's key and the parent's key.
     *
     * @param node node to uptade the key.
     * @return the old node's key.
     */
    private SecretKey buildKey(Node node){
        SecretKey key=node.getKEK().keyData;

        try{
            node.setKEK(new KEK(KeySingleMessage.XOR(KeySingleMessage.F(node),
                    tree.getNewR()),node.hashCode()));
        } catch(NoSuchAlgorithmException e){
            e.printStackTrace();
        }

        if(!node.isRoot()){
            Node aux=node.getParent();
            try{
                aux.setKEK(new KEK(KeySingleMessage.XOR(KeySingleMessage.F(aux),
                        tree.getNewR()),aux.hashCode()));
            } catch(NoSuchAlgorithmException e){
                e.printStackTrace();
            }
        }
        return key;
    }

    /**
     * Calculates the new parameter P which allows to update the client's keys.
     */
    private void calculateParameterP(){
        calculateParameterR();
        P=KeySingleMessage.XOR(tree.getNewR(),tree.getOldR());
        System.out.println("\nNew parameter P: "+Conversion.asHex(P));
    }

    /**
     * Calculates the new random number which allows calculates the new P.
     */
    private void calculateParameterR(){
        SecretKey r=KeySingleMessage.calculateParameterR(tree);
        tree.updateR(r);
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
