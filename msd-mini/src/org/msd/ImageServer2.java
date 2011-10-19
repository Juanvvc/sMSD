/*
 * ImageServer2.java
 *
 * Created on 19 de mayo de 2005, 19:41
 */

package org.msd;

import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

import javax.microedition.media.*;
import javax.microedition.media.control.*;
import javax.microedition.io.*;
import javax.bluetooth.*;

import java.io.*;

/** Starts an image server on the mobile phone
 *
 * @version $Revision: 1.1 $ $Date: 2005-08-12 17:35:48 $
 */
public class ImageServer2 extends MIDlet implements Runnable, CommandListener{
    private byte[] image=null;
    private StreamConnectionNotifier sn=null;
    
    public void startApp() {
        Form ok=new Form("Message");

        Command exit=new Command("Exit",Command.EXIT,1);
        ok.addCommand(exit);
        ok.setCommandListener(this);
        
        try{
            // take an snapshoot
            Player player=Manager.createPlayer("capture://video");
            player.start();
            player.realize();
            VideoControl vc=(VideoControl)player.getControl("VideoControl");
            vc.initDisplayMode(GUIControl.USE_GUI_PRIMITIVE,null);
            image=vc.getSnapshot(null);
            player.close();
            ok.append(new ImageItem("Image",
                Image.createImage(image,0,image.length),
                Item.LAYOUT_CENTER,"Error"));
            
            // start the server
            LocalDevice local=LocalDevice.getLocalDevice();
            local.setDiscoverable(DiscoveryAgent.GIAC);
            // UUID of the server of images
            String uuid="0000112A00001000800000805F9B34FB";
            // create the Bluetooth server
            sn=(StreamConnectionNotifier)Connector.open(
                "btspp://localhost:"+uuid.toString()+";name=imageserver");
            // start the thread
            Thread thread=new Thread(this);
            thread.start();                    
        }catch(Exception e){
            ok.append(e.toString());
        }
        
        Display.getDisplay(this).setCurrent(ok);
        
    }
    
    public void commandAction(Command c, Displayable d){
        destroyApp(true);
    }
    
    public void pauseApp() {
    }
    
    public void destroyApp(boolean unconditional) {
        notifyDestroyed();
    }
    
    public void run(){
        // while the Bluetooth server is not null
        while(sn!=null){
            StreamConnection conn=null;
            try{
                // get a remote connection
                conn=sn.acceptAndOpen();
                // get streams
                InputStream in=conn.openInputStream();
                OutputStream out=conn.openOutputStream();
                // for ever and ever (when the client close the
                // streams, an exception will be thrown and we will
                // get out this loop)
                while(true){
                    // get the parameter from the client
                    String line=org.msd.comm.CommManager.readLine(in,128);
                    if(line.equals("LIST")){
                        // send the name: Last photo
                        out.write("1\r\nLast photo\r\n".getBytes());
                        out.flush();                        
                    }else{
                        // in any other case, send the last image registered
                        if(image==null){
                            // if no image, send no bytes
                            out.write("0\r\n".getBytes());
                            out.flush();
                        }else{
                            // else, send the image
                            synchronized(image){
                                out.write((""+image.length+"\r\n").getBytes());
                                out.write(image);
                                out.flush();
                            }
                        }
                    }
                }
            }catch(Exception e){
                // after any exception, close silently the connection from
                //the client
                try{
                    conn.close();
                }catch(Exception e2){

                }
                Form f=(Form)Display.getDisplay(this).getCurrent();
                f.deleteAll();
                f.append(e.toString());
            }
        }
        Form f=(Form)Display.getDisplay(this).getCurrent();
        f.deleteAll();
        f.append("Server is null");
    }
}
