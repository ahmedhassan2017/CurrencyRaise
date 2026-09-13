package com.example.currencyraise.data.remote

import com.example.currencyraise.domain.model.Bank
import java.time.Clock
import javax.inject.Inject
import okhttp3.Call

internal class CibTa3weemRemoteSource @Inject constructor(
    calls: Call.Factory,
    parser: CibTa3weemParser,
    clock: Clock,
) : RateRemoteSource by HtmlRateRemoteSource(calls, Bank.CIB.sourceUrl, { html ->
    parser.parse(html, clock.instant())
})
