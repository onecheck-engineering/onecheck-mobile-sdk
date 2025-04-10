package com.onecheck.oc_vcgps_sdk.retrofit

import android.util.Log
import com.google.gson.GsonBuilder
import com.onecheck.oc_vcgps_sdk.Log.LogSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitConnection {
    // Singleton Pattern
    // 소프트웨어 디자인 패턴 중 하나로,
    // 프로그램 내에서 객체 하나를
    // 공유하는 패턴을 말한다.

    // Companion Object
    // 어떤 클래스의 모든 인스턴스의 공유하는 객체를
    // 만들고 싶을 때 사용하며,
    // 클래스당 한 개만 가질 수 있다.

    companion object{
        //BASE_URL은 도메인 설정시 수정 예정
        private const val BASE_URL = "https://onecheck-retail.kro.kr/"
        //private const val BASE_URL = "http://192.168.0.4:3000/"
        private var INSTANCE: Retrofit? = null

        fun getInstance(): Retrofit {
            if(INSTANCE == null) {
                // serializeNulls() 설정하지 않음, 기본적으로 null 필드 제외됨
                val gson = GsonBuilder().create()

                INSTANCE = Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build()
            }
            return INSTANCE!!
        }

        fun <T> makeApiCall(
            call: suspend() -> retrofit2.Response<T>,
            onSuccess: (T?) -> Unit,
            onFailure: (Exception) -> Unit
        ){
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = call() // Retrofit API 호출
                    //Log.d("response.isSuccessful", "${response.isSuccessful}")
                    if(response.isSuccessful){
                        withContext(Dispatchers.Main){
                            onSuccess(response.body()) // 성공 처리
                        }
                    } else if (response.code() == 500) {
                        // TODO : 오류 응답처리 추후 구현..
                        withContext(Dispatchers.Main){
                            onFailure(Exception("Response unsuccessful"))
                        }
                    }
                } catch(e: Exception){
                    withContext(Dispatchers.Main){
                        LogSdk.e("makeApiCall", "${e.printStackTrace()}")
                        onFailure(Exception("Response unsuccessful"))
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}