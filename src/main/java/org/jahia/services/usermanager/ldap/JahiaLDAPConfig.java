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

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.pool.impl.GenericKeyedObjectPool;
import org.jahia.services.usermanager.ldap.communication.LdapTemplateWrapper;
import org.jahia.services.usermanager.ldap.config.AbstractConfig;
import org.jahia.services.usermanager.ldap.config.GroupConfig;
import org.jahia.services.usermanager.ldap.config.UserConfig;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.DefaultDirObjectFactory;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.pool.factory.PoolingContextSource;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import org.springframework.ldap.pool.validation.DefaultDirContextValidator;

/**
 * Helper class to configure LDAP user and group providers via OSGi Config Admin service.
 */
public class JahiaLDAPConfig {

    public static final String POOL_APACHE_COMMONS = "apache-commons";
    public static final String POOL_LDAP = "ldap";
    public static final String WHEN_EXHAUSTED_BLOCK = "block";
    public static final String WHEN_EXHAUSTED_FAIL = "fail";
    public static final String WHEN_EXHAUSTED_GROW = "grow";
    public static final String LDAP_PROVIDER_KEY_PROP = "ldap.provider.key";
    private static Logger logger = LoggerFactory.getLogger(JahiaLDAPConfig.class);

    private String providerKey;
    private LDAPUserGroupProvider ldapUserGroupProvider;

    /**
     * Initializes an instance of this class.
     *
     * @param dictionary configuration parameters
     */
    public JahiaLDAPConfig(Dictionary<String, ?> dictionary) {
        providerKey = computeProviderKey(dictionary);
    }

    /**
     * defines or update the context of the provider
     * @param context the Spring application context object
     * @param dictionary configuration parameters
     */
    public void setContext(ApplicationContext context, Dictionary<String, ?> dictionary) {
        Properties userLdapProperties = new Properties();
        Properties groupLdapProperties = new Properties();
        UserConfig userConfig = new UserConfig();
        GroupConfig groupConfig = new GroupConfig();
        Enumeration<String> keys = dictionary.keys();

        String fileName = null;
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            if (Constants.SERVICE_PID.equals(key) ||
                    ConfigurationAdmin.SERVICE_FACTORYPID.equals(key)) {
                continue;
            } else if ("felix.fileinstall.filename".equals(key)) {
                fileName = (String) dictionary.get(key);
                continue;
            }
            Object value = dictionary.get(key);
            if (key.startsWith("user.")) {
                buildConfig(userLdapProperties, userConfig, key, value, true);
            } else if (key.startsWith("group.")) {
                buildConfig(groupLdapProperties, groupConfig, key, value, false);
            } else {
                userLdapProperties.put(transformPropKeyToBeanAttr(key), value);
                groupLdapProperties.put(transformPropKeyToBeanAttr(key), value);
            }
        }
        try {
            // populate config beans
            BeanUtils.populate(userConfig, userLdapProperties);
            BeanUtils.populate(groupConfig, groupLdapProperties);

            // handle defaults values
            userConfig.handleDefaults();
            groupConfig.handleDefaults();


            LdapContextSource lcs = new LdapContextSource();
            lcs.setUrl(userConfig.getUrl());
            if (StringUtils.isNotBlank(userConfig.getPublicBindDn())) {
                lcs.setUserDn(userConfig.getPublicBindDn());
            }
            if (StringUtils.isNotEmpty(userConfig.getPublicBindPassword())) {
                lcs.setPassword(userConfig.getPublicBindPassword());
            }

            Map<String, Object> publicEnv = new HashMap<>();
            if (POOL_LDAP.equalsIgnoreCase(userConfig.getLdapConnectPool()) || Boolean.valueOf(userConfig.getLdapConnectPool())) {
                lcs.setPooled(true);
                if (userConfig.getLdapConnectPoolAuthentication() != null) {
                    publicEnv.put("com.sun.jndi.ldap.connect.pool.authentication", userConfig.getLdapConnectPoolAuthentication());
                }
                if (userConfig.getLdapConnectPoolTimeout() != null && Long.valueOf(userConfig.getLdapConnectPoolTimeout()) > 0) {
                    publicEnv.put("com.sun.jndi.ldap.connect.pool.timeout", userConfig.getLdapConnectPoolTimeout());
                }
                if (userConfig.getLdapConnectPoolDebug() != null) {
                    publicEnv.put("com.sun.jndi.ldap.connect.pool.debug", userConfig.getLdapConnectPoolDebug());
                }
                if (userConfig.getLdapConnectPoolInitSize() != null) {
                    publicEnv.put("com.sun.jndi.ldap.connect.pool.initsize", userConfig.getLdapConnectPoolInitSize());
                }
                if (userConfig.getLdapConnectPoolMaxSize() != null) {
                    publicEnv.put("com.sun.jndi.ldap.connect.pool.maxsize", userConfig.getLdapConnectPoolMaxSize());
                }
                if (userConfig.getLdapConnectPoolPrefSize() != null) {
                    publicEnv.put("com.sun.jndi.ldap.connect.pool.prefsize", userConfig.getLdapConnectPoolPrefSize());
                }

                logger.info("Using built-in Java LDAP connection pooling with {} maximum active connections",
                        userConfig.getLdapConnectPoolMaxSize() != null ? userConfig.getLdapConnectPoolMaxSize()
                                : "unlimited");
            }
            if (userConfig.getLdapReadTimeout() != null) {
                publicEnv.put("com.sun.jndi.ldap.read.timeout", userConfig.getLdapReadTimeout());
            }
            if (userConfig.getLdapConnectTimeout() != null) {
                publicEnv.put("com.sun.jndi.ldap.connect.timeout", userConfig.getLdapConnectTimeout());
            }
            lcs.setBaseEnvironmentProperties(publicEnv);

            lcs.setReferral(groupConfig.getRefferal());
            lcs.setDirObjectFactory(DefaultDirObjectFactory.class);
            lcs.afterPropertiesSet();


            LdapTemplate ldap;

            if (POOL_APACHE_COMMONS.equalsIgnoreCase(userConfig.getLdapConnectPool())) {
                PoolingContextSource poolingContextSource = new PoolingContextSource();
                poolingContextSource.setContextSource(lcs);
                poolingContextSource.setDirContextValidator(new DefaultDirContextValidator());
                if (userConfig.getLdapConnectPoolMaxActive() != null) {
                    poolingContextSource.setMaxActive(userConfig.getLdapConnectPoolMaxActive());
                }
                if (userConfig.getLdapConnectPoolMaxIdle() != null) {
                    poolingContextSource.setMaxIdle(userConfig.getLdapConnectPoolMaxIdle());
                }
                if (userConfig.getLdapConnectPoolMaxTotal() != null) {
                    poolingContextSource.setMaxTotal(userConfig.getLdapConnectPoolMaxTotal());
                }
                if (userConfig.getLdapConnectPoolMaxWait() != null) {
                    poolingContextSource.setMaxWait(userConfig.getLdapConnectPoolMaxWait());
                }
                if (userConfig.getLdapConnectPoolMinEvictableIdleTimeMillis() != null) {
                    poolingContextSource.setMinEvictableIdleTimeMillis(userConfig.getLdapConnectPoolMinEvictableIdleTimeMillis());
                }
                if (userConfig.getLdapConnectPoolMinIdle() != null) {
                    poolingContextSource.setMinIdle(userConfig.getLdapConnectPoolMinIdle());
                }
                if (userConfig.getLdapConnectPoolNumTestsPerEvictionRun() != null) {
                    poolingContextSource.setNumTestsPerEvictionRun(userConfig.getLdapConnectPoolNumTestsPerEvictionRun());
                }
                if (userConfig.getLdapConnectPoolTestOnBorrow() != null) {
                    poolingContextSource.setTestOnBorrow(userConfig.getLdapConnectPoolTestOnBorrow());
                }
                if (userConfig.getLdapConnectPoolTestOnReturn() != null) {
                    poolingContextSource.setTestOnReturn(userConfig.getLdapConnectPoolTestOnReturn());
                }
                if (userConfig.getLdapConnectPoolTestWhileIdle() != null) {
                    poolingContextSource.setTestWhileIdle(userConfig.getLdapConnectPoolTestWhileIdle());
                }
                if (userConfig.getLdapConnectPoolTimeBetweenEvictionRunsMillis() != null) {
                    poolingContextSource.setTimeBetweenEvictionRunsMillis(userConfig.getLdapConnectPoolTimeBetweenEvictionRunsMillis());
                }
                if (WHEN_EXHAUSTED_BLOCK.equalsIgnoreCase(userConfig.getLdapConnectPoolWhenExhaustedAction())) {
                    poolingContextSource.setWhenExhaustedAction(GenericKeyedObjectPool.WHEN_EXHAUSTED_BLOCK);
                } else if (WHEN_EXHAUSTED_FAIL.equalsIgnoreCase(userConfig.getLdapConnectPoolWhenExhaustedAction())) {
                    poolingContextSource.setWhenExhaustedAction(GenericKeyedObjectPool.WHEN_EXHAUSTED_FAIL);
                } else if (WHEN_EXHAUSTED_GROW.equalsIgnoreCase(userConfig.getLdapConnectPoolWhenExhaustedAction())) {
                    poolingContextSource.setWhenExhaustedAction(GenericKeyedObjectPool.WHEN_EXHAUSTED_GROW);
                }

                ldap = new LdapTemplate(poolingContextSource);
                
                logger.info(
                        "Using LDAP connection pooling based on Apache Commons Pool with {} maximum active connections",
                        poolingContextSource.getMaxActive());
            } else {
                ldap = new LdapTemplate(lcs);
            }


            // AD workaround to ignore Exceptions
            ldap.setIgnorePartialResultException(true);
            ldap.setIgnoreNameNotFoundException(true);

            if (ldapUserGroupProvider == null) {
                ldapUserGroupProvider = (LDAPUserGroupProvider) context.getBean("ldapUserGroupProvider");
            } else {
                // Deactivate the provider before reconfiguring it.
                ldapUserGroupProvider.unregister();
            }

            ldapUserGroupProvider.setKey(providerKey);
            ldapUserGroupProvider.setUserConfig(userConfig);
            ldapUserGroupProvider.setGroupConfig(groupConfig);
            if (StringUtils.isNotEmpty(userConfig.getUidSearchName()) && StringUtils.isNotEmpty(groupConfig.getSearchName())) {
                ldapUserGroupProvider.setDistinctBase(!userConfig.getUidSearchName().startsWith(groupConfig.getSearchName()) &&
                        !groupConfig.getSearchName().startsWith(userConfig.getUidSearchName()));
            }
            ldapUserGroupProvider.setLdapTemplateWrapper(new LdapTemplateWrapper(ldap));
            ldapUserGroupProvider.setContextSource(lcs);
            ldapUserGroupProvider.setMaxLdapTimeoutCountBeforeDisconnect(userConfig.getMaxLdapTimeoutCountBeforeDisconnect());
            // Activate (again).
            ldapUserGroupProvider.register();

            if (userConfig.isMinimalSettingsOk() && groupConfig.isPreload()) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        List<String> l = ldapUserGroupProvider.searchGroups(new Properties(), 0, -1);
                        for (String s : l) {
                            ldapUserGroupProvider.getGroupMembers(s);
                        }
                    }
                }, "LDAP Preload").start();
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            logger.error("Invalid LDAP configuration:" + fileName + ", please refer to the LDAP configuration documentation", e);
        }
    }

    public void unregister() {
        if (ldapUserGroupProvider != null) {
            unregisterUserProvider();
        }

    }

    private void unregisterUserProvider() {
        ldapUserGroupProvider.unregister();
        ldapUserGroupProvider = null;
    }

    private String computeProviderKey(Dictionary<String, ?> dictionary) {
        String provideKey = (String) dictionary.get(LDAP_PROVIDER_KEY_PROP);
        if (provideKey != null) {
            return provideKey;
        }
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
        return (StringUtils.isBlank(confId) || "config".equals(confId))  ? "ldap" : ("ldap." + confId);
    }

    private String transformPropKeyToBeanAttr(String key){
        Iterable<String> upperStrings = Iterables.transform(Arrays.asList(StringUtils.split(key, '.')), new Function<String,String>() {
            public String apply(String input) {
                return (input == null) ? null : StringUtils.capitalize(input);
            }
        });
        return StringUtils.uncapitalize(StringUtils.join(upperStrings.iterator(), ""));
    }

    private void buildConfig(Properties properties, AbstractConfig config, String key, Object value, boolean isUser){
        if(key.contains(".attribute.map")){
            config.getAttributesMapper().put(StringUtils.substringBetween(key, isUser ? "user." : "group.", ".attribute.map").replace("_", ":"),
                    (String) value);
        } else if(key.contains("search.wildcards.attributes")){
            if(StringUtils.isNotEmpty((String) value)){
                for (String wildcardAttr : ((String) value).split(",")) {
                    config.getSearchWildcardsAttributes().add(wildcardAttr.trim());
                }
            }
        } else {
            properties.put(transformPropKeyToBeanAttr(key.substring(isUser ? 5 : 6)), value);
        }
    }

    public String getProviderKey() {
        return providerKey;
    }
}
