/**
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *     Copyright (C) 2002-2015 Jahia Solutions Group SA. All rights reserved.
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
 */
package org.jahia.services.usermanager.ldap;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.modules.external.users.ExternalUserGroupService;
import org.jahia.modules.external.users.UserGroupProviderConfiguration;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.settings.SettingsBean;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.springframework.core.NestedCheckedException;
import org.springframework.core.NestedRuntimeException;
import org.springframework.ldap.core.support.LdapContextSource;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Dictionary;
import java.util.Map;
import java.util.Properties;

/**
 * Class to implement specific behaviour for configuration creation/edition/deletion in server settings
 */
public class LdapProviderConfiguration implements UserGroupProviderConfiguration {

    private static final long serialVersionUID = 8082529526561969689L;
    
    private static Exception getRootCause(Exception e) {
        Throwable cause = null;
        if (e instanceof NestedCheckedException) {
            cause = ((NestedCheckedException) e).getMostSpecificCause();
        } else if (e instanceof NestedRuntimeException) {
            cause = ((NestedRuntimeException) e).getMostSpecificCause();
        } else {
            Throwable t = e;
            while (t.getCause() != null) {
                t = t.getCause();
            }
            cause = t;
        }
        return (cause instanceof Exception) ? (Exception) cause : new RuntimeException(cause);
    }

    private static String getValue(Properties properties, String... keys) {
        String value = null;
        for (String k : keys) {
            value = properties.getProperty(k);
            if (value != null) {
                break;
            }
        }
        
        return value;
    }
    
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
        return "/modules/ldap/userGroupProviderCreate.jsp";
    }

    @Override
    public String create(Map<String, Object> parameters, Map<String, Object> flashScope) throws Exception {
        String[] propKeys = (String[]) parameters.get("propKey");
        String[] propValues = (String[]) parameters.get("propValue");
        if (propKeys == null || propValues == null) {
            throw new Exception("No property has been set");
        }
        Properties properties = new Properties();
        for (int i = 0; i < propKeys.length; i++) {
            String propValue = propValues[i];
            if (StringUtils.isNotBlank(propValue)) {
                properties.put(propKeys[i], propValue);
            }
        }
        flashScope.put("ldapProperties", properties);
        String configName = (String) parameters.get("configName");
        if (configName != null) {
            configName = JCRContentUtils.generateNodeName(configName);
        }
        flashScope.put("configName", configName);
        if (!testConnection(properties)) {
            throw new Exception("Connection to the LDAP server impossible");
        }
        String providerKey;
        if (StringUtils.isBlank(configName)) {
            providerKey = "ldap";
            configName = jahiaLDAPConfigFactory.getName() + "-config.cfg";
        } else {
            providerKey = "ldap." + configName;
            configName = jahiaLDAPConfigFactory.getName() + "-" + configName + ".cfg";
        }
        File file = new File(SettingsBean.getInstance().getJahiaModulesDiskPath());
        if (file.exists()) {
            FileOutputStream out = new FileOutputStream(new File(file, configName));
            try {
                properties.store(out, "");
            } finally {
                IOUtils.closeQuietly(out);
            }
        } else {
            String pid = jahiaLDAPConfigFactory.getConfigPID(providerKey);
            if (pid != null) {
                throw new Exception("An LDAP provider with key '" + providerKey + "' already exists");
            }
            Configuration configuration = configurationAdmin.createFactoryConfiguration(jahiaLDAPConfigFactory.getName());
            properties.put(JahiaLDAPConfig.LDAP_PROVIDER_KEY_PROP, providerKey);
            configuration.update((Dictionary) properties);
        }
        return providerKey;
    }

    @Override
    public boolean isEditSupported() {
        return true;
    }

    @Override
    public String getEditJSP() {
        return "/modules/ldap/userGroupProviderEdit.jsp";
    }

    @Override
    public void edit(String providerKey, Map<String, Object> parameters, Map<String, Object> flashScope) throws Exception {
        String[] propKeys = (String[]) parameters.get("propKey");
        String[] propValues = (String[]) parameters.get("propValue");
        if (propKeys == null || propValues == null) {
            throw new Exception("No property has been set");
        }
        Properties properties = new Properties();
        for (int i = 0; i < propKeys.length; i++) {
            String propValue = propValues[i];
            if (StringUtils.isNotBlank(propValue)) {
                properties.put(propKeys[i], propValue);
            }
        }
        flashScope.put("ldapProperties", properties);
        if (!testConnection(properties)) {
            throw new Exception("Connection to the LDAP server impossible");
        }
        String configName;
        if (providerKey.equals("ldap")) {
            configName = jahiaLDAPConfigFactory.getName() + "-config.cfg";
        } else if (providerKey.startsWith("ldap.")) {
            configName = jahiaLDAPConfigFactory.getName() + "-" + providerKey.substring("ldap.".length()) + ".cfg";
        } else {
            throw new Exception("Wrong LDAP provider key: " + providerKey);
        }
        File file = new File(SettingsBean.getInstance().getJahiaModulesDiskPath(), configName);
        if (file.exists()) {
            FileOutputStream out = new FileOutputStream(file);
            try {
                properties.store(out, "");
            } finally {
                IOUtils.closeQuietly(out);
            }
        } else {
            String pid = jahiaLDAPConfigFactory.getConfigPID(providerKey);
            if (pid == null) {
                throw new Exception("Cannot find LDAP provider " + providerKey);
            }
            Configuration configuration = configurationAdmin.getConfiguration(pid);
            properties.put(JahiaLDAPConfig.LDAP_PROVIDER_KEY_PROP, providerKey);
            configuration.update((Dictionary) properties);
        }
    }

    @Override
    public boolean isDeleteSupported() {
        return true;
    }

    @Override
    public void delete(String providerKey, Map<String, Object> flashScope) throws Exception {
        String configName;
        if (providerKey.equals("ldap")) {
            configName = jahiaLDAPConfigFactory.getName() + "-config.cfg";
        } else if (providerKey.startsWith("ldap.")) {
            configName = jahiaLDAPConfigFactory.getName() + "-" + providerKey.substring("ldap.".length()) + ".cfg";
        } else {
            throw new Exception("Wrong LDAP provider key: " + providerKey);
        }
        File file = new File(SettingsBean.getInstance().getJahiaModulesDiskPath(), configName);
        if (file.exists()) {
            file.delete();
        } else {
            String pid = jahiaLDAPConfigFactory.getConfigPID(providerKey);
            if (pid == null) {
                throw new Exception("Cannot find LDAP provider " + providerKey);
            }
            Configuration configuration = configurationAdmin.getConfiguration(pid);
            configuration.delete();
        }
    }


    private boolean testConnection(Properties p) throws Exception {
        return testConnection(getValue(p, "url", "user.url", "group.url"),
                getValue(p, "public.bind.dn", "user.public.bind.dn", "group.public.bind.dn"),
                getValue(p, "public.bind.password", "user.public.bind.password", "group.public.bind.password"));
    }

    private boolean testConnection(String url, String bindDn, String bindPassword) throws Exception {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        LdapContextSource lcs = new LdapContextSource();
        lcs.setUrl(url);
        if (StringUtils.isNotBlank(bindDn)) {
            lcs.setUserDn(bindDn);
        }
        if (StringUtils.isNotBlank(bindPassword)) {
            lcs.setPassword(bindPassword);
        }
        try {
            lcs.afterPropertiesSet();
            lcs.getReadOnlyContext();
        } catch (Exception e) {
            throw getRootCause(e);
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