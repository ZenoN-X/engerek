/*
 * Copyright (c) 2010-2014 Evolveum
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

package com.evolveum.midpoint.web.page.admin.workflow;

import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.web.application.AuthorizationAction;
import com.evolveum.midpoint.web.application.PageDescriptor;

/**
 * @author lazyman
 */
@PageDescriptor(url = "/admin/workItemsClaimable", action = {
        @AuthorizationAction(actionUri = PageAdminWorkItems.AUTH_WORK_ITEMS_ALL,
                label = PageAdminWorkItems.AUTH_WORK_ITEMS_ALL_LABEL,
                description = PageAdminWorkItems.AUTH_WORK_ITEMS_ALL_DESCRIPTION),
        @AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_WORK_ITEMS_URL,
                label = "PageWorkItemsClaimable.auth.workItems.label",
                description = "PageWorkItemsClaimable.auth.workItems.description")})
public class PageWorkItemsClaimable extends PageWorkItems {

    public PageWorkItemsClaimable() {
        super(false);
    }

}
