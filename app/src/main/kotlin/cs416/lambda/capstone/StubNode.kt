package cs416.lambda.capstone
import io.grpc.ManagedChannelBuilder
import java.io.Closeable
import java.util.concurrent.TimeUnit


class StubNode(val host: String, val port: Int) : Closeable {
    private val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()
    private val stub = RaftServiceGrpc.newBlockingStub(channel)
    init {
        println("StubNode $host:$port created")
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
}