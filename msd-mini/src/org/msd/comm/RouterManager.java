/** This is the mini version */
package org.msd.comm;

import org.msd.proxy.MSDManager;
import org.msd.cache.Cache;
import java.util.Hashtable;
import org.msd.cache.Service;
import java.util.Enumeration;

/** This class implements the RouterManager layer in the protocol stack of the
 * MSD. It Routes a message or connection selecting the appropited bridge.
 * @version $Revision: 1.5 $ */
public class RouterManager{
    private Hashtable nets=null;
    private Cache cache=null;
    private MSDManager msd=null;

    /** @param m The MSDManager of the system */
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

    /** Sends a message to an MSD, routing if needed. Also, send multicast
     * messages to this MSD.
     * @param m Message
     * @param net interface from ot comes, or null if created in this MSD.
     * @throws java.lang.Exception If any error */
    public void route(Message m,NetworkManager net) throws Exception{
        // if hops==0, discard
        if(m.getHops()<=0){
            return;
        }
        // set hops=hops-1
        m.setHops(m.getHops()-1);

        // if its for me, do loopback
        if(m.getIDTo()==null){
            msd.receive(m,net);
        } else if(m.getIDTo().equals(cache.getID())){
            msd.receive(m,net);
            return;
        }

        // if is multicast...
        if(m.getIDTo()==null){
            // if we were the senders, send to network
            if(net==null){
                // send the message throught every network
                for(Enumeration e=nets.elements(); e.hasMoreElements();){
                    NetworkManager net2=(NetworkManager)e.nextElement();
                    net2.sendM(m);
                }
            } else{
                // bridge multicast messages
            }
        } else{
            // if the MSD has no gateway (or we are), send directly
            String idgw=getGateway(m.getIDTo());
            // in other case, send to the gateway
            // look in every Network manager and when found send directly
            for(Enumeration e=nets.elements(); e.hasMoreElements();){
                NetworkManager net2=(NetworkManager)e.nextElement();
                if(net2.getMSD(idgw)!=null){
                    net2.sendU(m,idgw);
                    return;
                }
            }
            // if we are here, some error happens
            if(net==null){
                throw new Exception("Host not reachable: "+m.getIDTo());
            } else{
            }
        }
    }

    /** @return The gateway to the remote MSD, or the identifier
     * of the remote MSD if no gateway is needed.
     * @param id Identifier of the remote MSD */
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


    /** Routes connections to other MSDs, not this one.
     * @param con Connection to route
     * @param net NetworkManager the connection comes from.
     * @throws java.lang.Exception If any error */
    public void route(Connection con,NetworkManager net) throws Exception{
        if(con.getIDTo().equals(msd.getCache().getID())){
            msd.receive(con,net);
        } else{
            Connection to=openConnection(con.getType(),con.getIDFrom(),
                                         con.getIDTo());
            TransformConnection.connect(con,to);
        }
    }

    /** Opens a connection to the identifier
     * @param type Type of the connection
     * @param from Identifier of the sender
     * @param to Identifier of the recipient
     * @throws java.lang.Exception If the connection couldn't be established
     * @return The new connection. */
    public Connection openConnection(int type,String from,String to) throws
            Exception{
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
        for(Enumeration e=nets.elements(); e.hasMoreElements();){
            NetworkManager n=(NetworkManager)e.nextElement();
            if(n.getMSD(gw)!=null){
                return n.getConnection(type,from,to,gw);
            }
        }
        throw new Exception("Unreachable host: "+to+" with gw="+gw);
    }
    
    /** Receives a message from a NetworkManager.
     * This method is not blocking and returns inmediately.
     * @param m The message received.
     * @param n The network the message comes from. If null, the message comes
     * from this MSD (loopback) */
    public void receive(Message m, NetworkManager n){
        new ReceivingThread(m,n);
    }

    /**
     * Receives a connection from a NetworkManager. This method is not blocking and returns inmediately.
     * @param c The connection received.
     * @param n The network the message comes from. If null, the message comes from this 
     * MSD (loopback)
     */
    public void receive(Connection c, NetworkManager n){
        new ReceivingThread(c,n);
    }   
    
    /** Thread to use to receive connections and messages in a non-blockig way */
    private class ReceivingThread extends Thread{
        private Object data=null;
        private NetworkManager net=null;
        /** Constructor. The Thread will start inmediately.
         * @param data The data (Connection or Message) received.
         * @param n The NetworkManager the data comes from.
         */
        public ReceivingThread(Object data, NetworkManager n){
            this.data=data;
            this.net=n;
            start();
        }
        public void run(){
            try{
                if(data instanceof Connection){
                    route((Connection)data,net);
                }else
                    route((Message)data,net);
            }catch(Exception e){
                System.err.println("Error while receiving object: "+e);
                e.printStackTrace();
            }
        }
    }
}
