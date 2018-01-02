/**
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *                                 http://www.jahia.com
 *
 *     Copyright (C) 2002-2018 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ==================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ===================================================================================
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

import org.jahia.services.cache.CacheHelper;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.io.IOException;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

public class JahiaLDAPConfigFactory implements ManagedServiceFactory, ApplicationContextAware {

    private static Logger logger = LoggerFactory.getLogger(JahiaLDAPConfigFactory.class);
    
    private ConfigurationAdmin configurationAdmin;

    private ApplicationContext context;

    private Map<String, JahiaLDAPConfig> ldapConfigs = new HashMap<String, JahiaLDAPConfig>();
    private Map<String, String> pidsByProviderKey = new HashMap<String, String>();

    public void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    public void start() {
        // do nothing
    }

    public void stop() {
        for (JahiaLDAPConfig config : ldapConfigs.values()) {
            config.unregister();
        }
        ldapConfigs.clear();
    }


    @Override
    public void updated(String pid, Dictionary<String, ?> dictionary) throws ConfigurationException {
        JahiaLDAPConfig ldapConfig;
        if (ldapConfigs.containsKey(pid)) {
            ldapConfig = ldapConfigs.get(pid);
        } else {
            ldapConfig = new JahiaLDAPConfig(dictionary);
            ldapConfigs.put(pid, ldapConfig);
            deleteConfig(pidsByProviderKey.put(ldapConfig.getProviderKey(), pid));
        }
        ldapConfig.setContext(context, dictionary);
        flushRelatedCaches();
    }

    private void deleteConfig(String pid) {
        if (pid == null) {
            return;
        }
        try {
            Configuration cfg = configurationAdmin.getConfiguration(pid);
            if (cfg != null) {
                cfg.delete();
            }
        } catch (IOException e) {
            logger.error("Unable to delete LDAP configuration for pid " + pid, e);
        }
    }

    @Override
    public void deleted(String pid) {
        JahiaLDAPConfig ldapConfig = ldapConfigs.remove(pid);
        String existingPid = ldapConfig != null ? pidsByProviderKey.get(ldapConfig.getProviderKey()) : null;
        if (existingPid != null && existingPid.equals(pid)) {
            pidsByProviderKey.remove(ldapConfig.getProviderKey());
            ldapConfig.unregister();
            flushRelatedCaches();
        }
    }

    public String getName() {
        return "org.jahia.services.usermanager.ldap";
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    public String getConfigPID(String providerKey) {
        return pidsByProviderKey.get(providerKey);
    }

    private void flushRelatedCaches() {
        CacheHelper.flushEhcacheByName("org.jahia.services.usermanager.JahiaUserManagerService.userPathByUserNameCache", true);
        CacheHelper.flushEhcacheByName("org.jahia.services.usermanager.JahiaGroupManagerService.groupPathByGroupNameCache", true);
        CacheHelper.flushEhcacheByName("org.jahia.services.usermanager.JahiaGroupManagerService.membershipCache", true);
        CacheHelper.flushEhcacheByName("LDAPUsersCache", true);
        CacheHelper.flushEhcacheByName("LDAPGroupsCache", true);
    }
}
