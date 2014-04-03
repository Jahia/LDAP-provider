/**
 * ==========================================================================================
 * =                        DIGITAL FACTORY v7.0 - Community Distribution                   =
 * ==========================================================================================
 *
 *     Rooted in Open Source CMS, Jahia's Digital Industrialization paradigm is about
 *     streamlining Enterprise digital projects across channels to truly control
 *     time-to-market and TCO, project after project.
 *     Putting an end to "the Tunnel effect", the Jahia Studio enables IT and
 *     marketing teams to collaboratively and iteratively build cutting-edge
 *     online business solutions.
 *     These, in turn, are securely and easily deployed as modules and apps,
 *     reusable across any digital projects, thanks to the Jahia Private App Store Software.
 *     Each solution provided by Jahia stems from this overarching vision:
 *     Digital Factory, Workspace Factory, Portal Factory and eCommerce Factory.
 *     Founded in 2002 and headquartered in Geneva, Switzerland,
 *     Jahia Solutions Group has its North American headquarters in Washington DC,
 *     with offices in Chicago, Toronto and throughout Europe.
 *     Jahia counts hundreds of global brands and governmental organizations
 *     among its loyal customers, in more than 20 countries across the globe.
 *
 *     For more information, please visit http://www.jahia.com
 *
 * JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION
 * ============================================
 *
 *     Copyright (C) 2002-2014 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ==========================================================
 *
 *     IF YOU DECIDE TO CHOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     "This program is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation; either version 2
 *     of the License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *     As a special exception to the terms and conditions of version 2.0 of
 *     the GPL (or any later version), you may redistribute this Program in connection
 *     with Free/Libre and Open Source Software ("FLOSS") applications as described
 *     in Jahia's FLOSS exception. You should have received a copy of the text
 *     describing the FLOSS exception, and it is also available here:
 *     http://www.jahia.com/license"
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ==========================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 */
package org.jahia.services.usermanager.ldap;

import org.apache.commons.lang.StringUtils;
import org.jahia.exceptions.JahiaInitializationException;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class to configure LDAP user and group providers via OSGi Config Admin service.
 */
public class JahiaLDAPConfig {

    private static Logger logger = LoggerFactory.getLogger(JahiaLDAPConfig.class);

    private String providerKey;
    private Map<String, String> userLdapProperties;
    private Map<String, String> groupLdapProperties;
    private JahiaUserManagerLDAPProvider jahiaUserManagerLDAPProvider;
    private JahiaGroupManagerLDAPProvider jahiaGroupManagerLDAPProvider;

    public JahiaLDAPConfig(ApplicationContext context, Dictionary<String, ?> dictionary) {
        providerKey = computeProviderKey(dictionary);
        populate(context, dictionary);
    }

    public void populate(ApplicationContext context, Dictionary<String, ?> dictionary) {
        userLdapProperties = new HashMap<String, String>();
        groupLdapProperties = new HashMap<String, String>();
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
        if (!userLdapProperties.isEmpty()) {
            if (jahiaUserManagerLDAPProvider == null) {
                jahiaUserManagerLDAPProvider = (JahiaUserManagerLDAPProvider) context.getBean("JahiaUserManagerLDAPProvider");
                jahiaUserManagerLDAPProvider.setKey(providerKey);
            }

            jahiaUserManagerLDAPProvider.setLdapProperties(userLdapProperties);
            try {
                jahiaUserManagerLDAPProvider.initProperties();
            } catch (JahiaInitializationException e) {
                logger.error("Failed to initialize JahiaUserManagerLDAPProvider");
            }
        } else if (jahiaUserManagerLDAPProvider != null) {
            unregisterUserProvider();
        }
        if (!groupLdapProperties.isEmpty()) {
            if (jahiaGroupManagerLDAPProvider == null) {
                jahiaGroupManagerLDAPProvider = (JahiaGroupManagerLDAPProvider) context.getBean("JahiaGroupManagerLDAPProvider");
                jahiaGroupManagerLDAPProvider.setKey(providerKey);
                jahiaGroupManagerLDAPProvider.setUserProvider(jahiaUserManagerLDAPProvider);
            }

            jahiaGroupManagerLDAPProvider.setLdapProperties(groupLdapProperties);
            try {
                jahiaGroupManagerLDAPProvider.initProperties();
            } catch (JahiaInitializationException e) {
                logger.error("Failed to initialize JahiaGroupManagerLDAPProvider");
            }
        } else if (jahiaGroupManagerLDAPProvider != null) {
            unregisterGroupProvider();
        }
    }

    public void unregister() {
        if (jahiaUserManagerLDAPProvider != null) {
            unregisterUserProvider();
        }
        if (jahiaGroupManagerLDAPProvider != null) {
            unregisterGroupProvider();
        }
    }

    private void unregisterUserProvider() {
        jahiaUserManagerLDAPProvider.unregister();
        jahiaUserManagerLDAPProvider = null;
    }

    private void unregisterGroupProvider() {
        jahiaGroupManagerLDAPProvider.unregister();
        jahiaGroupManagerLDAPProvider = null;
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
