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
package org.jahia.services.usermanager.ldap.config;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Map;
import java.util.Set;

/**
 * Abstract config provide by the ldap config file
 * @author kevan
 */
public abstract class AbstractConfig {
    private static final int DEFAULT_SEARCH_COUNT_LIMIT = 100;
    private static final int DEFAULT_MAX_TIMEOUT_COUNT = 3;

    private String url;
    private String publicBindDn;
    private String publicBindPassword;
    private String authentificationMode = "simple";
    private String contextFactory = "com.sun.jndi.ldap.LdapCtxFactory";

    private String ldapConnectPool = "apache-commons";

    private String ldapConnectTimeout = "5000";
    private String ldapReadTimeout = "5000";

    private String ldapConnectPoolAuthentication = "none simple";
    private String ldapConnectPoolTimeout;
    private String ldapConnectPoolDebug;
    private String ldapConnectPoolInitSize;
    private String ldapConnectPoolMaxSize;
    private String ldapConnectPoolPrefSize;

    private Integer ldapConnectPoolMaxActive;
    private Integer ldapConnectPoolMaxIdle;
    private Integer ldapConnectPoolMaxTotal;
    private Integer ldapConnectPoolMaxWait;
    private Integer ldapConnectPoolMinEvictableIdleTimeMillis;
    private Integer ldapConnectPoolMinIdle;
    private Integer ldapConnectPoolNumTestsPerEvictionRun;
    private Boolean ldapConnectPoolTestOnBorrow;
    private Boolean ldapConnectPoolTestOnReturn;
    private Boolean ldapConnectPoolTestWhileIdle;
    private Long ldapConnectPoolTimeBetweenEvictionRunsMillis;
    private String ldapConnectPoolWhenExhaustedAction;
    private int maxLdapTimeoutCountBeforeDisconnect = DEFAULT_MAX_TIMEOUT_COUNT;

    private long searchCountlimit = DEFAULT_SEARCH_COUNT_LIMIT;
    /**
     * Fixed query filter that is used when searching for users/groups to filter out "unwanted" entries.
     */
    private String searchFilter;
    private String searchObjectclass;
    private boolean searchAttributeInDn = false;
    private boolean canGroupContainSubGroups = false;
    private Set<String> searchWildcardsAttributes = Sets.newHashSet();
    private Map<String, String> attributesMapper = Maps.newHashMap();

    private String targetSite;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPublicBindDn() {
        return publicBindDn;
    }

    public void setPublicBindDn(String publicBindDn) {
        this.publicBindDn = publicBindDn;
    }

    public String getPublicBindPassword() {
        return publicBindPassword;
    }

    public void setPublicBindPassword(String publicBindPassword) {
        this.publicBindPassword = publicBindPassword;
    }

    public String getAuthentificationMode() {
        return authentificationMode;
    }

    public void setAuthentificationMode(String authentificationMode) {
        this.authentificationMode = authentificationMode;
    }

    public String getContextFactory() {
        return contextFactory;
    }

    public void setContextFactory(String contextFactory) {
        this.contextFactory = contextFactory;
    }

    public String getLdapConnectPool() {
        return ldapConnectPool;
    }

    public void setLdapConnectPool(String ldapConnectPool) {
        this.ldapConnectPool = ldapConnectPool;
    }

    public String getLdapConnectTimeout() {
        return ldapConnectTimeout;
    }

    public void setLdapConnectTimeout(String ldapConnectTimeout) {
        this.ldapConnectTimeout = ldapConnectTimeout;
    }

    public String getLdapReadTimeout() {
        return ldapReadTimeout;
    }

    public void setLdapReadTimeout(String ldapReadTimeout) {
        this.ldapReadTimeout = ldapReadTimeout;
    }

    public String getLdapConnectPoolAuthentication() {
        return ldapConnectPoolAuthentication;
    }

    public void setLdapConnectPoolAuthentication(String ldapConnectPoolAuthentication) {
        this.ldapConnectPoolAuthentication = ldapConnectPoolAuthentication;
    }

    public String getLdapConnectPoolTimeout() {
        return ldapConnectPoolTimeout;
    }

    public void setLdapConnectPoolTimeout(String ldapConnectPoolTimeout) {
        this.ldapConnectPoolTimeout = ldapConnectPoolTimeout;
    }

    public String getLdapConnectPoolDebug() {
        return ldapConnectPoolDebug;
    }

    public void setLdapConnectPoolDebug(String ldapConnectPoolDebug) {
        this.ldapConnectPoolDebug = ldapConnectPoolDebug;
    }

    public String getLdapConnectPoolInitSize() {
        return ldapConnectPoolInitSize;
    }

    public void setLdapConnectPoolInitSize(String ldapConnectPoolInitSize) {
        this.ldapConnectPoolInitSize = ldapConnectPoolInitSize;
    }

    public String getLdapConnectPoolMaxSize() {
        return ldapConnectPoolMaxSize;
    }

    public void setLdapConnectPoolMaxSize(String ldapConnectPoolMaxSize) {
        this.ldapConnectPoolMaxSize = ldapConnectPoolMaxSize;
    }

    public String getLdapConnectPoolPrefSize() {
        return ldapConnectPoolPrefSize;
    }

    public void setLdapConnectPoolPrefSize(String ldapConnectPoolPrefSize) {
        this.ldapConnectPoolPrefSize = ldapConnectPoolPrefSize;
    }

    public Integer getLdapConnectPoolMaxActive() {
        return ldapConnectPoolMaxActive;
    }

    public void setLdapConnectPoolMaxActive(Integer ldapConnectPoolMaxActive) {
        this.ldapConnectPoolMaxActive = ldapConnectPoolMaxActive;
    }

    public Integer getLdapConnectPoolMaxIdle() {
        return ldapConnectPoolMaxIdle;
    }

    public void setLdapConnectPoolMaxIdle(Integer ldapConnectPoolMaxIdle) {
        this.ldapConnectPoolMaxIdle = ldapConnectPoolMaxIdle;
    }

    public Integer getLdapConnectPoolMaxTotal() {
        return ldapConnectPoolMaxTotal;
    }

    public void setLdapConnectPoolMaxTotal(Integer ldapConnectPoolMaxTotal) {
        this.ldapConnectPoolMaxTotal = ldapConnectPoolMaxTotal;
    }

    public Integer getLdapConnectPoolMaxWait() {
        return ldapConnectPoolMaxWait;
    }

    public void setLdapConnectPoolMaxWait(Integer ldapConnectPoolMaxWait) {
        this.ldapConnectPoolMaxWait = ldapConnectPoolMaxWait;
    }

    public Integer getLdapConnectPoolMinEvictableIdleTimeMillis() {
        return ldapConnectPoolMinEvictableIdleTimeMillis;
    }

    public void setLdapConnectPoolMinEvictableIdleTimeMillis(Integer ldapConnectPoolMinEvictableIdleTimeMillis) {
        this.ldapConnectPoolMinEvictableIdleTimeMillis = ldapConnectPoolMinEvictableIdleTimeMillis;
    }

    public Integer getLdapConnectPoolMinIdle() {
        return ldapConnectPoolMinIdle;
    }

    public void setLdapConnectPoolMinIdle(Integer ldapConnectPoolMinIdle) {
        this.ldapConnectPoolMinIdle = ldapConnectPoolMinIdle;
    }

    public Integer getLdapConnectPoolNumTestsPerEvictionRun() {
        return ldapConnectPoolNumTestsPerEvictionRun;
    }

    public void setLdapConnectPoolNumTestsPerEvictionRun(Integer ldapConnectPoolNumTestsPerEvictionRun) {
        this.ldapConnectPoolNumTestsPerEvictionRun = ldapConnectPoolNumTestsPerEvictionRun;
    }

    public Boolean getLdapConnectPoolTestOnBorrow() {
        return ldapConnectPoolTestOnBorrow;
    }

    public void setLdapConnectPoolTestOnBorrow(Boolean ldapConnectPoolTestOnBorrow) {
        this.ldapConnectPoolTestOnBorrow = ldapConnectPoolTestOnBorrow;
    }

    public Boolean getLdapConnectPoolTestOnReturn() {
        return ldapConnectPoolTestOnReturn;
    }

    public void setLdapConnectPoolTestOnReturn(Boolean ldapConnectPoolTestOnReturn) {
        this.ldapConnectPoolTestOnReturn = ldapConnectPoolTestOnReturn;
    }

    public Boolean getLdapConnectPoolTestWhileIdle() {
        return ldapConnectPoolTestWhileIdle;
    }

    public void setLdapConnectPoolTestWhileIdle(Boolean ldapConnectPoolTestWhileIdle) {
        this.ldapConnectPoolTestWhileIdle = ldapConnectPoolTestWhileIdle;
    }

    public Long getLdapConnectPoolTimeBetweenEvictionRunsMillis() {
        return ldapConnectPoolTimeBetweenEvictionRunsMillis;
    }

    public void setLdapConnectPoolTimeBetweenEvictionRunsMillis(Long ldapConnectPoolTimeBetweenEvictionRunsMillis) {
        this.ldapConnectPoolTimeBetweenEvictionRunsMillis = ldapConnectPoolTimeBetweenEvictionRunsMillis;
    }

    public String getLdapConnectPoolWhenExhaustedAction() {
        return ldapConnectPoolWhenExhaustedAction;
    }

    public void setLdapConnectPoolWhenExhaustedAction(String ldapConnectPoolWhenExhaustedAction) {
        this.ldapConnectPoolWhenExhaustedAction = ldapConnectPoolWhenExhaustedAction;
    }

    public int getMaxLdapTimeoutCountBeforeDisconnect() {
        return maxLdapTimeoutCountBeforeDisconnect;
    }

    public void setMaxLdapTimeoutCountBeforeDisconnect(int maxLdapTimeoutCountBeforeDisconnect) {
        this.maxLdapTimeoutCountBeforeDisconnect = maxLdapTimeoutCountBeforeDisconnect;
    }

    public long getSearchCountlimit() {
        return searchCountlimit;
    }

    public void setSearchCountlimit(long searchCountlimit) {
        this.searchCountlimit = searchCountlimit;
    }

    public String getSearchObjectclass() {
        return searchObjectclass;
    }

    public void setSearchObjectclass(String searchObjectclass) {
        this.searchObjectclass = searchObjectclass;
    }

    public Set<String> getSearchWildcardsAttributes() {
        return searchWildcardsAttributes;
    }

    public void setSearchWildcardsAttributes(Set<String> searchWildcardsAttributes) {
        this.searchWildcardsAttributes = searchWildcardsAttributes;
    }

    public Map<String, String> getAttributesMapper() {
        return attributesMapper;
    }

    public void setAttributesMapper(Map<String, String> attributesMapper) {
        this.attributesMapper = attributesMapper;
    }

    public boolean isSearchAttributeInDn() {
        return searchAttributeInDn;
    }

    public void setSearchAttributeInDn(boolean searchAttributeInDn) {
        this.searchAttributeInDn = searchAttributeInDn;
    }

    public boolean isCanGroupContainSubGroups() {
        return canGroupContainSubGroups;
    }

    public void setCanGroupContainSubGroups(boolean canGroupContainSubGroups) {
        this.canGroupContainSubGroups = canGroupContainSubGroups;
    }

    public String getTargetSite() {
        return targetSite;
    }

    public void setTargetSite(String targetSite) {
        this.targetSite = targetSite;
    }

    /**
     * Returns fixed query filter that is used when searching for users/groups to filter out "unwanted" entries.
     * 
     * @return fixed query filter that is used when searching for users/groups to filter out "unwanted" entries
     */
    public String getSearchFilter() {
        return searchFilter;
    }

    /**
     * Sets the fixed query filter for search.
     * 
     * @param searchFilter string in LDAP filter format
     * @see #getSearchFilter()
     */
    public void setSearchFilter(String searchFilter) {
        this.searchFilter = (searchFilter != null && searchFilter.length() > 0 ? searchFilter : null);
    }
}
