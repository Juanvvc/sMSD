
package org.msd;

import java.awt.*;
import java.awt.event.*;

/** Configures the MSD for the medium version */
public class MSDConfig extends Dialog implements ActionListener{
    private Button ok,cancel;
    private TextField name,url,port,urlM,portM;
    private boolean canceled=false;
    /** @param owner The owner of this dialog */
    public MSDConfig(Frame owner){
        super(owner,"Configure",true);
        setLayout(new BorderLayout());
        Panel p=new Panel();

        // the buttons
        ok=new Button("OK");
        ok.addActionListener(this);
        cancel=new Button("Cancel");
        cancel.addActionListener(this);
        p.add(ok);
        p.add(cancel);
        add(p,BorderLayout.SOUTH);

        // the info
        p=new Panel();
        p.setLayout(new GridLayout(5,2));
        p.add(new Label("IDCache: "));
        name=new TextField("medium",10);
        p.add(name);
        p.add(new Label("Local IP: "));
        try{
            url=new TextField(java.net.InetAddress.getLocalHost().
                              getHostAddress(),10);
        } catch(Exception e){
            url=new TextField("unknown.1",10);
        }
        p.add(url);
        p.add(new Label("Local port: "));
        port=new TextField("15151",10);
        p.add(port);
        String urll=url.getText();
        urll=urll.substring(0,urll.lastIndexOf("."))+".255";
        p.add(new Label("Multi IP: "));
        urlM=new TextField(urll,10);
        p.add(urlM);
        p.add(new Label("Multi port: "));
        portM=new TextField("15151",10);
        p.add(portM);
        add(p,BorderLayout.CENTER);

        validate();
        pack();
        setVisible(true);
    }

    public void actionPerformed(ActionEvent e){
        if(e.getSource()==cancel){
            canceled=true;
        }
        setVisible(false);
    }

    /** @return Wether the dialog was canceled */
    public boolean canceled(){
        return canceled;
    }

    /** @return The local address entered by the user */
    public org.msd.comm.Address getLocalAddress(){
        return new org.msd.comm.Address(url.getText(),
                                        Integer.parseInt(port.getText()));
    }

    /** @return The multicast address entered by the user */
    public org.msd.comm.Address getMulticastAddress(){
        return new org.msd.comm.Address(urlM.getText(),
                                        Integer.parseInt(portM.getText()));
    }

    /** @return The IDCache choosen by the user */
    public String getIDCache(){
        return name.getText();
    }
}

