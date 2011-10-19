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
 * <p> This class is an implementation of the Batch Logical Key Hierarchy. It allows member joinings and leavings,
 * and does automatically the rekeying process after a time interval.
 *
 * @author  Oscar Burgos & Daniel Jarne
 * @version 1.1, 01/10/04
 */
public class LKHbatch extends LKH implements Runnable{
    ////////////////////////////////////////////////////////////////////////////
    //////// Class constructors ////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructs a new Batch LKH algorithm object. The constructor gives all the parameters needed to start the
     * algorithm execution.
     *
     * @param keyAlgorithm the algorithm used to generate the symmetric keys.
     * @param sleepTime the time interval between rekeying processes.
     * @param groupAddress the multicast group IP address.
     * @param port the UDP port used by the group.
     * @param notification the object used to trigger the LKH events.
     */
    public LKHbatch(int keyAlgorithm,int sleepTime,MSDManager msd,
                    LKHChangesSupport notification){
        tree=new Tree(keyAlgorithm);
        this.sleepTime=sleepTime;
        this.notification=notification;
        welcomed=new Vector(0);
        blackList=new Vector(0);
        changesList=new Vector(0);
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
        return LKHBATCH;
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
        Node performer=new Node();
        boolean processRekeying=false;

        if(!blackList.isEmpty()||!welcomed.isEmpty()){
            processRekeying=true;
        }

        for(int i=0;i<blackList.size();i++){
            Node removed=(Node)blackList.get(i);
            notification.triggerLKHChange(LKHEvent.DISCARD_NODE,removed);
            performer=tree.removeNode(removed);
            if(welcomed.size()>0){
                changesList.addElement(tree.insertNode((Node)welcomed.get(0)));
                welcomed.removeElementAt(0);
            } else if(performer!=null&&!blackList.contains(performer)&&
                      !changesList.contains(performer)){
                changesList.addElement(performer);
            }
        }
        blackList.clear();

        if(welcomed.size()>0){
            Node[] identifiers=new Node[welcomed.size()];
            for(int i=0;i<welcomed.size();i++){
                identifiers[i]=(Node)welcomed.get(i);
                changesList.addElement(identifiers[i]);
            }
            tree.insertNode(new Tree(identifiers,tree.getKeyGenerator()).
                            getRoot());
        }
        welcomed.clear();

        if(processRekeying){
            notification.triggerLKHChange(LKHEvent.TREE_UPDATED,performer);

            System.out.println(
                    "\n>>>>--->>>--->>--->---NEW REKEYING---<---<<---<<<---<<<<");

            if(!changesList.isEmpty()){
                sendRekeying(updatePathKeys());
                changesList.clear();
            }

            SecretKey newSEK=genNewSEK();
            System.out.println("\nNew Session Key value - "+
                               Conversion.asHex(newSEK)+"\n");
            if(!tree.isEmpty()){
                UDPsender.UDPsend(UDPsender.buildSEKPacket(LKH.LKHBATCH,
                        tree.getRoot(),newSEK));
            }
            notification.triggerLKHChange(LKHEvent.NEW_SEK,newSEK);

            System.out.println(
                    ">>>>--->>>--->>--->---END REKEYING---<---<<---<<<---<<<<");
        }
    }

    /**
     * Builds a vector containing all the nodes that must update its KEK.
     *
     * @return the vector with all the nodes that need a KEK updating.
     */
    private Vector updatePathKeys(){
        Vector newkeyspath=new Vector(0);

        for(int i=0;i<changesList.size();i++){
            Vector path=(((Node)changesList.get(i)).pathToRoot());
            newkeyspath.removeAll(path);
            newkeyspath.addAll(path);
        }

        for(int i=0;i<newkeyspath.size();i++){
            Node partner=(Node)newkeyspath.get(i);
            partner.setKEK(genNewKEK(partner.hashCode()));
        }
        return newkeyspath;
    }

    /**
     * Calls the rekeying process for all the descendants of the elements contained in the vector.
     *
     * @param changedkeys the vector containing the nodes which have a new KEK.
     */
    private void sendRekeying(Vector changedkeys){
        for(int i=0;i<changedkeys.size();i++){
            Node node=(Node)changedkeys.get(i);
            analizeAndSend(Node.LEFT,node,changedkeys);
            analizeAndSend(Node.RIGHT,node,changedkeys);
        }
    }

    /**
     * Makes the rekeying process. For each one of the given nodes, it selects one of its sons, analyzes the KEKs that
     * have been updated, and finally sends the new KEKs to the subgroup signed with the subgroups' root KEK.
     *
     * @param side the node's son location.
     * @param node whose descendants must update its KEKs.
     * @param changedkeys all the nodes that have updated its KEKs.
     */
    private void analizeAndSend(int side,Node node,Vector changedkeys){
        Node signer=null;
        Vector intersection=new Vector(0);

        switch(side){
        case Node.LEFT:
            signer=node.getLeftSon();
            break;
        case Node.RIGHT:
            signer=node.getRightSon();
            break;
        }
        intersection.addElement(signer);
        if(intersection.retainAll(changedkeys)){
            if(signer.isLeaf()&&!signer.isRoot()){
                UDPsender.UDPsend(UDPsender.buildCompleteKEKPacket(LKH.LKHBATCH,
                        MSDMconnection.KEK_LKH,signer));
            } else{
                intersection.addElement(signer);
                if(intersection.retainAll(changesList)){
                    UDPsender.UDPsend(UDPsender.buildCompleteKEKPacket(LKH.
                            LKHBATCH,MSDMconnection.KEK_LKH,signer));
                } else{
                    Vector nodesInNewTree=tree.treeLeaves(signer);
                    for(int i=0;i<nodesInNewTree.size();i++){
                        if(!((Node)nodesInNewTree.get(i)).isRoot()){
                            UDPsender.UDPsend(UDPsender.buildCompleteKEKPacket(
                                    LKH.LKHBATCH,MSDMconnection.KEK_LKH,
                                    (Node)nodesInNewTree.get(i)));
                        }
                    }
                }
            }
        }
    }

    /**
     * Starts the algorithm execution.
     */
    public void start(){
        if(sleeper==null){
            sleeper=new Thread(this);
            sleeper.start();
        }
    }

    /**
     * Stops the algorithm execution.
     */
    public void stop(){
        stopped=true;
        sleeper=null;
    }

    /**
     * Performs the batch execution of the algorithm. It sleeps for "sleepTime" seconds and when it gets up
     * processes all the joinings, leavings and does the rekeying by calling the process method.
     */
    public void run(){
        Thread thisThread=Thread.currentThread();
        while(sleeper==thisThread){
            try{
                Thread.sleep(sleepTime*1000);
            } catch(InterruptedException e){
            }
            if(!stopped){
                process();
            }
        }
    }
}
