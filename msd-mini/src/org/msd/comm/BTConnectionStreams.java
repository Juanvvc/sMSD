package org.msd.comm;

import java.io.IOException;
import javax.microedition.io.StreamConnection;

/** Create a ConnectionStreams from a Bluetooth connection.
 * This class stores a java.io.InputStream and java.io.OutputStream in a
 * single class.
 * @version $Revision: 1.2 $ */
class BTConnectionStreams extends ConnectionStreams{
    private StreamConnection s;
    /** @param s The Bluetooth connection to get the input and putput streams.
     * @throws IOException If the streams couldn't be taken.
     */
    public BTConnectionStreams(StreamConnection s) throws IOException{
        super(s.openInputStream(),s.openOutputStream());
        this.s=s;
    }

    public void close() throws IOException{
        super.close();
        s.close();
    }
}
