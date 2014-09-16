package org.jahia.services.usermanager.ldap;

import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unbind configuration at module start/stop
 */
public class JahiaLDAPConfigManager {
    public static final Logger logger = LoggerFactory.getLogger(JahiaLDAPConfigFactory.class);
    private ConfigurationAdmin configurationAdmin;

    public void start() {
        unbindConfiguration();
    }

    public void stop() {
        unbindConfiguration();
    }

    private void unbindConfiguration() {
        try {
            Configuration[] configurations = configurationAdmin.listConfigurations("(service.factoryPid=org.jahia.services.usermanager.ldap)");
            if (configurations != null) {
                for (Configuration configuration : configurations) {
                    configuration.setBundleLocation(null);
                }
            }
        } catch (Exception e) {
            logger.error("Cannot unbind configurations",e);
        }
    }

    public void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

}
