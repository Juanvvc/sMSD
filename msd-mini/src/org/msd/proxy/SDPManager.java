/** This is the mini version */
package org.msd.proxy;

import org.msd.cache.*;
import javax.bluetooth.*;
import java.util.Vector;

/** Make device and service searches with SDP protocol
 *
 * Note: through this document BZ=Bluetooth
 * @author rsoldado
 * @version $Revision: 1.4 $ */

public class SDPManager extends SDManager{
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

    /** Empty Constructor.
     * This class needs an empty constructor if it is loaded from
     * Class.newInstace() (for example, throught a ClassLoader). Anyway, you
     * must avoid using any method before configure the manager with init(). */
    public SDPManager(){}

    /* Initializes the manager. Called inside the constructor, you must call
     * this method yourself only if you creates the class other way than
     * the 'new' method (i.e. ClassLoader) */
    public void init(Network net,Cache cache) throws Exception{
        super.init(net,cache);

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
            // run be continuated by only one thread
            maxServicesSearches=1;
        }

        deviceList=new Vector();
    }

    /* Initializes the manager to do a search for just one uuid*/
    public void init(Network net,Cache cache,String uuid) throws
            Exception{
        super.init(net,cache);

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
            proto[0]=new UUID(0x1002);
        }
        // Interesting service attributes that we want retrieve for service
        // record representation
        int[] attributes={0x0100,0x0004};

        try{
            for(int i=0;i<deviceList.size();i++){
                RemoteDevice dev=(RemoteDevice)deviceList.elementAt(i);
                try{
                    discoveryAgent.searchServices(attributes,proto,dev,listener);

                    /** Determine if another search can be started
                     * If not, wait for a service search to end */
                    synchronized(listener){
                        serviceSearchCount++;
                        if(serviceSearchCount==maxServicesSearches){
                            try{
                                listener.wait();
                            } catch(Exception e){
                                e.printStackTrace();
                            }
                        }
                    }
                } catch(BluetoothStateException e){
                                e.printStackTrace();
                }
            }
            /** Wait until all the service searches have completed */
            while(serviceSearchCount>0){

                synchronized(listener){
                    serviceSearchCount++;
                    if(serviceSearchCount==maxServicesSearches){
                        try{
                            listener.wait();
                        } catch(Exception e){
                                e.printStackTrace();
                        }
                    }
                }
            }
        } catch(Exception e){
                                e.printStackTrace();        
        }

        searchCompleted(SDListener.COMPLETED);
    }

    /** Returns an integer that identify this as a SDP Proxy*/
    public int getType(){
        return 2;
    }

    /** Do a device and service search using SDP protocol */
    public synchronized void search() throws Exception{
        if(busy){
            return;
        }
        searchStarted();

        // we put the device search in a diferent thread
        // for come back inmediately
        Thread t=new Thread(){
            public void run(){
                try{
                    /** Listener of events occurred in the search */
                    listener=new DiscoveryAdapter();

                    /** Start the device search */
                    // Delete all devices of a before search
                    deviceList.removeAllElements();
                    if(!discoveryAgent.startInquiry(DiscoveryAgent.GIAC,
                            listener)){
                    }

                    /** Wait until all devices are found before trying to start the service search */
                    synchronized(listener){
                        try{
                            listener.wait();
                        } catch(Exception e){

                        }
                    }

                    // Service search is started
                    if(deviceList.size()==0){
                        // complete the searching
                        searchCompleted(SDListener.COMPLETED);
                    } else if(deviceList.size()>0){
                        RemoteDevice[] devList=new RemoteDevice[deviceList.size()];
                        deviceList.copyInto(devList);
                        searchServices((RemoteDevice[])devList);
                    }

                } catch(Exception e){
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
            }
        }
    }

    /** Called when a service search is finished by a device
     *  It makes possible to continue the thread that is waiting for a search finished */
    private void serviceSearchEnd(int transID){

        serviceSearchCount--;
        synchronized(listener){
            listener.notifyAll();
        }

    }

    /** Called when a service is found by a device */
    private void servicesFound(ServiceRecord[] servicesRecord){
        System.out.println(servicesRecord.length+" service(s) found");
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
                elements.addElement(serv);

                // Get the Service Name
                elm=(DataElement)servicesRecord[i].getAttributeValue(0x0100);

                if(elm!=null&&elm.getDataType()==DataElement.STRING){
                    serviceList.addElement(servicesRecord[i]);
                    serv.setName((String)elm.getValue());
                    System.out.println("Service "+(String)elm.getValue());
                }

                // Get the url-connection
                Network n=(Network)network.clone();
                n.setURL(servicesRecord[i].getConnectionURL(ServiceRecord.
                        NOAUTHENTICATE_NOENCRYPT,false));
                serv.appendChild(n);
            }
        }

        catch(Exception e){
            e.printStackTrace();
        }
    }


    /** Listener of searches */
    private class DiscoveryAdapter implements DiscoveryListener{


        /** Called when a device is found in a inquiry
         * Add the device found to the list*/
        public void deviceDiscovered(RemoteDevice rd,DeviceClass cod){
            try{
                System.out.println(rd.getFriendlyName(false)+" found");
                // If a device was found in a previous search is
                // not added to the list
                if(!deviceList.contains(rd)){
                    deviceList.addElement(rd);
                }
            } catch(Exception e){
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
    }
}
