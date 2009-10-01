package org.chi.persistence.web;

import java.util.List;

import org.chi.db.auth.ISecurity;
import org.chi.persistence.XmlSerializer;
import org.chi.util.Log;
import org.chi.util.StringUtil;
import org.chi.web.SecurityRequest;
import org.chi.web.impl.DefaultApplicationContext;
import org.chi.web.impl.DefaultResourceContext;
import org.chi.web.impl.DefaultSecurityContext;

import electric.xml.Element;

/**
 * Security option for persistence
 * @author rgrey
 */
public abstract class PersistenceSecurity implements ISecurity {

    private DefaultResourceContext rc;
    
    /**
     * Default
     * @param ctx
     * @param rc
     */
    public PersistenceSecurity(
            DefaultApplicationContext ctx, DefaultResourceContext rc) {
        this.rc = rc;
    }
    
    /**
     * Get the store associated with security
     * @return
     */
    public abstract String getStoreName();
    
    /**
     * Extract a user XML element from the persistent store
     * @param user
     * @param pass
     * @param xmls
     * @return
     */
    public abstract Element getUserElement(String user, String pass, 
            XmlSerializer xmls);
    
    /**
     * Extract the groups from the user XML element
     * @param userElement
     * @return
     */
    public abstract List<String> getGroups(Element userElement);

    public SecurityRequest checkAuthorization(SecurityRequest srq) {
        srq.authorized = false;
        Log.debug("PersistenceSecurity.checkAuthorization () : pgid [" + 
                srq.pgid + "] username [" + srq.user + "] password [****]" + 
                " submit [" + srq.submit + "] store [" + getStoreName() + "]");
        if (StringUtil.isEmpty(srq.user)) { 
            srq.setReasonForCode(SecurityRequest.REASON_NO_EMAIL);
            return srq;
        }
        if (StringUtil.isEmpty(srq.pass)) {
            srq.setReasonForCode(SecurityRequest.REASON_NO_PASSWORD);
            return srq;
        }
        XmlSerializer xmls = Loader.getStore(getStoreName());
        if (xmls == null) {
            srq.setReasonForCode(SecurityRequest.REASON_SYSTEM_ERROR);
            return srq;
        }
        Element userElement = getUserElement(srq.user, srq.pass, xmls);
        if (userElement == null) {
            srq.setReasonForCode(SecurityRequest.REASON_INVALID_PASSWORD);
            return srq;
        }
        List<String> groups = getGroups(userElement);
        if (groups == null) {
            srq.setReasonForCode(SecurityRequest.REASON_GROUP_NOT_AUTHORIZED);
            return srq;
        }
        DefaultSecurityContext secCtx = 
            (DefaultSecurityContext) rc.getSecurityContext();
        for (String next : secCtx.getAuthGroups()) {
            if (groups.contains(next)) srq.authorized = true;
        }
        if (! srq.authorized) 
            srq.setReasonForCode(SecurityRequest.REASON_GROUP_NOT_AUTHORIZED);
        srq.userid = srq.user;
        if (userElement.hasAttribute("id")) 
            srq.usernum = userElement.getAttribute("id");
        srq.userGroups = groups.toArray(new String[0]);
        return srq;
    }

}
