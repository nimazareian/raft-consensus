package cs416.lambda.capstone
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import java.io.Closeable
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger { }

class StubNode(
    val stubNodeId: Int,
    val address: String,
    val port: Int,
    private val requestVoteResponseObserver: StreamObserver<VoteResponse>,
    private val appendEntriesResponseStreamObserver: StreamObserver<AppendEntriesResponse>
) : Closeable {
    private val channel = ManagedChannelBuilder.forAddress(address, port).usePlaintext().build()
    private val stub = RaftServiceGrpc.newStub(channel)

    @Volatile
    // Refers to the index of the next log entry to send to this follower
    var nextIndex: Int = 0

    @Volatile
    // Refers to the last log entry known to be committed on follower
    // node.matchIndex = node.nextIndex - 1
    var matchIndex: Int = -1

    init {
        logger.debug { "StubNode $address:$port created" }
    }

    fun decreaseIndex() {
        if (nextIndex > 0) {
            nextIndex -= 1
        }
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
        return "StubNode(id=$stubNodeId, host='$address', port=$port)"
    }
}