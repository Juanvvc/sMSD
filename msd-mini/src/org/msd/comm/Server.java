package org.msd.comm;

import org.msd.comm.NetworkManager;

/** Server in a network.
 * A listening server waits for remote connections (at the network level, i.e.,
 * RFCOMM or TCP connections, or UDP messages) and inform to a network manager
 * of the streams received.
 * @version $Revision: 1.5 $ */
interface Server{
    /** Start the server in url and port. The meaning of this
     * parameters depends of the implementers */
    public void start(String url,int port) throws Exception;

    /** Stop the server. The server will be no longer used.
     * Calling again to start is not guaranteed to work.
     * If the server is yet stopped, a new calling do nothing */
    public void stop();

    /** Set the network manager to receive incoming connections */
    public void setManager(NetworkManager m);

    /** Open a connection from the sendoer of the last message to this MSD,
     * for connecting MSD 'to' and 'from' with messages 'type' */
    public Connection openConnection(CommManager com,int type,String idfrom,
                                     String idto) throws Exception;

    /** Get the url to connect to this server */
    public String getURL();

    /* Get the port to connect to this server */
    public int getPort();
}

