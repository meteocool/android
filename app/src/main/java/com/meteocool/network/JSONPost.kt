package com.meteocool.network

/**
 * JSONPost to update client information for the server.
 */
data class JSONPost(val lat : Double,
                    val lon : Double,
                    val altitude : Double,
                    val accuracy : Float,
                    val verticalAccuracy : Float,
                    val pressure : Double,
                    val timestamp : Double,
                    val token : String,
                    val source : String,
                    val ahead : Int,
                    val intensity : Int,
                    val lang : String) : JSON()
