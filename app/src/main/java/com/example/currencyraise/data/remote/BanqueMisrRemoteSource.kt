package com.example.currencyraise.data.remote

import com.example.currencyraise.data.mapper.BANQUE_MISR_URL
import com.example.currencyraise.data.mapper.toExchangeRate
import java.time.Clock
import javax.inject.Inject
import okhttp3.Call

internal class BanqueMisrRemoteSource @Inject constructor(
    calls: Call.Factory,
    parser: BanqueMisrParser,
    clock: Clock,
) : RateRemoteSource by HtmlRateRemoteSource(calls, BANQUE_MISR_URL, { html ->
    parser.parse(html).toExchangeRate(clock.instant())
})
