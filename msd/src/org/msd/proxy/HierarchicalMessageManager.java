package org.msd.proxy;

import org.msd.comm.*;
import org.msd.cache.*;
import java.util.Collection;

import org.apache.log4j.Logger; //@@l

/** This class implements the hierarchical algorythm to share the cache
 * in the MSD network.
 */
class HierarchicalMessageManager extends MessageManager{
    private static final Logger logger=Logger.getLogger( //@@l
            HierarchicalMessageManager.class); //@@l
    /** The constructor.
     * @param msd The MSDManager to use
     */
    public HierarchicalMessageManager(MSDManager msd){
        super(msd);
    }

    /** Manages a GET connection.
     *
     * The first message contains the template to look in the local
     * cache. If no template is provided, the filtered whole cache
     * will be returned. If the "ask" attribute of the template is
     * set, this MSD will ask for the template to every known main MSD
     * in the network. */
    protected void manageGet(Connection con,NetworkManager net) throws
            Exception{
        int level=getLevel(net);
        if(level!=WAIT_EVENT&&level!=INITIAL_UPDATE&&level!=UPDATE){
            logger.warn("GET: We are not ready: level="+level); //@@l
            return;
        }

        // get the first message from the connection
        Message m=con.receive();

        Element template=null;
        java.util.Vector v=new java.util.Vector();
        if(m.getData()==null||m.getData().length==0){
            // if it is not a main MSD, the main will be pleased to know
            // our internal services
            if(!net.isMain()){
                // send the local services
                v.addAll(msd.getElements());
                // and the description
                v.add(msd.getMSD());
            }

            // search for every main MSD in the internal cache.
            template=(Service)cache.createElementFromXML(
                    "<service name=\"MSD\"><network main=\"\"/></service>");
            v.addAll(cache.getElements(template,cache.getChilds()));
        } else{
            // search for the template in the internal cache
            template=cache.createElementFromXML(new String(m.getData()));
            v.addAll(cache.getElements(template));

            // if this msd is the main MSD of the network...
            if(net.isMain()){
                // if the template has the ask attribute
                if(template.getAttrStr("ask")!=null){
                    // remove the attribute
                    template.removeAttr("ask");
                    // get the main MSDs in the local cache
                    Service mainMsd=(Service)cache.createElementFromXML(
                            "<service name=\"MSD\"><network main=\"\"/></service>");
                    Object[] o=cache.getElements(mainMsd,cache.getChilds()).
                               toArray();
                    // for each known main MSD, ask for the template
                    for(int i=0;i<o.length;i++){
                        Element e=(Element)o[i];
                        // continue if the remote MSD is me or the one connecting to me
                        if(e.getIDCache().equals(msd.getID())||
                           e.getIDCache().equals(con.getIDFrom())){
                            continue;
                        }
                        // get the elements matching the template and put them in
                        // the vector v
                        if(e.getType()==Element.SERVICE){
                            try{
                                // get the services from the remote MSD
                                Connection c=msd.getConnection(Connection.GET,
                                        e.getIDCache());
                                c.sendBytes(template.toString().getBytes());
                                Cache cc=validateCache(c.receive());
                                transformInCache(cc,null);
                                v.addAll(cc.getChilds());
                            } catch(Exception ex){
                                logger.debug("Error while getting cache: "+ex); //@@l
                            }
                        }
                    }
                }
            }
        }

        // construct the cache
        Cache sendCache=new Cache(msd.getID());
        sendCache.setElements(v);
        transformOutCache(sendCache,net,con.getIDFrom());

        // send back the cache
        con.sendBytes(sendCache.toString().getBytes());

        // close the connection
        con.close();
    }

    /** Does a browsing.
     * If this MSD is main of the network, searches inside the local cache.
     * If it is client, does a GET connection to the main MSD of the network.
     * @param template Template to browse.
     * @param net Network to browse to for services.
     * @return A collection with the elements matching template,
     * or null if an error ocurred.
     */
    public Collection doBrowse(Element template,NetworkManager net){
        if(getLevel(net)!=WAIT_EVENT){
            logger.warn("DO_BROWSE: We are not ready: level="+getLevel(net)); //@@l
            return null;
        }

        setLevel(BROWSE,net);
        logger.info("BROWSE level"); //@@l
        Collection c=null;
        if(net.isMain()){
            try{
                c=cache.getElements(template);
            } catch(Exception e){
                logger.error("Error while browsing: "+e); //@@l
                e.printStackTrace();
            }
        } else{
            try{
                Connection con=msd.getConnection(Connection.GET,net.getMSDMain());
                template.setAttrStr("ask","");
                Message m=new Message(template.toString().getBytes(),msd.getID(),
                                      net.getMSDMain(),Connection.GET);
                con.send(m);
                m=con.receive();
                con.close();
                Cache cc=validateCache(m);
                c=cc.getChilds();
            } catch(Exception e){
                logger.error("Error while browsing: "+e); //@@l
                e.printStackTrace();
            }
        }
        setLevel(WAIT_EVENT,net);
        return c;
    }

    /** Manages an UPDATE message. When an MSD receives this message must send a
     *  GET message, because the transmitter's cache has been changed.
     * @param m The received message.
     * @param net The network this message comes from.
     * @throws java.lang.Exception If something goes wrong. */
    protected synchronized void manageUpdate(Message m,NetworkManager net) throws
            Exception{
        // Ignore the message if we are not main or bridges of the network.
        if(net.isMain()||msd.getNetworksBridged(net.getGenericName()).size()!=0){
            super.manageUpdate(m,net);
        }else{
            logger.debug("Ignoring the UPDATE message"); //@@l
        }
    }
}
