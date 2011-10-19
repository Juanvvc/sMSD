/* This is the mini version */
package org.msd.cache;

import java.util.Vector;
import java.io.*;
import java.util.Hashtable;
import java.util.Enumeration;

/** This class stores an abstract element information in the cache.
 * It works as an interface to the org.w3c.dom.Element class. You
 * must extend this class to provice concrete elements: services,
 * and classes, and nettworks... but using always to store and retrieve
 * informetion the methods of this class. For example, if a service must
 * save and restore the URL of the server, write two methods like this in
 * Service class (extending Element):
 * <pre>public void setURL(String url){ setAttrStr("url",url); }
 * public String getURL(){ return setAttrStr("url"); }</pre>
 * @version $Revision: 1.5 $ */
public abstract class Element{
    /** Identifiers of an element.
     * The extending classes should have a unique identifier listed below. */
    public final static int ELEMENT=0;
    public final static int SERVICE=1;
    public final static int CLASSTYPE=2;
    public final static int NETWORK=3;

    /** Element's name */
    private String name;
    private String id;
    /** Reference's identifier.
         /** Identifier of the cache which creates this element. */
     private String idcache=null;
    /** Attributes of the element.
     * The key is the name of the attribute, and the value an String */
    private Hashtable attrs=new Hashtable();
    /** Special attributes.  */
    private Hashtable specialAttrs=new Hashtable();
    /** Childs of this element. Includes just the first level childs:
     * classes, proxies, networks... */
    private Vector childs;
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

        // initializes collections
        childs=new Vector();
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
    }

    /** Returns this element identifier.
     * There is no method to set the identifier: is created during the
     * construction of this elements and not modificable to ensures
     * its unicity.
     * @return The identifier or null if not set */
    public String getID(){
        return id;
    }

    /** Set the identifier od this element.
     * This attribute identifies a service in the original cache of the element.
     * @param id New identifier of the element
     * @see Cache#getNewID
     */
    public void setID(String id){
        this.id=id;
    }

    /** Set the identifier of the original cache of this element.
     * Inside the construction this identifier is set to the cache
     * passed in parameters. If this element is from an external cache, the
     * identifier will be no correct and shoul be set with this method.
     *
     * If idcache is unset (null or empty) remove the identifier as well.
     * @param idcache New identifier of the cache */
    public void setIDCache(String idcache){
        if(idcache==null||idcache.length()==0){
            this.idcache=null;
        }else{
            this.idcache=idcache;
        }
    }

    /** @return The identifier of the cache this elements comes from. */
    public String getIDCache(){
        return idcache;
    }

    /** Set an attribute to a String.
     *
     * Use this method when an attribute can be represented by a String.
     * You can also use a wrap-around to store Serializble classes: filter
     * the output of writeObject through a BASE64 filter and store the
     * resulting String with this method.
     * @param name Name of the attribute. Case sensitive, use the same
     * name for restoring.
     * @param value Value of the attribute. If null, do not save. */
    public void setAttrStr(String name,String value) throws Exception{
        removeAttr(name);
        if(value==null){
            removeAttr(name);
        } else{
            attrs.put(name,value);
        }
    }

    /** Remove an attribute from the attribute list of the element.
     * @param name Name of the attribute to remove. If not found, do nothing. */
    public void removeAttr(String name){
        attrs.remove(name);
    }

    /** Returns the value of an attribute as a String.
     * @param name Case-sensitive name of the attribute.
     * @return Attribute value, or null if not found. */
    public String getAttrStr(String name){
        return(String)attrs.get(name);
    }

    public void setSpecialAttr(String name,String value){
        if(value==null){
            specialAttrs.remove(name);
        } else{
            specialAttrs.put(name,value);
        }
    }

    /** @return An enumeration of the names of the special attributes */
    public Enumeration getSpecialAttrs(){
        return specialAttrs.keys();
    }

    public String getSpecialAttr(String name){
        return(String)specialAttrs.get(name);
    }

    /** Append an element to this one.
     *
     * We do not append the element, but a clone of the element. So
     * the passed element can be appended to several elements. For example,
     * services can share classes, so the same class can be appended to
     * several services.
     *
     * After appendinf, this class call to cache.childAppended(child) */
    public void appendChild(Element newChild){
        // remove the cache identifier: it is not useful any more
        newChild.setIDCache(null);
        // if there was an error, returns
        if(newChild==null){
            return;
        }
        // append to this node
        childs.addElement(newChild);
        // inform to the cache a new child has been appended... if joined
        if(joined){
            cache.childAppended(newChild);
        }
    }

    /** Remove a child from this element. */
    public void deleteChild(Element e) throws Exception{
        childs.removeElement(e);
        if(joined){
            cache.deleteElement(e);
        }
    }

    /** @return A collection with the names of the elements of this node. */
    public Enumeration getAttribNames(){
        return attrs.keys();
    }


    /** @return A collection with the childs of this element */
    public Vector getChilds(){
        return childs;
    }

    /** Return the generic name of this element.
     * The name can not contains spaces, colons nor punctuation other than
     * slashes.
     *
     * Examples: service, classtype, network, user... */
    public abstract String getTypeName();

    /** @return The identifier of an element type.
     * The identifier should be unique for each type.
     * For example, service=0, classtype=1... */
    public abstract int getType();

    /** Returns if the template match this element.
     *
     * Match is defininig as: A template does not match if it is of a
     * different type or has an attribute or child not included in the
     * element, including different names and identifiers.
     *
     * Keep in mind the element can have attributes and childs not listed in
     * the template, and match it. */
    public boolean match(Element template){
        if(template.getType()!=getType()){
            return false;
        }
        if(template.getName()!=null&&!template.getName().equals(name)){
            return false;
        }
        if(template.getIDCache()!=null){
            if(!template.getIDCache().equals(idcache)){
                return false;
            }
        }
        for(Enumeration e=template.getSpecialAttrs();e.hasMoreElements();){
            String k=(String)e.nextElement();
            String v=(String)getSpecialAttr(k);
            if(v==null){
                return false;
            }
            if(!v.equals(template.getSpecialAttr(k))){
                return false;
            }
        }
        for(Enumeration e=template.getAttribNames();e.hasMoreElements();){
            String k=(String)e.nextElement();
            String v=(String)getAttrStr(k);
            if(v==null){
                return false;
            }
            if(!v.equals(template.getAttrStr(k))){
                return false;
            }
        }
        for(Enumeration e=template.getChilds().elements(); e.hasMoreElements();){
            boolean matchAny=false;
            Element te=(Element)e.nextElement();
            for(Enumeration e2=getChilds().elements();e2.hasMoreElements();){
                if(((Element)e2.nextElement()).match(te)){
                    matchAny=true;
                    break;
                }
            }
            if(!matchAny){
                return false;
            }
        }
        return true;
    }

    /** @return A clone of this element using the same cache */
    public Object clone(){
        return clone(cache);
    }

    /** @return A clone of this element.
     * A clone has the same attributes including identifiers. */
    public Object clone(Cache cache){
        try{
            Element e;
            switch(getType()){
                case SERVICE: e=new Service(cache,false); break;
                case NETWORK: e=new Network(cache,false); break;
                case CLASSTYPE: e=new ClassType(cache,false); break;
                default: throw new Exception("Not recognized");
            }
            e.setIDCache(cache.getID());
            e.setID(id);
            e.setName(name);
            for(Enumeration en=attrs.keys(); en.hasMoreElements();){
                String k=(String)en.nextElement();
                e.setAttrStr(k,(String)attrs.get(k));
            }
            for(Enumeration en=specialAttrs.keys(); en.hasMoreElements();){
                String k=(String)en.nextElement();
                e.setSpecialAttr(k,(String)specialAttrs.get(k));
            }
            for(Enumeration en=childs.elements(); en.hasMoreElements();){
                e.appendChild((Element)en.nextElement());
            }
            return e;
        }catch(Exception e){
            return null;
        }
    }

    /** @return An XML description of the element. */
    public String toString(){
        return Cache.getXMLParser().toString(this);
    }

    /** Save the XML desription of this element in a stream */
    public void toStream(OutputStream out) throws Exception{
        out.write(toString().getBytes());
    }

    /** If the element have been joined to the cache */
    public boolean isJoined(){
        return joined;
    }

    /** Set if the element have been joined to to cache */
    public void setJoined(boolean j){
        joined=j;
    }
    
    /** @return The cache that created tis element */
    public Cache getCache(){
        return cache;
    }

}
