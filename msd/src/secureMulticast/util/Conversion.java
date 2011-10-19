package secureMulticast.util;

import javax.crypto.SecretKey;

/**
 * <p> This class provides some methods for converting byte[] or SecretKey into an hexadecimal String.
 *
 * @author  Oscar Burgos & Daniel Jarne
 * @version 1.1, 01/10/04
 */

public class Conversion
{
    ////////////////////////////////////////////////////////////////////////////
	//////// Class methods /////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////

    /**
     * Turns array of bytes into hex string
     *
     * @param buf Array of bytes to convert to hex string
     * @return Generated hex string
     */
    public static String asHex (byte buf[])
    {
        StringBuffer strbuf = new StringBuffer(buf.length * 2);
        int i;

        for (i = 0; i < buf.length; i++)
        {
            if (((int) buf[i] & 0xff) < 0x10)
                strbuf.append("0");

            strbuf.append(Long.toString((int) buf[i] & 0xff, 16));
        }

        return strbuf.toString();
     }

    /**
     * Turns a SecretKey into hex string
     *
     * @param key SecretKey to convert to hex string.
     * @return Generated hex string
     */
    static public String asHex(SecretKey key)
    {
        return asHex(key.getEncoded());
    }
}
