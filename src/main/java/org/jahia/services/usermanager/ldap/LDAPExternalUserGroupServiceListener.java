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
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OSGI service listener that react on ExternalUserGroupService service,
 * it's used by the providers that extends the BaseUserGroupProvider
 *
 * Automatically unregister and register back providers when service is remove and add again in the OSGI env
 * TODO should be moved to external-provider-users-groups module
 */
public class LDAPExternalUserGroupServiceListener implements ServiceListener {
    private static final Logger logger = LoggerFactory.getLogger(LDAPExternalUserGroupServiceListener.class);

    private LDAPBaseUserGroupProvider userGroupProvider;
    private BundleContext bundleContext;

    public LDAPExternalUserGroupServiceListener(LDAPBaseUserGroupProvider userGroupProvider, BundleContext bundleContext) {
        this.userGroupProvider = userGroupProvider;
        this.bundleContext = bundleContext;
    }

    @Override
    public void serviceChanged(ServiceEvent serviceEvent) {
        ExternalUserGroupService service;
        switch (serviceEvent.getType()) {
            default:
            case ServiceEvent.UNREGISTERING:
                logger.info("External user group service is gone, automatically unregister provider: " + userGroupProvider.getKey());
                service = (ExternalUserGroupService)
                        bundleContext.getService(serviceEvent.getServiceReference());

                service.unregister(userGroupProvider.getKey());
                break;
            case ServiceEvent.REGISTERED:
                logger.info("External user group service is back, automatically register provider: " + userGroupProvider.getKey());
                service = (ExternalUserGroupService)
                        bundleContext.getService(serviceEvent.getServiceReference());

                service.register(userGroupProvider.getKey(), userGroupProvider.getSiteKey(), userGroupProvider);
                break;
        }
    }
}