package org.msd.cache;

/** Represents a proxy in the cache.
 * @version $Revision: 1.1.1.1 $
 * @date $Date: 2005-02-15 14:59:22 $ */
public class Proxy extends Element {
    public Proxy(Cache c, boolean add){ super(c,add); }
    public String getTypeName(){ return "proxy"; }
    public int getType(){ return PROXY; }
    /** Set the type of this proxy, according to the value of SDManager.getType */
    public void setSDType(int t){
        try{
            setAttrStr("sdtype",""+t);
        } catch(Exception e){
            logger.warn("Error setting the type of a Proxy: "+e.toString());  //@@l
        }
    }
    /** Returns the type of this proxy, according to the value of SDManager.getType.
     * If the type is not defined, returns -1 */
    public int getSDType(){
        try{
            return Integer.valueOf(getAttrStr("sdtype")).intValue();
        } catch(Exception e){
            logger.warn("Type of proxy is not defined");  //@@l
            return -1;
        }
    }
}
