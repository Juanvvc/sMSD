package secureMulticast.binaryTree;

/**
 * This class is an abstraction of the data needed from a member that has joined a secure multicast group.
 *
 * <p> Any member that joins a secure group must have a unique identifier to make it different from any other
 * member. It is necessary to allow the remote members to ask for any kind of processing (rekeying, group leaving,... )
 * because this identifier is unique and sent through a ciphered channel (only known by the owner member) in the
 * joining process. This unique identifier is built with the hash value of the node associated with the remote
 * member.
 * On the other hand, we need the IP address of the remote member in order to make possible the
 * leaving protocol besides allowing sending unicast messages to the remote member in rekeying processes.
 *
 * @author  Oscar Burgos & Daniel Jarne
 * @version 1.1, 01/10/04
 */
public class MemberID {
    ////////////////////////////////////////////////////////////////////////////
    //////// MemberID fields ///////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Specifies the unique identifier associated to the remote member.
     */
    public int identifier;

    ////////////////////////////////////////////////////////////////////////////
    //////// Class constructors ////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructs a MemberID object (the identifier value is assigned when the MemberID is associated
     * to a leaf node that belongs to a LKH Tree.
     *
     * @param address a string representing the IP address of the remote member.
     */
    public MemberID(int id) {
        identifier=id;
    }
}
