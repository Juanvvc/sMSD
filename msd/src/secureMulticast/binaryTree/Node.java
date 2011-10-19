package secureMulticast.binaryTree;

import secureMulticast.keyDistribution.symKeyGen.KEK;
import secureMulticast.keyDistribution.symKeyGen.KeyOFT;

import javax.crypto.SecretKey;
import java.util.Vector;

/**
 * This class represents a node of a LKH binary tree.
 *
 * <p> Any node in a LKH tree is linked to its parent and sons (2 if the tree is a binary tree like in our case).
 * Besides, the node is responsible of storing a symmetric key and the identifier of a member if the node has no
 * sons (what we call a leaf node) which will identify the multicast member associated to the node.
 *
 * @author  Oscar Burgos & Daniel Jarne
 * @version 1.1, 01/10/04
 */
public class Node
{
	////////////////////////////////////////////////////////////////////////////
	//////// Static and basic Node fields //////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Defines node's location according to its parent's link.
	 * Left location.
	 */
	public static final int LEFT = -1;

	/**
	 * Defines node's location according to its parent's link.
	 * Right location.
	 */
	public static final int RIGHT = 1;

	/**
	 * Defines node's location according to its parent's link.
	 * Node is the root of the tree (no parent node) or the node is not linked.
	 */
	private static final int NONE = 0;

	/**
	 * Specifies the parent node to which this node is linked to.
	 */
	private Node parent;

	/**
	 * Specifies the left son node to which this node is linked to.
	 */
	private Node leftSon;

	/**
	 * Specifies the right son node to which this node is linked to.
	 */
	private Node rightSon;

	/**
	 * Specifies the name of the node [row, column].
	 * The root's name is [1, 1] and the count go on for further nodes.
	 */
	private int[] name;

	/**
	 * Specifies the symmetric key associated to the node.
	 */
	private KEK key;

    /**
      * Specifies the previous symmetric key associated to the node.
      */
    private KEK oldkey;


	/**
	 * Specifies the identifier of the member associated to the node.
	 * It includes the unique identifier and the IP address of the member's machine.
	 */
	private MemberID member;

	////////////////////////////////////////////////////////////////////////////
	//////// Fields for the graphic classes ////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Specifies the current horizontal coordinate of the node.
	 */
	private int x;

	/**
	 * Specifies the current vertical coordinate of the node.
	 */
	private int y;

	/**
	 * Specifies the horizontal destination coordinate to which the node is moving.
	 */
	private int X;

	/**
	 * Specifies the vertical destination coordinate to which the node is moving.
	 */
	private int Y;

	/**
	 * Specifies the horizontal differential for the node movement.
	 */
	private int vx;

	/**
	 * Specifies the vertical differential for the node movement.
	 */
	private int vy;

	/**
	 * Specifies the node's visualization according to its state.
	 */
	private int mode;

	////////////////////////////////////////////////////////////////////////////
	//////// Class constructors ////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructs a LKH node with null fields.
	 */
	public Node()
	{
		name = null;
		parent = null;
		leftSon = null;
		rightSon = null;
		key = null;
		member = null;
	}

	/**
	 * Constructs a LKH node associated to a multicast member. The field member equals
	 * to the specified parameter id.
	 *
	 * @param id specifies the object representing the identifier of the multicast member.
	 * 			 The int value of the identifier contained in the object id is given by
	 *			 the hash value of the newly created node. Doing this we achieve the target
	 *			 of making the value really unique.
	 */
	public Node(MemberID id)
	{
		name = null;
		parent = null;
		leftSon = null;
		rightSon = null;
		key = null;
		id.identifier = hashCode();
		member = id;
	}

	////////////////////////////////////////////////////////////////////////////
	//////// Class methods /////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Returns true if the node is the root of the LKH tree (has no parent).
	 *
	 * @return a boolean value specifying whether it is the root of the tree or not.
	 */
	public boolean isRoot()
	{
		if (parent != null)
			return false;
		else
			return true;
	}

	/**
	 * Returns true if the node is a leaf of the LKH tree (has no sons).
	 *
	 * @return a boolean value specifying whether it is a leaf node or not.
	 */
	public boolean isLeaf()
	{
		return leftSon == null && rightSon == null;
	}

	/**
	 * Returns the nodes in the path towards the root of the LKH tree.
	 *
	 * @return a vector containing the nodes in the path from the node's parent to the tree root both contained.
	 */
	public Vector pathToRoot()
	{
		Vector path = new Vector(0);

		for (Node node = getParent(); node != null; node = node.getParent())
			path.addElement(node);

		return path;
	}

	/**
	 * Returns the maximum depth of the subtree defined by taking the node as a tree root.
	 *
	 * @return an int value specifying the maximum depth of the subtree defined by the node.
	 */
	public int getSuccessorsDepth()
	{
		return new Tree(Node.this).getDepth();
	}

	/**
	 * Returns the depth gap from the node given as parameter to the node.
	 *
	 * @param root the node from which is taken the relative depth.
	 * @return the relative depth between the parameter and the node.
	 */
	public int getDepth(Node root)
	{
		Node node = this;
		int depth;

		for (depth = 1; node != root; depth++)
			node = node.getParent();

		return depth;
	}

	/**
	 * Returns the relative position of the node in relation to the parameter node.
	 *
	 * @param comp the node to compare with.
	 * @return an int value specifying the relative position ({@link #LEFT LEFT}, {@link #RIGHT RIGHT},
	 * 			{@link #NONE NONE} constants).
	 */
	public int compareSide(Node comp)
	{
		int numNodesInLevel = (int) Math.pow(2, comp.getName()[0] - getName()[0]);
		if (numNodesInLevel <= 1)
			return NONE;

		int posInLevel = comp.getName()[1];
		if (posInLevel > numNodesInLevel)
			posInLevel = comp.getName()[1] % numNodesInLevel;
		if (posInLevel == 0)
			posInLevel = numNodesInLevel;

		if (posInLevel <= (numNodesInLevel / 2))
			return LEFT;
		else
			return RIGHT;
	}

	/**
	 * Returns an int specifying whether it is the left son of its parent, the right one or has no parent.
	 *
	 * @return an int constant value of the side ({@link #LEFT LEFT}, {@link #RIGHT RIGHT},
	 * 			{@link #NONE NONE} constants).
	 */
	public int getSide()
	{
		if (!isRoot())
		{
			if (this == parent.getLeftSon())
				return LEFT;
			else
				return RIGHT;
		}
		else
			return NONE;
	}

	/**
	 * Returns the parent node to which the node is linked.
	 *
	 * @return the parent node.
	 */
	public Node getParent()
	{
		return parent;
	}

	/**
	 * Returns the left son node to which the node is linked.
	 *
	 * @return the left son.
	 */
	public Node getLeftSon()
	{
		return leftSon;
	}

	/**
	 * Returns the right son node to which the node is linked.
	 *
	 * @return the right son.
	 */
	public Node getRightSon()
	{
		return rightSon;
	}

	/**
	 * Returns the son node placed in the side we pass as parameter.
	 *
	 * @param side the side where is placed the son node. Use constants LEFT, RIGHT.
	 * @return the son placed at the given side.
	 */
	public Node getSon(int side)
	{
		if (!isLeaf())
		{
			if (side == LEFT)
				return getLeftSon();
			else if (side == RIGHT)
				return getRightSon();
		}
		return null;
	}

	/**
	 * Returns the sibling node (the other son of the node's parent).
	 *
	 * @return the sibling node.
	 */
	public Node getSibling()
	{
		if (getParent() != null)
		{
			if (getSide() == LEFT)
				return getParent().getRightSon();
			else
				return getParent().getLeftSon();
		}
		else
			return null;
	}

	/**
	 * Returns the symmetric key stored in the node.
	 *
	 * @return the symmetric key.
	 */
	public KEK getKEK()
	{
		return key;
	}

    public KEK getOldKEK()
    {
        return oldkey;
    }

	/**
	 * Returns the object that represents the identifier of the member associated to the node.
	 *
	 * @return the unique identifier.
	 */
	public MemberID getMemberID()
	{
		return member;
	}

	/**
	 * Returns the name of the node [row, column]. The root's name is [1, 1] and
	 * the count go on for further nodes.
	 *
	 * @return the name of the node [row, column] in an int array.
	 */
	public int[] getName()
	{
		return name;
	}

	/**
	 * Returns the name of the node [row, column]. The root's name is [1, 1] and
	 * the count go on for further nodes.
	 *
	 * @return the name of the node [row, column] in a String: "row, column".
	 */
	public String getNameToString()
	{
		return name[0] + "," + name[1];
	}

	/**
	 * Sets the value of the linked parent node to the node object given by the paramater.
	 *
	 * @param parent specifies the new value of the linked parent node.
	 */
	public void setParent(Node parent)
	{
		this.parent = parent;
	}

	/**
	 * Sets the value of the linked left son node to the node object given by the paramater.
	 *
	 * @param leftSon specifies the new value of the linked left son node.
	 */
	public void setLeftSon(Node leftSon)
	{
		this.leftSon = leftSon;
	}

	/**
	 * Sets the value of the linked right son node to the node object given by the paramater.
	 *
	 * @param rightSon specifies the new value of the linked right son node.
	 */
	public void setRightSon(Node rightSon)
	{
		this.rightSon = rightSon;
	}

	/**
	 * Links the node given as parameter to the free-son side of the node. It gives priority to fill
	 * the sons space from left to right if it is empty.
	 *
	 * @param node the node to be linked as son of the node.
	 */
	public void setSon(Node node)
	{
		if (getLeftSon() == null)
			setLeftSon(node);
		else
			setRightSon(node);
	}

	/**
	 * Sets the symmetric key to be stored in the node.
	 *
	 * @param key the object containing the symmetric key to store.
	 */
	public void setKEK(KEK key)
	{
		oldkey=this.key;
        this.key = key;
	}

	/**
	 * Sets the name [row, column] of the node to the given value.
	 *
	 * @param name an array containing the row and column values.
	 */
	public void setName(int[] name)
	{
		this.name = name;
	}

	////////////////////////////////////////////////////////////////////////////
	//////// Binary Search methods /////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////
	//////// in-order:   left subtree -> root -> right subtree /////////////////
	//////// pre-order:  root -> left subtree -> right subtree /////////////////
	//////// post-order: left subtree -> right subtree -> root /////////////////
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Returns the first node in in-order (search algorithm) starting from the node.
	 *
	 * @return the first node in in-order.
	 */
	private Node firstInOrder()
	{
		Node node;

		for (node = this; node.getLeftSon() != null; node = node.getLeftSon())
			;

		return node;
	}

	/**
	 * Returns the last node in in-order (search algorithm) starting from the node.
	 *
	 * @return the last node in in-order.
	 */
	private Node lastInOrder()
	{
		Node node;

		for (node = this; node.getRightSon() != null; node = node.getRightSon())
			;

		return node;
	}

	/**
	 * Returns the previous node in in-order (search algorithm) starting from the node.
	 *
	 * @return the previous node in in-order.
	 */
	private Node prevInOrder()
	{
		Node temp = this;
		Node node;

		if (temp.getLeftSon() != null)
			node = temp.getLeftSon().lastInOrder();
		else
			for (; (node = temp.getParent()) != null && temp == node.getLeftSon(); temp = node)
				;

		return node;
	}

	/**
	 * Returns the next node in in-order (search algorithm) starting from the node.
	 *
	 * @return the next node in in-order.
	 */
	private Node nextInOrder()
	{
		Node temp = this;
		Node node;

		if (temp.getRightSon() != null)
			node = temp.getRightSon().firstInOrder();
		else
			for (; (node = temp.getParent()) != null && temp == node.getRightSon(); temp = node)
				;

		return node;
	}

	/**
	 * Returns the first node in pre-order (search algorithm) starting from the node.
	 *
	 * @return the first node in pre-order.
	 */
	public Node firstPrOrder()
	{
		return this;
	}

	/**
	 * Returns the last node in pre-order (search algorithm) starting from the node.
	 *
	 * @return the last node in pre-order.
	 */
	public Node lastPrOrder()
	{
		Node node;

		for (node = this; node.getRightSon() != null || node.getLeftSon() != null;)
		{
			if (node.getRightSon() != null)
				node = node.getRightSon();
			else
				node = node.getLeftSon();
		}

		return node;
	}

	/**
	 * Returns the previous node in pre-order (search algorithm) starting from the node.
	 *
	 * @return the previous node in pre-order.
	 */
	public Node prevPrOrder()
	{
		Node node = parent;
		if (node != null && node.getLeftSon() != null && this != node.getLeftSon())
			node = node.leftSon.lastPrOrder();

		return node;
	}

	/**
	 * Returns the next node in pre-order (search algorithm) starting from the node.
	 *
	 * @return the next node in pre-order.
	 */
	public Node nextPrOrder()
	{
		Node temp = this;
		Node node;

		if (temp.getLeftSon() != null)
			node = temp.getLeftSon();
		else if (temp.getRightSon() != null)
			node = temp.getRightSon();
		else
		{
			for (; (node = temp.getParent()) != null && (temp != node.getLeftSon() || node.getRightSon() == null); temp = node)
				;
			if (node != null)
				node = node.rightSon;
		}

		return node;
	}

	/**
	 * Returns the first node in post-order (search algorithm) starting from the node.
	 *
	 * @return the first node in post-order.
	 */
	public Node firstPoOrder()
	{
		Node temp;
		Node node;

		for (node = this; (temp = node.getLeftSon() != null ? node.getLeftSon() : node.getRightSon()) != null; node = temp)
			;

		return node;
	}

	/**
	 * Returns the last node in post-order (search algorithm) starting from the node.
	 *
	 * @return the last node in post-order.
	 */
	public Node lastPoOrder()
	{
		return this;
	}

	/**
	 * Returns the previous node in post-order (search algorithm) starting from the node.
	 *
	 * @return the previous node in post-order.
	 */
	public Node prevPoOrder()
	{
		Node temp = this;
		Node node;

		if (temp.getRightSon() != null)
			node = temp.getRightSon();
		else if (temp.getLeftSon() != null)
			node = temp.getLeftSon();
		else
		{
			for (; (node = temp.getParent()) != null && (node.getLeftSon() == null || temp == node.getLeftSon()); temp = node)
				;
			if (node != null)
				node = node.getLeftSon();
		}

		return node;
	}

	/**
	 * Returns the next node in post-order (search algorithm) starting from the node.
	 *
	 * @return the next node in post-order.
	 */
	public Node nextPoOrder()
	{
		Node node = getParent();

		if (node != null && this == node.getLeftSon() && node.getRightSon() != null)
			node = node.getRightSon().firstPoOrder();

		return node;
	}

	/**
	 * Returns the next node in in-order (search algorithm) starting from the node and giving a direction.
	 *
	 * @param direction the direction to go on (backward - {@link #LEFT LEFT}, forward - {@link #RIGHT RIGHT}).
	 * @return the next node in in-order specified by the direction given by the parameter.
	 */
	private Node getNext(int direction)
	{
		return direction != LEFT ? nextInOrder() : prevInOrder();
	}

	////////////////////////////////////////////////////////////////////////////
	//////// Class graphic methods /////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Returns the number of nodes at any depth between the node given as parameter and the node.
	 *
	 * @param root the reference node.
	 * @return an int value containing the quantity of nodes between the reference root and the node.
	 */
	public int getWidth(Node root)
	{
		Node node = this;
		int width = 0;
		int side = root.compareSide(this);

		while (node != root && node != null)
		{
			node = node.getNext(-side);
			width += side;
		}

		return width;
	}

	/**
	 * Returns the mode of the node.
	 *
	 * @return the node's mode.
	 */
	public int getMode()
	{
		return mode;
	}

	/**
	 * Sets the mode to the specified value.
	 *
	 * @param mode the new value of mode.
	 */
	public void setMode(int mode)
	{
		this.mode = mode;
	}

	/**
	 * Sets the field mode to zero (null mode).
	 */
	public void reset()
	{
		mode = 0;
	}

	/**
	 * Returns the node's horizontal position.
	 *
	 * @return the node's x coordinate.
	 */
	public int x()
	{
		return x;
	}

	/**
	 * Returns the node's vertical position.
	 *
	 * @return the node's y coordinate.
	 */
	public int y()
	{
		return y;
	}

	/**
	 * Sets the node location to the specified coordinates.
	 *
	 * @param x the x coordinate.
	 * @param y the y coordinate.
	 */
	public void setLocation(int x, int y)
	{
		this.x = x;
		this.y = y;
	}

	/**
	 * Sets the node destination to the specified coordinates.
	 *
	 * @param X the x destination coordinate.
	 * @param Y the y destination coordinate.
	 */
	public void setDestination(int X, int Y)
	{
		this.X = X;
		this.Y = Y;
	}

	/**
	 * Updates de node's location according to the given speed besides updating the movement differentials.
	 *
	 * @param speed the node's movement speed.
	 */
	public void drawIn(int speed)
	{
		if (mode != 0)
		{
			moveTo(X, Y, speed);
			return;
		}
		int dx = (X - x) / 2;
		int dy = (Y - y) / 2;
		if (dx != 0 || dy != 0)
		{
			x += dx;
			y += dy;
			return;
		}
		else
		{
			x = X;
			y = Y;
			return;
		}
	}

	/**
	 * Updates the node's location moving it towards (X,Y) coordinates with the specified speed.
	 *
	 * @param X the x destination coordinate.
	 * @param Y the y destination coordinate.
	 * @param speed the movement speed value.
	 */
	public void moveTo(int X, int Y, int speed)
	{
		if (speed == 0)
			return;
		this.X = X;
		this.Y = Y;
		if (arrived())
			return;
		int dx = abs(X - x);
		int dy = abs(Y - y);
		int ix = sign(X - x);
		int iy = sign(Y - y);
		int n = dx < dy ? dy : dx;
		if (n > 5)
		{
			vx = ix * dx;
			vy = iy * dy;
		}
		int delay = (10 - speed) + 2;
		n /= delay;
		if (dx >= dy)
		{
			dy <<= 1;
			int balance = dy - dx;
			dx <<= 1;
			for (int i = 0; i <= n; i++)
			{
				if (balance >= 0)
				{
					y += iy;
					balance -= dx;
				}
				balance += dy;
				x += ix;
				if (arrived())
					return;
			}
		}
		else
		{
			dx <<= 1;
			int balance = dx - dy;
			dy <<= 1;
			for (int i = 0; i <= n; i++)
			{
				if (balance >= 0)
				{
					x += ix;
					balance -= dy;
				}
				balance += dx;
				y += iy;
				if (x == X && y == Y)
					return;
			}
		}
	}

	/**
	 * Returns whether or not the node has arrived to the (X,Y) destination coordinates.
	 *
	 * @param X the x destination coordinate.
	 * @param Y the y destination coordinate.
	 * @return true if it has arrived, false otherwise.
	 */
	public boolean arrivedTo(int X, int Y)
	{
		return x == X && y == Y;
	}

	/**
	 * Returns whether or not the node has arrived to the (X,Y) destination coordinates.
	 *
	 * @return true if it has arrived, false otherwise.
	 */
	public boolean arrived()
	{
		return x == X && y == Y;
	}

	/**
	 * Moves the node's location simulating that the node has been pushed.
	 *
	 * @param dx the x differential to add to the current x coordinate.
	 * @param dy the y differential to add to the current y coordinate.
	 */
	private void push(int dx, int dy)
	{
		x += dx;
		y += dy;
	}

	/**
	 * Moves the node's location simulating that the node is falling vertically.
	 *
	 * @param speed the falling speed.
	 */
	public void fall(int speed)
	{
		int dv = speed / 4;
		if (dv == 0)
			dv = 1;
		vy += dv;
		y += vy;
		if (y > Y)
		{
			y = Y;
			vy = 0;
			return;
		}
		else
			return;
	}

	/**
	 * Moves the node according to the differentials of the given node. The node pretends to
	 * having been hitted by the pin node and shakes because of it.
	 *
	 * @param pin the hitting node.
	 */
	public void hitWith(Node pin)
	{
		push(pin.vx / 2, pin.vy / 2);
	}

	/**
	 * Returns the absolute value of the given int value.
	 *
	 * @param a an int value.
	 * @return the absolute value of a.
	 */
	private static int abs(int a)
	{
		return a >= 0 ? a : -a;
	}

	/**
	 * Returns the sign of the given int value.
	 *
	 * @param a an int value.
	 * @return the sign (1, -1) of a.
	 */
	private static int sign(int a)
	{
		return a >= 0 ? 1 : -1;
	}

    /**
     * Returns the difference of level between the leaf node of the deepest level and the leaf node of the less deep
     * level of the subtree defined by taking the node as a tree root.
     *
     * @return the difference of level between the leaf node of the deepest level and the leaf node of the less deep
     * level.
     */
    public int balanced()
	{
		return new Tree(Node.this).balanced();
	}

    /**
     * Returns the node's blind key. This blind key is generated by the function
     * hash SHA-256 of the key stored in the node.
     *
     * @return the node's blind key.
     */
    public KEK getBlindKEK()
    {
        return new KEK(KeyOFT.blind(getKEK().keyData), getKEK().LKH_ID);
    }

    /**
	 * Sets the symmetric key to be stored in the node.
	 *
	 * @param key the object containing the symmetric key to store.
	 */
    public void setKEK(SecretKey key)
    {
        setKEK(new KEK(key, hashCode()));
    }
}
