elect o.clntcode, case
	when cms.defaulttradingacc = 'CASH' then '1'
	when cms.defaulttradingacc = 'MRGN' then '2'
	when cms.defaulttradingacc = 'DVP' then '3'
	when cms.defaulttradingacc = 'SBL' then '4'
	when cms.defaulttradingacc = 'SBLLender' then '5'
END as accounts,
tb.TTLMarketID ,
o.instrument,
sum(o.asat) ledger  from "REVIEW801LEDGERSTKv5" o, CMS_VIEW cms , TTLMarketBoard tb
where
    o.market = tb.Market and
    o.ClntCode = cms.accountcode
group by
o.clntcode, case
	when cms.defaulttradingacc = 'CASH' then '1'
	when cms.defaulttradingacc = 'MRGN' then '2'
	when cms.defaulttradingacc = 'DVP' then '3'
	when cms.defaulttradingacc = 'SBL' then '4'
	when cms.defaulttradingacc = 'SBLLender' then '5'
END   ,
tb.TTLMarketID ,
o.instrument