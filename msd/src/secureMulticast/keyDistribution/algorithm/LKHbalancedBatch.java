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
 * <p> This class is an implementation of the Balanced Batch Logical Key Hierarchy. It allows member joinings and leavings,
 * and does automatically the rekeying process after a time interval.
 *
 * @author  Oscar Burgos & Daniel Jarne
 * @version 1.1, 01/10/04
 */
public class LKHbalancedBatch extends LKH implements Runnable{
    ////////////////////////////////////////////////////////////////////////////
    //////// Class constructors ////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructs a new Balanced Batch LKH algorithm object. The constructor gives all the parameters needed to start
     * the algorithm execution.
     *
     * @param keyAlgorithm the algorithm used to generate the symmetric keys.
     * @param sleepTime the time interval between rekeying processes.
     * @param groupAddress the multicast group IP address.
     * @param port the UDP port used by the group.
     * @param notification the object used to trigger the LKH events.
     */
    public LKHbalancedBatch(int keyAlgorithm,int sleepTime,MSDManager msd,
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
        return LKHBALANCEDBATCH;
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
        notification.triggerLKHChange(LKHEvent.LEAVING,n);
    }

    /**
     * Makes all the rekeying process according to the elements stored in the vectors (the new members, the leaving
     * members, etc). It must be implemented as a synchronized method because in the redefinition it should modify
     * the welcomed, the blackList and the changesList vectors.
     */
    public synchronized void process(){
        boolean processRekeying=false;

        if(!blackList.isEmpty()||!welcomed.isEmpty()){
            processRekeying=true;
        }

        if(blackList.size()>0){
            if((Node)blackList.firstElement()==tree.getRoot()){
                tree.removeNode((Node)blackList.firstElement());
                return;
            }
        }

        if(processRekeying){
            prune_tree(mark_rekeying_nodes());

            for(int i=0;i<welcomed.size();i++){
                changesList.addElement(welcomed.get(i));
            }

            update_tree();
            notification.triggerLKHChange(LKHEvent.TREE_UPDATED,changesList);

            System.out.println(
                    "\n>>>>--->>>--->>--->---NEW REKEYING---<---<<---<<<---<<<<");

            if(!changesList.isEmpty()){
                sendRekeying();
                changesList.clear();
            }
            welcomed.clear();
            blackList.clear();

            SecretKey newSEK=genNewSEK();
            System.out.println("\nNew Session Key value - "+
                               Conversion.asHex(newSEK)+"\n");
            if(!tree.isEmpty()){
                UDPsender.UDPsend(UDPsender.buildSEKPacket(LKH.LKHBALANCEDBATCH,
                        tree.getRoot(),newSEK));
            }
            notification.triggerLKHChange(LKHEvent.NEW_SEK,newSEK);

            System.out.println(
                    ">>>>--->>>--->>--->---END REKEYING---<---<<---<<<---<<<<");
        }
    }

    /**
     * Gets all the nodes recently inserted and the removed nodes' sibling (changesList vector), and creates
     * a vector with the nodes that should change its KEK; altough in our case, it will be deleted (equivalent to non
     * linked).
     *
     * @return a vector containing the nodes to be removed from the Tree structure.
     */
    private Vector mark_rekeying_nodes(){
        Vector deletedNodes=new Vector(0);

        for(int i=0;i<blackList.size();i++){
            Node removed=(Node)blackList.get(i);
            notification.triggerLKHChange(LKHEvent.DISCARD_NODE,removed);
            Vector path=removed.pathToRoot();
            path.add(removed);
            deletedNodes.removeAll(path);
            deletedNodes.addAll(path);
        }

        return deletedNodes;
    }

    /**
     * Performs the deletion of the nodes marked to be removed, and stores all the subtrees' roots created in the
     * changesList vector.
     *
     * @param deletedNodes the nodes to be removed from the Tree structure.
     */
    private void prune_tree(Vector deletedNodes){
        for(int i=0;i<deletedNodes.size();i++){
            Node node=(Node)deletedNodes.get(i);
            Node performer=node.getLeftSon();
            if(performer!=null&&isNotInList(deletedNodes,performer)&&
               !changesList.contains(performer)){
                performer.setParent(null);
                changesList.addElement(performer);
            }
            performer=node.getRightSon();
            if(performer!=null&&isNotInList(deletedNodes,performer)&&
               !changesList.contains(performer)){
                performer.setParent(null);
                changesList.addElement(performer);
            }
        }
        if(changesList.isEmpty()&&!tree.isEmpty()&&blackList.isEmpty()){
            changesList.addElement(tree.getRoot());
        }
    }

    /**
     * Returns a boolean value specifying whether the node is in the deleted nodes list or not.
     *
     * @param deletedNodes the list with the nodes to be removed from the structure.
     * @param performer the node we want to know whether it is in list or not.
     * @return true if the node is in the deleted nodes list, false otherwise.
     */
    private static boolean isNotInList(Vector deletedNodes,Node performer){
        Vector intersection=new Vector(0);
        intersection.addElement(performer);
        return intersection.retainAll(deletedNodes);
    }

    /**
     * Builds the new tree structure linking all the resting subtrees and the new joining members. The process
     * is done according to the specified by the balanced batch algorithm.
     */
    private void update_tree(){
        Vector aux=new Vector(0);
        aux.addAll(changesList);

        while(aux.size()>1){
            Vector aux2=getMinDepthSubtrees(aux);
            aux.removeAll(aux2);

            for(int i=0;i<aux2.size()/2;i++){
                Node newnode=new Node();
                newnode.setKEK(genNewKEK(newnode.hashCode()));
                link(newnode,(Node)aux2.get(2*i),(Node)aux2.get(2*i+1));
                aux.addElement(newnode);
            }
            if((aux2.size()%2)!=0){
                Node newnode=new Node();
                newnode.setKEK(genNewKEK(newnode.hashCode()));
                Node node=(Node)(getMinDepthSubtrees(aux)).firstElement();
                aux.remove(node);
                link(newnode,node,(Node)aux2.lastElement());
                aux.addElement(newnode);
            }
        }
        if(!aux.isEmpty()){
            Node newRoot=(Node)aux.firstElement();
            tree.setRoot(newRoot);
            Tree.swapNodeNames(newRoot);
        } else{
            tree.setRoot(null);
        }
    }

    /**
     * Returns a vector containing the root nodes of the minimum depth subtrees of the given list.
     *
     * @param subtrees the subtrees list.
     * @return the vector containing the subtrees with minumim depth.
     */
    private static Vector getMinDepthSubtrees(Vector subtrees){
        int mindepth=0;
        Vector minsubtree=new Vector(0);

        for(int i=0;i<subtrees.size();i++){
            Node node=(Node)subtrees.get(i);
            int depth=node.getSuccessorsDepth();
            if(depth<mindepth||mindepth==0){
                mindepth=depth;
                minsubtree.clear();
                minsubtree.add(node);
            } else if(depth==mindepth){
                minsubtree.add(node);
            }
        }

        for(int j=0;j<minsubtree.size();j++){
            for(int i=0;i<minsubtree.size()-1-j;i++){
                if(((Node)minsubtree.get(i)).balanced()<
                   ((Node)minsubtree.get(i+1)).balanced()){
                    Node aux=(Node)minsubtree.get(i);
                    minsubtree.setElementAt(minsubtree.get(i+1),i);
                    minsubtree.setElementAt(aux,i+1);
                }
            }
        }

        return minsubtree;
    }

    /**
     * Links nodes to form a family (parent node - left son - right son).
     *
     * @param parent the node whose role will be the parent.
     * @param son1 the node whose role will be the left son.
     * @param son2 the node whose role will be the right son.
     */
    private static void link(Node parent,Node son1,Node son2){
        son1.setParent(parent);
        son2.setParent(parent);
        parent.setSon(son1);
        parent.setSon(son2);
    }

    /**
     * Buils and send all the rekeying messages to the subtrees that need to update they KEKs.
     */
    private void sendRekeying(){
        for(int i=0;i<changesList.size();i++){
            Node signer=(Node)changesList.get(i);
            UDPsender.UDPsend(UDPsender.buildCompleteKEKPacket(LKH.
                    LKHBALANCEDBATCH,MSDMconnection.KEK_LKH,signer));
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
