package com.example.douyintv.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkClient {

    private const val BASE_URL = "https://www.douyin.com/"

    private val headerInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("accept", "*/*")
            .addHeader("accept-language", "zh-CN,zh;q=0.9")
            .addHeader("priority", "u=1, i")
            .addHeader("sec-ch-ua", "\"Chromium\";v=\"142\", \"Google Chrome\";v=\"142\", \"Not_A Brand\";v=\"99\"")
            .addHeader("sec-ch-ua-mobile", "?0")
            .addHeader("sec-ch-ua-platform", "\"macOS\"")
            .addHeader("sec-fetch-dest", "empty")
            .addHeader("sec-fetch-mode", "cors")
            .addHeader("sec-fetch-site", "same-origin")
            .addHeader("cookie", "hevc_supported=true; store-region=cn-zj; store-region-src=uid; xgplayer_device_id=15065695860; xgplayer_user_id=800004875816; __live_version__=%221.1.2.7847%22; live_use_vvc=%22false%22; SelfTabRedDotControl=%5B%7B%22id%22%3A%227478997008868214794%22%2C%22u%22%3A13%2C%22c%22%3A0%7D%5D; enter_pc_once=1; login_time=1750835089379; volume_info=%7B%22isUserMute%22%3Afalse%2C%22isMute%22%3Afalse%2C%22volume%22%3A0.6%7D; UIFID_TEMP=b7ffea4ffb7fd578f49f51586a953ac3b119cdf53ef4e59d8d754c665e3673926caae2cb0c77fceebd8efffec6a757b1622d5f72559b91c5576edd6c412690937055c9979586fb2ba8fa5d8e51720839; s_v_web_id=verify_mhsj1lef_EVclY5Gr_ghKL_4m15_8ADi_cb761WQsfD1z; __ac_nonce=0692be77e00ac019d8e56; __ac_signature=_02B4Z6wo00f01tlIAagAAIDA6t3368xZwXrZaAUAAN-H2f; douyin.com; device_web_cpu_core=12; device_web_memory_size=8; dy_swidth=1728; dy_sheight=1117; stream_recommend_feed_params=%22%7B%5C%22cookie_enabled%5C%22%3Atrue%2C%5C%22screen_width%5C%22%3A1728%2C%5C%22screen_height%5C%22%3A1117%2C%5C%22browser_online%5C%22%3Atrue%2C%5C%22cpu_core_num%5C%22%3A12%2C%5C%22device_memory%5C%22%3A8%2C%5C%22downlink%5C%22%3A10%2C%5C%22effective_type%5C%22%3A%5C%224g%5C%22%2C%5C%22round_trip_time%5C%22%3A100%7D%22; passport_csrf_token=956421d05185522bce8505b60d3d4368; passport_csrf_token_default=956421d05185522bce8505b60d3d4368; sid_guard=9aa7fc540532edb93929cc57834b73a3%7C1764484993%7C21600%7CSun%2C+30-Nov-2025+12%3A43%3A13+GMT; uid_tt=e488b55bf77d8d93a2237255e95c5374; uid_tt_ss=e488b55bf77d8d93a2237255e95c5374; sid_tt=9aa7fc540532edb93929cc57834b73a3; sessionid=9aa7fc540532edb93929cc57834b73a3; sessionid_ss=9aa7fc540532edb93929cc57834b73a3; session_tlb_tag=sttt%7C18%7Cmqf8VAUy7bk5KcxXg0tzo__________d6od9FrhHP_KRexsXzIxS7jfULyfBDmmTtV4hqaVheJY%3D; session_tlb_tag_bk=sttt%7C18%7Cmqf8VAUy7bk5KcxXg0tzo__________d6od9FrhHP_KRexsXzIxS7jfULyfBDmmTtV4hqaVheJY%3D; is_staff_user=false; sid_ucp_v1=1.0.0-KDkzYTI1Y2Q0NGU0NTU0ZDY3MmE1NzUzZTg3ZjMxYTlhOGNlYjMxMGMKCRCBz6_JBhjvMRoCaGwiIDlhYTdmYzU0MDUzMmVkYjkzOTI5Y2M1NzgzNGI3M2Ez; ssid_ucp_v1=1.0.0-KDkzYTI1Y2Q0NGU0NTU0ZDY3MmE1NzUzZTg3ZjMxYTlhOGNlYjMxMGMKCRCBz6_JBhjvMRoCaGwiIDlhYTdmYzU0MDUzMmVkYjkzOTI5Y2M1NzgzNGI3M2Ez; strategyABtestKey=%221764484993.48%22; fpk1=U2FsdGVkX1+/x2H30wFbVi5bL/rwV+J2Sm8jJ56MFkft1P9tihZg5yut/FI76Fcfm2058i4iYovPjobX+4zB0Q==; fpk2=f18b5213b6de2490ec9be218b0f025b0; __security_mc_1_s_sdk_crypt_sdk=f587ebfa-4d91-abbd; __security_mc_1_s_sdk_cert_key=5c2739ed-48ac-97bd; __security_mc_1_s_sdk_sign_data_key_web_protect=c3182ebe-476b-a975; bd_ticket_guard_client_web_domain=2; UIFID=b7ffea4ffb7fd578f49f51586a953ac3b119cdf53ef4e59d8d754c665e367392c6fa7212377a2f91aa608515849d5e32ae00510f967dbfa3033f93de0f22b5198307a376dfc9508a5a17f47234068ba6c5fea8102c1efa91aa5a4ec02dd060589ef5627d2c7a092f35f7ca0f6449f35932e3e4bf6b2678abecebb270beab0a956d4d7532a08a523416e12834dc51c78d1dbeccc760698f265083dd73d3da252c98ea5d0ad72039a2e8ed8df7419129e7e186aa565cd71fd506191efaa79831d0; is_dash_user=1; home_can_add_dy_2_desktop=%221%22; bd_ticket_guard_client_data=eyJiZC10aWNrZXQtZ3VhcmQtdmVyc2lvbiI6MiwiYmQtdGlja2V0LWd1YXJkLWl0ZXJhdGlvbi12ZXJzaW9uIjoxLCJiZC10aWNrZXQtZ3VhcmQtcmVlLXB1YmxpYy1rZXkiOiJCSXFObUdTMVNuZURSVWhzQVplbjMySnV1T1NDYkZsWFRyWXFsRDBXMXEvQ3JOZU1sNmhWSW50Ui9hZVNwdFJIQjNwVkRNbkg5Uy9VMXdZd3daQ0doc3c9IiwiYmQtdGlja2V0LWd1YXJkLXdlYi12ZXJzaW9uIjoyfQ%3D%3D; bd_ticket_guard_client_data_v2=eyJyZWVfcHVibGljX2tleSI6IkJJcU5tR1MxU25lRFJVaHNBWmVuMzJKdXVPU0NiRmxYVHJZcWxEMFcxcS9Dck5lTWw2aFZJbnRSL2FlU3B0UkhCM3BWRE1uSDlTL1Uxd1l3d1pDR2hzdz0iLCJ0c19zaWduIjoidHMuMS5lMWUyNmI3ZTZiYmM4Y2JiYTI0MzM2Y2EyNGJjYTk4NjU3ZTIzZWFkNzBkYTU0M2ViZGU3OTQzNjAwMWRlM2IyYzRmYmU4N2QyMzE5Y2YwNTMxODYyNGNlZGExNDkxMWNhNDA2ZGVkYmViZWRkYjJlMzBmY2U4ZDRmYTAyNTc1ZCIsInJlcV9jb250ZW50Ijoic2VjX3RzIiwicmVxX3NpZ24iOiIvcXBUTU1RWCtxVm9LUWdEeXhYaFFSanVzWXRzK3paSno3aWhBbVJCeEtrPSIsInNlY190cyI6IiNHZWs1ZkZMY1MyZlVEVTNhcUhmV1pRQUhyUy92ZlFEb1YwVkFBYUZvUlg0R1RJRlBJdGNSM0o5Ymp5TEEifQ%3D%3D; odin_tt=f41c68b1fd53543d714709b6526c52d4cc3857fe3dff5bdfea9d4ba3e647db127b190d8961b1651a983225f3fd877accfa1a229e9dc0806601daa4798101b60d6269835b994df5d889d1deb1c2f7728b; biz_trace_id=53e3fa3c; gulu_source_res=eyJwX2luIjoiYjYwM2IxOTA2OTIxYzA2MTQyN2RmODI4Y2MzNWMxYTIyNTE4ZTZiNmQxMGQyYmRiY2Q0Y2MzOTQ0ODNlM2E4YiJ9; feed_cache_data=%7B%22scmVersion%22%3A%221.0.8.399%22%2C%22date%22%3A1764485001460%2C%22dyQ%22%3A%221764485000%22%2C%22awemeId%22%3A%227570923101731540224%22%2C%22awemeInfoVersion%22%3A%223.0%22%7D; download_guide=%221%2F20251130%2F0%22; xg_device_score=7.933119974046879; ttwid=1%7CrrSlrzv6bli09OVT9-hsLRkGrXMG06ti76uSQSRCiSk%7C1764485299%7C1720b1c229a2230763a006b7b3c698e4e263dbb4ac9551e1c3d7c92289239438; sdk_source_info=7e276470716a68645a606960273f276364697660272927676c715a6d6069756077273f276364697660272927666d776a68605a607d71606b766c6a6b5a7666776c7571273f275e58272927666a6b766a69605a696c6061273f27636469766027292762696a6764695a7364776c6467696076273f275e582729277672715a646971273f2763646976602729277f6b5a666475273f2763646976602729276d6a6e5a6b6a716c273f2763646976602729276c6b6f5a7f6367273f27636469766027292771273f27303434373d31303d3131333234272927676c715a75776a716a666a69273f2763646976602778; bit_env=2CajdonnjCYOzw7zncjCsjTW21h1E1FebNy1gJCwcUp5fNzixJJGMQcOuWpzmEf44ckJWu5x6vdJWt6I4DXVaMeNQBxoNz0yh8v_QpP9U_Grs12isDxnvBtnpuUMqML8mr8IGknBj_ATr_AT7GgOc23b2kFypzPxU_gQpoZ0gJ1EvBf67LVjyD37RTKGDMSdV1eHGjBLtdyag5txRROLSb_AywIywKw-JZ4ilPDqbpIGJyQf-OrQAyAT51eQEvHfoiTQNnyCFWvwkLmph07cbKRK0BRADKZXyXTJrHE4d7VrAWbQrBsLrHzwQD0LZxxgMijKWh-JBsePCjb5h82WmlmJuB8dacWu4qKiIYkC3d1ZNCDuHBmrZZOH0gMA5XQUdKR_1j_b5Ps7sGEy3_O2njHgmT65invvlormglj-dqasSp-JiuokZYCEJ9v7nNFgEsMWb5ZBx6wP5_zCOCVKyxKI7NFa3MSXVKPIMPbuqE-lEGNTw-a3z4DWrmR1TrkpvT9kE78hpfmVQCmi1dckxnxIfZKQAbchuNZlxhyqwgo%3D; stream_player_status_params=%22%7B%5C%22is_auto_play%5C%22%3A0%2C%5C%22is_full_screen%5C%22%3A0%2C%5C%22is_full_webscreen%5C%22%3A0%2C%5C%22is_mute%5C%22%3A0%2C%5C%22is_speed%5C%22%3A1%2C%5C%22is_visible%5C%22%3A1%7D%22; IsDouyinActive=true")
            .addHeader("Referer", "https://www.douyin.com/?recommend=1")
            .build()
        chain.proceed(request)
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(headerInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .build()

    val api: DouyinApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DouyinApi::class.java)
    }
}
