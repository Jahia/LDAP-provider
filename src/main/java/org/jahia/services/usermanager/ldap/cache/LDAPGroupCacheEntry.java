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
package org.jahia.services.usermanager.ldap.cache;

import org.jahia.modules.external.users.Member;
import org.jahia.services.usermanager.JahiaGroup;

import java.io.Serializable;
import java.util.List;

/**
 * Represents an entry in the LDAP grouo cache.
 * 
 * @author kevan
 */
public class LDAPGroupCacheEntry extends LDAPAbstractCacheEntry implements Serializable{
    private static final long serialVersionUID = -3585067276227107907L;

    private JahiaGroup group;
    private List<Member> members;
    private boolean isDynamic = false;
    private String dynamicMembersURL;

    public LDAPGroupCacheEntry(String name) {
        setName(name);
    }

    public List<Member> getMembers() {
        return members;
    }

    public void setMembers(List<Member> members) {
        this.members = members;
    }

    public JahiaGroup getGroup() {
        return group;
    }

    public void setGroup(JahiaGroup group) {
        this.group = group;
    }

    public boolean isDynamic() {
        return isDynamic;
    }

    public void setDynamic(boolean isDynamic) {
        this.isDynamic = isDynamic;
    }

    public String getDynamicMembersURL() {
        return dynamicMembersURL;
    }

    public void setDynamicMembersURL(String dynamicMembersURL) {
        this.dynamicMembersURL = dynamicMembersURL;
    }
}
