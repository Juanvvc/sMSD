package org.msd.cache;

import org.msd.cache.Element;

/** Represents an user.
 * This class is ot currently used anywhere. In the future, users will
 * be an entity to be stored, but not now. Consult the MSD document from INRIA.
 * @version $Revision: 1.2 $
 * @date $Date: 2005-04-05 17:50:09 $ */
public class User extends Element {
    public User(Cache c, boolean add){ super(c,add); }
    public String getTypeName(){ return "user"; }
    public int getType(){ return USER; }
}
