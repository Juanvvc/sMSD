/*
 * MSDPrinter2.java
 *
 * Created on 29 de abril de 2005, 18:27
 */

package org.msd;

import org.msd.cache.*;
import org.msd.proxy.*;
import java.util.Vector;
import org.msd.comm.ConnectionStreams;
import java.io.OutputStream;

/** A command line MSDPrinter for testing mini-MSD without little device.
 * @version $Revision: 1.4 $ $Date: 2005-09-02 16:21:14 $
 */
public class MSDPrinter2 implements TimeListener,MSDListener {
    private MSDManager manager;
    private TimeManager time;
    private String text=null;

    public static void main(String args[]){
        if(args.length<2){
            System.err.println("Use: MSDPrinter2 id text");
            System.exit(0);
        }
        new MSDPrinter2(args[0],args[1]);
    }

    /** Creates a new instance of MSDPrinter2 */
    public MSDPrinter2(String id,String text){
        this.text=text;
        doJoining(id);
    }

    private void doJoining(String id){
        try{
            manager=new MSDManagerMini();
            manager.addMSDListener(this);
            manager.init(null,new Cache(id));
            TimeManager.getTimeManager().register(this,20);
        }catch(Exception e){
            e.printStackTrace();
            if(manager!=null){
                manager.finish();
            }
        }
    }

    private void doPrinting(){
        try{
            Cache cache=manager.getCache();
            Service s=new Service(cache,false);
            s.setName("printer");
            Vector v=manager.searchService(s, false);

            if(v.size()==0){
                System.out.println("No printer found");
                manager.finish();
                System.exit(0);
            }
            s=(Service)v.elementAt(0);
            ConnectionStreams cs=manager.useService(s);
            OutputStream out=cs.getOutputStream();
            out.write(text.getBytes());
            out.flush();
            cs.close();

            System.out.println("Printed");
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void event(MSDEvent e){
        if(e.getType()==e.UPDATED){
            Thread t=new Thread(){
                public void run(){
                    doPrinting();
                }
            };
            t.start();
        }
    }

    public boolean signal(int t, Object data){
        if(t==0){
            System.out.println("MSD Main searching timeout");
            manager.finish();
            System.exit(0);
        }
        return false;
    }

}
