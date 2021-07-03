package com.meteocool.location

import android.app.Activity
import com.google.android.gms.common.api.Status

class ResolvableApiException: com.google.android.gms.common.api.ResolvableApiException {
	constructor(status: Status) : super(status)
}
