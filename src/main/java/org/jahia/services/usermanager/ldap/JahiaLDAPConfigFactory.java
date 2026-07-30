/*
 * Copyright (C) 2002-2022 Jahia Solutions Group SA. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jahia.services.usermanager.ldap;

import org.jahia.modules.external.users.ExternalUserGroupService;
import org.jahia.services.cache.CacheHelper;
import org.jahia.services.usermanager.ldap.cache.LDAPCacheManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import org.jahia.services.usermanager.ldap.communication.LdapTemplateWrapper;

@Component(service = {JahiaLDAPConfigFactory.class, ManagedServiceFactory.class}, property = "service.pid=org.jahia.services.usermanager.ldap", immediate = true)
public class JahiaLDAPConfigFactory implements ManagedServiceFactory {

    private static Logger logger = LoggerFactory.getLogger(JahiaLDAPConfigFactory.class);

    private ConfigurationAdmin configurationAdmin;
    private LDAPCacheManager ldapCacheManager;
    private ExternalUserGroupService externalUserGroupService;
    private BundleContext bundleContext;

    private Map<String, JahiaLDAPConfig> ldapConfigs = new HashMap<String, JahiaLDAPConfig>();
    private Map<String, String> pidsByProviderKey = new HashMap<String, String>();

    @Reference(service = ConfigurationAdmin.class)
    public void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    @Reference(service = LDAPCacheManager.class)
    public void setLdapCacheManager(LDAPCacheManager ldapCacheManager) {
        this.ldapCacheManager = ldapCacheManager;
    }

    @Reference(service = ExternalUserGroupService.class)
    public void setExternalUserGroupService(ExternalUserGroupService externalUserGroupService) {
        this.externalUserGroupService = externalUserGroupService;
    }

    @Activate
    public void start(BundleContext context) {
       this.bundleContext = context;
    }

    @Deactivate
    public void stop() {
        for (JahiaLDAPConfig config : ldapConfigs.values()) {
            config.unregister();
        }
        ldapConfigs.clear();
    }


    @Override
    public void updated(String pid, Dictionary<String, ?> dictionary) throws ConfigurationException {
        // This class loader gymnastic is required, since Spring library is embedded we need to make sure Spring ldap is using
        // the current bundle class loader to load its classes
        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(JahiaLDAPConfigFactory.class.getClassLoader());
        try {
            JahiaLDAPConfig ldapConfig;
            if (ldapConfigs.containsKey(pid)) {
                ldapConfig = ldapConfigs.get(pid);
            } else {
                ldapConfig = new JahiaLDAPConfig(dictionary);
                ldapConfigs.put(pid, ldapConfig);
                deleteConfig(pidsByProviderKey.put(ldapConfig.getProviderKey(), pid));
            }
            ldapConfig.setContext(externalUserGroupService, ldapCacheManager, bundleContext, dictionary);
            flushRelatedCaches();
        } finally {
            Thread.currentThread().setContextClassLoader(oldClassLoader);
        }
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

    public LdapTemplateWrapper getLdapTemplateWrapper(String providerKey) {
        for (JahiaLDAPConfig jahiaLDAPConfig : ldapConfigs.values()) {
            if (jahiaLDAPConfig.getProviderKey().equals(providerKey)) {
                return jahiaLDAPConfig.getLdapUserGroupProvider().getLdapTemplateWrapper();
            }
        }
        return null;
    }
}
