package org.msd.proxy;

import org.msd.comm.*;
import org.msd.proxy.MSDManagerSimp;
import java.util.ResourceBundle;
import java.io.ByteArrayInputStream;
import org.msd.cache.Cache;
import org.apache.log4j.Logger; //@@l

/** A tester to let easier debugging with a MSDManagerSimp.
 * @version $Revision: 1.5 $
 */
public class MSDTester{
    public static final Logger logger=Logger.getLogger(MSDTester.class); //@@l
    private MSDManagerSimp msdmanager=null;

    public MSDTester(String id){
        ResourceBundle res=ResourceBundle.getBundle(MSDManager.DEFAULT);
        org.apache.log4j.BasicConfigurator.configure(); //@@l

        // Make the cache of the system
        // Unique identifier of this MSD
        Cache cache=null;
        try{
            cache=new Cache(id);
            cache.load(new ByteArrayInputStream(res.getString("CacheXML").getBytes()));
            cache.setID(id);
            new org.msd.BrowserPropertiesGUI(new javax.swing.JFrame(),cache);
            logger.info("Using cache: "+cache); //@@l //@@l
        } catch(Exception e){
            logger.fatal("Cache not created: "+e.toString()); //@@l
            e.printStackTrace(); //@@l
            System.exit(1);
        }
        logger.debug("Using cache: "+cache); //@@l

        // start MSDManager
        try{
            msdmanager=new MSDManagerSimp();
            msdmanager.init("MSD",cache,MSDManager.DEFAULT);
            msdmanager.setTester(this);
        } catch(Exception e){
            logger.error("Error while creating MSD: "+e); //@@l
            e.printStackTrace(); //@@l
            System.exit(1);
        }
    }


    private Connection con=null;
    private Object newCon=new Object();
    public void receive(Connection c){
        synchronized(newCon){
            con=c;
            newCon.notify();
        }
    }

    public Connection getRemoteConnection(){
        synchronized(newCon){
            try{
                newCon.wait();
            }catch(Exception e){
                System.err.println(e.toString());
            }
        }
        return con;
    }

    public void receive(Message m){
        System.out.println("Message from "+m.getIDFrom()+": "+
                           new String(m.getData()));
    }

    public MSDManager getMSDManager(){
        return msdmanager;
    }

    public static void main(String[] args){

        MSDTester msdtester=new MSDTester(args[0]);
    }
}
