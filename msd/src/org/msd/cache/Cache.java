/* Cache in charge of storing the found services. */

package org.msd.cache;

import java.io.InputStream;
import java.io.OutputStream;
import org.w3c.dom.*;
import java.util.Collection;
import java.util.Vector;
import javax.xml.parsers.*;
import org.apache.log4j.Level; //@@l
import org.apache.log4j.Logger; //@@l

/**
 * This class is in charge of storing the services the system has found through
 * proxies or internal cache mechanism. In the system can be several caches:
 * one by proxy, one more to export in secure mode and one for insecure...
 * But every Browser has a global cache which includes the others. So the
 * structure of caches in the system is ierarchical. A shared cache for
 * everything is also possible.
 *
 * Inside this document, Element means the one of org.msd.proxy.cache and
 * org.w3c.dom.Element always will be referred with the whole path.
 *
 * @author juanvv
 * @date $Date: 2005-09-27 16:57:09 $
 * @version $Revision: 1.24 $ */
public class Cache{
    private static Logger logger=Logger.getLogger(Cache.class); //@@l
    /** Level of logging inside the Cache. Logging in the cache could be
     * extremely verbose. Set this level independly of the default level
     * of the whole system */
    public final static Level LOGLEVEL=(Level)Level.INFO; //@@l
    /** DOM document storing the cache XML */
    private Document doc;
    /** Root node inside the document. Corresponds to doc.getFirstChild() */
    private org.w3c.dom.Element rootNode;
    /** Identifier of the next element which will be created */
    private long idNext;
    /** Cache's identifier */
    private String idCache;
    /** XML document builder */
    private static DocumentBuilder db=null;
    /** vector storing the childs of the cache */
//    private Vector<Element> childs;   //@@1.5
    private Vector childs; //@@1.4

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

    /** Construct a new cache.
     * The identifier is set to '' (empty string).
     * @throws java.lang.Exception If any error occurs */
    public Cache() throws Exception{
        this("");
    }

    /** Construct a cache with an identifier.
     * If identifier is a number, use it as the seed for the identifiers
     * of the elements of this cache.
     * @param id A not null identifier of the cache
     * @throws Exception An exception if the cache wasn't able to start. */
    public Cache(String id) throws Exception{
        if(id==null){
            throw new NullPointerException("The identifier is null");
        }
        //set the logging level to INFO (DEBUG level inside the Cache
        //are extremely verbose)
        logger.setLevel(LOGLEVEL); //@@l

        // Save the cache identifier
        setID(id);
        // Try to set the seed of the identifiers
        try{
            idNext=Long.valueOf(id).longValue()+1; //+1 para no repetir ID
        } catch(Exception e){
            // If you can't, choose a ramdom one
            idNext=(long)(1000*Math.random());
        }
        logger.debug("ID seed set to: "+idNext); //@@l

        // Create a new XML document builder. This classes could throw exceptions
        if(db==null){
            DocumentBuilderFactory dbf=DocumentBuilderFactory.newInstance();
            db=dbf.newDocumentBuilder();
        }
        // Clean the cache
        reset();

        // set the complete mode
        rootNode.setAttribute(MODE_NAME,"complete");
    }

    /** @return Collection of elements that descend from this cache. */
    //public Collecion<Element> getDescendant(){ //@@1.5
    public Collection getDescendants(){ //@@1.4
        //Collection<Element> d=new Vector<Element>(); //@@1.5
        Collection d=new Vector();
        Object o[]=getChilds().toArray();
        //for(Element e:getChilds()){ //@@1.5
        for(int i=0;i<o.length;i++){ //@@1.4
            Element e=(Element)o[i]; //@@1.4
            d.add(e);
            Object o2[]=e.getChilds().toArray(); //@@1.4
            //for(Element e2:e.getChilds()){ //@@1.5
            for(int j=0;j<o2.length;j++){ //@@1.4
                Element e2=(Element)o2[j]; //@@1.4
                d.add(e2);
            }
        }
        return d;
    }

    /** @param idcache The idenfier of the original cache of the element
     * @param id The identifier of the element in the cache
     * @return The element idenfied by (idcache-id) or null */
    public Element getElement(String idcache,String id){
        Element e=new Service(this,false);
        e.setIDCache(idcache);
        e.setID(id);
        Collection c=getElements(e,getChilds());
        if(c.size()>1){
            logger.warn("There is more than one element with ("+idcache+","+id+ //@@l
                        ")"); //@@l
        }
        if(c.size()==0){
            return null;
        }
        return(Element)c.iterator().next();
    }

    /** @param template The template to look for in the cache
     * @return A collection with the elements in the cache following this template */
    //    public Collection<Element> getElements(Element template){ //@@1.5
    public Collection getElements(Element template){ //@@1.4
        return getElements(template,getDescendants());
    }

    /** Return a collection of elements with the same structur
     * of the element template. This method use Element.match for matching.
     * @param template Template to use.
     * @param col Collection of elements to try matching. The normal usage
     * is getElements(template,getChilds()): it will search in the childs of
     * the cache, i.e., services. You can also chain this method and
     * getElementNot.
     * @return A collection of elements matching the template.
     */
    //    public Collection<Element> getElements(Element template,Collection<Element> col){ //@@1.5
    public Collection getElements(Element template,Collection col){
//        Collection<Element> coinciden=new Vector<Element>();  //@@1.5
        Collection coinciden=new Vector(); //@@1.4
        Object o[]=col.toArray();
        for(int i=0;i<o.length;i++){
            Element el=(Element)o[i];
            if(el.match(template)){
                coinciden.add(el);
            }
        }
        return coinciden;
    }

    /** @param template Template to NOT look for in the cache.
     * @return A collection with the elements not matching the template.
     */
    //    public Collection<Element> getElements(Element template){ //@@1.5
    public Collection getElementsNot(Element template){ //@@1.4
        return getElementsNot(template,getDescendants());
    }

    /** @return A collection of elements with NOT the same structure
     * of the element template. This method use Element.match for matching.
     * @param template Template to use.
     * @param col Collection of elements to try matching.
     */
    //    public Collection<Element> getElements(Element template,Collection<Element> col){ //@@1.5
    public Collection getElementsNot(Element template,Collection col){
//        Collection<Element> coinciden=new Vector<Element>();  //@@1.5
        Collection coinciden=new Vector(); //@@1.4
        Object o[]=col.toArray();
        for(int i=0;i<o.length;i++){
            Element el=(Element)o[i];
            if(!el.match(template)){
                coinciden.add(el);
            }
        }
        return coinciden;
    }

    /** @return A new unique identifier for elements. */
    protected synchronized String getNewID(){
        return ""+idNext++;
    }

    /** Store the cache in a stream. The cache is stored in XML format.
     * @param out The output stream to store the cache.
     * @throws java.lang.Exception If any error occurs */
    public void save(OutputStream out) throws Exception{
        // We save with XMLTools
        XMLTools.saveDOMToStream(rootNode,out);
    }

    /** Load a cache from the stream.
     * This creates a new cache from a stream. The stream must contain
     * a well defined complete cache. In other case the cache will be
     * corrupted. The identifier of the cache is loaded
     * from source. This method equals reset(); uptade(out);
     * @param in The input stream to read the cache from
     * @throws java.lang.Exception If any error occurs */
    public void load(InputStream in) throws Exception{
        logger.debug("Loading cache from stream"); //@@l
        // reset the cache
        reset();
        org.w3c.dom.Document d=getDocFromStream(in);
        String newID=((org.w3c.dom.Element)d.getFirstChild()).getAttribute(
                IDCACHE_NAME);
        if(newID==null){
            throw new Exception("Document doesn't contain an idcache attribute");
        }
        // update it
        join(d);
        setID(newID);
    }

    /** Join a cache in a stream with this.
     * The stream should contains a properly defined cache. If not,
     * this cache can be corrupted.
     * @param in The input stream to read the cache from
     * @throws java.lang.Exception If any error occurs */
    public void update(InputStream in) throws Exception{
        logger.debug("Loading cache from stream"); //@@l
        // join the new cache
        join(getDocFromStream(in));
    }

    /** @param in A input stream to read
     * @return A DOM object readed from stream
     * @throws java.lang.Exception If any error occrus during reading. */
    private org.w3c.dom.Document getDocFromStream(InputStream in) throws
            Exception{
        // Save the cache in a byte array. It seems the parse method is not able
        // to block for new bytes in caches loaded from networks, so we should
        // use this runaround.
        byte[] newCache=XMLTools.toByteArray(in);
        //logger.debug("Received cache: "+new String(newCache)); //@@l
        // Parse the document
        Document newDoc=db.parse(new java.io.ByteArrayInputStream(newCache));

        // Take info from cache
        org.w3c.dom.Element newRootNode=(org.w3c.dom.Element)newDoc.
                                        getFirstChild();
        if(!newRootNode.getNodeName().equals(Cache.CACHE_NAME)){
            throw new Exception("Doesn't seem a cache at all");
        }
        String newIDCache=newRootNode.getAttribute(Cache.IDCACHE_NAME);
        if(newIDCache==null||newIDCache.length()==0){
            throw new Exception("Doesn't seem a cache: wrong cache id");
        }

        return newDoc;
    }

    /** Equivalente to join(c.getDocument());
     * @param c Join cache c to this one.
     * @throws java.lang.Ex */
    public void join(Cache c){
        try{
            join(c.getDocument());
        } catch(Exception e){
            logger.warn("Error while joining cache: "+e); //@@l
        }
    }

    /** Join a document to this one.
     * If a cache with the same identifier was joined before, and the
     * new cache is mode=complete, removes the services from
     * previously cache. Do nothing if the new cache is a summary.
     * @param newDoc Document representing the cache to be joined
     * @throws Exception If the document does not describe a cache. */
    public void join(org.w3c.dom.Document newDoc) throws Exception{
        // take info from elements
        org.w3c.dom.Element newRootNode=(org.w3c.dom.Element)newDoc.
                                        getFirstChild();
        Vector newChilds=new Vector();
        org.w3c.dom.Element node;
        node=(org.w3c.dom.Element)newRootNode.getFirstChild();

        // take the identifier of the input cache
        String newID=newRootNode.getAttribute(IDCACHE_NAME);
        if(newID==null||newID.length()==0){
            logger.warn("IDCache is empty!"); //@@l
        }
        // take the mode of the input cache
        String mode=newRootNode.getAttribute(MODE_NAME);
        if(mode.length()==0||mode==null){
            mode="complete";
        }
        logger.info("Joining cache "+newID+" mode "+mode); //@@l
        // if mode is 'summary', return
        if(mode.equals("summary")){
            logger.warn("Ignoring the summary: cache not joinable"); //@@l
            return;
        }
        // if mode is 'complete', remove every element of the local cache
        // with idcache==idremotecache
        if(mode==null||mode.equals("complete")){
            deleteElementsFromCache(newID);
        }
        // if the mode is 'update', just join the services
        // (this overwrites the ones previosly defined)

        // join elements one by one
        while(node!=null){
            addElement(createElementFromDOM(node));
            node=(org.w3c.dom.Element)node.getNextSibling();
        }
    }

    /** @param newID Delete elements from or gatewayed by this cache identifier */
    public void deleteElementsFromCache(String newID){
        Service s=new Service(this,false);
        s.setIDCache(newID);
        Object[] o=getElements(s,getChilds()).toArray();
        logger.debug("Deleting "+o.length+" elements out-of-date idcache="+ //@@l
                     newID); //@@l
        for(int i=0;i<o.length;i++){
            deleteElement((Element)o[i]);
        }
        s.setIDCache("");
        s.setGateway(newID);
        o=getElements(s,getChilds()).toArray();
        logger.debug("Deleting "+o.length+" elements out-of-date gateway="+ //@@l
                     newID); //@@l
        for(int i=0;i<o.length;i++){
            deleteElement((Element)o[i]);
        }
    }

    /** @param e Removes element from the cache. If null, do nothing. */
    public void deleteElement(Element e){
        if(e==null){
            return;
        }
        logger.debug("Removing element with ID: "+e.getID()); //@@l
        // First remove every child
//        for(Element h:e.getChilds()) deleteElement(h);          //@@1.5
        Object o[]=e.getChilds().toArray(); //@@1.4
        for(int i=0;i<o.length;i++){ //@@1.4
            deleteElement((Element)o[i]); //@@1.4
        } //@@1.4

        // remove from XML structure
        org.w3c.dom.Node delnode=e.getElementDOM();
        Node parent=delnode.getParentNode();
        if(parent!=null){
            parent.removeChild(delnode);
        }
        // remove from childs vector
        childs.remove(e);
    }

    /** Create an element from XML source.
     * @param xml XML code to get the element from. It must not include
     * &lt;cache/&gt; element around. It is a wrap to createElementFromDOM().
     * @remarks The element is NOT automatycally joined to the cache.
     * @return The element created from XML
     * @throws java.lang.Exception exception if the string is not well formed XML*/
    public Element createElementFromXML(String xml) throws Exception{
        Document doc2=db.parse(new java.io.ByteArrayInputStream(xml.getBytes()));
        if(doc2==null||doc2.getFirstChild()==null){
            throw new Exception("XML not valid: "+xml);
        }
        org.w3c.dom.Element e=(org.w3c.dom.Element)doc2.getFirstChild();
        Element e2=createElementFromDOM(e);
        return e2;
    }

    /** Create an element from a DOM node.
     * @param node DOM node to create our element.
     * @return a new cache element from node
     * @throws java.lang.Exception If the node does not define an element.
     */
    public Element createElementFromDOM(org.w3c.dom.Element node) throws
            Exception{

        // Create the elemente from its type or name.
        int tipo=Element.ELEMENT;
        try{
            // take the type of element
            tipo=Integer.valueOf(node.getAttribute(Cache.TYPE_NAME)).intValue();
        } catch(Exception e){
            // we can not guess the type: try the name. It is not recommended, but...
            String name=node.getNodeName();
            if(name.equals("classtype")){
                tipo=Element.CLASSTYPE;
            } else if(name.equals("service")){
                tipo=Element.SERVICE;
            } else if(name.equals("proxy")){
                tipo=Element.PROXY;
            } else if(name.equals("user")){
                tipo=Element.USER;
            } else if(name.equals("network")){
                tipo=Element.NETWORK;
            }
        }
        Element elem;
        switch(tipo){
        case Element.NETWORK:
            elem=new Network(this,false);
            break;
        case Element.PROXY:
            elem=new Proxy(this,false);
            break;
        case Element.SERVICE:
            elem=new Service(this,false);
            break;
        case Element.CLASSTYPE:
            elem=new ClassType(this,false);
            break;
        case Element.USER:
            elem=new User(this,false);
            break;
        default:
            throw new Exception("Unknown type element: "+tipo+" ("+
                                node.getNodeName()+")");
        }
        // Here we have a new element in elem, or an exception was thrown.

        // copy attributes to the node (including id)
        NamedNodeMap nnm=node.getAttributes();
        org.w3c.dom.Element node2=elem.getElementDOM();
        for(int i=0;i<nnm.getLength();i++){
            Node n=nnm.item(i);
            node2.setAttribute(n.getNodeName(),n.getNodeValue());
        }
        // if element defines an idcache, set it.
        String idCache=node2.getAttribute(Cache.IDCACHE_NAME);
        if(idCache!=null&&idCache.length()>0){
            elem.setIDCache(idCache);
        }
        // if element defines a name, set it
        String name=node2.getAttribute(Cache.NAME_NAME);
        if(name!=null&&name.length()>0){
            elem.setName(name);
        }

        // take the rest of childs
        org.w3c.dom.Element hijo=(org.w3c.dom.Element)node.getFirstChild();
        while(hijo!=null){
            if(hijo.getNodeName().equals(Cache.ATTR_NAME)){
                Node text=hijo.getFirstChild();
                String content="";
                if(text!=null){
                    content=text.getNodeValue();
                }
                elem.setAttrStr(hijo.getAttribute(Cache.NAME_NAME),content);
            } else{
                elem.appendChild(createElementFromDOM(hijo));
            }
            hijo=(org.w3c.dom.Element)hijo.getNextSibling();
        }

        return elem;
    }

    /** @param elements New collection of childs of this cache. Does a reset
     *  before adding the new childs. */
//    public void setElements(Collection<Element> elements){    //@@1.5
    public void setElements(Collection elements){ //@@1.4
        reset();
        Object o[]=elements.toArray();
        for(int i=0;i<o.length;i++){
            Element e=(Element)o[i];
            // We join a clon of the element, not the original one.
            addElement((Element)e.clone(this));
        }
    }

    /** Remove every child from this cache. */
    public synchronized void reset(){
        logger.debug("Reset on cache"); //@@l
        // Create a new Xml document
        doc=db.newDocument();
        // Create a new root node
        rootNode=doc.createElement(CACHE_NAME);
        rootNode.setAttribute(IDCACHE_NAME,idCache);
//        rootNode.setIdAttribute("id",true); //@@1.5
        doc.appendChild(rootNode);
        // decimos que no tenemos hijos
        childs=new Vector(); //@@1.4
//	childs=new Vector<Element>();                     //@@1.5
//	elementsByIDs=new Hashtable<String,Element>();    //@@1.5
        // We will continue use the old seed for new identifiers
    }

    /** @param e Add this element to the cache. If the element has nor idcache
     * or id defined, set a new one identifiers. If the element was not created
     * with this cache, makes a clone before joining. If the elements was
     * yet joined, remove it. */
    public void addElement(Element e){
        logger.debug("Adding element: "+e); //@@l
        Element e2=e;
        if(e.getElementDOM().getOwnerDocument()!=getDocument()){
            logger.debug("Cloning element before joining"); //@@l
            e2=(Element)e.clone(this);
        }
        if(e2.getID()==null||e2.getIDCache()==null){
            e2.setIDCache(getID());
            e2.setID(getNewID());
        }else{
            deleteElement(getElement(e.getIDCache(),e.getID()));
        }
        // add the child to our structure...
        childs.add(e2);
        // ... and XML's
        rootNode.appendChild(e2.getElementDOM());

        childAppended(e2);
    }

    /** @param child Say to the cache a new child has been appended.
     * addElement() calls to this method. Call again here
     * if after adding an element to the cache you add
     * more childs to this element.*/
    public void childAppended(Element child){
        child.setJoined(true);
        // ...and its childs
        Object o[]=child.getChilds().toArray();
        for(int i=0;i<o.length;i++){
            childAppended((Element)o[i]);
        }
    }

    /** @return The document storing the cache. */
    public org.w3c.dom.Document getDocument(){
        return doc;
    }

    /** @return A collection with the elements of the cache */
//    public Collection<Element> getChilds(){ return childs; } //@@1.5
    public Collection getChilds(){ //@@1.4
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
    public void setID(String id){
        logger.info("Setting idcache="+id); //@@l
        String oldID=this.idCache;
        if(oldID!=null&&oldID.length()>0){
            synchronized(idCache){
                Object o[]=getChilds().toArray();
                for(int i=0;i<o.length;i++){
                    Element e=(Element)o[i];
                    if(e.getIDCache().equals(oldID)){
                        e.setIDCache(id);
                    }
                }
            }
        }
        idCache=id;
        if(rootNode!=null){
            rootNode.setAttribute(IDCACHE_NAME,idCache);
        }
    }

    /** @return String with the XML code */
    public String toString(){
        try{
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
            toStream(out);
            return out.toString();
        } catch(Exception e){
            logger.warn("Unwritable cache: "+e); //@@l
            e.printStackTrace(); //@@l
            return null;
        }
    }

    /** @param out Save the cache in this stream.
     * @throws java.lang.Exception If any error occurs */
    public void toStream(OutputStream out) throws Exception{
        org.msd.cache.XMLTools.saveDOMToStream(doc,out);
    }

    /** @return A new cache exactly the same that this one */
    public Object clone(){
        try{
            Cache c=new Cache(idCache);
            c.setElements(childs);
            return c;
        }catch(Exception e){
            return null;
        }
    }
}
