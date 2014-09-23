package org.jahia.services.usermanager.ldap;

import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

/**
 * Unbind configuration at module start/stop
 */
public class JahiaLDAPConfigManager {
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
    // do nothing
        }
    }

    public void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

}
