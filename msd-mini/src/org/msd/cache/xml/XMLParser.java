package org.msd.cache.xml;

import org.msd.cache.*;

/** An interface to the XML parser.
 * This parser can be an actual parser using the JDOM or SAX method or
 * a simple one. This is a convenient way for avoiding an complete library
 * for parsing XML files in little devices.
 *
 * A default, simple class implementing this interface and just managing
 * and raw processing the input stream should be provided. A complete XML
 * parser using the libraries from javax.xml and org.w3c can be provided, but
 * it is nor mandatory. */
public interface XMLParser {
    /** Load a cache from the xml.
     * The content of the cache is deleted before loading the file.
     * @param cache The cache to be loaded.
     * @param xml The XML of the cache.
     * @throws Exception If the cache can not be loaded */
    public void load(Cache cache,String xml) throws Exception;
    /** Updates a cache from an XML.
     * @param cache The cache to be updated.
     * @param xml the XML of the updating cache.
     * @throws Exception If the cache can not be updated. */
    public void update(Cache cache, String xml) throws Exception;
    /** @param xml The XML of an element.
     * @param cache The cache containing the element.
     * @returns A new element for this XML.
     * @throws Exception If the element can not be created */
    public Element createElementFromXML(Cache cache, String xml) throws Exception;
    /** @return Stores A cache in a string.
     * @param cache The cache to be returned as a string. */
    public String toString(Cache cache);
    /** @return Stores an element in a string.
     * @param e The element to be returned as a string. */
    public String toString(Element e);
}
