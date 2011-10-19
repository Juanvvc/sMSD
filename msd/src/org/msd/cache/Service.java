package org.msd.cache;

import org.msd.cache.Element;
import java.util.Collection;
import java.util.Vector;

/** Represents a service.
 * @version $Revision: 1.9 $
 * @date $Date: 2005-06-10 10:48:23 $ */
public class Service extends Element{
    /** Construct a service to a cache. */
    public Service(Cache c,boolean add){
        super(c,add);
    }

    public String getTypeName(){
        return "service";
    }

    public int getType(){
        return SERVICE;
    }

    /** @return Return a ClassType collection with classes belonging this service */
    public Collection getClasses(){
        Vector salida=new Vector(); //@@1.4
//        Vector<Element> salida=new Vector<Element>(); //@@1.5
        // Cover all sons looking up for sons with class type
//        for(Element e: getChilds()){                          //@@1.5
        Object o[]=getChilds().toArray(); //@@1.4
        for(int i=0;i<o.length;i++){ //@@1.4
            Element e=(Element)o[i]; //@@1.4
            if(e.getType()==Element.CLASSTYPE){
                salida.add(e);
            }
        }
        return salida;
    }

    /** Add a classes structure to the service is belonging */
    public void addClass(ClassType ct){
        appendChild(ct);
    }

    /** Get the identifier of the MSD gateway-bridge in the network
     * to connect to the service. Is null, or empty, the service is
     * in the same gateway as this network. */
    public void setGateway(String gw){
        if(gw!=null&&gw.length()>0){
            getElementDOM().setAttribute("gw",gw);
        } else{
            getElementDOM().removeAttribute("gw");
        }
    }

    /** Get the gateway to connect with the service. Is no gateway
     * defined, return null. */
    public String getGateway(){
        try{
            String gw=getElementDOM().getAttribute("gw");
            if(gw==null||gw.length()==0){
                gw=null;
            }
            return gw;
        } catch(Exception e){
            return null;
        }
    }

    /** Get the network description of a service.
     * @param name Generic name of the network.
     * @return The network descriptor or null if not found */
    public Network getNetwork(String name){
        Object o[]=getChilds().toArray();
        for(int i=0;i<o.length;i++){
            Element e=(Element)o[i];
            if(e.getType()==Element.NETWORK&&e.getName().equals(name)){
                return(Network)e;
            }
        }
        return null;
    }
    /** Get a collection woth the networks of this service.
     * @return A collection with the networks of this service, even empty */
    public Collection getNetworks(){
        Vector v=new Vector();
        Object o[]=getChilds().toArray();
        for(int i=0; i<o.length; i++){
            Element e=(Element)o[i];
            if(e.getType()==Element.NETWORK){
                v.add(e);
            }
        }
        return v;
    }

    /** Set the number of hops to reach this service.
     * @param h Not-negative number of hops.
     */
    public void setHops(int h){
        if(h<1){
            this.getElementDOM().removeAttribute("hops");
        } else{
            this.getElementDOM().setAttribute("hops",""+h);
        }
    }

    /** @returns The number of hops to reach the service */
    public int getHops(){
        try{
            return Integer.valueOf(getElementDOM().getAttribute("hops")).
                    intValue();
        } catch(Exception e){
            return 0;
        }
    }

    /** @return The confidence level of this service */
    public int getConfidence(){
        try{
            return Integer.valueOf(getElementDOM().getAttribute("confidence")).
                    intValue();
        } catch(Exception e){
            return 0;
        }

    }

    /** @param c The conficende level of this service */
    public void setConfidence(int c){
        if(c<1){
            this.getElementDOM().removeAttribute("confidence");
        } else{
            this.getElementDOM().setAttribute("confidence",""+c);
        }
    }
}
