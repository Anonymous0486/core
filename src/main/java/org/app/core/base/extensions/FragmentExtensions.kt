package org.app.core.base.extensions

import android.app.Activity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import org.app.core.base.utils.hideSoftInput
import org.app.core.base.utils.showMessage
import org.app.core.base.utils.showNoInternetAlert
import org.app.core.R

fun FragmentManager.findFragment(fragment: Fragment) =
    findFragmentByTag(fragment.javaClass.simpleName)

fun Fragment.hideKeyboard() = hideSoftInput(requireActivity())

fun Fragment.showNoInternetAlert() = showNoInternetAlert(requireActivity())

fun Fragment.showMessage(message: String?) = showMessage(requireContext(), message)

fun Fragment.showError(message: String, retryActionName: String? = null, action: (() -> Unit)? = null) =
    requireView().showSnackBar(message, retryActionName, action)

fun Fragment.getMyColor(@ColorRes id: Int) = ContextCompat.getColor(requireContext(), id)

fun Fragment.getMyDrawable(@DrawableRes id: Int) = ContextCompat.getDrawable(requireContext(), id)!!

fun Fragment.getMyString(id: Int) = resources.getString(id)

fun <A : Activity> Fragment.openActivityAndClearStack(activity: Class<A>) {
    requireActivity().openActivityAndClearStack(activity)
}

fun <A : Activity> Fragment.openActivity(activity: Class<A>) {
    requireActivity().openActivity(activity)
}

fun <T> Fragment.getNavigationResultLiveData(key: String = "result") =
    findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<T>(key)

fun <T> Fragment.removeNavigationResultObserver(key: String = "result") =
    findNavController().currentBackStackEntry?.savedStateHandle?.remove<T>(key)

fun <T> Fragment.setNavigationResult(result: T, key: String = "result") {
    findNavController().previousBackStackEntry?.savedStateHandle?.set(key, result)
}

fun Fragment.onBackPressedCustomAction(action: () -> Unit) {
    requireActivity().onBackPressedDispatcher.addCallback(
        viewLifecycleOwner,
        object : OnBackPressedCallback(true) {
            override
            fun handleOnBackPressed() {
                action()
            }
        }
    )
}

fun Fragment.navigateSafe(directions: NavDirections, navOptions: NavOptions? = null) {
    try {
        val options = navOptions ?: NavOptions.Builder()
            .setEnterAnim(R.anim.anim_slide_in_right)
            .setExitAnim(R.anim.anim_slide_out_left)
            .setPopEnterAnim(R.anim.anim_slide_in_left)
            .setPopExitAnim(R.anim.anim_slide_out_right)
            .build()
        findNavController().navigate(directions, options)
    } catch (ex: Exception) {
        ex.printStackTrace()
    }
}

fun Fragment.backToPreviousScreen() {
    try {
        findNavController().navigateUp()
    } catch (_ : Exception) {}
}