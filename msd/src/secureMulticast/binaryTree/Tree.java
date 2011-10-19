package secureMulticast.binaryTree;

import secureMulticast.keyDistribution.symKeyGen.KEK;
import secureMulticast.keyDistribution.symKeyGen.KeyOFT;
import secureMulticast.keyDistribution.symKeyGen.KeySingleMessage;
import secureMulticast.keyDistribution.symKeyGen.SymKeyGenerator;

import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.util.Vector;

/**
 * This class represents a LKH binary tree.
 *
 * <p> A LKH binary tree is a binary hierarchy set up by LKH nodes. In this hierarchy
 * we store all the needed symmetric keys and also the multicast member identities
 * in the deepest nodes, known as leaf nodes. So, it is possible to manage big multicast groups
 * and to rekey algorithms in an easy way.
 *
 * @author  Oscar Burgos & Daniel Jarne
 * @version 1.1, 01/10/04
 */
public class Tree
{
	////////////////////////////////////////////////////////////////////////////
	//////// Static and basic Tree fields //////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Defines the DES algorithm.
	 */
	public static final int DES = 0;

	/**
	 * Defines the Blowfish algorithm.
	 */
	public static final int Blowfish = 1;

	/**
	 * Defines the Triple DES algorithm.
	 */
	public static final int DESede = 2;

    /**
     * Defines the AES algorithm.
     */
    public static final int AES = 3;

	/**
	 * Specifies the root of the tree. It is the reference to get any other node in the structure.
	 */
	private Node root;

	/**
	 * Specifies the next node to get descendants.
	 */
	private Node nextParent;

	/**
	 * Specifies the object which will generate all the symmetric keys needed to update or generate the keys
	 * stored in the nodes.
	 */
	private SymKeyGenerator keyGenerator;

    /**
     * Specifies the random number needed for updating the keys for the Single
     * Message LKH and Batch Single Message LKH algorithms.
     */
    private SecretKey newR;

    /**
     * Specifies the previous random number needed for updating the keys for the
     * Single Message LKH algorithm and Batch Single Message LKH algorithm.
     */
    private SecretKey oldR;

	////////////////////////////////////////////////////////////////////////////
	//////// Class constructors ////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructs a LKH Tree with the given initial nodes and key generator.
	 *
	 * @param initialMembers the initial nodes that set up the tree structure.
	 * @param keyGenerator the generator of symmetric keys.
	 */
	public Tree(Node[] initialMembers, SymKeyGenerator keyGenerator)
	{
		root = null;
		nextParent = null;
		this.keyGenerator = keyGenerator;

		createTreeOfNmembers(initialMembers);
	}

	/**
	 * Constructs an empty LKH Tree.
	 *
	 * @param keyAlgorithm the algorithm used to generate the symmetric keys.
	 */
	public Tree(int keyAlgorithm)
	{
		root = null;
		nextParent = null;
		keyGenerator = new SymKeyGenerator(keyAlgorithm);
	}

	/**
	 * Constructs a LKH Tree from a node, which is taken as root.
	 *
	 * @param root the node which will be the tree root.
	 */
	public Tree(Node root)
	{
		this.root = root;
		nextParent = null;
		keyGenerator = null;
	}

	////////////////////////////////////////////////////////////////////////////
	//////// Class methods /////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Returns a boolean value specifying whether the tree contains nodes or not.
	 *
	 * @return true if the tree contains no nodes, otherwise returns false.
	 */
	public boolean isEmpty()
	{
		return root == null;
	}

	/**
	 * Inserts the nodes in the array passed as parameter to the tree structure.
	 *
	 * @param initialMembers the array of nodes to be inserted in the tree.
	 */
	private void createTreeOfNmembers(Node[] initialMembers)
	{
		if (initialMembers.length == 0)
			return;
		for (int i = 0; i < initialMembers.length; i++)
			insertNode(initialMembers[i]);
	}

	/**
	 * Returns the Node object which acts as the root of the tree.
	 *
	 * @return the root of the tree.
	 */
	public Node getRoot()
	{
		return root;
	}

	/**
	 * Sets the parameter as the root of the tree.
	 *
	 * @param root the node to be placed as the root of the tree.
	 */
	public void setRoot(Node root)
	{
		this.root = root;
	}

	/**
	 * Returns the symmetric key generator used in the tree.
	 *
	 * @return the key generator object.
	 */
	public SymKeyGenerator getKeyGenerator()
	{
		return keyGenerator;
	}

	/**
	 * Returns a KEK object generated from the node hash value and to be used as a Key Encryption Key.
	 *
	 * @param nodeHashCode the hash value of the Node object which is going to store the returned KEK.
	 * @return a KEK object to be stored as a KEK in a node.
	 */
	public KEK generateKEK(int nodeHashCode)
	{
		return keyGenerator.generateKEK(nodeHashCode);
	}

	/**
	 * Returns a symmetric key to be used as a Session Encryption Key.
	 *
	 * @return a symmetric key to cipher the session data.
	 */
	public SecretKey generateSEK()
	{
		return keyGenerator.generateSEK();
	}

	/**
	 * Inserts a node in the tree structure (note that it can also be a node acting as the root of another tree).
	 * Renames the newly node's sibling (the one that already existed in the tree) and updates the value of the next
	 * node to have descendants.
	 *
	 * @param node the node to be inserted.
	 * @return the newly inserted node.
	 */
	public Node insertNode(Node node)
	{
		Node newnode;
		if (root == null)
		{
			newnode = add(null, node);
			root = newnode;
		}
		else
		{
			Node member = nextParent;
			if (member != root)
			{
				if (member.getSide() == Node.LEFT)
					member.getParent().setLeftSon(null);
				else
					member.getParent().setRightSon(null);
			}
			Node internode = add(member.getParent(), null);
			link(internode, member);
			swapNodeNames(member);
			newnode = add(internode, node);
		}

		findNextParent();

		return newnode;
	}

	/**
	 * Inserts the node into the tree and gives it the KEK to store.
	 * It is also done the renaming process of the newly inserted node (and descendants, if it has some).
	 *
	 * @param parent the parent node.
	 * @param nodeToInsert the son node.
	 * @return the newly inserted node (the son).
	 */
	private Node add(Node parent, Node nodeToInsert)
	{
		if (nodeToInsert == null)
			nodeToInsert = new Node();

		if (nodeToInsert.getKEK() == null)
			nodeToInsert.setKEK(generateKEK(nodeToInsert.hashCode()));
		link(parent, nodeToInsert);
		if (parent != null)
			nodeToInsert.setLocation(parent.x(), parent.y());

		swapNodeNames(nodeToInsert);

		return nodeToInsert;
	}

	/**
	 * Makes the linking process between two nodes (parent and son relationship). If the parent is null,
	 * the son is placed as the root of the tree.
	 *
	 * @param parent the parent node.
	 * @param son the son node.
	 * @return the newly inserted node (the son).
	 */
	private Node link(Node parent, Node son)
	{
		if (son != null)
			son.setParent(parent);
		if (parent != null)
			parent.setSon(son);
		else
			root = son;

		return son;
	}

	/**
	 * Removes a node (and its parent if it is needed to prevent from having nodes with only one son) from the
	 * tree structure. It also renames the nodes affected by the process.
	 *
	 * @param node the node to be removed.
	 * @return the sibling of the node to remove.
	 */
	public Node removeNode(Node node)
	{
		// Level 1.
		if (node == root)
			return root = null;

		// From level 2 and beyond.
		Node parent = node.getParent();
		Node sibling = node.getSibling();
		Node grandPa = null;
		if (parent != null)
			grandPa = parent.getParent();

		if (grandPa != null)
		{
			if (parent.getSide() == Node.LEFT)
				grandPa.setLeftSon(null);
			else
				grandPa.setRightSon(null);
		}

		link(grandPa, sibling);
		swapNodeNames(sibling);

		findNextParent();

		return sibling;
	}

	/**
	 * Renames the node passed as a parameter and all of its descendants.
	 *
	 * @param root the node to be renamed.
	 */
	public static void swapNodeNames(Node root)
	{
		Node parent;

		for (Node node = root; node != null; node = node.nextPrOrder())
		{
			parent = node.getParent();

			if (parent != null)
			{
				if (node.getSide() == Node.LEFT)
					node.setName(new int[]{parent.getName()[0] + 1, 2 * parent.getName()[1] - 1});
				else
					node.setName(new int[]{parent.getName()[0] + 1, 2 * parent.getName()[1]});
			}
			else
				node.setName(new int[]{1, 1});
		}
	}

	/**
	 * Updates the value of the field nextParent to the leaf node with lowest level.
	 */
	private void findNextParent()
	{
		nextParent = null;

		for (Node node = root; node != null; node = node.nextPrOrder())
		{
			if (node.isLeaf())
			{
				if (nextParent == null)
					nextParent = node;
				else if (node.getDepth(root) < nextParent.getDepth(root))
					nextParent = node;
			}
		}
	}

	/**
	 * Returns the value of the field nextParent.
	 *
	 * @return the value of nextParent.
	 */
	public Node getNextParent()
	{
		findNextParent();
		return nextParent;
	}

	/**
	 * Returns the Node object whose name is the one required.
	 *
	 * @param name an int array containing the name of the node [row, column].
	 * @return the Node corresponding to the specified name.
	 */
	public Node getNodeByName(int[] name)
	{
		Node node;
		for (node = root; node != null && !(node.getName()[0] == name[0] && node.getName()[1] == name[1]); node = node.nextPrOrder())
			;
		return node;
	}

	/**
	 * Returns the Node object (it must be a leaf node) associated to the specified member identifier.
	 *
	 * @param identifier the member ID.
	 * @return the Node corresponding to the specified member ID.
	 */
	public Node getNodeByMemberIdentifier(int identifier)
	{
		Vector leavesInTree = treeLeaves(null);
		for (int i = 0; i < leavesInTree.size(); i++)
		{
			Node node = (Node) leavesInTree.get(i);
			if (node.getMemberID().identifier == identifier)
				return node;
		}
		return null;
	}

	/**
	 * Returns the number of leaf nodes in the tree (number of members in the multicast group).
	 *
	 * @return the number of leaf nodes (members) in the multicast group.
	 */
	public int treeNumLeaves()
	{
		return treeLeaves(null).size();
	}

	/**
	 * Returns a vector containing the Node objects associated to a member of the multicast group.
	 *
	 * @param root the subtree root from which the leaves are searched (a null value means searching
	 *			   from the root of the tree).
	 * @return a vector containing the leaf nodes (nodes associated to members).
	 */
	public Vector treeLeaves(Node root)
	{
		if (root == null)
			root = this.root;

		Vector leaves = new Vector(0);

		if (!isEmpty())
			for (Node node = root; node != null; node = node.nextPrOrder())
				if (node.isLeaf())
					leaves.addElement(node);

		return leaves;
	}

	/**
	 * Returns the number of nodes that set up the tree hierarchy.
	 *
	 * @return the total number of nodes in the tree.
	 */
	public int numNodes()
	{
		Node node;
		int i;

		for (node = root, i = 0; node != null; node = node.nextPrOrder(), i++)
			;

		return i;
	}

	/**
	 * Returns an array of nodes containing the nodes in the path from root to the specified node, both included.
	 *
	 * @param name an int array containing destination node's row and column.
	 * @return an array containing the nodes in path.
	 */
	public Node[] pathToNode(int[] name)
	{
		Node[] path = new Node[name[0]];
		Node destination = getNodeByName(name);
		int i = 0;

		for (Node node = root; node != destination; node = node.getSon(node.compareSide(destination)))
			path[i++] = node;
		path[i] = destination;

		return path;
	}

	/**
	 * Returns the depth of the tree.
	 *
	 * @return the depth of the tree.
	 */
	public int getDepth()
	{
		if (isEmpty())
			return 0;

		int maxdepth = 1;

		for (Node node = root.firstPoOrder(); node != root; node = node.nextPoOrder())
		{
			int depth = node.getDepth(root);
			if (depth > maxdepth)
				maxdepth = depth;
		}
		return maxdepth;
	}

    /**
     * Returns the difference between the leaf node of the deepest level and the leaf node of the less deep level of the tree.
     *
     * @return the difference between the leaf node of the deepest level and the leaf node of the less deep level of the tree.
     */
    public int balanced()
	{
		int maxdepth = 0;
        int mindepth = 0;

        for (Node node = root.firstPoOrder(); node != root; node = node.nextPoOrder())
		{
			int depth = node.getDepth(root);
            if(node.isLeaf())
                if (maxdepth == 0 && mindepth == 0)
                {
			    	maxdepth = depth;
                    mindepth = depth;
                }
                else if(maxdepth < depth)
                    maxdepth = depth;
                else if(mindepth > depth)
                    mindepth = depth;
		}
		return maxdepth - mindepth;
	}

    /**
     * Recalculates all the keys for the OFT algorithm.
     */
    public void recalculateKeyOFT()
    {
        calculateBlind(root);
    }

    /**
     * Returns the blind key of the specific node for the OFT algorithm.
     *
     * @param node the node whose blind key is calculated.
     * @return the blind key of the specific node fot the OFT algorithm.
     */
    public SecretKey calculateBlind(Node node)
    {
        SecretKey key1;
        SecretKey key2;

        if(!node.getLeftSon().isLeaf())
        {
            key1 = KeyOFT.blind(calculateBlind(node.getLeftSon()));
        }
        else key1 = node.getLeftSon().getBlindKEK().keyData;

        if(!node.getRightSon().isLeaf())
        {
            key2 = KeyOFT.blind(calculateBlind(node.getRightSon()));
        }
        else key2 = node.getRightSon().getBlindKEK().keyData;

        node.setKEK(new KEK(KeyOFT.XOR(key1, key2), node.hashCode()));

        return KeyOFT.XOR(key1, key2);
    }

    /**
     * Sets up the random number needed for updating the keys in the Single Message LKH algorithm and Batch Single
     * Message LKH algorithm with the specific value.
     *
     * @param R the new random number.
     */
    public void setNewR(SecretKey R)
    {
        newR = R;
    }

    /**
     * Returns the random number needed for updating the keys in the Single Message LKH algorithm and Batch Single
     * Message LKH algorithm.
     *
     * @return the random number.
     */
    public SecretKey getNewR()
    {
        return newR;
    }

    /**
     * Sets up the previous random number needed for updating the keys in the Single Message LKH algorithm and Batch
     * Single Message LKH algorithm with the specific value.
     *
     * @param R the new random number.
     */
    public void setOldR(SecretKey R)
    {
        oldR = R;
    }

    /**
     * Returns the previous random number needed for updating the keys in the Single Message LKH algorithm and Batch
     * Single Message LKH algorithm.
     *
     * @return the previous random number.
     */
    public SecretKey getOldR()
    {
        return oldR;
    }

    /**
     * Updates the random number needed for updating the keys in the Single Message LKH algorithm and Batch Single
     * Message LKH algorithm. Sets up as previous random number the current value and sets up as the current random number the
     * specific value as parameter.
     *
     * @param R the new random number.
     */
    public void updateR(SecretKey R)
    {
        oldR = newR;
        newR = R;
    }

    /**
	 * Inserts a node in the tree structure for the Single Message LKH and Batch Single Message LKH algorithms (note
     * that it can also be a node acting as the root of another tree).
	 * Renames the newly node's sibling (the one that already existed in the tree) and updates the value of the next
	 * node to have descendants.
	 *
	 * @param node the node to be inserted.
	 * @return the newly inserted node.
	 */
    public Node insertNodeSingleMessage(Node node) {
        node = insertNode(node);
        try {
            node.setKEK(new KEK(KeySingleMessage.XOR(KeySingleMessage.F(node), newR), node.hashCode()));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        if(node!=root)
        {
            Node aux = node.getParent();
            try {
                    aux.setKEK(new KEK(KeySingleMessage.XOR(KeySingleMessage.F(aux), newR), aux.hashCode()));
            } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
            }
        }
        return node;
    }
}
