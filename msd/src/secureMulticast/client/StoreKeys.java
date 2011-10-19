package secureMulticast.client;

import secureMulticast.keyDistribution.symKeyGen.KEK;
import secureMulticast.keyDistribution.symKeyGen.KeyOFT;

import java.util.Vector;

/**
 * <p> This class implements all the needed methods to store and to recover the KEKs in any algorithm.
 *
 * @author  Oscar Burgos & Daniel Jarne
 * @version 1.1, 01/10/04
 */
public class StoreKeys {
    private Vector StoredKEKs;
    private int LKH_version;
    private int newKEKindex;

    /**
     * Constructs a object capable of to store and to recover the KEKs.
     */
    public StoreKeys()
    {
        StoredKEKs = new Vector();
    }

    /**
     * Returns the index of the Vector which contains the passed id.
     *
     * @param LKH_ID id of the searched key.
     * @return an int which is the index where is the searched key.
     */
    public int getIndexKey(int LKH_ID)
    {
        for (int index = 0; index < StoredKEKs.size(); index++)
		{
			KEK key = ((KEK) StoredKEKs.get(index));
			if (key.LKH_ID == LKH_ID)
                return index;
		}
        return -1;
    }

    /**
     * Returns a KEKobject in order to decipher a packet.
     *
     * @param identificators Vector specifying some identifiers of some the needed key.
     * @return a KEKobject which contains a symmetric key needed to decipher the packet.
     */
    public KEK getKeyToDecipher(Vector identificators)
    {
        for(int index = 0; index < identificators.size(); index++)
        {
            int indexKEK = getIndexKey(((Integer) identificators.get(index)).intValue());
            if(indexKEK != -1)
                return (KEK) StoredKEKs.get(indexKEK);
        }
        return null;
    }

    /**
     * Returns a KEKobject in order to decipher a packet.
     *
     * @param LKH_ID an int value specifying the identifier of the key needed.
     * @return a KEKobject which contains the symmetric key needed to decipher the packet.
     */
    public KEK getKeyToDecipher(int LKH_ID)
    {
        int index = getIndexKey(LKH_ID);
        if(index >= 0)
            return (KEK) StoredKEKs.get(index);
        return null;
    }

    /**
     * Removes the KEK which has the passed identificator.
     *
     * @param LKH_ID key identificator
     */
    public void removeKeys(int LKH_ID)
    {
        int index = getIndexKey(LKH_ID);
        if(index >= 0)
            StoredKEKs.setSize(index + 1);
    }

    /**
     * Removes the KEKs which have any identificator of the Vector.
     *
     * @param list identificators list.
     */
    public void removeKEKs(Vector list)
    {
        Vector indexs = new Vector(0);

        for(int i=0; i<StoredKEKs.size(); i++)
            for(int j=0; j<list.size(); j++)
            {
                if(((KEK)StoredKEKs.get(i)).LKH_ID == ((Integer)list.get(j)).intValue())
                {
                    System.out.println("Deleted node with ID: " + ((Integer)list.get(j)).intValue());
                    indexs.add(new Integer(i));
                }
            }
        for(int i=indexs.size()-1; i>=0; i--)
            StoredKEKs.removeElementAt(((Integer)indexs.get(i)).intValue());
    }

    /**
     * Returns a vector containing the whole KEKs stored in the client.
     *
     * @return a vector containing all the stored KEKs.
     */
    public Vector getStoredKEKs()
	{
		return StoredKEKs;
	}

    /**
     * Returns the index of the first changed key in the last rekeying.
     *
     * @return the index of the first changed key in the last rekeying.
     */
    public int getNewKEKIndex()
	{
		return newKEKindex;
	}

    /**
     * Updates the vector that contains the KEKs needed by the client. It also updates the value of
     * the index of the first of the new stored keys.
     *
     * @param LKH_version an int value specifying the LKH algorithm that is used.
     * @param Keys the new KEKs to be stored.
     */
    public void storeKeys(int LKH_version, Vector Keys)
	{
		if (StoredKEKs.size() == 0)
		{
			this.LKH_version = LKH_version;
			StoredKEKs.addElement(Keys.get(0));
			return;
		}
		else if (this.LKH_version != LKH_version)
		{
			System.out.println("Incorrect LKH version");
			return;
		}

		newKEKindex = StoredKEKs.size() - 1;
		StoredKEKs.addAll(Keys);
	}

    /**
     * Updates the vector that contains the KEKs needed by the client for Single Message algorithm. It also updates the
     * value of the index of the first of the new stored keys.
     *
     * @param LKH_version an int value specifying the LKH algorithm that is used.
     * @param Keys the new KEKs to be stored.
     */
    public void storeKeysSM(int LKH_version, Vector Keys)
	{
		if (this.LKH_version != LKH_version)
		{
            System.out.println("Incorrect LKH version");
			return;
		}
        newKEKindex = - 1;
		StoredKEKs.addAll(Keys);
	}

    /**
     * Updates the vector that contains the KEKs needed by the client for OFT algorithm. It also updates the
     * value of the index of the first of the new stored keys.
     *
     * @param LKH_version an int value specifying the LKH algorithm that is used.
     * @param Keys the new KEKs to be stored.
     * @param flag flag which indicates the operation to realize.
     * @param StoredBlindKEKs the stored blind keys in the client.
     */
    public void storeKeysOFT(int LKH_version, Vector Keys, int flag, Vector StoredBlindKEKs)
	{
        if (StoredKEKs.size() == 0)
		{
			this.LKH_version = LKH_version;
			StoredKEKs.addElement(Keys.get(0));
			return;
		}
		else if (this.LKH_version != LKH_version)
		{
            System.out.println("Incorrect LKH version");
			return;
		}

        switch(flag)
        {
            case 0:
                for(int i=0; i<Keys.size(); i++)
                    StoredBlindKEKs.add(((KEK)Keys.get(i)));
                break;

            case 1:
                for(int i=0; i<StoredKEKs.size(); i++)
                    if(((KEK)(StoredKEKs.get(i))).LKH_ID==((KEK)Keys.get(0)).LKH_ID)
                        newKEKindex = i;
                KEK aux = (KEK) StoredKEKs.get(0);
                StoredKEKs.removeAllElements();
                StoredKEKs.add(aux);
                for(int i=0; i<StoredBlindKEKs.size(); i++)
                {
                    if(((KEK)StoredBlindKEKs.get(i)).LKH_ID==((KEK)Keys.get(0)).LKH_ID)
                    {
                        StoredBlindKEKs.set(i, Keys.get(0));
                        newKEKindex=i;
                    }
                }
                break;

            case 2:
                newKEKindex=-1;
                StoredKEKs.removeAllElements();
                StoredKEKs.add(Keys.get(0));
                StoredBlindKEKs.remove(0) ;
                break;

            case 3:
                newKEKindex=-1;
                StoredKEKs.removeAllElements();
                StoredKEKs.add(Keys.get(0));
                StoredBlindKEKs.add(0, Keys.get(1));
                break;
             case 4:
                newKEKindex=-1;
                StoredKEKs.removeAllElements();
                StoredKEKs.add(Keys.get(0));
                break;
        }
        KeyOFT.recalculateOFT(StoredKEKs, StoredBlindKEKs);
	}

    /**
     * Removes all of the elements from Vector StoredKEKs.
     */
    public void clear()
    {
        StoredKEKs.clear();
    }

    /**
     * Returns the number of components in Vector StoredKEKs.
     *
     * @return the number of components in Vector StoredKEKs.
     */
    public int size()
    {
        return StoredKEKs.size();
    }

    /**
     * Inserts the specified element at the specified position in Vector StoredKEKs.
     *
     * @param index index at which the specified key is to be inserted.
     * @param key key to be inserted.
     */
    public void add(int index, KEK key)
    {
        StoredKEKs.add(index, key);
    }

    /**
     * Returns the element at the specified position in this Vector.
     *
     * @param index index of element to return.
     * @return object at the specified index
     */
    public Object get(int index)
    {
        return StoredKEKs.get(index);
    }

    /**
     * Replaces the element at the specified position in Vector StoredKEKs with the specified key.
     *
     * @param index index of element to replace.
     * @param key key to be stored at the specified position.
     */
    public void set(int index, KEK key)
    {
        StoredKEKs.set(index, key);
    }

    /**
     * Returns the version of the used LKH algorithm.
     *
     * @return the version of the used LKH algorithm.
     */
    public int getLKHversion()
    {
        return LKH_version;
    }
}
