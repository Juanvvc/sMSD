package org.msd.cache;

import org.msd.cache.Element;
import java.util.Vector;

/** Represents a service.
 * @version $Revision: 1.5 $ */
public class Service extends Element{
    /** Constructs a service for a cache.
     * @param c The cache of the service
     * @param add Wether this service must be joined to the cache or not. */
    public Service(Cache c,boolean add){
        super(c,add);
    }

    /** @return The name of the type of this element: service */
    public String getTypeName(){
        return "service";
    }

    /** @return The type of this element: Element.SERVICE */
    public int getType(){
        return SERVICE;
    }

    /** @return A Vector of ClassType objects with classes of this service */
    public Vector getClasses(){
        Vector salida=new Vector();
        // Cover all sons looking up for sons with class type
        Vector childs=getChilds();
        for(int i=0; i<childs.size(); i++){
            Element e=(Element)childs.elementAt(i);
            if(e.getType()==Element.CLASSTYPE){
                salida.addElement(e);
            }
        }
        return salida;
    }

    /** Add a classes structure to the service */
    public void addClass(ClassType ct){
        appendChild(ct);
    }

    /** Sets the identifier of the MSD gateway-bridge in the network
     * to connect to the service. Is null or empty, the service is
     * in the same gateway as this network.
     * @param gw The gateway*/
    public void setGateway(String gw){
        setSpecialAttr("gw",gw);
    }

    /** @return The gateway to connect with the service. If no gateway is
     * defined, return null. */
    public String getGateway(){
        return getSpecialAttr("gw");
    }

    /** Gets the description of a network of the service.
     * @param name Generic name of the network.
     * @return The network descriptor or null if not found */
    public Network getNetwork(String name){
        Vector childs=getChilds();
        for(int i=0;i<childs.size(); i++){
            Element e=(Element)childs.elementAt(i);
            if(e.getType()==Element.NETWORK&&e.getName().equals(name)){
                return(Network)e;
            }
        }
        return null;
    }

    /** Gets a vector with the networks of this service.
     * @return A vector with the networks of this service, even empty */
    public Vector getNetworks(){
        Vector v=new Vector();
        Vector childs=getChilds();
        for(int i=0;i<childs.size(); i++){
            Element e=(Element)childs.elementAt(i);
            if(e.getType()==Element.NETWORK){
                v.addElement(e);
            }
        }
        return v;
    }

    /** Sets the number of hops to reach this service.
     * @param h Not-negative number of hops.
     */
    public void setHops(int h){
        setSpecialAttr("hops",""+h);
    }

    /** @return The number of hops to reach the service */
    public int getHops(){
        try{
            return Integer.valueOf(getSpecialAttr("hops")).intValue();
        } catch(Exception e){
            return 0;
        }
    }
    
    /** @return The level of confidence of this service */
    public int getConfidence(){
        try{
            return Integer.parseInt(getSpecialAttr("confidence"));
        }catch(Exception e){
            return 0;
        }
    }
    
    /** @param c The level of conficence of this service */
    public void setConfidence(int c){
        setSpecialAttr("confidence",""+c);
    }
}
