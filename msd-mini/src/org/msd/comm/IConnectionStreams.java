package org.msd.comm;

import java.io.IOException;

/** Open and close standard streams from an Internet Socket */
class IConnectionStreams extends ConnectionStreams{
    private java.net.Socket s;
    /** @param s The socket connection to get the input and putput streams.
     * @throws IOException If the streams couldn't be taken.
     */
    public IConnectionStreams(java.net.Socket s) throws IOException{
        super(s.getInputStream(),s.getOutputStream());
        this.s=s;
    }

    public void close() throws IOException{
        super.close();
        s.close();
    }
}
