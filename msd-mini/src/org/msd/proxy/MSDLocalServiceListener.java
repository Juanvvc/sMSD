package org.msd.proxy;

import org.msd.comm.Connection;

/** The interface to be implemented by the objects offering a local
 * service registered with the MSD library.
 */
public interface MSDLocalServiceListener{
        /** Wether or not the service can be connected with this remote Connection.
         * @param c The connection to the remote client.
         * @throws java.lang.Exception If the connection is not accepted.
         */
        public boolean canConnect(Connection c);
        /** Starts using the service with this previosly accepted connection.
         * @param c The connection
         */
        public void use(Connection c);
}
