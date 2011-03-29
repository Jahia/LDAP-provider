/**
 * Jahia Enterprise Edition v6.5
 *
 * Copyright (C) 2002-2011 Jahia Solutions Group. All rights reserved.
 *
 * Jahia delivers the first Open Source Web Content Integration Software by combining Enterprise Web Content Management
 * with Document Management and Portal features.
 *
 * The Jahia Enterprise Edition is delivered ON AN "AS IS" BASIS, WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED.
 *
 * Jahia Enterprise Edition must be used in accordance with the terms contained in a separate license agreement between
 * you and Jahia (Jahia Sustainable Enterprise License - JSEL).
 *
 * If you are unsure which license is appropriate for your use, please contact the sales department at sales@jahia.com.
 */

package org.jahia.params.valves;

import org.apache.commons.codec.binary.Base64;
import org.jahia.pipelines.PipelineException;
import org.jahia.pipelines.valves.ValveContext;
import org.jahia.registries.ServicesRegistry;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.JahiaUserManagerLDAPProvider;
import org.jahia.services.usermanager.JahiaUserManagerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.Iterator;
import java.util.List;

/**
 * User: toto
 */
public class LemonLdapAuthValveImpl extends BaseAuthValve {
    private static final transient Logger logger = LoggerFactory
            .getLogger(LemonLdapAuthValveImpl.class);

    public void invoke(Object context, ValveContext valveContext) throws PipelineException {
        if (!isEnabled()) {
            valveContext.invokeNext(context);
            return;
        }

        AuthValveContext authContext = (AuthValveContext) context;
        HttpServletRequest request = authContext.getRequest();
        String auth = request.getHeader("Authorization");
        if (auth != null) {
            try {
                logger.debug("Header found : "+auth);
                auth = auth.substring(6).trim();
                Base64 decoder = new Base64();
                String cred = new String(decoder.decode(auth.getBytes("UTF-8")),"UTF-8");
                int colonInd = cred.indexOf(':');
                String dn = cred.substring(0,colonInd);
                String prof = cred.substring(colonInd+1);

                logger.debug("Looking for dn "+dn);

                List<? extends JahiaUserManagerProvider> v = ServicesRegistry.getInstance().getJahiaUserManagerService().getProviderList();
                for (Iterator<? extends JahiaUserManagerProvider> iterator = v.iterator(); iterator.hasNext();) {
                    JahiaUserManagerProvider userManagerProviderBean = iterator.next();
                    if (userManagerProviderBean.getClass().getName().equals(JahiaUserManagerLDAPProvider.class.getName())) {
                        JahiaUserManagerLDAPProvider jahiaUserManagerLDAPProvider = (JahiaUserManagerLDAPProvider)userManagerProviderBean;
                        JahiaUser jahiaUser = jahiaUserManagerLDAPProvider.lookupUserFromDN(dn);
                        if (jahiaUser != null) {
                            if (isAccounteLocked(jahiaUser)) {
                                logger.debug("Login failed. Account is locked for user " + dn);
                                return;
                            }
                            logger.debug("DN found in ldap provider, user authenticated");
                            authContext.getSessionFactory().setCurrentUser(jahiaUser);
                            request.setAttribute("lemonldap.profile", prof);
                        }
                        return;
                    }
                }
            } catch (Exception e) {
                logger.debug("Exception thrown",e);
            }
        } else {
            logger.debug("No authorization header found");
        }
        valveContext.invokeNext(context);
    }

}
