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
package org.jahia.services.usermanager.ldap.cache;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.cache.CacheHelper;
import org.jahia.services.cache.ModuleClassLoaderAwareCacheEntry;
import org.jahia.services.cache.ehcache.EhCacheProvider;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for LDAP provider related caches.
 *
 * @author kevan
 */
@Component(service = {LDAPCacheManager.class}, immediate = true)
public class LDAPCacheManager {
    public static final String LDAP_USER_CACHE = "LDAPUsersCache";
    public static final String LDAP_GROUP_CACHE = "LDAPGroupsCache";

    private static Logger logger = LoggerFactory.getLogger(LDAPCacheManager.class);

    private Ehcache groupCache;
    private Ehcache userCache;

    @Activate
    protected void start(){
        EhCacheProvider cacheProvider = (EhCacheProvider) SpringContextSingleton.getInstance().getContext().getBean("ehCacheProvider");
        final CacheManager cacheManager = cacheProvider.getCacheManager();
        userCache = cacheManager.getCache(LDAP_USER_CACHE);
        if (userCache == null) {
            userCache = createLDAPCache(cacheManager, LDAP_USER_CACHE);
        } else {
            userCache.removeAll();
        }
        groupCache = cacheManager.getCache(LDAP_GROUP_CACHE);
        if (groupCache == null) {
            groupCache = createLDAPCache(cacheManager, LDAP_GROUP_CACHE);
        } else  {
            groupCache.removeAll();
        }
    }

    @Deactivate
    protected void stop(){
        // flush
        if (userCache != null) {
            userCache.removeAll();
        }
        if (groupCache != null) {
            groupCache.removeAll();
        }
    }

    private Ehcache createLDAPCache(CacheManager cacheManager, String cacheName) {
        CacheConfiguration cacheConfiguration = cacheManager.getConfiguration().getDefaultCacheConfiguration() != null ?
                cacheManager.getConfiguration().getDefaultCacheConfiguration().clone() :
                new CacheConfiguration();
        cacheConfiguration.setName(cacheName);
        cacheConfiguration.setEternal(false);
        cacheConfiguration.setTimeToIdleSeconds(3600);
        // Create a new cache with the configuration
        Ehcache cache = new Cache(cacheConfiguration);
        cache.setName(cacheName);
        // Cache name has been set now we can initialize it by putting it in the manager.
        // Only Cache manager is initializing caches.
        return cacheManager.addCacheIfAbsent(cache);
    }
        
    public void clearUserCacheEntryByName(String providerKey, String username) {
        userCache.remove(getCacheNameKey(providerKey, username));
    }

    public LDAPUserCacheEntry getUserCacheEntryByName(String providerKey, String username) {
        return (LDAPUserCacheEntry) CacheHelper.getObjectValue(userCache, getCacheNameKey(providerKey, username));
    }

    public LDAPUserCacheEntry getUserCacheEntryByDn(String providerKey, String dn) {
        return (LDAPUserCacheEntry) CacheHelper.getObjectValue(userCache, getCacheDnKey(providerKey, dn));
    }

    public void cacheUser(String providerKey, LDAPUserCacheEntry ldapUserCacheEntry) {
        if (logger.isDebugEnabled()) {
            logger.debug("Caching user: {}", ldapUserCacheEntry.getName());
        }
        ModuleClassLoaderAwareCacheEntry cacheEntry = new ModuleClassLoaderAwareCacheEntry(ldapUserCacheEntry, "ldap");
        userCache.put(new Element(getCacheNameKey(providerKey, ldapUserCacheEntry.getName()), cacheEntry));
        if (ldapUserCacheEntry.getDn() != null) {
            userCache.put(new Element(getCacheDnKey(providerKey, ldapUserCacheEntry.getDn()), cacheEntry));
        }
    }

    public LDAPGroupCacheEntry getGroupCacheEntryName(String providerKey, String groupname) {
        return (LDAPGroupCacheEntry) CacheHelper.getObjectValue(groupCache, getCacheNameKey(providerKey, groupname));
    }

    public LDAPGroupCacheEntry getGroupCacheEntryByDn(String providerKey, String dn) {
        return (LDAPGroupCacheEntry) CacheHelper.getObjectValue(groupCache, getCacheDnKey(providerKey, dn));
    }

    public void cacheGroup(String providerKey, LDAPGroupCacheEntry ldapGroupCacheEntry) {
        if (logger.isDebugEnabled()) {
            logger.debug("Caching group: {}", ldapGroupCacheEntry.getName());
        }
        ModuleClassLoaderAwareCacheEntry cacheEntry = new ModuleClassLoaderAwareCacheEntry(ldapGroupCacheEntry, "ldap");
        groupCache.put(new Element(getCacheNameKey(providerKey, ldapGroupCacheEntry.getName()), cacheEntry));
        if (ldapGroupCacheEntry.getDn() != null) {
            groupCache.put(new Element(getCacheDnKey(providerKey, ldapGroupCacheEntry.getDn()), cacheEntry));
        }
    }

    private String getCacheNameKey(String providerKey, String objectName) {
        return providerKey + "n" + objectName;
    }

    private String getCacheDnKey(String providerKey, String objectName) {
        return providerKey + "d" + objectName;
    }
}