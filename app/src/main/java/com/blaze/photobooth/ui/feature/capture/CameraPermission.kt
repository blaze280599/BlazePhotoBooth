package com.blaze.photobooth.ui.feature.capture

/** Where the camera permission stands (README §12). */
enum class CameraPermission {
    Granted,

    /** Not asked yet in this visit: show the friendly pre-prompt card first. */
    NeedsRequest,

    /** Denied, but Android will still show the dialog again: the card offers another try. */
    Denied,

    /** Denied for good ("Don't allow" twice): only system Settings can turn it on. */
    Blocked,
    ;

    companion object {
        /**
         * [showRationale] is `shouldShowRequestPermissionRationale`, which is only meaningful after a
         * request: true after one denial, false once Android stops asking.
         */
        fun of(granted: Boolean, requested: Boolean, showRationale: Boolean): CameraPermission = when {
            granted -> Granted
            !requested -> NeedsRequest
            showRationale -> Denied
            else -> Blocked
        }
    }
}
