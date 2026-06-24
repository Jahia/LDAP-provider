/*
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *                                 http://www.jahia.com
 *
 *     Copyright (C) 2002-2020 Jahia Solutions Group SA. All rights reserved.
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


import org.jahia.modules.external.users.ExternalUserGroupService;
import org.jahia.modules.external.users.UserGroupProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base abstract implementation of a user / group provider.
 * 
 * @author Sergiy Shyrkov
 * TODO should be moved to external-provider-users-groups module
 */
public abstract class LDAPBaseUserGroupProvider implements UserGroupProvider {
    private static final Logger logger = LoggerFactory.getLogger(LDAPBaseUserGroupProvider.class);

    private ExternalUserGroupService externalUserGroupService;
    private LDAPExternalUserGroupServiceListener externalUserGroupServiceListener;
    private BundleContext bundleContext;

    private String key;

    protected ExternalUserGroupService getExternalUserGroupService() {
        return externalUserGroupService;
    }

    protected String getKey() {
        return key;
    }

    protected String getSiteKey() {
        return null;
    }

    public void register() {
        externalUserGroupService.register(getKey(), getSiteKey(), this);

        // create listener if null
        if (externalUserGroupServiceListener == null) {
            externalUserGroupServiceListener = new LDAPExternalUserGroupServiceListener(this, bundleContext);
        }
        // start listener to react on service availability
        try {
            bundleContext.addServiceListener(externalUserGroupServiceListener, "(objectclass=" + ExternalUserGroupService.class.getName() + ")");
        } catch (InvalidSyntaxException e) {
            logger.error("Error adding service listener for ExternalUserGroupService", e);
        }
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void unregister() {
        externalUserGroupService.unregister(getKey());
        // remove listener, no need to react on service availability since current provider is unregistered manually
        if (externalUserGroupServiceListener != null) {
            bundleContext.removeServiceListener(externalUserGroupServiceListener);
        }
    }

    public void setExternalUserGroupService(ExternalUserGroupService externalUserGroupService) {
        this.externalUserGroupService = externalUserGroupService;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }
}
