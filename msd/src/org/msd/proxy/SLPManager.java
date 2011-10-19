package org.msd.proxy;

import org.apache.log4j.*; //@@l
import com.solers.slp.*;
import java.util.Locale;
import java.util.Vector;
import java.util.StringTokenizer;
import org.msd.cache.*;

/** Manages the SLP protocol.
 *
 * @author juanvvc
 * @version $Revision: 1.10 $ */
public class SLPManager extends SDManager {
    private static Logger logger=Logger.getLogger(SLPManager.class); //@@l
    /** threads in memory */
    //    private Vector<Thread> threads;   //@@1.5
    private Vector threads; //@@1.4

    /** Empty Constructor.
     * This class needs an empty constructor if it is loaded from
     * Class.newInstace (for example, through a ClassLoader). Anyway, you
     * must avoid using any method before configure the manager with init(). */
    public SLPManager(){
        logger.setLevel(SDManager.LEVEL);
    }

    /* Initializes the manager. Called inside the constructor, you must call
     * this method yourself only if you creates the class other way than
     * the 'new' method (i.e. ClassLoader) */
    public void init(Network net,Cache cache,String resource) throws Exception{
        super.init(net,cache,resource);
        working=true;
        //        threads=new Vector<Thread>();     //@@1.5
        threads=new Vector(); //@@1.4
        startInterface(net.getURL());
    }

    public void reinit(Network net) throws Exception{
        startInterface(net.getURL());
    }

    /** @param interf Start searching in this network interface.
     * The interface is an IP number of a system interface. Useful in systems
     * with several IP interfaces, and we want to perform searching just
     * in one of them.
     * @throws NullPointerException if interf==null.
     */
    public void startInterface(String interf){
        // register the interface to listen
        String interfaces=System.getProperty("net.slp.interfaces");
        if(interfaces==null||interfaces.length()==0){
            interfaces=interf;
        } else{
            interfaces=interfaces+","+interf;
        }
        System.setProperty("net.slp.interfaces",interfaces);
        logger.info("Searching SLP on "+interf); //@@l
    }

    /** @param interf Stop searching on tis network interface. If the interface
     * was not started, do nothing.
     * @see #startInterface
     * @throws NullPointerException if interf==null.
     */
    public void stopInterface(String interf){
        // remove the interface from the list of interfaces
        String ip=network.getURL();
        String interfaces=System.getProperty("net.slp.interfaces");
        String interfaces2="";
        if(interfaces!=null){
            // remove ip from the interfaces list, and colons if exist
            StringTokenizer st=new StringTokenizer(interfaces,",",false);
            while(st.hasMoreTokens()){
                String i=st.nextToken();
                if(!i.equals(ip)){
                    if(interfaces2.length()==0){
                        interfaces2=i;
                    } else{
                        interfaces2+=","+i;
                    }
                }
            }
            System.setProperty("net.slp.interfaces",interfaces2);
        }
    }

    /** @return SLP identifier */
    public int getType(){
        return 1;
    }

    /** Start a searching of services in SLP.
     * @throws java.lang.Exception If the searching can not start.
     * @todo Maybe we should check the Locales parameter? */
    public synchronized void search() throws Exception {
        if(busy){
            logger.warn("Another searching is taking place"); //@@l
            return;
        }
        // start a searching
        searchStarted();
        // use other thread to return inmediatly
        Thread t=new Thread("SLPManager"){
            public void run(){
                try{
                    // Search by type of services
                    Locator locator=ServiceLocationManager.getLocator(Locale.US);
                    Vector scopes=ServiceLocationManager.findScopes();
                    // for each found scope...
                    for(int i=0;i<scopes.size();i++){
                        String scopeName=(String)scopes.elementAt(i);
                        logger.info("Scope found: "+scopeName); //@@l
                        // create a new vector with this scope
                        //                        Vector<String> scopeUnico=new Vector<String>(); //@@1.5
                        Vector scopeUnico=new Vector(); //@@1.4
                        scopeUnico.add(scopeName);
                        // create a new class node for this scope
                        ClassType scope=new ClassType(cache,false);
                        scope.setName(scopeName);
                        scope.setAttrStr("slp-type","scope");
                        // search services just for this scope
                        ServiceLocationEnumeration sle=locator.findServiceTypes(
                                "",scopeUnico);
                        boolean anyService=false;
                        // take every type of service and search for its urls
                        while(sle.hasMoreElements()){
                            // copy the scopeto a new Class element
                            ClassType scope2=(ClassType)scope.clone();

                            anyService=true;
                            ServiceType st=(ServiceType)sle.nextElement();

                            // create the tree structure representing this service
                            ClassType class1=new ClassType(cache,false);
                            class1.setName(st.getPrincipleTypeName());
                            class1.setAttrStr("slp-type","principletype");
                            if(st.getAbstractTypeName()!=null&&
                               !st.getConcreteTypeName().equals("")){
                                ClassType class2=new ClassType(cache,false);
                                class2.setName(st.getConcreteTypeName());
                                class2.setAttrStr("slp-type","concreteclass");
                                class1.appendChild(class2);
                            }
                            scope2.appendChild(class1);

                            // search the urls in other thread
                            threads.add(new URLThread(st,scopes,locator,scope2));
                        }

                        // exit nmediately if no services found
                        if(!anyService){
                            searchCompleted(SDListener.COMPLETED,"");
                        }
                    } // end of scope searching
                } catch(Exception e){
                    // inform we finish withs errors
                    logger.error("Error while searching for service types: "+e); //@@l
                    e.printStackTrace();
                    searchCompleted(SDListener.ERROR,e.toString());
                }
            }
        };
        // start this thread: look for scopes
        t.start();
        // return inmediately
    }

    /** Calles when an URL is found
     * @param urls URLs roll. If null, there was an error.
     * @param t Thread which found the url. We will deregister this thread. */
    private void urlsFound(ServiceLocationEnumeration urls,URLThread t){
        // if there wasn't any error...
        if(urls!=null){
            // create a nre thread for the URL to search for attributes
            while(urls.hasMoreElements()){
                ServiceURL url=(ServiceURL)urls.nextElement();
                logger.info("Service found: "+t.service.getPrincipleTypeName()+ //@@l
                            ":"+url.getHost()); //@@l
                // Join in the cache a new service with the name as the address of the service
                Service serv=new Service(cache,true);
                elements.add(serv);
                serv.setName(t.service.getPrincipleTypeName());
                try{
                    Network n=(Network)network.clone();
                    n.setURL(url.getHost());
                    n.setPort(url.getPort());
                    serv.appendChild(n);
                } catch(Exception e){
                    logger.warn("Error while setting attributes: "+e.toString()); //@@l
                }
                serv.addClass(t.classes);
                // start the attribute searching thread
                threads.add(new AttributeThread(url,t.scopes,t.locator,serv));
                // append this proxy to the service
                serv.appendChild(proxy);
            }
        }
        // delete the thread which found this url of the roll of threads
        threads.remove(t);
        // if the roll is empty, the searching is over.
        if(threads.isEmpty()){
            searchCompleted(SDListener.COMPLETED,"");
        }
    }

    /** Called when attributes where found.
     * @param attributes Roll of attributes found. If null, there were errors.
     * @param t Thread which found the attributes. */
    private void attributesFound(ServiceLocationEnumeration attributes,
                                 AttributeThread t){
        // if there was any error...
        if(attributes!=null){
            // append the attributes to the service node
            while(attributes.hasMoreElements()){
                ServiceLocationAttribute sla=(ServiceLocationAttribute)
                                             attributes.nextElement();
                try{
                    t.service.setAttrStr(sla.getId(),sla.toString());
                } catch(Exception e){
                    logger.warn("Error while setting attributes: "+e.toString()); //@@l
                }
            }
        }
        // removes the thread from the roll
        threads.remove(t);
        // if it was the last, the searcing is over
        if(threads.isEmpty()){
            searchCompleted(SDListener.COMPLETED,"");
        }
    }

    /** Thread for searching for URLs */
    private class URLThread extends Thread{
        /** service type we are searching */
        ServiceType service;
        /** Scopes used */
        Vector scopes;
        /** SLP locator we are using */
        Locator locator;
        /** Classes for this url */
        ClassType classes;
        /** Constructor. Create a thread, run it and return.
         * @param s The type of service
         * @param scopes The vector of scopes
         * @param locator The SLP Locator object
         * @param classes The classes of the service */
        URLThread(ServiceType s,Vector scopes,Locator locator,ClassType classes){
            this.service=s;
            this.scopes=scopes;
            this.locator=locator;
            this.classes=classes;
            start();
        }

        public void run(){
            try{
                ServiceLocationEnumeration sle=locator.findServices(service,
                        scopes,"");
                urlsFound(sle,this);
            } catch(Exception e){
                logger.warn("Error while looking for URLs: "+e.toString()); //@@l
                urlsFound(null,this);
                e.printStackTrace();
            }
        }
    }


    /** Thread for searching for attributes for an URL. */
    private class AttributeThread extends Thread{
        /** URL we were searching for. */
        ServiceURL url;
        /** Scopes we are using */
        Vector scopes;
        /** Ouer SLP locator */
        Locator locator;
        /** Descriptor of the service of this URL */
        Element service;
        /** Constructor. Create a new thread, run it and return.
         * @param url The service url
         * @param scopes The vector of scopes
         * @param locator An SLP locator object
         * @param service The descriptor of the service */
        AttributeThread(ServiceURL url,Vector scopes,Locator locator,
                        Element service){
            this.url=url;
            this.scopes=scopes;
            this.locator=locator;
            this.service=service;
            start();
        }

        public void run(){
            try{
                ServiceLocationEnumeration sle=locator.findAttributes(url,
                        scopes,new Vector());
                attributesFound(sle,this);
            } catch(Exception e){
                logger.warn("Error while looking for services: "+e.toString()); //@@l
                attributesFound(null,this);
            }
        }
    }


    public void finish(){
        if(!working){
            return;
        }
        working=false;
        logger.info("SLPManager finished."); //@@l
        // clean the net.slp.interfaces attribute
        System.setProperty("net.slp.interfaces","");
    }

    public void finish(Network net){
        stopInterface(net.getURL());
        // if there are no more interfaces, finish the manager
        if(System.getProperty("net.slp.interfaces").length()==0){
            finish();
        }
    }
}
