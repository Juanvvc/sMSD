/* Cache in charge of storing the found services.
 *
 * This is the mini version */

package org.msd.cache;

import java.io.OutputStream;
import java.util.Vector;
import java.util.Enumeration;
import org.msd.cache.xml.*;

/**
 * This class is in charge of storing the services the system has found through
 * proxies or internal cache mechanism. In the system can be several caches:
 * one by proxy, one more to export in secure mode and one for insecure...
 * But every Browser has a global cache which includes the others. So the
 * structure of caches in the system is ierarchical. A shared cache for
 * everything is also possible.
 *
 * @version $Revision: 1.9 $ */
public class Cache{
    /** Cache's identifier */
    private String idCache;
    /** vector storing the childs of the cache */
    private Vector childs;
    /** The seed identifier */
    private long idNext=0;

    /** Name of the cache for XML */
    public static final String CACHE_NAME="cache";
    /** Name of 'identifier' attribute */
    public static final String ID_NAME="id";
    /** Name of 'attribute' nodes */
    public static final String ATTR_NAME="attr";
    /** Name of 'name' attribute */
    public static final String NAME_NAME="name";
    /** Name of 'identifier of original cache' attribute. */
    public static final String IDCACHE_NAME="idcache";
    /** Name of 'type of node' attribute.
     * The type of a node is stablish with this attribute, not the name of
     * the node */
    public static final String TYPE_NAME="type";
    /** Name of 'url' attribute. */
    public static final String URL_NAME="url";
    /** Name of 'mode' attribute.
     * The meaning of this attribute depends of the mode. Read the DTD. */
    public static final String MODE_NAME="mode";
    
    /** The xml parser to be used in the cache */
    private static XMLParser parser=null;
    static{
        // set the mini parser as the default one.
        setXMLParser(new MiniXMLParser());
    }

    /** Construct a new cache.
     * The identifier is set to '' (empty string).
     * @throws java.lang.Exception If any error occurs */
    public Cache() throws Exception{
        this("");
    }

    /** Construct a cache with an identifier.
     * If identifier is a number, use it as the seed for the identifiers
     * of the elements of this cache.
     * @param id Identifier of the cache
     * @throws Exception An exception if the cache wasn't able to start. */
    public Cache(String id) throws Exception{
        idCache=id;
        idNext=(long)0;
        childs=new Vector();
    }

    /** @return Vector of elements that descend from this cache. */
    public Vector getDescendants(){
        Vector d=new Vector();
        for(Enumeration en=getChilds().elements();en.hasMoreElements();){
            Element e=(Element)en.nextElement();
            d.addElement(e);
            for(Enumeration en2=e.getChilds().elements();en2.hasMoreElements();){
                Element e2=(Element)en2.nextElement();
                d.addElement(e2);
            }
        }
        return d;
    }

    /** @param idcache The idenfier of the original cache of the element
     * @param id The identifier of the element in the cache
     * @return The element idenfied by (idcache-id) or null */
    public Element getElement(String idcache,String id){
        for(int i=0; i<childs.size(); i++){
            try {
                Element e=(Element)childs.elementAt(i);
                if(e.getIDCache().equals(idcache)&&e.getID().equals(id)){
                    return e;
                }
            }catch(Exception ex){
            }
        }
        return null;
    }

    /** @param template The template to look for in the cache
     * @return A collection with the elements in the cache following this template */
    //    public Vector<Element> getElements(Element template){ //@@1.5
    public Vector getElements(Element template){ //@@1.4
        return getElements(template,getChilds());
    }

    /** Return a collection of elements with the same structur
     * of the element template. This method use Element.match for matching.
     * @param template Template to use.
     * @param col Vector of elements to try matching. The normal usage
     * is getElements(template,getChilds()): it will search in the childs of
     * the cache, i.e., services. You can also chain this method and
     * getElementNot.
     * @return A collection of elements matching the template.
     */
    public Vector getElements(Element template,Vector col){
        Vector salida=new Vector();
        for(int i=0; i<col.size(); i++){
            Element e=(Element)col.elementAt(i);
            if(e.match(template)){
                salida.addElement(e);
            }
        }
        return salida;
    }

    /** @param template Template to NOT look for in the cache.
     * @return A collection with the elements not matching the template.
     */
    public Vector getElementsNot(Element template){
        return getElementsNot(template,getChilds());
    }

    /** @return A collection of elements with NOT the same structure
     * of the element template. This method use Element.match for matching.
     * @param template Template to use.
     * @param col Vector of elements to try matching.
     */
    public Vector getElementsNot(Element template,Vector col){
        Vector salida=new Vector();
        for(int i=0; i<col.size(); i++){
            Element e=(Element)col.elementAt(i);
            if(!e.match(template)){
                salida.addElement(e);
            }
        }
        return salida;
    }

    /** @return A new unique identifier for elements. */
    protected synchronized String getNewID(){
        return ""+idNext++;
    }

    /** Store the cache in a stream. The cache is stored in XML format.
     * @param out The output stream to store the cache.
     * @throws java.lang.Exception If any error occurs */
    public void save(OutputStream out) throws Exception{
        out.write(toString().getBytes());
    }

    /** Load a cache from the string.
     * This creates a new cache from a stream. The stream must contain
     * a well defined complete cache. In other case the cache will be
     * corrupted. The identifier of the cache is loaded
     * from source. This method equals reset(); uptade(xml);
     * @param xml The XML to read the cache from.
     * @throws java.lang.Exception If any error occurs */
    public void load(String xml) throws Exception{
        parser.load(this,xml);
    }

    /** Join a cache in a string with this.
     * The stream should contains a properly defined cache. If not,
     * this cache can be corrupted.
     * @param xml The XML to read the cache from.
     * @throws java.lang.Exception If any error occurs */
    public void update(String xml) throws Exception{
        parser.update(this,xml);
    }
    
    /** Join  the elements of a cache to this one.
     * @param c The cache to be joined */
    public void join(Cache c){
        Vector cc=c.getChilds();
        for(int i=0; i<cc.size(); i++){
            addElement((Element)cc.elementAt(i));
        }
    }
    
    /** Creates an element from its XML.
     * Only works with Services, and only gets its name, iddentifiers and network information.
     * @param xml The XML to read the service from.
     * @throws Exception If the xml is not readable or not a service.
     * @return The Service read from the string. */    
    public Service createElementFromXML(String xml) throws Exception{
        return (Service)parser.createElementFromXML(this, xml);
    }

    /** @param newID Delete elements from or gatewayed by this cache identifier */
    public void deleteElementsFromCache(String newID){
        throw new RuntimeException("This method is not implemented");
    }

    /** @param e Removes element from the cache. If null, do nothing. */
    public void deleteElement(Element e){
        childs.removeElement(e);
    }

    /** @param elements New collection of childs of this cache. Does a reset
     *  before adding the new childs. */
    public void setElements(Vector elements){
        for(int i=0; i<elements.size(); i++){
            Element e=(Element)elements.elementAt(i);
            // We join a clon of the element, not the original one.
            addElement((Element)e.clone(this));
        }
    }

    /** Remove every child from this cache. */
    public synchronized void reset(){
        childs=new Vector();
    }

    /** @param e Add this element to the cache. If the element has nor idcache
     * or id defined, set a new one identifiers. If the element was not created
     * with this cache, makes a clone before joining. If the elements was
     * yet joined, remove it. */
    public void addElement(Element e){
        Element e2=e;
        if(e2.getID()==null||e2.getIDCache()==null){
            e2.setIDCache(getID());
            e2.setID(getNewID());
        } else{
            deleteElement(getElement(e.getIDCache(),e.getID()));
        }
        // add the child to our structure...
        childs.addElement(e2);

        childAppended(e2);
    }

    /** @param child Say to the cache a new child has been appended.
     * addElement() calls to this method. Call again here
     * if after adding an element to the cache you add
     * more childs to this element.*/
    public void childAppended(Element child){
        child.setJoined(true);
        // ...and its childs
        Vector childs=child.getChilds();
        for(int i=0;i<childs.size();i++){
            childAppended((Element)childs.elementAt(i));
        }
    }

    /** @return A collection with the elements of the cache */
    public Vector getChilds(){ //@@1.4
        return childs; //@@1.4
    } //@@1.4

    /** @return The cache identifier */
    public String getID(){
        return idCache;
    }

    /** @param id The new identifier of the cache.
     * This method also reset the old cache identifier of the elements created
     * by this cache.
     */
    public final void setID(String id){
        String oldID=this.idCache;
        if(oldID!=null&&oldID.length()>0){
            Vector childs=getChilds();
            for(int i=0; i<childs.size(); i++){
                Element e=(Element)childs.elementAt(i);
                if(e.getIDCache().equals(oldID)){
                    e.setIDCache(id);
                }
            }
        }
        idCache=id;
    }

    /** @return String with the XML code */
    public String toString(){
        return parser.toString(this);
    }
    
    /** @param xmlParser The XML parser to be used. */
    public static void setXMLParser(XMLParser xmlParser){
        parser=xmlParser;
    }
    
    /** @return The XMLPArser to be used */
    public static XMLParser getXMLParser(){
        return parser;
    }
}
