package com.meteocool.network

/**
 * JSONPost to clear notifications from clients phone.
 */
 data class JSONClearPost(val token : String,
                         val from : String) : JSON()
