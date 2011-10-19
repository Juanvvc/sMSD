package org.msd.cache;

import org.msd.cache.Element;

/** Represents a Class of a service.
 * @version $Revision: 1.1.1.1 $
 * @date $Date: 2005-02-15 14:59:22 $ */
public class ClassType extends Element {
    public ClassType(Cache c, boolean add){ super(c,add); }
    public String getTypeName(){ return "classtype"; }
    public int getType(){ return CLASSTYPE; }
}
