package org.msd.comm;

/** Represent an address in a network.
 * At IP networks, an address is the IP and the Port of the service.
 * At bluetooth networks, the address is just an URL (it includes Bluetooth
 * Address and channel).
 * @version $Revision: 1.3 $ */
public class Address{
    private String url=null;
    private int port=-1;
    
    /** Constructor.
     * @param url URL of the address.
     * @param port Port of the address. Set to -1 if it is useless */
    public Address(String url, int port){
        this.url=url;
        this.port=port;
    }    

    public String getURL(){
        return url;
    }

    public int getPort(){
        return port;
    }

    public String toString(){
        return url+":"+port;
    }

    public void fromString(String s){
        int j=-1, i=-1;
        // get the last index of ":". Notice we can not use lastIndexOf
        // method since it is not included in MIDP2.0
        while((i=s.indexOf(":",i+1))>-1){
            j=i;
        }
        url=s.substring(0,j);
        port=Integer.parseInt(s.substring(j+1));
    }
}
