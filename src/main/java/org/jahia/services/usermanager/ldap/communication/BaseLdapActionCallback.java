/**
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *                                 http://www.jahia.com
 *
 *     Copyright (C) 2002-2016 Jahia Solutions Group SA. All rights reserved.
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
package org.jahia.services.usermanager.ldap.communication;

import org.jahia.modules.external.users.ExternalUserGroupService;
import org.jahia.services.content.decorator.JCRMountPointNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.CommunicationException;
import org.springframework.ldap.InsufficientResourcesException;
import org.springframework.ldap.ServiceUnavailableException;
import org.springframework.ldap.core.LdapTemplate;

/**
 * Base LDAP template action callback that unmounts the LDAP provider in case of communication issue with the LDAP server
 * using the onError method.
 * Feel free to use it, implementing at least the doInLdap to wrap the call to the ldapTemplate object.
 *
 * @author kevan
 */
public abstract class BaseLdapActionCallback<T> implements LdapTemplateCallback<T> {
    private static Logger logger = LoggerFactory.getLogger(BaseLdapActionCallback.class);
    private final ExternalUserGroupService externalUserGroupService;
    private final String key;

    protected BaseLdapActionCallback(ExternalUserGroupService externalUserGroupService, String key) {
        this.externalUserGroupService = externalUserGroupService;
        this.key = key;
    }

    @Override
    public abstract T doInLdap(LdapTemplate ldapTemplate);

    @Override
    public T onError(Exception e)  {
        final Throwable cause = e.getCause();
        logger.error("An error occurred while communicating with the LDAP server " + key, e);
        if (cause instanceof javax.naming.CommunicationException || cause instanceof javax.naming.NamingException || cause instanceof CommunicationException || cause instanceof ServiceUnavailableException || cause instanceof InsufficientResourcesException) {
            externalUserGroupService.setMountStatus(key, JCRMountPointNode.MountStatus.waiting, cause.getMessage());
        } else {
            externalUserGroupService.setMountStatus(key, JCRMountPointNode.MountStatus.error, e.getMessage());
        }

        return null;
    }
}
