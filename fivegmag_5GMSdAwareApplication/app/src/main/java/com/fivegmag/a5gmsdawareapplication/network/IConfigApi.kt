package com.fivegmag.a5gmsdawareapplication.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.Call
import retrofit2.http.Url

interface IConfigApi {
    @GET
    fun fetchConfiguration(@Url url: String): Call<ResponseBody>?
}