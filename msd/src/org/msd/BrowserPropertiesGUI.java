/*
 * BrowserPropertiesGUI.java
 *
 * Created on 13 de marzo de 2005, 12:49
 */

package org.msd;

import org.msd.cache.*;
import org.msd.proxy.DefaultResourceBundle;
import javax.swing.*;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Vector;
import java.net.InetAddress;
import org.apache.log4j.Logger; //@@l

/** This class reads properties from the loaded cache and default configuration of the
 * system and present a graphical dialog to change them. This dialog is modal, and when
 * finished the cache and default properties will be the new ones configured by the user.
 * @version $Revision: 1.19 $
 */
public class BrowserPropertiesGUI extends JDialog{
    private static final Logger logger=Logger.getLogger(BrowserPropertiesGUI.class); //@@l

    private Cache cache;
    private DefaultResourceBundle res=new DefaultResourceBundle();
    private Network ethernet;
    private Network bluetooth;
    private Network wifi;

    private Network bridgeI1I2;
    private Network bridgeI1B;
    private Network bridgeI2B;

    private boolean canceled=true;

    /** Creates new form BrowserPropertiesGUI.
     * @param owner The owner of the dialog
     * @param c The cache to read the configuration from */
    public BrowserPropertiesGUI(JFrame owner,Cache c){
        super(owner,"Browser Properties",true);

        cache=c;
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        // create the bridges
        Network ethernetS=new Network(cache,false);
        ethernetS.setIDCache("");
        ethernetS.setName("ethernet");
        Network wifiS=new Network(cache,false);
        wifiS.setName("wifi");
        Network bluetoothS=new Network(cache,false);
        bluetoothS.setName("bluetooth");
        bridgeI1I2=new Network(cache,false);
        bridgeI1I2.setName("bridge");
        bridgeI1B=(Network)bridgeI1I2.clone();
        bridgeI2B=(Network)bridgeI1I2.clone();
        bridgeI1I2.appendChild(ethernetS);
        bridgeI1I2.appendChild(wifiS);
        bridgeI1B.appendChild(ethernetS);
        bridgeI1B.appendChild(bluetoothS);
        bridgeI2B.appendChild(wifiS);
        bridgeI2B.appendChild(bluetoothS);

        // load default cache if the passed one is empty
        if(cache.getChilds().size()==0){
            try{
                cache.load(new java.io.ByteArrayInputStream(res.getString(
                        "CacheXML").getBytes()));
            }catch(Exception e){
                throw new RuntimeException("Couldn't load default cache: "+e);
            }
        }

        // read values from current conf
        Service msd=new Service(cache,false);
        msd.setName("MSD");
        try{
            msd=(Service)cache.getElements(msd,cache.getChilds()).iterator().
                next();
            if(msd==null){
                throw new Exception();
            }
            // remove the read MSD from the cache
            cache.deleteElement(msd);
        }catch(Exception e){
            throw new NullPointerException("No MSD defined in cache");
        }
        ethernet=msd.getNetwork("ethernet");
        wifi=msd.getNetwork("wifi");
        bluetooth=msd.getNetwork("bluetooth");

        initComponents();

        // set values from configuration
        lstAlgorithms.addItem("shared");
        lstAlgorithms.addItem("single");
        lstAlgorithms.addItem("hierarchical");
        lstAlgorithms.setSelectedIndex(0);
        if(ethernet!=null){
            chkI1Active.setSelected(true);
            if(ethernet.getURL().length()>0){
                lstInternet1IPs.addItem(ethernet.getURL());
            }
            txtI1LocalPort.setText(""+ethernet.getPort());
            if(ethernet.isMain()){
                chkI1Main.setSelected(true);
            }
        }
        if(wifi!=null){
            chkI2Active.setSelected(true);
            if(wifi.getURL().length()>0){
                lstInternet2IPs.addItem(wifi.getURL());
            }
            txtI2LocalPort.setText(""+wifi.getPort());
            if(wifi.isMain()){
                chkI2Main.setSelected(true);
            }
        }else{
            txtI2LocalPort.setText(res.getString("MSD.wifi.MulticastPort"));
        }
        if(bluetooth!=null){
            chkBTActive.setSelected(true);
            if(bluetooth.isMain()){
                chkBTMain.setSelected(true);
            }
        }
        long l=System.currentTimeMillis();
        String id=Long.toString(l&0xffff);
        txtID.setText(id);

        txtI1MulticastURL.setText(res.getString("MSD.ethernet.MulticastURL"));
        txtI1MulticastPort.setText(res.getString("MSD.ethernet.MulticastPort"));
        txtI2MulticastURL.setText(res.getString("MSD.wifi.MulticastURL"));
        txtI2MulticastPort.setText(res.getString("MSD.wifi.MulticastPort"));

        // set the bridges
        if(cache.getElements(bridgeI1I2).size()>0){
            chkBI1I2.setSelected(true);
        }
        if(cache.getElements(bridgeI1B).size()>0){
            chkBI1B.setSelected(true);
        }
        if(cache.getElements(bridgeI2B).size()>0){
            chkBI2B.setSelected(true);
        }

        // set the list as the IPs found in interfaces
        Object[] ips=getIPs();
        for(int i=0; i<ips.length; i++){
            // discard IPv6 addresses
            String ip=(String)ips[i];
            if(ip.length()>15){
                continue;
            }
            lstInternet1IPs.addItem(ips[i]);
            lstInternet2IPs.addItem(ips[i]);
        }
        pack();
        setVisible(true);
    }

    /** @return If the dialog was canceled. */
    public boolean canceled(){
        return canceled;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    private void initComponents() {//GEN-BEGIN:initComponents
        javax.swing.JLabel jLabel1;
        javax.swing.JLabel jLabel2;
        javax.swing.JLabel jLabel3;
        javax.swing.JLabel jLabel4;
        javax.swing.JLabel jLabel7;
        javax.swing.JPanel jPanel1;
        javax.swing.JPanel jPanel2;
        javax.swing.JPanel jPanel3;
        javax.swing.JPanel jPanel5;
        javax.swing.JPanel jPanel6;
        javax.swing.JPanel jPanel7;

        jPanel4 = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        txtID = new JTextField(cache.getID());
        jLabel2 = new javax.swing.JLabel();
        lstAlgorithms = new javax.swing.JComboBox();
        jPanel2 = new javax.swing.JPanel();
        chkBTActive = new javax.swing.JCheckBox();
        chkBTMain = new javax.swing.JCheckBox();
        jLabel3 = new javax.swing.JLabel();
        txtBTUUID = new JTextField(res.getString("MSD.UUID"));
        jPanel3 = new javax.swing.JPanel();
        chkI1Active = new javax.swing.JCheckBox();
        chkI1Main = new javax.swing.JCheckBox();
        jLabel4 = new javax.swing.JLabel();
        jPanel8 = new javax.swing.JPanel();
        lstInternet1IPs=new javax.swing.JComboBox();
        lstInternet2IPs=new javax.swing.JComboBox();
        txtI1LocalPort = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        jPanel9 = new javax.swing.JPanel();
        txtI1MulticastURL = new javax.swing.JTextField();
        txtI1MulticastPort = new javax.swing.JTextField();
        jPanel5 = new javax.swing.JPanel();
        chkI2Active = new javax.swing.JCheckBox();
        chkI2Main = new javax.swing.JCheckBox();
        jLabel7 = new javax.swing.JLabel();
        jPanel10 = new javax.swing.JPanel();
        txtI2LocalPort = new javax.swing.JTextField();
        jLabel6 = new javax.swing.JLabel();
        jPanel11 = new javax.swing.JPanel();
        txtI2MulticastURL = new javax.swing.JTextField();
        txtI2MulticastPort = new javax.swing.JTextField();
        jPanel6 = new javax.swing.JPanel();
        chkBI1I2 = new javax.swing.JCheckBox();
        chkBI2B = new javax.swing.JCheckBox();
        chkBI1B = new javax.swing.JCheckBox();
        jPanel7 = new javax.swing.JPanel();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();

        getContentPane().setLayout(new java.awt.GridLayout(3, 2));

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        jPanel1.setLayout(new java.awt.GridLayout(2, 2));

        jPanel1.setBorder(new javax.swing.border.TitledBorder("General"));
        jLabel1.setText("Initial ID");
        jPanel1.add(jLabel1);

        txtID.setColumns(5);
        jPanel1.add(txtID);

        jLabel2.setText("Algorithm");
        jPanel1.add(jLabel2);

        jPanel1.add(lstAlgorithms);

        getContentPane().add(jPanel1);

        jPanel2.setLayout(new java.awt.GridLayout(2, 2));

        jPanel2.setBorder(new javax.swing.border.TitledBorder("Bluetooth"));
        chkBTActive.setText("Active");
        jPanel2.add(chkBTActive);

        chkBTMain.setText("Main");
        jPanel2.add(chkBTMain);

        jLabel3.setText("UUID");
        jPanel2.add(jLabel3);

        txtBTUUID.setColumns(10);
        jPanel2.add(txtBTUUID);

        getContentPane().add(jPanel2);

        jPanel3.setLayout(new java.awt.GridLayout(3, 2));

        jPanel3.setBorder(new javax.swing.border.TitledBorder("Ethernet"));
        chkI1Active.setText("Active");
        jPanel3.add(chkI1Active);

        chkI1Main.setText("Main");
        jPanel3.add(chkI1Main);

        jLabel4.setText("Local");
        jPanel3.add(jLabel4);

        jPanel8.add(lstInternet1IPs);

        txtI1LocalPort.setColumns(3);
        jPanel8.add(txtI1LocalPort);

        jPanel3.add(jPanel8);

        jLabel5.setText("Multicast");
        jPanel3.add(jLabel5);

        txtI1MulticastURL.setColumns(10);
        jPanel9.add(txtI1MulticastURL);

        txtI1MulticastPort.setColumns(3);
        jPanel9.add(txtI1MulticastPort);

        jPanel3.add(jPanel9);

        getContentPane().add(jPanel3);

        jPanel5.setLayout(new java.awt.GridLayout(3, 2));

        jPanel5.setBorder(new javax.swing.border.TitledBorder("Wi-Fi"));
        chkI2Active.setText("Active");
        jPanel5.add(chkI2Active);

        chkI2Main.setText("Main");
        jPanel5.add(chkI2Main);

        jLabel7.setText("Local");
        jPanel5.add(jLabel7);

        jPanel10.add(lstInternet2IPs);

        txtI2LocalPort.setColumns(3);
        jPanel10.add(txtI2LocalPort);

        jPanel5.add(jPanel10);

        jLabel6.setText("Multicast");
        jPanel5.add(jLabel6);

        txtI2MulticastURL.setColumns(10);
        jPanel11.add(txtI2MulticastURL);

        txtI2MulticastPort.setColumns(3);
        jPanel11.add(txtI2MulticastPort);

        jPanel5.add(jPanel11);

        getContentPane().add(jPanel5);

        jPanel6.setLayout(new java.awt.GridLayout(3, 2));

        jPanel6.setBorder(new javax.swing.border.TitledBorder("Bridging"));
        chkBI1I2.setText("Ethernet and Wi-Fi");
        jPanel6.add(chkBI1I2);

        chkBI2B.setText("Wi-Fi and Bluetooth");
        jPanel6.add(chkBI2B);

        chkBI1B.setText("Ethernet and Bluetooth");
        jPanel6.add(chkBI1B);

        getContentPane().add(jPanel6);

        jButton1.setText("Ok");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        jPanel7.add(jButton1);

        jButton2.setText("Cancel");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });

        jPanel7.add(jButton2);

        getContentPane().add(jPanel7);

        pack();
    }//GEN-END:initComponents

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt){//GEN-FIRST:event_jButton2ActionPerformed
        canceled=true;
        setVisible(false);
    }//GEN-LAST:event_jButton2ActionPerformed

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt){//GEN-FIRST:event_jButton1ActionPerformed
        // construct the new properties of the system
        DefaultResourceBundle.setValue("MSD.ethernet.MulticastURL",txtI1MulticastURL.getText());
        DefaultResourceBundle.setValue("MSD.wifi.MulticastURL",txtI2MulticastURL.getText());
        DefaultResourceBundle.setValue("MSD.ethernet.MulticastPort",txtI1MulticastPort.getText());
        DefaultResourceBundle.setValue("MSD.wifi.MulticastPort",txtI2MulticastPort.getText());
        DefaultResourceBundle.setValue("MSD.UUID",txtBTUUID.getText());
        DefaultResourceBundle.setValue("MSD.Algorithm",
                     (String)lstAlgorithms.getModel().getSelectedItem());

        try{
            //create the cache
            cache.setID(txtID.getText());
            Service msd=new Service(cache,true);
            msd.setName("MSD");
            Network net;
            if(chkBTActive.getSelectedObjects()!=null){
                net=new Network(cache,false);
                net.setName("bluetooth");
                if(chkBTMain.getSelectedObjects()!=null){
                      net.setMain(true);
                  }
                        msd.appendChild(net);
            }
            if(chkI1Active.getSelectedObjects()!=null){
                net=new Network(cache,false);
                net.setName("ethernet");
                net.setURL((String)lstInternet1IPs.getModel().getSelectedItem());
                net.setPort(Integer.valueOf(txtI1LocalPort.getText()).intValue());
                if(chkI1Main.getSelectedObjects()!=null){
                    net.setMain(true);
                }
                msd.appendChild(net);
            }
            if(chkI2Active.getSelectedObjects()!=null){
                net=new Network(cache,false);
                net.setName("wifi");
                net.setURL((String)lstInternet2IPs.getModel().getSelectedItem());
                net.setPort(Integer.valueOf(txtI2LocalPort.getText()).intValue());
                if(chkI2Main.getSelectedObjects()!=null){
                    net.setMain(true);
                }
                msd.appendChild(net);
            }

            // remove bridges in msd
            Network b=new Network(cache,false);
            b.setName("bridge");
            Object[] bs=cache.getElements(b).toArray();
            for(int i=0;i<bs.length;i++){
                msd.deleteChild((Network)bs[i]);
            }
            // add active bridges
            if(chkBI1B.getSelectedObjects()!=null){
                msd.appendChild(bridgeI1B);
            }
            if(chkBI1I2.getSelectedObjects()!=null){
                msd.appendChild(bridgeI1I2);
            }
            if(chkBI2B.getSelectedObjects()!=null){
                msd.appendChild(bridgeI2B);
            }
        } catch(Exception e){
            throw new RuntimeException("Error while configuring system: "+e);
        }

        canceled=false;
        setVisible(false);
    }//GEN-LAST:event_jButton1ActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox chkBI1B;
    private javax.swing.JCheckBox chkBI1I2;
    private javax.swing.JCheckBox chkBI2B;
    private javax.swing.JCheckBox chkBTActive;
    private javax.swing.JCheckBox chkBTMain;
    private javax.swing.JCheckBox chkI1Active;
    private javax.swing.JCheckBox chkI1Main;
    private javax.swing.JCheckBox chkI2Active;
    private javax.swing.JCheckBox chkI2Main;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel11;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JComboBox lstAlgorithms;
    private javax.swing.JComboBox lstInternet1IPs;
    private javax.swing.JComboBox lstInternet2IPs;
    private javax.swing.JTextField txtBTUUID;
    private javax.swing.JTextField txtI1LocalPort;
    private javax.swing.JTextField txtI1MulticastPort;
    private javax.swing.JTextField txtI1MulticastURL;
    private javax.swing.JTextField txtI2LocalPort;
    private javax.swing.JTextField txtI2MulticastPort;
    private javax.swing.JTextField txtI2MulticastURL;
    private javax.swing.JTextField txtID;
    // End of variables declaration//GEN-END:variables

    /** @return An array of IPs (as String) of this system */
    private Object[] getIPs(){
        try{
            Vector exit=new Vector();
            Enumeration e=NetworkInterface.getNetworkInterfaces();
            while(e.hasMoreElements()){
                NetworkInterface n=(NetworkInterface)e.nextElement();
                logger.info("New Ethernet interface: "+n.getName()); //@@l
                Enumeration e2=n.getInetAddresses();
                while(e2.hasMoreElements()){
                    String ip=((InetAddress)e2.nextElement()).getHostAddress();
                    logger.info("New IP: "+ip); //@@l
                    exit.add(ip);
                }
            }
            return exit.toArray();
        }catch(Exception e){
            logger.warn("Error while getting network interfaces: "+e); //@@l
            return new String[0];
        }
    }
}
