<%@ include file="/WEB-INF/jsp/includes/taglibs.jsp" %>
<%@ page import="org.joget.apps.app.service.AppUtil"%>
<%@ page import="org.joget.commons.util.SecurityUtil"%>
<%@ page import="java.io.File,org.joget.commons.util.SetupManager"%>
<%@ page import="org.joget.commons.util.HostManager"%>

<c:set var="isVirtualHostEnabled" value="<%= HostManager.isVirtualHostEnabled() %>"/>
<c:set var="isNonceSupported" value="<%= SecurityUtil.getNonceGenerator() != null %>"/>

<commons:header />
<style>
    .row-content{
        display: block;
        float: none;
    }

    .form-input{
        width: 50%
    }

    .form-input input, .form-input textarea{
        width: 100%
    }

    .row-title{
        font-weight: bold;
    }
</style>
<div id="nav">
    <div id="nav-title">
        <p><i class="fa fa-cogs"></i> <fmt:message key='console.header.top.label.settings'/></p>
    </div>
    <div id="nav-body">
        <ul id="nav-list">
            <jsp:include page="subMenu.jsp" flush="true" />
        </ul>
    </div>
</div>

<div id="main">
    <div id="main-title"></div>
    <div id="main-action">
    </div>
    <div id="main-body">
        <div id="generalSetup">
            <form method="post" action="${pageContext.request.contextPath}/web/console/setting/wallet/submit">                
                <div class="main-body-content-subheader">
                    <span><fmt:message key="console.setting.wallet.header.uiSetting"/></span>
                </div>                
                <div class="main-body-row">
                    <span class="row-content">
                        <div class="form-row">
                            <label for="walletName"><fmt:message key="console.setting.wallet.label.walletName"/></label>
                            <span class="form-input">
                                <input id="defaultWalletName" type="text" name="walletName" value="<c:out value="${walletMap['walletName']}"/>"/>                                
                            </span>
                        </div>
                    </span>
                </div>
				<div class="main-body-row">
                    <span class="row-content">
                        <div class="form-row">
                            <label for="walletAddress"><fmt:message key="console.setting.wallet.label.walletAddress"/></label>
                            <span class="form-input">
                                <input id="defaultWalletAddress" type="text" name="walletAddress" value="<c:out value="${walletMap['walletAddress']}"/>"/>                                
                            </span>
                        </div>
                    </span>
                </div>
				<div class="main-body-row">
                    <span class="row-content">
                        <div class="form-row">
                            <label for="publicKey"><fmt:message key="console.setting.wallet.label.publicKey"/></label>
                            <span class="form-input">
                                <input id="defaultPublicKey" type="text" name="publicKey" value="<c:out value="${walletMap['publicKey']}"/>"/>                                
                            </span>
                        </div>
                    </span>
                </div>
				<div class="main-body-row">
                    <span class="row-content">
                        <div class="form-row">
                            <label for="privateKey"><fmt:message key="console.setting.wallet.label.privateKey"/></label>
                            <span class="form-input">
                                <input id="defaultPrivateKey" type="text" name="privateKey" value="<c:out value="${walletMap['privateKey']}"/>"/>                                
                            </span>
                        </div>
                    </span>
                </div>
				<div class="main-body-row">
                    <span class="row-content">
                        <div class="form-row">
                            <label for="dfmsHost"><fmt:message key="console.setting.wallet.label.dfmsHost"/></label>
                            <span class="form-input">
                                <input id="defaultDfmsHost" type="text" name="dfmsHost" value="<c:out value="${walletMap['dfmsHost']}"/>"/>                                
                            </span>
                        </div>
                    </span>
                </div>
				<div class="main-body-row">
                    <span class="row-content">
                        <div class="form-row">
                            <label for="bcHost"><fmt:message key="console.setting.wallet.label.bcHost"/></label>
                            <span class="form-input">
                                <input id="defaultBcHost" type="text" name="bcHost" value="<c:out value="${walletMap['bcHost']}"/>"/>                                
                            </span>
                        </div>
                    </span>
                </div>
                <div class="form-buttons">
                    <input class="form-button" type="submit" value="<fmt:message key="general.method.label.save"/>" />
                </div>
            </form>
        </div>
    </div>
</div>

<script>
    //masterLoginHash
    var loginHashDeliminator = '<%= org.joget.directory.model.User.LOGIN_HASH_DELIMINATOR %>';
    if($('#masterLoginPassword').val() != '' && $('#masterLoginUsername').val() != ''){
    getLoginHash($('#masterLoginUsername').val(), $('#masterLoginPassword').val());
    }
    $('#masterLoginUsername, #masterLoginPassword').keyup(function(){
    if($('#masterLoginPassword').val() != '' && $('#masterLoginUsername').val() != ''){
    getLoginHash($('#masterLoginUsername').val(), $('#masterLoginPassword').val());
    }else{
    $('#masterLoginHash').text("-");
    }
    });
    function getLoginHash(username, password) {
    var callback = {
    success : function(o) {
    var o = eval("(" + o + ")");
    $('#masterLoginHash').text(o.hash);
    }
    }
    var params = "username=" + username + "&password=" + password;
    ConnectionManager.post('${pageContext.request.contextPath}/web/console/setting/general/loginHash', callback, params);
    }
</script>

<script>
    Template.init("", "#nav-setting-wallet");
</script>

<commons:footer />
