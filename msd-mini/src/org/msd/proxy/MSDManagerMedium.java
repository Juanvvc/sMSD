/* This is the medium version */
package org.msd.proxy;

import org.msd.cache.*;
import org.msd.comm.*;
import java.util.*;

/**
 * This class implements the MSDManager layer in the protocol stack of the MSD.
 * Applications using the MSD as a library will probably use only this class,
 * and its events.
 * 
 * <p>
 * Code to configure an MSDManagerMedium with a default identifier and values
 * for a wifi network (a main, complete MSD must be present in the network)
 * 
 * <pre>
 *  MSDManagerMedium msd=new MSDManagerMedium();
 *  msd.init();
 *  msd.addMSDListener(&lt;MSDListener&gt;);
 *  msd.initNet(new NetConfig(&quot;bluetooth&quot;));
 * </pre>
 * 
 * </p>
 * 
 * <p>
 * Code to configure an MSDManagerMedium with an identifier "medium" and using a
 * local MSD as daemon in port 15151 for the network "wifi".
 * 
 * <pre>
 *  MSDManagerMedium msd=new MSDManagerMedium();
 *  msd.init(null, new Cache(&quot;medium&quot;), null);
 *  msd.addMSDListener(&lt;MSDListener&gt;);
 *  NetConfig nc=new NetConfig(&quot;wifi&quot;);
 *  nc.setMulticastAddress(null);
 *  nc.setMSDAddress(new Address(&quot;127.0.0.1&quot;,15151));
 *  nc.setLocalAddress(null,0);
 *  msd.initNet(nc);
 * </pre>
 * 
 * In both cases the MSD will be ready just after an MSDEvent.UPDATED event.
 * 
 * This MSDMedium uses a simplified version in the Hierarchical protocol to
 * share the cache, and can onl interact with complete MSDs running the Shared
 * protocol.
 * 
 * @version $Revision: 1.8 $
 */
public class MSDManagerMedium extends MSDManager {
	/** Hashtable of services registered by this SD */
	protected Hashtable msdlocalservices;

	/**
	 * Empty Constructor. This class needs an empty constructor if it is loaded
	 * from Class.newInstace (for example, throught a ClassLoader). Anyway, you
	 * must avoid using any method before configure the manager with init().
	 */
	public MSDManagerMedium() {
	}

	/** Initializes the manager with the default values */
	public void init() throws Exception {
		// creates a cache with the miliseconds as identifier
		Cache c = new Cache("" + (new java.util.Date()).getTime());
		// creates a default msd service descriptor
		Service m = new Service(c, true);
		m.setName("MSD");
		// initialices the MSD
		init(null, c);
	}

	/**
	 * Initializes a network using a network configuration. The MSD has to be
	 * initialized yet.
	 * 
	 * @param netConf
	 *            Network configuration to use.
	 * @throws Exception
	 *             If the nwtwork can not be initialized.
	 * @todo Start an IAmHere thread.
	 */
	public void initNet(NetConfig netConf) throws Exception {
		if (!working) {
			throw new Exception("The manager does not seem to be working!");
		}
		System.out.println("Initializing network " + netConf.getName());
		if (nets.get(netConf.getName()) != null) {
			throw new Exception("Network is initialized yet");
		}

		Network net = msd.getNetwork(netConf.getName());
		if (net == null) {
			net = new Network(cache, false);
			net.setName(netConf.getName());
			msd.appendChild(net);
		}
		net.setAddress(netConf.getLocalAddress());
		try {
			NetworkManager nm = new InternetNetworkManager();
			Address local = netConf.getLocalAddress();
			Address multicast = netConf.getMulticastAddress();
			nm.init(net.getName(), local, multicast, this, comm);
			nets.put(net.getName(), nm);

			// start an IAmHere message each ten seconds
			new IAmHere(10,nm);
		} catch (Throwable e) {
			e.printStackTrace();
			msd.deleteChild(net);
			throw new Exception("Network not started: " + e);
		}

		// if there is a main MSD, set the main
		if (netConf.getMSDAddress() != null) {
			Service main = new Service(cache, true);
			main.setName("MSD");
			// we are not aware of the id of the main MSD, and "0" means
			// "you, whatever your id", identified by its network address.
			main.setIDCache("0");
			net = new Network(cache, false);
			net.setName(netConf.getName());
			net.setMain(true);
			net.setAddress(netConf.getMSDAddress());
			main.appendChild(net);
			((NetworkManager) nets.get(net.getName())).setMSDMain(main);
			doInitialUpdate();
			triggerEvent(MSDEvent.UPDATED, net.getName());
		} else {
			searchMainMSD(netConf.getName());
		}
		System.out.println("Network initialized: " + netConf.getName());
	}

	/**
	 * Initializes this manager.
	 * 
	 * @param ignored
	 *            This parameter is ignored.
	 * @param cache
	 *            The cache to use. The first child is the description of the
	 *            MSD, or the cache must be empty.
	 */
	public void init(Network ignored, Cache cache) throws Exception {
		System.out.println("Initializating MSD");
		Network netMSD = new Network(cache, false);
		netMSD.setName("MSD");
		super.init(netMSD, cache);

		// start objects
		nets = new Hashtable();
		router = new RouterManager(this);
		comm = new CommManager();

		// get the first child of the cache: it should be the MSD
		try {
			msd = (Service) cache.getChilds().elementAt(0);
		} catch (Exception e) {
			// MSD not found: create a simple one
			msd = new Service(cache, true);
			msd.setName("MSD");
		}

		working = true;
	}

	/** Receive a connection where receive and send messages */
	public void receive(Connection c, NetworkManager net) throws Exception {
		switch (c.getType()) {
		case Connection.USE:
			manageUse(c, net);
			break;
		default:
			System.out.println("Closing connection of type " + c.getType());
			c.close();
		}
	}

	/** Manage an USE connection */
	public void manageUse(Connection con, NetworkManager net) throws Exception {
		System.out.println("USE Connection");
		// look if it is a local service
		// get the USE message
		Message m = con.receive();

		// try to connect to the service
		try {
			// ensures the service is mine
			Service serv = (Service) cache.createElementFromXML(new String(m
					.getData()));
			if (serv == null || !serv.getIDCache().equals(cache.getID())) {
				throw new Exception("Service not mine");
			}
			Service s = (Service) cache.getElement(serv.getIDCache(), serv
					.getID());
			if (s == null) {
				throw new Exception("Service to use not found");
			}

			MSDLocalServiceListener local = localServiceListener(s);
			if (local != null) {
				if (!local.canConnect(con)) {
					throw new Exception("Connection not allowed");
				}
				// send a confirmation message to the other party (i.e. same
				// USE)
				con.send(m);
				// and use the service
				local.use(con);
			} else {
				throw new Exception("Connection not local");
			}
		} catch (Exception e) {
			// error: send error message
			con.send(new Message(e.toString().getBytes(), getID(), con
					.getIDFrom(), Message.ERROR));
		}
	}

	/**
	 * Registers a service in the MSD
	 * 
	 * @param s
	 *            Service to register. It has to be created by the local cache
	 *            but not joined.
	 * @throws Exception
	 *             If the service can not be registered.
	 */
	public void registerService(Service s, MSDLocalServiceListener l)
			throws Exception {
		if (msdlocalservices == null) {
			msdlocalservices = new Hashtable();
		}
		if (s.getCache() == cache) {
			cache.addElement(s);
			System.out.println("Registering service " + s.getID());
			triggerCacheUpdated(null);
			msdlocalservices.put(s.getID(), l);
		} else {
			throw new Exception("The service can not be registered");
		}
	}

	/**
	 * Deregisters a service registerd with register().
	 * 
	 * @param id
	 *            Identifier of the service to deregister.
	 * @throws Exception
	 *             if the service can not be deregistered
	 */
	public void deregisterService(String id) throws Exception {
		Element e = cache.getElement(cache.getID(), id);
		if (e == null) {
			return;
		}
		deleteElement(e);
		msdlocalservices.remove(id);
		triggerCacheUpdated(null);
	}

	/**
	 * @return An MSDLocalServiceListener to a service registered with the
	 *         register() method.
	 * @see #register
	 */
	private MSDLocalServiceListener localServiceListener(Service s) {
		return (MSDLocalServiceListener) msdlocalservices.get(s.getID());
	}

	/**
	 * Receive a message from a network manager.
	 * 
	 * @return A message as a response, or null if no response is needed
	 */
	public void receive(Message m, NetworkManager net) throws Exception {
		/** End of line */
		/**
		 * Message keeping the response to the received message. Message is null
		 * if not response is needed
		 */
		switch (m.getType()) {
		case Message.MAIN_REPLY:
			try {
				// store the main information
				Service s = cache.createElementFromXML(new String(m.getData()));
				net.setMSDMain(s);
				cache.addElement(s);

				doInitialUpdate();

				this.triggerCacheUpdated(net);
			} catch (Exception e) {
				e.printStackTrace();
			}
			break;
		default:
		}
	}

	/**
	 * Looks for the main MSD in a network.
	 * 
	 * @param name
	 *            The generic name of the network to search for main MSDs.
	 */
	public void searchMainMSD(String name) {
		NetworkManager n = (NetworkManager) nets.get(name);
		if (n == null) {
			throw new NullPointerException("The network is not started");
		}
		doMainRequest(n);
	}

	/**
	 * Triggers a cacheUpdated event to update the rest of the MSD network with
	 * the changes in the local cache.
	 * 
	 * @param net
	 *            Network we want to update. If null, update every network.
	 */
public void triggerCacheUpdated(NetworkManager net){
         Service msdMain=null;
         
         Cache cache2=null; // the cache to send
         try{
	         // Save in v our services
	         Service s=new Service(cache,false);
	         s.setIDCache(cache.getID());
	         // Cache to send: our sevices in the local cache
	         cache2=new Cache(cache.getID());
	         cache2.setElements(cache.getElements(s,cache.getChilds()));
         }catch(Exception ex){
        	 // Do nothing
        	 return;
         }
         
         if(net==null){
        	 // ifnet==null, updates every network
            for(Enumeration e=nets.elements(); e.hasMoreElements();){
            	try{
	            	net=(NetworkManager)e.nextElement();
	            	msdMain=net.getMSDMain();
	            	 // Create an UPDATE connection to the leader
	                Connection con=net.getConnection(Connection.UPDATE,cache.getID(),
	                                                 msdMain.getIDCache(),
	                                                 msdMain.getNetwork(net.getGenericName()).getAddress());
	                // get (and ignore) the UPDATE identifier
	                con.receiveBytes();
	                // send the cache
	                con.sendBytes(cache2.toString().getBytes());
	                con.closeConnection();
            	}catch(Exception ex){
            		System.out.println("Error updating: "+ex.toString());
            	}
            }
         }else{
        	 try{
	        	// else updates the network passed
	        	msdMain=net.getMSDMain();
	        	// Create an UPDATE connection to the leader
	            Connection con=net.getConnection(Connection.UPDATE,cache.getID(),
	                                             msdMain.getIDCache(),
	                                             msdMain.getNetwork(net.getGenericName()).getAddress());
	            // get (and ignore) the UPDATE identifier
	            con.receiveBytes();
	            // send the cache
	            con.sendBytes(cache2.toString().getBytes());
	            con.closeConnection();
        	 }catch(Exception ex){
        		 System.out.println("Error updating: "+ex.toString());
        	 }
        }
        
        triggerEvent(MSDEvent.UPDATED,net==null?null:net.getGenericName());
    }
	/**
	 * Does an initial update asking for every MSD servicein the network to the
	 * main MSD.
	 * 
	 * @param netName
	 *            Generic name of the network to be updated.
	 * @throws Exception
	 *             if the initial update can not be preformed.
	 * @todo This method will do an initial update in every network. Change to
	 *       just a specific network.
	 */
	private void doInitialUpdate() throws Exception {
		Service msds = new Service(cache, false);
		msds.setName("MSD");
		msds.setIDCache("");
		searchService(msds, false);
	}

	/**
	 * This threads send an I_AM_HERE message periodically multicasted in a
	 * network.
	 */
	class IAmHere implements TimeListener {
		private NetworkManager net;

		Message m = new Message(null, getID(), null, Message.I_AM_HERE);

		/**
		 * @param time
		 *            Time in seconds between I_AM_HERE messages.
		 * @param factor
		 *            If any neighbor does not send messages in factor*time
		 *            seconds, inform MSDManager it has left the network. If
		 *            factor<1, never inform.
		 * @param net
		 *            The network to send the IAMHere messeges.
		 */
		public IAmHere(int time, NetworkManager net) {
			this.net = net;
			m.setEncode(false);
			TimeManager.getTimeManager().register(this, time);
		}

		public boolean signal(int type, Object data) {
			try {
				net.sendM(m);
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
			return true;
		}
	}
}
