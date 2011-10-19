package org.msd.proxy;

import javax.swing.*;
import org.msd.comm.*;
import org.msd.Dialog;

/** This dialog manually asks if a message with hash code is valid.
 * The porpouse of thi class is to test the security of the system, manually
 * letting verified and not verified messages in the network. */
class AskValid{
    private boolean valid=true;
    public AskValid(Message m, CommManager c){
        JLabel l=new JLabel("A message from "+m.getIDFrom()+" is "+(c.validate(m)?"valid":"not valid")+". Mark as valid?");
        Dialog d=new Dialog("Is the message valid?",l,Dialog.OKCANCEL);
        valid=d.ok();
    }
    public boolean valid(){
        return valid;
    }
}
