SELECT CINACTIVEBUY, ctodaybuy, CTODAYCONFIRMBUY, ctodaysell,	CTODAYCONFIRMSELL , * FROM VCBACCOUNT


SELECT  trim( CLIENTID) as CLIENTID , ttl.tradingaccseq , ttl.marketid , ttl.instrumentid,   TINACTIVEBUY , TINACTIVESELL ,
        TTODAYBUY , TTODAYSELL , TTODAYCONFIRMBUY , TTODAYCONFIRMSELL , * FROM VCBTRADINGACC ttl

---Stock---

select SUBSTRING( SUBACCOUNTID , 0 ,7), InstrumentID , BS, SUM(convert(DECIMAL(16,8), InstLedgerDelta ))
from TTLMQOrders where SubAccountID  like '%068927%' and MQdatetime >= '2024-12-21 15:00:00.000' and convert(DECIMAL(16,8), InstLedgerDelta) <> 0 and MQstatus <> 'Fail'
                   and Instrumentid = trim('00939') group by BS, subaccountid, instrumentid

----- Cash ----

select SubAccountID , SUM(convert(DECIMAL(16,8), CashLedgerDelta )) from   TTLMQOrders where SubAccountID like '%212291%'
                                                                                         and MQdatetime >= '2024-07-31 19:00:00.000' and MQstatus <> 'Fail' and convert(DECIMAL(16,8), CashLedgerDelta) <> 0 group by SubAccountID



