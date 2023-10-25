/*
 * Copyright (C) 2023 The Android Open Source Project
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
 *
 */

package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.Context
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.ConstraintSet.BOTTOM
import androidx.constraintlayout.widget.ConstraintSet.END
import androidx.constraintlayout.widget.ConstraintSet.PARENT_ID
import androidx.constraintlayout.widget.ConstraintSet.START
import androidx.constraintlayout.widget.ConstraintSet.TOP
import com.android.systemui.common.ui.ConfigurationState
import com.android.systemui.flags.FeatureFlagsClassic
import com.android.systemui.flags.Flags
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.res.R
import com.android.systemui.shade.NotificationPanelView
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.AlwaysOnDisplayNotificationIconViewStore
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.NotificationIconContainerViewBinder
import com.android.systemui.statusbar.notification.icon.ui.viewmodel.NotificationIconContainerAlwaysOnDisplayViewModel
import com.android.systemui.statusbar.phone.DozeParameters
import com.android.systemui.statusbar.phone.NotificationIconAreaController
import com.android.systemui.statusbar.phone.NotificationIconContainer
import com.android.systemui.statusbar.phone.ScreenOffAnimationController
import com.android.systemui.statusbar.policy.ConfigurationController
import javax.inject.Inject
import kotlinx.coroutines.DisposableHandle

class AodNotificationIconsSection
@Inject
constructor(
    private val context: Context,
    private val configurationState: ConfigurationState,
    private val configurationController: ConfigurationController,
    private val dozeParameters: DozeParameters,
    private val featureFlags: FeatureFlagsClassic,
    private val nicAodViewModel: NotificationIconContainerAlwaysOnDisplayViewModel,
    private val nicAodIconViewStore: AlwaysOnDisplayNotificationIconViewStore,
    private val notificationPanelView: NotificationPanelView,
    private val notificationIconAreaController: NotificationIconAreaController,
    private val screenOffAnimationController: ScreenOffAnimationController,
) : KeyguardSection() {

    private var nicBindingDisposable: DisposableHandle? = null
    private val nicId = R.id.aod_notification_icon_container
    private lateinit var nic: NotificationIconContainer

    override fun addViews(constraintLayout: ConstraintLayout) {
        if (!featureFlags.isEnabled(Flags.MIGRATE_KEYGUARD_STATUS_VIEW)) {
            return
        }
        nic =
            NotificationIconContainer(context, null).apply {
                id = nicId
                setPaddingRelative(
                    resources.getDimensionPixelSize(R.dimen.below_clock_padding_start_icons),
                    0,
                    0,
                    0
                )
                setVisibility(View.INVISIBLE)
            }

        constraintLayout.addView(nic)
    }

    override fun bindData(constraintLayout: ConstraintLayout) {
        if (!featureFlags.isEnabled(Flags.MIGRATE_KEYGUARD_STATUS_VIEW)) {
            return
        }

        if (featureFlags.isEnabled(Flags.NOTIFICATION_ICON_CONTAINER_REFACTOR)) {
            nic.setOnLockScreen(true)
            nicBindingDisposable?.dispose()
            nicBindingDisposable =
                NotificationIconContainerViewBinder.bind(
                    nic,
                    nicAodViewModel,
                    configurationState,
                    configurationController,
                    dozeParameters,
                    featureFlags,
                    screenOffAnimationController,
                    nicAodIconViewStore,
                )
        } else {
            notificationIconAreaController.setupAodIcons(nic)
        }
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        if (!featureFlags.isEnabled(Flags.MIGRATE_KEYGUARD_STATUS_VIEW)) {
            return
        }
        val bottomMargin =
            context.resources.getDimensionPixelSize(R.dimen.keyguard_status_view_bottom_margin)

        val useSplitShade = context.resources.getBoolean(R.bool.config_use_split_notification_shade)

        val topAlignment =
            if (useSplitShade) {
                TOP
            } else {
                BOTTOM
            }

        constraintSet.apply {
            connect(nicId, TOP, R.id.keyguard_status_view, topAlignment, bottomMargin)
            connect(nicId, START, PARENT_ID, START)
            connect(nicId, END, PARENT_ID, END)
            constrainHeight(
                nicId,
                context.resources.getDimensionPixelSize(R.dimen.notification_shelf_height)
            )
        }
    }

    override fun removeViews(constraintLayout: ConstraintLayout) {
        constraintLayout.removeView(nicId)
        nicBindingDisposable?.dispose()
    }
}
