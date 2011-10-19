package org.msd;

import java.awt.Frame;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import org.msd.comm.ConnectionStreams;
import org.msd.proxy.*;
import org.msd.cache.*;

import java.util.Vector;
import java.util.Enumeration;
import java.util.Hashtable;

/** This example of the MSD medium library is a client of an image server.
 * It joins to the MSD network, search for image servers and shows its
 * images. Then it offers the user the possibilit of printing the image
 * with other service.
 */
public class ImageClient extends Frame{
    /** Connection streams to the image server */
    private ConnectionStreams cs=null;
    /** MSDManager object */
    private MSDManagerMedium msd=null;
    /** The bytes of the image currently displayed */
    private byte[] imagebytes=null;
    /** The thread printing an image oor getting the list */
    Thread thread=null;
    /** A pointer to this. Useful in inner classes to the father */
    private ImageClient IAm=this;

    /** Creates a new client */
    public ImageClient(){
        try{
            jbInit();
            pack();
            setTitle("Image client");

            // create the configuration of the MSD
            MSDConfig config=new MSDConfig(this);
            if(config.canceled()){
                System.exit(0);
            }
           
            // initialize the MSDManager
            Cache cache=new Cache(config.getIDCache());
            msd=new MSDManagerMedium();
            msd.init(null,cache);
            NetConfig nc=new NetConfig("wifi");
            nc.setLocalAddress(config.getLocalAddress());
            nc.setMulticastAddress(config.getMulticastAddress());
            msd.initNet(nc);

            setVisible(true);
        } catch(Exception ex){
            ex.printStackTrace();
            System.exit(1);
        }
    }

    /** @param args Ignored */
    public static void main(String[] args){
        ImageClient imageclient=new ImageClient();
        imageclient.setVisible(true);
    }

    /** Init the GUI */
    private void jbInit() throws Exception{
        search.setLabel("Search");
        search.addActionListener(new ImageClient_search_actionAdapter(this));
        print.setLabel("Print");
        print.addActionListener(new ImageClient_print_actionAdapter(this));
        exit.setLabel("Exit");
        exit.addActionListener(new ImageClient_exit_actionAdapter(this));
        images.addActionListener(new ImageClient_images_actionAdapter(this));
        this.add(images,java.awt.BorderLayout.NORTH);
        this.add(buttons,java.awt.BorderLayout.SOUTH);
        buttons.add(search);
        buttons.add(print);
        buttons.add(exit);
        ScrollPane sp=new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
        this.add(image,java.awt.BorderLayout.CENTER);
    }

    /** Get the list of images from a server.
     * @param cs The ConnectionStreams to the server.
     */
    private void connectImageServer(ConnectionStreams cs){
        // if we were connected, close
        try{
            if(this.cs!=null){
                cs.getOutputStream().write("BYE\r\n".getBytes());
                cs.getOutputStream().flush();
                this.cs.close();
            }
        } catch(Exception e){

        }
        this.cs=cs;
        // get the list and show it
        System.out.println("Getting image list");
        images.removeAll();
        try{
            cs.getOutputStream().write("LIST\r\n".getBytes());
            cs.getOutputStream().flush();
            InputStream in=cs.getInputStream();
            String line=org.msd.comm.CommManager.readLine(in,128);
            int l=Integer.parseInt(line);
            for(int i=0;i<l;i++){
                images.add(org.msd.comm.CommManager.readLine(in,128));
            }
        } catch(Exception e){
            System.err.println("Error while creating list: "+e);
            e.printStackTrace();
        }
        pack();
        System.out.println("Prepared");
    }

    List images=new List();
    Panel buttons=new Panel();
    Button search=new Button();
    Button print=new Button();
    Button exit=new Button();
    ImageViewer image=new ImageViewer();

    /** Search for image servers. */
    public void search_actionPerformed(ActionEvent e){
        if(thread!=null&&thread.isAlive()){
             synchronized(thread){
                thread.interrupt();
             }
        }
        thread=new Thread(){
            public void run(){
                try{
                    // look for a list of image servers
                    Service ims=new Service(msd.getCache(),false);
                    ims.setName("imageserver");
                    Vector services=msd.searchService(ims,false);
                    // let the user choose the preferred
                    ServiceList sl=new ServiceList("Image servers",services,IAm);
                    ims=sl.getSelected();
                    setTitle("Image client "+ims.getID()+"@"+ims.getIDCache());
                    // connect to it
                    ConnectionStreams cs=msd.useService(ims);
                    connectImageServer(cs);
                } catch(Exception ex){
                    System.err.println("Error while connecting: "+ex);
                    ex.printStackTrace();
                }
            }
        };
        thread.start();
    }

    /** Print the selected image. */
    public void print_actionPerformed(ActionEvent e){
        if(thread!=null&&thread.isAlive()){
            synchronized(thread){
                thread.interrupt();
            }
        }
        thread=new Thread(){
            public void run(){
                if(imagebytes==null){
                    return;
                }
                try{
                    // look for a list of image servers
                    Service prints=new Service(msd.getCache(),false);
                    prints.setName("printer");
                    Vector services=msd.searchService(prints,false);
                    // let the user choose the preferred
                    ServiceList sl=new ServiceList("Image servers",services,IAm);
                    prints=sl.getSelected();
                    // connect to it and use the printer
                    OutputStream out=msd.useService(prints).getOutputStream();
                    for(int j=0; j<imagebytes.length; j+=1024){
                        out.write(imagebytes,j,Math.min(1024,imagebytes.length-j));
                        out.flush();
                    }                    
                    out.close();
                } catch(Exception ex){
                    System.err.println("Error while connecting: "+ex);
                    ex.printStackTrace();
                }
            }
        };
        thread.start();
    }

    /** Exits */
    public void exit_actionPerformed(ActionEvent e){
        finish();
        System.exit(0);
    }

    /** Finishes this program, freeing the resources. */
    public void finish(){
        if(cs!=null){
            try{
                cs.close();
            } catch(Exception e){
            }
            cs=null;
        }
        msd.finish();
    }

    /** Get the image selected and show it */
    public void images_actionPerformed(ActionEvent e){
        if(thread!=null&&thread.isAlive()){
            synchronized(thread){
                thread.interrupt();
            }
        }
        thread=new Thread(){
            public void run(){
                String img=images.getSelectedItem();
                try{
                    System.out.println("Retreiving image: "+img);
                    // write the name of the image selected
                    OutputStream out=cs.getOutputStream();
                    out.write(img.getBytes());
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
                        cs.getOutputStream().write('0');
                        cs.getOutputStream().flush();
                    }
                    // construct the image
                    Image im=Toolkit.getDefaultToolkit().createImage(b);
                    imagebytes=b;
                    // show it
                    image.setImage(im);
                    image.repaint();
                    
                    // save in a file
                    FileOutputStream fout=new FileOutputStream(new File("espera"));
                    fout.write(b);
                    fout.flush();
                    fout.close();
                    
                    System.out.println("Done");
                } catch(Exception ex){
                    System.err.println("Error during retreiving image: "+ex);
                }
            }
        };
        thread.start();
    }

    /** This class shows an image. */
    private class ImageViewer extends Canvas{
        private Image image=null;
        public ImageViewer(){
            setSize(new Dimension(160,160));
        }

        private void setImage(Image im){
            image=im;
        }
        private Image getImage(){
            return image;
        }

        public void update(Graphics g){
            paint(g);
        }

        public void paint(Graphics g){
            if(image!=null){
                g.drawImage(image,0,0,this);
            }
        }
    }
}


/** This class presents a list of service in a modal frame letting
 * the user choose for the service desired.
 */
class ServiceList extends Dialog{
    private List list=null;
    private Hashtable services=null;
    /** Creates and show a modal dialog box with the list of services.
     * @param name The title of the frame.
     * @param services The vector of services.
     * @param owner The owner frame. */
    ServiceList(String name,Vector services,Frame owner){
        super(owner,name,true);
        this.services=new Hashtable();
        list=new List();
        setLayout(new BorderLayout());
        for(Enumeration e=services.elements();e.hasMoreElements();){
            Service s=(Service)e.nextElement();
            int conf=0;
            if(s.getHops()==0){
                if(s.getConfidence()>0)
                    conf=100;
                else
                    conf=0;                
            }else{
                conf=(100*s.getConfidence())/s.getHops();
            }
            String n=s.getID()+"@"+s.getIDCache()+" ("+conf+"%)";
            list.add(n);
            this.services.put(n,s);
        }
        add(list,BorderLayout.CENTER);
        Button b=new Button("OK");
        b.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e){
                setVisible(false);
            }
        });
        add(b,BorderLayout.SOUTH);
        pack();
        setVisible(true);
    }

    /** @return The selected service */
    public Service getSelected(){
        return(Service)services.get(list.getSelectedItem());
    }
}

class ImageClient_images_actionAdapter implements ActionListener{
    private ImageClient adaptee;
    ImageClient_images_actionAdapter(ImageClient adaptee){
        this.adaptee=adaptee;
    }

    public void actionPerformed(ActionEvent e){
        adaptee.images_actionPerformed(e);
    }
}


class ImageClient_exit_actionAdapter implements ActionListener{
    private ImageClient adaptee;
    ImageClient_exit_actionAdapter(ImageClient adaptee){
        this.adaptee=adaptee;
    }

    public void actionPerformed(ActionEvent e){
        adaptee.exit_actionPerformed(e);
    }
}


class ImageClient_print_actionAdapter implements ActionListener{
    private ImageClient adaptee;
    ImageClient_print_actionAdapter(ImageClient adaptee){
        this.adaptee=adaptee;
    }

    public void actionPerformed(ActionEvent e){
        adaptee.print_actionPerformed(e);
    }
}


class ImageClient_search_actionAdapter implements ActionListener{
    private ImageClient adaptee;
    ImageClient_search_actionAdapter(ImageClient adaptee){
        this.adaptee=adaptee;
    }

    public void actionPerformed(ActionEvent e){
        adaptee.search_actionPerformed(e);
    }
}
