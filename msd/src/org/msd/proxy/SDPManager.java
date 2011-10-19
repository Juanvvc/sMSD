package org.msd.proxy;

import org.msd.cache.*;
import javax.bluetooth.*;
import org.apache.log4j.*; //@@l
import java.util.Vector;
import java.util.Enumeration;
import org.klings.wireless.BluetoothNumbers.*;
import java.util.ResourceBundle;

/** Make device and service searches with SDP protocol
 *
 * Note: through this document BZ=Bluetooth
 * @author rsoldado
 * @version $Revision: 1.12 $ */

public class SDPManager extends SDManager{
    /** Logger of the class */
    private Logger logger=Logger.getLogger(SDPManager.class); //@@l

    /** Own SDP listener */
    private DiscoveryListener listener;

    /**  DiscoveryAgent for the Bluetooth local device */
    private DiscoveryAgent discoveryAgent;

    /** The max number of service searches  that can occur at any one time */
    private int maxServicesSearches=0;

    /** The number of service searches that are presently in progress */
    private int serviceSearchCount;

    /** Keeps track of the devices found during an inquiry */
    private Vector deviceList;

    /** Boolean saying if the search is for just one uuid (true) or for the Public Browse Group (false)*/
    private boolean singleSearch=false;

    /** uuid to do the single search*/
    private String uuid;

    /** ResurceBundle to read the configuration from */
    private ResourceBundle res;

    /** Empty Constructor.
     * This class needs an empty constructor if it is loaded from
     * Class.newInstace() (for example, throught a ClassLoader). Anyway, you
     * must avoid using any method before configure the manager with init(). */
    public SDPManager(){
        logger.setLevel(SDManager.LEVEL);
    }

    /* Initializes the manager. Called inside the constructor, you must call
     * this method yourself only if you creates the class other way than
     * the 'new' method (i.e. ClassLoader) */
    public void init(Network net,Cache cache,String resource) throws Exception{
        super.init(net,cache,resource);

        // Do things inside in a lightly danger way
        try{
            // Retrieves the DiscoveryAgent that allows us to perform device and
            // service discovery
            discoveryAgent=LocalDevice.getLocalDevice().getDiscoveryAgent();
            // Takes name services and BZ classes
            //bznames=ResourceBundle.getBundle("lang.bznames");
        } catch(Exception e){
            // if faced an error an exception is launched
            working=false;
            throw e;
        }
        working=true;

        /* Retrieve the maxim number of concurrent service searches that
            can exist at any one time */
        try{
            maxServicesSearches=Integer.parseInt(LocalDevice.getProperty(
                    "bluetooth.sd.trans.max"));
        } catch(NumberFormatException e){
            logger.warn("Error asking for max number of threads: "+e.getMessage()); //@@l
            // run be continuated by only one thread
            maxServicesSearches=1;
        }

        deviceList=new Vector();
    }

    /* Initializes the manager to do a search for just one uuid*/
    public void init(Network net,Cache cache,String resource,String uuid) throws
            Exception{
        super.init(net,cache,resource);

        // Get the uuid and prepare the system to do a search only for this uuid
        this.uuid=uuid;
        if(uuid!=null){
            singleSearch=true;
        }

        // Do things inside in a lightly danger way
        try{
            // Retrieves the DiscoveryAgent that allows us to perform device and
            // service discovery
            discoveryAgent=LocalDevice.getLocalDevice().getDiscoveryAgent();
            // Takes name services and BZ classes
            //bznames=ResourceBundle.getBundle("lang.bznames");
        } catch(Exception e){
            // if faced an error an exception is launched
            working=false;
            throw e;
        }
        working=true;

        /* Retrieve the maxim number of concurrent service searches that
            can exist at any one time */
        try{
            maxServicesSearches=Integer.parseInt(LocalDevice.getProperty(
                    "bluetooth.sd.trans.max"));
        } catch(NumberFormatException e){
            logger.warn("Error asking for max number of threads: "+e.getMessage()); //@@l
            // run be continuated by only one thread
            maxServicesSearches=1;
        }

        deviceList=new Vector();
    }


    /** Completes a service search on each remote device in the list */
    private void searchServices(RemoteDevice[] devList){

        UUID[] proto;
        if(singleSearch){
            //Look up for the single uuid
            proto=new UUID[2];
            /* Add the UUID for L2Cap for make sure that the service record found will
             * support L2CAP */
            proto[0]=new UUID(0x0100);
            proto[1]=new UUID(uuid,false);
        } else{
            //Look up for the PublicBrowseGroup (0x1002)
            proto=new UUID[1];
            proto[0]=new UUID(BTServiceClass.PUBLICBROWSEGROUP);
        }
        // Interesting service attributes that we want retrieve for service
        // record representation
        int[] attributes={
                         BTServiceAttributeId.SDP_SERVICERECORDHANDLE,
                         BTServiceAttributeId.SDP_SERVICECLASSIDLIST,
                         BTServiceAttributeId.SDP_PROTOCOLDESCRIPTORLIST,
                         BTServiceAttributeId.SDP_SERVICENAME,
                         BTServiceAttributeId.SDP_SERVICEDESCRIPTION,
                         BTServiceAttributeId.SDP_PROVIDERNAME
        };

        try{
            logger.debug("Devices found: "+deviceList.size()); //@@l
            for(int i=0;i<deviceList.size();i++){
                RemoteDevice dev=(RemoteDevice)deviceList.elementAt(i);
                try{
                    logger.debug("Start service search for: "+ //@@l
                                 devList[i].getFriendlyName(false)); //@@l
                    discoveryAgent.searchServices(attributes,proto,dev,listener);

                    /** Determine if another search can be started
                     * If not, wait for a service search to end */
                    synchronized(listener){
                        serviceSearchCount++;
                        if(serviceSearchCount==maxServicesSearches){
                            try{
                                listener.wait();
                            } catch(Exception e){}
                        }
                    }
                } catch(BluetoothStateException e){
                    logger.warn("Failed to start the search on this device: "+e); //@@l
                }
            }
            /** Wait until all the service searches have completed */
            while(serviceSearchCount>0){

                synchronized(listener){
                    serviceSearchCount++;
                    if(serviceSearchCount==maxServicesSearches){
                        try{
                            listener.wait();
                        } catch(Exception e){}
                    }
                }
            }
        } catch(Exception e){}

        searchCompleted(SDListener.COMPLETED);
    }

    /** Returns an integer that identify this as a SDP Proxy*/
    public int getType(){
        return 2;
    }

    /** Do a device and service search using SDP protocol */
    public synchronized void search() throws Exception {
        if(busy){
            logger.warn("Another searching is taking place"); //@@l
            return;
        }
        searchStarted();

        // we put the device search in a diferent thread
        // for come back inmediately
        Thread t=new Thread("SDPManager"){
            public void run(){
                try{
                    /** Listener of events occurred in the search */
                    listener=new DiscoveryAdapter();

                    /** Start the device search */
                    // Delete all devices of a before search
                    deviceList.clear();
                    if(!discoveryAgent.startInquiry(DiscoveryAgent.GIAC,
                            listener)){
                        logger.error("Inquiry not started "); //@@l
                    }

                    /** Wait until all devices are found before trying to start the service search */
                    synchronized(listener){
                        try{
                            listener.wait();
                        } catch(Exception e){

                            logger.error("Error while searching for devices: "+ //@@l
                                         e.toString()); //@@l

                        }
                    }

                    // Service search is started
                    if(deviceList.size()==0){
                        logger.debug("No devices found"); //@@l
                        // complete the searching
                        searchCompleted(SDListener.COMPLETED);
                    } else if(deviceList.size()>0){
                        RemoteDevice[] devList=new RemoteDevice[deviceList.size()];
                        deviceList.copyInto(devList);
                        searchServices((RemoteDevice[])devList);
                    }

                } catch(Exception e){
                    logger.error("Error while searching for devices: "+ //@@l
                                 e.toString()); //@@l
                }
            }
        };
        t.start();
    }

    /** Called when device search has been finished
     *  It makes possible to continue the thread that is waiting for a search finished. */
    private void inquiryEnd(){
        synchronized(listener){
            try{
                listener.notifyAll();
            } catch(Exception e){
                logger.error("Error while notifing: "+e.toString()); //@@l
            }
        }
    }

    /** Called when a service search is finished by a device
     *  It makes possible to continue the thread that is waiting for a search finished */
    private void serviceSearchEnd(int transID){

        serviceSearchCount--;
        logger.debug("End of searching for this device: "); //@@l
        synchronized(listener){
            listener.notifyAll();
        }

    }

    /** Called when a service is found by a device */
    private void servicesFound(ServiceRecord[] servicesRecord){
        if(servicesRecord.length==0){
            return;
        }

        try{

            DataElement elm=null;
            // Keeps track of the services found
            Vector serviceList=new Vector();

            /** Retrieve interesting service attributes for every service found
             * and store them in a cache
             * */
            for(int i=0;i<servicesRecord.length;i++){
                // Give form to the service for cache
                Service serv=new Service(cache,true);
                elements.add(serv);

                // SERVICE CLASSES

                //Get the service class ID list
                elm=(DataElement)servicesRecord[i].getAttributeValue(
                        BTServiceAttributeId.SDP_SERVICECLASSIDLIST);

                // We put classes in vector in opposite order like them were found
                // that is: classes will be listed in order from the most general class to the most specific class
                Vector classes=new Vector();
                // short UUID (16 bits) from service class
                int shortUUID=0;

                if(elm!=null&&elm.getDataType()==DataElement.DATSEQ){
                    //elm should be a DATSEQ of UUIDs
                    DataElement elm2=null;
                    UUID uuid=null;

                    try{
                        Enumeration e=(Enumeration)elm.getValue();
                        while(e.hasMoreElements()){
                            elm2=(DataElement)e.nextElement();

                            if(elm2.getDataType()==DataElement.UUID){
                                uuid=(UUID)elm2.getValue();
                                // put it in classes vector
                                classes.add(0,uuid);
                            }
                        }
                    } catch(Exception e){
                        logger.error("Unpredicted Object: "+e.toString()); //@@l
                    }
                }

                // Make class structure inside father service of class at the moment analized

                Element classFather=serv;
                // Make class arquitectura depending of structure found in classes
                for(Enumeration e=classes.elements();e.hasMoreElements();){
                    UUID uuid=(UUID)e.nextElement();
                    ClassType classElement=new ClassType(cache,false);

                    // Get service name and show it
                    classElement.setAttrStr("sdp-uuid",uuid.toString());
                    shortUUID=BTUUIDTool.shortUUID(uuid);
                    String className;
                    if(shortUUID!=-1){
                        className=BTServiceClass.serviceClassName(shortUUID)+
                                  ", ("+BTUUIDTool.toHexString(shortUUID)+")";
                    } else{
                        className="0x"+uuid.toString();
                    }
                    classElement.setName(className);
                    logger.debug("ServiceClassIdList: "+className); //@@l

                    // Add the class to the father and we decide this class is the father from next classes
                    classFather.appendChild(classElement);
                    classFather=classElement;
                }

                // THE REST OF ATTRIBUTES

                // Get the Service Name
                RemoteDevice dev=servicesRecord[i].getHostDevice();
                elm=(DataElement)servicesRecord[i].getAttributeValue(
                        BTServiceAttributeId.SDP_SERVICENAME);

                if(elm!=null&&elm.getDataType()==DataElement.STRING){
                    serviceList.addElement(servicesRecord[i]);
                    logger.debug("Service: "+(String)elm.getValue()+" found!"); //@@l
                    serv.setName((String)elm.getValue());
                }

                // Get the Service Record Handle
                elm=(DataElement)servicesRecord[i].getAttributeValue(
                        BTServiceAttributeId.SDP_SERVICERECORDHANDLE);
                if(elm!=null&&elm.getDataType()==DataElement.U_INT_4){
                    long var=elm.getLong();
                    logger.debug("Service Record Handle:  0x"+ //@@l
                                 Long.toString(var,16)); //@@l
                    serv.setAttrStr("sdp-servicerecordhandle",
                                    Long.toString(var,16));
                }

                //Get the Service Description
                elm=(DataElement)servicesRecord[i].getAttributeValue(
                        BTServiceAttributeId.SDP_SERVICEDESCRIPTION);
                if(elm!=null&&elm.getDataType()==DataElement.STRING){
                    logger.debug("Description: "+(String)elm.getValue()); //@@l
                    serv.setAttrStr("description",(String)elm.getValue());
                }

                // Get the Provider Name
                elm=(DataElement)servicesRecord[i].getAttributeValue(
                        BTServiceAttributeId.SDP_PROVIDERNAME);

                if(elm!=null&&elm.getDataType()==DataElement.STRING){
                    logger.debug("Provider: "+(String)elm.getValue()); //@@l
                    serv.setAttrStr("provider",(String)elm.getValue());
                }

                // Get the url-connection
                Network n=(Network)network.clone();
                n.setURL(servicesRecord[i].getConnectionURL(ServiceRecord.
                        NOAUTHENTICATE_NOENCRYPT,false));
                serv.appendChild(n);

                // Retrieve the protocol descriptor list:
                // protocols that could be acceded by the service
                elm=(DataElement)servicesRecord[i].getAttributeValue(
                        BTServiceAttributeId.SDP_PROTOCOLDESCRIPTORLIST);

                if(elm!=null&&elm.getDataType()==DataElement.DATSEQ){

                    //elm should be a DATSEQ of DATSEQs of UUIDs optionals parameters
                    DataElement elm2=null;
                    DataElement elm3=null;
                    UUID uuid=null;
                    String out2=null;

                    try{
                        //Get the enumeration to the "outer" DATSEQ
                        Enumeration e=(Enumeration)elm.getValue();

                        //Iterate through the "outer" DATSEQ
                        while(e.hasMoreElements()){

                            elm2=(DataElement)e.nextElement();

                            if(elm2.getDataType()==DataElement.DATSEQ){

                                //Get the enumeration to the "inner" DATSEQ
                                Enumeration e2=(Enumeration)elm2.getValue();
                                elm3=(DataElement)e2.nextElement();

                                //the first element should be UUID
                                if(elm3.getDataType()==DataElement.UUID){

                                    uuid=(UUID)elm3.getValue();
                                    //get a short UUID (16 bits)
                                    int id=BTUUIDTool.shortUUID(uuid);

                                    if(id!=-1){
                                        out2=BTProtocol.protocolName(id)+", ("+
                                             BTUUIDTool.toHexString(id)+")";

                                    } else{
                                        out2="0x"+uuid.toString();
                                    }

                                    logger.debug("ProtocolDescriptorList: "+ //@@l
                                                 out2); //@@l

                                    //if the protocol is L2CAP should retrieve the PSM
                                    //if the protocol is RFCOMM should retrieve the channel number
                                    if((id==BTProtocol.L2CAP||
                                        id==BTProtocol.RFCOMM)&&
                                       e2.hasMoreElements()){

                                        elm3=(DataElement)e2.nextElement();
                                        int type=elm3.getDataType();
                                        //we are looking for an any class integer
                                        if(type>=DataElement.U_INT_1&&
                                           type<=DataElement.INT_16){
                                            if(id==BTProtocol.L2CAP){
                                                logger.debug("PSM: "+ //@@l
                                                        elm3.getLong()); //@@l

                                            } else{
                                                logger.debug("Channel: "+ //@@l
                                                        elm3.getLong()); //@@l
                                            }
                                        }

                                    } //End to check for protocols and DATSEQ Elements
                                } //End to check if elm3 is a UUID
                            } //End check for Initial element == DATSEQ
                        }

                    } catch(Exception e){
                        logger.warn("Unpredicted object: "+e.toString()); //@@l
                    }
                }

                // Add the proxy to the service
                serv.appendChild(proxy);
            }
        } catch(Exception e){
            logger.error("Error while registering services: "+e.toString()); //@@l

        }
    }


    /** Listener of searches */
    private class DiscoveryAdapter implements DiscoveryListener{


        /** Called when a device is found in a inquiry
         * Add the device found to the list*/
        public void deviceDiscovered(RemoteDevice rd,DeviceClass cod){
            try{
                // If a device was found in a previous search is
                // not added to the list
                if(!deviceList.contains(rd)){
                    deviceList.addElement(rd);
                    logger.debug("Device found: "+rd.getFriendlyName(false)); //@@l
                }
            } catch(Exception e){
                logger.debug("Error while discovering devices: "+e.toString()); //@@l
            }
        }

        /** Called when the device search is finished */
        public void inquiryCompleted(int discType){
            inquiryEnd();
        }

        /** Called when a service is found */
        public void servicesDiscovered(int tansID,ServiceRecord[] servRecord){
            servicesFound(servRecord);
        }

        /** Called when a service search is finished  */
        public void serviceSearchCompleted(int transID,int respCode){
            serviceSearchEnd(transID);
        }
    }


    public void finish(){
        if(!working){
            return;
        }
        working=false;
        logger.info("SDPManager finished."); //@@l
    }
}
