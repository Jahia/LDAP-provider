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
package org.jahia.services.usermanager.ldap.cache;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import org.jahia.services.cache.CacheHelper;
import org.jahia.services.cache.ModuleClassLoaderAwareCacheEntry;
import org.jahia.services.cache.ehcache.EhCacheProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for LDAP provider related caches.
 *
 * @author kevan
 */
public class LDAPCacheManager {
    public static final String LDAP_USER_CACHE = "LDAPUsersCache";
    public static final String LDAP_GROUP_CACHE = "LDAPGroupsCache";

    private static Logger logger = LoggerFactory.getLogger(LDAPCacheManager.class);

    private Ehcache groupCache;
    private Ehcache userCache;
    private EhCacheProvider cacheProvider;

    void start(){
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

    private Ehcache createLDAPCache(CacheManager cacheManager, String cacheName) {
        CacheConfiguration cacheConfiguration = new CacheConfiguration();
        cacheConfiguration.setName(cacheName);
        cacheConfiguration.setTimeToIdleSeconds(3600);
        cacheConfiguration.setEternal(false);
        // Create a new cache with the configuration
        Ehcache cache = new Cache(cacheConfiguration);
        cache.setName(cacheName);
        // Cache name has been set now we can initialize it by putting it in the manager.
        // Only Cache manager is initializing caches.
        return cacheManager.addCacheIfAbsent(cache);
    }

    void stop(){
        // flush
        if (userCache != null) {
            userCache.removeAll();
        }
        if (groupCache != null) {
            groupCache.removeAll();
        }
    }

    public void setCacheProvider(EhCacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
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