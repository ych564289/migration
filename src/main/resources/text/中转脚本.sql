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

di
035920_1000
042735_1000
061563_1000
069533_1000
079185_1000
081385_1000
097907_1000
100035_1000
100110_1000
100899_1000
107863_1000
113752_1000
115323_1000
161936_1000
175818_1000
178771_1000
187980_1000
189688_1000
189838_1000
192659_1000
199616_1000
200280_1000
200573_1000
201231_1000
202567_1000
203618_1000
208628_1000
209699_1000
210559_1000
210896_1000
210897_1000
211837_1000
212561_1000
213337_1000
213787_1000

050042_2000
050099_2000
050399_2000
050417_2000
050569_2000
050699_2000
050705_2000
050752_2000
096360_2000
097862_2000
118303_2000
170677_2000
198063_2000
201138_2000
201160_2000
201191_2000
205623_2000
208561_2000
209029_2000
212739_2000
212750_2000
213592_2000
215215_2000
621165_2000
900030_2000
900188_2000
912011_2000
912598_2000
912735_2000
915310_2000
915538_2000
916308_2000
916398_2000
916550_2000

137615_3000
138937_3000
162222_3000
178867_3000
192035_3000
196659_3000
197209_3000
197527_3000
197530_3000
197531_3000
200975_3000
202566_3000
203391_3000
207089_3000
207620_3000
209023_3000
209105_3000
209168_3000
209178_3000
209902_3000
210558_3000
210628_3000
210863_3000
211017_3000
211137_3000
215212_3000
215533_3000
912306_3000
913132_3000

102689_4000
103537_4000
121761_4000
131311_4000
166138_4000
168680_4000
189687_4000
192815_4000
192851_4000
195350_4000
197205_4000
197290_4000
197907_4000
198728_4000
198985_4000
201879_4000
202770_4000
203088_4000
203280_4000
209952_4000
209961_4000
913123_4000

GAO
093051_3000
096276_3000
211176_3000
211719_3000
211868_3000
211918_3000
211925_3000
212178_3000
212538_3000
212579_3000
212751_3000
212765_3000
212778_3000
212917_3000
212919_3000
213760_3000
213928_3000
213931_3000
213998_3000
215073_3000
215371_3000
912520_3000


