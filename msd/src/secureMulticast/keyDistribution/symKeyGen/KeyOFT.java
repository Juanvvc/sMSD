package secureMulticast.keyDistribution.symKeyGen;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Vector;

/**
 * <p> This class allows generating keys for the OFT algorithm.
 *
 * @author  Oscar Burgos & Daniel Jarne
 * @version 1.1, 01/10/04
 */
public class KeyOFT
{
    ////////////////////////////////////////////////////////////////////////////
	//////// Class methods /////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

    /**
     * Returns a SecretKey whose value is (key1 ^ key2).
     *
     * @param key1 value to be XOR'ed with key2.
     * @param key2 value to be XOR'ed with key1.
     * @return key1 ^ key2
     */
    public static SecretKey XOR(SecretKey key1, SecretKey key2)
    {
        byte[] a = key1.getEncoded();
        byte[] b = key2.getEncoded();
        byte[] c = new byte[a.length];

        if(a.length == 0 || b.length == 0 || a.length != b.length)
            return null;

        for(int i=a.length-1; i>=0; i--)
        {
            c[i] = (byte) (a[i] ^ b[i]);
        }
        return new SecretKeySpec(c, key1.getAlgorithm());
    }

    /**
     * Blind a key for SHA-256.
     *
     * @param key to blind.
     * @return a blind key.
     */
    public static SecretKey blind(SecretKey key)
    {
        try {
            byte[] buffer = key.getEncoded();
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(buffer);
            byte[] temp = sha.digest();
            for(int i=0; i<buffer.length; i++)
                buffer[i] = temp[i];
            return new SecretKeySpec(buffer, key.getAlgorithm());
        } catch (NoSuchAlgorithmException e) {}
        return null;
    }

    /**
     * Calculates the used blind KEKs for obtaining the keys path for the OFT algortihm.
     *
     * @param storedKEKs sibling keys path
     * @return blind KEKs
     */
    public static Vector calcualteBlinds(Vector storedKEKs)
    {
        Vector blindKEKs = new Vector(0);
        blindKEKs.add(new KEK(blind(((KEK) storedKEKs.get(0)).keyData), ((KEK) storedKEKs.get(0)).LKH_ID));
        for(int i=1; i < storedKEKs.size(); i++)
        {
            blindKEKs.add(new KEK(XOR(blind(((KEK) storedKEKs.get(i-1)).keyData), ((KEK) storedKEKs.get(i)).keyData), ((KEK) storedKEKs.get(i)).LKH_ID));
        }
        blindKEKs.remove(0);
        return blindKEKs;
    }

    /**
     * Calculate the keys path from the sibling keys path and the blind keys path.
     *
     * @param StoredKEKs  sibling keys path
     * @param StoredBlindKEKs blind keys path
     */
    public static void recalculateOFT(Vector StoredKEKs, Vector StoredBlindKEKs)
    {
        KEK tmp = (KEK) StoredKEKs.get(0);
        StoredKEKs.removeAllElements();
        StoredKEKs.add(tmp);
        for(int i=0; i<StoredBlindKEKs.size(); i++)
        {
            SecretKey key = KeyOFT.XOR(KeyOFT.blind(((KEK) StoredKEKs.get(i)).keyData), ((KEK) StoredBlindKEKs.get(i)).keyData);
            StoredKEKs.add(new KEK(key, ((KEK)(StoredBlindKEKs.get(i))).LKH_ID));
        }
    }
}
