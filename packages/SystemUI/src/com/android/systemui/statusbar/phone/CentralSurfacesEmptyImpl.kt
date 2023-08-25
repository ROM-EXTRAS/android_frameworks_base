/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.statusbar.phone

import android.content.Intent
import androidx.lifecycle.LifecycleRegistry
import com.android.keyguard.AuthKeyguardMessageArea
import com.android.systemui.animation.ActivityLaunchAnimator
import com.android.systemui.navigationbar.NavigationBarView
import com.android.systemui.plugins.ActivityStarter.OnDismissAction
import com.android.systemui.qs.QSPanelController
import com.android.systemui.statusbar.NotificationPresenter
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import java.io.PrintWriter

/**
 * Empty implementation of [CentralSurfaces] for variants only need to override portions of the
 * interface.
 *
 * **Important**: Prefer binding an Optional<CentralSurfaces> to an empty optional instead of
 * including this class.
 */
abstract class CentralSurfacesEmptyImpl : CentralSurfaces {
    override val lifecycle = LifecycleRegistry(this)
    override fun updateIsKeyguard() = false
    override fun updateIsKeyguard(forceStateChange: Boolean) = false
    override fun getKeyguardMessageArea(): AuthKeyguardMessageArea? = null
    override fun isLaunchingActivityOverLockscreen() = false
    override fun onKeyguardViewManagerStatesUpdated() {}
    override fun isDeviceInVrMode() = false
    override fun getPresenter(): NotificationPresenter? = null
    override fun onInputFocusTransfer(start: Boolean, cancel: Boolean, velocity: Float) {}
    override fun getCommandQueuePanelsEnabled() = false
    override fun getBiometricUnlockController(): BiometricUnlockController? = null
    override fun showWirelessChargingAnimation(batteryLevel: Int) {}
    override fun checkBarModes() {}
    override fun updateBubblesVisibility() {}
    override fun setInteracting(barWindow: Int, interacting: Boolean) {}
    override fun dump(pwOriginal: PrintWriter, args: Array<String>) {}
    override fun getDisplayWidth() = 0f
    override fun getDisplayHeight() = 0f
    override fun showKeyguard() {}
    override fun hideKeyguard() = false
    override fun showKeyguardImpl() {}
    override fun fadeKeyguardAfterLaunchTransition(
        beforeFading: Runnable?,
        endRunnable: Runnable?,
        cancelRunnable: Runnable?,
    ) {}
    override fun startLaunchTransitionTimeout() {}
    override fun hideKeyguardImpl(forceStateChange: Boolean) = false
    override fun keyguardGoingAway() {}
    override fun setKeyguardFadingAway(startTime: Long, delay: Long, fadeoutDuration: Long) {}
    override fun finishKeyguardFadingAway() {}
    override fun userActivity() {}
    override fun endAffordanceLaunch() {}
    override fun shouldKeyguardHideImmediately() = false
    override fun showBouncerWithDimissAndCancelIfKeyguard(
        performAction: OnDismissAction?,
        cancelAction: Runnable?,
    ) {}
    override fun getNavigationBarView(): NavigationBarView? = null
    override fun isOverviewEnabled() = false
    override fun showPinningEnterExitToast(entering: Boolean) {}
    override fun showPinningEscapeToast() {}
    override fun setBouncerShowing(bouncerShowing: Boolean) {}
    override fun getWakefulnessState() = 0
    override fun isScreenFullyOff() = false
    override fun showScreenPinningRequest(taskId: Int, allowCancel: Boolean) {}
    override fun getEmergencyActionIntent(): Intent? = null
    override fun isCameraAllowedByAdmin() = false
    override fun isGoingToSleep() = false
    override fun notifyBiometricAuthModeChanged() {}
    override fun setTransitionToFullShadeProgress(transitionToFullShadeProgress: Float) {}
    override fun setPrimaryBouncerHiddenFraction(expansion: Float) {}
    override fun updateScrimController() {}
    override fun isKeyguardShowing() = false
    override fun shouldIgnoreTouch() = false
    override fun isDeviceInteractive() = false
    override fun awakenDreams() {}
    override fun clearNotificationEffects() {}
    override fun isBouncerShowing() = false
    override fun isBouncerShowingScrimmed() = false
    override fun isBouncerShowingOverDream() = false
    override fun isKeyguardSecure() = false
    override fun updateNotificationPanelTouchState() {}
    override fun getRotation() = 0
    override fun setBarStateForTest(state: Int) {}
    override fun showTransientUnchecked() {}
    override fun clearTransient() {}
    override fun acquireGestureWakeLock(time: Long) {}
    override fun setAppearance(appearance: Int) = false
    override fun getBarMode() = 0
    override fun resendMessage(msg: Int) {}
    override fun resendMessage(msg: Any?) {}
    override fun setLastCameraLaunchSource(source: Int) {}
    override fun setLaunchCameraOnFinishedGoingToSleep(launch: Boolean) {}
    override fun setLaunchCameraOnFinishedWaking(launch: Boolean) {}
    override fun setLaunchEmergencyActionOnFinishedGoingToSleep(launch: Boolean) {}
    override fun setLaunchEmergencyActionOnFinishedWaking(launch: Boolean) {}
    override fun getQSPanelController(): QSPanelController? = null
    override fun getDisplayDensity() = 0f
    override fun extendDozePulse() {}
    override fun setIsLaunchingActivityOverLockscreen(isLaunchingActivityOverLockscreen: Boolean) {}
    override fun getAnimatorControllerFromNotification(
        associatedView: ExpandableNotificationRow?,
    ): ActivityLaunchAnimator.Controller? = null
}
