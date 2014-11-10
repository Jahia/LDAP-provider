<%@ page language="java" contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="jcr" uri="http://www.jahia.org/tags/jcr" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%--@elvariable id="providerKey" type="java.lang.String"--%>

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

        $(document).ready(function () {
            $.ajax({
                type : 'get',
                url: '<c:url value="${url.base}${currentNode.path}.getLdapConfiguration.do"/>',
                <c:if test="${not empty providerKey}">
                data: [{name:'providerKey', value:'${providerKey}'}],
                </c:if>
                dataType: "json",
                success: function(data, textStatus, jqXHR) {
                    $.each(data, function(key, value) {
                        $("<div class=\"row-fluid\"><div class=\"span4\"><input type=\"text\" name=\"propKey\" value=\"" +
                                key +
                                "\"" +
                                (value.required == "true" ? " readonly " : "") +
                                " required class=\"span12\"/></div><div class=\"span7\"><input type=\"text\" name=\"propValue\" value=\"" +
                                value.value +
                                "\"" +
                                (value.required == "true" ? " required " : "") +
                                "class=\"span12\"/></div>" +
                                (value.required == "true" ? "" : "<div class=\"span1\"><a class=\"btn\" onclick=\"$(this).parent().parent().remove()\"><i class=\"icon icon-minus\"></i></a></div>") +
                                "</div>").insertBefore($("#addField${currentNode.identifier}"));
                    });
                },
                error: function(jqXHR, textStatus, errorThrown) {
                }
            });
        });
    </script>
</template:addResources>

<fieldset class="box-1">
    <c:if test="${empty providerKey}">
        <label>
            <div class="row-fluid">
                <div class="span4">
                    <fmt:message key="label.name"/>
                </div>
                <div class="span8">
                    ldap.<input type="text" name="configName" required />
                </div>
            </div>
        </label>
    </c:if>
    <a id="addField${currentNode.identifier}" class="btn" onclick="addField()"><i class="icon icon-plus"></i></a>
</fieldset>
