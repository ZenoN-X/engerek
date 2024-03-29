/*
 * Copyright (c) 2010-2015 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.web.page.admin.certification.dto;

import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.schema.util.CertCampaignTypeUtil;
import com.evolveum.midpoint.web.component.data.column.InlineMenuable;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItem;
import com.evolveum.midpoint.web.component.util.Selectable;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.web.page.PageBase;
import com.evolveum.midpoint.web.util.WebMiscUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCampaignStateType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCampaignType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationStageType;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.StringResourceModel;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.datatype.XMLGregorianCalendar;
import java.util.ArrayList;
import java.util.List;

/**
 * @author mederly
 */
public class CertCampaignListItemDto extends Selectable implements InlineMenuable {

    public static final String F_NAME = "name";
    public static final String F_DESCRIPTION = "description";
    public static final String F_STATE = "state";
    public static final String F_CURRENT_STAGE_NUMBER = "currentStageNumber";
    public static final String F_NUMBER_OF_STAGES = "numberOfStages";
    public static final String F_DEADLINE_AS_STRING = "deadlineAsString";

    private AccessCertificationCampaignType campaign;           // TODO replace by elementary items
    private List<InlineMenuItem> menuItems;
    private String deadlineAsString;

    public CertCampaignListItemDto(AccessCertificationCampaignType campaign, PageBase page) {
        this.campaign = campaign;
        deadlineAsString = computeDeadlineAsString(page);
    }

    @Override
    public List<InlineMenuItem> getMenuItems() {
        if (menuItems == null) {
            menuItems = new ArrayList<>();
        }
        return menuItems;
    }

    public String getName() {
        return WebMiscUtil.getName(campaign);
    }

    public String getOid() {
        return campaign.getOid();
    }

    public AccessCertificationCampaignStateType getState() {
        return campaign.getState();
    }

    public String getDescription() {
        return campaign.getDescription();
    }

    public Integer getCurrentStageNumber() {
        int currentStage = campaign.getStageNumber();
        if (AccessCertificationCampaignStateType.IN_REVIEW_STAGE.equals(campaign.getState()) ||
                AccessCertificationCampaignStateType.REVIEW_STAGE_DONE.equals(campaign.getState())) {
            return currentStage;
        } else {
            return null;
        }
    }

    public Integer getNumberOfStages() {
        return CertCampaignTypeUtil.getNumberOfStages(campaign);
    }

    private String computeDeadlineAsString(PageBase page) {
        AccessCertificationStageType currentStage = CertCampaignTypeUtil.getCurrentStage(campaign);
        XMLGregorianCalendar end;
        Boolean stageLevelInfo;
        if (campaign.getStageNumber() == 0) {
            end = campaign.getEnd();
            stageLevelInfo = false;
        } else if (currentStage != null) {
            end = currentStage.getEnd();
            stageLevelInfo = true;
        } else {
            end = null;
            stageLevelInfo = null;
        }

        if (end == null) {
            return "";
        } else {
            long delta = XmlTypeConverter.toMillis(end) - System.currentTimeMillis();

            // round to hours; we always round down
            long precision = 3600000L;      // 1 hour
            if (Math.abs(delta) > precision) {
                delta = (delta / precision) * precision;
            }

            //todo i18n for durations
            if (delta > 0) {
                String key = stageLevelInfo ? "PageCertCampaigns.inForStage" : "PageCertCampaigns.inForCampaign";
                return new StringResourceModel(key, page, null, null,
                        DurationFormatUtils.formatDurationWords(delta, true, true)).getString();
            } else if (delta < 0) {
                String key = stageLevelInfo ? "PageCertCampaigns.agoForStage" : "PageCertCampaigns.agoForCampaign";
                return new StringResourceModel(key, page, null, null,
                        DurationFormatUtils.formatDurationWords(-delta, true, true)).getString();
            } else {
                String key = stageLevelInfo ? "PageCertCampaigns.nowForStage" : "PageCertCampaigns.nowForCampaign";
                return page.getString(key);
            }
        }
    }

    // TODO remove this
    public AccessCertificationCampaignType getCampaign() {
        return campaign;
    }
}
