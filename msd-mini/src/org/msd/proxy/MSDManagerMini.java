/* This is the mini version */
package org.msd.proxy;

import org.msd.cache.*;
import org.msd.comm.*;
import java.util.*;

/** This class implements the MSDManager layer in the protocol stack of the MSD.
 * Applications using the MSD as a library will probably use only this class,
 * and its events.
 *
 * @version $Revision: 1.4 $
 */
public class MSDManagerMini extends MSDManager{
    /** Empty Constructor.
     * This class needs an empty constructor if it is loaded from
     * Class.newInstace (for example, throught a ClassLoader). Anyway, you
     * must avoid using any method before configure the manager with init(). */
    public MSDManagerMini(){}

    /** Initializes this manager */
    public void init(Network ignored,Cache cache) throws Exception{
        Network netMSD=new Network(cache,false);
        netMSD.setName("MSD");
        super.init(netMSD,cache);

        // start objects
        nets=new Hashtable();
        router=new RouterManager(this);
        comm=new CommManager();

        msd=new Service(cache,true);
        msd.setName("MSD");

        // try to start the Bluetooth network
        Network net=new Network(cache,false);
        net.setName("bluetooth");
        msd.appendChild(net);

        String network=net.getName();

        try{
            NetworkManager nm=new BluetoothNetworkManager();
            String uuid="0000111A00001000800000805F9B34FB";
            Address local=new Address(uuid,-1);
            nm.init(network,local,null,this,comm);
            nets.put(network,nm);
        } catch(Throwable e){
            System.err.println("Bluetooth network not started: "+e);
            e.printStackTrace();
        }

        // if we do not have an available network, exit
        if(nets.size()==0){
            throw new Exception("No network available");
        }

        // try to join the network
        NetworkManager bluetooth=(NetworkManager)nets.get("bluetooth");
        doMainRequest(bluetooth);

        working=true;
    }

    /** Receive a connection where receive and send messages */
    public void receive(Connection c,NetworkManager net) throws Exception{
        System.out.println("Closing connection of type "+c.getType());
        c.close();
    }

    /** Receive a message from a network manager.
     * @return A message as a response, or null if no response
     * is needed */
    public void receive(Message m,NetworkManager net) throws Exception{
        /** End of line */
        /** Message keeping the response to the received message. Message is
         * null if not response is needed */
        switch(m.getType()){
        case Message.MAIN_REPLY:
            try{
                // store the main information
                Service s=cache.createElementFromXML(new String(m.getData()));
                net.setMSDMain(s);
                cache.addElement(s);
                
                // get the MSDs from the main
                s=new Service(cache,false);
                s.setIDCache("");
                s.setName("MSD");
                searchService(s,false);
                
                triggerEvent(MSDEvent.UPDATED,net==null?null:net.getGenericName());
            }catch(Exception e){
                e.printStackTrace();
            }            
            break;
        default:
        }
    }  
}
