package secureMulticast.keyDistribution.algorithm;

import secureMulticast.binaryTree.*;
import secureMulticast.keyDistribution.net.*;
import secureMulticast.keyDistribution.symKeyGen.*;
import secureMulticast.event.LKHChangesSupport;

import java.util.*;
import javax.crypto.*;

/**
 * <p> This abstract class specifies the methods that must be redefined in all of the classes that implement any of
 * the Logical Key Algorithms that already exist. This class is an abstraction of the processes that take place in
 * the algorithm such as members joinings, leavings, generating new Key Encryption Keys and new Session Encryption Keys
 * and the rekeying processes.
 *
 * @author  Oscar Burgos & Daniel Jarne
 * @version 1.1, 01/10/04
 */
public abstract class LKH {
    ////////////////////////////////////////////////////////////////////////////
    //////// static and basic LKH fields ///////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Defines an int value for the LKH algorithm.
     */
    public static final int LKHSIMPLE = 0;

    /**
     * Defines an int value for Batch LKH algorithm.
     */
    public static final int LKHBATCH = 1;

    /**
     * Defines an int value for the Balanced Batch LKH algorithm.
     */
    public static final int LKHBALANCEDBATCH = 2;

    public static final int OFTSIMPLE = 3;

    public static final int LKHSINGLEMESSAGE = 4;

    public static final int LKHSINGLEMESSAGEBATCH = 5;


    /**
     * Specifies the LKH Tree managed by the algorithm.
     */
    protected Tree tree;

    /**
     * Specifies the vector where the newly created nodes (member joinings) are stored.
     */
    protected Vector welcomed;

    /**
     * Specifies the vector where the nodes to be removed (member leavings) are stored.
     */
    protected Vector blackList;

    /**
     * Specifies the object responsible for the UDP comunications (sending rekeying messages through the multicast
     * channel).
     */
    protected MSDMconnection UDPsender;

    /**
     * Specifies the object responsible for triggering events to the registered listeners.
     */
    protected LKHChangesSupport notification;

    /**
     * Specifies the thread that allows to sleep the algorithm thread (in case of the batch and balanced batch LKH).
     */
    protected Thread sleeper;

    /**
     * Specifies the time that the algorithm thread sleeps (in case of the batch and balanced batch LKH).
     */
    protected int sleepTime;

    /**
     * Specifies whether the algorithm is running or not.
     */
    protected boolean stopped;

    /**
     * Specifies the vector where the newly added or removed nodes are stored.
     */
    protected Vector changesList;
    protected Vector specialNode;

    ////////////////////////////////////////////////////////////////////////////
    //////// Class abstract methods ////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Returns an int value describing the LKH algorithm used.
     *
     * @return an int value describing the algorithm.
     */
    public abstract int LKH_version();

    /**
     * Adds a new node (new member) to the LKH Tree structure. It must be implemented as a synchronized method because
     * in the redefinition it should modify the welcome vector.
     *
     * @param newMember the new member's associated node (with its MemberID and KEK).
     */
    public abstract void memberJoining(Node newMember);

    /**
     * Removes the node with the specified ID (member leaving) from the LKH Tree structure. It must be implemented as
     * a synchronized method because in the redefinition it should modify the blackList vector.
     *
     * @param identifier the member's ID that is leaving the group.
     */
    public abstract void memberLeaving(int identifier);

    /**
     * Makes all the rekeying process according to the elements stored in the vectors (the new members, the leaving
     * members, etc). It must be implemented as a synchronized method because in the redefinition it should modify
     * the welcomed, the blackList and the changesList vectors.
     */
    public abstract void process();

    /**
     * Starts the algorithm execution (it only takes effect in case of the batch and balanced batch LKH because they
     * implement the Runnable class).
     */
    public abstract void start();

    /**
     * Stops the algorithm execution (it only takes effect in case of the batch and balanced batch LKH because they
     * implement the Runnable class).
     */
    public abstract void stop();

    ////////////////////////////////////////////////////////////////////////////
    //////// Class methods /////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Generates a new KEK object with the identifier specified as parameter.
     *
     * @param nodeHashValue the hash value of the node to which the KEK will be associated.
     * @return the newly created KEK.
     */
    public KEK genNewKEK(int nodeHashValue) {
        return tree.generateKEK(nodeHashValue);
    }

    /**
     * Generates a new SEK.
     * @return the new genereated symmetric key.
     */
    public SecretKey genNewSEK() {
        return tree.generateSEK();
    }

    ////////////////////////////////////////////////////////////////////////////
    //////// Class graphic methods /////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Returns the managed tree.
     *
     * @return the managed LKH Tree object.
     */
    public Tree managedTree() {
        return tree;
    }

    /**
     * Returns the nodes to be removed.
     *
     * @return a vector containing the nodes to be removed from the tree structure.
     */
    public Vector getBlackList() {
        return blackList;
    }
}
