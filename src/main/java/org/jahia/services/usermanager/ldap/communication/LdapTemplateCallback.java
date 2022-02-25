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
package org.jahia.services.usermanager.ldap.communication;

import org.springframework.ldap.core.LdapTemplate;

/**
 * Interface that define ldap action(s) call(s) using ldapTemplate
 * @author kevan
 */
public interface LdapTemplateCallback <T> {
    /**
     * do whatever you want in ldap using ldapTemplate
     * @param ldapTemplate ldapTemplate is provided
     * @return
     */
    T doInLdap(LdapTemplate ldapTemplate);

    /**
     * called in case of success
     * @return
     */
    void onSuccess();

    /**
     * Will be call in case of Exception in the doInLdap function
     * @param e the Exception that trigger the onError.
     * @return
     */
    T onError(Exception e);
}