/* Manager event of discovery protocols */

package org.msd.proxy;
import org.msd.proxy.SDListener;

/** Keep information about managers protocols events.
 * @version $Revision: 1.2 $*/
public class SDEvent{
    private String errorMsg="";
    private int code=0;
    private SDManager manager=null;

    /** Constructor.
     * @param manager Reference to the send event manager.
     * @param code Code of event according to SDListener values
     * @param errorMsg An explanatory message if failed. If there isn't
     * an associated message, give empty string (no null) */
    public SDEvent(SDManager manager, int code, String errorMsg){
	    this.manager=manager;
        this.code=code;
        if(errorMsg==null)
            this.errorMsg="";
        else
            this.errorMsg=errorMsg;
    }

    /** Constructor.
     * Gives COMPLETED code and empty message by default.
     * @param manager Reference to the manager sending event. */
    public SDEvent(SDManager manager){
        this(manager,SDListener.COMPLETED,"");
    }

    /** Retrieve the manager who throwed the event.
     * It is a convenient method that is equivalent to
     * (SDManager)SDEvent.getSource() */
    public SDManager getManager(){ return manager; }

    /** Retrieve the associated error message.
     * This message can not be null */
    public String getErrorMsg(){ return errorMsg; }

    /** Retrieve the event code such as in SDListener is defined.
      */
    public int getCode(){ return code; }
}
