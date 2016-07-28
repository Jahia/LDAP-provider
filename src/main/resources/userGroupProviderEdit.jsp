<%@ page import="org.jahia.data.templates.JahiaTemplatesPackage" %>
<%@ page import="org.jahia.registries.ServicesRegistry" %>
<%@ page import="org.jahia.services.render.Resource" %>
<%@ page import="org.jahia.services.templates.JahiaTemplateManagerService" %>
<%@ page import="org.jahia.utils.i18n.ResourceBundles" %>
<%@ page import="javax.servlet.jsp.jstl.fmt.LocalizationContext" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.jahia.bin.ActionResult" %>
<%@ page import="org.osgi.service.cm.Configuration" %>
<%@ page import="java.util.*" %>
<%@ page import="org.jahia.services.usermanager.ldap.JahiaLDAPConfig" %>
<%@ page import="org.jahia.services.usermanager.ldap.JahiaLDAPConfigFactory" %>
<%@ page import="org.osgi.service.cm.ConfigurationAdmin" %>
<%@ page language="java" contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="jcr" uri="http://www.jahia.org/tags/jcr" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="query" uri="http://www.jahia.org/tags/queryLib" %>
<%@ taglib prefix="functions" uri="http://www.jahia.org/tags/functions" %>
<%--@elvariable id="providerKey" type="java.lang.String"--%>

<%
    Resource currentResource = (Resource) pageContext.findAttribute("currentResource");
    JahiaTemplatesPackage ldap = ServicesRegistry.getInstance().getJahiaTemplateManagerService().getTemplatePackageById("ldap");
    ResourceBundle rb = ResourceBundles.get(ldap, currentResource.getLocale());
    LocalizationContext ctx = new LocalizationContext(rb);
    pageContext.setAttribute("bundle", ctx);
    String providerKey = (String) pageContext.findAttribute("providerKey");
    Map<String,String> previousProperties = (Map<String,String>) pageContext.findAttribute("ldapProperties");
    System.out.println(previousProperties);
    List<String> defaultProperties  = (List<String>) ldap.getContext().getBean("defaultProperties");
    pageContext.setAttribute("defaultProperties", defaultProperties);

    JahiaLDAPConfigFactory jahiaLDAPConfigFactory = (JahiaLDAPConfigFactory) ldap.getContext().getBean("JahiaLDAPConfigFactory");
    ConfigurationAdmin configurationAdmin = (ConfigurationAdmin) ldap.getContext().getBean("configurationAdmin");

    Dictionary<String, Object> properties = null;
    if (providerKey != null) {
        String pid = jahiaLDAPConfigFactory.getConfigPID(providerKey);
        Configuration configuration = configurationAdmin.getConfiguration(pid);
        properties = configuration.getProperties();
    }

    Map<String, String> res = new LinkedHashMap<String, String>();
    for (String key : defaultProperties) {
        if (previousProperties != null && previousProperties.containsKey(key)) {
            res.put(key, previousProperties.get(key));
        } else if (properties != null && properties.get(key) != null) {
            res.put(key, (String) properties.get(key));
        } else {
            res.put(key, "");
        }
    }
    Map<String, String> sorted = new TreeMap<String, String>();

    if (previousProperties != null) {
        for (String key : previousProperties.keySet()) {
            if (!defaultProperties.contains(key) && !JahiaLDAPConfig.LDAP_PROVIDER_KEY_PROP.equals(key)) {
                sorted.put(key, previousProperties.get(key));
            }
        }
    }

    if (properties != null) {
        Enumeration<String> keys = properties.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            if (!sorted.containsKey(key) && !defaultProperties.contains(key) && !key.startsWith("service.") && !key.startsWith("felix.") && !JahiaLDAPConfig.LDAP_PROVIDER_KEY_PROP.equals(key)) {
                sorted.put(key, (String) properties.get(key));
            }
        }
    }
    res.putAll(sorted);

    pageContext.setAttribute("ldapProperties", res);

%>
<jcr:jqom statement="select * from [jnt:virtualsite] as site where ischildnode(site,'/sites') and localname(site) <> 'systemsite'" var="sites"/>
<datalist id="sites">
    <c:forEach items="${sites.nodes}" var="site">
        <option value="${site.name}"/>
    </c:forEach>
</datalist>
<template:addResources type="javascript" resources="jquery.min.js,jquery.form.min.js"/>
<template:addResources>
    <script type="text/javascript">
        function addField() {
            $("<div class=\"row-fluid\">" +
            "<div class=\"span4\"><input type=\"text\" name=\"propKey\" value=\"\" required class=\"span12\"/></div>" +
            "<div class=\"span7\"><input type=\"text\" name=\"propValue\" value=\"\" class=\"span12\"/></div>" +
            "<div class=\"span1\"><a class=\"btn\" onclick=\"$(this).parent().parent().remove()\"><i class=\"icon icon-minus\"></i></a></div>" +
            "</div>").insertBefore($("#addField${currentNode.identifier}"));
        }
    </script>
</template:addResources>

<fieldset class="box-1">
    <label>
        <div class="row-fluid">
            <a class="btn" href="<c:url value='https://www.jahia.com/get-started/for-developers/developers-techwiki/users-and-groups/ldap-connector-7-1'/>" target="_blank"><fmt:message bundle="${bundle}" key="ldap.provider.documentation"/></a>
        </div>
    </label>
    <c:if test="${empty providerKey}">
    <label>
        <div class="row-fluid">
            <div class="span4">
                <fmt:message bundle="${bundle}" key="ldap.provider.name"/>
            </div>
            <div class="span8">
                <input type="text" name="configName" value="${configName}" />
            </div>
        </div>
    </label>
    </c:if>
    <c:forEach var="property" items="${ldapProperties}">
        <label>
            <div class="row-fluid">
                <div class="span4">
                    <fmt:message bundle="${bundle}" key="ldap.provider.${property.key}" var="label" />
                    <c:if test="${fn:startsWith(label,'???')}">
                        ${property.key}
                    </c:if>
                    <c:if test="${not fn:startsWith(label,'???')}">
                        ${label} ( ${property.key} )
                    </c:if>
                </div>
                <div class="span7">
                    <input type="${fn:containsIgnoreCase(property.key, 'password')?'password':'text'}" name="propValue.${property.key}" value="${property.value}" ${property.key eq 'target.site'? 'list="sites"' : 'class="span12"'}/>
                </div>
                <c:if test="${not functions:contains(defaultProperties, property.key)}">
                    <div class="span1">
                        <a class="btn" onclick="$(this).parent().parent().remove()"><i class="icon icon-minus"></i></a>
                    </div>
                </c:if>
            </div>
        </label>
    </c:forEach>
    <a id="addField${currentNode.identifier}" class="btn" onclick="addField()"><i class="icon icon-plus"></i></a>
</fieldset>
