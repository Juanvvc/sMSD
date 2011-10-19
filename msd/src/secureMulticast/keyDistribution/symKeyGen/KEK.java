package secureMulticast.keyDistribution.symKeyGen;

import secureMulticast.keyDistribution.cipher.*;

import javax.crypto.*;
import java.io.*;

/**
 * <p> This class is an abstraction of the Key Encryption Key structure that must be stored in each of the nodes
 * that set up the LKH tree.
 *
 * @author  Oscar Burgos & Daniel Jarne
 * @version 1.1, 01/10/04
 */
public class KEK
{
	////////////////////////////////////////////////////////////////////////////
	//////// Static and basic KEK fields ///////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Defines an int value for the DES algorithm (String "DES" hash value).
	 */
	private static final int DES = 67570;

	/**
	 * Defines an int value for the Blowfish algorithm (String "Blowfish" hash value).
	 */
	private static final int Blowfish = -581607126;

	/**
	 * Defines an int value for the Triple DES algorithm (String "DESede" hash value).
	 */
	private static final int DESede = 2013078132;

    /**
	 * Defines an int value for the AES algorithm (String "AES" hash value).
	 */
    private static final int AES = 64687;

	/**
	 * Specifies an int valoue for the identifier of this KEK.
	 */
	public int LKH_ID;

	/**
	 * Specifies an int value for the symmetric key algorithm.
	 */
	private int keyType;

	/**
	 * Specifies the symmetric key data.
	 */
	public SecretKey keyData;

	////////////////////////////////////////////////////////////////////////////
	//////// Class constructors ////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructs a KEK object. A KEK object needs the identifier for the KEK and the symmetric key data.
	 *
	 * @param key the symmetric key data of this KEK.
	 * @param nodeHashCode the hash value of the object node which stores the KEK (the identifier for the node).
	 */
	public KEK(SecretKey key, int nodeHashCode)
	{
		LKH_ID = nodeHashCode;
		keyType = key.getAlgorithm().hashCode();
		keyData = key;
	}

	////////////////////////////////////////////////////////////////////////////
	//////// Class methods /////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

	/** Builds the KEKs data structure to be inserted in a rekeying packet. KEK structure:
	 *
	 * <table border="1" cellspacing="1">
	 * <tr>
	 * <td><div align="center"><strong>Key ID</strong></div></td>
	 * <td><div align="center"><strong>Key type</strong></div></td>
	 * <td><div align="center"><strong>Key data</strong></div></td>
	 * </tr>
	 * <tr>
	 * <td><div align="center">4 bytes</div></td>
	 * <td><div align="center">8 bytes (ciphered)</div></td>
	 * <td><div align="center">X bytes (depending on cipher key used)</div></td>
	 * </tr>
	 * </table>
	 *
	 * @param cipher the object used to cipher the KEK data.
	 * @return a byte array containing the KEK ID, type and ciphered data.
	 */
	public byte[] buildKEKpacket(SymEncrypter cipher)
	{
		ByteArrayOutputStream KEK = new ByteArrayOutputStream();

		KEK.write(intTo4Bytes(LKH_ID), 0, 4);
		KEK.write(intTo4Bytes(keyType), 0, 4);
		byte[] cipheredData = cipher.encrypt(keyData);
		KEK.write(cipheredData, 0, cipheredData.length);

		return KEK.toByteArray();
	}

	/**
	 * Unpacks the KEK message and rebuilds the KEK data in a new KEK object.
	 *
	 * @param cipher the object used to decipher the data.
	 * @param KEKpacket the KEK ciphered bytes.
	 * @return a new KEK object containing the unpacked key data.
	 */
	public static KEK unbuildKEKpacket(SymEncrypter cipher, byte[] KEKpacket)
	{
		ByteArrayOutputStream KEK = new ByteArrayOutputStream();

		KEK.write(KEKpacket, 0, 4);
		int LKH_ID = BytesToInt(KEK.toByteArray());
		KEK.reset();
		KEK.write(KEKpacket, 4, 4);
		int keyType = BytesToInt(KEK.toByteArray());
		KEK.reset();
		KEK.write(KEKpacket, 8, KEKpacket.length - 8);
		SecretKey keyData = cipher.decrypt(KEK.toByteArray(), KeyTypeString(keyType));

		return new KEK(keyData, LKH_ID);
	}

	/**
	 * Converts an int value to 4 bytes.
	 *
	 * @param number the number to convert in a byte array.
	 * @return a byte array containing the bytes that represents the int value.
	 */
	public static byte[] intTo4Bytes(int number)
	{
		byte[] bytes = new byte[4];

		bytes[0] = (byte) ((number >>> 0) & 0xff);
		bytes[1] = (byte) ((number >>> 8) & 0xff);
		bytes[2] = (byte) ((number >>> 16) & 0xff);
		bytes[3] = (byte) ((number >>> 24) & 0xff);

		return bytes;
	}

	/**
	 * Converts a byte array to an int value.
	 *
	 * @param bytes the byte array to convert to an int value.
	 * @return the int value specified by the byte array.
	 */
	public static int BytesToInt(byte[] bytes)
	{
		return (bytes[0] & 0xff) + ((bytes[1] & 0xff) << 8) + ((bytes[2] & 0xff) << 16) + ((bytes[3] & 0xff) << 24);
	}

	/**
	 * Maps an int value to a description string for each of the supported key types.
	 *
	 * @param keyType an int value for the key type.
	 * @return the mapped string value.
	 */
	private static String KeyTypeString(int keyType)
	{
		switch (keyType)
		{
			case DES:
				return "DES";
			case Blowfish:
				return "Blowfish";
			case DESede:
				return "DESede";
            case AES:
                return "AES";
			default:
				return null;
		}
	}
}