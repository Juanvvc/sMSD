package org.msd.proxy;

import java.util.Vector;
import java.util.Date;
import org.apache.log4j.Logger; //@@l

/** Send a signal to the listeners after a time.
 * This class try to send a signal to the regiter listeners after a time.
 * This time is not at all accurate: the errors can be very high.
 * @version $Revision: 1.7 $ */
public class TimeManager implements Runnable{
    private final static Logger logger=Logger.getLogger(TimeManager.class); //@@l
    /** Time in seconds between update calls */
    private int time=10;
    /** Reference to the thread running */
    private Thread thread=null;
    /** Reference to the running time manager */
    private static TimeManager timer=new TimeManager();
    /** Vector or listeners */
    private Vector listeners=new Vector();

    /** The constructor is private. No ones can make an instace of this class. */
    private TimeManager(){
        setAccurancy(10);
        start();
    }

    /** @return The TimeManager of the system */
    public static TimeManager getTimeManager(){
        return timer;
    }

    /** @param time Precission time, in seconds. This is the minimum
     * time to signal listeners.
     */
    public void setAccurancy(int t){
        time=t;
        logger.info("Accurancy set to "+t); //@@l
    }

    /** Start signals.
     * If yet started, do nothing. */
    public void start(){
        if(thread==null){
            thread=new Thread(this,"TimeManager");
            thread.start();
            logger.info("TimeManager started"); //@@l
        }
    }

    /** @return Wether the TimeManager is started */
    public boolean started(){
        return thread!=null;
    }

    /** @return A Vector with the current listeners of this manager. */
    public Vector getListeners(){
        return listeners;
    }

    /** Stop signals.
     * If yet stopped, do nothing. */
    public void stop(){
        // If thread is null it was stopped: leave out
        if(thread==null){
            return;
        }
        // Interrupt thread
        thread.interrupt();
        // Inform to rest of the class that there is not thread
        thread=null;
        logger.info("TimeManager stopped"); //@@l
    }

    /** Perform signaling every 'time' seconds. */
    public void run(){
    	boolean interrupted=false;
        while(thread!=null&&!interrupted){
            try{
                Thread.sleep(1000*time);
                //logger.debug("A new time event");

                synchronized(listeners){
                    Object o[]=listeners.toArray();
                    boolean r[]=new boolean[o.length];
                    for(int i=0;i<o.length;i++){
                        Node n=(Node)listeners.get(i);
                        Date d=new Date();
                        if((d.getTime()-n.date.getTime())>n.time*1000){
                            r[i]=n.listener.signal(n.type,n.data);
                            if(r[i]){
                                n.date=d;
                            }
                        }else{
                            r[i]=true;
                        }
                    }
                    // remove no looping listeners
                    for(int i=r.length; i>0; i--){
                        if(!r[i-1]){
                            listeners.remove(i-1);
                        }
                    }
                }
            } catch(InterruptedException e){
                logger.info("interrupted"); //@@l
                interrupted=true;
            } catch(Exception e){
                logger.warn("Exception while waiting: "+e); //@@l
                interrupted=false;
            }
        }
    }

    /** Register a signal.
     * @param l Listener to be informed
     * @param t Time in seconds.
     * @param d Data to inform
     * @param type Type of the signal
     */
    public void register(TimeListener l,int t,int type,Object d){
        Node n=new Node();
        n.data=d;
        n.time=t;
        n.listener=l;
        n.date=new Date();
        n.type=type;
        synchronized(listeners){
            listeners.add(n);
        }
        logger.debug("Signal registered: "+type+", "+l); //@@l
    }

    /** This method is equivalent to register(l,t,0,null)
     * @param l The listener
     * @param t Time in seconds
     */
    public void register(TimeListener l,int t){
        register(l,t,0,null);
    }

    /** Finish this class */
    public void finalize()  throws Throwable{
        stop();
         super.finalize();
    }

    /** Class to store the information from a listener */
    private class Node{
        TimeListener listener;
        int time,type;
        Object data;
        Date date;
    }
}
