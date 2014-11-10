/**
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *     Copyright (C) 2002-2014 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ======================================================================================
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
 *     describing the FLOSS exception, also available here:
 *     http://www.jahia.com/license"
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ======================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 *
 *
 * ==========================================================================================
 * =                                   ABOUT JAHIA                                          =
 * ==========================================================================================
 *
 *     Rooted in Open Source CMS, Jahia’s Digital Industrialization paradigm is about
 *     streamlining Enterprise digital projects across channels to truly control
 *     time-to-market and TCO, project after project.
 *     Putting an end to “the Tunnel effect”, the Jahia Studio enables IT and
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
 */
package org.jahia.services.usermanager.ldap;

import org.jahia.modules.external.users.ExternalUserGroupService;
import org.jahia.modules.external.users.UserGroupProviderConfiguration;
import org.jahia.settings.SettingsBean;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.webflow.core.collection.ParameterMap;

import java.io.File;
import java.io.IOException;
import java.util.Dictionary;
import java.util.Properties;

/**
 * Class to implement specific behaviour for configuration creation/edition/deletion in server settings
 */
public class LdapProviderConfiguration implements UserGroupProviderConfiguration {

    private static Logger logger = LoggerFactory.getLogger(LdapProviderConfiguration.class);

    private String userGroupProviderClass;
    private ExternalUserGroupService externalUserGroupService;
    private JahiaLDAPConfigFactory jahiaLDAPConfigFactory;
    private ConfigurationAdmin configurationAdmin;

    @Override
    public String getName() {
        return "LDAP";
    }

    @Override
    public boolean isCreateSupported() {
        return true;
    }

    @Override
    public String getCreateJSP() {
        return "/modules/ldap/userGroupProviderConfig.jsp";
    }

    @Override
    public boolean create(ParameterMap parameters) {
        return true;
    }

    @Override
    public boolean isEditSupported() {
        return true;
    }

    @Override
    public String getEditJSP() {
        return "/modules/ldap/userGroupProviderConfig.jsp";
    }

    @Override
    public boolean edit(String providerKey, ParameterMap parameters) {
        String pid = jahiaLDAPConfigFactory.getConfigPID(providerKey);
        if (pid == null) {
            return false;
        }
        String[] propKeys = parameters.getArray("propKey");
        String[] propValues = parameters.getArray("propValue");
        if (propKeys == null || propValues == null) {
            return false;
        }
        try {
            Configuration configuration = configurationAdmin.getConfiguration(pid);
            Dictionary properties = new Properties();
            for (int i = 0; i < propKeys.length; i++) {
                properties.put(propKeys[i], propValues[i]);
            }
            configuration.update(properties);
        } catch (IOException e) {
            logger.error("Error while trying to update configuration for " + providerKey, e);
            return false;
        }

        String configName = null;
        if (providerKey.equals("ldap")) {
            configName = jahiaLDAPConfigFactory.getName() + "-config.cfg";
        } else if (providerKey.startsWith("ldap.")) {
            configName = jahiaLDAPConfigFactory.getName() + "-" + providerKey.substring("ldap.".length()) + ".cfg";
        }
        if (configName != null) {
            File file = new File(SettingsBean.getInstance().getJahiaModulesDiskPath() + File.separatorChar + configName);
            if (file.exists()) {
                file.delete();
            }
        }
        return true;
    }

    @Override
    public boolean isDeleteSupported() {
        return true;
    }

    @Override
    public boolean delete(String providerKey) {
        String pid = jahiaLDAPConfigFactory.getConfigPID(providerKey);
        if (pid == null) {
            return false;
        }
        try {
            Configuration configuration = configurationAdmin.getConfiguration(pid);
            configuration.delete();
        } catch (IOException e) {
            logger.error("Error while trying to delete configuration for " + providerKey, e);
            return false;
        }

        String configName = null;
        if (providerKey.equals("ldap")) {
            configName = jahiaLDAPConfigFactory.getName() + "-config.cfg";
        } else if (providerKey.startsWith("ldap.")) {
            configName = jahiaLDAPConfigFactory.getName() + "-" + providerKey.substring("ldap.".length()) + ".cfg";
        }
        if (configName != null) {
            File file = new File(SettingsBean.getInstance().getJahiaModulesDiskPath() + File.separatorChar + configName);
            if (file.exists()) {
                file.delete();
            }
        }
        return true;
    }

    public void init() {
        externalUserGroupService.setConfiguration(userGroupProviderClass, this);
    }

    public void setUserGroupProviderClass(String userGroupProviderClass) {
        this.userGroupProviderClass = userGroupProviderClass;
    }

    public void setExternalUserGroupService(ExternalUserGroupService externalUserGroupService) {
        this.externalUserGroupService = externalUserGroupService;
    }

    public void setJahiaLDAPConfigFactory(JahiaLDAPConfigFactory jahiaLDAPConfigFactory) {
        this.jahiaLDAPConfigFactory = jahiaLDAPConfigFactory;
    }

    public void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }
}
