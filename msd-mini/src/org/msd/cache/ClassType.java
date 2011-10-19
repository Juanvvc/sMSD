package org.msd.cache;

import org.msd.cache.Element;

/** Represents a Class of a service.
 * @version $Revision: 1.2 $ */
public class ClassType extends Element{
    public ClassType(Cache c,boolean add){
        super(c,add);
    }

    public String getTypeName(){
        return "classtype";
    }

    public int getType(){
        return CLASSTYPE;
    }
}
