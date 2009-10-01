package org.chi.persistence.web;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.util.HashMap;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.net.BCodec;
import org.chi.persistence.ParseException;
import org.chi.persistence.RelaxSchema;
import org.chi.persistence.SchemaLoader;
import org.chi.persistence.XmlSerializer;
import org.chi.persistence.util.UpdateThread;
import org.chi.util.ArrayUtils;
import org.chi.util.ElectricUtil;
import org.chi.util.Log;
import org.chi.util.StringUtil;
import org.chi.web.AbstractApplicationContext;
import org.chi.web.AbstractResourceContext;
import org.chi.web.PageClassCall;

import electric.xml.Document;
import electric.xml.Element;
import electric.xml.Elements;
import electric.xml.NodeReader;

/**
 * Page class call wrapped around persistence mechanism
 * @author rgrey
 */
public class Loader implements PageClassCall {
    
    public static final int MAX_PARAMS_LEN = 100;

    private static final Class<?> schema = RelaxSchema.class;
    private static final BCodec decode = new BCodec();
    private static HashMap<String, XmlSerializer> instances;

    private AbstractResourceContext rc;

    public void setApplicationContext(AbstractApplicationContext appCtx) {
        if (instances == null) {
            String sep = File.separator;
            String dir = appCtx.getAppWebDir() + sep + ".." + sep;
            loadStores(dir, appCtx.getConfigElement("persistence"));
        }
    }

    public void setResourceContext(AbstractResourceContext resCtx) {
        this.rc = resCtx;
    }

    /**
     * Complete a database query
     * @param request
     * @param post_e
     * @return
     */
    public Element doQuery(HttpServletRequest request, Element post_e) {
        String msgName = "Loader.doQuery";
        long timing = System.currentTimeMillis();
        Element[] q = ElectricUtil.getAllElementsNamed(rc.getPagePost(), "query");
        Log.debug(msgName + "() : executing " + q.length + " query/queries");
        for (Element next : q) {
            XmlSerializer persist = getPersistence(next);
            if (persist == null) continue;
            String query = next.getAttribute("query");
            if (StringUtil.isEmpty(query)) continue;
            String paramKey = next.getAttribute("param-key");
            if (StringUtil.isEmpty(paramKey)) paramKey = "param";
            Log.debug(msgName + "() : parameter key [" + paramKey + "]");
            String[] params = rc.getParameters(paramKey);
            if (params.length > MAX_PARAMS_LEN) {
                Log.error("Max param length exceeded");
                return null; 
            }
            if (ArrayUtils.isEmpty(params)) params = new String[0];
            Element parent = next.getParentElement();
            try {
                timing = Log.timing(msgName, timing, "setup");
                Log.debug(msgName + "() : execute [" + query + "]");
                String result = persist.sqlLoad(query, params);
                timing = Log.timing(msgName, timing, "load");
                if (StringUtil.isEmpty(result)) continue;
                NodeReader reader = new NodeReader(new StringReader(result));
                Document doc = new Document();
                doc.read(reader);
                parent.replaceChild(doc.getRoot(), next);
                timing = Log.timing(msgName, timing, "update");
            } catch (Exception e) {
                Log.error("Unable to complete query");
                parent.replaceChild(handleException(e), next);
            }
        }
        return null;
    }

    /**
     * Remove an element from the database
     * @param request
     * @param post_e
     * @return if necessary, error message
     */
    public Element doRemove(HttpServletRequest request, Element post_e) {
        XmlSerializer persist = getPersistence(post_e);
        try {
            String t = request.getParameter("type");
            Serializable[] sids = request.getParameterValues("id");
            if (StringUtil.isEmpty(t) || ArrayUtils.isEmpty(sids)) return null;
            Serializable[] ids = new Serializable[sids.length];
            for (int x = 0 ; x < ids.length ; x++) {
                ids[x] = (Serializable) new Long(sids[x].toString());
            }
            persist.remove(t, ids);
        } catch (Exception e) {
            Log.error("Unable to remove");
            return handleException(e);
        }
        return null;
    }
    
    /**
     * Create new elements in the database
     * @param request
     * @param post_e
     * @return
     */
    public Element doSave(HttpServletRequest request, Element post_e) {
        String msgName = "Loader.doSave";
        long timing = System.currentTimeMillis();
        XmlSerializer persist = getPersistence(post_e);
        timing = Log.timing(msgName, timing, "getPersistence");
        if (persist == null) return null;
        try {
            Element[] elements = extractXmlFromRequest(post_e);
            timing = Log.timing(msgName, timing, "extract");
            if (ArrayUtils.isEmpty(elements)) return null;
            StringBuilder build = new StringBuilder();
            for (Element element : elements) {
                build.append(persist.save(element.toString()));
            }
            timing = Log.timing(msgName, timing, "save");
            String result = build.toString();
            if (StringUtil.isEmpty(result)) return null;
            post_e.removeChildren();
            Element[] children = extractChildren(result);
            timing = Log.timing(msgName, timing, "extract");
            if (children.length == 1) return children[0];
            for (Element child : extractChildren(result)) {
                post_e.appendChild(child);
            }
            return null;
        } catch (Exception e) {
            Log.error("Unable to save new element");
            return handleException(e);
        }
    }

    /**
     * Copy an existing element and children
     * @param request
     * @param post_e
     * @return new object
     */
    public Element doCopy(HttpServletRequest request, Element post_e) {
        XmlSerializer persist = getPersistence(post_e);
        try {
            String t = request.getParameter("type");
            String id = request.getParameter("id");
            if (StringUtil.isEmpty(t) || StringUtil.isEmpty(id)) return null;
            Long lid = Long.parseLong(id);
            Element[] results = extractChildren(persist.copy(t, lid));
            return results.length > 0 ? results[0] : null;
        } catch (Exception e) {
            Log.error("Unable to copy");
            return handleException(e);
        }
    }

    /**
     * Extract the validation rules for a schema
     * @param request
     * @param post_e
     * @return
     */
    public Element doValidateRules(HttpServletRequest request, Element post_e) {
        String _class = post_e.getAttribute("class");
        if (StringUtil.isEmpty(_class)) return null;
        XmlSerializer persist = getPersistence(post_e);
        Object s = persist.getDomainClassField(_class, "validationStr");
        if (s == null) return null;
        try {
            NodeReader reader = new NodeReader(new StringReader(s.toString()));
            Document doc = new Document();
            doc.read(reader);
            return doc.getRoot();
        } catch (Exception e) {
            Log.exception("Unable to load validation rules", e);
            return null;
        }
    }

    /**
     * Extract a parameter as the XML body
     * @param request
     * @param post_e
     * @return
     */
    public Element doExtract(HttpServletRequest request, Element post_e) {
        try {
            Element[] elements = extractXmlFromRequest(post_e);
            if (elements == null) return null;
            if (elements.length == 1) return elements[0];
            Element result = new Element("wrapper");
            for (Element next : elements) result.appendChild(next);
            return result;
        } catch (Exception e) {
            Log.exception("Unable to extract XML", e);
            return null;
        }
    }
    
    /**
     * Load all the persistent stores. This must be called by some mechanism
     * for the store systems to function
     * @param topDir 
     * @param cfg
     */
    public static synchronized void loadStores(String topDir, Element cfg) {
        if (instances != null || cfg == null) return;
        instances =  new HashMap<String, XmlSerializer>();
        for (Element scfg : ElectricUtil.getAllElementsNamed(cfg, "store")) {
            String id = scfg.getAttribute("id");
            Log.console("Loader.loadStores() : load [" + id + "]");
            Element hcfg = scfg.getElement("hibernate-config");
            Element sdir = scfg.getElement("schema-dir");
            Element qfile = scfg.getElement("query-file");
            if (hcfg == null || scfg == null || qfile == null) {
                Log.error("Incorrect store configuration");
                continue;
            }
            // check to see if this should be loaded as a flat file
            String qreal = qfile.getString();
            if (new File(topDir + "/" + qreal).exists()) qreal = topDir + "/" + qreal;
            try {
                SchemaLoader sl = new SchemaLoader(hcfg.getString(), 
                        topDir + sdir.getString(), schema);
                XmlSerializer serial = sl.load(qreal, true);
                UpdateThread.start(serial);
                instances.put(id, serial);
            } catch (Exception e) {
                Log.exception("Unable to load xml serializer", e);
            }
        }
    }

    /**
     * Get a loaded store
     * @param key
     * @return
     */
    public static synchronized XmlSerializer getStore(String key) {
        return instances != null ? instances.get(key) : null;
    }

    /**
     * Load an {@link XmlSerializer}
     * @param post_e
     * @return
     */
    private XmlSerializer getPersistence(Element post_e) {
        String store = post_e.getAttribute("store");
        if (StringUtil.isEmpty(store)) {
            Log.console("Loader.getPersistence(): No store att on query tag");
            return null;
        }
        XmlSerializer xmls = instances.get(store);
        if (xmls == null) Log.error("Bad store id [" + store + "]");
        return xmls;
    }
    
    /**
     * Extract XML from the HTTP request
     * @param post 
     * @return
     * @throws DecoderException
     * @throws IOException 
     */
    private Element[] extractXmlFromRequest(Element post) 
            throws DecoderException, IOException {
        // try the submitted parameters
        String content = rc.getParameter("xml");
        if (! StringUtil.isEmpty(content)) {
            Log.debug("Loader.extractXml() : encoded xml [" + content + "]");
            String dec = decode.decode(content);
            Log.debug("Loader.extractXml() : decoded xml [" + dec + "]");
            return extractChildren(dec);
        }
        // try to extract the post content
        Elements kids = post.getElements();
        if (kids.size() > 0) return genArray(kids);
        // couldn't find anything
        Log.error("Unable to extract XML");
        return null;
    }
    
    /**
     * Generate an exception XML element
     * @param e
     * @return
     */
    private Element handleException(Exception e) {
        Element message = new Element("message");
        Element result = new Element("error");
        Element type = new Element("type");
        message.setText(e.getMessage());
        type.setText(e.getClass().getCanonicalName());
        result.addChild(type);
        result.addChild(message);
        if (! (e instanceof ParseException)) 
            Log.exception("Unable to process persistence request", e);
        return result;
    }
    
    /**
     * Convenience
     * @param kids
     * @return
     */
    private Element[] genArray(Elements kids) {
        Element[] result = new Element[kids.size()];
        for (int x = 0 ; kids.hasMoreElements() ; x++) result[x] = kids.next();
        return result;
    }
    
   /**
    * Extract one or more children from a block of XML
    * @param content
    * @return
    * @throws IOException 
    */
    private Element[] extractChildren(String content) throws IOException {
       content = "<main>" + content + "</main>";
       NodeReader reader = new NodeReader(new StringReader(content));
       Document doc = new Document();
       doc.read(reader);
       return genArray(doc.getRoot().getElements());
    }

}
