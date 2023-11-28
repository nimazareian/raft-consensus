package cs416.lambda.capstone

import yahoofinance.YahooFinance

class TradeServiceImpl {
    fun buyStock(request: BuyRequest): BuyReply {
        println("Buy request received: $request")
        val stockPrice = YahooFinance.get(request.stock)?.quote?.price?.toInt()
        // Response to client
        //TODO: PROPAGATE TO BUY REQUEST LOG ENTRY TO OTHER RAFT NODES
        if(stockPrice != null && (stockPrice * request.amount <= request.actBalance)) {
            return BuyReply.newBuilder().setPurchased(true).build()
        }
        return BuyReply.newBuilder().setPurchased(false).build()
    }

    fun sellStock(request: SellRequest): SellReply {
        println("Sell request received: $request")
        val stockPrice = YahooFinance.get(request.stock)?.quote?.price?.toInt()

        if(stockPrice != null) {
            val sellAmount = stockPrice * request.amount
            //on successfull sell, reply with how much money user is getting
            return SellReply.newBuilder().setSellAmount(sellAmount).build()
        } else {
            //on sell fail, reply with -1 idk
            return SellReply.newBuilder().setSellAmount(-1).build()
        }
    }
    fun getStocks(request: GetStocksRequest): GetStocksReply {
        //TODO: DO SOMETHING
        return GetStocksReply.newBuilder().build()
    }



}
