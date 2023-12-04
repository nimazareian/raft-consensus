package cs416.lambda.capstone

import yahoofinance.YahooFinance

class TradeServiceImpl {
    fun buyStock(request: BuyRequest): BuyResponse {
        println("Buy request received: $request")
        // https://query2.finance.yahoo.com/v8/finance/chart/GME
        // this built in api doesnt work, need to use normal requests library
        val test = YahooFinance.get(request.stock)

        val stockPrice = test.quote.price.toInt()
        // Response to client
        //TODO: PROPAGATE TO BUY REQUEST LOG ENTRY TO OTHER RAFT NODES
        if (stockPrice != null && (stockPrice * request.amount <= request.actBalance)) {
            return BuyResponse.newBuilder().setPurchased(true).build()
        }
        return BuyResponse.newBuilder().setPurchased(false).build()
    }

    fun sellStock(request: SellRequest): SellResponse {
        println("Sell request received: $request")
        val stockPrice = YahooFinance.get(request.stock)?.quote?.price?.toInt()

        if (stockPrice != null) {
            val sellAmount = stockPrice * request.amount
            //on successfull sell, reply with how much money user is getting
            return SellResponse.newBuilder().setSellAmount(sellAmount).build()
        } else {
            //on sell fail, reply with -1 idk
            return SellResponse.newBuilder().setSellAmount(-1).build()
        }
    }
//    fun getStocks(request: GetStocksRequest): GetStocksReply {
//        //TODO: DO SOMETHING
//
//        return GetStocksReply.newBuilder().build()
//    }


}
