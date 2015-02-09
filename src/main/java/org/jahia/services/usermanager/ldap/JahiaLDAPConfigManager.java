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
 *     Rooted in Open Source CMS, Jahia's Digital Industrialization paradigm is about
 *     streamlining Enterprise digital projects across channels to truly control
 *     time-to-market and TCO, project after project.
 *     Putting an end to "the Tunnel effect", the Jahia Studio enables IT and
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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unbind configuration at module start/stop
 */
public class JahiaLDAPConfigManager {

    private static Logger logger = LoggerFactory.getLogger(JahiaLDAPConfigManager.class);

    private ConfigurationAdmin configurationAdmin;

    public void start() {
        unbindConfiguration(true);
    }

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
        Long timestamp = (Long) configuration.getProperties().get("felix.fileinstall.source.timestamp");
        if (fileLocation != null && fileLocation.startsWith("file:")) {
            try {
                File source = new File(new URI(fileLocation));
                if (!source.exists() || timestamp == null || source.lastModified() > timestamp) {
                    // the configuration file was either deleted or is outdated -> delete the persisted configuration
                    try {
                        configuration.delete();
                        logger.info("Deleting persisted LDAP configuration " + configuration.getPid() + " (location: "
                                + fileLocation + ") as the correspondign file was either deleted or is outdated.");
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
