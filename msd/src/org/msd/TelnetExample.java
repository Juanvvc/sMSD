package org.msd;

import javax.swing.JFrame;
import javax.swing.JButton;
import java.awt.*;
import javax.swing.JTextArea;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import org.msd.proxy.*;
import org.msd.cache.*;

import org.apache.log4j.Logger; //@@l
import java.util.Collection;

import java.io.*;

/** This client example look for and uses a telnet service.
 * The service shuld be desribed in SLP with
 * slptool register service:telnet://HOST:PORT
 * Telnet is a not-secure algotirhm, so remember not
 * login to a critical user and remove the server when the
 * test is finished. In Linux, it is possible be asked about
 * a term type: "xterm" seems to work.
 * The telnet protocol is described in RFC854
 * @version $Revision: 1.2 $ $Date: 2005-09-27 17:01:22 $
 */
public class TelnetExample extends JFrame implements MSDListener{
    private static final Logger logger=Logger.getLogger(TelnetExample.class); //@l

    public TelnetExample(){
        try{
            org.apache.log4j.BasicConfigurator.configure(); //@l
            jbInit();
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        } catch(Exception ex){
            logger.fatal("Error during init: "+ex); //@@l
            ex.printStackTrace();
            System.exit(1);
        }
    }

    private MSDManager msd=null;
    private Cache cache=null;

    public static void main(String[] args){
        TelnetExample telnetexample=new TelnetExample();
        telnetexample.setVisible(true);
    }

    private void jbInit() throws Exception{
        cmdTelnet.setActionCommand("Command");
        cmdTelnet.setText("Join");
        cmdTelnet.addActionListener(new TelnetExample_cmdTelnet_actionAdapter(this));
        text.setEnabled(false);
        command.setColumns(20);
        ok.setActionCommand("ok");
        ok.addActionListener(new TelnetExample_ok_actionAdapter(this));
        this.getContentPane().add(cmdTelnet,java.awt.BorderLayout.NORTH);
        this.getContentPane().add(text,java.awt.BorderLayout.CENTER);
        this.getContentPane().add(jPanel1,java.awt.BorderLayout.SOUTH);
        jPanel1.setLayout(gridLayout2);
        gridLayout2.setRows(2);
        label.setText("");
        command.setText("");
        ok.setText("Ok");
        jPanel1.add(jPanel2);
        jPanel2.add(command);
        jPanel2.add(ok);
        jPanel1.add(label);
        text.setText("");
    }

    JButton cmdTelnet=new JButton();
    JTextArea text=new JTextArea();
    JPanel jPanel1=new JPanel();
    JPanel jPanel2=new JPanel();
    GridLayout gridLayout2=new GridLayout();
    JLabel label=new JLabel();
    JTextField command=new JTextField();
    JButton ok=new JButton();

    private int modo;

    /** If the button is pressed, perform the proper action:
     * create and join to the network, look for the service and use it
     * or disconnect.
     */
    public void cmdTelnet_actionPerformed(ActionEvent e){
        switch(modo){
        case 0:

            // connect to MSD
            try{
                cache=new Cache();
                msd=new MSDManager();
                BrowserPropertiesGUI b=new BrowserPropertiesGUI(this,cache);
                if(!b.canceled()){
                    msd.init("MSD",cache,MSDManager.DEFAULT);
                }
                modo=1;
                cmdTelnet.setText("Search");
            } catch(Exception ex){
                logger.error("Error: "+ex); //@@l
                ex.printStackTrace();
            }
            break
                    ;
        case 1:

            // search telnet
            searchTelnet();
            modo=2;
            cmdTelnet.setText("Disconnect");
            break;
        case 2:
            try{
                out.close();
            } catch(Exception ex){
                logger.warn("Error while closing: "+ex); //@@l
                ex.printStackTrace();
            }
            cmdTelnet.setText("Search");
            modo=1;
        }
    }


    /** @param e An event from MSDManager */
    public void event(MSDEvent e){
        switch(e.getType()){
        case MSDEvent.LEVEL:

            // get level
            int level=msd.getLevel(e.getNetwork());
            showText("Level: "+nameLevel(level));
            break;
        }
    }

    /** @param level A level int.
     * @return The level name. */
    public String nameLevel(int level){
        switch(level){
        case MessageManager.BROWSE:
            return "Browsing";
        case MessageManager.DISCOVER_MAIN:
            return "Discovering main";
        case MessageManager.ELECTION:
            return "Electing";
        case MessageManager.INITIAL_UPDATE:
            return "Initial Update";
        case MessageManager.JOINING:
            return "Joining";
        case MessageManager.UPDATE:
            return "Update";
        case MessageManager.USE:
            return "Using";
        case MessageManager.WAIT_EVENT:
            return "Waiting";
        default:
            return ""+level;
        }
    }

    /** Search for a telnet. If one is found, call to useTelnet. */
    private void searchTelnet(){
        Service telnet=null;
        try{
            telnet=(Service)cache.createElementFromXML("<service><classtype name=\"DEFAULT\"><classtype name=\"telnet\"/></classtype></service>");
        } catch(Exception e){
            logger.warn("Pattern malformed"); //@@l
        }
        logger.info("Looking for "+telnet); //@@l
        showText("Looking for telnet...");
        Collection c=msd.searchService(telnet,true);
        if(c.size()>0){
            showText("Telnet found");
            telnet=(Service)c.iterator().next();
            useTelnet(telnet);
        } else{
            showText("Telnet not found");
        }
    }

    /** @param telnet Use the telnet command */
    private void useTelnet(Service telnet){
        showText("Using telnet "+telnet.getID()+" at "+telnet.getIDCache());
        try{
            org.msd.comm.ConnectionStreams cs=msd.useService(telnet);
            // start reader
            new ReadTelnet(cs.getInputStream());
            out=cs.getOutputStream();
            // IAC DO ECHO. See RFC857
            out.write(new byte[]{(byte)0xff,(byte)0xfd,(byte)0x01});
            out.flush();
        } catch(Exception e){
            showText("Error: "+e);
            e.printStackTrace();
        }
    }

    private OutputStream out=null;

    /** @param text Log text on screen */
    private void showText(String text){
        label.setText(text);
    }

    public void ok_actionPerformed(ActionEvent e){
        try{
            out.write(text.getText().getBytes());
            // append CR+LF
            out.write(new byte[]{(byte)0x0d,(byte)0x0a});
            out.flush();
            //text.setText("");
        } catch(Exception ex){
            logger.error("Error writing: "+ex); //@@l
            ex.printStackTrace();
        }
    }

    private void writeString(String s){
        System.out.print(s);
        text.append(s);
    }

    /** Read the telnet protocol from an input stream. */
    private class ReadTelnet implements Runnable{
        private InputStream in;
        /** @param in Start reading telnet protocol from this stream */
        public ReadTelnet(InputStream in){
            this.in=in;
            Thread t=new Thread(this,"TelnetExample");
            t.start();
        }
        /** */
        public void run(){
            try{
                byte[] buffer=new byte[256];
                while(true){
                    int r=in.read(buffer);
                    logger.debug(r+" bytes readed"); //@@l

                    for(int i=0;i<r;i++){
                        // ignore NULL
                        if(buffer[i]==0){
                            continue;
                        }
                        if(buffer[i]==(byte)0xff){
                            // special command
                            switch(buffer[i+1]){
                            case(byte)0xfd:
                            case(byte)0xfe:
                                // DO and DON'T: WON'T any option
                                byte[] resp={(byte)0xff,(byte)0xfc,buffer[i+2]};
                                out.write(resp);
                                out.flush();
                                i+=2;
                                break;
                            case(byte)0xfb:
                            case(byte)0xfc:
                                // ignore WILL and WON'T
                                i+=2;
                                break;
                            default:
                                // ignore any other command
                                i+=1;
                            }
                        } else{
                            // normal byte to be written
                            byte[] b={buffer[i]};
                            writeString(new String(b));
                        }
                    }
                }
            } catch(Exception e){
                // after any error, close the stream
                try{
                    in.close();
                } catch(Exception e2){
                }
                e.printStackTrace();
            }
        }
    }
}


class TelnetExample_ok_actionAdapter implements ActionListener{
    private TelnetExample adaptee;
    TelnetExample_ok_actionAdapter(TelnetExample adaptee){
        this.adaptee=adaptee;
    }

    public void actionPerformed(ActionEvent e){
        adaptee.ok_actionPerformed(e);
    }
}


class TelnetExample_cmdTelnet_actionAdapter implements ActionListener{
    private TelnetExample adaptee;
    TelnetExample_cmdTelnet_actionAdapter(TelnetExample adaptee){
        this.adaptee=adaptee;
    }

    public void actionPerformed(ActionEvent e){
        adaptee.cmdTelnet_actionPerformed(e);
    }
}
