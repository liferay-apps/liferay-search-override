package com.liferay.apps.search.override;

import com.liferay.apps.search.override.crawler.LayoutCrawler;
import com.liferay.layout.page.template.model.LayoutPageTemplateStructure;
import com.liferay.layout.page.template.service.LayoutPageTemplateStructureLocalService;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.search.Document;
import com.liferay.portal.kernel.search.Field;
import com.liferay.portal.kernel.util.Html;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.search.spi.model.index.contributor.ModelDocumentContributor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Locale;
import java.util.Set;

@Component(
		immediate = true,
		property = {
				"service.ranking:Integer=101",
				"indexer.class.name=com.liferay.portal.kernel.model.Layout",
		},
		service = ModelDocumentContributor.class
)
public class LayoutModelDocumentContributorOverride implements ModelDocumentContributor<Layout> {

	@Override
	public void contribute(Document document, Layout layout) {
		if (layout.isSystem() ||
				(layout.getStatus() != WorkflowConstants.STATUS_APPROVED)) {

			return;
		}

		document.addText(
				Field.DEFAULT_LANGUAGE_ID, layout.getDefaultLanguageId());
		document.addLocalizedText(Field.NAME, layout.getNameMap());
		document.addText(
				"privateLayout", String.valueOf(layout.isPrivateLayout()));
		document.addText(Field.TYPE, layout.getType());

		LayoutPageTemplateStructure layoutPageTemplateStructure =
				_layoutPageTemplateStructureLocalService.
						fetchLayoutPageTemplateStructure(
								layout.getGroupId(), layout.getPlid());

		for (String languageId : layout.getAvailableLanguageIds()) {
			Locale locale = LocaleUtil.fromLanguageId(languageId);

			document.addText(
					Field.getLocalizedName(locale, Field.TITLE),
					layout.getName(locale));
		}

		if (layoutPageTemplateStructure == null) {
			return;
		}

		Set<Locale> locales = LanguageUtil.getAvailableLocales(
				layout.getGroupId());

		for (Locale locale : locales) {
			String content = prepareContent(_layoutCrawler.getLayoutContent(layout, locale));
			if (Validator.isNull(content)) {
				continue;
			}
			document.addText(Field.getLocalizedName(locale, Field.CONTENT), content);
		}
	}

	private String prepareContent(String content) {
		if (Validator.isNull(content)) {
			return content;
		}
		// Start Page content pre-processing
		org.jsoup.nodes.Document htmlDocument = Jsoup.parse(content, "UTF-8");
		Element contentEl = htmlDocument.getElementById("content");
		if (contentEl != null) {
			content = contentEl.toString();
			org.jsoup.nodes.Document contentDocument = Jsoup.parse(content, "UTF-8");
			// Cut off portlets code
			Elements portlets = contentDocument.select(".portlet");
			for (Element portlet : portlets) {
				portlet.remove();
			}
			content = contentDocument.toString();
		}
		// End Page content pre-processing
		content = _html.stripHtml(content);
		content = content.replaceAll("_", "");
		content = content.replaceAll("\\s{2,}", " ").trim();
		return content;
	}

	@Reference
	private Html _html;

	@Reference
	private LayoutCrawler _layoutCrawler;

	@Reference
	private LayoutPageTemplateStructureLocalService _layoutPageTemplateStructureLocalService;

}