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

    public LDAPUserCacheEntry getUserCacheEntryByName(String providerKey, String username) {
        return (LDAPUserCacheEntry) CacheHelper.getObjectValue(userCache, getCacheNameKey(providerKey, username));
    }

    public LDAPUserCacheEntry getUserCacheEntryByDn(String providerKey, String dn) {
        return (LDAPUserCacheEntry) CacheHelper.getObjectValue(userCache, getCacheDnKey(providerKey, dn));
    }

    public void cacheUser(String providerKey, LDAPUserCacheEntry ldapUserCacheEntry) {
        ModuleClassLoaderAwareCacheEntry cacheEntry = new ModuleClassLoaderAwareCacheEntry(ldapUserCacheEntry, "ldap");
        userCache.put(new Element(getCacheNameKey(providerKey, ldapUserCacheEntry.getName()), cacheEntry));
        userCache.put(new Element(getCacheDnKey(providerKey, ldapUserCacheEntry.getDn()), cacheEntry));
    }

    public LDAPGroupCacheEntry getGroupCacheEntryName(String providerKey, String groupname) {
        return (LDAPGroupCacheEntry) CacheHelper.getObjectValue(groupCache, getCacheNameKey(providerKey, groupname));
    }

    public LDAPGroupCacheEntry getGroupCacheEntryByDn(String providerKey, String dn) {
        return (LDAPGroupCacheEntry) CacheHelper.getObjectValue(groupCache, getCacheDnKey(providerKey, dn));
    }

    public void cacheGroup(String providerKey, LDAPGroupCacheEntry ldapGroupCacheEntry) {
        ModuleClassLoaderAwareCacheEntry cacheEntry = new ModuleClassLoaderAwareCacheEntry(ldapGroupCacheEntry, "ldap");
        groupCache.put(new Element(getCacheNameKey(providerKey, ldapGroupCacheEntry.getName()), cacheEntry));
        groupCache.put(new Element(getCacheDnKey(providerKey, ldapGroupCacheEntry.getDn()), cacheEntry));
    }

    private String getCacheNameKey(String providerKey, String objectName) {
        return providerKey + "n" + objectName;
    }

    private String getCacheDnKey(String providerKey, String objectName) {
        return providerKey + "d" + objectName;
    }
}
