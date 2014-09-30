package org.jahia.services.usermanager.ldap.cache;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import org.jahia.services.cache.CacheHelper;
import org.jahia.services.cache.ModuleClassLoaderAwareCacheEntry;
import org.jahia.services.cache.ehcache.EhCacheProvider;

/**
 * @author kevan
 */
public class LDAPCacheManager {
    public static final String LDAP_USER_CACHE = "LDAPUsersCache";
    public static final String LDAP_GROUP_CACHE = "LDAPGroupsCache";

    private Ehcache groupCache;
    private Ehcache userCache;
    private EhCacheProvider cacheProvider;

    void start(){
        final CacheManager cacheManager = cacheProvider.getCacheManager();
        userCache = cacheManager.getCache(LDAP_USER_CACHE);
        if (userCache == null) {
            cacheManager.addCache(LDAP_USER_CACHE);
            userCache = cacheManager.getCache(LDAP_USER_CACHE);
        } else {
            userCache.removeAll();
        }
        groupCache = cacheManager.getCache(LDAP_GROUP_CACHE);
        if (groupCache == null) {
            cacheManager.addCache(LDAP_GROUP_CACHE);
            groupCache = cacheManager.getCache(LDAP_GROUP_CACHE);
        } else  {
            groupCache.removeAll();
        }
    }

    void stop(){
        // flush
        userCache.removeAll();
        groupCache.removeAll();
    }

    public void setCacheProvider(EhCacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }

    public LDAPUserCacheEntry getUserCacheEntry(String providerKey, String username) {
        return (LDAPUserCacheEntry) CacheHelper.getObjectValue(userCache, getCacheKey(providerKey, username));
    }

    public void cacheUser(String providerKey, LDAPUserCacheEntry ldapUserCacheEntry) {
        userCache.put(new Element(getCacheKey(providerKey, ldapUserCacheEntry.getName()), new ModuleClassLoaderAwareCacheEntry(ldapUserCacheEntry, "ldap")));
    }

    public LDAPGroupCacheEntry getGroupCacheEntry(String providerKey, String groupname) {
        return (LDAPGroupCacheEntry) CacheHelper.getObjectValue(groupCache, getCacheKey(providerKey, groupname));
    }

    public void cacheGroup(String providerKey, LDAPGroupCacheEntry ldapGroupCacheEntry) {
        groupCache.put(new Element(getCacheKey(providerKey, ldapGroupCacheEntry.getName()), new ModuleClassLoaderAwareCacheEntry(ldapGroupCacheEntry, "ldap")));
    }

    private String getCacheKey(String providerKey, String objectName) {
        return providerKey + "n" + objectName;
    }
}
