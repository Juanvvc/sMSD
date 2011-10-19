package org.msd.comm;

import org.msd.proxy.MSDManager;
import org.msd.cache.Cache;
import java.util.Hashtable;
import org.msd.cache.Service;

import org.apache.log4j.Logger; //@@l

/** This class implements the RouterManager layer in the protocol stack of the
 * MSD. It Routes a message or connection selecting the appropited bridge.
 * @version $Revision: 1.29 $ */
public class RouterManager{
    private static final Logger logger=Logger.getLogger(RouterManager.class); //@@l
    private Hashtable nets=null;
    private Cache cache=null;
    private MSDManager msd=null;

    /**
     * The constructor.
     * @param m The MSDManager layer of the system
     * @throws java.lang.Exception If the router can not be established
     */
    public RouterManager(MSDManager m) throws Exception{
        cache=m.getCache();
        nets=m.getNetworks();
        msd=m;

        if(msd==null){
            throw new Exception("We have no MSDManager");
        }
        if(nets==null){
            throw new Exception("We have no networks");
        }
        if(cache==null){
            throw new Exception("We have no cache");
        }
    }

    /** Sends a message to an MSD, routing if needed. Sends multicast
     * messages from this MSD. This method can be blocked until the message
     * is managed.
     * @param m Message
     * @param net interface from ot comes, or null if created in this MSD.
     * @throws java.lang.Exception If any error */
    public void route(Message m,NetworkManager net) throws Exception{
        logger.debug("Routing message type "+m.getType()+" from "+m.getIDFrom()+ //@@l
                     " to "+m.getIDTo()); //@@l
        // if hops==0, discard
        if(m.getHops()<=0){
            logger.debug("Discarding message"); //@@l
            return;
        }
        // set hops=hops-1
        m.setHops(m.getHops()-1);

        // if its for me, do loopback
        if(m.getIDTo()==null){
            msd.receive(m,net);
        } else if(m.getIDTo().equals(cache.getID())||m.getIDTo().equals("0")){
            msd.receive(m,net);
            return;
        }

        // if is multicast...
        if(m.getIDTo()==null){
            // if we were the senders, send to network
            if(net==null){
                // send the message throught every network
                Object o[]=nets.values().toArray();
                for(int i=0;i<o.length;i++){
                    NetworkManager net2=(NetworkManager)o[i];
                    net2.sendM(m);
                    logger.debug("Sent through "+net2.getGenericName()); //@@l
                }
            } else{
                // bridge multicast messages
                /*                Object o[]=msd.getNetworksBridged(net.getGenericName()).toArray();
                                for(int i=0; i<o.length; i++){
                                    NetworkManager n=(NetworkManager)o[i];
                                    n.sendM(m);
                                } */
            }
        } else{
            // if the MSD has no gateway (or we are), send directly
            String idgw=getGateway(m.getIDTo());
            // in other case, send to the gateway
            // look in every Network manager and when found send directly
            Object o[]=nets.values().toArray();
            for(int i=0;i<o.length;i++){
                NetworkManager net2=(NetworkManager)o[i];
                if(net2.getMSD(idgw)!=null){
                    net2.sendU(m,idgw);
                    return;
                }
            }
            // if we are here, some error happens
            if(net==null){
                throw new Exception("Host not reachable: "+m.getIDTo());
            } else{
                logger.warn("Host not reachable: "+m.getIDTo()); //@@l
            }
        }
    }

    /**
     * Returns the gateway (bridge) to the remote MSD, or the identifier
     * of the remote MSD if no gateway is needed.
     * @param id Identifier of the remote MSD
     * @return An identifier of an MSD to contact the MSD identified by 'id',
     * or null if unknown.
     */
    public String getGateway(String id){
        Service msd=this.msd.getMSD(id);
        if(msd==null){
            return null;
        }
        String gw=msd.getGateway();
        if(gw==null){
            gw=msd.getIDCache();
        }
        return gw;
    }

    /** Routes connections in MSDs.
     * If the connection is for this MSD, this method calls to
     * MSDManager.receive and can be block until the connection is managed.
     * @param con Connection to route
     * @param net NetworkManager the connection comes from.
     * @throws java.lang.Exception After any error */
    public void route(Connection con,NetworkManager net) throws Exception{
        logger.debug("Connect connection from "+con.getIDFrom()+" to "+ //@@l
                     con.getIDTo()); //@@l
        if(con.getIDTo()==null||con.getIDTo().equals("0")||
           con.getIDTo().equals(msd.getCache().getID())){
            msd.receive(con,net);
        } else{
            Connection to=openConnection(con.getType(),con.getIDFrom(),
                                         con.getIDTo());
            TransformConnection.connect(con,to);
        }
    }

    /** Opens a connection to the remote MSD, bridging if needed.
     * @param type Type of the connection
     * @param from Identifier of the sender
     * @param to Identifier of the recipient
     * @throws java.lang.Exception If the connection couldn't be established
     * @todo Test the local connections
     * @return The new connection. */
    public Connection openConnection(int type,String from,String to) throws
            Exception{
        logger.debug("Open connection from "+from+" to "+to+" type "+type); //@@l

        // a local connection
        if(to.equals(msd.getID())){
            LocalConnection one=new LocalConnection(type,to);
            LocalConnection other=new LocalConnection(type,to);
            one.setOther(other);
            other.setOther(one);
            receive(one,null);
            return other;
        }

        // look for the receiver in the cache, take the gateway
        Service msd=this.msd.getMSD(to);
        if(msd==null){
            throw new Exception("Host unknown"); //@@l
        }
        String gw=msd.getGateway();
        if(gw==null||gw.length()==0){
            gw=to;
        }
        // look for the gw in the networks
        Object o[]=nets.values().toArray();
        for(int i=0;i<o.length;i++){
            NetworkManager n=(NetworkManager)o[i];
            if(n.getMSD(gw)!=null){
                return n.getConnection(type,from,to,gw);
            }
        }
        throw new Exception("Unreachable host: "+to);
    }

    /** Receives a message from a NetworkManager.
     * This method is not blocking and returns inmediately.
     * @param m The message received.
     * @param n The network the message comes from. If null, the message comes
     * from this MSD (loopback) */
    public void receive(Message m,NetworkManager n){
        new ConnectionThread(m,n);
    }

    /** Receives a connection from a NetworkManager.
     * This method is not blocking and returns inmediately.
     * @param c The connection received.
     * @param n The network the message comes from. If null, the message comes
     * from this MSD (loopback) */
    public void receive(Connection c,NetworkManager n){
        new ConnectionThread(c,n);
    }

    /** Thread to use to receive connections and messages in a non-blockig way */
    private class ConnectionThread extends Thread{
        private Object data=null;
        private NetworkManager net=null;
        /** Constructor. The Thread will start inmediately.
         * @param data The data (Connection or Message) received.
         * @param n The NetworkManager the data comes from.
         */
        public ConnectionThread(Object data,NetworkManager n){
            super("ManageMessage");
            if(data==null){
                throw new NullPointerException();
            }
            this.data=data;
            this.net=n;
            start();
        }

        public void run(){
            try{
                if(data instanceof Connection){
                    route((Connection)data,net);
                } else{
                    route((Message)data,net);
                }
            } catch(Exception e){
                logger.warn("Error while receiving entity: "+e); //@@l
                e.printStackTrace();
                if(data instanceof Connection){
                    ((Connection)data).closeConnection();
                }
            }
        }
    }
}
