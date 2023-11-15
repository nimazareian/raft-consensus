package cs416.lambda.capstone

import com.google.protobuf.MessageLite


class App {
    val greeting: String
        get() {
            return "Hello World!"
        }
}

fun main() {
    println(App().greeting);

    val list = ArrayList<MessageLite>()
    BuyOuterClass.Buy.AMOUNT_FIELD_NUMBER
//    list.add()
    
//    val cmd = Commands.MoreMsg {
//        bar = "test"
//    }
}
