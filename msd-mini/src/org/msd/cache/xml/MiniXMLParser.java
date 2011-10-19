package org.msd.cache.xml;

import org.msd.cache.*;
import java.util.Vector;
import java.util.Enumeration;

/** A simple implementation of the XML parser for J2ME devices
 * without XML support.
 *
 * Notice this class uses StringBuffer for optimitation. */
public class MiniXMLParser implements XMLParser{
    /** Load a cache from the xml.
     * The content of the cache is deleted before loading the file.
     * @param cache The cache to be loaded.
     * @param xml The XML of the cache.
     * @throws Exception If the cache can not be loaded */
    public void load(Cache cache,String xml){
        cache.reset();

        int p1=xml.indexOf(" idcache=\"");
	p1+=10;
        int p2=xml.indexOf("\"",p1);
        String idcache=xml.substring(p1,p2);
	cache.setID(idcache);
        
        update(cache,xml);
    }
    
    /** Updates a cache from an XML.
     * @param cache The cache to be updated.
     * @param xml the XML of the updating cache.
     * @throws Exception If the cache can not be updated. */
    public void update(Cache cache, String xml){
        StringTokenizer st=new StringTokenizer(xml,"<service");
        // remove the header: <?xml... and this staff, until the first service
        st.nextToken();
	while(st.hasMoreTokens()){
		try{
                    cache.addElement(createElementFromXML(cache,st.nextToken()));
		}catch(Exception e){
                    System.err.println(e.toString()+" in "+xml);
		}
	}
    }
    
    /** @param xml The XML of an element.
     * @param cache The cache containing the element.
     * @returns A new element for this XML.
     * @throws Exception If the element can not be created */
    public Element createElementFromXML(Cache cache, String xml) throws Exception{
            int main=0;
            if(main>-1){
                int p1=xml.indexOf(" idcache=\"",main);
		p1+=10;
                int p2=xml.indexOf("\"",p1);
                String idcache=xml.substring(p1,p2);
		
                p1=xml.indexOf(" id=\"",main);
		p1+=5;
                p2=xml.indexOf("\"",p1);
                String id=xml.substring(p1,p2);

                p1=xml.indexOf(" name=\"",main);
		p1+=7;
                p2=xml.indexOf("\"",p1);
                String name=xml.substring(p1,p2);

		Service s=new Service(cache,false);
		s.setName(name);
		s.setIDCache(idcache);
		s.setID(id);

                p1=xml.indexOf(" gw=\"",main);
		if(p1>-1){
			p1+=5;
        	        p2=xml.indexOf("\"",p1);
                	String gw=xml.substring(p1,p2);
			s.setGateway(gw);
		}
                
                p1=xml.indexOf(" confidence=\"",main);
                if(p1>-1){
                    p1+=13;
                    p2=xml.indexOf("\"",p1);
                    try{
                        s.setConfidence(Integer.parseInt(xml.substring(p1,p2)));
                    }catch(Exception e){                        
                    }
                }

                p1=xml.indexOf(" hops=\"",main);
                if(p1>-1){
                    p1+=7;
                    p2=xml.indexOf("\"",p1);
                    try{
                        s.setHops(Integer.parseInt(xml.substring(p1,p2)));
                    }catch(Exception e){                        
                    }
                }
                
		main=xml.indexOf("<network",main);
		while(main>-1){
			int end=xml.indexOf("</network",main);
			Network n=new Network(cache,false);

                	p1=xml.indexOf(" name=\"",main);
			if(p1<end && p1>-1){
				p1+=7;
        		        p2=xml.indexOf("\"",p1);
                		name=xml.substring(p1,p2);
				n.setName(name);
			}

                	p2=xml.indexOf(" name=\"url\"",main);
	                p1=xml.indexOf(">",p2);
        	        p2=xml.indexOf("<",p1);
                	String url=xml.substring(p1+1,p2);
			n.setURL(url);

                	p2=xml.indexOf(" name=\"port\"",main);
			if(p2>-1 && p2<end){
		                p1=xml.indexOf(">",p2);
        		        p2=xml.indexOf("<",p1);
				int p=Integer.parseInt(xml.substring(p1+1,p2));
				n.setPort(p);
			}

			s.appendChild(n);
			main++;
			main=xml.indexOf("<network",main);
		}

		return s;
	}else{
		throw new Exception("Not a service");
	}
    }
    
    /** @return Stores A cache in a string.
     @param cache The cache to be returned as a string. */
    public String toString(Cache cache){
        String idCache=cache.getID();
        Vector childs=cache.getChilds();
        StringBuffer xml=new StringBuffer("<?xml version=\"1.0\"?>\n");
        xml.append("<cache idcache=\"").append(idCache).append("\" mode=\"mini\">");
        for(Enumeration e=childs.elements();e.hasMoreElements();){
            xml.append(e.nextElement().toString());
        }
        xml.append("</cache>");
        return xml.toString();
    }
    
    /** @return Stores an element in a string.
    * @param cache The element to be returned as a string. */
    public String toString(Element e){
        StringBuffer xml= new StringBuffer("<");
        xml.append(e.getTypeName());
        if(e.getName()!=null){
            xml.append(" name=\"").append(e.getName()).append("\"");
        }
        if(e.getIDCache()!=null){
            xml.append(" idcache=\"").append(e.getIDCache()).
                    append("\" id=\"").append(e.getID()).append("\"");
        }
        for(Enumeration e2=e.getSpecialAttrs();e2.hasMoreElements();){
            String key=(String)e2.nextElement();
            String value=(String)e.getSpecialAttr(key);
            xml.append(" ").append(key).append("=\"").
                    append(value).append("\"");
        }
        xml.append(">");
        for(Enumeration e2=e.getAttribNames();e2.hasMoreElements();){
            String k=(String)e2.nextElement();
            String v=(String)e.getAttrStr(k);
            xml.append("<attr name=\"").append(k).append("\">").
                    append(v).append("</attr>");
        }
        for(Enumeration e2=e.getChilds().elements();e2.hasMoreElements();){
            xml.append(e2.nextElement().toString());
        }
        xml.append("</").append(e.getTypeName()).append(">");
        return xml.toString();
    }
}
