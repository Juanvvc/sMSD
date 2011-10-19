package secureMulticast.keyDistribution.algorithm;

import secureMulticast.event.LKHChangesSupport;
import secureMulticast.event.LKHEvent;
import secureMulticast.util.Conversion;
import secureMulticast.binaryTree.Tree;
import secureMulticast.binaryTree.Node;
import secureMulticast.keyDistribution.net.MSDMconnection;
import secureMulticast.keyDistribution.symKeyGen.KeySingleMessage;
import secureMulticast.keyDistribution.symKeyGen.KEK;

import javax.crypto.SecretKey;
import java.util.Vector;
import java.util.Random;
import java.security.NoSuchAlgorithmException;
import java.math.BigInteger;

import org.msd.proxy.MSDManager;

/**
 * <p> This class is an implementation of the Batch Single Message Logical Key Hierarchy. It allows member joinings and
 * leavings, and does automatically the rekeying process after a time interval.
 *
 * @author  Oscar Burgos & Daniel Jarne
 * @version 1.1, 01/10/04
 */
public class BatchSingleMessageLKH extends LKH implements Runnable{
    ////////////////////////////////////////////////////////////////////////////
    //////// LKHBatchSingleMessage fields //////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Defines the parameter which will allow to update the client's keys.
     */
    SecretKey P;

    /**
     * Defines a list that contains the removed nodes.
     */
    Vector removedList=new Vector();

    /**
     * Defines a list that contains the joinings nodes.
     */
    Vector welcomedList;


    ////////////////////////////////////////////////////////////////////////////
    //////// Class constructors ////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructs a new Batch Single Message LKH algorithm object. The constructor gives all the parameters needed to
     * start the algorithm execution.
     *
     * @param keyAlgorithm the algorithm used to generate the symmetric keys.
     * @param groupAddress the multicast group IP address.
     * @param port the UDP port used by the group.
     * @param notification the object used to trigger the LKH events.
     */
    public BatchSingleMessageLKH(int keyAlgorithm,int sleepTime,
                                 MSDManager msd,LKHChangesSupport notification){
        tree=new Tree(keyAlgorithm);
        this.sleepTime=sleepTime;
        this.notification=notification;
        welcomed=new Vector(0);
        welcomedList=new Vector(0);
        blackList=new Vector(0);
        changesList=new Vector(0);
        specialNode=new Vector(0);
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
     * Returns an int value describing the LKH algorithm used.
     *
     * @return an int value describing the algorithm.
     */
    public int LKH_version(){
        return LKHSINGLEMESSAGEBATCH;
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
            if(removed.getParent()!=null){
                removedList.add(removed.getParent().getKEK());
            }
            if(welcomed.size()>0){
                Node node=tree.insertNode((Node)welcomed.get(0));
                changesList.addElement(node);
                welcomedList.addElement(node);
                if(!blackList.contains(node.getSibling())&&node.getSibling()!=null){
                    specialNode.addElement(node.getSibling());
                }
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
                welcomedList.addElement(identifiers[i]);
            }
            Tree tmp=new Tree(identifiers,tree.getKeyGenerator());
            updateKeys(tmp);
            Node a=tree.insertNode(tmp.getRoot());
            if(a.getSibling()!=null){
                specialNode.addElement(a.getSibling());
            }
        }
        welcomed.clear();

        if(processRekeying){
            notification.triggerLKHChange(LKHEvent.TREE_UPDATED,performer);

            System.out.println(
                    "\n>>>>--->>>--->>--->---NEW REKEYING---<---<<---<<<---<<<<");

            rekeying();
            changesList.clear();
            specialNode.clear();
            welcomedList.clear();

            SecretKey newSEK=genNewSEK();
            System.out.println("\nNew Session Key value - "+
                               Conversion.asHex(newSEK)+"\n");
            if(!tree.isEmpty()){
                UDPsender.UDPsend(UDPsender.buildSEKPacket(LKH.
                        LKHSINGLEMESSAGEBATCH,tree.getRoot(),newSEK));
            }
            notification.triggerLKHChange(LKHEvent.NEW_SEK,newSEK);

            System.out.println(
                    ">>>>--->>>--->>--->---END REKEYING---<---<<---<<<---<<<<");

            if(removedList.size()>=10){
                UDPsender.UDPsend(UDPsender.buildBlackListPacket(LKH.
                        LKHSINGLEMESSAGEBATCH,
                        MSDMconnection.SIMPLE_MESSAGE_REMOVE,
                        removedList));
                String s="Packet with the list of removed ID nodes: ";
                for(int i=0;i<removedList.size();i++){
                    s=s+((KEK)removedList.get(i)).LKH_ID+" ";
                }
                System.out.println(s);

                removedList.clear();
            }
        }
    }

    /**
     * Realizes the rekeying for Batch Single Message LKH algorithm.
     */
    private void rekeying(){
        Vector newkeyspath=new Vector(0);
        Vector nodesP=new Vector(0);

        for(int i=0;i<changesList.size();i++){
            Vector path=(((Node)changesList.get(i)).pathToRoot());
            newkeyspath.removeAll(path);
            newkeyspath.addAll(path);
        }

        for(int i=0;i<specialNode.size();i++){
            Vector path=(((Node)specialNode.get(i)).pathToRoot());
            newkeyspath.removeAll(path);
            newkeyspath.addAll(path);
        }

        if(tree.getRoot()!=null){
            for(Node node=tree.getRoot().firstPoOrder();
                          node!=tree.getRoot();node=node.nextPoOrder()){
                if(node.isLeaf()){
                    Node tmp=node;
                    Node old=node;
                    while(!newkeyspath.contains(tmp)&&tmp.getParent()!=null){
                        old=tmp;
                        tmp=tmp.getParent();
                    }
                    if(!newkeyspath.contains(old)&&!specialNode.contains(old)&&
                       !welcomedList.contains(old)&&!nodesP.contains(old)&&
                       old.getParent()!=null){
                        nodesP.add(old);
                    }
                }
            }
        }

        if(tree.getRoot()!=null){
            if(tree.getRoot().isLeaf()&&!welcomedList.contains(tree.getRoot())){
                nodesP.add(tree.getRoot());
            }
        }

        SecretKey oldkey[]=new SecretKey[welcomedList.size()];

        for(int i=0;i<welcomedList.size();i++){
            oldkey[i]=buildKey((Node)welcomedList.get(i));
        }

        calculateParameterP();

        for(int i=0;i<welcomedList.size();i++){
            UDPsender.UDPsend(UDPsender.buildWelcomePacketSingleMessage(LKH.
                    LKHSINGLEMESSAGEBATCH,MSDMconnection.KEK_LKH,
                    (Node)welcomedList.get(i),P,oldkey[i]));
        }

        for(int i=0;i<welcomedList.size();i++){
            specialNode.remove(welcomedList.get(i));
        }

        for(int i=0;i<specialNode.size();i++){
            UDPsender.UDPsend(UDPsender.buildAddNodePacketSingleMessage(LKH.
                    LKHSINGLEMESSAGEBATCH,MSDMconnection.KEK_SINGLEMESSAGE_ADD,
                    (Node)specialNode.get(i),P));
        }

        if(!nodesP.isEmpty()){
            UDPsender.UDPsend(UDPsender.
                              buildCompleteKEKPacketSingleMessageAddingNodes(
                                      LKH.LKHSINGLEMESSAGEBATCH,
                                      MSDMconnection.KEK_SINGLEMESSAGE,
                                      nodesP,P));
        }

        if(tree.getRoot()!=null){
            updateKeys();
        }
    }

    /**
     * Updates the KEKs of the tree.
     *
     * @param tree tree which contains the KEKs for update.
     */
    private void updateKeys(Tree tree){
        for(Node node=tree.getRoot().firstPoOrder();node!=tree.getRoot();
                      node=node.nextPoOrder()){
            if(!node.isLeaf()){
                try{
                    node.setKEK(new KEK(KeySingleMessage.XOR(KeySingleMessage.F(
                            node),this.tree.getNewR()),node.hashCode()));
                } catch(NoSuchAlgorithmException e){}
            }
        }
        if(!tree.getRoot().isLeaf()){
            try{
                tree.getRoot().setKEK(new KEK(KeySingleMessage.XOR(
                        KeySingleMessage.F(tree.getRoot()),this.tree.getNewR()),
                                              tree.getRoot().hashCode()));
            } catch(NoSuchAlgorithmException e){}
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
     * Performs the batch execution of the algorithm. It sleeps for "sleepTime" seconds and when it wakes up
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
     * Updates the keys.
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
        } catch(NoSuchAlgorithmException e){}

        if(!node.isRoot()){
            Node aux=node.getParent();
            try{
                aux.setKEK(new KEK(KeySingleMessage.XOR(KeySingleMessage.F(aux),
                        tree.getNewR()),aux.hashCode()));
            } catch(NoSuchAlgorithmException e){}
        }
        return key;
    }
}
