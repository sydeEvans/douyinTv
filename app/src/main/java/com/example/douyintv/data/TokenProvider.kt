package com.example.douyintv.data

interface TokenProvider {
    fun msToken(): String
    fun aBogus(): String
    fun xSecsdkWebExpire(): Long
    fun xSecsdkWebSignature(): String
    fun uifid(): String
    fun cookies(): String
}

object DefaultTokenProvider : TokenProvider {
    private const val MS_TOKEN = "GNB_ub09vbwao1TBsqofJFW44DYgs9OnCwBYWXzMQCMFEFISKud5Q64TnGpnFG0FGL002BtajSIRKnMnsluW9-c_3H_9x0TxuvEueApzRhwOGOfPTOXr5VshdqJf_rM1Sb_TaQEj8vimBgVE4viMgGWVVMX8pV-kDDz38e9ay8u6altNn91s87Ws"
    private const val A_BOGUS = "EJUVDtU7mZRfFV%2FbuOk6C3lUnKyMrTSyjMioRPpPexOyOwzcESPpBxaPboK8uctqK8BhkK57%2FxeAYEdbsTXsZCekLmZkSZsjm05AnSfL0Z71Y4JgvqSsCYbEFk-TlS4YuQIXi%2F65UssJ2D56IqCzAQ-yw%2FUrBbfD0N-tV2YaP2csBSWc2iFQYoEXtkvKUVdR"
    private const val X_SECSDK_WEB_SIGNATURE = "ee08cad39a1beaf818a2eaa6fd819bd2"
    private const val UIFID = "b7ffea4ffb7fd578f49f51586a953ac3b119cdf53ef4e59d8d754c665e367392c6fa7212377a2f91aa608515849d5e32ae00510f967dbfa3033f93de0f22b5198307a376dfc9508a5a17f47234068ba6c5fea8102c1efa91aa5a4ec02dd060589ef5627d2c7a092f35f7ca0f6449f35932e3e4bf6b2678abecebb270beab0a956d4d7532a08a523416e12834dc51c78d1dbeccc760698f265083dd73d3da252c98ea5d0ad72039a2e8ed8df7419129e7e186aa565cd71fd506191efaa79831d0"
    private const val COOKIES = "hevc_supported=true; store-region=cn-zj; store-region-src=uid; xgplayer_device_id=15065695860; xgplayer_user_id=800004875816; __live_version__=%221.1.2.7847%22; live_use_vvc=%22false%22; SelfTabRedDotControl=%5B%7B%22id%22%3A%227478997008868214794%22%2C%22u%22%3A13%2C%22c%22%3A0%7D%5D; enter_pc_once=1; login_time=1750835089379; volume_info=%7B%22isUserMute%22%3Afalse%2C%22isMute%22%3Afalse%2C%22volume%22%3A0.6%7D; UIFID_TEMP=b7ffea4ffb7fd578f49f51586a953ac3b119cdf53ef4e59d8d754c665e3673926caae2cb0c77fceebd8efffec6a757b1622d5f72559b91c5576edd6c412690937055c9979586fb2ba8fa5d8e51720839; s_v_web_id=verify_mhsj1lef_EVclY5Gr_ghKL_4m15_8ADi_cb761WQsfD1z; __ac_nonce=0692be77e00ac019d8e56; __ac_signature=_02B4Z6wo00f01tlIAagAAIDA6t3368xZwXrZaAUAAN-H2f; douyin.com; device_web_cpu_core=12; device_web_memory_size=8; dy_swidth=1728; dy_sheight=1117; stream_recommend_feed_params=%22%7B%5C%22cookie_enabled%5C%22%3Atrue%2C%5C%22screen_width%5C%22%3A1728%2C%5C%22screen_height%5C%22%3A1117%2C%5C%22browser_online%5C%22%3Atrue%2C%5C%22cpu_core_num%5C%22%3A12%2C%5C%22device_memory%5C%22%3A8%2C%5C%22downlink%5C%22%3A10%2C%5C%22effective_type%5C%22%3A%5C%224g%5C%22%2C%5C%22round_trip_time%5C%22%3A100%7D%22; passport_csrf_token=956421d05185522bce8505b60d3d4368; passport_csrf_token_default=956421d05185522bce8505b60d3d4368; sid_guard=9aa7fc540532edb93929cc57834b73a3%7C1764484993%7C21600%7CSun%2C+30-Nov-2025+12%3A43%3A13+GMT; uid_tt=e488b55bf77d8d93a2237255e95c5374; uid_tt_ss=e488b55bf77d8d93a2237255e95c5374; sid_tt=9aa7fc540532edb93929cc57834b73a3; sessionid=9aa7fc540532edb93929cc57834b73a3; sessionid_ss=9aa7fc540532edb93929cc57834b73a3; session_tlb_tag=sttt%7C18%7Cmqf8VAUy7bk5KcxXg0tzo__________d6od9FrhHP_KRexsXzIxS7jfULyfBDmmTtV4hqaVheJY%3D; session_tlb_tag_bk=sttt%7C18%7Cmqf8VAUy7bk5KcxXg0tzo__________d6od9FrhHP_KRexsXzIxS7jfULyfBDmmTtV4hqaVheJY%3D; is_staff_user=false; sid_ucp_v1=1.0.0-KDkzYTI1Y2Q0NGU0NTU0ZDY3MmE1NzUzZTg3ZjMxYTlhOGNlYjMxMGMKCRCBz6_JBhjvMRoCaGwiIDlhYTdmYzU0"
    override fun msToken() = MS_TOKEN
    override fun aBogus() = A_BOGUS
    override fun xSecsdkWebExpire() = 1764489350548L
    override fun xSecsdkWebSignature() = X_SECSDK_WEB_SIGNATURE
    override fun uifid() = UIFID
    override fun cookies() = COOKIES
}
