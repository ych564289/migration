SELECT CINACTIVEBUY, ctodaybuy, CTODAYCONFIRMBUY, ctodaysell,	CTODAYCONFIRMSELL , * FROM VCBACCOUNT


SELECT  trim( CLIENTID) as CLIENTID , ttl.tradingaccseq , ttl.marketid , ttl.instrumentid,   TINACTIVEBUY , TINACTIVESELL ,
        TTODAYBUY , TTODAYSELL , TTODAYCONFIRMBUY , TTODAYCONFIRMSELL , * FROM VCBTRADINGACC ttl

---Stock---

select SUBSTRING( SUBACCOUNTID , 0 ,7), InstrumentID , BS, SUM(convert(DECIMAL(16,8), InstLedgerDelta ))
from TTLMQOrders where SubAccountID  like '%068927%' and MQdatetime >= '2024-12-21 15:00:00.000' and convert(DECIMAL(16,8), InstLedgerDelta) <> 0 and MQstatus <> 'Fail'
                   and Instrumentid = trim('00939') group by BS, subaccountid, instrumentid

----- Cash ----

select SubAccountID , SUM(convert(DECIMAL(16,8), CashLedgerDelta )) from   TTLMQOrders
where SubAccountID like '%212291%'
 and MQdatetime >= '2024-07-31 19:00:00.000' and MQstatus <> 'Fail' and convert(DECIMAL(16,8), CashLedgerDelta) <> 0
 group by SubAccountID

-- 金额
CINACTIVEBUY,ctodaybuy,CTODAYCONFIRMBUY,CINACTIVESELL,CTODAYSELL,CTODAYCONFIRMSELL
--股数
TINACTIVEBUY , TINACTIVESELL ,TTODAYBUY , TTODAYSELL , TTODAYCONFIRMBUY , TTODAYCONFIRMSELL

SELECT  '003901' AS clientid, 'HKEX' AS ttlmarketid,'01666'            AS instrument,  28000   AS issueamt , 'TTL BO issue - Missing MQ' AS rem FROM  SCDUMMY UNION ALL
SELECT  '912977' AS clientid, 'HKEX' AS ttlmarketid,'00001'            AS instrument,  52500   AS issueamt , 'TTL BO issue - Missing MQ' AS rem FROM  SCDUMMY UNION ALL
SELECT  '181281' AS clientid, 'SPMK' AS ttlmarketid,'NDF-230822-BAML1' AS instrument,  1       AS issueamt , 'TTL BO issue - Missing MQ' AS rem FROM  SCDUMMY  UNION ALL
SELECT  '201378' AS clientid, 'SPMK' AS ttlmarketid,'XS2957448553'     AS instrument,  -288000 AS issueamt , 'Octoback Issue: No cancel date on 90685703 result in no cancellation on octoback' AS rem FROM  SCDUMMY UNION ALL
SELECT  '208183' AS clientid, 'HKEX' AS ttlmarketid,'07226'            AS instrument,  350000  AS issueamt , 'TTL BO issue - Missing MQ' AS rem FROM  SCDUMMY