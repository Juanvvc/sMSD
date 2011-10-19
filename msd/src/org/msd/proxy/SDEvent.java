/* Manager event of discovery protocols */

package org.msd.proxy;
import org.msd.proxy.SDListener;

/**
 * This class keeps information about SDManager events.
 * @version $Revision: 1.4 $
 */
public class SDEvent extends java.util.EventObject{
    private String errorMsg="";
    private int code=0;
    private SDManager manager=null;

    /**
     * The constructor of this class.
     * @param manager Reference to the send event manager.
     * @param code Code of event according to SDListener values
     * @param errorMsg An explanatory message if failed. If there isn't
     * an associated message, give empty string (no null)
     */
    public SDEvent(SDManager manager, int code, String errorMsg){
        super(manager);
        this.code=code;
        if(errorMsg==null)
            this.errorMsg="";
        else
            this.errorMsg=errorMsg;
    }

    /**
     * A default constructor of tis class.
     * 
     * Set the COMPLETE code os the event and not message.
     * @param manager Reference to the manager sending event.
     */
    public SDEvent(SDManager manager){
        this(manager,SDListener.COMPLETED,"");
    }

    /**
     * This is a convenient method that is equivalent to (SDManager)SDEvent.getSource()
     * @return The SDManager that thrown this event
     */
    public SDManager getManager(){ return (SDManager)getSource(); }

    /**
     * This methods return the associated message of the event.
     * @return The error message of the event. It can not be null.
     */
    public String getErrorMsg(){ return errorMsg; }

    /**
     * This methos returns the code of the event.
     * @return The code of the event.
     */
    public int getCode(){ return code; }
}
