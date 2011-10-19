package org.msd.comm;

/** Represent an address in a network.
 * At IP networks, an address is the IP and the Port of the service.
 * At bluetooth networks, the address is just an URL (it includes Bluetooth
 * Address and channel).
 * @version $Revision: 1.3 $ */
public class Address{
    private String url=null;
    private int port=-1;
    private String genericName=null;
    /** Constructor.
     * @param url URL of the address.
     * @param port Port of the address. Set to -1 if useless.
     * @param name Generic name of the network this message comes/go.
     */
    public Address(String url,int port,String name){
        this.url=url;
        this.port=port;
        this.genericName=name;
    }

    public String getURL(){
        return url;
    }

    public int getPort(){
        return port;
    }

    public String getName(){
        return genericName;
    }

    public String toString(){
        return url+":"+port;
    }
}
