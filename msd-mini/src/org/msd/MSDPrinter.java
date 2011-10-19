/*
 *
 * Created on 29 de abril de 2005, 16:44
 */

package org.msd;

import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

import org.msd.proxy.*;
import org.msd.cache.*;
import java.util.Vector;
import java.io.OutputStream;
import org.msd.comm.ConnectionStreams;
import org.msd.comm.NetworkManager;

/** This Midlet starts a mini MSD, look for a string and print it.
 *
 * @version $Revision: 1.8 $
 */
public class MSDPrinter extends MIDlet implements CommandListener,MSDListener,TimeListener {
    private Display display;
    private Form start;
    private Form msdid;
    private Form msd;
    private TextBox text;
    private Form printing;
    private Alert alertMSD;
    
    private TextField txtmsdid;
    
    private MSDManager manager;
    
    private Command ok, exit;
    
    public void startApp() {
        display=Display.getDisplay(this);

        // common controls
        ok=new Command("Ok",Command.OK,1);
        exit=new Command("Exit",Command.EXIT,1);
        
        // start up
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
        start.addCommand(exit);
        start.setCommandListener(this);
       
        // MSD init
        msdid=new Form("Initialize MSD");
        txtmsdid=new TextField("MSD ID:","mini",10,TextField.ANY);
        txtmsdid.setLayout(Item.LAYOUT_CENTER); //MIDP2
        msdid.append(txtmsdid);
        msdid.addCommand(ok);
        msdid.addCommand(exit);
        msdid.setCommandListener(this);

        // Joining
        msd=new Form("Joining");
        msd.addCommand(ok);
        msd.addCommand(exit);
        msd.setCommandListener(this);

        // Text to print
        text=new TextBox("Text:","",100,TextField.ANY);
        text.addCommand(ok);
        text.addCommand(exit);
        text.setCommandListener(this);

        // Printing
        printing=new Form("Printing");
        printing.addCommand(ok);
        printing.addCommand(exit);
        printing.setCommandListener(this);
        
        // Alerts
        alertMSD=new Alert("Error","No MSD found",null,AlertType.ERROR);
        
        display.setCurrent(start);
        TimeManager.getTimeManager().start();
        TimeManager.getTimeManager().register(this,10,1,start);
    }
    
    public void pauseApp() {
    }
    
    public void destroyApp(boolean unconditional) {
        if(manager!=null){
            manager.finish();
        }
        notifyDestroyed();
    }
    
    public void commandAction(Command c, Displayable d){
        if(c.getCommandType()==Command.EXIT){
            destroyApp(true);
        }
        if(c.getCommandType()==Command.OK){
            if(d==msdid){
                display.setCurrent(msd);
                Thread t=new Thread(){
                    public void run(){
                        doJoining();
                    }
                };
                t.start();
            }else if(d==msd){
                display.setCurrent(text);                
            }else if(d==text){
                display.setCurrent(printing);
                Thread t=new Thread(){
                    public void run(){
                        doPrinting();
                    }
                };
                t.start();
            }else if(d==printing){
                display.setCurrent(text);
            }else{
                display.setCurrent(msdid);
            }
        }
    }
    
    private void doJoining(){
        msd.deleteAll();
        msd.append("Joining network...");
        if(manager!=null){
            return;
        }
        try{
            manager=new MSDManagerMini();
            manager.addMSDListener(this);
            manager.init(null,new Cache(txtmsdid.getString()));
            TimeManager.getTimeManager().register(this,30,1,msd);
        }catch(Exception e){
            System.err.println("Error while joining: "+e);
            e.printStackTrace();
            if(manager!=null){
                manager.finish();
            }
            msd.append("Error: "+e);
        }
    }
    
    private void doPrinting(){
        try{
            printing.deleteAll();
            printing.append("Printing...");
            
            Cache cache=manager.getCache();
            Service s=new Service(cache,false);
	    s.setName("printer");
            Vector v=manager.searchService(s, false);

            if(v.size()==0){
                printing.append("No printer");
                return;
            }
            s=(Service)v.elementAt(0);
            ConnectionStreams cs=manager.useService(s);
            OutputStream out=cs.getOutputStream();
            out.write(text.getString().getBytes());
            out.flush();
            cs.close();

            printing.append("Done");
        }catch(Exception e){
            e.printStackTrace();
            printing.append("Error: "+e);
        }
    }
    
    public void event(MSDEvent e){
        String idmain=((NetworkManager)manager.getNetworks().get(e.getNetwork())).getMSDMain().getIDCache();        
        msd.append("Done: Net="+e.getNetwork()+" MSD="+idmain);
    }
    
    public boolean signal(int t,Object o){        
        Displayable current=display.getCurrent();
        if(current==msd&&o==msd){
            manager.finish();
            display.setCurrent(alertMSD,start);
        }else if(current==start&&o==start){
            display.setCurrent(msdid);
        }
        return false;
    }
}
