package secureMulticast.keyDistribution.symKeyGen;

import javax.crypto.*;
import java.security.NoSuchAlgorithmException;


/**
 * <p> This class is responsible of generating all of the symmetric keys used in the package. It is
 * instanced in the LKH Tree class, and with its methods it is capable of generating new KEKs and SEKs.
 *
 * @author  Oscar Burgos & Daniel Jarne
 * @version 1.1, 01/10/04
 */
public class SymKeyGenerator
{
	////////////////////////////////////////////////////////////////////////////
	//////// Static and basic KEK fields ///////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Defines an int value for the DES algorithm.
	 */
	public static final int DES = 0;

	/**
	 * Defines an int value for the Blowfish algorithm.
	 */
	public static final int Blowfish = 1;

	/**
	 * Defines an int value for the Triple DES algorithm.
	 */
	public static final int DESede = 2;

    /**
	 * Defines an int value for the AES algorithm.
	 */
    public static final int AES = 3;

	/**
	 * Specifies a string value for the algorithm implementation.
	 */
	private String algorithmImpl;

	/**
	 * Specifies the instantiated key generator object.
	 */
	private KeyGenerator keyGen;

	////////////////////////////////////////////////////////////////////////////
	//////// Class constructors ////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructs a symmetric key generator object capable of generating keys through the specified algorithm
	 * implementation.
	 *
	 * @param algorithm an int value specifying the algorithm implementation to use.
	 */
	public SymKeyGenerator(int algorithm)
	{
		switch (algorithm)
		{
			case DES:
				algorithmImpl = "DES";
				break;
			case Blowfish:
				algorithmImpl = "Blowfish";
				break;
			case DESede:
				algorithmImpl = "DESede";
				break;
            case AES:
                algorithmImpl = "AES";
                break;
			default:
				algorithmImpl = "DES";
				break;
		}
		makeKeyGen();
	}

	/**
	 * Constructs a symmetric key generator object capable of generating keys through the specified algorithm
	 * implementation.
	 *
	 * @param algorithm a string value specifying the algorithm implementation to use.
	 */
	public SymKeyGenerator(String algorithm)
	{
		algorithmImpl = algorithm;
		makeKeyGen();
	}

	////////////////////////////////////////////////////////////////////////////
	//////// Class methods /////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Returns a string specifying the algorithm implementation used to generate the keys.
	 *
	 * @return a string containing the description of the used algorithm implementation.
	 */
	public String getAlgorithm()
	{
		return algorithmImpl;
	}

	/**
	 * Creates the key generator instance with the key algorithm implementation specified in the constructor.
	 */
	private void makeKeyGen()
	{
        try
        {
            keyGen = KeyGenerator.getInstance(algorithmImpl);
            if(algorithmImpl=="AES")
                keyGen.init(256);
        } catch (NoSuchAlgorithmException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Generates a KEK object assigning the identifier specified by the hash value of the container node.
	 *
	 * @param nodeHashCode the hash value of the container node.
	 * @return the newly generated KEK.
	 */
	public KEK generateKEK(int nodeHashCode)
	{
        return new KEK(keyGen.generateKey(), nodeHashCode);
	}

	/**
	 * Generates a SEK object to allow cipher the session data.
	 *
	 * @return the newly generated symmetric key.
	 */
	public SecretKey generateSEK()
	{
        return keyGen.generateKey();
	}
}