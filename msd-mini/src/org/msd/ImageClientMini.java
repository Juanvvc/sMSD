package org.msd;

import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.media.*;
import javax.microedition.media.control.*;


import org.msd.proxy.*;
import org.msd.cache.*;
import java.util.Vector;
import java.util.Hashtable;
import java.io.OutputStream;
import org.msd.comm.ConnectionStreams;
import org.msd.comm.NetworkManager;
import java.io.InputStream;

import javax.bluetooth.*;
import javax.microedition.io.*;

/** This Midlet starts a mini MSD, look for image servers or local camera, show
 * an image and let the user print it. It also register an image server,
 * publishing the last photo taken with the local camera.
 *
 * This MIDlet needs MIDP2.0, CLDC1.0, JSR82 (Bluetooth) and JSR64 (MMA)
 *
 * @version $Revision: 1.8 $
 */
public class ImageClientMini extends MIDlet 
        implements CommandListener,MSDListener,TimeListener {
    /** The display of the MIDlet */
    private Display display;
    /** The starting form */
    private Form start;
    /** The form asking for the MSD identifier */
    private Form msdid;
    /** A form to show texts */
    public Form texts;
    /** A form to show images */
    private Form image;
    /** The field to write the identifier of the MSD */
    private TextField txtmsdid;
    /** The item to show the image */
    private ImageItem imgimage;
    /** The MSDManager of the system */
    private MSDManager manager;
    /** Command buttons */
    private Command ok, exit, print, back, list;
    /** The bytes of the image showed */
    private byte[] nowShowing=null;
    /** Theproducer for a new image */
    private ImageProducer ip=null;
    /** The local server for images */
//    private ImageServer imageServer=null;
            
    /** Starts the application */
    public void startApp() {
        app=this;
        // get the display
        display=Display.getDisplay(this);

        // common controls
        ok=new Command("Ok",Command.OK,1);
        exit=new Command("Exit",Command.EXIT,1);
        back=new Command("Back",Command.BACK,1);
        print=new Command("Print",Command.SCREEN,2);
        list=new Command("List of servers",Command.SCREEN,3);
        
        // start up form, with a logo
        start=new Form("Start up");
        try{
            ImageItem su_image=new ImageItem("logo:",Image.createImage("/logo.png"),
                ImageItem.LAYOUT_CENTER,"UPC/UBISEC");
            start.append(su_image);
        }catch(Exception e){
            StringItem su_string=new StringItem("logo:","UPC/UBISEC");
            su_string.setLayout(Item.LAYOUT_CENTER); //MIDP2
            start.append(su_string);
        }
        start.addCommand(ok);
        start.setCommandListener(this);
       
        // MSD init
        msdid=new Form("Initialize MSD");
        txtmsdid=new TextField("MSD ID:","mini",10,TextField.ANY);
        txtmsdid.setLayout(Item.LAYOUT_CENTER);
        msdid.append(txtmsdid);
        msdid.addCommand(ok);
        msdid.addCommand(exit);
        msdid.setCommandListener(this);

        // A form to show messages
        texts=new Form("Messages");
        texts.addCommand(ok);
        texts.addCommand(exit);
        texts.setCommandListener(this);
        
        // Image
        image=new Form("Image");
        image.addCommand(print);
        image.addCommand(list);
        image.addCommand(back);
        image.addCommand(exit);
        image.setCommandListener(this);
        
        display.setCurrent(start);
        TimeManager.getTimeManager().start();
        
        try{
//            imageServer=new ImageServer();
//            texts.append("Image server: "+imageServer.getURL());
            texts.append("No local server. ");
        }catch(Exception e){
            showAlert("No local server: "+e.toString(),null);
        }
    }

    /** Pause the application: do nothing */
    public void pauseApp() {
    }
    
    /**
     * Destroy the application: close the MSDManager
     * @param unconditional If the closing is unconditional or can be canceled throwing an Exception
     */
    public void destroyApp(boolean unconditional) {
        try{
            if(ip!=null){
                ip.finish();
            }
            if(manager!=null){
                manager.finish();
            }
        }catch(Throwable e){            
        }
//        if(imageServer!=null){
//            imageServer.finish();
//        }
        notifyDestroyed();
    }
    
    /** Get an action from the displayable object.
     * @param c The command.
     * @param d The displayable object */
    public void commandAction(Command c, Displayable d){
        if(c.getCommandType()==Command.EXIT){
            destroyApp(true);
        }else if(c.getCommandType()==Command.OK){
            if(d==msdid){
                new Tasks(Tasks.JOINING);
            }else if(d==texts){
                new Tasks(Tasks.SEARCH_SERVERS);
            }else if(d==start){
                display.setCurrent(msdid);
            }
        }else if(c==print){
            new Tasks(Tasks.PRINTING);            
        }else if(c==list){
            new Tasks(Tasks.SEARCH_SERVERS);
        }else if(c.getCommandType()==Command.BACK&&d==image){
            new Tasks(Tasks.SHOW_IMAGE);
        }
    }
    
    /** Do a task in other thread, not to block the current one. This is
     * specially important (as well as mandatory) in the thread of events */
    class Tasks extends Thread{
        int task=0;
        static final int JOINING=1;
        static final int SEARCH_SERVERS=2;
        static final int SHOW_IMAGE=3;
        static final int PRINTING=5;
        Tasks(int t){
            task=t;
            start();
        }
        public void run(){
            switch(task){
                case JOINING: doJoining(); break;
                case SEARCH_SERVERS: doSearchServers(); break;
                case SHOW_IMAGE: doShowImage(); break;
                case PRINTING: doPrinting(); break;
            }
        }
    }
    
    /** Show an alert. The next form is the current one.
     * @param alert Message to show.
     * @param next The next displayable. If null, use current */
    private void showAlert(String alert, Displayable next){
        Alert a=new Alert("Warning",alert,null,AlertType.WARNING);
        display.setCurrent(a,next==null?display.getCurrent():next);
    }
    
    /** Search the servers, let the user choose the one wanted and show an
     * image. The MSDManager should be connected. */
    private void doSearchServers(){
        if(manager==null){
            showAlert("Manager is null",null);
            return;
        }
        if(ip!=null){
            ip.finish();
        }
        ip=null;
        nowShowing=null;
        try{
            // look for a list of image servers
            Service ims=new Service(manager.getCache(),false);
            ims.setName("imageserver");
            Vector services=manager.searchService(ims,false);
            // let the user choose the server
            Hashtable h=new Hashtable();
            String[] servicestr=new String[services.size()+1];
            for(int j=0; j<services.size(); j++){
                Service s=(Service)services.elementAt(j);
                String i=s.getID()+"@"+s.getIDCache()+"("+s.getConfidence()+
                        " of "+s.getHops()+")";
                h.put(i,s);
                servicestr[j]=i;
            }
            servicestr[servicestr.length-1]="Camera";
            StringList sl=new StringList(display,"Servers",servicestr);
            String i=sl.getSelected();
            if(i==null){
                // if canceled, exit the application
                destroyApp(true);
            }
            ims=(Service)h.get(i);
            // set the image producer
            if(ims==null){
//                ip=new CameraImageProducer(display,imageServer,manager);
                ip=new CameraImageProducer(display,manager);
            }else{
                ip=new ServerImageProducer(ims,manager,display);
            }
            
            // show an image
            doShowImage();
            
        } catch(Exception ex){
            System.err.println("Error while connecting: "+ex);
            showAlert(ex.toString(),null);
            ex.printStackTrace();
        }
    }
    
    /** Get an image from the producer and show it */
    private void doShowImage(){
        if(ip==null){
            showAlert("The image producer is null!",null);
            return;
        }
        
        image.deleteAll();
        try{
            nowShowing=ip.getImage();
            // if nowShowing==null, the ImageServer is no longer offering
            // images and we should get back the server list.
            if(nowShowing==null){
                ip.finish();
                ip=null;
                new Tasks(Tasks.SEARCH_SERVERS);
            }else{
                ImageItem im=new ImageItem("Image",
                        Image.createImage(nowShowing,0,nowShowing.length),
                        Item.LAYOUT_CENTER,"Error");
                image.append(im);
                display.setCurrent(image);
            }
        }catch(Exception e){
            e.printStackTrace();
            image.deleteAll();
            display.setCurrent(image);
            ip.finish();
            ip=null;
            showAlert(e.toString(),null);
        }
    }
    
    /** Join to the MSD Network. */
    private void doJoining(){
        display.setCurrent(texts);
        texts.append("Joining network...");
        if(manager!=null){
            return;
        }
        try{
            manager=new MSDManagerMini();
            manager.addMSDListener(this);
            manager.init(null,new Cache(txtmsdid.getString()));
            TimeManager.getTimeManager().register(this,30,1,texts);       
        }catch(Exception e){
            System.err.println("Error while joining: "+e);
            e.printStackTrace();
            if(manager!=null){
                manager.finish();
            }
            manager=null;
            texts.append("Error: "+e);
        }
    }
    
    /** Print the image being displayed letting the user choose the service.
     * Actually, this method prints the bytes in nowShowing array */
    private void doPrinting(){
        try{
            // look for a list of image servers
            Service ims=new Service(manager.getCache(),false);
            ims.setName("printer");
            Vector services=manager.searchService(ims,false);
            if(services.size()==0){
                throw new Exception("No printers found");
            }
            // let the user choose the printer
            Hashtable h=new Hashtable();
            String[] servicestr=new String[services.size()];
            for(int j=0; j<services.size(); j++){
                Service s=(Service)services.elementAt(j);
                String i=s.getID()+"@"+s.getIDCache()+"("+s.getConfidence()+
                        " of "+s.getHops()+")";
                h.put(i,s);
                servicestr[j]=i;
            }
            StringList sl=new StringList(display,"Printers",servicestr);
            String i=sl.getSelected();
            if(i==null){
                doShowImage();
            }
            ims=(Service)h.get(i);
            
            // use the printer
            ConnectionStreams cs=manager.useService(ims);
            OutputStream out=cs.getOutputStream();
            for(int j=0; j<nowShowing.length; j+=1024){
                out.write(nowShowing,j,Math.min(1024,nowShowing.length-j));
                out.flush();
            }
            cs.close();
            
            // back to show the image
            display.setCurrent(image);
        }catch(Exception e){
            e.printStackTrace();
            showAlert(e.toString(),null);
        }
    }
    
    /**
     * Get an event from the MSD: in the Mini version there is only one event:
     *     the MAIN was found.
     * @param e The event
     */
    public void event(MSDEvent e){
        String idmain=((NetworkManager)manager.getNetworks().get(e.getNetwork())).getMSDMain().getIDCache();        
        texts.append("Done: Net="+e.getNetwork()+" MSD="+idmain);
    }
    
    /**
     * Get a signal from the time manager.
     * Only one signal is registered: timeout to look for main
     * @param t The type of signal
     * @param o The data of the signal
     * @return If the signal is periodic
     */
    public boolean signal(int t,Object o){
        texts.append("MAIN not found");
        return false;
    }    
        
    public static ImageClientMini app=null;
}

/** A producer for images */
interface ImageProducer{
    /** @return A new image from the producer. If null, no more images are
     * available (or more likely, the user press some type of 'back' button) */
    public byte[] getImage();
    /** Finish the producer */
    public void finish();
}

/** A producer of images wih the internal camera */
class CameraImageProducer implements ImageProducer{
    Display display;
    VideoControl vc=null;
    Player player=null;
//    ImageServer localServer=null;
    OutputStream out=null;
    InputStream in=null;
    /** @param d The display of the MIDLet.
     * @param l The local server of images to publish the last taken photo. If
     * null, do not use the local server. */
//    CameraImageProducer(Display d,ImageServer l,MSDManager msd) throws Exception{
    CameraImageProducer(Display d, MSDManager msd) throws Exception{
        display=d;
//        localServer=l;
        // start the player and control.
        player=Manager.createPlayer("capture://video");
        player.start();
        player.realize();
        vc=(VideoControl)player.getControl("VideoControl");
        vc.initDisplayMode(GUIControl.USE_GUI_PRIMITIVE,null);
        
        // let the user choose a server to publish the images
        // look for a list of image servers
        try{
            Service ims=new Service(msd.getCache(),false);
            ims.setName("imageserver");
            Vector services=msd.searchService(ims,false);
            // let the user choose the server
            Hashtable h=new Hashtable();
            String[] servicestr=new String[services.size()+1];
            for(int j=0; j<services.size(); j++){
                Service s=(Service)services.elementAt(j);
                String i=s.getID()+"@"+s.getIDCache();
                h.put(i,s);
                servicestr[j]=i;
            }
            servicestr[servicestr.length-1]="Do not publish";
            StringList sl=new StringList(display,"Publish in",servicestr);
            String i=sl.getSelected();
            if(i==null){
                throw new Exception("Canceled");
            }
            ConnectionStreams cs=msd.useService((Service)h.get(i));
            out=cs.getOutputStream();
            in=cs.getInputStream();
        }catch(Exception e){
            
        }
    }
    public byte[] getImage(){
        // take an snapshoot from the camera
        // (In Nokia 6600 we can not see the video)
        try{
            byte[] b=vc.getSnapshot(null);
//            if(localServer!=null){
//                localServer.setImage(b);
//            }
            
            if(out!=null){
                out.write(("PUBLISH\r\nPhoto\r\n"+b.length+"\r\n").getBytes());
                for(int i=0; i<b.length; i+=1024){
                    out.write(b,i,Math.min(1024,b.length-i));
                    out.flush();
                    in.read();
                }
            }
            
            return b;
        }catch(Exception e){
            System.err.println("Exception while capturing image: "+e);
            return null;
        }
    }
    public void finish(){
        try{
            player.close();
            if(out!=null){
                out.write("BYE\r\n".getBytes());
                out.flush();
                out.close();
            }
        }catch(Exception e){
            System.err.println("Error while finishing: "+e);
        }
    }

}

/** A remote server producer of images  */
class ServerImageProducer implements ImageProducer{
	private Display display;
	private ConnectionStreams cs;
	ServerImageProducer(Service s,MSDManager m,Display d) throws Exception{
            cs=m.useService(s);
            display=d;
	}
	public byte[] getImage(){
            // get the image list
            String[] images;
            try{
                cs.getOutputStream().write("LIST\r\n".getBytes());
                cs.getOutputStream().flush();
                InputStream in=cs.getInputStream();
                String line=org.msd.comm.CommManager.readLine(in,128);
                int l=Integer.parseInt(line);
                images=new String[l];
                for(int i=0;i<l;i++){
                    images[i]=(org.msd.comm.CommManager.readLine(in,128));
                }                
            } catch(Exception e){
                System.err.println("Error getting images: "+e);
                e.printStackTrace();
                return null;
            }

            // let the user choose the image
            StringList sl=new StringList(display,"Images",images);
            String selected=sl.getSelected();
            if(selected==null){
                return null;
            }

            // get the image
            try{
                System.out.println("Retreiving image: "+selected);
                // write the name of the image selected
                OutputStream out=cs.getOutputStream();
                out.write(selected.getBytes());
                out.write("\r\n".getBytes());
                out.flush();
                // take the length of the image
                String length=org.msd.comm.CommManager.readLine(cs.getInputStream(),
                        128);
                int l=Integer.valueOf(length).intValue();
                // read the bytes
                byte[] b=new byte[l];
                int read=0;
                while(read<l){
                    read+=cs.getInputStream().read(b,read,l-read);
                    out.write('0');
                    out.flush();
                }
                return b;
            } catch(Exception ex){
                System.err.println("Error during retreiving image: "+ex);
                return null;
            }                
	}
        
        public void finish(){
            try{
                cs.getOutputStream().write("BYE\r\n".getBytes());
                cs.getOutputStream().flush();
                cs.close();
            }catch(Exception e){
                System.err.println("Exception while closing streams: "+e);
            }
        }
}

/** Print a list of strings and let the user choose one of them */
class StringList implements CommandListener{
    /** The list */
    private List list=null;
    /** The item selected */
    private String selected=null;
    /** Presents a list to the user. This constructor blocks until the user
     * choose an item or press the cancel button.
     * 
     * @param display The display
     * @param title The title of the list
     * @param strings An array of the strings to choose */
    public StringList(Display display, String title,String[] strings){
        list=new List(title,List.IMPLICIT,strings,null);
        Command select=new Command("Select",Command.ITEM,1);
        Command cancel=new Command("Cancel",Command.BACK,2);
        list.setSelectCommand(select);
        list.addCommand(cancel);
        list.setCommandListener(this);

        display.setCurrent(list);
        synchronized(this){
            try{
                wait();
            }catch(Exception e){
                selected=null;
            }
        }
    }
    /** @return The string choosed by the user. If the cnacel button was
     * pressed, return null. */
    public String getSelected(){
        return selected;
    }

    public void commandAction(Command c,Displayable d){
        if(c.getCommandType()==Command.ITEM){
            selected=list.getString(list.getSelectedIndex());
        }else{
            selected=null;
        }
        synchronized(this){
            notify();
        }
    }
}


