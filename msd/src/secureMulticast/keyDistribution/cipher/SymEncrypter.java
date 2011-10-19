package secureMulticast.keyDistribution.cipher;

import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/**
 * <p> This class implements all the ciphering and deciphering processes in the package. With its
 * methods it allows to cipher and decipher byte arrays and converting keys into byte arrays and vice versa.
 *
 * @author  Oscar Burgos & Daniel Jarne
 * @version 1.1, 01/10/04
 */
public class SymEncrypter
{
	////////////////////////////////////////////////////////////////////////////
	//////// SymEncrypter fields ///////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Specifies the cipher object used to encrypt data.
	 */
	private Cipher ecipher;

	/**
	 * Specifies the cipher object used to decrypt data.
	 */
	private Cipher dcipher;

	////////////////////////////////////////////////////////////////////////////
	//////// Class constructors ////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructs the symmetric key encrypter object. Gets the instance of both of the cipher objects.
	 *
	 * @param algorithm the algorithm we will use to cipher and decipher the data.
	 * @param key the symmetric key to initialize the cipher.
	 */
	public SymEncrypter(String algorithm, SecretKey key)
	{
		try
		{
			ecipher = Cipher.getInstance(algorithm);
			dcipher = Cipher.getInstance(algorithm);
			ecipher.init(Cipher.ENCRYPT_MODE, key);
			dcipher.init(Cipher.DECRYPT_MODE, key);

		}
		catch (NoSuchPaddingException e)
		{
			e.printStackTrace();
		}
		catch (NoSuchAlgorithmException e)
		{
			e.printStackTrace();
		}
		catch (InvalidKeyException e)
		{
			e.printStackTrace();
		}
	}

	////////////////////////////////////////////////////////////////////////////
	//////// Class methods /////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Encrypts the given byte array and returns the ciphered bytes.
	 *
	 * @param bytesToCipher the bytes to be ciphered.
	 * @return a byte array containing the ciphered bytes.
	 */
	public byte[] encrypt(byte[] bytesToCipher)
	{
		byte[] encrypted = new byte[0];

		try
		{
			encrypted = ecipher.doFinal(bytesToCipher);
		}
		catch (IllegalStateException e)
		{
			e.printStackTrace();
		}
		catch (IllegalBlockSizeException e)
		{
			e.printStackTrace();
		}
		catch (BadPaddingException e)
		{
			e.printStackTrace();
		}

		return encrypted;
	}

	/**
	 * Encrypts a symmetric key object and returns a ciphered byte array.
	 *
	 * @param keyToCipher the symmetric key to cipher.
	 * @return a byte array containing the ciphered key's bytes.
	 */
	public byte[] encrypt(SecretKey keyToCipher)
	{
		return encrypt(keyToCipher.getEncoded());
	}

	/**
	 * Decrypts the given bytes and returns in a byte array yhe decrypted bytes.
	 *
	 * @param bytesToDecipher the bytes to decipher.
	 * @return a byte array containing the decrypted bytes.
	 */
	public byte[] decrypt(byte[] bytesToDecipher)
	{
		byte[] decrypted = new byte[0];

		try
		{
			decrypted = dcipher.doFinal(bytesToDecipher);
		}
		catch (IllegalStateException e)
		{
			e.printStackTrace();
		}
		catch (IllegalBlockSizeException e)
		{
			e.printStackTrace();
		}
		catch (BadPaddingException e)
		{
			e.printStackTrace();
		}
		return decrypted;
	}

	/**
	 * Decrypts the given bytes and rebuilds a symmetric key object.
	 *
	 * @param keyToDecipher the ciphered bytes containing the key.
	 * @param keyAlgorithm the algorithm implementation of the ciphered key.
	 * @return the recovered symmetric key object.
	 */
	public SecretKey decrypt(byte[] keyToDecipher, String keyAlgorithm)
	{
		return new SecretKeySpec(decrypt(keyToDecipher), keyAlgorithm);
	}
}