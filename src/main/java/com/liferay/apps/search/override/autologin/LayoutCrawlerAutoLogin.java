package com.liferay.apps.search.override.autologin;

import com.liferay.apps.search.override.crawler.LayoutCrawler;
import com.liferay.apps.search.override.util.SearchKeys;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.model.role.RoleConstants;
import com.liferay.portal.kernel.security.auto.login.AutoLogin;
import com.liferay.portal.kernel.security.auto.login.BaseAutoLogin;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.RoleLocalService;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@Component(immediate = true, service = AutoLogin.class)
public class LayoutCrawlerAutoLogin extends BaseAutoLogin {

    @Override
    protected String[] doLogin(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String headerKey = request.getHeader(SearchKeys.AUTOLOGIN_KEY);
        if (Validator.isBlank(headerKey)) {
            return new String[0];
        }
        String autoLoginKey = layoutCrawler.getAutoLoginKey();
        if (!StringUtil.equals(autoLoginKey, headerKey)) {
            return new String[0];
        }
        // Login default crawler user
        String[] credentials = new String[3];
        User crawlerUser = getCrawlerUser();
        credentials[0] = String.valueOf(crawlerUser.getUserId());
        credentials[1] = crawlerUser.getPassword();
        credentials[2] = Boolean.TRUE.toString();
        return credentials;
    }

    private User getCrawlerUser() {
        if (defaultAdmin == null) {
            long companyId = getDefaultCompanyId();
            Role adminRole = roleLocalService.fetchRole(companyId, RoleConstants.ADMINISTRATOR);
            List<User> adminUsers = userLocalService.getRoleUsers(adminRole.getRoleId(), QueryUtil.ALL_POS, QueryUtil.ALL_POS);
            if (ListUtil.isNotEmpty(adminUsers)) {
                defaultAdmin = adminUsers.get(0);
            }
        }
        return defaultAdmin;
    }

    private long getDefaultCompanyId() {
        List<Company> companies = companyLocalService.getCompanies(QueryUtil.ALL_POS, QueryUtil.ALL_POS);
        Company defaultCompany = companies.get(0);
        return defaultCompany.getCompanyId();
    }

    private User defaultAdmin;

    @Reference
    private CompanyLocalService companyLocalService;
    @Reference
    private UserLocalService userLocalService;
    @Reference
    private RoleLocalService roleLocalService;
    @Reference
    private LayoutCrawler layoutCrawler;


}
