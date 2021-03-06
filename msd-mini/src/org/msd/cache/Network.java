package org.msd.cache;

import org.msd.cache.Element;

/** Represents a network.
 * This class stores the information related to a specific network of a service.
 * For example, URL and Port of the services.
 *
 * @version $Revision: 1.3 $ */
public class Network extends Element{
    public Network(Cache c,boolean add){
        super(c,add);
    }

    public String getTypeName(){
        return "network";
    }

    public int getType(){
        return NETWORK;
    }

    /** Set wether this network is main or not */
    public void setMain(boolean m){
        setSpecialAttr("main",""+m);
    }

    /** Get wether this network is main */
    public boolean isMain(){
        try{
            String m=getSpecialAttr("main");
            if(m.equals("true")){
                return true;
            }else{
                return false;
            }
        } catch(Exception e){
            return false;
        }
    }

    /** @return Return URL of contact of a service.
     * This URL can be of whatever kind: TCP/IP, Bluetooth... */
    public String getURL(){
        return getAttrStr(Cache.URL_NAME);
    }

    /** Set the URL of contact with a service */
    public void setURL(String url) throws Exception{
        setAttrStr("url",url);
    }

    /** Set the port of the service */
    public void setPort(int p) throws Exception{
        setAttrStr("port",""+p);
    }

    /** Returns the port of the service.
     * @return If the port is not defined (as in Bluetooth networks) returns -1 */
    public int getPort(){
        try{
            return Integer.valueOf(getAttrStr("port")).intValue();
        } catch(Exception e){
            return-1;
        }
    }

    /** Returns the address of this network listener */
    public org.msd.comm.Address getAddress(){
        return new org.msd.comm.Address(getURL(),getPort());
    }

    /** Set the address of this network */
    public void setAddress(org.msd.comm.Address address){
        try{
            setURL(address.getURL());
            setPort(address.getPort());
        } catch(Exception e){
        }
    }
}
