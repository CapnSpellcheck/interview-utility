/*
 * Good day, welcome to the source code for the Twinkle™ Dating app, created by Julian Pellico!
 * Copyright © (c) 2017 Shimmering Ideas LLC. All rights reserved.
 */

package julianpellico.interviewutils

import android.util.Log
import com.android.volley.*
import com.android.volley.toolbox.HttpHeaderParser
import com.beust.klaxon.*

private const val tag_ = "KlaxonRequest"

interface ResponseHandler<t> : Response.Listener<t>, Response.ErrorListener

abstract class KlaxonBaseRequest<T> : Request<T> {
    protected val listener: ResponseHandler<T>
    var paramz: Map<String, String>? = null
    protected var responseFromCache = false
    var contentType: String? = null

    constructor(method: Int, url: String, listener: ResponseHandler<T>) : super(method, url, listener) {
        this.listener = listener
    }

    override fun getParams(): Map<String, String>? {
        return paramz
    }

    override fun addMarker(tag: String?) {
        super.addMarker(tag)
        if ("cache-hit" == tag) {
            responseFromCache = true
        }
    }

    override fun deliverResponse(response: T) {
        listener.onResponse(response)
    }

    override fun getBodyContentType(): String {
        contentType?.let {
            return it
        }
        return super.getBodyContentType()
    }
}

open class KlaxonRequest(method: Int, url: String, listener: ResponseHandler<JsonObject>) :
    KlaxonBaseRequest<JsonObject>(method, url, listener)
{
    init {
        if (method != Method.GET)
            setShouldCache(false)
    }

    override fun parseNetworkResponse(response: NetworkResponse): Response<JsonObject> {
        return try {
            val jsonstr = String(response.data)
            val cacheEntry = HttpHeaderParser.parseCacheHeaders(response)
            Log.d(tag_, "response [$url]: $jsonstr")
            Log.d(tag_, "Headers: " + response.headers)
            Response.success(Parser().parse(StringBuilder(jsonstr)) as JsonObject, cacheEntry)
        } catch (ex: Exception) {
            Log.e(tag_, "JSON parse error")
            Response.error(ParseError(ex))
        }
    }

    override fun deliverResponse(response: JsonObject) {
        if (responseFromCache)
            response["@#fromcache"] = true
        super.deliverResponse(response)
    }

    override fun getHeaders(): MutableMap<String, String> {
        return mutableMapOf("Accept" to "application/json")
    }

    override fun parseNetworkError(error: VolleyError): VolleyError {
        if (error is ServerError || error is AuthFailureError) {
            try {
                val str = String(error.networkResponse!!.data)
                Log.e(tag_, "network error: $str")
                val obj = Parser().parse(StringBuilder(str)) as? JsonObject
                if (error is ServerError)
                    return KlaxonServerError(error, obj)
                if (error is AuthFailureError)
                    return KlaxonServerError(error, obj)
            } catch(ex: Exception) {}
        }
        return super.parseNetworkError(error)
    }

    override fun getBodyContentType(): String {
        return super.getBodyContentType()
    }
}

class KlaxonServerError : ServerError
{
    val obj: JsonObject?

    constructor(rootError: ServerError, obj: JsonObject?) : super(rootError.networkResponse) {
        this.obj = obj
        this._realNetworkTimeMs = rootError.networkTimeMs
    }

    constructor(rootError: AuthFailureError, obj: JsonObject?) : super(rootError.networkResponse) {
        this.obj = obj
        this._realNetworkTimeMs = rootError.networkTimeMs
    }

    val exception: String?
        get() = obj?.string("exception")

    private var _realNetworkTimeMs: Long
    override fun getNetworkTimeMs(): Long = _realNetworkTimeMs
    override fun getLocalizedMessage(): String = "Error response: ${obj?.toJsonString(false)}"
}
