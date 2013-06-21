/**
 * This file is part of Jahia, next-generation open source CMS:
 * Jahia's next-generation, open source CMS stems from a widely acknowledged vision
 * of enterprise application convergence - web, search, document, social and portal -
 * unified by the simplicity of web content management.
 *
 * For more information, please visit http://www.jahia.com.
 *
 * Copyright (C) 2002-2013 Jahia Solutions Group SA. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * As a special exception to the terms and conditions of version 2.0 of
 * the GPL (or any later version), you may redistribute this Program in connection
 * with Free/Libre and Open Source Software ("FLOSS") applications as described
 * in Jahia's FLOSS exception. You should have received a copy of the text
 * describing the FLOSS exception, and it is also available here:
 * http://www.jahia.com/license
 *
 * Commercial and Supported Versions of the program (dual licensing):
 * alternatively, commercial and supported versions of the program may be used
 * in accordance with the terms and conditions contained in a separate
 * written agreement between you and Jahia Solutions Group SA.
 *
 * If you are unsure which license is appropriate for your use,
 * please contact the sales department at sales@jahia.com.
 */

package org.jahia.services.usermanager.ldap;

import org.apache.commons.lang.StringUtils;
import org.jahia.exceptions.JahiaInitializationException;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractApplicationContext;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class JahiaLDAPConfig {

    private static Logger logger = LoggerFactory.getLogger(JahiaLDAPConfig.class);

    private JahiaUserManagerLDAPProvider jahiaUserManagerLDAPProvider;

    private JahiaGroupManagerLDAPProvider jahiaGroupManagerLDAPProvider;

    public JahiaLDAPConfig(AbstractApplicationContext context, Dictionary<String, ?> dictionary) {
        String key = computeProviderKey(dictionary);
        jahiaUserManagerLDAPProvider = (JahiaUserManagerLDAPProvider) context.getBean("JahiaUserManagerLDAPProvider");
        jahiaUserManagerLDAPProvider.setKey(key);
        jahiaGroupManagerLDAPProvider = (JahiaGroupManagerLDAPProvider) context.getBean("JahiaGroupManagerLDAPProvider");
        jahiaGroupManagerLDAPProvider.setKey(key);
        populate(dictionary);
    }

    public void populate(Dictionary<String, ?> dictionary) {
        Map<String, String> userLdapProperties = new HashMap<String, String>();
        Map<String, String> groupLdapProperties = new HashMap<String, String>();
        Enumeration<String> keys = dictionary.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            if (Constants.SERVICE_PID.equals(key) ||
                    ConfigurationAdmin.SERVICE_FACTORYPID.equals(key) ||
                    "felix.fileinstall.filename".equals(key)) {
                continue;
            }
            String value = (String) dictionary.get(key);
            if (key.startsWith("user.")) {
                userLdapProperties.put(key.substring(5), value);
            } else if (key.startsWith("group.")) {
                groupLdapProperties.put(key.substring(6), value);
            } else {
                userLdapProperties.put(key, value);
                groupLdapProperties.put(key, value);
            }
        }
        jahiaUserManagerLDAPProvider.setLdapProperties(userLdapProperties);
        try {
            jahiaUserManagerLDAPProvider.initProperties();
        } catch (JahiaInitializationException e) {
            logger.error("Failed to initialize JahiaUserManagerLDAPProvider");
        }
        jahiaGroupManagerLDAPProvider.setLdapProperties(groupLdapProperties);
        try {
            jahiaGroupManagerLDAPProvider.initProperties();
        } catch (JahiaInitializationException e) {
            logger.error("Failed to initialize JahiaGroupManagerLDAPProvider");
        }
    }

    public void unregister() {
        jahiaUserManagerLDAPProvider.unregister();
        jahiaGroupManagerLDAPProvider.unregister();
    }

    private String computeProviderKey(Dictionary<String, ?> dictionary) {
        String filename = (String) dictionary.get("felix.fileinstall.filename");
        String factoryPid = (String) dictionary.get(ConfigurationAdmin.SERVICE_FACTORYPID);
        String confId;
        if (StringUtils.isBlank(filename)) {
            confId = (String) dictionary.get(Constants.SERVICE_PID);
            if (StringUtils.startsWith(confId, factoryPid + ".")) {
                confId = StringUtils.substringAfter(confId, factoryPid + ".");
            }
        } else {
            confId = StringUtils.removeEnd(StringUtils.substringAfter(filename,
                    factoryPid + "-"), ".cfg");
        }
        if (StringUtils.isBlank(confId) || "config".equals(confId)) {
            return "ldap";
        } else {
            return "ldap." + confId;
        }
    }
}
