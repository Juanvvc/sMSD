package org.msd;

import javax.swing.JFrame;
import javax.swing.JTextPane;
import java.awt.*;
import javax.swing.JButton;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import org.msd.cache.*;
import org.msd.proxy.*;
import org.apache.log4j.*; //@@l
import javax.swing.JLabel;
import java.util.Collection;

/** An example of the MSD: enter a text, look for a printer and use it. */
public class PrinterExample extends JFrame implements MSDListener{
    private static final Logger logger=Logger.getLogger(PrinterExample.class); //@@l
    private boolean printing=false;

    /** Main method.
     * @param args Arguments of the system */
    public static void main(String[] args){
        BasicConfigurator.configure(); //@@l
        PrinterExample example=new PrinterExample();
        example.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        example.setTitle("MSD: Printer example");
        example.setVisible(true);
        example.setSize(new Dimension(400,500));
        example.validate();
    }

    public PrinterExample(){
        try{
            jbInit();
        } catch(Exception ex){
            ex.printStackTrace();
        }
    }

    /** Initiliza graphical widgets.
     * @throws java.lang.Exception An exception if any error occurs */
    private void jbInit() throws Exception{
        txtText.setMinimumSize(new Dimension(100,200));
        txtText.setText("");
        cmdPrinter.setActionCommand("Print");
        cmdPrinter.addActionListener(new
                                     PrinterExample_cmdPrinter_actionAdapter(this));
        lblBar.setText("");
        this.getContentPane().add(lblBar,java.awt.BorderLayout.SOUTH);
        this.getContentPane().add(cmdPrinter,java.awt.BorderLayout.NORTH);
        this.getContentPane().add(txtText,java.awt.BorderLayout.CENTER);
        cmdPrinter.setText("Print");
    }

    private JTextPane txtText=new JTextPane();
    private JButton cmdPrinter=new JButton();
    private Cache cache=null;
    private MSDManager msd=null;
    JLabel lblBar=new JLabel();

    /** @param e Event: the "print" button has been pressed */
    public void cmdPrinter_actionPerformed(ActionEvent e){
        printing=true;
        if(msd==null){
            // make a new MSDManager
            try{
                // create default cache
                cache=new Cache("200");
                // show properties dialog
                BrowserPropertiesGUI bp=new BrowserPropertiesGUI(this,cache);
                if(bp.canceled()){
                    logger.info("Init process canceled by the user"); //@@l
                    return;
                }
                // create manager
                msd=new MSDManager();
                msd.init("MSD",cache,MSDManager.DEFAULT);
                msd.addMSDListener(this);
            } catch(Exception ex){
                showText("Error while initializating MSD: "+ex);
                msd.finish();
                msd=null;
                ex.printStackTrace();
            }
        }else{
            searchPrinter();
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
            case MSDEvent.UPDATED:
          //      if(printing){
                    printing=false;
          //          searchPrinter();
          //      }
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
            return "Ready";
        default:
            return ""+level;
        }
    }

    /** Search for a printer. If one is found, call to usePrinter.
     * @todo Use a more general pattern. This one only finds printers on SLP
     * with scope=DEFAULT */
    private void searchPrinter(){
        Service printer=null;
        try{
            printer=(Service)cache.createElementFromXML("<service><classtype name=\"DEFAULT\"><classtype name=\"printer\"/></classtype></service>");
	}catch(Exception e){
            logger.warn("Pattern malformed"); //@@l
        }
	logger.info("Looking for "+printer); //@@l
        showText("Looking for printer...");
        Collection c=msd.searchService(printer,true);
        if(c.size()>0){
            showText("Printer found");
            printer=(Service)c.iterator().next();
            usePrinter(printer);
        } else{
            showText("Printer not found");
        }
    }

    /** @param printer Write on this printer the text entered */
    private void usePrinter(Service printer){
        showText("Using printer "+printer.getID()+" at "+
                 printer.getIDCache());
        try{
            org.msd.comm.ConnectionStreams cs=msd.useService(printer);
            java.io.OutputStream out=cs.getOutputStream();
            logger.debug("WRITTING: "+new String(txtText.getText())); //@@l
            out.write(txtText.getText().getBytes());
            out.flush();
            cs.close();
            showText("Done");
        } catch(Exception e){
            showText("Error: "+e);
            e.printStackTrace();
        }
    }

    /** @param text Log text on screen */
    private void showText(String text){
        lblBar.setText(text);
    }
}


class PrinterExample_cmdPrinter_actionAdapter implements ActionListener{
    private PrinterExample adaptee;
    PrinterExample_cmdPrinter_actionAdapter(PrinterExample adaptee){
        this.adaptee=adaptee;
    }

    public void actionPerformed(ActionEvent e){
        adaptee.cmdPrinter_actionPerformed(e);
    }
}

