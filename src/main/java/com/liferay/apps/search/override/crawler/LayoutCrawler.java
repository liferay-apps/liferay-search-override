package com.liferay.apps.search.override.crawler;

import com.liferay.apps.search.override.threadlocal.SessionIdThreadLocal;
import com.liferay.apps.search.override.util.SearchKeys;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextThreadLocal;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.Validator;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;


@Component(immediate = true, service = LayoutCrawler.class)
public class LayoutCrawler {

    private String autoLoginKey = StringPool.BLANK;

    public String getLayoutContent(Layout layout, Locale locale) {
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();

        HttpClient httpClient = httpClientBuilder.setUserAgent(
                _USER_AGENT
        ).build();

        try {
            InetAddress inetAddress = _portal.getPortalServerInetAddress(false);

            ThemeDisplay themeDisplay = new ThemeDisplay();

            Company company = _companyLocalService.getCompany(
                    layout.getCompanyId());

            themeDisplay.setCompany(company);

            themeDisplay.setLanguageId(LocaleUtil.toLanguageId(locale));
            themeDisplay.setLayout(layout);
            themeDisplay.setLayoutSet(layout.getLayoutSet());
            themeDisplay.setLocale(locale);
            themeDisplay.setScopeGroupId(layout.getGroupId());
            themeDisplay.setServerName(inetAddress.getHostName());
            themeDisplay.setServerPort(_portal.getPortalServerPort(false));
            themeDisplay.setSiteGroupId(layout.getGroupId());

            HttpGet httpGet = new HttpGet(_portal.getLayoutFullURL(layout, themeDisplay));

            // --------------- Start Customization ------------------------------
            HttpServletRequest sessionRequest = null;
            HttpResponse httpResponse = null;
            ServiceContext serviceContext = ServiceContextThreadLocal.getServiceContext();
            if (serviceContext != null) {
                sessionRequest = serviceContext.getRequest();
            }
            if (sessionRequest != null) {
                // Use current users' session (if available)
                httpGet.setHeader(SearchKeys.COOKIE_NAME, sessionRequest.getHeader(SearchKeys.COOKIE_NAME));
                httpResponse = httpClient.execute(httpGet);
            } else {
                // Use default user's session otherwise
                // (auto-login first time, and re-use sessionId in subsequent calls)
                String sessionId = SessionIdThreadLocal.getSessionId();
                if (Validator.isNull(sessionId)) {
                    // Set "autoLoginKey" header to initiate the auto-login flow
                    autoLoginKey = UUID.randomUUID().toString();
                    httpGet.setHeader(SearchKeys.AUTOLOGIN_KEY, autoLoginKey);
                    httpResponse = httpClient.execute(httpGet);
                    // Save sessionId to thread-local to re-use session in subsequent calls
                    sessionId = getSessionId(httpResponse);
                    SessionIdThreadLocal.setSessionId(sessionId);
                } else {
                    // Use sessionId from thread-local
                    String cookieSessionId = String.format(SearchKeys.JSESSIONID_COOKIE, sessionId);
                    httpGet.setHeader(SearchKeys.COOKIE_NAME, cookieSessionId);
                    httpResponse = httpClient.execute(httpGet);
                }
            }
            // --------------- End Customization -----------------------------------

            StatusLine statusLine = httpResponse.getStatusLine();

            if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
                return EntityUtils.toString(httpResponse.getEntity());
            }

            if (_log.isWarnEnabled()) {
                _log.warn("Unable to get layout content");
            }
        }
        catch (Exception exception) {
            if (_log.isWarnEnabled()) {
                _log.warn("Unable to get layout content", exception);
            }
        }

        return StringPool.BLANK;
    }

    private String getSessionId(HttpResponse httpResponse) {
        String sessionId = StringPool.BLANK;
        Header[] headers = httpResponse.getAllHeaders();
        Optional<Header> sessionHeaderOptional = Arrays
                .stream(headers)
                .filter(h -> h.toString().contains(SearchKeys.JSESSIONID))
                .findFirst();
        if (sessionHeaderOptional.isPresent()) {
            Header sessionHeader = sessionHeaderOptional.get();
            Optional<String> sessionIdOptional = Arrays.stream(sessionHeader.getElements())
                    .filter(e -> e.getName().equals(SearchKeys.JSESSIONID))
                    .map(HeaderElement::getValue)
                    .findFirst();
            if (sessionIdOptional.isPresent()) {
                sessionId = sessionIdOptional.get();
            }
        }
        return sessionId;
    }

    public String getAutoLoginKey() {
        return autoLoginKey;
    }

    private static final String _USER_AGENT = "Liferay Page Crawler";

    private static final Log _log = LogFactoryUtil.getLog(LayoutCrawler.class);

    @Reference
    private CompanyLocalService _companyLocalService;

    @Reference
    private Portal _portal;

}
