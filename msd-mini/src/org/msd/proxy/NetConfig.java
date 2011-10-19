
package org.msd.proxy;

import org.msd.comm.Address;

/** Configuration of a network interface. Use this class to easily configure
 * a network for an MSDManager.
 *
 * If the address of the MSD is supplied, the MSDManager will use this address
 * and does not try to find a main MSD. This is useful in networks with a well
 * know MSD, or in clients using a complete and local MSD as a daemon (thus
 * the "address of the main MSD" will be a local address).
 *
 * If the multicast address is null, no multicast communication will be
 * available, and no security will be provided. This is useful if
 * the address of the main MSD is supplied and it is not expected to change,
 * specially if it is a local daemon of the system. 
 *
 * @version $Revision: 1.1 $
 */
public class NetConfig {
    /** The generic name of the network */
    private String name;
    /** The multicast address for the network */
    private Address multicastAddress=null;
    /** The address of the main MSD of the network */
    private Address mainMSD=null;
    /** The address of this MSD in the network */
    private Address localAddress=null;
    
    /** Creates a new instance of NetConfig.
     * The address values are the default ones for the network.
     * @param name The genric name of the network */
    public NetConfig(String name) {
        this.name=name;
        defaultValues();
    }
    /** @return The generic name of the network. */
    public String getName(){
        return name;
    }
    /** @return The multicast address of the network. */
    public Address getMulticastAddress(){
        return multicastAddress;
    }
    /** @param value The multicast address of the network */
    public void setMulticastAddress(Address value){
        multicastAddress=value;
    }
    /** @return The local address of this network. If null, use the
     * network default. If the url of the address is null, try to guess using
     * the given port. */
    public Address getLocalAddress(){
        return localAddress;
    }
    /** @param value The local address of the network */
    public void setLocalAddress(Address value){
        localAddress=value;
    }
    /** @return The address of the main MSD of the network. */
    public Address getMSDAddress(){
        return mainMSD;
    }
    /** @param value The address of the main MSD of the network */
    public void setMSDAddress(Address value){
        mainMSD=value;
    }
    
    /** Fills the config with the default values for the network.
     * If the name of the network is not "ethernet", "wifi" or
     * "bluetooth", does nothing. */
    private void defaultValues(){
        if(name.equals("ethernet")){
            setMSDAddress(null);
            setLocalAddress(new Address(null,15150));
            setMulticastAddress(new Address("239.255.255.254", 15150));            
        }else if(name.equals("wifi")){
            setMSDAddress(null);
            setLocalAddress(new Address(null, 15151));
            setMulticastAddress(new Address("192.168.1.255",15151));
        }else if(name.equals("bluetooth")){
            setMSDAddress(null);
            setMulticastAddress(null);
            setLocalAddress(new Address("0000111A00001000800000805F9B34FB",-1));
        }
    }
}
