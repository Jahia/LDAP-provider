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
 * LdapTemplate wrapper that wrap all the call to the ldapTemplate object
 * You need to use this class instead of use directly the ldapTemplate in order to react to the communication issue with the ldap server
 * @author kevan
 */
public class LdapTemplateWrapper {
    private LdapTemplate ldapTemplate;

    public LdapTemplateWrapper(LdapTemplate ldapTemplate) {
        this.ldapTemplate = ldapTemplate;
    }

    public <X> X execute(LdapTemplateCallback<X> callback) {
        try {
            X x = callback.doInLdap(ldapTemplate);
            callback.onSuccess();
            return x;
        } catch (Exception e) {
            return callback.onError(e);
        }
    }

    public void setLdapTemplate(LdapTemplate ldapTemplate) {
        this.ldapTemplate = ldapTemplate;
    }
}
