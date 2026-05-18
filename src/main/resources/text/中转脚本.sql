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

SELECT  '186602' AS clientid, 'HKD' AS ccy, -110.48 - 1470.64 AS issueamt , 'TTL BO issue - Missing MQ' AS rem FROM  SCDUMMY UNION ALL
SELECT  '091757' AS clientid, 'USD' AS ccy, 197675.18 - 197673.84 AS issueamt , 'TTL BO issue - Missing MQ' AS rem FROM  SCDUMMY UNION ALL
SELECT  '006451' AS clientid, 'HKD' AS ccy, 2341833.35 - 2338938.98 AS issueamt , 'TTL BO issue - Missing MQ' AS rem FROM  SCDUMMY  UNION ALL
SELECT  '083282' AS clientid, 'HKD' AS ccy, -53.0 AS issueamt , 'TTL BO issue - Missing MQ' AS rem FROM  SCDUMMY

SELECT  '186602' AS clientid, 'HKD' AS ccy, -110.48 - 1470.64 AS issueamt , 'TTL BO issue - Missing MQ' AS rem FROM  SCDUMMY UNION ALL SELECT  '091757' AS clientid, 'USD' AS ccy, 197675.18 - 197673.84 AS issueamt , 'TTL BO issue - Missing MQ' AS rem FROM  SCDUMMY UNION ALL SELECT  '006451' AS clientid, 'HKD' AS ccy, 2341833.35 - 2338938.98 AS issueamt , 'TTL BO issue - Missing MQ' AS rem FROM  SCDUMMY  UNION ALL SELECT  '083282' AS clientid, 'HKD' AS ccy, -53.0 AS issueamt , 'TTL BO issue - Missing MQ' AS rem FROM  SCDUMMY

select
    normal.ClntCode,
    case when dvp.ClntCode is null  then 3
         when normal.AcctType =  'CASH' then 1
         when normal.AcctType =  'CUST' then 1
         when normal.AcctType =  'MRGN' then 2
         when normal.Lender =  'Y' then 4
         when normal.borrower = 'Y' then 4
        end as defaultclientcode
from
    (select distinct clnt.ClntCode, typ.AcctType, typ.Lender , typ.Borrower  from clnt clnt , ClntAcctType typ
     where clnt.clntcode = typ.ClntCode and typ.active = 'Yes' ) normal left outer join
    ( Select Distinct ClntCode from ClntMarketAcctTypeSum where SettleMethod = 'CHEQUE' and AcctType = 'CASH') dvp
    on normal.clntcode = dvp.ClntCode

SELECT *
FROM employees
ORDER BY employee_id
OFFSET 10 ROWS        -- 跳过前10条记录
    FETCH FIRST 10 ROWS ONLY;  -- 获取接下来的10条记录


SELECT CLIENTID , TRADINGACCSUBSTATUSID   FROM SCTRADINGACCSTATUSDETAIL  WHERE TRADINGACCSEQ = 4 AND TRADINGACCSUBSTATUSID  = 12  AND CLIENTID NOT IN (

    SELECT DISTINCT CLIENTID    FROM SCTRADINGACCSTATUSDETAIL   WHERE  TRADINGACCSEQ = 4 AND TRADINGACCSUBSTATUSID  IN (16,19,21,22,23,24,7,8,9) -- AND CLIENTID = '000056'

)

SELECT active_client.*, tradable_HKEX.clientid AS have_HKEX_trading_market, tradable_MAMK.clientid  AS have_MAMK_trading_market, tradable_SZMK.clientid   AS have_SZMK_trading_market

     ,ac_bal.currencyid, ac_bal.csettled, HKBCAN.clientid AS have_HK_BCAN, SZBCAN.clientid AS have_sz_bcan, SHBCAN.clientid AS have_SJ_bcan

FROM (

         SELECT CLIENTID , TRADINGACCSUBSTATUSID   FROM SCTRADINGACCSTATUSDETAIL  WHERE TRADINGACCSEQ = 1 AND TRADINGACCSUBSTATUSID  = 12  AND CLIENTID NOT IN (

             SELECT DISTINCT CLIENTID    FROM SCTRADINGACCSTATUSDETAIL   WHERE  TRADINGACCSEQ = 1 AND TRADINGACCSUBSTATUSID  IN (16,19,21,22,23,24,7,8,9) -- AND CLIENTID = '000056'

         )) active_client
         LEFT OUTER JOIN (SELECT DISTINCT clientid FROM SCTRADINGACCMARKET WHERE TRADINGACCSEQ = 1 AND marketid = 'HKEX') tradable_HKEX ON  active_client.clientid = tradable_HKEX.clientid
         LEFT OUTER JOIN (SELECT DISTINCT clientid FROM SCTRADINGACCMARKET WHERE TRADINGACCSEQ = 1 AND marketid = 'MAMK') tradable_MAMK ON  active_client.clientid = tradable_MAMK.clientid
         LEFT OUTER JOIN (SELECT DISTINCT clientid FROM SCTRADINGACCMARKET WHERE TRADINGACCSEQ = 1 AND marketid = 'SZMK') tradable_SZMK ON  active_client.clientid = tradable_SZMK.clientid
         LEFT OUTER JOIN (SELECT * FROM VCBACCOUNT WHERE csettled > 0 AND accountseq = 1 ) ac_bal ON active_client.clientid = ac_bal.clientid

         LEFT OUTER JOIN (select DISTINCT clientid, marketid, INVESTORCODESTATUS from SCTradingAccMarketBrokerInvestorCode where marketid IN ('HKEX') AND TRADINGACCSEQ = 1 AND INVESTORCODESTATUS = 'A') HKBCAN ON  active_client.clientid = HKBCAN.clientid
         LEFT OUTER JOIN (select DISTINCT clientid, marketid, INVESTORCODESTATUS from SCTradingAccMarketBrokerInvestorCode where marketid IN ('SZMK') AND TRADINGACCSEQ = 1 AND INVESTORCODESTATUS = 'A') SZBCAN ON  active_client.clientid = SZBCAN.clientid
         LEFT OUTER JOIN (select DISTINCT clientid, marketid, INVESTORCODESTATUS from SCTradingAccMarketBrokerInvestorCode where marketid IN ('MAMK') AND TRADINGACCSEQ = 1 AND INVESTORCODESTATUS = 'A') SHBCAN ON  active_client.clientid = SHBCAN.clientid
ORDER BY 1
