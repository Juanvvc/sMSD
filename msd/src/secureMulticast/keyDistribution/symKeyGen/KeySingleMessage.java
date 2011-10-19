package secureMulticast.keyDistribution.symKeyGen;

import secureMulticast.binaryTree.Node;
import secureMulticast.binaryTree.Tree;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * <p> This class allows generating keys for the Single Message algorithm.
 *
 * @author  Oscar Burgos & Daniel Jarne
 * @version 1.1, 01/10/04
 */
public class KeySingleMessage {

    static byte[] seed;
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
        return XOR(key1.getEncoded(), key2.getEncoded(), key1.getAlgorithm());
    }

    /**
     * Returns a SecretKey whose value is (key1 ^ key2).
     *
     * @param key1 value to be XOR'ed with key2.
     * @param key2 value to be XOR'ed with key1.
     * @param algorithm
     * @return key1 ^ key2
     */
    public static SecretKey XOR(byte[] key1, byte[] key2, String algorithm)
    {
        byte[] c = new byte[key1.length];

        if(key1.length == 0 || key2.length == 0 || key1.length != key2.length)
            return null;

        for(int i=key1.length-1; i>=0; i--)
        {
            c[i] = (byte) (key1[i] ^ key2[i]);
        }
        return new SecretKeySpec(c, algorithm);
    }

    /**
     * Returns a SecretKey whose value is (key1 * key2).
     *
     * @param key1 value to be multiplyed with key2.
     * @param key2 value to be multiplyed with key1.
     * @return key1 * key2
     */
    public static BigInteger multiply(BigInteger key1, BigInteger key2)
    {
        return key1.multiply(key2);
    }

    /**
     * Returns a BigInteger whose value is (key1 * key2).
     *
     * @param key1 value to be multiplyed with key2.
     * @param key2 value to be multiplyed with key1.
     * @return key1 * key2
     */
    public static BigInteger multiply(BigInteger key1, byte[] key2)
    {
        return multiply(key1, new BigInteger(doPositive(key2)));
    }

    /**
     * Transforms a SecretKey into a BigInteger.
     *
     * @param key value to be multiplyed with key2.
     * @return a BigInteger.
     */
    public static BigInteger toBigInteger(SecretKey key)
    {
        return new BigInteger(key.getEncoded());
    }

    /**
     * Transforms a BigInteber into a SecretKey.
     *
     * @param number the BigInteger to transform
     * @param algorithm the algorithm's key
     * @return the BigInteger transformed to SecretKey
     */
    public static SecretKey toSecretKey(BigInteger number, String algorithm)
    {
        return new SecretKeySpec(number.toByteArray(), algorithm);
    }

    /**
     * Calculates the invariable part of the key for Single Message algorithms.
     *
     * @param node node to which calculates the invariable part of the key.
     * @return the calculated key.
     * @throws NoSuchAlgorithmException
     */
    public static SecretKey F(Node node) throws NoSuchAlgorithmException {
        byte[] buffer = new byte [node.getKEK().keyData.getEncoded().length];
        int identify = node.getKEK().LKH_ID;
        for(int i=0, shift = 24; i<4; i++, shift -=8)
            buffer[i] = (byte) (0xFF & (identify >> shift));
        for(int i=5; i< node.getKEK().keyData.getEncoded().length; i++)
            buffer[i]=0;

        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        sha.update(XOR(buffer, seed, node.getKEK().keyData.getAlgorithm()).getEncoded());
        byte[] temp = sha.digest();
        for(int i=0; i<node.getKEK().keyData.getEncoded().length; i++)
            buffer[i] = (temp[i]);
        buffer[0]= (byte) (buffer[0] | 0x80);
        buffer[buffer.length-1]=(byte) (buffer[buffer.length-1] | 0x01);
        return new SecretKeySpec(buffer, node.getKEK().keyData.getAlgorithm());
    }

    /**
     * Sets up the seed.
     *
     * @param value value for the seed
     * @param length seed's length in bytes
     */
    public static void setSeed(byte[] value, int length)
    {
        byte[] tmp = new byte[length];
        for(int i=0; i<tmp.length; i++)
            tmp[i]=0;
        for(int i=0; i<tmp.length && i<value.length-1; i++)
            tmp[i]=value[i+1];
        seed = tmp;
    }

    /**
     * Returns the seed.
     *
     * @return the seed
     */
    public static byte[] getSeed()
    {
        return seed;
    }

    /**
     * Calculates the R parameter in Single Message LKH and Batch Single Message LKH.
     *
     * @param tree the member's tree
     * @return value of the parameter R
     */
    public static SecretKey calculateParameterR(Tree tree)
    {
        SecretKey r = tree.generateSEK();
        byte[] key = r.getEncoded();
        key[key.length-1]= (byte) (key[key.length-1] & (0xFE));
        key[0]=(byte) (key[0] & (0x7F));
        return new SecretKeySpec(key, tree.getKeyGenerator().getAlgorithm());
    }

    /**
     * Translates a SecretKey into a byte array containing two's-complement binary representation.
     *
     * @param key SecretKey to translate
     * @return bayte array containing two's-complement binary representation
     */
    public static byte[] doPositive(SecretKey key)
    {
        return doPositive(key.getEncoded());
    }

    /**
     * Translates a byte array into a byte array containing two's-complement binary representation.
     *
     * @param key byte array to translate
     * @return bayte array containing two's-complement binary representation
     */
    public static byte[] doPositive(byte[] key)
    {
        byte[] tmp = new byte[(key.length)+1];
        tmp[0]=0;
        for(int j=1; j<key.length+1; j++)
            tmp[j]=key[j-1];
        return tmp;
    }

    /**
     * Obtains the parameter P from encrypted packet by Single Message using the passed key.
     *
     * @param packet the encrypt packet.
     * @param key the key for decrypt.
     * @return the
     */
    public static SecretKey obtainP(byte[] packet, KEK key) {
        BigInteger a = new BigInteger(packet);
        byte[] tmp = new byte[(key.keyData.getEncoded().length)+1];
            tmp[0]=0;
            for(int j=1; j<key.keyData.getEncoded().length+1; j++)
                tmp[j]=key.keyData.getEncoded()[j-1];
        BigInteger b = a.mod(new BigInteger(tmp));
        return new SecretKeySpec(b.toByteArray(), key.keyData.getAlgorithm());
    }

    /**
     * Returns a SecretKey whose value is (key1 + key2).
     *
     * @param a value to be added with b.
     * @param b value to be added with a.
     * @return a + b
     */
    public static BigInteger add(BigInteger a, SecretKey b)
    {
        return a.add(new BigInteger(b.getEncoded()));
    }
}
