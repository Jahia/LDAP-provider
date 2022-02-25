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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unbind configuration at module start/stop
 */
@Component(service = {JahiaLDAPConfigManager.class}, immediate = true)
public class JahiaLDAPConfigManager {

    private static Logger logger = LoggerFactory.getLogger(JahiaLDAPConfigManager.class);

    private ConfigurationAdmin configurationAdmin;

    @Activate
    public void start() {
        unbindConfiguration(true);
    }

    @Deactivate
    public void stop() {
        unbindConfiguration(false);
    }

    private void unbindConfiguration(boolean verify) {
        try {
            Configuration[] configurations = configurationAdmin
                    .listConfigurations("(service.factoryPid=org.jahia.services.usermanager.ldap)");
            if (configurations != null) {
                for (Configuration configuration : configurations) {
                    configuration.setBundleLocation(null);
                    if (verify) {
                        verify(configuration);
                    }
                }
            }
        } catch (Exception e) {
            // do nothing
        }
    }

    private void verify(Configuration configuration) {
        String fileLocation = (String) configuration.getProperties().get("felix.fileinstall.filename");
        if (fileLocation != null && fileLocation.startsWith("file:")) {
            try {
                File source = new File(new URI(fileLocation));
                if (!source.exists()) {
                    // the configuration file was deleted -> delete the persisted configuration
                    try {
                        configuration.delete();
                        logger.info("Deleting persisted LDAP configuration " + configuration.getPid() + " (location: "
                                + fileLocation + ") as the correspondign file was deleted.");
                    } catch (IOException e) {
                        logger.error("Unable to delete persisted LDAP condifguration " + configuration.getPid()
                                + " (location: " + fileLocation + ")", e);
                    }
                }
            } catch (URISyntaxException e1) {
                logger.error(e1.getMessage(), e1);
            }
        }
    }

    public void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

}
