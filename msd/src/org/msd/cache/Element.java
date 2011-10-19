package org.msd.cache;

import java.lang.String;
import java.util.Vector;
import java.util.Collection;
import java.io.*;
import org.apache.log4j.Logger; //@@l
import java.util.Hashtable;

/** This class stores an abstract element information in the cache.
 * It works as an interface to the org.w3c.dom.Element class. You
 * must extend this class to provice concrete elements: services,
 * and classes, and nettworks... but using always to store and retrieve
 * informetion the methods of this class. For example, if a service must
 * save and restore the URL of the server, write two methods like this in
 * Service class (extending Element):
 * <pre>public void setURL(String url){ setAttrStr("url",url); }
 * public String getURL(){ return setAttrStr("url"); }</pre>
 * @version $Revision: 1.13 $
 * @date $Date: 2005-08-24 17:08:50 $ */
public abstract class Element{
    Logger logger=Logger.getLogger(Element.class); //@@l

    /** Identifiers of an element.
     * The extending classes should have a unique identifier listed below. */
    public final static int ELEMENT=0;
    public final static int SERVICE=1;
    public final static int CLASSTYPE=2;
    public final static int NETWORK=3;
    public final static int PROXY=4;
    public final static int USER=5;

    /** Element's name */
    private String name;
    /** Reference's identifier.
    /** Identifier of the cache which creates this element. */
    private String idcache=null;
    /** DOM element actualy storing the information */
    private org.w3c.dom.Element node;
    /** Names of aatributes of this element.
     * The key is the names, refering to the DOM attributes */
//    Hashtable<String,org.w3c.dom.Element> attribs;   //@@1.5
    Hashtable attribs; //@@1.4
    /** Childs of this element. Includes just the first level childs:
     * classes, proxies, networks... */
//    private Collection<Element> childs;    //@@1.5
    private Vector childs; //@@1.4
    /** Reference to the cache that is storing us. */
    private Cache cache;
    /** Wether the element has been joined to the cache. Set by the cache */
    private boolean joined=false;

    /** Constructor.
     * Create the element in the cache, maybe appending inmediately.
     * @param cache Cache in charge of this element.
     * @param add Wether this element should be joined to the cache
     * inmediately. This parameter is provided for helping: not every
     * element must be appended to a cache. For example, a template
     * shouldn't. If the element is not joined to the cache, the idcache
     * and id attributes are not set.
     * @throws NullPointerException If cache==null*/
    public Element(Cache cache,boolean add){
        if(cache==null){
            throw new NullPointerException();
        }
        // set the logging level according to the cache
        logger.setLevel(Cache.LOGLEVEL); //@@l

        // Create the XML element
        node=cache.getDocument().createElement(getTypeName());
        //node.setAttribute(Cache.TYPE_NAME,""+getType());

        // initializes collections
//        childs=new Vector<Element>(); //@@1.5
        childs=new Vector(); //@@1.4
//        attribs=new Hashtable<String,org.w3c.dom.Element>();    //@@1.5
        attribs=new Hashtable(); //@@1.4
        // append yourself to the cache, if you should
        if(add){
            cache.addElement(this);
        }
        // save cache reference
        this.cache=cache;
    }

    /** Empty constructor is not allowed */
    private Element(){}

    /** Returns this element's name */
    public String getName(){
        return name;
    }

    /** Set his element's name.
     * The name is human readable in English. Set
     * names in other lenaguages in the attributes. */
    public void setName(String n){
        name=n;
        node.setAttribute(Cache.NAME_NAME,name);
        logger.debug("Element id="+getID()+" name="+name); //@@l
    }

    /** Returns this element identifier.
     * There is no method to set the identifier: is created during the
     * construction of this elements and not modificable to ensures
     * its unicity.
     * @return The identifier or null if not set */
    public String getID(){
        return node.getAttribute(Cache.ID_NAME);
    }

    /** Set the identifier od this element.
     * This attribute identifies a service in the original cache of the element.
     * @param idcache New identifier of the element
     * @see Cache.getNewID
     */
    public void setID(String id){
        if(id==null||id.length()==0){
            node.removeAttribute(Cache.ID_NAME);
        }else{
            node.setAttribute(Cache.ID_NAME,id);
        }
    }

    /** Set the identifier of the original cache of this element.
     * Inside the construction this identifier is set to the cache
     * passed in parameters. If this element is from an external cache, the
     * identifier will be no correct and shoul be set with this method.
     *
     * If idcache is unsep (null or empty) remove the identifier as well.
     * @param idcache New identifier of the cache */
    public void setIDCache(String idcache){
        this.idcache=idcache;
        if(idcache==null||idcache.length()==0){
            node.removeAttribute(Cache.IDCACHE_NAME);
        } else{
            node.setAttribute(Cache.IDCACHE_NAME,idcache);
        }
        if(idcache==null){
            node.removeAttribute(Cache.ID_NAME);
        }
    }

    /** @return The identifier of the cache this elements comes. */
    public String getIDCache(){
        return idcache;
    }

    /** Set an attribute to a String.
     *
     * Use this methid when a attribute can be represented by a String.
     * You can also use a wrap-around to store Serializble classes: filter
     * the output of writeObject through a BASE64 filter and store the
     * resulting String with this method.
     * @param name Name of the attribute. Case sensitive, use the same
     * name for restoring.
     * @param value Value of the attribute. If null, do not save. */
    public void setAttrStr(String name,String value) throws Exception{
        if(value==null||name==null){
            return;
        }
        if(name==null){
            return;
        }
        // if the attribute was set, delete it
        removeAttr(name);

        // new node for the attribute
        org.w3c.dom.Element a=cache.getDocument().createElement(Cache.ATTR_NAME);
        // set the name
        a.setAttribute(Cache.NAME_NAME,name);
        // set "we are a string"
        //a.setAttribute(Cache.STRING_NAME,"true");
        // set the value
        a.appendChild(cache.getDocument().createTextNode(value));
        // append the attribute to the node
        node.appendChild(a);
        // save the attribute in the attributes collection
        attribs.put(name,a);
    }

    /** Remove an attribute from the attribute list of the element.
     * @param name Name of the attribute to remove. If not found, do nothing. */
    public void removeAttr(String name){
        org.w3c.dom.Element a=(org.w3c.dom.Element)attribs.get(name);
        if(a==null){
            return;
        }
        node.removeChild(a);
        attribs.remove(name);
    }

    /** Returns the value of an attribute as a String.
     * @param name Case-sensitive name of the attribute.
     * @returns Attribute value, or null if not found. */
    public String getAttrStr(String name){
        // take the attribute (DOM element)
        org.w3c.dom.Element a=(org.w3c.dom.Element)attribs.get(name);
        // if not found, return null
        if(a==null){
            return null;
        }
        // if found, return its text
        try{
            return a.getFirstChild().getNodeValue();
        } catch(Exception e){
            logger.warn("Error while reading attrib: "+e.toString()); //@@l
            return null;
        }
        //note we do not look if istring="true". Should we?
    }

    /** Append an element to this one.
     *
     * We do not append the element, but a clone of the element. So
     * the passed element can be appended to several elements. For example,
     * services can share classes, so the same class can be appended to
     * several services.
     *
     * After appendinf, this class call to cache.childAppended(child) */
    public void appendChild(Element child){
        // clone the element
        Element newChild=(Element)child.clone();
        // remove the cache identifier: it is not useful any more
        newChild.setIDCache("");
        // if there was an error, returns
        if(newChild==null){
            return;
        }
        // append to this node
        node.appendChild(newChild.getElementDOM());
        childs.add(newChild);
        // inform to the cache a new child has been appended... if joined
        if(joined){
            cache.childAppended(newChild);
        }
    }

    /** Remove a child from this element. */
    public void deleteChild(Element e) throws Exception{
        node.removeChild(e.getElementDOM());
        childs.remove(e);
        if(joined){
            cache.deleteElement(e);
        }
    }

    /** Returns the node actually having this element */
    public org.w3c.dom.Element getElementDOM(){
        return node;
    }

    /** Returns a collection with the names of the elements of this node. */
//    public Collection<String> getAttribNames() { return attribs.keySet(); }  //@@1.5
    public Collection getAttribNames(){
        return attribs.keySet();
    } //@@1.4


    /** Returns a collection with the childs of this element */
//    public Collection<Element> getChilds(){ return childs; }  //@@1.5
    public Collection getChilds(){
        return childs;
    } //@@1.4

    /** Return the generic name of this element.
     * The name can not contains spaces, colons nor punctuation other than
     * slashes.
     *
     * Examples: service, classtype, network, user... */
    public abstract String getTypeName();

    /** Returns the identifier of an element type.
     * The identifier should be unique for each type.
     * For example, service=0, classtype=1... */
    public abstract int getType();

    /** Returns of the template match this element.
     *
     * Match is defininig as: A template does not match if it is of a
     * different type or has an attribute or child not included in the
     * element, including different names and identifiers.
     *
     * Keep in mind the element can have attributes and childs not listed in
     * the template, and match it. */
    public boolean match(Element template){
        try{
            // same type?
            if(getType()!=template.getType()){
                return false;
            }

            // test every XML attribute... except identifier and idcache if empty
            org.w3c.dom.NamedNodeMap nnm=template.getElementDOM().getAttributes();
            for(int i=0;i<nnm.getLength();i++){
                org.w3c.dom.Node n=nnm.item(i);
                String attrName=n.getNodeName();
                String attrValue=n.getNodeValue();
                if(attrName.equals(Cache.IDCACHE_NAME)||
                   attrName.equals(Cache.ID_NAME)){
                    if(attrValue.length()==0){
                        continue;
                    }
                }
                if(!node.hasAttribute(attrName)){
                    return false;
                }
                if(!node.getAttribute(attrName).equals(attrValue)){
                    return false;
                }
            }

            // test all attributes matching
//            for(String attr: template.getAttribNames()){                          //@@1.5
            Object o[]=template.getAttribNames().toArray(); //@@1.4
            for(int i=0; i<o.length; i++){ //@@1.4
                String attr=(String)o[i]; //@@1.4
                String tempAttr=template.getAttrStr(attr);
                String origAttr=getAttrStr(attr);
                boolean same=tempAttr.equals(origAttr);
                if(!same){
                    return false;
                }
            }
            //test all child matching
//            for(Element e: template.getChilds()){                           //@@1.5
            o=template.getChilds().toArray(); //@@1.4
            for(int i=0; i<o.length; i++){ //@@1.4
                Element e=(Element)o[i]; //@@1.4
                boolean anyMatch=false;
//                for(Element ec: childs){                          //@@1.5
                Object o2[]=childs.toArray(); //@@1.4
                for(int j=0; j<o2.length; j++){ //@@1.4
                    Element ec=(Element)o2[j]; //@@1.4
                    if(ec.match(e)){
                        anyMatch=true;
                        break;
                    }
                }

                if(!anyMatch){
                    return false;
                }
            }

            return true;
        } catch(Exception ex){
            // if there was any error, not match
            logger.warn("Error while matching (likely not important): "+ //@@l
                        ex.toString()); //@@l
            return false;
        }
    }

    /** Returns a clon of this element using the same cache */
    public Object clone(){
        return clone(cache);
    }

    /** Returns a clone of this element.
     * A clone has the same attributes including identifiers. */
    public Object clone(Cache cache){
        try{
            return cache.createElementFromDOM(this.node);
        } catch(Exception e){
            logger.warn("Error clonning an object: "+e.toString()); //@@l
            return null;
        }
    }

    /** Returns a XML description of the element. */
    public String toString(){
        try{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            toStream(out);
            return out.toString();
        } catch(Exception e){
            return null;
        }
    }

    /** Save the XML desription of this element in a stream */
    public void toStream(OutputStream out) throws Exception{
        org.msd.cache.XMLTools.saveDOMToStream(node,out);
    }

    /** If the element have been joined to the cache */
    public boolean isJoined(){
        return joined;
    }

    /** Set if the element have been joined to to cache */
    public void setJoined(boolean j){
        joined=j;
    }

    /** @return The cache creating this element */
    public Cache getCache(){
        return cache;
    }
}
