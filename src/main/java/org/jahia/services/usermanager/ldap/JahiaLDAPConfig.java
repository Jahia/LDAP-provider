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

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.StringUtils;
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

import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * Helper class to configure LDAP user and group providers via OSGi Config Admin service.
 */
public class JahiaLDAPConfig {
    private static Logger logger = LoggerFactory.getLogger(JahiaLDAPConfig.class);
    private String providerKey;
    private LDAPUserGroupProvider ldapUserGroupProvider;

    public JahiaLDAPConfig(ApplicationContext context, Dictionary<String, ?> dictionary) {
        providerKey = computeProviderKey(dictionary);
        setContext(context, dictionary);
    }

    /**
     * defines or update the context of the provider
     * @param context
     * @param dictionary
     */
    public void setContext(ApplicationContext context, Dictionary<String, ?> dictionary) {
        Properties userLdapProperties = new Properties();
        Properties groupLdapProperties = new Properties();
        UserConfig userConfig = new UserConfig();
        GroupConfig groupConfig = new GroupConfig();
        Enumeration<String> keys = dictionary.keys();

        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            if (Constants.SERVICE_PID.equals(key) ||
                    ConfigurationAdmin.SERVICE_FACTORYPID.equals(key) ||
                    "felix.fileinstall.filename".equals(key)) {
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

            // instantiate ldap context
            if (userConfig.isMinimalSettingsOk()) {
                LdapContextSource lcs = new LdapContextSource();
                lcs.setUrl(userConfig.getUrl());
                if (StringUtils.isNotEmpty(userConfig.getPublicBindDn())) {
                    lcs.setUserDn(userConfig.getPublicBindDn());
                }
                if (StringUtils.isNotEmpty(userConfig.getPublicBindPassword())) {
                    lcs.setPassword(userConfig.getPublicBindPassword());
                }

                if(userConfig.isLdapConnectPool()){
                    lcs.setPooled(userConfig.isLdapConnectPool());
                    Map<String, Object> publicEnv = new Hashtable<String, Object>(1);
                    if (Long.valueOf(userConfig.getLdapConnectTimeout()) > 0) {
                        publicEnv.put("com.sun.jndi.ldap.connect.timeout", userConfig.getLdapConnectTimeout());
                    }
                    lcs.setBaseEnvironmentProperties(publicEnv);
                }

                lcs.setPooled(userConfig.isLdapConnectPool());
                lcs.setReferral(groupConfig.getRefferal());
                lcs.setDirObjectFactory(DefaultDirObjectFactory.class);
                lcs.afterPropertiesSet();
                LdapTemplate ldap = new LdapTemplate(lcs);

                // AD workaround to ignore Exceptions
                ldap.setIgnorePartialResultException(true);
                ldap.setIgnoreNameNotFoundException(true);
                
                boolean doRegister = false;
                if (ldapUserGroupProvider == null) {
                    ldapUserGroupProvider = (LDAPUserGroupProvider) context.getBean("ldapUserGroupProvider");
                    doRegister = true;
                }

                ldapUserGroupProvider.setKey(providerKey);
                ldapUserGroupProvider.setUserConfig(userConfig);
                ldapUserGroupProvider.setGroupConfig(groupConfig);
                ldapUserGroupProvider.setLdapTemplate(ldap);
                if (doRegister) {
                    ldapUserGroupProvider.register();
                }
            } else {
                unregister();
            }
        } catch (IllegalAccessException e) {
            logger.error("Config LDAP invalid, pls read the documentation on LDAP configuration", e);
        } catch (InvocationTargetException e) {
            logger.error("Config LDAP invalid, pls read the documentation on LDAP configuration", e);
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
        if (StringUtils.isBlank(confId) || "config".equals(confId)) {
            return "ldap";
        } else {
            return "ldap." + confId;
        }
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
}
