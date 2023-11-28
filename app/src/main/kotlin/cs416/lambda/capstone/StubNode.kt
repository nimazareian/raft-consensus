package cs416.lambda.capstone
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import java.io.Closeable
import java.util.concurrent.TimeUnit


class StubNode(val host: String, val port: Int, private val requestVoteResponseObserver: StreamObserver<VoteResponse>,
               private val appendEntriesResponseStreamObserver: StreamObserver<AppendEntriesResponse>) : Closeable {
    private val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()
    private val stub = RaftServiceGrpc.newStub(channel)

    init {
        stub.withWaitForReady()
        println("StubNode $host:$port created")
    }

    fun requestVote(request: VoteRequest) {
        stub.requestVote(request, requestVoteResponseObserver);
    }

    fun appendEntries(request: AppendEntriesRequest) {
        stub.appendEntries(request, appendEntriesResponseStreamObserver);
    }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }

    override fun toString(): String {
        return "StubNode(host='$host', port=$port)"
    }
}