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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.modules.external.users.ExternalUserGroupService;
import org.jahia.modules.external.users.UserGroupProviderConfiguration;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.settings.SettingsBean;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.springframework.core.NestedCheckedException;
import org.springframework.core.NestedRuntimeException;
import org.springframework.ldap.core.support.LdapContextSource;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Paths;
import java.util.Dictionary;
import java.util.Map;
import java.util.Properties;
import org.jahia.services.usermanager.ldap.communication.LdapTemplateWrapper;

/**
 * Class to implement specific behaviour for configuration creation/edition/deletion in server settings
 */
@Component(service = {UserGroupProviderConfiguration.class}, immediate = true)
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

    private ExternalUserGroupService externalUserGroupService;
    private JahiaLDAPConfigFactory jahiaLDAPConfigFactory;
    private ConfigurationAdmin configurationAdmin;

    @Override
    public String getProviderClass() {
        return "org.jahia.services.usermanager.ldap.LDAPUserGroupProvider";
    }

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
        return "/modules/ldap/userGroupProviderEdit.jsp";
    }

    @Override
    public String create(Map<String, Object> parameters, Map<String, Object> flashScope) throws Exception {
        Properties properties = getProperties(parameters);
        flashScope.put("ldapProperties", properties);

        // config name
        String configName = (String) parameters.get("configName");
        if(StringUtils.isBlank(configName)) {
            // if we didn't provide a not-blank config name, generate one
            configName = "ldap" + System.currentTimeMillis();
        }
        // normalize the name
        configName = JCRContentUtils.generateNodeName(configName);
        flashScope.put("configName", configName);

        // provider key
        String providerKey = "ldap." + configName;
        configName = jahiaLDAPConfigFactory.getName() + "-" + configName + ".cfg";

        // check that we don't already have a provider with that key
        String pid = jahiaLDAPConfigFactory.getConfigPID(providerKey);
        if (pid != null) {
            throw new Exception("An LDAP provider with key '" + providerKey + "' already exists");
        }

        try {
            if (!testConnection(properties)) {
                throw new Exception("Connection to the LDAP server impossible");
            }
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }

        File folder = new File(SettingsBean.getInstance().getJahiaVarDiskPath(), "karaf/etc");
        if (folder.exists()) {
            FileOutputStream out = new FileOutputStream(new File(folder, configName));
            try {
                properties.store(out, "");
            } finally {
                IOUtils.closeQuietly(out);
            }
        } else {
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
        Properties properties = getProperties(parameters);
        flashScope.put("ldapProperties", properties);
        try {
            if (!testConnection(properties)) {
                throw new Exception("Connection to the LDAP server impossible");
            }
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
        String configName;
        if (providerKey.equals("ldap")) {
            configName = jahiaLDAPConfigFactory.getName() + "-config.cfg";
        } else if (providerKey.startsWith("ldap.")) {
            configName = jahiaLDAPConfigFactory.getName() + "-" + providerKey.substring("ldap.".length()) + ".cfg";
        } else {
            throw new Exception("Wrong LDAP provider key: " + providerKey);
        }

        File file = getExistingConfigFile(configName);
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

    private Properties getProperties(Map<String, Object> parameters) throws Exception {
        String[] propKeys;
        String[] propValues;
        if (parameters.get("propKey") instanceof String) {
            propKeys = new String[] { (String) parameters.get("propKey") };
            propValues = new String[] { (String) parameters.get("propValue") };
        } else {
            propKeys = (String[]) parameters.get("propKey");
            propValues = (String[]) parameters.get("propValue");
        }
        Properties properties = new Properties();
        if (propKeys != null) {
            for (int i = 0; i < propKeys.length; i++) {
                String propValue = propValues[i];
                if (StringUtils.isNotBlank(propValue)) {
                    properties.put(propKeys[i], propValue);
                }
            }
        }
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (entry.getKey().startsWith("propValue.")) {
                String key = StringUtils.substringAfter(entry.getKey(), "propValue.");
                if (StringUtils.isNotBlank((String) entry.getValue())) {
                    properties.put(key, entry.getValue());
                }
            }
        }
        if (parameters.isEmpty()) {
            throw new Exception("No property has been set");
        }
        return properties;
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
        File file = getExistingConfigFile(configName);
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

    private File getExistingConfigFile(String configName) {
        File file = new File(SettingsBean.getInstance().getJahiaVarDiskPath(), "karaf/etc/" + configName);
        if (!file.exists()) {
            file = new File(SettingsBean.getInstance().getJahiaVarDiskPath(), "modules/" + configName);
        }
        return file;
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

    @Reference(service = ExternalUserGroupService.class)
    public void setExternalUserGroupService(ExternalUserGroupService externalUserGroupService) {
        this.externalUserGroupService = externalUserGroupService;
    }

    @Reference(service = JahiaLDAPConfigFactory.class)
    public void setJahiaLDAPConfigFactory(JahiaLDAPConfigFactory jahiaLDAPConfigFactory) {
        this.jahiaLDAPConfigFactory = jahiaLDAPConfigFactory;
    }

    @Reference(service = ConfigurationAdmin.class)
    public void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    public LdapTemplateWrapper getLdapTemplateWrapper(String providerKey) {
        return jahiaLDAPConfigFactory.getLdapTemplateWrapper(providerKey);
    }
}
