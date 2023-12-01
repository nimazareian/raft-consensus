package cs416.lambda.capstone
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import java.io.Closeable
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger { }

class StubNode(
    val address: String,
    val port: Int,
    private val requestVoteResponseObserver: StreamObserver<VoteResponse>,
    private val appendEntriesResponseStreamObserver: StreamObserver<AppendEntriesResponse>
) : Closeable {
    private val channel = ManagedChannelBuilder.forAddress(address, port).usePlaintext().build()
    private val stub = RaftServiceGrpc.newStub(channel)

    @Volatile
    var nextIndex: Int = 0
    @Volatile
    var matchIndex: Int = -1
    init {
        // TODO: Should we wait till the channel is ready?
        // stub.withWaitForReady()
        logger.debug { "StubNode $address:$port created" }
    }

    fun requestVote(request: VoteRequest) {
        stub.requestVote(request, requestVoteResponseObserver)
    }

    fun appendEntries(request: AppendEntriesRequest) {
        stub.appendEntries(request, appendEntriesResponseStreamObserver)
    }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }

    override fun toString(): String {
        return "StubNode(host='$address', port=$port)"
    }
}