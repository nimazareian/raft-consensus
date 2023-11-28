package cs416.lambda.capstone
import io.grpc.ManagedChannelBuilder
import java.io.Closeable
import java.util.concurrent.TimeUnit


class StubNode(val address: String, val port: Int) : Closeable {
    private val channel = ManagedChannelBuilder.forAddress(address, port).usePlaintext().build()
    private val stub = RaftServiceGrpc.newBlockingStub(channel)
    init {
        println("StubNode $address:$port created")
    }

    fun requestVote(request: VoteRequest): VoteResponse {
        return stub.requestVote(request)
    }

    fun appendEntries(request: AppendEntriesRequest): AppendEntriesResponse {
        return stub.appendEntries(request);
    }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }

    override fun toString(): String {
        return "StubNode(host='$address', port=$port)"
    }
}